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

import android.support.annotation.NonNull
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.drspaceboo.transtracker.R
import com.drspaceboo.transtracker.TransTrackerApp
import com.drspaceboo.transtracker.domain.HomeAction
import com.drspaceboo.transtracker.domain.HomeDomain
import com.drspaceboo.transtracker.domain.HomeResult
import com.drspaceboo.transtracker.ui.gallery.GalleryController
import com.drspaceboo.transtracker.ui.selectphoto.SelectPhotoController
import com.drspaceboo.transtracker.ui.settings.SettingsController
import com.drspaceboo.transtracker.ui.singlephoto.SinglePhotoController
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

        viewDisposables += sharedEvents
                .filter { event -> event !== HomeUiEvent.PreviousRecord && event !== HomeUiEvent.NextRecord }
                .map { event ->
                    return@map when (event) {
                        is HomeUiEvent.SelectPhoto -> SelectPhotoController()
                        is HomeUiEvent.Settings -> SettingsController()
                        is HomeUiEvent.PreviousRecord -> HomeController()
                        is HomeUiEvent.NextRecord -> HomeController()
                        is HomeUiEvent.FaceGallery -> GalleryController(isFaceGallery = true)
                        is HomeUiEvent.BodyGallery -> GalleryController(isFaceGallery = false)
                        is HomeUiEvent.ImageClick -> SinglePhotoController()
                    }
                }.subscribe { controller -> router.pushController(RouterTransaction.with(controller)) }
    }

    override fun onDetach(view: View) {
        viewDisposables.clear()
    }

    override fun onDestroy() {
        if (!resultDisposable.isDisposed) {
            resultDisposable.dispose()
        }
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
