/*
 * Copyright © 2018-2023 TransTracks. All rights reserved.
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
import com.drspaceboo.transtracks.TransTracksApp
import com.drspaceboo.transtracks.databinding.SettingsBinding
import com.drspaceboo.transtracks.ui.settings.SettingsUiState.Content
import com.drspaceboo.transtracks.ui.settings.SettingsUiState.Loading
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.gone
import com.drspaceboo.transtracks.util.loadAd
import com.drspaceboo.transtracks.util.setVisibleOrGone
import com.drspaceboo.transtracks.util.toFullDateString
import com.drspaceboo.transtracks.util.toV3
import com.drspaceboo.transtracks.util.visible
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.jakewharton.rxbinding3.appcompat.navigationClicks
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.checkedChanges
import io.reactivex.rxjava3.core.Observable
import java.time.LocalDate

sealed class SettingsUiEvent {
    object Back : SettingsUiEvent()
    object ChangeName : SettingsUiEvent()
    object ChangeEmail : SettingsUiEvent()
    object SignIn : SettingsUiEvent()
    object ChangePassword : SettingsUiEvent()
    object DeleteAccount : SettingsUiEvent()
    object SignOut : SettingsUiEvent()
    object ChangeStartDate : SettingsUiEvent()
    object ChangeTheme : SettingsUiEvent()
    object ChangeLockMode : SettingsUiEvent()
    object ChangeLockDelay : SettingsUiEvent()
    object Import : SettingsUiEvent()
    object Export : SettingsUiEvent()
    object ToggleAnalytics : SettingsUiEvent()
    object ToggleCrashReports : SettingsUiEvent()
    object ToggleAds : SettingsUiEvent()
    object ShowAdConsent : SettingsUiEvent()
    object Contribute : SettingsUiEvent()
    object PrivacyPolicy : SettingsUiEvent()
}

data class SettingsUIUserDetails(
    val name: String?, val email: String?, val hasPasswordProvider: Boolean
)

sealed class SettingsUiState {
    data class Content(
        val userDetails: SettingsUIUserDetails?, val startDate: LocalDate, val theme: String,
        val lockMode: String, val enableLockDelay: Boolean, val lockDelay: String,
        val appVersion: String, val copyright: String, val showAds: Boolean,
        val hasAdConsent: Boolean, val enableAnalytics: Boolean, val enableCrashReports: Boolean
    ) : SettingsUiState()

    data class Loading(val content: Content, val overallProgress: Int, val stepProgress: Int) :
        SettingsUiState()
}

class SettingsView(context: Context, attributeSet: AttributeSet) :
    ConstraintLayout(context, attributeSet) {
    private lateinit var binding: SettingsBinding

    val events: Observable<SettingsUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.mergeArray(
            binding.settingsToolbar.navigationClicks().toV3().map { SettingsUiEvent.Back },
            binding.settingsAccountName.clicks().toV3().map { SettingsUiEvent.ChangeName },
            binding.settingsAccountEmail.clicks().toV3().map { SettingsUiEvent.ChangeEmail },
            binding.settingsAccountSignIn.clicks().toV3().map { SettingsUiEvent.SignIn },
            binding.settingsAccountChangePassword.clicks().toV3()
                .map { SettingsUiEvent.ChangePassword },
            binding.settingsAccountDeleteAccount.clicks().toV3()
                .map { SettingsUiEvent.DeleteAccount },
            binding.settingsAccountSignOut.clicks().toV3().map { SettingsUiEvent.SignOut },
            binding.settingsStartDate.clicks().toV3().map { SettingsUiEvent.ChangeStartDate },
            binding.settingsTheme.clicks().toV3().map { SettingsUiEvent.ChangeTheme },
            binding.settingsLock.clicks().toV3().map { SettingsUiEvent.ChangeLockMode },
            binding.settingsLockDelay.clicks().toV3().map { SettingsUiEvent.ChangeLockDelay },
            binding.settingsImport.clicks().toV3().map { SettingsUiEvent.Import },
            binding.settingsExport.clicks().toV3().map { SettingsUiEvent.Export },
            binding.settingsAnalytics.checkedChanges().toV3()
                .filter { userAction }.map { SettingsUiEvent.ToggleAnalytics },
            binding.settingsCrashReports.checkedChanges().toV3()
                .filter { userAction }.map { SettingsUiEvent.ToggleCrashReports },
            binding.settingsShowAds.checkedChanges().toV3()
                .filter { userAction }.map { SettingsUiEvent.ToggleAds },
            binding.settingsAdConsentShow.clicks().toV3().map { SettingsUiEvent.ShowAdConsent },
            binding.settingsContribute.clicks().toV3().map { SettingsUiEvent.Contribute },
            binding.settingsPrivacyPolicy.clicks().toV3().map { SettingsUiEvent.PrivacyPolicy }
        )
    }

    private var currentStartDate: LocalDate? = null

    private var userAction = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = SettingsBinding.bind(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        binding.settingsAccountNameLabel.setOnClickListener { binding.settingsAccountName.performClick() }
        binding.settingsAccountEmailLabel.setOnClickListener { binding.settingsAccountEmail.performClick() }
        binding.settingsStartLabel.setOnClickListener { binding.settingsStartDate.performClick() }
        binding.settingsThemeLabel.setOnClickListener { binding.settingsTheme.performClick() }
        binding.settingsLockLabel.setOnClickListener { binding.settingsLock.performClick() }
        binding.settingsLockDescription.setOnClickListener { binding.settingsLock.performClick() }
        binding.settingsLockDelayLabel.setOnClickListener { binding.settingsLockDelay.performClick() }
    }

    fun display(state: SettingsUiState) {
        userAction = false
        when (state) {
            is Content -> {
                binding.settingsLoadingLayout.gone()
                displayContent(state)
            }

            is Loading -> {
                binding.settingsLoadingLayout.visible()
                binding.settingsLoadingProgress.progress = state.overallProgress
                binding.settingsLoadingProgress.secondaryProgress = state.stepProgress
            }
        }
        userAction = true
    }

    private fun displayContent(content: Content) {
        if (content.userDetails != null) {
            displayUserDetails(content.userDetails)
        } else {
            binding.settingsAccountDescription.visible()
            binding.settingsAccountNameLayout.gone()
            binding.settingsAccountEmailLayout.gone()
            binding.settingsAccountSignIn.visible()
            binding.settingsAccountChangePassword.gone()
            binding.settingsAccountLoggedInButtonSpace1.gone()
            binding.settingsAccountDeleteAccount.gone()
            binding.settingsAccountLoggedInButtonSpace2.gone()
            binding.settingsAccountSignOut.gone()
        }

        currentStartDate = content.startDate

        binding.settingsStartDate.text = content.startDate.toFullDateString(context)
        binding.settingsTheme.text = content.theme
        binding.settingsLock.text = content.lockMode

        binding.settingsLockDelayLabel.isEnabled = content.enableLockDelay
        binding.settingsLockDelay.isEnabled = content.enableLockDelay

        binding.settingsLockDelay.text = content.lockDelay

        binding.settingsAnalytics.isChecked = content.enableAnalytics
        binding.settingsCrashReports.isChecked = content.enableCrashReports
        binding.settingsShowAds.isChecked = content.showAds
        binding.settingsAdLayout.setVisibleOrGone(content.hasAdConsent)
        binding.settingsAdConsentShow.isEnabled = content.showAds

        binding.settingsAppVersion.text = content.appVersion

        binding.settingsCopyright.text = content.copyright

        if (TransTracksApp.hasConsentToShowAds() && content.showAds) {
            binding.settingsAdLayout.visible()

            if (binding.settingsAdLayout.childCount <= 0) {
                AdView(context).apply {
                    adUnitId = getString(R.string.ADS_SETTINGS_AD_ID)
                    binding.settingsAdLayout.addView(this)
                    loadAd(context)
                    adListener = object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            binding.settingsAdLayout.gone()
                        }
                    }
                }
            }
        } else {
            binding.settingsAdLayout.gone()
        }
    }

    private fun displayUserDetails(user: SettingsUIUserDetails) {
        binding.settingsAccountDescription.gone()
        binding.settingsAccountNameLayout.visible()
        binding.settingsAccountEmailLayout.visible()
        binding.settingsAccountSignIn.gone()
        binding.settingsAccountLoggedInButtonSpace1.visible()
        binding.settingsAccountDeleteAccount.visible()
        binding.settingsAccountLoggedInButtonSpace2.visible()
        binding.settingsAccountSignOut.visible()

        if (user.email != null) {
            binding.settingsAccountChangePassword.visible()

            val buttonRes =
                if (user.hasPasswordProvider) R.string.change_password else R.string.set_password
            binding.settingsAccountChangePassword.setText(buttonRes)
        } else {
            binding.settingsAccountChangePassword.gone()
        }

        binding.settingsAccountName.text = user.name ?: getString(R.string.unknown)
        binding.settingsAccountEmail.text = user.email ?: getString(R.string.unknown)
    }
}
