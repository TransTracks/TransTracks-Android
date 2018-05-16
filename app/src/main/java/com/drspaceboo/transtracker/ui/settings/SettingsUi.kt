/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracker.ui.settings

import android.content.Context
import android.support.constraint.ConstraintLayout
import android.util.AttributeSet
import android.widget.Button
import com.drspaceboo.transtracker.R
import com.drspaceboo.transtracker.util.toFullDateString
import com.jakewharton.rxbinding2.view.clicks
import io.reactivex.Observable
import kotterknife.bindView
import org.threeten.bp.LocalDate

sealed class SettingsUiEvent {
    data class ChangeStartDate(val current: LocalDate?) : SettingsUiEvent()
}

sealed class SettingsUiState {
    data class Loaded(val startDate: LocalDate) : SettingsUiState()
}

class SettingsView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    var currentStartDate: LocalDate? = null

    private val startDate: Button by bindView(R.id.settings_start_date)

    val events: Observable<SettingsUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        startDate.clicks().map<SettingsUiEvent> { SettingsUiEvent.ChangeStartDate(currentStartDate) }
    }

    fun display(state: SettingsUiState) {
        when (state) {
            is SettingsUiState.Loaded -> {
                currentStartDate = state.startDate

                startDate.text = state.startDate.toFullDateString(startDate.context)
            }
        }
    }
}
