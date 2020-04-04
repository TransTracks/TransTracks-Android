/*
 * Copyright © 2019 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.domain

import com.drspaceboo.transtracks.domain.SettingsAction.SettingsUpdated
import com.drspaceboo.transtracks.ui.settings.SettingsUIUserDetails
import com.drspaceboo.transtracks.util.RxSchedulers
import com.drspaceboo.transtracks.util.hasPasswordProvider
import com.drspaceboo.transtracks.util.settings.LockDelay
import com.drspaceboo.transtracks.util.settings.LockType
import com.drspaceboo.transtracks.util.settings.SettingsManager
import com.drspaceboo.transtracks.util.settings.Theme
import com.google.firebase.auth.FirebaseAuth
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import org.threeten.bp.LocalDate

sealed class SettingsAction {
    object SettingsUpdated : SettingsAction()
}

sealed class SettingsResult {
    data class Content(
        val userDetails: SettingsUIUserDetails?, val startDate: LocalDate, val theme: Theme, val lockType: LockType,
        val lockDelay: LockDelay
    ) : SettingsResult()
}

class SettingsDomain {
    private val settingsUpdatedActions: Observable<SettingsUpdated> =
        SettingsManager.userSettingsUpdated.map { SettingsUpdated }

    val actions: PublishRelay<SettingsAction> = PublishRelay.create()
    private val mergedActions: Observable<SettingsAction> = Observable.merge(actions, settingsUpdatedActions)

    val results: Observable<SettingsResult> = mergedActions
        .startWith(SettingsUpdated)
        .compose(settingsActionsToResults())
        .subscribeOn(RxSchedulers.io())
        .observeOn(RxSchedulers.main())
        .replay(1)
        .refCount()

    companion object {
        private fun settingsActionsToResults() = ObservableTransformer<SettingsAction, SettingsResult> { actions ->
            actions.map { action ->
                return@map when (action) {
                    SettingsUpdated -> {
                        val userDetails = FirebaseAuth.getInstance().currentUser?.let {
                            SettingsUIUserDetails(it.displayName, it.email, it.hasPasswordProvider())
                        }

                        SettingsResult.Content(
                            userDetails, SettingsManager.getStartDate(context = null), SettingsManager.getTheme(),
                            SettingsManager.getLockType(), SettingsManager.getLockDelay()
                        )
                    }
                }
            }
        }
    }
}
