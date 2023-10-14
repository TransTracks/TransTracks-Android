/*
 * Copyright © 2020 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.ui.settings.SettingsConflictDialog.SettingsConflictAdapter.SettingsConflictViewHolder
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.settings.FirebaseSettingUtil
import com.drspaceboo.transtracks.util.settings.LockDelay
import com.drspaceboo.transtracks.util.settings.LockType
import com.drspaceboo.transtracks.util.settings.SettingsManager
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key
import com.drspaceboo.transtracks.util.settings.SettingsManager.Key.*
import com.drspaceboo.transtracks.util.settings.Theme
import com.drspaceboo.transtracks.util.toFullDateString
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotterknife.bindView
import java.time.LocalDate

object SettingsConflictDialog {
    fun create(differences: List<Pair<Key, Any>>, context: Context): AlertDialog {
        val themedContext = context

        @SuppressLint("InflateParams") //We can't pass root for the dialog we haven't created yet
        val view = LayoutInflater.from(context)
            .inflate(R.layout.settings_conflict_dialog, null) as RecyclerView
        val adapter = SettingsConflictAdapter(differences)
        view.adapter = adapter
        view.layoutManager = LinearLayoutManager(themedContext)

        return AlertDialog.Builder(themedContext)
            .setTitle(R.string.settings_conflict)
            .setMessage(R.string.settings_conflict_message)
            .setPositiveButton(R.string.apply_settings) { dialog, _ ->
                val data: Map<String, Any> = differences.mapIndexed { index, (key, value) ->
                    val valueToUse = when (adapter.choices[index]) {
                        true -> value
                        false -> SettingsManager.firebaseValueForKey(key, context)!!
                    }

                    return@mapIndexed key.name to valueToUse
                }.toMap()

                val docRef = FirebaseSettingUtil.getSettingsDocRef()
                docRef.update(data)
                SettingsManager.enableFirebaseSync()
                dialog.dismiss()
            }
            .setView(view)
            .create()
    }

    private class SettingsConflictAdapter(val differences: List<Pair<Key, Any>>) :
        RecyclerView.Adapter<SettingsConflictViewHolder>() {
        val choices: Array<Boolean> = Array(differences.size) { true }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            SettingsConflictViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.settings_conflict_item, parent, false),
                this
            )

        override fun getItemCount(): Int = differences.size

        override fun onBindViewHolder(holder: SettingsConflictViewHolder, position: Int) {
            holder.bind(differences[position], choices[position])
        }

        class SettingsConflictViewHolder(itemView: View, adapter: SettingsConflictAdapter) :
            RecyclerView.ViewHolder(itemView) {
            private val label: TextView by bindView(R.id.settings_conflict_label)
            private val group: MaterialButtonToggleGroup by bindView(R.id.settings_conflict_group)
            private val local: MaterialButton by bindView(R.id.settings_conflict_option_local)
            private val server: MaterialButton by bindView(R.id.settings_conflict_option_server)

            init {
                group.addOnButtonCheckedListener { _, id, isChecked ->
                    adapter.choices[adapterPosition] =
                        (id == local.id && !isChecked) || (id == server.id && isChecked)
                }
            }

            fun bind(conflict: Pair<Key, Any>, useServer: Boolean) {
                val (key, serverConflictValue) = conflict

                @StringRes val nameRes: Int
                val localValue: String
                val serverValue: String

                when (key) {
                    lockCode -> {
                        nameRes = R.string.lock_code_label
                        localValue = when {
                            SettingsManager.getLockCode()
                                .isEmpty() -> itemView.getString(R.string.no_code)

                            else -> itemView.getString(R.string.use_local_code)
                        }
                        serverValue = when {
                            ((serverConflictValue as? String)
                                ?: "").isEmpty() -> itemView.getString(R.string.no_code)

                            else -> itemView.getString(R.string.use_server_code)
                        }
                    }

                    lockDelay -> {
                        nameRes = R.string.lock_delay_label
                        localValue = itemView.getString(
                            SettingsManager.getLockDelay().displayNameRes()
                        )
                        serverValue = itemView.getString(
                            LockDelay.valueOf(serverConflictValue as String).displayNameRes()
                        )
                    }

                    lockType -> {
                        nameRes = R.string.select_lock_mode
                        localValue = itemView.getString(
                            SettingsManager.getLockType().displayNameRes()
                        )
                        serverValue = itemView.getString(
                            LockType.valueOf(serverConflictValue as String).displayNameRes()
                        )
                    }

                    startDate -> {
                        nameRes = R.string.start_date_label
                        localValue = SettingsManager.getStartDate(itemView.context)
                            .toFullDateString(itemView.context)
                        serverValue = LocalDate.ofEpochDay(serverConflictValue as Long)
                            .toFullDateString(itemView.context)
                    }

                    theme -> {
                        nameRes = R.string.theme_label
                        localValue = itemView.getString(SettingsManager.getTheme().displayNameRes())
                        serverValue = itemView.getString(
                            Theme.valueOf(serverConflictValue as String).displayNameRes()
                        )
                    }

                    enableAnalytics -> {
                        nameRes = R.string.anonymous_analytics
                        localValue = itemView.getString(
                            SettingsManager.getEnableAnalytics().displayNameRes()
                        )
                        serverValue = itemView.getString(
                            (serverConflictValue as Boolean).displayNameRes()
                        )
                    }

                    enableCrashReports -> {
                        nameRes = R.string.anonymous_crash_reports
                        localValue = itemView.getString(
                            SettingsManager.getEnableCrashReports().displayNameRes()
                        )
                        serverValue = itemView.getString(
                            (serverConflictValue as Boolean).displayNameRes()
                        )
                    }

                    showAds -> {
                        nameRes = R.string.support_ads
                        localValue = itemView.getString(SettingsManager.showAds().displayNameRes())
                        serverValue = itemView.getString(
                            (serverConflictValue as Boolean).displayNameRes()
                        )
                    }

                    currentAndroidVersion, incorrectPasswordCount, saveToFirebase,
                    showAccountWarning, showWelcome, userLastSeen -> throw IllegalArgumentException(
                        "This settings should not be in conflict because they don't get synced"
                    )
                }

                label.setText(nameRes)
                local.text = localValue
                server.text = serverValue

                group.check(
                    when (useServer) {
                        true -> R.id.settings_conflict_option_server
                        false -> R.id.settings_conflict_option_local
                    }
                )
            }
        }
    }

    @StringRes
    private fun Boolean.displayNameRes() = when (this) {
        true -> R.string.enabled
        false -> R.string.disabled
    }
}

