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
import com.drspaceboo.transtracker.ui.gallery.GalleryController
import com.drspaceboo.transtracker.ui.selectphoto.SelectPhotoController
import com.drspaceboo.transtracker.ui.settings.SettingsController
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import org.threeten.bp.LocalDate

class HomeController : Controller() {
    private var resultDisposable: Disposable = Disposables.disposed()

    override fun onCreateView(@NonNull inflater: LayoutInflater, @NonNull container: ViewGroup): View {
        val view: HomeView = inflater.inflate(R.layout.home, container, false) as HomeView

        resultDisposable = view.events.map { event ->
            when (event) {
                is HomeUiEvent.SelectPhoto -> SelectPhotoController()
                is HomeUiEvent.Gallery -> GalleryController()
                is HomeUiEvent.Settings -> SettingsController()
            }
        }.subscribe { controller -> router.pushController(RouterTransaction.with(controller)) }

        view.display(HomeUiState.Loaded(12,
                                        LocalDate.of(2017, 8, 17),
                                        LocalDate.of(2018, 5, 14),
                                        emptyList(),
                                        emptyList(),
                                        true))

        return view
    }

    override fun onDestroy() {
        super.onDestroy()
        resultDisposable.dispose()
    }
}
