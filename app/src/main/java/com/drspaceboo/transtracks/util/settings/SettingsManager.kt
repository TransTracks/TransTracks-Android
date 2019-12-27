/*
 * Copyright © 2019 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.util.settings

import androidx.annotation.StyleRes
import com.drspaceboo.transtracks.BuildConfig
import com.drspaceboo.transtracks.R
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import org.threeten.bp.LocalDate

object SettingsManager {
    private val lockTypeUpdatedRelay: PublishRelay<LockType> = PublishRelay.create()
    val lockTypeUpdated: Observable<LockType> = lockTypeUpdatedRelay

    private val themeUpdatedRelay: PublishRelay<Theme> = PublishRelay.create()
    val themeUpdated: Observable<Theme> = themeUpdatedRelay

    private val userSettingsUpdatedRelay: PublishRelay<Unit> = PublishRelay.create()
    val userSettingsUpdated: Observable<Any> by lazy {
        Observable.merge(userSettingsUpdatedRelay, lockTypeUpdated, themeUpdated)
    }

    //region Current Android Version
    fun getCurrentAndroidVersion(): Int? = PrefUtil.getInt(Key.currentAndroidVersion, null)

    fun updateCurrentAndroidVersion() = PrefUtil.setInt(Key.currentAndroidVersion, BuildConfig.VERSION_CODE)
    //endregion

    //region Incorrect Password Count
    fun getIncorrectPasswordCount(): Int = PrefUtil.getInt(Key.incorrectPasswordCount, 0)!!

    fun incrementIncorrectPasswordCount() = PrefUtil.setInt(Key.incorrectPasswordCount, getIncorrectPasswordCount() + 1)

    fun resetIncorrectPasswordCount() = PrefUtil.setInt(Key.incorrectPasswordCount, 0)
    //endregion

    //region Lock Code
    fun getLockCode(): String = PrefUtil.getString(Key.lockCode, "")!!

    fun setLockCode(newLockCode: String) {
        PrefUtil.setString(Key.lockCode, newLockCode)
        userSettingsUpdatedRelay.accept(Unit)
    }
    //endregion

    //region Lock Delay
    fun getLockDelay(): LockDelay = PrefUtil.getEnum(Key.lockDelay, LockDelay.default())

    fun setLockDelay(newLockDelay: LockDelay) {
        PrefUtil.setEnum(Key.lockDelay, newLockDelay)
        userSettingsUpdatedRelay.accept(Unit)
    }
    //endregion

    //region Lock Type
    fun getLockType(): LockType = PrefUtil.getEnum(Key.lockType, LockType.default())

    fun setLockType(newLockType: LockType) {
        PrefUtil.setEnum(Key.lockType, newLockType)
        lockTypeUpdatedRelay.accept(newLockType)
    }
    //endregion

    //region Show Account Warning
    fun showAccountWarning(): Boolean = PrefUtil.getBoolean(Key.showAccountWarning, false)

    fun setAccountWarning(newAccountWarning: Boolean) = PrefUtil.setBoolean(Key.showAccountWarning, newAccountWarning)
    //endregion

    //region Show Ads
    fun showAds(): Boolean = PrefUtil.getBoolean(Key.showAds, true)

    fun setShowAds(newShowAds: Boolean) = PrefUtil.setBoolean(Key.showAds, newShowAds)
    //endregion

    //region Show Welcome
    fun showWelcome(): Boolean = PrefUtil.getBoolean(Key.showWelcome, true)

    fun setShowWelcome(newShowWelcome: Boolean) = PrefUtil.setBoolean(Key.showWelcome, newShowWelcome)
    //endregion

    //region Start Date
    fun getStartDate(): LocalDate {
        val startDate = PrefUtil.getDate(Key.startDate)

        @Suppress("LiftReturnOrAssignment") // Reads better in the if statement
        if (startDate != null) {
            return startDate
        } else {
            val newStartDate = LocalDate.now()
            setStartDate(newStartDate)
            return newStartDate
        }
    }

    fun setStartDate(newStartDate: LocalDate) {
        PrefUtil.setDate(Key.startDate, newStartDate)
        userSettingsUpdatedRelay.accept(Unit)
    }
    //endregion

    //region Theme
    fun getTheme(): Theme = PrefUtil.getEnum(Key.theme, Theme.default())

    fun setTheme(newTheme: Theme) {
        PrefUtil.setEnum(Key.theme, newTheme)
        themeUpdatedRelay.accept(newTheme)
    }
    //endregion

    //region User last seen
    fun getUserLastSeen(): Long = PrefUtil.getLong(Key.userLastSeen, System.currentTimeMillis())!!

    fun updateUserLastSeen() = PrefUtil.setLong(Key.userLastSeen, System.currentTimeMillis())
    //endregion

    @Suppress("EnumEntryName") //These don't follow standard naming convention to match across platforms
    enum class Key {
        currentAndroidVersion,
        incorrectPasswordCount,
        lockCode,
        lockDelay,
        lockType,
        saveToFirebase,
        showAccountWarning,
        showAds,
        showWelcome,
        startDate,
        theme,
        userLastSeen
    }
}

@Suppress("EnumEntryName") //These don't follow standard naming convention to match across platforms
enum class LockDelay {
    instant, oneMinute, twoMinutes, fiveMinutes, fifteenMinutes;

    fun getMilli(): Long {
        val delayMinutes = when (this) {
            oneMinute -> 1L
            twoMinutes -> 2L
            fiveMinutes -> 5L
            fifteenMinutes -> 15L
            else -> 0L
        }

        return 1000L * 60L * delayMinutes
    }

    companion object {
        fun default() = instant
    }
}

@Suppress("EnumEntryName") //These don't follow standard naming convention to match across platforms
enum class LockType {
    off, normal, trains;

    companion object {
        fun default() = off
    }
}

@Suppress("EnumEntryName") //These don't follow standard naming convention to match across platforms
enum class Theme {
    pink, blue, purple, green;

    @StyleRes
    fun styleRes() = when (this) {
        pink -> R.style.PinkAppTheme
        blue -> R.style.BlueAppTheme
        purple -> R.style.PurpleAppTheme
        green -> R.style.GreenAppTheme
    }

    companion object {
        fun default() = pink
    }
}

class DocumentDoesNotExistException : Exception()
class UserNotLoggedInException : Exception()

