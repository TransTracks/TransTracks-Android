/*
 * Copyright © 2018-2023 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.gallery

import android.Manifest
import android.content.DialogInterface
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.TransTracksApp
import com.drspaceboo.transtracks.background.CameraHandler
import com.drspaceboo.transtracks.background.StoragePermissionHandler
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.ui.MainActivity
import com.drspaceboo.transtracks.ui.PickMediaHandlingData
import com.drspaceboo.transtracks.ui.gallery.GalleryFragmentArgs
import com.drspaceboo.transtracks.ui.gallery.GalleryFragmentDirections
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.Observables
import com.drspaceboo.transtracks.util.ShareUtil
import com.drspaceboo.transtracks.util.dismissIfShowing
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.openDefault
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.settings.SettingsManager
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.realm.kotlin.Realm
import java.io.File

class GalleryFragment : Fragment(R.layout.gallery) {
    val args: GalleryFragmentArgs by navArgs()

    private var photoTakenDisposable: Disposable = Disposable.disposed()
    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    private var confirmDeleteDialog: AlertDialog? = null

    override fun onStart() {
        super.onStart()
        val view = view as? GalleryView ?: throw AssertionError("View must be GalleryView")

        AnalyticsUtil.logEvent(Event.GalleryControllerShown(args.isFaceGallery))

        val type: Int = when (args.isFaceGallery) {
            true -> Photo.TYPE_FACE
            false -> Photo.TYPE_BODY
        }

        view.display(
            GalleryUiState.Loaded(
                type,
                args.initialDay,
                TransTracksApp.hasConsentToShowAds() && SettingsManager.showAds()
            )
        )

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents.ofType<GalleryUiEvent.Back>()
            .subscribe { requireActivity().onBackPressed() }

        viewDisposables += sharedEvents.ofType<GalleryUiEvent.ImageClick>()
            .subscribe { event ->
                findNavController().navigate(
                    GalleryFragmentDirections.actionGalleryToSinglePhoto(event.photoId)
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
                        type,
                        args.initialDay,
                        selectedIds,
                        TransTracksApp.hasConsentToShowAds() && SettingsManager.showAds()
                    )
                )
            }

        viewDisposables += sharedEvents.ofType<GalleryUiEvent.EndActionMode>()
            .subscribe {
                view.display(
                    GalleryUiState.Loaded(
                        type,
                        args.initialDay,
                        TransTracksApp.hasConsentToShowAds() && SettingsManager.showAds()
                    )
                )
            }

        viewDisposables += Observables.combineLatest(
            sharedEvents.ofType<GalleryUiEvent.AddPhotoCamera>(),
            CameraHandler.cameraPermissionEnabled
        ) { _, cameraEnabled -> cameraEnabled }
            .subscribe { cameraEnabled ->
                if (cameraEnabled) {
                    CameraHandler.takePhoto(activity as AppCompatActivity)
                } else {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                        AlertDialog.Builder(requireActivity())
                            .setTitle(R.string.permission_required)
                            .setMessage(R.string.camera_permission_required_message)
                            .setPositiveButton(R.string.grant_permission) { _, _ ->
                                CameraHandler
                                    .requestIfNeeded(requireActivity() as AppCompatActivity)
                            }
                            .setNeutralButton(R.string.cancel, null)
                            .show()
                    } else {
                        val didShow = CameraHandler
                            .requestIfNeeded(requireActivity() as AppCompatActivity)

                        if (!didShow) {
                            CameraHandler.showCameraPermissionDisabledSnackBar(
                                view, requireActivity()
                            )
                        }
                    }
                }
            }

        viewDisposables += CameraHandler.cameraPermissionBlocked
            .filter { showRationale -> !showRationale }
            .subscribe {
                CameraHandler.showCameraPermissionDisabledSnackBar(view, requireActivity())
            }

        viewDisposables += Observables.combineLatest(
            sharedEvents.ofType<GalleryUiEvent.AddPhotoGallery>(),
            StoragePermissionHandler.storagePermissionEnabled
        ) { event, storageEnabled -> event to storageEnabled }
            .subscribe { (event, storageEnabled) ->
                val activity = activity as? MainActivity ?: return@subscribe

                if (PickVisualMedia.isPhotoPickerAvailable(activity)) {
                    activity.launchPickMedia(
                        PickVisualMedia.ImageOnly,
                        PickMediaHandlingData(type = event.type, popToId = R.id.homeFragment)
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    CameraHandler.requestPhotoFromAnotherApp(activity)
                } else if (storageEnabled) {
                    findNavController().navigate(
                        GalleryFragmentDirections.actionGalleryToSelectPhoto(
                            type = event.type, destinationToPopTo = R.id.galleryFragment
                        )
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
                    realm.query(Photo::class, "${Photo.FIELD_ID} == '$photoId'")
                        .first()
                        .find()
                        ?.let { filePaths.add(it.filePath) }
                }

                realm.close()

                if (event.selectedIds.size != filePaths.size) {
                    Snackbar.make(view, R.string.error_sharing_photos, Snackbar.LENGTH_LONG).show()
                    return@subscribe
                }

                view.display(
                    GalleryUiState.Loaded(
                        type,
                        args.initialDay,
                        TransTracksApp.hasConsentToShowAds() && SettingsManager.showAds()
                    )
                )

                ShareUtil.sharePhotos(filePaths.map { path -> File(path) }, view.context)
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
                                .query(Photo::class, "${Photo.FIELD_ID} == '$photoId'")
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
                                    findLatest(photoToDelete)?.let { delete(it) }
                                    success = true
                                }
                            }
                        }

                        realm.close()

                        dialog.dismiss()
                        if (success) {
                            view.display(
                                GalleryUiState.Loaded(
                                    type,
                                    args.initialDay,
                                    TransTracksApp.hasConsentToShowAds() && SettingsManager.showAds()
                                )
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

        if (photoTakenDisposable.isDisposed) {
            photoTakenDisposable = CameraHandler.photoTaken
                .subscribe { absolutePath ->
                    findNavController().navigate(
                        GalleryFragmentDirections.actionGlobalAssignPhotos(
                            uris = arrayOf(Uri.fromFile(File(absolutePath))),
                            type = type,
                            destinationToPopTo = R.id.galleryFragment
                        )
                    )
                }
        }
    }

    override fun onDetach() {
        viewDisposables.clear()
        super.onDetach()
    }
}
