/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.settings

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.DialogInterface
import android.support.annotation.NonNull
import android.support.v7.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.EncryptionUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.Observables
import com.drspaceboo.transtracks.util.PrefUtil
import com.drspaceboo.transtracks.util.PrefUtil.LOCK_DELAY_FIFTEEN_MINUTES
import com.drspaceboo.transtracks.util.PrefUtil.LOCK_DELAY_FIVE_MINUTES
import com.drspaceboo.transtracks.util.PrefUtil.LOCK_DELAY_INSTANT
import com.drspaceboo.transtracks.util.PrefUtil.LOCK_DELAY_ONE_MINUTE
import com.drspaceboo.transtracks.util.PrefUtil.LOCK_DELAY_TWO_MINUTES
import com.drspaceboo.transtracks.util.PrefUtil.LOCK_NORMAL
import com.drspaceboo.transtracks.util.PrefUtil.LOCK_OFF
import com.drspaceboo.transtracks.util.PrefUtil.LOCK_TRAINS
import com.drspaceboo.transtracks.util.PrefUtil.THEME_PINK
import com.drspaceboo.transtracks.util.Quadruple
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import io.reactivex.disposables.CompositeDisposable
import org.threeten.bp.LocalDate

class SettingsController : Controller() {
    private var viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(@NonNull inflater: LayoutInflater, @NonNull container: ViewGroup): View {
        return inflater.inflate(R.layout.settings, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is SettingsView) throw AssertionError("View must be SettingsView")

        AnalyticsUtil.logEvent(Event.SettingsControllerShown)

        val sharedEvents = view.events.share()

        viewDisposables += Observables
                .combineLatest(PrefUtil.startDate.asObservable(), PrefUtil.theme.asObservable(),
                               PrefUtil.lockType.asObservable(), PrefUtil.lockDelay.asObservable())
                { startDate, theme, lockType, lockDelay ->
                    Quadruple(startDate, theme, lockType, lockDelay)
                }
                .map { (startDate, theme, lockType, lockDelay) ->
                    val themeName = view.getString(if (theme == THEME_PINK) R.string.pink else R.string.blue)

                    val lockName = view.getString(
                            when (lockType) {
                                LOCK_OFF -> R.string.disabled
                                LOCK_NORMAL -> R.string.enabled_normal
                                LOCK_TRAINS -> R.string.enabled_trains
                                else -> throw IllegalArgumentException("Unhandled lock type")
                            })

                    val lockDelayName = view.getString(
                            when (lockDelay) {
                                LOCK_DELAY_INSTANT -> R.string.instant
                                LOCK_DELAY_ONE_MINUTE -> R.string.one_minute
                                LOCK_DELAY_TWO_MINUTES -> R.string.two_minutes
                                LOCK_DELAY_FIVE_MINUTES -> R.string.five_minutes
                                LOCK_DELAY_FIFTEEN_MINUTES -> R.string.fifteen_minutes
                                else -> throw IllegalArgumentException("Unhandled lock delay")
                            })

                    return@map SettingsUiState.Loaded(startDate, themeName, lockName,
                                                      enableLockDelay = lockType != LOCK_OFF,
                                                      lockDelay = lockDelayName)
                }
                .subscribe { state -> view.display(state) }

        viewDisposables += sharedEvents
                .ofType<SettingsUiEvent.Back>()
                .subscribe { router.handleBack() }

        viewDisposables += sharedEvents
                .ofType<SettingsUiEvent.ChangeStartDate>()
                .subscribe {
                    val startDate = PrefUtil.startDate.get()

                    //Note: The DatePickerDialog uses 0 based months
                    DatePickerDialog(view.context,
                                     { _, year, month, dayOfMonth ->
                                         PrefUtil.startDate.set(LocalDate.of(year, month + 1, dayOfMonth))
                                     },
                                     startDate.year, startDate.monthValue - 1, startDate.dayOfMonth).show()
                }

        viewDisposables += sharedEvents
                .ofType<SettingsUiEvent.ChangeTheme>()
                .subscribe {
                    val theme = PrefUtil.theme.get()

                    AlertDialog.Builder(view.context)
                            .setTitle(R.string.select_theme)
                            .setSingleChoiceItems(arrayOf(view.getString(R.string.pink),
                                                          view.getString(R.string.blue)),
                                                  theme) { dialog: DialogInterface, index: Int ->
                                if (theme != index) {
                                    PrefUtil.theme.set(index)
                                    router.replaceTopController(RouterTransaction.with(SettingsController()))
                                }
                                dialog.dismiss()
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                }

        viewDisposables += sharedEvents
                .ofType<SettingsUiEvent.ChangeLockMode>()
                .subscribe { _ ->
                    fun showRemovePasswordDialog() {
                        val builder = AlertDialog.Builder(view.context)
                                .setTitle(R.string.enter_password_to_disable_lock)

                        @SuppressLint("InflateParams") // Unable to provide root
                        val dialogView = LayoutInflater.from(builder.context)
                                .inflate(R.layout.enter_password_dialog, null)
                        val password: EditText = dialogView.findViewById(R.id.set_password_code)

                        val passwordDialog = builder.setView(dialogView)
                                .setPositiveButton(R.string.disable, null)
                                .setNegativeButton(R.string.cancel, null)
                                .create()


                        password.addTextChangedListener(object : TextWatcher {
                            override fun afterTextChanged(s: Editable?) {
                                passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                        .isEnabled = !s.isNullOrBlank()
                            }

                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int,
                                                           after: Int) {
                            }

                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int,
                                                       count: Int) {
                            }
                        })
                        passwordDialog.setOnShowListener { dialog ->
                            val positiveButton = passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            positiveButton.isEnabled = false

                            positiveButton.setOnClickListener {
                                if (PrefUtil.lockCode.get()
                                        != EncryptionUtil.encryptAndEncode(password.text.toString(),
                                                                           PrefUtil.CODE_SALT)) {
                                    Toast.makeText(view.context, R.string.incorrect_password,
                                                   Toast.LENGTH_LONG)
                                            .show()
                                    return@setOnClickListener
                                }

                                PrefUtil.lockCode.set("")
                                PrefUtil.lockType.set(PrefUtil.LOCK_OFF)
                                dialog.dismiss()
                            }
                        }

                        passwordDialog.show()

                        passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    }

                    fun showSetPasswordDialog(newLockType: Int) {
                        val builder = AlertDialog.Builder(view.context)
                                .setTitle(R.string.set_password)

                        @SuppressLint("InflateParams") // Unable to provide root
                        val dialogView = LayoutInflater.from(builder.context)
                                .inflate(R.layout.set_password_dialog, null)
                        val password: EditText = dialogView.findViewById(R.id.set_password_code)
                        val confirm: EditText = dialogView.findViewById(R.id.confirm_password_code)

                        val passwordDialog = builder.setView(dialogView)
                                .setPositiveButton(R.string.set_password, null)
                                .setNegativeButton(R.string.cancel, null)
                                .create()

                        password.addTextChangedListener(object : TextWatcher {
                            override fun afterTextChanged(s: Editable?) {
                                passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !s.isNullOrBlank()
                            }

                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        })

                        passwordDialog.setOnShowListener { dialog ->
                            val positiveButton = passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            positiveButton.isEnabled = false
                            positiveButton.setOnClickListener {
                                val passwordText = password.text.toString()
                                val confirmText = confirm.text.toString()

                                if (passwordText.isEmpty()) {
                                    Toast.makeText(view.context, R.string.password_cannot_be_empty,
                                                   Toast.LENGTH_LONG)
                                            .show()
                                    return@setOnClickListener
                                } else if (passwordText != confirmText) {
                                    Toast.makeText(view.context, R.string.password_and_confirm,
                                                   Toast.LENGTH_LONG)
                                            .show()
                                    return@setOnClickListener
                                }

                                PrefUtil.lockCode.set(EncryptionUtil.encryptAndEncode(password.text.toString(), PrefUtil.CODE_SALT))
                                PrefUtil.lockType.set(newLockType)

                                dialog.dismiss()
                            }
                        }
                        passwordDialog.show()


                    }

                    val lockMode = PrefUtil.lockType.get()

                    AlertDialog.Builder(view.context)
                            .setTitle(R.string.select_lock_mode)
                            .setSingleChoiceItems(arrayOf(view.getString(R.string.disabled),
                                                          view.getString(R.string.enabled_normal),
                                                          view.getString(R.string.enabled_trains)),
                                                  lockMode) { dialog: DialogInterface, newLockType: Int ->
                                if (lockMode != newLockType) {
                                    val hasCode = PrefUtil.lockCode.get().isNotEmpty()

                                    when {
                                        newLockType == PrefUtil.LOCK_OFF -> {
                                            //Turn off lock, and remove the code
                                            showRemovePasswordDialog()
                                        }

                                        hasCode -> {
                                            //Changing to another type with the code on, just update type
                                            PrefUtil.lockType.set(newLockType)
                                        }

                                        else -> {
                                            //Changing to a type with a code, let's ask for the code
                                            showSetPasswordDialog(newLockType)
                                        }
                                    }
                                }
                                dialog.dismiss()
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                }

        viewDisposables += sharedEvents
                .ofType<SettingsUiEvent.ChangeLockDelay>()
                .subscribe {
                    val delay = PrefUtil.lockDelay.get()

                    AlertDialog.Builder(view.context)
                            .setTitle(R.string.select_theme)
                            .setSingleChoiceItems(arrayOf(view.getString(R.string.instant),
                                                          view.getString(R.string.one_minute),
                                                          view.getString(R.string.two_minutes),
                                                          view.getString(R.string.five_minutes),
                                                          view.getString(R.string.fifteen_minutes)),
                                                  delay) { dialog: DialogInterface, index: Int ->
                                if (delay != index) {
                                    PrefUtil.lockDelay.set(index)
                                }
                                dialog.dismiss()
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                }
    }

    override fun onDetach(view: View) {
        viewDisposables.clear()
    }
}
