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

import android.preference.PreferenceManager
import android.support.annotation.IntDef
import com.drspaceboo.transtracks.TransTracksApp
import com.f2prateek.rx.preferences2.LocalDateConverter
import com.f2prateek.rx.preferences2.Preference
import com.f2prateek.rx.preferences2.RxSharedPreferences
import org.threeten.bp.LocalDate

object PrefUtil {
    private const val KEY_SHOW_ADS = "showAds"
    private const val KEY_SHOW_WELCOME = "showWelcome"
    private const val KEY_START_DATE = "startDate"
    private const val KEY_THEME = "theme"

    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    @IntDef(THEME_PINK, THEME_BLUE)
    annotation class Theme

    const val THEME_PINK = 0
    const val THEME_BLUE = 1

    private val rxPreferences: RxSharedPreferences =
            RxSharedPreferences.create(PreferenceManager.getDefaultSharedPreferences(TransTracksApp.instance))

    val showAds: Preference<Boolean> = rxPreferences.getBoolean(KEY_SHOW_ADS, true)

    val showWelcome: Preference<Boolean> = rxPreferences.getBoolean(KEY_SHOW_WELCOME, true)

    val startDate: Preference<LocalDate> = rxPreferences.getObject(KEY_START_DATE, LocalDate.now(),
            LocalDateConverter())

    val theme: Preference<Int> = rxPreferences.getInteger(KEY_THEME, THEME_PINK)
}
