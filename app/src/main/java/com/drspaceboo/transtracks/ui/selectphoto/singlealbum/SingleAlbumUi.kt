/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.selectphoto.singlealbum

import android.content.Context
import android.net.Uri
import android.support.constraint.ConstraintLayout
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.util.AttributeSet
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.ui.widget.AdapterSpanSizeLookup
import com.drspaceboo.transtracks.util.PrefUtil
import com.drspaceboo.transtracks.util.isNotDisposed
import com.jakewharton.rxbinding2.support.v7.widget.navigationClicks
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import kotterknife.bindView

sealed class SingleAlbumUiEvent {
    object Back : SingleAlbumUiEvent()
    data class SelectPhoto(val uri: Uri) : SingleAlbumUiEvent()
}

sealed class SingleAlbumUiState {
    data class Loaded(val bucketId: String) : SingleAlbumUiState()
}

class SingleAlbumView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.single_album_toolbar)
    private val recyclerView: RecyclerView by bindView(R.id.single_album_recycler_view)

    private val eventRelay: PublishRelay<SingleAlbumUiEvent> = PublishRelay.create()
    val events: Observable<SingleAlbumUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge(toolbar.navigationClicks().map { SingleAlbumUiEvent.Back },
                         eventRelay.doOnNext { event ->
                             if (event !is SingleAlbumUiEvent.SelectPhoto) {
                                 return@doOnNext
                             }

                             val position = gridLayoutManager.findFirstVisibleItemPosition()

                             if (position == RecyclerView.NO_POSITION) {
                                 return@doOnNext
                             }

                             adapter?.let { adapter ->
                                 val uriString: String? = adapter.getUri(position)?.toString()

                                 if (uriString != null) {
                                     PrefUtil.setAlbumFirstVisible(bucketId, uriString)
                                 }
                             }
                         })
    }

    private var photoClickDisposable: Disposable = Disposables.disposed()

    private val gridLayoutManager = GridLayoutManager(context, GRID_SPAN)
    private var adapter: SingleAlbumAdapter? = null

    private var bucketId: String = ""

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        gridLayoutManager.spanSizeLookup = AdapterSpanSizeLookup(recyclerView, GRID_SPAN)
        recyclerView.layoutManager = gridLayoutManager
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        if (photoClickDisposable.isNotDisposed()) {
            photoClickDisposable.dispose()
        }
    }

    fun display(state: SingleAlbumUiState) {
        when (state) {
            is SingleAlbumUiState.Loaded -> {
                bucketId = state.bucketId

                if (adapter == null) {
                    adapter = SingleAlbumAdapter(context, state.bucketId)
                    recyclerView.adapter = adapter

                    val firstVisibleUriString = PrefUtil.getAlbumFirstVisible(bucketId)
                    if (firstVisibleUriString != null) {
                        val position = adapter!!.getItemPosition(Uri.parse(firstVisibleUriString))

                        if (position != RecyclerView.NO_POSITION) {
                            recyclerView.scrollToPosition(position)
                        }
                    }
                }

                if (photoClickDisposable.isDisposed) {
                    photoClickDisposable = adapter!!.itemClick
                            .map<SingleAlbumUiEvent> { index ->
                                val uri = adapter!!.getUri(index)!!
                                return@map SingleAlbumUiEvent.SelectPhoto(uri)
                            }
                            .subscribe(eventRelay)
                }
            }
        }
    }

    companion object {
        const val GRID_SPAN = 3
    }
}
