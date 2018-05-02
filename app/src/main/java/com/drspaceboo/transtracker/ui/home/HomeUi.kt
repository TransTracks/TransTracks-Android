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

import android.content.Context
import android.support.constraint.ConstraintLayout
import android.util.AttributeSet
import android.widget.Button
import com.drspaceboo.transtracker.R
import com.jakewharton.rxbinding2.view.clicks
import io.reactivex.Observable
import kotterknife.bindView

sealed class HomeUiEvent {
    object SelectPhoto : HomeUiEvent()
    object Gallery : HomeUiEvent()
    object Settings : HomeUiEvent()
}

class HomeView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {

    private val selectPhoto: Button by bindView(R.id.home_to_select_photo)
    private val gallery: Button by bindView(R.id.home_to_gallery)
    private val settings: Button by bindView(R.id.home_to_settings)

    val events: Observable<HomeUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge(selectPhoto.clicks().map { HomeUiEvent.SelectPhoto },
                         gallery.clicks().map { HomeUiEvent.Gallery },
                         settings.clicks().map { HomeUiEvent.Settings })
    }
}
