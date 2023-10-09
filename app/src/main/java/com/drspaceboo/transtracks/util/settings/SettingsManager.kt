/*
 * Copyright © 2019-2022 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.util.settings

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.drspaceboo.transtracks.BuildConfig
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.TransTracksApp
import com.drspaceboo.transtracks.ui.settings.SettingsConflictDialog
import com.drspaceboo.transtracks.util.safeValueOf
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key.*
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.DocumentReference
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import com.jakewharton.rxrelay3.PublishRelay
import io.reactivex.rxjava3.core.Observable
import java.io.IOException
import java.time.LocalDate

object SettingsManager {
    val lockTypeUpdatedRelay: PublishRelay<LockType> = PublishRelay.create()
    val lockTypeUpdated: Observable<LockType> = lockTypeUpdatedRelay

    val themeUpdatedRelay: PublishRelay<Theme> = PublishRelay.create()
    val themeUpdated: Observable<Theme> = themeUpdatedRelay

    val userSettingsUpdatedRelay: PublishRelay<Unit> = PublishRelay.create()
    val userSettingsUpdated: Observable<Any> by lazy {
        Observable.merge(userSettingsUpdatedRelay, lockTypeUpdated, themeUpdated)
    }

    //region Current Android Version
    fun getCurrentAndroidVersion(): Int? = PrefUtil.getInt(currentAndroidVersion, null)

    fun updateCurrentAndroidVersion() =
        PrefUtil.setInt(currentAndroidVersion, BuildConfig.VERSION_CODE)
    //endregion

    //region Incorrect Password Count
    fun getIncorrectPasswordCount(): Int = PrefUtil.getInt(incorrectPasswordCount, 0)!!

    fun incrementIncorrectPasswordCount(activity: Activity) {
        val newCount = getIncorrectPasswordCount() + 1
        PrefUtil.setInt(incorrectPasswordCount, newCount)

        if (saveToFirebase()) {
            FirebaseSettingUtil.setInt(incorrectPasswordCount, newCount, activity)
        }
    }

    fun resetIncorrectPasswordCount(activity: Activity) {
        PrefUtil.setInt(incorrectPasswordCount, 0)

        if (saveToFirebase()) {
            FirebaseSettingUtil.setInt(incorrectPasswordCount, 0, activity)
        }
    }
    //endregion

    //region Lock Code
    fun getLockCode(): String = PrefUtil.getString(lockCode, "")!!

    fun setLockCode(newLockCode: String, activity: Activity) {
        PrefUtil.setString(lockCode, newLockCode)
        userSettingsUpdatedRelay.accept(Unit)

        if (saveToFirebase()) {
            FirebaseSettingUtil.setString(lockCode, newLockCode, activity)
        }
    }
    //endregion

    //region Lock Delay
    fun getLockDelay(): LockDelay = PrefUtil.getEnum(lockDelay, LockDelay.default())

    fun setLockDelay(newLockDelay: LockDelay, activity: Activity) {
        PrefUtil.setEnum(lockDelay, newLockDelay)
        userSettingsUpdatedRelay.accept(Unit)

        if (saveToFirebase()) {
            FirebaseSettingUtil.setEnum(lockDelay, newLockDelay, activity)
        }
    }
    //endregion

    //region Lock Type
    fun getLockType(): LockType = PrefUtil.getEnum(lockType, LockType.default())

    fun setLockType(newLockType: LockType, activity: Activity) {
        PrefUtil.setEnum(lockType, newLockType)
        lockTypeUpdatedRelay.accept(newLockType)

        if (saveToFirebase()) {
            FirebaseSettingUtil.setEnum(lockType, newLockType, activity)
        }
    }
    //endregion

    //region Show Account Warning
    fun showAccountWarning(): Boolean = PrefUtil.getBoolean(showAccountWarning, false)

    fun setAccountWarning(newAccountWarning: Boolean, context: Context?) {
        PrefUtil.setBoolean(showAccountWarning, newAccountWarning)

        if (saveToFirebase()) {
            FirebaseSettingUtil.setBool(showAccountWarning, newAccountWarning, context)
        }
    }
    //endregion

    //region Show Ads
    fun showAds(): Boolean = PrefUtil.getBoolean(showAds, true)

    fun toggleShowAds(context: Context?) {
        val newShowAds = !showAds()
        PrefUtil.setBoolean(showAds, newShowAds)
        userSettingsUpdatedRelay.accept(Unit)

        if (saveToFirebase()) {
            FirebaseSettingUtil.setBool(showAds, newShowAds, context)
        }
    }
    //endregion

    //region Show Welcome
    fun showWelcome(): Boolean = PrefUtil.getBoolean(showWelcome, true)

    fun setShowWelcome(newShowWelcome: Boolean, activity: Activity) {
        PrefUtil.setBoolean(showWelcome, newShowWelcome)

        if (saveToFirebase()) {
            FirebaseSettingUtil.setBool(showWelcome, newShowWelcome, activity)
        }
    }
    //endregion

    //region Start Date
    fun getStartDate(context: Context?): LocalDate {
        val startDate = PrefUtil.getDate(startDate)

        @Suppress("LiftReturnOrAssignment") // Reads better in the if statement
        if (startDate != null) {
            return startDate
        } else {
            val newStartDate = LocalDate.now()
            setStartDate(newStartDate, context)
            return newStartDate
        }
    }

    fun setStartDate(newStartDate: LocalDate, context: Context?) {
        PrefUtil.setDate(startDate, newStartDate)
        userSettingsUpdatedRelay.accept(Unit)

        if (saveToFirebase()) {
            FirebaseSettingUtil.setLong(startDate, newStartDate.toEpochDay(), context)
        }
    }
    //endregion

    //region Theme
    fun getTheme(): Theme = PrefUtil.getEnum(theme, Theme.default())

    fun setTheme(newTheme: Theme, context: Context?) {
        PrefUtil.setEnum(theme, newTheme)
        themeUpdatedRelay.accept(newTheme)

        if (saveToFirebase()) {
            FirebaseSettingUtil.setEnum(theme, newTheme, context)
        }
    }
    //endregion

    //region Analytics
    fun getEnableAnalytics(): Boolean = PrefUtil.getBoolean(enableAnalytics, true)

    fun toggleEnableAnalytics(context: Context?) {
        val newEnableAnalytics = !getEnableAnalytics()
        PrefUtil.setBoolean(enableAnalytics, newEnableAnalytics)

        context?.apply {
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(newEnableAnalytics)
        }
        userSettingsUpdatedRelay.accept(Unit)

        if (saveToFirebase()) {
            FirebaseSettingUtil.setBool(enableAnalytics, newEnableAnalytics, context)
        }
    }
    //endregion

    //region Crash Reports
    fun getEnableCrashReports(): Boolean = PrefUtil.getBoolean(enableCrashReports, true)

    fun toggleEnableCrashReports(context: Context?) {
        val newEnableCrashReports = !getEnableCrashReports()
        PrefUtil.setBoolean(enableCrashReports, newEnableCrashReports)

        context?.apply {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(newEnableCrashReports)
        }
        userSettingsUpdatedRelay.accept(Unit)

        if (saveToFirebase()) {
            FirebaseSettingUtil.setBool(enableCrashReports, newEnableCrashReports, context)
        }
    }
    //endregion

    //region User last seen
    fun getUserLastSeen(): Long = PrefUtil.getLong(userLastSeen, System.currentTimeMillis())!!

    fun updateUserLastSeen() = PrefUtil.setLong(userLastSeen, System.currentTimeMillis())
    //endregion

    //region Json Exporting/Importing
    fun getSettingsAsJson() = JsonObject().apply {
        addProperty(currentAndroidVersion.name, getCurrentAndroidVersion())
        addProperty(startDate.name, PrefUtil.getDate(startDate)?.toEpochDay())
        addProperty(theme.name, getTheme().name)
    }

    @Throws(IOException::class)
    fun getSettingsFromJson(jsonReader: JsonReader) {
        while (jsonReader.hasNext()) {
            when (jsonReader.nextName()) {
                currentAndroidVersion.name -> {
                    //No-op, this is here in-case we need to handle incompatibility in the future
                    jsonReader.skipValue()
                }

                startDate.name -> {
                    try {
                        setStartDate(LocalDate.ofEpochDay(jsonReader.nextLong()), context = null)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }

                theme.name -> {
                    try {
                        setTheme(Theme.valueOf(jsonReader.nextString()), context = null)
                    } catch (e: java.lang.Exception) {
                        e.printStackTrace()
                    }
                }

                else -> jsonReader.skipValue()
            }
        }
    }
    //endregion

    //region Firebase handling
    fun startFirbaseSyncIfLoggedIn(context: Context) {
        if (FirebaseAuth.getInstance().currentUser != null) {
            enableFirebaseSync()
        }
    }

    fun enableFirebaseSync() {
        PrefUtil.setBoolean(saveToFirebase, true)
        TransTracksApp.instance.firebaseSettingUtil.addListener()
    }

    fun disableFirebaseSync() {
        PrefUtil.setBoolean(saveToFirebase, false)
        TransTracksApp.instance.firebaseSettingUtil.removeListener()
    }

    fun firebaseNeedsSetup(context: Context?) {
        disableFirebaseSync()

        if (context != null && FirebaseAuth.getInstance().currentUser != null) {
            attemptFirebaseAutoSetup(context)
        }
    }

    fun attemptFirebaseAutoSetup(context: Context) {
        try {
            val docRef = FirebaseSettingUtil.getSettingsDocRef()

            docRef.get().addOnSuccessListener { document ->
                val data = document.data
                if (document.exists() && data != null) {
                    val difference = data.filter { (keyString, value) ->
                        val key = safeValueOf<Key>(keyString) ?: return@filter false
                        return@filter when (key) {
                            lockCode -> value is String && value != getLockCode()

                            lockDelay -> value is String
                                    && safeValueOf<LockDelay>(value) != null
                                    && value != getLockDelay().name

                            lockType -> value is String
                                    && safeValueOf<LockType>(value) != null
                                    && value != getLockType().name

                            startDate -> value is Long && value != getStartDate(context).toEpochDay()

                            theme -> value is String
                                    && safeValueOf<Theme>(value) != null
                                    && value != getTheme().name

                            enableAnalytics -> value is Boolean && value != getEnableAnalytics()

                            enableCrashReports -> value is Boolean && value != getEnableCrashReports()

                            showAds -> value is Boolean && value != showAds()

                            saveToFirebase, showAccountWarning, showWelcome, userLastSeen,
                            currentAndroidVersion, incorrectPasswordCount -> false
                        }
                    }.map { (key, value) -> Key.valueOf(key) to value }

                    if (difference.isEmpty()) {
                        setFirebaseDocument(docRef, context)
                    } else {
                        SettingsConflictDialog.create(difference, context).show()
                    }
                } else {
                    setFirebaseDocument(docRef, context)
                }
            }
        } catch (e: UserNotLoggedInException) {
            //Looks like we called this function at the wrong time, turn of the Firebase saving
            disableFirebaseSync()
        }
    }

    private fun setFirebaseDocument(docRef: DocumentReference, context: Context) {
        val data: HashMap<String, Any> = HashMap()
        values().forEach { key ->
            firebaseValueForKey(key, context)?.let { value -> data[key.name] = value }
        }
        docRef.set(data)
        enableFirebaseSync()
    }

    fun firebaseValueForKey(key: Key, context: Context): Any? = when (key) {
        lockCode -> getLockCode()
        lockDelay -> getLockDelay().name
        lockType -> getLockType().name
        showAds -> showAds()
        showWelcome -> showWelcome()
        startDate -> getStartDate(context).toEpochDay()
        theme -> getTheme().name
        enableAnalytics -> getEnableAnalytics()
        enableCrashReports -> getEnableCrashReports()

        currentAndroidVersion, incorrectPasswordCount, saveToFirebase, showAccountWarning,
        userLastSeen -> null
    }

    fun saveToFirebase(): Boolean = PrefUtil.getBoolean(saveToFirebase, false)
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
        userLastSeen,
        enableAnalytics,
        enableCrashReports
    }
}

@Suppress("EnumEntryName") //These don't follow standard naming convention to match across platforms
enum class LockDelay {
    instant, oneMinute, twoMinutes, fiveMinutes, fifteenMinutes;

    @StringRes
    fun displayNameRes() = when (this) {
        instant -> R.string.instant
        oneMinute -> R.string.one_minute
        twoMinutes -> R.string.two_minutes
        fiveMinutes -> R.string.five_minutes
        fifteenMinutes -> R.string.fifteen_minutes
    }

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

    @StringRes
    fun displayNameRes() = when (this) {
        off -> R.string.disabled
        normal -> R.string.enabled_normal
        trains -> R.string.enabled_trains
    }

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

    @StringRes
    fun displayNameRes() = when (this) {
        pink -> R.string.pink
        blue -> R.string.blue
        purple -> R.string.purple
        green -> R.string.green
    }

    companion object {
        fun default() = pink
    }
}

class UserNotLoggedInException : Exception()

