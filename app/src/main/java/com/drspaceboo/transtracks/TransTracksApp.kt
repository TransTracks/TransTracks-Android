/*
 * Copyright © 2018-2023 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks

import android.annotation.SuppressLint
import android.app.Application
import com.drspaceboo.transtracks.domain.DomainManager
import com.drspaceboo.transtracks.util.FileUtil
import com.drspaceboo.transtracks.util.settings.FirebaseSettingUtil
import com.drspaceboo.transtracks.util.settings.LockDelay
import com.drspaceboo.transtracks.util.settings.LockType
import com.drspaceboo.transtracks.util.settings.PrefUtil
import com.drspaceboo.transtracks.util.settings.SettingsManager
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key
import com.drspaceboo.transtracks.util.settings.Theme
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics
import io.reactivex.rxjava3.subjects.BehaviorSubject

class TransTracksApp : Application() {
    val domainManager = DomainManager()

    val adConsentStatus = BehaviorSubject.createDefault(ConsentStatus.UNKNOWN)

    val firebaseSettingUtil: FirebaseSettingUtil by lazy(LazyThreadSafetyMode.NONE) {
        FirebaseSettingUtil()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        MobileAds.initialize(this)

        appVersionUpdateIfNecessary()

        FileUtil.clearTempFolder()

        SettingsManager.startFirbaseSyncIfLoggedIn(this)

        //Clearing these, as we don't want to maintain this state across launches
        PrefUtil.setSelectPhotoFirstVisible("")
        PrefUtil.clearAllAlbumFirstVisiblePrefs()
    }

    companion object {
        lateinit var instance: TransTracksApp
            private set

        @SuppressLint("ApplySharedPref") //Share pref changes we want to block on
        fun appVersionUpdateIfNecessary() {
            val currentVersion = SettingsManager.getCurrentAndroidVersion()
            val newVersion = BuildConfig.VERSION_CODE

            if (currentVersion == newVersion) return

            if (currentVersion == null) {
                //User's first tracked version

                val prefs = PrefUtil.getDefaultPrefs()
                val allPrefs = prefs.all

                //Update how we are storing LockDelay, LockType, StartDate, and Theme

                val lockDelay = allPrefs[Key.lockDelay.name]
                if (lockDelay != null) {
                    when (lockDelay) {
                        is Int -> {
                            val newLockDelay: LockDelay = when (lockDelay) {
                                0 -> LockDelay.instant
                                1 -> LockDelay.oneMinute
                                2 -> LockDelay.twoMinutes
                                3 -> LockDelay.fiveMinutes
                                4 -> LockDelay.fifteenMinutes
                                else -> LockDelay.default()
                            }

                            prefs.edit()
                                .remove(Key.lockDelay.name)
                                .putString(Key.lockDelay.name, newLockDelay.name)
                                .commit()
                        }

                        is String -> {
                            //No-op
                        }

                        else -> prefs.edit().remove(Key.lockDelay.name).commit()
                    }
                }

                val lockType = allPrefs[Key.lockType.name]
                if (lockType != null) {
                    when (lockType) {
                        is Int -> {
                            val newLockType: LockType = when (lockType) {
                                0 -> LockType.off
                                1 -> LockType.normal
                                2 -> LockType.trains
                                else -> LockType.default()
                            }

                            prefs.edit()
                                .remove(Key.lockType.name)
                                .putString(Key.lockType.name, newLockType.name)
                                .commit()
                        }

                        is String -> {
                            //No-op
                        }

                        else -> prefs.edit().remove(Key.lockType.name).commit()
                    }
                }

                val startDate = allPrefs[Key.startDate.name]
                if (startDate != null) {
                    when (startDate) {
                        is String -> {
                            val startDateLong = startDate.toLongOrNull()

                            prefs.edit()
                                .remove(Key.startDate.name)
                                .apply {
                                    if (startDateLong != null) {
                                        putLong(Key.startDate.name, startDateLong)
                                    }
                                }
                                .commit()
                        }

                        is Long -> {
                            //No-op
                        }

                        else -> prefs.edit().remove(Key.startDate.name).commit()
                    }

                    val startDateLong = (startDate as? String)?.toLongOrNull()

                    prefs.edit()
                        .remove(Key.startDate.name)
                        .apply {
                            if (startDateLong != null) {
                                putLong(Key.startDate.name, startDateLong)
                            }
                        }
                        .commit()
                }

                val theme = allPrefs[Key.theme.name]
                if (theme != null) {
                    when (theme) {
                        is Int -> {
                            val newTheme: Theme = when (theme) {
                                0 -> Theme.pink
                                1 -> Theme.blue
                                2 -> Theme.purple
                                3 -> Theme.green
                                else -> Theme.default()
                            }

                            prefs.edit()
                                .remove(Key.theme.name)
                                .putString(Key.theme.name, newTheme.name)
                                .commit()
                        }

                        is String -> {
                            //No-op
                        }

                        else -> prefs.edit().remove(Key.theme.name).commit()
                    }
                }

                //Untracked user that has a lock at this point, we should warn them about not having an account
                if (SettingsManager.getLockType() != LockType.off) {
                    FirebaseAnalytics.getInstance(instance)
                        .logEvent("user_needs_to_show_warning", null)
                    SettingsManager.setAccountWarning(true, context = null)
                }
            }

            SettingsManager.updateCurrentAndroidVersion()
        }

        fun hasConsentToShowAds(): Boolean =
            listOf(ConsentStatus.NOT_REQUIRED, ConsentStatus.OBTAINED)
                .contains(instance.adConsentStatus.value)
    }
}
