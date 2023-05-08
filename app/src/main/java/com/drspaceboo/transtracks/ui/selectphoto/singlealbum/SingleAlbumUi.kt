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
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.ui.widget.AdapterSpanSizeLookup
import com.drspaceboo.transtracks.util.settings.PrefUtil
import com.drspaceboo.transtracks.util.isNotDisposed
import com.drspaceboo.transtracks.util.toV3
import com.jakewharton.rxbinding3.appcompat.navigationClicks
import com.jakewharton.rxrelay3.PublishRelay
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable

import kotterknife.bindView
import java.lang.ref.WeakReference

sealed class SingleAlbumUiEvent {
    object Back : SingleAlbumUiEvent()
    data class SelectPhoto(val uri: Uri) : SingleAlbumUiEvent()
    data class SelectionUpdate(var uris: ArrayList<Uri>) : SingleAlbumUiEvent()
    object EndMultiSelect : SingleAlbumUiEvent()
    data class SaveMultiple(var uris: ArrayList<Uri>) : SingleAlbumUiEvent()
}

sealed class SingleAlbumUiState {
    data class Loaded(val bucketId: String) : SingleAlbumUiState()
    data class Selection(val bucketId: String, val selectedUris: ArrayList<Uri>) : SingleAlbumUiState()

    companion object {
        fun getBucketId(state: SingleAlbumUiState): String = when (state) {
            is SingleAlbumUiState.Loaded -> state.bucketId
            is SingleAlbumUiState.Selection -> state.bucketId
        }
    }
}

class SingleAlbumView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.single_album_toolbar)
    private val recyclerView: RecyclerView by bindView(R.id.single_album_recycler_view)

    private val eventRelay: PublishRelay<SingleAlbumUiEvent> = PublishRelay.create()
    val events: Observable<SingleAlbumUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge(toolbar.navigationClicks().toV3().map { SingleAlbumUiEvent.Back },
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

    private var photoClickDisposable: Disposable = Disposable.disposed()

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
        bucketId = SingleAlbumUiState.getBucketId(state)

        val shouldShowActionMode = state is SingleAlbumUiState.Selection

        if (shouldShowActionMode && !actionModeHandler.isActive()) {
            toolbar.startActionMode(actionModeHandler)
        } else if (!shouldShowActionMode && actionModeHandler.isActive()) {
            actionModeHandler.finish()
        }

        if (shouldShowActionMode) {
            actionModeHandler.setTitle(
                    (state as SingleAlbumUiState.Selection).selectedUris.size.toString())
        }

        if (adapter == null) {
            adapter = SingleAlbumAdapter(context, bucketId)
            recyclerView.adapter = adapter

            val firstVisibleUriString = PrefUtil.getAlbumFirstVisible(bucketId)
            if (firstVisibleUriString != null) {
                val position = adapter!!.getItemPosition(Uri.parse(firstVisibleUriString))

                if (position != RecyclerView.NO_POSITION) {
                    recyclerView.scrollToPosition(position)
                }
            }
        }

        adapter?.selectionMode = shouldShowActionMode
        if (shouldShowActionMode) {
            val selectedUris: ArrayList<Uri> = when (state) {
                is SingleAlbumUiState.Selection -> state.selectedUris
                else -> ArrayList()
            }

            adapter?.updateSelectedUris(selectedUris)
        }

        if (photoClickDisposable.isDisposed) {
            photoClickDisposable = adapter!!.events.subscribe(eventRelay)
        }
    }

    private val actionModeHandler = object : ActionMode.Callback {
        private var modeRef = WeakReference<ActionMode>(null)
        private var titleText = ""

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val adapter: SingleAlbumAdapter = recyclerView.adapter as SingleAlbumAdapter?
                    ?: return false

            val event: SingleAlbumUiEvent = when (item.itemId) {
                R.id.select_photo_selection_save ->
                    SingleAlbumUiEvent.SaveMultiple(adapter.getSelectedUris())

                else -> throw IllegalArgumentException("Unhandled item")
            }

            eventRelay.accept(event)
            finish()

            return true
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            modeRef = WeakReference(mode)
            mode.menuInflater.inflate(R.menu.select_photo_selection, menu)
            mode.title = titleText
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.title = titleText
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            modeRef = WeakReference<ActionMode>(null)
            eventRelay.accept(SingleAlbumUiEvent.EndMultiSelect)
        }

        fun finish() {
            modeRef.get()?.finish()
        }

        fun isActive(): Boolean {
            return modeRef.get() != null
        }

        fun setTitle(newTitleText: String) {
            titleText = newTitleText
            modeRef.get()?.title = titleText
        }
    }

    companion object {
        const val GRID_SPAN = 3
    }
}
