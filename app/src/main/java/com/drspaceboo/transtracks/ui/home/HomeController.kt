/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.home

import android.Manifest
import android.support.annotation.NonNull
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.TransTracksApp
import com.drspaceboo.transtracks.background.StoragePermissionHandler
import com.drspaceboo.transtracks.domain.HomeAction
import com.drspaceboo.transtracks.domain.HomeDomain
import com.drspaceboo.transtracks.domain.HomeResult
import com.drspaceboo.transtracks.ui.gallery.GalleryController
import com.drspaceboo.transtracks.ui.selectphoto.SelectPhotoController
import com.drspaceboo.transtracks.ui.settings.SettingsController
import com.drspaceboo.transtracks.ui.singlephoto.SinglePhotoController
import com.drspaceboo.transtracks.util.Observables
import com.drspaceboo.transtracks.util.Utils
import com.drspaceboo.transtracks.util.isNotDisposed
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import io.reactivex.ObservableTransformer
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables

class HomeController : Controller() {
    private var resultDisposable: Disposable = Disposables.disposed()
    private var viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(@NonNull inflater: LayoutInflater, @NonNull container: ViewGroup): View {
        return inflater.inflate(R.layout.home, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is HomeView) throw AssertionError("View must be HomeView")

        val domain: HomeDomain = TransTracksApp.instance.domainManager.homeDomain

        if (resultDisposable.isDisposed) {
            resultDisposable = domain.results.subscribe()
        }

        domain.actions.accept(HomeAction.ReloadDay)

        viewDisposables += domain.results
                .compose(homeResultsToStates)
                .subscribe { state -> view.display(state) }

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents
                .ofType<HomeUiEvent.PreviousRecord>()
                .map { HomeAction.PreviousDay }
                .subscribe(domain.actions)

        viewDisposables += sharedEvents
                .ofType<HomeUiEvent.NextRecord>()
                .map { HomeAction.NextDay }
                .subscribe(domain.actions)

        viewDisposables += Observables.combineLatest(
                sharedEvents.ofType<HomeUiEvent.SelectPhoto>(),
                StoragePermissionHandler.storagePermissionEnabled) { _, storageEnabled -> storageEnabled }
                .subscribe { storageEnabled ->
                    if (storageEnabled) {
                        router.pushController(RouterTransaction.with(SelectPhotoController()))
                    } else {
                        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                            AlertDialog.Builder(activity!!)
                                    .setTitle(R.string.permission_required)
                                    .setMessage(R.string.storage_permission_required_message)
                                    .setPositiveButton(R.string.grant_permission) { _, _ ->
                                        StoragePermissionHandler.requestIfNeeded(router.activity as AppCompatActivity)
                                    }
                                    .setNeutralButton(R.string.cancel, null)
                                    .show()
                        } else {
                            StoragePermissionHandler.requestIfNeeded(router.activity as AppCompatActivity)
                        }
                    }
                }

        viewDisposables += StoragePermissionHandler.storagePermissionBlocked
                .filter { showRationale -> !showRationale }
                .subscribe { _ ->
                    Snackbar.make(view, R.string.storage_permission_disabled,
                                  Snackbar.LENGTH_LONG)
                            .setAction(R.string.settings) { _ -> Utils.goToDeviceSettings(activity!!) }
                            .show()
                }

        viewDisposables += sharedEvents
                .filter { event ->
                    event !== HomeUiEvent.PreviousRecord && event !== HomeUiEvent.NextRecord
                            && event !== HomeUiEvent.SelectPhoto
                }
                .subscribe { event ->
                    when (event) {
                        is HomeUiEvent.Settings ->
                            router.pushController(RouterTransaction.with(SettingsController()))

                        is HomeUiEvent.PreviousRecord ->
                            router.pushController(RouterTransaction.with(HomeController())
                                                          .tag(HomeController.TAG))

                        is HomeUiEvent.NextRecord ->
                            router.pushController(RouterTransaction.with(HomeController())
                                                          .tag(HomeController.TAG))

                        is HomeUiEvent.FaceGallery ->
                            router.pushController(RouterTransaction.with(
                                    GalleryController(isFaceGallery = true, initialDay = event.day)))

                        is HomeUiEvent.BodyGallery ->
                            router.pushController(RouterTransaction.with(
                                    GalleryController(isFaceGallery = false, initialDay = event.day)))

                        is HomeUiEvent.ImageClick ->
                            router.pushController(RouterTransaction.with(SinglePhotoController(event.photoId)))

                        is HomeUiEvent.AddPhoto ->
                            router.pushController(RouterTransaction.with(
                                    SelectPhotoController(event.currentDate.toEpochDay(), event.type)))
                    }
                }
    }

    override fun onDetach(view: View) {
        viewDisposables.clear()
    }

    override fun onDestroy() {
        if (resultDisposable.isNotDisposed()) {
            resultDisposable.dispose()
        }
    }

    companion object {
        const val TAG = "HomeController"
    }
}

val homeResultsToStates = ObservableTransformer<HomeResult, HomeUiState> { results ->
    results.map { result ->
        return@map when (result) {
            is HomeResult.Loading -> HomeUiState.Loading

            is HomeResult.Loaded -> HomeUiState.Loaded(result.dayString, result.showPreviousRecord,
                                                       result.showNextRecord, result.startDate, result.currentDate,
                                                       result.facePhotos, result.bodyPhotos, result.showAds)
        }
    }
}
