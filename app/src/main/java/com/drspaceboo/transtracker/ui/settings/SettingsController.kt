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
import android.support.annotation.NonNull
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Controller
import com.drspaceboo.transtracker.R
import com.drspaceboo.transtracker.util.plusAssign
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.disposables.CompositeDisposable
import org.threeten.bp.LocalDate

class SettingsController : Controller() {
    private var viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(@NonNull inflater: LayoutInflater, @NonNull container: ViewGroup): View {
        return inflater.inflate(R.layout.settings, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is SettingsView) throw AssertionError("View must be SettingsView")

        viewDisposables += view.events.subscribe { event ->
            when (event) {
                is SettingsUiEvent.Back -> router.handleBack()

                is SettingsUiEvent.ChangeStartDate -> {
                    var dateToUse = event.current

                    if (dateToUse == null) {
                        dateToUse = LocalDate.now()
                    }

                    //Note: The DatePickerDialog uses 0 based months
                    DatePickerDialog(view.context,
                                     DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                                         view.display(SettingsUiState.Loaded(LocalDate.of(year, month + 1, dayOfMonth)))
                                     },
                                     dateToUse!!.year, dateToUse.monthValue - 1, dateToUse.dayOfMonth).show()
                }
            }
        }

        view.display(SettingsUiState.Loaded(LocalDate.of(2017, 8, 17)))
    }

    override fun onDetach(view: View) {
        viewDisposables.clear()
    }
}
