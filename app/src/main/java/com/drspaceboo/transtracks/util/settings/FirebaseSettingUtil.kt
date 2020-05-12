/*
 * Copyright © 2020 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.util.settings

import android.content.Context
import android.util.Log
import com.drspaceboo.transtracks.util.safeValueOf
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key.currentAndroidVersion
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key.incorrectPasswordCount
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key.lockCode
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key.lockDelay
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key.lockType
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key.saveToFirebase
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key.showAccountWarning
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key.showAds
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key.showWelcome
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key.startDate
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key.theme
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key.userLastSeen
import com.google.android.gms.tasks.OnFailureListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import java.lang.ref.WeakReference

class FirebaseSettingUtil {
    class FailureListener(context: Context?) : OnFailureListener {
        private val contextRef: WeakReference<Context>? = context?.let { WeakReference(context) }

        override fun onFailure(exception: java.lang.Exception) {
            if (exception !is FirebaseFirestoreException) return

            if (exception.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                SettingsManager.firebaseNeedsSetup(contextRef?.get())
            }
        }
    }

    companion object {
        private const val SETTINGS_DOCUMENT = "settings"
        private val LOG_TAG: String = FirebaseSettingUtil::class.java.simpleName


        fun setBool(key: Key, value: Boolean, context: Context?) {
            try {
                getSettingsDocRef().update(mapOf(key.name to value)).addOnFailureListener(FailureListener(context))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun <T : Enum<T>> setEnum(key: Key, value: T, context: Context?) {
            try {
                getSettingsDocRef().update(mapOf(key.name to value.name))
                    .addOnFailureListener(FailureListener(context))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun setLong(key: Key, value: Long, context: Context?) {
            try {
                getSettingsDocRef().update(mapOf(key.name to value)).addOnFailureListener(FailureListener(context))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun setString(key: Key, value: String, context: Context) {
            try {
                getSettingsDocRef().update(mapOf(key.name to value)).addOnFailureListener(FailureListener(context))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @Throws(UserNotLoggedInException::class)
        fun getSettingsDocRef(): DocumentReference {
            val user = FirebaseAuth.getInstance().currentUser ?: throw UserNotLoggedInException()

            return FirebaseFirestore.getInstance().collection(user.uid).document(SETTINGS_DOCUMENT)
        }
    }

    private val db = FirebaseFirestore.getInstance()

    private var listener: ListenerRegistration? = null

    fun addListener() {
        removeListener()

        val user = FirebaseAuth.getInstance().currentUser ?: return

        listener = db.collection(user.uid).document(SETTINGS_DOCUMENT).addSnapshotListener { snapshot, error ->
            error?.printStackTrace()

            snapshot?.data?.let { data ->
                data.forEach { (keyString, value) ->
                    val key = safeValueOf<Key>(keyString) ?: return@forEach
                    when (key) {
                        lockCode -> when (value) {
                            is String -> {
                                PrefUtil.setString(key, value)
                                SettingsManager.userSettingsUpdatedRelay.accept(Unit)
                            }
                            else -> Log.d(LOG_TAG, "${key.name} is not a String : '$value'")
                        }

                        lockDelay -> when (value) {
                            is String -> {
                                PrefUtil.setString(key, value)
                                SettingsManager.userSettingsUpdatedRelay.accept(Unit)
                            }
                            else -> Log.d(LOG_TAG, "${key.name} is not a String : '$value'")
                        }

                        lockType -> when (value) {
                            is String -> when (val type = safeValueOf<LockType>(value)) {
                                null -> Log.d(LOG_TAG, "$value is not a valid LockType")
                                else -> {
                                    PrefUtil.setEnum(key, type)
                                    SettingsManager.lockTypeUpdatedRelay.accept(type)
                                }
                            }
                            else -> Log.d(LOG_TAG, "${key.name} is not a String : '$value'")
                        }

                        showAds, showWelcome -> when (value) {
                            is Boolean -> PrefUtil.setBoolean(key, value)
                            else -> Log.d(LOG_TAG, "${key.name} is not a Boolean : '$value'")
                        }

                        startDate -> when (value) {
                            is Int -> {
                                PrefUtil.setInt(key, value)
                                SettingsManager.userSettingsUpdatedRelay.accept(Unit)
                            }
                            else -> Log.d(LOG_TAG, "${key.name} is not a Int : '$value'")
                        }

                        theme -> when (value) {
                            is String -> when (val theme = safeValueOf<Theme>(value)) {
                                null -> Log.d(LOG_TAG, "$value is not a valid Theme")
                                else -> {
                                    PrefUtil.setEnum(key, theme)
                                    SettingsManager.themeUpdatedRelay.accept(theme)
                                }
                            }
                            else -> Log.d(LOG_TAG, "${key.name} is not a String : '$value'")
                        }

                        currentAndroidVersion, saveToFirebase, userLastSeen, incorrectPasswordCount,
                        showAccountWarning -> {
                            //No-op
                        }
                    }
                }
            }
        }
    }

    fun removeListener() {
        listener?.remove()
        listener = null
    }
}
