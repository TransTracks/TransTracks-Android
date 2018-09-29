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
import android.widget.ImageView
import com.drspaceboo.transtracks.R
import com.jakewharton.rxbinding2.support.v7.widget.navigationClicks
import com.jakewharton.rxbinding2.view.clicks
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import kotterknife.bindView

sealed class AssignPhotoUiEvent {
    object Back : AssignPhotoUiEvent()
    object ChangeDate : AssignPhotoUiEvent()
    object ChangeType : AssignPhotoUiEvent()
    object Save : AssignPhotoUiEvent()
}

sealed class AssignPhotoUiState {
    object Loading : AssignPhotoUiState()
    data class Loaded(val photoUri: Uri, val date: String, val type: String) : AssignPhotoUiState()
}

class AssignPhotoView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.assign_photo_toolbar)
    private val image: ImageView by bindView(R.id.assign_photo_image)

    private val dateLabel: View by bindView(R.id.assign_photo_date_label)
    private val date: Button by bindView(R.id.assign_photo_date)
    private val typeLabel: View by bindView(R.id.assign_photo_type_label)
    private val type: Button by bindView(R.id.assign_photo_type)

    private val save: Button by bindView(R.id.assign_photo_save)

    val events: Observable<AssignPhotoUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge(toolbar.navigationClicks().map { AssignPhotoUiEvent.Back },
                         date.clicks().map { AssignPhotoUiEvent.ChangeDate },
                         type.clicks().map { AssignPhotoUiEvent.ChangeType },
                         save.clicks().map { AssignPhotoUiEvent.Save })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        dateLabel.setOnClickListener { date.performClick() }
        typeLabel.setOnClickListener { type.performClick() }
    }

    fun display(state: AssignPhotoUiState) {
        when (state) {
            is AssignPhotoUiState.Loaded -> {
                Picasso.get()
                        .load(state.photoUri)
                        .fit()
                        .centerInside()
                        .into(image)

                date.text = state.date
                type.text = state.type
            }
        }
    }
}
