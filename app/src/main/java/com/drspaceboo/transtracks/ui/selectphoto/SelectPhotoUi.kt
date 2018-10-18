/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.selectphoto

import android.content.Context
import android.net.Uri
import android.support.constraint.ConstraintLayout
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.util.PrefUtil
import com.drspaceboo.transtracks.util.isNotDisposed
import com.jakewharton.rxbinding2.support.v7.widget.itemClicks
import com.jakewharton.rxbinding2.support.v7.widget.navigationClicks
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import kotterknife.bindView
import java.lang.ref.WeakReference

sealed class SelectPhotoUiEvent {
    object Back : SelectPhotoUiEvent()
    object TakePhoto : SelectPhotoUiEvent()
    data class PhotoSelected(val uri: Uri) : SelectPhotoUiEvent()
    object ViewAlbums : SelectPhotoUiEvent()
    object ExternalGalleries : SelectPhotoUiEvent()
    data class SelectionUpdate(var uris: ArrayList<Uri>) : SelectPhotoUiEvent()
    object EndMultiSelect : SelectPhotoUiEvent()
    data class SaveMultiple(var uris: ArrayList<Uri>) : SelectPhotoUiEvent()
}

sealed class SelectPhotoUiState {
    object Loaded : SelectPhotoUiState()
    data class Selection(val selectedUris: ArrayList<Uri>) : SelectPhotoUiState()
}

class SelectPhotoView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.select_photo_toolbar)
    private val recyclerView: RecyclerView by bindView(R.id.select_photo_recycler_view)

    private var adapterDisposable: Disposable = Disposables.disposed()

    private val eventRelay: PublishRelay<SelectPhotoUiEvent> = PublishRelay.create()
    val events: Observable<SelectPhotoUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge(toolbar.navigationClicks().map { SelectPhotoUiEvent.Back },
                         toolbar.itemClicks().map<SelectPhotoUiEvent> { item ->
                             return@map when (item.itemId) {
                                 R.id.select_photo_menu_folders -> SelectPhotoUiEvent.ViewAlbums
                                 R.id.select_photo_menu_external -> SelectPhotoUiEvent.ExternalGalleries
                                 else -> throw IllegalArgumentException("Unhandled toolbar item")
                             }
                         },
                         eventRelay.doOnNext { event ->
                             if (event !is SelectPhotoUiEvent.PhotoSelected) {
                                 return@doOnNext
                             }

                             val position = gridLayoutManager.findFirstVisibleItemPosition()

                             if (position == RecyclerView.NO_POSITION) {
                                 return@doOnNext
                             }

                             val uriString: String = adapter?.getUri(position)?.toString() ?: ""
                             PrefUtil.selectPhotoFirstVisible.set(uriString)
                         })
    }

    private val gridLayoutManager = GridLayoutManager(context, GRID_SPAN)
    private var adapter: SelectPhotoAdapter? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        toolbar.inflateMenu(R.menu.select_photo)
        recyclerView.layoutManager = gridLayoutManager
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (adapterDisposable.isNotDisposed()) {
            adapterDisposable.dispose()
        }
    }

    fun display(state: SelectPhotoUiState) {
        val shouldShowActionMode = state is SelectPhotoUiState.Selection

        if (shouldShowActionMode && !actionModeHandler.isActive()) {
            toolbar.startActionMode(actionModeHandler)
        } else if (!shouldShowActionMode && actionModeHandler.isActive()) {
            actionModeHandler.finish()
        }

        if (shouldShowActionMode) {
            actionModeHandler.setTitle(
                    (state as SelectPhotoUiState.Selection).selectedUris.size.toString())
        }

        if (adapter == null) {
            adapter = SelectPhotoAdapter(context)

            recyclerView.adapter = adapter

            val firstVisibleUriString = PrefUtil.selectPhotoFirstVisible.get()
            if (firstVisibleUriString.isNotBlank()) {
                val position = adapter!!.getItemPosition(Uri.parse(firstVisibleUriString))

                if (position != RecyclerView.NO_POSITION) {
                    recyclerView.scrollToPosition(position)
                }
            }
        }

        adapter?.selectionMode = shouldShowActionMode
        if (shouldShowActionMode) {
            val selectedUris: ArrayList<Uri> = when (state) {
                is SelectPhotoUiState.Selection -> state.selectedUris
                else -> ArrayList()
            }

            adapter?.updateSelectedUris(selectedUris)
        }

        if (adapterDisposable.isDisposed) {
            adapterDisposable = adapter!!.events.subscribe(eventRelay)
        }
    }

    private val actionModeHandler = object : ActionMode.Callback {
        private var modeRef = WeakReference<ActionMode>(null)
        private var titleText = ""

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val adapter: SelectPhotoAdapter = recyclerView.adapter as SelectPhotoAdapter?
                    ?: return false

            val event: SelectPhotoUiEvent = when (item.itemId) {
                R.id.select_photo_selection_save ->
                    SelectPhotoUiEvent.SaveMultiple(adapter.getSelectedUris())

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
            eventRelay.accept(SelectPhotoUiEvent.EndMultiSelect)
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
