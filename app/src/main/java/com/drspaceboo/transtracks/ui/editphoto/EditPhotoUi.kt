/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.editphoto

import android.content.Context
import android.support.constraint.ConstraintLayout
import android.support.v7.widget.Toolbar
import android.util.AttributeSet
import android.widget.Button
import android.widget.ImageView
import com.drspaceboo.transtracks.R
import com.jakewharton.rxbinding2.support.v7.widget.navigationClicks
import com.jakewharton.rxbinding2.view.clicks
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import kotterknife.bindView
import java.io.File

sealed class EditPhotoUiEvent {
    object Back : EditPhotoUiEvent()
    object ChangeDate : EditPhotoUiEvent()
    object ChangeType : EditPhotoUiEvent()
    object Update : EditPhotoUiEvent()
}

sealed class EditPhotoUiState {
    object Loading : EditPhotoUiState()
    data class Loaded(val photoPath: String, val date: String, val type: String) : EditPhotoUiState()
}

class EditPhotoView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.edit_photo_toolbar)
    private val image: ImageView by bindView(R.id.edit_photo_image)

    private val date: Button by bindView(R.id.edit_photo_date)
    private val type: Button by bindView(R.id.edit_photo_type)

    private val save: Button by bindView(R.id.edit_photo_save)

    val events: Observable<EditPhotoUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge(toolbar.navigationClicks().map { EditPhotoUiEvent.Back },
                         date.clicks().map { EditPhotoUiEvent.ChangeDate },
                         type.clicks().map { EditPhotoUiEvent.ChangeType },
                         save.clicks().map { EditPhotoUiEvent.Update })
    }

    fun display(state: EditPhotoUiState) {
        when (state) {
            is EditPhotoUiState.Loaded -> {
                Picasso.get()
                        .load(File(state.photoPath))
                        .fit()
                        .centerInside()
                        .into(image)

                date.text = state.date
                type.text = state.type
            }
        }
    }
}
