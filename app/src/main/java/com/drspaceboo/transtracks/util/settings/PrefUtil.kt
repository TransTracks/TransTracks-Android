/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.util.settings

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.drspaceboo.transtracks.BuildConfig
import com.drspaceboo.transtracks.TransTracksApp
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key
import com.f2prateek.rx.preferences2.LocalDateConverter
import com.f2prateek.rx.preferences2.Preference
import com.f2prateek.rx.preferences2.RxSharedPreferences
import org.threeten.bp.LocalDate

object PrefUtil {
    //region Constants
    const val CODE_SALT = BuildConfig.CODE_SALT
    //endregion

    //region Default Prefs
    fun getDefaultPrefs(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(TransTracksApp.instance)

    fun getBoolean(key: Key, default: Boolean): Boolean = getDefaultPrefs().getBoolean(key.name, default)

    fun setBoolean(key: Key, value: Boolean) = getDefaultPrefs().edit().putBoolean(key.name, value).apply()

    inline fun <reified T : Enum<T>> getEnum(key: Key, default: T): T = getString(key, default.name)?.let {
        return@let try {
            enumValueOf<T>(it)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            default
        }
    } ?: default

    fun <T : Enum<T>> setEnum(key: Key, value: T) = setString(key, value.name)

    fun getInt(key: Key, default: Int?): Int? = getDefaultPrefs().let { prefs ->
        if (prefs.contains(key.name)) prefs.getInt(key.name, -1) else default
    }

    fun setInt(key: Key, value: Int) = getDefaultPrefs().edit().putInt(key.name, value).apply()

    fun getString(key: Key, default: String?): String? = getDefaultPrefs().getString(key.name, default)

    fun setString(key: Key, value: String) = getDefaultPrefs().edit().putString(key.name, value).apply()
    //endregion

    //region AlbumFirstVisible
    private fun getAlbumFirstVisiblePrefs(): SharedPreferences =
        TransTracksApp.instance.getSharedPreferences("albumFirstVisible", Context.MODE_PRIVATE)

    fun clearAllAlbumFirstVisiblePrefs() {
        getAlbumFirstVisiblePrefs().edit().clear().apply()
    }

    fun setAlbumFirstVisible(bucketId: String, uri: String?) {
        getAlbumFirstVisiblePrefs().edit().putString(bucketId, uri).apply()
    }

    fun getAlbumFirstVisible(bucketId: String): String? = getAlbumFirstVisiblePrefs().getString(bucketId, null)
    //endregion


    private const val KEY_LOCK_CODE = "lockCode"
    private const val KEY_SELECT_PHOTO_FIRST_VISIBLE = "selectPhotoFirstVisible"
    private const val KEY_SHOW_ADS = "showAds"
    private const val KEY_SHOW_WELCOME = "showWelcome"
    private const val KEY_START_DATE = "startDate"
    private const val KEY_USER_LAST_SEEN = "userLastSeen"

    private val rxPreferences: RxSharedPreferences by lazy(LazyThreadSafetyMode.NONE) {
        RxSharedPreferences.create(getDefaultPrefs())
    }

    val lockCode: Preference<String> by lazy(LazyThreadSafetyMode.NONE) {
        rxPreferences.getString(KEY_LOCK_CODE, "")
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

    val userLastSeen: Preference<Long> by lazy(LazyThreadSafetyMode.NONE) {
        rxPreferences.getLong(KEY_USER_LAST_SEEN, 0)
    }
}
