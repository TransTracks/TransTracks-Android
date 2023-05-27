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

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.TransTracksApp
import com.drspaceboo.transtracks.background.StoragePermissionHandler
import com.drspaceboo.transtracks.domain.HomeAction
import com.drspaceboo.transtracks.domain.HomeDomain
import com.drspaceboo.transtracks.domain.HomeResult
import com.drspaceboo.transtracks.ui.gallery.GalleryController
import com.drspaceboo.transtracks.ui.milestones.MilestonesController
import com.drspaceboo.transtracks.ui.selectphoto.SelectPhotoController
import com.drspaceboo.transtracks.ui.settings.SettingsController
import com.drspaceboo.transtracks.ui.singlephoto.SinglePhotoController
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.Observables
import com.drspaceboo.transtracks.util.settings.PrefUtil
import com.drspaceboo.transtracks.util.isNotDisposed
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.settings.SettingsManager
import com.drspaceboo.transtracks.util.toFullDateString
import com.drspaceboo.transtracks.util.using
import io.reactivex.rxjava3.core.ObservableTransformer
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable


class HomeController : Controller() {
    private var resultDisposable: Disposable = Disposable.disposed()
    private var viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(@NonNull inflater: LayoutInflater, @NonNull container: ViewGroup): View {
        return inflater.inflate(R.layout.home, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is HomeView) throw AssertionError("View must be HomeView")

        AnalyticsUtil.logEvent(Event.HomeControllerShown)

        val domain: HomeDomain = TransTracksApp.instance.domainManager.homeDomain

        if (resultDisposable.isDisposed) {
            resultDisposable = domain.results.subscribe()
        }

        domain.actions.accept(HomeAction.ReloadDay)

        if (SettingsManager.showWelcome()) {
            val builder = AlertDialog.Builder(view.context)
                .setTitle(R.string.welcome)
                .setMessage(R.string.welcome_message)

            @SuppressLint("InflateParams") //Cannot avoid passing null for the root here
            val welcomeView = LayoutInflater.from(builder.context).inflate(R.layout.welcome, null)

            val startDate: TextView = welcomeView.findViewById(R.id.welcome_start_date)
            startDate.text = SettingsManager.getStartDate(activity!!).toFullDateString(startDate.context)

            builder.setView(welcomeView)
                .setPositiveButton(R.string.looks_good, null)
                .setNegativeButton(R.string.change_setting) { dialog: DialogInterface, _: Int ->
                    router.pushController(RouterTransaction.with(SettingsController()).using(VerticalChangeHandler()))
                    dialog.dismiss()
                }
                .show()

            SettingsManager.setShowWelcome(false, activity!!)
        } else if (SettingsManager.showAccountWarning()) {
            AlertDialog.Builder(view.context)
                .setTitle(R.string.warning_title)
                .setMessage(R.string.warning_message)
                .setPositiveButton(R.string.create_account) { dialog, _ ->
                    SettingsManager.setAccountWarning(false, view.context)
                    dialog.dismiss()
                    router.pushController(RouterTransaction.with(SettingsController()).using(VerticalChangeHandler()))
                }
                .setNegativeButton(R.string.risk_it) { dialog, _ ->
                    SettingsManager.setAccountWarning(false, view.context)
                    dialog.dismiss()
                }
                .show()
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
                        router.pushController(RouterTransaction.with(SelectPhotoController())
                                                      .using(VerticalChangeHandler()))
                    } else {
                        StoragePermissionHandler.handleRequestingPermission(
                                view, activity as AppCompatActivity)
                    }
                }

        viewDisposables += StoragePermissionHandler.storagePermissionBlocked
                .filter { showRationale -> !showRationale }
                .subscribe { _ ->
                    StoragePermissionHandler.showStoragePermissionDisabledSnackBar(
                            view, activity as AppCompatActivity)
                }

        viewDisposables += Observables.combineLatest(
                sharedEvents.ofType<HomeUiEvent.AddPhoto>(),
                StoragePermissionHandler.storagePermissionEnabled) { event, storageEnabled -> event to storageEnabled }
                .subscribe { (event, storageEnabled) ->
                    if (storageEnabled) {
                        router.pushController(RouterTransaction.with(
                                SelectPhotoController(event.currentDate.toEpochDay(), event.type))
                                                      .using(VerticalChangeHandler()))
                    } else {
                        StoragePermissionHandler.handleRequestingPermission(
                                view, activity as AppCompatActivity)
                    }
                }

        viewDisposables += sharedEvents
                .filter { event ->
                    event !== HomeUiEvent.PreviousRecord && event !== HomeUiEvent.NextRecord
                            && event !== HomeUiEvent.SelectPhoto && event !is HomeUiEvent.AddPhoto
                }
                .subscribe { event ->
                    when (event) {
                        is HomeUiEvent.Settings ->
                            router.pushController(RouterTransaction.with(SettingsController())
                                                          .using(VerticalChangeHandler()))

                        is HomeUiEvent.Milestones ->
                            router.pushController(RouterTransaction.with(
                                    MilestonesController(event.day)).tag(MilestonesController.TAG))

                        is HomeUiEvent.FaceGallery ->
                            router.pushController(RouterTransaction.with(
                                    GalleryController(isFaceGallery = true, initialDay = event.day))
                                                          .tag(GalleryController.TAG)
                                                          .using(VerticalChangeHandler()))

                        is HomeUiEvent.BodyGallery ->
                            router.pushController(RouterTransaction.with(
                                    GalleryController(isFaceGallery = false, initialDay = event.day))
                                                          .tag(GalleryController.TAG)
                                                          .using(VerticalChangeHandler()))

                        is HomeUiEvent.ImageClick ->
                            router.pushController(RouterTransaction.with(
                                    SinglePhotoController(event.photoId))
                                                          .using(VerticalChangeHandler()))

                        is HomeUiEvent.AddPhoto,
                        is HomeUiEvent.NextRecord,
                        is HomeUiEvent.PreviousRecord,
                        is HomeUiEvent.SelectPhoto -> throw IllegalStateException("unexpected event")
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

            is HomeResult.Loaded ->
                HomeUiState.Loaded(result.dayString, result.showPreviousRecord,
                                   result.showNextRecord, result.startDate, result.currentDate,
                                   result.hasMilestones, result.showAds)
        }
    }
}
