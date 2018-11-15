/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.util.gone
import com.drspaceboo.transtracks.util.loadAd
import com.drspaceboo.transtracks.util.toFullDateString
import com.drspaceboo.transtracks.util.visible
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdView
import com.jakewharton.rxbinding2.support.v7.widget.navigationClicks
import com.jakewharton.rxbinding2.view.clicks
import io.reactivex.Observable
import kotterknife.bindView
import org.threeten.bp.LocalDate

sealed class SettingsUiEvent {
    object Back : SettingsUiEvent()
    object ChangeStartDate : SettingsUiEvent()
    object ChangeTheme : SettingsUiEvent()
    object ChangeLockMode : SettingsUiEvent()
    object ChangeLockDelay : SettingsUiEvent()
    object PrivacyPolicy : SettingsUiEvent()
}

sealed class SettingsUiState {
    data class Loaded(val startDate: LocalDate, val theme: String, val lockMode: String,
                      val enableLockDelay: Boolean, val lockDelay: String, val appVersion: String, val copyright: String, val showAds: Boolean) : SettingsUiState()
}

class SettingsView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.settings_toolbar)

    private val startLabel: View by bindView(R.id.settings_start_label)
    private val startDate: Button by bindView(R.id.settings_start_date)
    private val themeLabel: View by bindView(R.id.settings_theme_label)
    private val theme: Button by bindView(R.id.settings_theme)
    private val lockLabel: View by bindView(R.id.settings_lock_label)
    private val lock: Button by bindView(R.id.settings_lock)
    private val lockDescription: View by bindView(R.id.settings_lock_description)
    private val lockDelayLabel: View by bindView(R.id.settings_lock_delay_label)
    private val lockDelay: Button by bindView(R.id.settings_lock_delay)

    private val appVersion: TextView by bindView(R.id.settings_app_version)
    private val privacyPolicy: Button by bindView(R.id.settings_privacy_policy)

    private val copyright: TextView by bindView(R.id.settings_copyright)

    private val adViewLayout: View by bindView(R.id.settings_ad_layout)
    private val adView: AdView by bindView(R.id.settings_ad_view)

    val events: Observable<SettingsUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.mergeArray(toolbar.navigationClicks().map { SettingsUiEvent.Back },
                              startDate.clicks().map { SettingsUiEvent.ChangeStartDate },
                              theme.clicks().map { SettingsUiEvent.ChangeTheme },
                              lock.clicks().map { SettingsUiEvent.ChangeLockMode },
                              lockDelay.clicks().map { SettingsUiEvent.ChangeLockDelay },
                              privacyPolicy.clicks().map { SettingsUiEvent.PrivacyPolicy })
    }

    private var currentStartDate: LocalDate? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        startLabel.setOnClickListener { startDate.performClick() }
        themeLabel.setOnClickListener { theme.performClick() }
        lockLabel.setOnClickListener { lock.performClick() }
        lockDescription.setOnClickListener { lock.performClick() }
        lockDelayLabel.setOnClickListener { lockDelay.performClick() }

        adView.adListener = object : AdListener() {
            override fun onAdFailedToLoad(code: Int) {
                adViewLayout.gone()
            }
        }
    }

    fun display(state: SettingsUiState) {
        when (state) {
            is SettingsUiState.Loaded -> {
                currentStartDate = state.startDate

                startDate.text = state.startDate.toFullDateString(startDate.context)
                theme.text = state.theme
                lock.text = state.lockMode

                lockDelayLabel.isEnabled = state.enableLockDelay
                lockDelay.isEnabled = state.enableLockDelay

                lockDelay.text = state.lockDelay

                appVersion.text = state.appVersion

                copyright.text = state.copyright

                if (state.showAds) {
                    adViewLayout.visible()
                    adView.loadAd()
                } else {
                    adViewLayout.gone()
                }
            }
        }
    }
}
