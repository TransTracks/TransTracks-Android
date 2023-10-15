/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.selectphoto.selectalbum

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.util.isNotDisposed
import com.drspaceboo.transtracks.util.toV3
import com.jakewharton.rxbinding3.appcompat.navigationClicks
import com.jakewharton.rxrelay3.PublishRelay
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable

import kotterknife.bindView

sealed class SelectAlbumUiEvent {
    object Back : SelectAlbumUiEvent()
    data class SelectAlbum(val bucketId: String) : SelectAlbumUiEvent()
}

sealed class SelectAlbumUiState {
    object Loaded : SelectAlbumUiState()
}

class AlbumView(
    context: Context, attributeSet: AttributeSet
) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.select_album_toolbar)
    private val recyclerView: RecyclerView by bindView(R.id.select_album_recycler_view)

    private val eventRelay: PublishRelay<SelectAlbumUiEvent> =
        PublishRelay.create<SelectAlbumUiEvent>()
    val events: Observable<SelectAlbumUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge(
            toolbar.navigationClicks().toV3().map { SelectAlbumUiEvent.Back },
            eventRelay
        )
    }

    private var albumClickDisposable: Disposable = Disposable.disposed()

    fun display(state: SelectAlbumUiState) {
        when (state) {
            is SelectAlbumUiState.Loaded -> {
                val selectAlbumAdapter: SelectAlbumAdapter

                if (recyclerView.adapter == null) {
                    recyclerView.layoutManager = LinearLayoutManager(context)
                    selectAlbumAdapter = SelectAlbumAdapter()
                    recyclerView.adapter = selectAlbumAdapter
                } else {
                    selectAlbumAdapter = recyclerView.adapter as SelectAlbumAdapter
                }

                selectAlbumAdapter.fetchData(context)
                albumClickDisposable = selectAlbumAdapter.itemClick
                    .map { SelectAlbumUiEvent.SelectAlbum(it) }
                    .subscribe(eventRelay)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (albumClickDisposable.isNotDisposed()) {
            albumClickDisposable.dispose()
        }
    }
}
