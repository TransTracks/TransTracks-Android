/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.util

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.annotation.IntDef
import com.drspaceboo.transtracks.BuildConfig
import com.drspaceboo.transtracks.TransTracksApp
import com.f2prateek.rx.preferences2.LocalDateConverter
import com.f2prateek.rx.preferences2.Preference
import com.f2prateek.rx.preferences2.RxSharedPreferences
import org.threeten.bp.LocalDate

object PrefUtil {
    private const val KEY_LOCK_CODE = "lockCode"
    private const val KEY_LOCK_DELAY = "lockDelay"
    private const val KEY_LOCK_TYPE = "lockType"
    private const val KEY_SELECT_PHOTO_FIRST_VISIBLE = "selectPhotoFirstVisible"
    private const val KEY_SHOW_ADS = "showAds"
    private const val KEY_SHOW_WELCOME = "showWelcome"
    private const val KEY_START_DATE = "startDate"
    private const val KEY_THEME = "theme"
    private const val KEY_USER_LAST_SEEN = "userLastSeen"

    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    @IntDef(LOCK_OFF, LOCK_NORMAL, LOCK_TRAINS)
    annotation class LockType

    const val LOCK_OFF = 0
    const val LOCK_NORMAL = 1
    const val LOCK_TRAINS = 2

    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    @IntDef(LOCK_DELAY_INSTANT, LOCK_DELAY_ONE_MINUTE, LOCK_DELAY_TWO_MINUTES,
            LOCK_DELAY_FIVE_MINUTES, LOCK_DELAY_FIFTEEN_MINUTES)
    annotation class LockDelay

    const val LOCK_DELAY_INSTANT = 0
    const val LOCK_DELAY_ONE_MINUTE = 1
    const val LOCK_DELAY_TWO_MINUTES = 2
    const val LOCK_DELAY_FIVE_MINUTES = 3
    const val LOCK_DELAY_FIFTEEN_MINUTES = 4

    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    @IntDef(THEME_PINK, THEME_BLUE, THEME_PURPLE, THEME_GREEN)
    annotation class Theme

    const val THEME_PINK = 0
    const val THEME_BLUE = 1
    const val THEME_PURPLE = 2
    const val THEME_GREEN = 3

    private val rxPreferences: RxSharedPreferences by lazy(LazyThreadSafetyMode.NONE) {
        RxSharedPreferences.create(PreferenceManager.getDefaultSharedPreferences(TransTracksApp.instance))
    }

    val lockCode: Preference<String> by lazy(LazyThreadSafetyMode.NONE) {
        rxPreferences.getString(KEY_LOCK_CODE, "")
    }

    val lockDelay: Preference<Int> by lazy(LazyThreadSafetyMode.NONE) {
        rxPreferences.getInteger(KEY_LOCK_DELAY, LOCK_DELAY_INSTANT)
    }

    fun getLockDelayMilli(): Long {
        val delayMinutes = when (lockDelay.get()) {
            LOCK_DELAY_ONE_MINUTE -> 1L
            LOCK_DELAY_TWO_MINUTES -> 2L
            LOCK_DELAY_FIVE_MINUTES -> 5L
            LOCK_DELAY_FIFTEEN_MINUTES -> 15L
            else -> 0L
        }

        return 1000L * 60L * delayMinutes
    }

    val lockType: Preference<Int> by lazy(LazyThreadSafetyMode.NONE) {
        rxPreferences.getInteger(KEY_LOCK_TYPE, LOCK_OFF)
    }

    val selectPhotoFirstVisible: Preference<String> by lazy(LazyThreadSafetyMode.NONE) {
        rxPreferences.getString(KEY_SELECT_PHOTO_FIRST_VISIBLE, "")
    }

    val showAds: Preference<Boolean> by lazy(LazyThreadSafetyMode.NONE) {
        rxPreferences.getBoolean(KEY_SHOW_ADS, true)
    }

    val showWelcome: Preference<Boolean> by lazy(LazyThreadSafetyMode.NONE) {
        rxPreferences.getBoolean(KEY_SHOW_WELCOME, true)
    }

    val startDate: Preference<LocalDate> by lazy(LazyThreadSafetyMode.NONE) {
        rxPreferences.getObject(KEY_START_DATE, LocalDate.now(), LocalDateConverter())
    }

    val theme: Preference<Int> by lazy(LazyThreadSafetyMode.NONE) {
        rxPreferences.getInteger(KEY_THEME, THEME_PINK)
    }

    val userLastSeen: Preference<Long> by lazy(LazyThreadSafetyMode.NONE) {
        rxPreferences.getLong(KEY_USER_LAST_SEEN, 0)
    }

    private fun getAlbumFirstVisiblePrefs(): SharedPreferences {
        return TransTracksApp.instance.getSharedPreferences("albumFirstVisible",
                                                            Context.MODE_PRIVATE)
    }

    fun clearAllAlbumFirstVisiblePrefs() {
        getAlbumFirstVisiblePrefs().edit().clear().apply()
    }

    fun setAlbumFirstVisible(bucketId: String, uri: String?) {
        getAlbumFirstVisiblePrefs().edit().putString(bucketId, uri).apply()
    }

    fun getAlbumFirstVisible(bucketId: String): String? {
        return getAlbumFirstVisiblePrefs().getString(bucketId, null)
    }

    const val CODE_SALT = BuildConfig.CODE_SALT
}
