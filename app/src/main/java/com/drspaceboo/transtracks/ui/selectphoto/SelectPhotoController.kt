/*
 * Copyright © 2018 TransTracks. All rights reserved.
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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.background.CameraHandler
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.ui.assignphoto.AssignPhotosController
import com.drspaceboo.transtracks.ui.home.HomeController
import com.drspaceboo.transtracks.ui.selectphoto.selectalbum.SelectAlbumController
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.Observables
import com.drspaceboo.transtracks.util.RxSchedulers
import com.drspaceboo.transtracks.util.isNotDisposed
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.using
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import java.io.File

@Deprecated("This shouldn't be handled in app, let's move to use selecting with other apps")
class SelectPhotoController(args: Bundle) : Controller(args) {
    constructor(
        epochDay: Long? = null, @Photo.Type type: Int = Photo.TYPE_FACE,
        tagOfControllerToPopTo: String = HomeController.TAG
    ) : this(Bundle().apply {
        if (epochDay != null) {
            putLong(KEY_EPOCH_DAY, epochDay)
        }
        putInt(KEY_TYPE, type)
        putString(KEY_TAG_OF_CONTROLLER_TO_POP_TO, tagOfControllerToPopTo)
    })

    private val epochDay: Long? = when (args.containsKey(KEY_EPOCH_DAY)) {
        true -> args.getLong(KEY_EPOCH_DAY)
        false -> null
    }

    private val type: Int = args.getInt(KEY_TYPE)

    private var photoTakenDisposable: Disposable = Disposable.disposed()
    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(
        @NonNull inflater: LayoutInflater, @NonNull container: ViewGroup
    ): View {
        return inflater.inflate(R.layout.select_photo, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is SelectPhotoView) throw AssertionError("View must be SelectPhotoView")
        AnalyticsUtil.logEvent(Event.SelectPhotoControllerShown)

        view.display(SelectPhotoUiState.Loaded)

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents
            .ofType<SelectPhotoUiEvent.Back>()
            .subscribe { router.handleBack() }

        viewDisposables += sharedEvents.ofType<SelectPhotoUiEvent.ViewAlbums>()
            .subscribe {
                val popTo = args.getString(KEY_TAG_OF_CONTROLLER_TO_POP_TO)!!
                router.pushController(
                    RouterTransaction.with(SelectAlbumController(epochDay, type, popTo))
                        .using(HorizontalChangeHandler())
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
                        AlertDialog.Builder(activity!!)
                            .setTitle(R.string.permission_required)
                            .setMessage(R.string.camera_permission_required_message)
                            .setPositiveButton(R.string.grant_permission) { _, _ ->
                                CameraHandler.requestIfNeeded(router.activity as AppCompatActivity)
                            }
                            .setNeutralButton(R.string.cancel, null)
                            .show()
                    } else {
                        val didShow =
                            CameraHandler.requestIfNeeded(router.activity as AppCompatActivity)

                        if (!didShow) {
                            CameraHandler.showCameraPermissionDisabledSnackBar(view, activity!!)
                        }
                    }
                }
            }

        viewDisposables += CameraHandler.cameraPermissionBlocked
            .filter { showRationale -> !showRationale }
            .subscribe { CameraHandler.showCameraPermissionDisabledSnackBar(view, activity!!) }

        viewDisposables += sharedEvents
            .ofType<SelectPhotoUiEvent.PhotoSelected>()
            .subscribe { event ->
                router.pushController(
                    RouterTransaction
                        .with(
                            AssignPhotosController(
                                arrayListOf(event.uri),
                                epochDay,
                                type,
                                args.getString(KEY_TAG_OF_CONTROLLER_TO_POP_TO)!!
                            )
                        )
                        .using(HorizontalChangeHandler())
                )
            }

        viewDisposables += sharedEvents.ofType<SelectPhotoUiEvent.SaveMultiple>()
            .subscribe { event ->
                router.pushController(
                    RouterTransaction
                        .with(
                            AssignPhotosController(
                                event.uris,
                                epochDay,
                                type,
                                args.getString(KEY_TAG_OF_CONTROLLER_TO_POP_TO)!!
                            )
                        )
                        .using(HorizontalChangeHandler())
                )
            }

        if (photoTakenDisposable.isDisposed) {
            photoTakenDisposable = CameraHandler.photoTaken.subscribe { absolutePath ->
                router.pushController(
                    RouterTransaction
                        .with(
                            AssignPhotosController(
                                arrayListOf(Uri.fromFile(File(absolutePath))),
                                epochDay,
                                type,
                                args.getString(KEY_TAG_OF_CONTROLLER_TO_POP_TO)!!
                            )
                        )
                        .using(HorizontalChangeHandler())
                )
            }
        }
    }

    override fun onDetach(view: View) {
        viewDisposables.clear()
    }

    override fun onDestroy() {
        if (photoTakenDisposable.isNotDisposed()) {
            photoTakenDisposable.dispose()
        }
    }

    companion object {
        private const val KEY_EPOCH_DAY = "epochDay"
        private const val KEY_TYPE = "type"
        private const val KEY_TAG_OF_CONTROLLER_TO_POP_TO = "tagOfControllerToPopTo"
    }
}
