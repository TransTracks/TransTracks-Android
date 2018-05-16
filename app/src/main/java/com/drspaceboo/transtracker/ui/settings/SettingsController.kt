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
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import org.threeten.bp.LocalDate

class SettingsController : Controller() {
    private var resultDisposable: Disposable = Disposables.disposed()

    override fun onCreateView(@NonNull inflater: LayoutInflater, @NonNull container: ViewGroup): View {
        val view: SettingsView = inflater.inflate(R.layout.settings, container, false) as SettingsView

        resultDisposable = view.events.subscribe { event ->
            when (event) {
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

        return view
    }

    override fun onDestroy() {
        super.onDestroy()
        resultDisposable.dispose()
    }
}
