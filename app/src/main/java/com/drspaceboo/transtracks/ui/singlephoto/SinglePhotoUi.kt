/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.singlephoto

import android.content.Context
import android.support.constraint.ConstraintLayout
import android.support.v7.widget.Toolbar
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import com.drspaceboo.transtracks.R
import com.jakewharton.rxbinding2.support.v7.widget.navigationClicks
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import kotterknife.bindView
import java.io.File

sealed class SinglePhotoUiEvent {
    object Back : SinglePhotoUiEvent()
}

sealed class SinglePhotoUiState {
    data class Loaded(val photoPath: String, val details: String) : SinglePhotoUiState()
}

class SinglePhotoView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.single_photo_toolbar)

    private val image: ImageView by bindView(R.id.single_photo_image)
    private val details: TextView by bindView(R.id.single_photo_details)

    val events: Observable<SinglePhotoUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        toolbar.navigationClicks().map<SinglePhotoUiEvent> { SinglePhotoUiEvent.Back }
    }

    fun display(state: SinglePhotoUiState) {
        when (state) {
            is SinglePhotoUiState.Loaded -> {
                Picasso.get()
                        .load(File(state.photoPath))
                        .fit()
                        .centerInside()
                        .into(image)

                details.text = state.details
            }
        }
    }
}
