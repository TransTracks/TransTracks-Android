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
import androidx.constraintlayout.widget.ConstraintLayout
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.gone
import com.drspaceboo.transtracks.util.loadAd
import com.drspaceboo.transtracks.util.toFullDateString
import com.drspaceboo.transtracks.util.visible
import com.google.android.gms.ads.AdListener
import com.jakewharton.rxbinding3.appcompat.navigationClicks
import com.jakewharton.rxbinding3.view.clicks
import io.reactivex.Observable
import kotlinx.android.synthetic.main.settings.view.*
import org.threeten.bp.LocalDate

sealed class SettingsUiEvent {
    object Back : SettingsUiEvent()
    object ChangeName : SettingsUiEvent()
    object ChangeEmail : SettingsUiEvent()
    object SignIn : SettingsUiEvent()
    object ChangePassword : SettingsUiEvent()
    object SignOut : SettingsUiEvent()
    object ChangeStartDate : SettingsUiEvent()
    object ChangeTheme : SettingsUiEvent()
    object ChangeLockMode : SettingsUiEvent()
    object ChangeLockDelay : SettingsUiEvent()
    object PrivacyPolicy : SettingsUiEvent()
}

data class SettingsUIUserDetails(val name: String?, val email: String?, val hasPasswordProvider: Boolean)

sealed class SettingsUiState {
    data class Loaded(
        val userDetails: SettingsUIUserDetails?, val startDate: LocalDate, val theme: String, val lockMode: String,
        val enableLockDelay: Boolean, val lockDelay: String, val appVersion: String, val copyright: String,
        val showAds: Boolean
    ) : SettingsUiState()
}

class SettingsView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    val events: Observable<SettingsUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.mergeArray(
            settings_toolbar.navigationClicks().map { SettingsUiEvent.Back },
            settings_account_name.clicks().map { SettingsUiEvent.ChangeName },
            settings_account_email.clicks().map { SettingsUiEvent.ChangeEmail },
            settings_account_sign_in.clicks().map { SettingsUiEvent.SignIn },
            settings_account_change_password.clicks().map { SettingsUiEvent.ChangePassword },
            settings_account_sign_out.clicks().map { SettingsUiEvent.SignOut },
            settings_start_date.clicks().map { SettingsUiEvent.ChangeStartDate },
            settings_theme.clicks().map { SettingsUiEvent.ChangeTheme },
            settings_lock.clicks().map { SettingsUiEvent.ChangeLockMode },
            settings_lock_delay.clicks().map { SettingsUiEvent.ChangeLockDelay },
            settings_privacy_policy.clicks().map { SettingsUiEvent.PrivacyPolicy }
        )
    }

    private var currentStartDate: LocalDate? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        settings_account_name_label.setOnClickListener { settings_account_name.performClick() }
        settings_account_email_label.setOnClickListener { settings_account_email.performClick() }
        settings_start_label.setOnClickListener { settings_start_date.performClick() }
        settings_theme_label.setOnClickListener { settings_theme.performClick() }
        settings_lock_label.setOnClickListener { settings_lock.performClick() }
        settings_lock_description.setOnClickListener { settings_lock.performClick() }
        settings_lock_delay_label.setOnClickListener { settings_lock_delay.performClick() }

        settings_ad_view.adListener = object : AdListener() {
            override fun onAdFailedToLoad(code: Int) {
                settings_ad_layout.gone()
            }
        }
    }

    fun display(state: SettingsUiState) {
        when (state) {
            is SettingsUiState.Loaded -> {
                if (state.userDetails != null) {
                    displayUserDetails(state.userDetails)
                } else {
                    settings_account_description.visible()
                    settings_account_name_layout.gone()
                    settings_account_email_layout.gone()
                    settings_account_sign_in.visible()
                    settings_account_change_password.gone()
                    settings_account_logged_in_button_space.gone()
                    settings_account_sign_out.gone()
                }

                currentStartDate = state.startDate

                settings_start_date.text = state.startDate.toFullDateString(settings_start_date.context)
                settings_theme.text = state.theme
                settings_lock.text = state.lockMode

                settings_lock_delay_label.isEnabled = state.enableLockDelay
                settings_lock_delay.isEnabled = state.enableLockDelay

                settings_lock_delay.text = state.lockDelay

                settings_app_version.text = state.appVersion

                settings_copyright.text = state.copyright

                if (state.showAds) {
                    settings_ad_layout.visible()
                    settings_ad_view.loadAd()
                } else {
                    settings_ad_layout.gone()
                }
            }
        }
    }

    private fun displayUserDetails(user: SettingsUIUserDetails) {
        settings_account_description.gone()
        settings_account_name_layout.visible()
        settings_account_email_layout.visible()
        settings_account_sign_in.gone()
        settings_account_logged_in_button_space.visible()
        settings_account_logged_in_button_space.visible()
        settings_account_sign_out.visible()

        if (user.email != null) {
            settings_account_change_password.visible()

            val buttonRes = if (user.hasPasswordProvider) R.string.change_password else R.string.set_password
            settings_account_change_password.setText(buttonRes)
        } else {
            settings_account_change_password.gone()
        }

        settings_account_name.text = user.name ?: getString(R.string.unknown)
        settings_account_email.text = user.email ?: getString(R.string.unknown)
    }
}
