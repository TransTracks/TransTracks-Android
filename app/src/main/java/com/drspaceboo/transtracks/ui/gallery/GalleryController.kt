/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.gallery

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.background.StoragePermissionHandler
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.ui.selectphoto.SelectPhotoController
import com.drspaceboo.transtracks.ui.singlephoto.SinglePhotoController
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.Observables
import com.drspaceboo.transtracks.util.ShareUtil
import com.drspaceboo.transtracks.util.dismissIfShowing
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.openDefault
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.settings.SettingsManager
import com.drspaceboo.transtracks.util.using
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.realm.kotlin.Realm
import java.io.File

class GalleryController(args: Bundle) : Controller(args) {
    constructor(isFaceGallery: Boolean, initialDay: Long) : this(Bundle().apply {
        putBoolean(KEY_IS_FACE_GALLERY, isFaceGallery)
        putLong(KEY_INITIAL_DAY, initialDay)
    })

    private val isFaceGallery: Boolean = args.getBoolean(KEY_IS_FACE_GALLERY)
    private val initialDay: Long = args.getLong(KEY_INITIAL_DAY)

    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    private var confirmDeleteDialog: AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.gallery, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is GalleryView) throw AssertionError("View must be GalleryView")

        AnalyticsUtil.logEvent(Event.GalleryControllerShown(isFaceGallery))

        val type: Int = when (isFaceGallery) {
            true -> Photo.TYPE_FACE
            false -> Photo.TYPE_BODY
        }

        view.display(GalleryUiState.Loaded(type, initialDay, SettingsManager.showAds()))

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents.ofType<GalleryUiEvent.Back>()
            .subscribe { router.handleBack() }

        viewDisposables += sharedEvents.ofType<GalleryUiEvent.ImageClick>()
            .subscribe { event ->
                router.pushController(
                    RouterTransaction.with(SinglePhotoController(event.photoId))
                        .using(HorizontalChangeHandler())
                )
            }

        viewDisposables += sharedEvents
            .filter { event ->
                event is GalleryUiEvent.StartMultiSelect || event is GalleryUiEvent.SelectionUpdated
            }
            .subscribe { event ->
                val selectedIds: ArrayList<String> = when (event) {
                    GalleryUiEvent.StartMultiSelect -> ArrayList()
                    is GalleryUiEvent.SelectionUpdated -> event.selectedIds
                    else -> throw IllegalArgumentException("Unhandled event '${event.javaClass.simpleName}'")
                }

                view.display(
                    GalleryUiState.Selection(
                        type, initialDay, selectedIds, SettingsManager.showAds()
                    )
                )
            }

        viewDisposables += sharedEvents.ofType<GalleryUiEvent.EndActionMode>()
            .subscribe {
                view.display(GalleryUiState.Loaded(type, initialDay, SettingsManager.showAds()))
            }

        viewDisposables += Observables.combineLatest(
            sharedEvents.ofType<GalleryUiEvent.AddPhoto>(),
            StoragePermissionHandler.storagePermissionEnabled
        ) { event, storageEnabled -> event to storageEnabled }
            .subscribe { (event, storageEnabled) ->
                if (storageEnabled) {
                    router.pushController(
                        RouterTransaction
                            .with(
                                SelectPhotoController(
                                    type = event.type, tagOfControllerToPopTo = TAG
                                )
                            )
                            .using(VerticalChangeHandler())
                    )
                } else {
                    StoragePermissionHandler.handleRequestingPermission(
                        view, activity as AppCompatActivity
                    )
                }
            }

        viewDisposables += StoragePermissionHandler.storagePermissionBlocked
            .filter { showRationale -> !showRationale }
            .subscribe { _ ->
                StoragePermissionHandler.showStoragePermissionDisabledSnackBar(
                    view, activity as AppCompatActivity
                )
            }

        viewDisposables += sharedEvents.ofType<GalleryUiEvent.Share>()
            .subscribe { event ->
                if (event.selectedIds.isEmpty()) {
                    Snackbar.make(view, R.string.select_photos_to_share, Snackbar.LENGTH_LONG)
                        .show()
                    return@subscribe
                }

                val filePaths = ArrayList<String>(event.selectedIds.size)
                val realm = Realm.openDefault()

                event.selectedIds.forEach { photoId ->
                    realm.query(Photo::class, "${Photo.FIELD_ID} == $photoId")
                        .first()
                        .find()
                        ?.let { filePaths.add(it.filePath) }
                }

                realm.close()

                if (event.selectedIds.size != filePaths.size) {
                    Snackbar.make(view, R.string.error_sharing_photos, Snackbar.LENGTH_LONG).show()
                    return@subscribe
                }

                view.display(GalleryUiState.Loaded(type, initialDay, SettingsManager.showAds()))

                ShareUtil.sharePhotos(filePaths.map { path -> File(path) }, view.context, this)
            }

        viewDisposables += sharedEvents.ofType<GalleryUiEvent.Delete>()
            .subscribe { event ->
                confirmDeleteDialog?.dismissIfShowing()

                if (event.selectedIds.isEmpty()) {
                    Snackbar.make(view, R.string.select_photos_to_delete, Snackbar.LENGTH_LONG)
                        .show()
                    return@subscribe
                }

                confirmDeleteDialog = AlertDialog.Builder(view.context)
                    .setTitle(R.string.are_you_sure)
                    .setMessage(R.string.confirm_delete_photo)
                    .setPositiveButton(R.string.delete) { dialog: DialogInterface, _: Int ->
                        var success = false

                        val realm = Realm.openDefault()
                        val photosToDelete = ArrayList<Photo>()

                        event.selectedIds.forEach { photoId ->
                            val photoToDelete: Photo? = realm
                                .query(Photo::class, "${Photo.FIELD_ID} == $photoId")
                                .first()
                                .find()

                            if (photoToDelete != null) {
                                photosToDelete.add(photoToDelete)
                            }
                        }

                        //If we found all the photos we were trying to delete
                        if (event.selectedIds.size == photosToDelete.size) {
                            photosToDelete.forEach { photoToDelete ->
                                val image = File(photoToDelete.filePath)

                                if (image.exists()) {
                                    image.delete()
                                }

                                realm.writeBlocking {
                                    delete(photoToDelete)
                                    success = true
                                }
                            }
                        }

                        realm.close()

                        dialog.dismiss()
                        if (success) {
                            view.display(
                                GalleryUiState.Loaded(type, initialDay, SettingsManager.showAds())
                            )
                        } else {
                            Snackbar.make(
                                view, R.string.error_deleting_photos, Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setOnDismissListener { confirmDeleteDialog = null }
                    .create()

                confirmDeleteDialog!!.show()
            }
    }

    override fun onDetach(view: View) {
        viewDisposables.clear()
    }

    companion object {
        private const val KEY_IS_FACE_GALLERY = "isFaceGallery"
        private const val KEY_INITIAL_DAY = "initialDay"

        const val TAG = "GalleryController"
    }
}
