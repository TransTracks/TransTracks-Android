/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracker.ui.settings

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.support.annotation.NonNull
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.drspaceboo.transtracker.R
import com.drspaceboo.transtracker.util.*
import com.drspaceboo.transtracker.util.PrefUtil.THEME_PINK
import io.reactivex.disposables.CompositeDisposable
import org.threeten.bp.LocalDate

class SettingsController : Controller() {
    private var viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(@NonNull inflater: LayoutInflater, @NonNull container: ViewGroup): View {
        return inflater.inflate(R.layout.settings, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is SettingsView) throw AssertionError("View must be SettingsView")

        val sharedEvents = view.events.share()

        viewDisposables += Observables
                .combineLatest(PrefUtil.startDate.asObservable(), PrefUtil.theme.asObservable()) { startDate, theme ->
                    startDate to theme
                }
                .map { (startDate, theme) ->
                    val themeName = view.getString(if (theme == THEME_PINK) R.string.pink else R.string.blue)

                    return@map SettingsUiState.Loaded(startDate, themeName)
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
                            .setSingleChoiceItems(
                                    arrayOf(view.getString(R.string.pink), view.getString(R.string.blue)), theme,
                                    { dialog: DialogInterface, index: Int ->
                                        if (theme != index) {
                                            PrefUtil.theme.set(index)
                                            router.replaceTopController(RouterTransaction.with(SettingsController()))
                                        }
                                        dialog.dismiss()
                                    })
                            .show()
                }
    }

    override fun onDetach(view: View) {
        viewDisposables.clear()
    }
}
