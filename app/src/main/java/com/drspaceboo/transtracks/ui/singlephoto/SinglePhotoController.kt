/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.singlephoto

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.ui.editphoto.EditPhotoController
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.ShareUtil
import com.drspaceboo.transtracks.util.dismissIfShowing
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.toFullDateString
import com.drspaceboo.transtracks.util.using
import com.google.android.material.snackbar.Snackbar
import io.reactivex.disposables.CompositeDisposable
import io.realm.Realm
import java.time.LocalDate
import java.io.File

class SinglePhotoController(args: Bundle) : Controller(args) {
    constructor(photoId: String) : this(Bundle().apply {
        putString(KEY_PHOTO_ID, photoId)
    })

    private val photoId: String = args.getString(KEY_PHOTO_ID)!!

    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    private var confirmDeleteDialog: AlertDialog? = null

    override fun onCreateView(@NonNull inflater: LayoutInflater, @NonNull container: ViewGroup): View {
        return inflater.inflate(R.layout.single_photo, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is SinglePhotoView) throw AssertionError("View must be SinglePhotoView")

        AnalyticsUtil.logEvent(Event.SinglePhotoControllerShown)

        Realm.getDefaultInstance().use { realm ->
            val photo = realm.where(Photo::class.java).equalTo(Photo.FIELD_ID, photoId)
                    .findFirst()

            if (photo == null) {
                AlertDialog.Builder(view.context)
                        .setTitle(R.string.error_loading_photo)
                        .setMessage(R.string.error_loading_photo_message)
                        .setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int ->
                            dialog.dismiss()
                            router.handleBack()
                        }
                        .setCancelable(false)
                        .show()
                return@use
            }

            val details = view.getString(
                    R.string.photo_detail_replacement,
                    LocalDate.ofEpochDay(photo.epochDay).toFullDateString(view.context),
                    Photo.getTypeName(photo.type, view.context))

            view.display(SinglePhotoUiState.Loaded(photo.filePath, details, photo.id))
        }

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents.ofType<SinglePhotoUiEvent.Back>()
                .subscribe { router.handleBack() }

        viewDisposables += sharedEvents.ofType<SinglePhotoUiEvent.Edit>()
                .subscribe { event ->
                    router.pushController(RouterTransaction.with(EditPhotoController(event.photoId))
                                                  .using(HorizontalChangeHandler()))
                }

        viewDisposables += sharedEvents.ofType<SinglePhotoUiEvent.Share>()
                .subscribe { event ->
                    var filePath: String? = null

                    Realm.getDefaultInstance().use { realm ->
                        val photoToDelete: Photo = realm.where(Photo::class.java)
                                .equalTo(Photo.FIELD_ID, event.photoId)
                                .findFirst() ?: return@use

                        filePath = photoToDelete.filePath
                    }

                    if (filePath == null) {
                        Snackbar.make(view, R.string.error_sharing_photo, Snackbar.LENGTH_LONG)
                                .show()
                        return@subscribe
                    }

                    ShareUtil.sharePhoto(File(filePath), view.context, this)
                }

        viewDisposables += sharedEvents.ofType<SinglePhotoUiEvent.Delete>()
                .subscribe { event ->
                    confirmDeleteDialog?.dismissIfShowing()

                    confirmDeleteDialog = AlertDialog.Builder(view.context)
                            .setTitle(R.string.are_you_sure)
                            .setMessage(R.string.confirm_delete_photo)
                            .setPositiveButton(R.string.delete) { dialog: DialogInterface, _: Int ->
                                var success = false
                                Realm.getDefaultInstance().use { realm ->
                                    val photoToDelete: Photo? = realm.where(Photo::class.java)
                                            .equalTo(Photo.FIELD_ID, event.photoId)
                                            .findFirst()

                                    if (photoToDelete == null) {
                                        success = false
                                        return@use
                                    }

                                    val image = File(photoToDelete.filePath)

                                    if (image.exists()) {
                                        image.delete()
                                    }

                                    realm.executeTransaction {
                                        photoToDelete.deleteFromRealm()
                                        success = true
                                    }
                                }

                                dialog.dismiss()
                                if (success) {
                                    router.handleBack()
                                } else {
                                    Snackbar.make(view, R.string.error_deleting_photo,
                                                  Snackbar.LENGTH_LONG)
                                            .show()
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
        private const val KEY_PHOTO_ID = "photoId"
    }
}
