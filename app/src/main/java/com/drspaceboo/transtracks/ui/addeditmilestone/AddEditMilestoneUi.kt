/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.addeditmilestone

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.ui.addeditmilestone.AddEditMilestoneUiState.Display
import com.drspaceboo.transtracks.util.setTextRetainingSelection
import com.drspaceboo.transtracks.util.showKeyboard
import com.drspaceboo.transtracks.util.toFullDateString
import com.jakewharton.rxbinding3.appcompat.itemClicks
import com.jakewharton.rxbinding3.appcompat.navigationClicks
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.afterTextChangeEvents
import io.reactivex.Observable
import kotterknife.bindView
import java.time.LocalDate

sealed class AddEditMilestoneUiEvent {
    object Back : AddEditMilestoneUiEvent()
    object Delete : AddEditMilestoneUiEvent()
    data class ChangeDate(val day: Long) : AddEditMilestoneUiEvent()
    data class TitleUpdated(val newTitle: String) : AddEditMilestoneUiEvent()
    data class DescriptionUpdated(val newDescription: String) : AddEditMilestoneUiEvent()
    data class Save(val day: Long, val title: String, val description: String) : AddEditMilestoneUiEvent()
}

sealed class AddEditMilestoneUiState {
    object Loading : AddEditMilestoneUiState()
    data class Display(val day: Long, val title: String, val description: String, val isAdd: Boolean) :
        AddEditMilestoneUiState()
}

class AddEditMilestoneView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.add_milestone_toolbar)
    private val toolbarTitle: TextView by bindView(R.id.add_milestone_toolbar_title)

    private val titleLabel: View by bindView(R.id.add_milestone_title_label)
    private val title: EditText by bindView(R.id.add_milestone_title)
    private val dateLabel: View by bindView(R.id.add_milestone_date_label)
    private val date: Button by bindView(R.id.add_milestone_date)
    private val descriptionLabel: View by bindView(R.id.add_milestone_description_label)
    private val description: EditText by bindView(R.id.add_milestone_description)

    private val save: Button by bindView(R.id.add_milestone_save)

    private var isUserChange: Boolean = true

    val events: Observable<AddEditMilestoneUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.mergeArray(
            toolbar.navigationClicks().map { AddEditMilestoneUiEvent.Back },
            toolbar.itemClicks().map { item ->
                return@map when (item.itemId) {
                    R.id.add_edit_milestone_menu_delete -> AddEditMilestoneUiEvent.Delete
                    else -> throw IllegalArgumentException("Unhandled item id")
                }
            },
            title.afterTextChangeEvents().skipInitialValue().filter { isUserChange }.map {
                AddEditMilestoneUiEvent.TitleUpdated(it.editable.toString())
            }.distinctUntilChanged(),
            description.afterTextChangeEvents().skipInitialValue().filter { isUserChange }.map {
                AddEditMilestoneUiEvent.DescriptionUpdated(it.editable.toString())
            }.distinctUntilChanged(),
            date.clicks().map { AddEditMilestoneUiEvent.ChangeDate(day) },
            save.clicks().map {
                AddEditMilestoneUiEvent.Save(day, title.text.toString(), description.text.toString())
            })
    }

    private var day: Long = 0L

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        toolbar.inflateMenu(R.menu.add_edit_milestone)

        titleLabel.setOnClickListener {
            title.requestFocus()
            title.showKeyboard()
        }

        dateLabel.setOnClickListener { date.performClick() }

        descriptionLabel.setOnClickListener {
            description.requestFocus()
            description.showKeyboard()
        }
    }

    fun display(state: AddEditMilestoneUiState) {
        isUserChange = false
        when (state) {
            is Display -> {
                @StringRes val titleRes: Int = when (state.isAdd) {
                    true -> R.string.add_milestone
                    false -> R.string.edit_milestone
                }

                toolbarTitle.setText(titleRes)
                toolbar.menu.getItem(0).isVisible = !state.isAdd

                day = state.day
                title.setTextRetainingSelection(state.title)
                date.text = LocalDate.ofEpochDay(day).toFullDateString(context)
                description.setTextRetainingSelection(state.description)

                save.setText(titleRes)
            }
        }
        isUserChange = true
    }
}
