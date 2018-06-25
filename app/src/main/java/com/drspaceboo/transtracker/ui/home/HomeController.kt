/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracker.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.support.annotation.NonNull
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.drspaceboo.transtracker.R
import com.drspaceboo.transtracker.TransTrackerApp
import com.drspaceboo.transtracker.background.StoragePermissionHandler
import com.drspaceboo.transtracker.domain.HomeAction
import com.drspaceboo.transtracker.domain.HomeDomain
import com.drspaceboo.transtracker.domain.HomeResult
import com.drspaceboo.transtracker.ui.gallery.GalleryController
import com.drspaceboo.transtracker.ui.selectphoto.SelectPhotoController
import com.drspaceboo.transtracker.ui.settings.SettingsController
import com.drspaceboo.transtracker.ui.singlephoto.SinglePhotoController
import com.drspaceboo.transtracker.util.Observables
import com.drspaceboo.transtracker.util.isNotDisposed
import com.drspaceboo.transtracker.util.ofType
import com.drspaceboo.transtracker.util.plusAssign
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

        val domain: HomeDomain = TransTrackerApp.instance.domainManager.homeDomain

        if (resultDisposable.isDisposed) {
            resultDisposable = domain.results.subscribe()
        }

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
                        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
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
                .subscribe {
                    Snackbar.make(view, R.string.storage_permission_disabled,
                                  Snackbar.LENGTH_LONG)
                            .setAction(R.string.settings) { goToDeviceSettings() }
                            .show()
                }

        viewDisposables += sharedEvents
                .filter { event ->
                    event !== HomeUiEvent.PreviousRecord && event !== HomeUiEvent.NextRecord
                            && event !== HomeUiEvent.SelectPhoto
                }
                .map { event ->
                    return@map when (event) {
                        is HomeUiEvent.Settings -> SettingsController()
                        is HomeUiEvent.PreviousRecord -> HomeController()
                        is HomeUiEvent.NextRecord -> HomeController()
                        is HomeUiEvent.FaceGallery -> GalleryController(isFaceGallery = true)
                        is HomeUiEvent.BodyGallery -> GalleryController(isFaceGallery = false)
                        is HomeUiEvent.ImageClick -> SinglePhotoController()
                        else -> throw IllegalArgumentException("Unhandled event - $event")
                    }
                }.subscribe { controller -> router.pushController(RouterTransaction.with(controller)) }
    }

    override fun onDetach(view: View) {
        viewDisposables.clear()
    }

    override fun onDestroy() {
        if (resultDisposable.isNotDisposed()) {
            resultDisposable.dispose()
        }
    }

    private fun goToDeviceSettings() {
        //Setting the user to the settings screen
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val uri = Uri.fromParts("package", activity!!.packageName, null)
        intent.data = uri
        startActivity(intent)
    }
}

val homeResultsToStates = ObservableTransformer<HomeResult, HomeUiState> { results ->
    results.map { result ->
        return@map when (result) {
            is HomeResult.Loading -> HomeUiState.Loading

            is HomeResult.Loaded -> HomeUiState.Loaded(result.dayString, result.showPreviousRecord,
                                                       result.showNextRecord, result.startDate, result.currentDate,
                                                       result.bodyPhotos, result.facePhotos, result.showAds)
        }
    }
}
