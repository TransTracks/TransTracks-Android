/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.gallery

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.support.annotation.StringRes
import android.support.constraint.ConstraintLayout
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.util.AttributeSet
import android.widget.TextView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.ui.widget.AdapterSpanSizeLookup
import com.jakewharton.rxbinding2.support.v7.widget.navigationClicks
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import kotterknife.bindView

sealed class GalleryUiEvent {
    object Back : GalleryUiEvent()
    data class ImageClick(val photoId: String) : GalleryUiEvent()
}

sealed class GalleryUiState {
    data class FaceGallery(val initialDay: Long) : GalleryUiState()
    data class BodyGallery(val initialDay: Long) : GalleryUiState()

    companion object {
        @Photo.Type
        fun getType(state: GalleryUiState) = when (state) {
            is FaceGallery -> Photo.TYPE_FACE
            is BodyGallery -> Photo.TYPE_BODY
        }

        fun getInitialDay(state: GalleryUiState) = when (state) {
            is GalleryUiState.FaceGallery -> state.initialDay
            is GalleryUiState.BodyGallery -> state.initialDay
        }
    }
}

class GalleryView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.gallery_toolbar)
    private val title: TextView by bindView(R.id.gallery_title)
    private val recyclerView: RecyclerView by bindView(R.id.gallery_recycler_view)
    private var layoutManager = GridLayoutManager(context, GRID_SPAN)

    private val eventRelay: PublishRelay<GalleryUiEvent> = PublishRelay.create()
    val events: Observable<GalleryUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge(toolbar.navigationClicks().map<GalleryUiEvent> { GalleryUiEvent.Back },
                         eventRelay)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        layoutManager.spanSizeLookup = AdapterSpanSizeLookup(recyclerView)
        recyclerView.layoutManager = layoutManager
    }

    fun display(state: GalleryUiState) {
        @StringRes val titleRes: Int = when (state) {
            is GalleryUiState.FaceGallery -> R.string.face_gallery
            is GalleryUiState.BodyGallery -> R.string.body_gallery
        }

        title.setText(titleRes)

        val adapter = GalleryAdapter(GalleryUiState.getType(state), eventRelay) { adapter ->
            val scrollTo = adapter.getPositionOfDay(GalleryUiState.getInitialDay(state))
            if (scrollTo != -1) {
                Handler(Looper.getMainLooper()).post {
                    layoutManager.scrollToPositionWithOffset(scrollTo, 0)
                }
            }
        }
        recyclerView.adapter = adapter
    }

    companion object {
        const val GRID_SPAN = 3
    }
}
