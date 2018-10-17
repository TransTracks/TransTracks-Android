/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.assignphoto

import android.content.Context
import android.net.Uri
import android.support.constraint.ConstraintLayout
import android.support.v7.widget.Toolbar
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.util.setVisibleOrGone
import com.jakewharton.rxbinding2.support.v7.widget.navigationClicks
import com.jakewharton.rxbinding2.view.clicks
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import kotterknife.bindView
import org.threeten.bp.LocalDate

sealed class AssignPhotoUiEvent {
    object Back : AssignPhotoUiEvent()
    data class ChangeDate(val index: Int) : AssignPhotoUiEvent()
    data class UsePhotoDate(val index: Int, val photoDate: LocalDate) : AssignPhotoUiEvent()
    data class ChangeType(val index: Int) : AssignPhotoUiEvent()
    data class Save(val index: Int) : AssignPhotoUiEvent()
    data class Skip(val index: Int, val count: Int) : AssignPhotoUiEvent()
}

sealed class AssignPhotoUiState {
    object Loading : AssignPhotoUiState()

    data class Loaded(val index: Int, val count: Int, val photoUri: Uri, val title: String,
                      val date: String, val photoDate: LocalDate?, val type: String,
                      val showSkip: Boolean) : AssignPhotoUiState()
}

class AssignPhotoView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.assign_photo_toolbar)
    private val title: TextView by bindView(R.id.assign_photo_title)
    private val image: ImageView by bindView(R.id.assign_photo_image)

    private val dateLabel: View by bindView(R.id.assign_photo_date_label)
    private val date: Button by bindView(R.id.assign_photo_date)
    private val photoDate: ImageButton by bindView(R.id.assign_photo_use_photo_date)
    private val typeLabel: View by bindView(R.id.assign_photo_type_label)
    private val type: Button by bindView(R.id.assign_photo_type)

    private val save: Button by bindView(R.id.assign_photo_save)
    private val skip: Button by bindView(R.id.assign_photo_skip)

    val events: Observable<AssignPhotoUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.mergeArray(toolbar.navigationClicks().map { AssignPhotoUiEvent.Back },
                              date.clicks().map { AssignPhotoUiEvent.ChangeDate(currentIndex) },
                              photoDate.clicks().map {
                                  AssignPhotoUiEvent.UsePhotoDate(currentIndex, currentPhotoDate)
                              },
                              type.clicks().map { AssignPhotoUiEvent.ChangeType(currentIndex) },
                              save.clicks().map { AssignPhotoUiEvent.Save(currentIndex) },
                              skip.clicks().map { AssignPhotoUiEvent.Skip(currentIndex, currentCount) })
    }

    private var currentPhotoDate: LocalDate = LocalDate.MIN
    private var currentIndex: Int = 0
    private var currentCount: Int = 0

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        dateLabel.setOnClickListener { date.performClick() }
        typeLabel.setOnClickListener { type.performClick() }
    }

    fun display(state: AssignPhotoUiState) {
        when (state) {
            is AssignPhotoUiState.Loaded -> {
                if (state.photoDate != null) {
                    currentPhotoDate = state.photoDate
                }
                currentIndex = state.index
                currentCount = state.count

                title.text = state.title

                Picasso.get()
                        .load(state.photoUri)
                        .fit()
                        .centerInside()
                        .into(image)

                date.text = state.date

                photoDate.setVisibleOrGone(state.photoDate != null)

                type.text = state.type

                skip.setVisibleOrGone(state.showSkip)
            }
        }
    }
}
