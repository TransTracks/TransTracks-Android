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
import android.support.annotation.StringRes
import android.support.constraint.ConstraintLayout
import android.support.v7.widget.Toolbar
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.util.showKeyboard
import com.drspaceboo.transtracks.util.toFullDateString
import com.jakewharton.rxbinding2.support.v7.widget.itemClicks
import com.jakewharton.rxbinding2.support.v7.widget.navigationClicks
import com.jakewharton.rxbinding2.view.clicks
import io.reactivex.Observable
import kotterknife.bindView
import org.threeten.bp.LocalDate

sealed class AddEditMilestoneUiEvent {
    object Back : AddEditMilestoneUiEvent()
    object Delete : AddEditMilestoneUiEvent()
    data class ChangeDate(val day: Long, val title: String,
                          val description: String) : AddEditMilestoneUiEvent()

    data class Save(val day: Long, val title: String,
                    val description: String) : AddEditMilestoneUiEvent()
}

sealed class AddEditMilestoneUiState {
    data class Add(val day: Long, val title: String,
                   val description: String, val isAdd: Boolean) : AddEditMilestoneUiState()

}

class AddEditMilestoneView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.add_milestone_toolbar)
    private val toolbarTitle: TextView by bindView(R.id.add_milestone_toolbar_title)

    private val title: EditText by bindView(R.id.add_milestone_title)
    private val date: Button by bindView(R.id.add_milestone_date)
    private val descriptionLabel: View by bindView(R.id.add_milestone_description_label)
    private val description: EditText by bindView(R.id.add_milestone_description)

    private val save: Button by bindView(R.id.add_milestone_save)

    val events: Observable<AddEditMilestoneUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge(toolbar.navigationClicks().map { AddEditMilestoneUiEvent.Back },
                         toolbar.itemClicks().map { item ->
                             return@map when (item.itemId) {
                                 R.id.add_edit_milestone_menu_delete -> AddEditMilestoneUiEvent.Delete
                                 else -> throw IllegalArgumentException("Unhandled item id")
                             }
                         },
                         date.clicks().map {
                             AddEditMilestoneUiEvent.ChangeDate(day, title.text.toString(),
                                                                description.text.toString())
                         },
                         save.clicks().map {
                             AddEditMilestoneUiEvent.Save(day, title.text.toString(),
                                                          description.text.toString())
                         })
    }

    private var day: Long = 0L

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        toolbar.inflateMenu(R.menu.add_edit_milestone)
        descriptionLabel.setOnClickListener {
            description.requestFocus()
            description.showKeyboard()
        }
    }

    fun display(state: AddEditMilestoneUiState) {
        when (state) {
            is AddEditMilestoneUiState.Add -> {
                @StringRes val titleRes: Int = when (state.isAdd) {
                    true -> R.string.add_milestone
                    false -> R.string.edit_milestone
                }

                toolbarTitle.setText(titleRes)
                toolbar.menu.getItem(0).isVisible = !state.isAdd

                day = state.day
                title.setText(state.title)
                date.text = LocalDate.ofEpochDay(day).toFullDateString(context)
                description.setText(state.description)

                save.setText(titleRes)
            }
        }
    }
}
