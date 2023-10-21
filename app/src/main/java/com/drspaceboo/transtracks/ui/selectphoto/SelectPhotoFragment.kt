/*
 * Copyright © 2018-2023 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.selectphoto

import android.Manifest
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.background.CameraHandler
import com.drspaceboo.transtracks.ui.selectphoto.SelectPhotoFragmentDirections
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.Observables
import com.drspaceboo.transtracks.util.RxSchedulers
import com.drspaceboo.transtracks.util.isNotDisposed
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import java.io.File

@Deprecated("This shouldn't be handled in app, let's move to use selecting with other apps")
class SelectPhotoFragment : Fragment(R.layout.select_photo) {
    val args: SelectPhotoFragmentArgs by navArgs()

    private var photoTakenDisposable: Disposable = Disposable.disposed()
    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()
        val view = view as? SelectPhotoView ?: throw AssertionError("View must be SelectPhotoView")
        AnalyticsUtil.logEvent(Event.SelectPhotoControllerShown)

        view.display(SelectPhotoUiState.Loaded)

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents
            .ofType<SelectPhotoUiEvent.Back>()
            .subscribe { requireActivity().onBackPressed() }

        viewDisposables += sharedEvents.ofType<SelectPhotoUiEvent.ViewAlbums>()
            .subscribe {
                findNavController().navigate(
                    SelectPhotoFragmentDirections.actionSelectAlbum(
                        type = args.type,
                        destinationToPopTo = args.destinationToPopTo,
                        epochDay = args.epochDay
                    )
                )
            }

        viewDisposables += sharedEvents.ofType<SelectPhotoUiEvent.ExternalGalleries>()
            .subscribe { CameraHandler.requestPhotoFromAnotherApp(activity as AppCompatActivity) }

        viewDisposables += sharedEvents.ofType<SelectPhotoUiEvent.SelectionUpdate>()
            .observeOn(RxSchedulers.main())
            .subscribe { event ->
                val state = when {
                    event.uris.isEmpty() -> SelectPhotoUiState.Loaded
                    else -> SelectPhotoUiState.Selection(event.uris)
                }
                view.display(state)
            }

        viewDisposables += sharedEvents.ofType<SelectPhotoUiEvent.EndMultiSelect>()
            .observeOn(RxSchedulers.main())
            .subscribe { view.display(SelectPhotoUiState.Loaded) }

        viewDisposables += Observables.combineLatest(
            sharedEvents.ofType<SelectPhotoUiEvent.TakePhoto>(),
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
                                CameraHandler.requestIfNeeded(requireActivity() as AppCompatActivity)
                            }
                            .setNeutralButton(R.string.cancel, null)
                            .show()
                    } else {
                        val didShow =
                            CameraHandler.requestIfNeeded(requireActivity() as AppCompatActivity)

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

        viewDisposables += sharedEvents
            .ofType<SelectPhotoUiEvent.PhotoSelected>()
            .subscribe { event ->
                findNavController().navigate(
                    SelectPhotoFragmentDirections.actionGlobalAssignPhotos(
                        uris = arrayOf(event.uri),
                        type = args.type,
                        destinationToPopTo = args.destinationToPopTo,
                        epochDay = args.epochDay
                    )
                )
            }

        viewDisposables += sharedEvents.ofType<SelectPhotoUiEvent.SaveMultiple>()
            .subscribe { event ->
                findNavController().navigate(
                    SelectPhotoFragmentDirections.actionGlobalAssignPhotos(
                        uris = event.uris.toTypedArray(),
                        type = args.type,
                        destinationToPopTo = args.destinationToPopTo,
                        epochDay = args.epochDay
                    )
                )
            }

        if (photoTakenDisposable.isDisposed) {
            photoTakenDisposable = CameraHandler.photoTaken
                .subscribe { absolutePath ->
                    findNavController().navigate(
                        SelectPhotoFragmentDirections.actionGlobalAssignPhotos(
                            uris = arrayOf(Uri.fromFile(File(absolutePath))),
                            type = args.type,
                            destinationToPopTo = args.destinationToPopTo,
                            epochDay = args.epochDay
                        )
                    )
                }
        }
    }

    override fun onDetach() {
        viewDisposables.clear()
        super.onDetach()
    }

    override fun onDestroyView() {
        if (photoTakenDisposable.isNotDisposed()) {
            photoTakenDisposable.dispose()
        }
        super.onDestroyView()
    }
}
