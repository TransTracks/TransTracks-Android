/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracker.ui.gallery

import android.content.Context
import android.support.annotation.StringRes
import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.util.AttributeSet
import android.widget.TextView
import com.drspaceboo.transtracker.R
import com.jakewharton.rxbinding2.support.v7.widget.navigationClicks
import io.reactivex.Observable
import kotterknife.bindView

sealed class GalleryUiEvent {
    object Back : GalleryUiEvent()
}

sealed class GalleryUiState {
    object FaceGallery : GalleryUiState()
    object BodyGallery : GalleryUiState()
}

class GalleryView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.gallery_toolbar)
    private val title: TextView by bindView(R.id.gallery_title)
    private val recyclerView: RecyclerView by bindView(R.id.gallery_recycler_view)

    val events: Observable<GalleryUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        toolbar.navigationClicks().map<GalleryUiEvent> { GalleryUiEvent.Back }
    }

    fun display(state: GalleryUiState) {
        @StringRes val titleRes: Int = when (state) {
            is GalleryUiState.FaceGallery -> R.string.face_gallery
            is GalleryUiState.BodyGallery -> R.string.body_gallery
        }

        title.setText(titleRes)
    }
}
