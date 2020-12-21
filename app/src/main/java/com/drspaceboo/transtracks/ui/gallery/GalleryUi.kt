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
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.ui.widget.AdapterSpanSizeLookup
import com.drspaceboo.transtracks.util.*
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdView
import com.jakewharton.rxbinding3.appcompat.itemClicks
import com.jakewharton.rxbinding3.appcompat.navigationClicks
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import kotterknife.bindView
import java.lang.ref.WeakReference

sealed class GalleryUiEvent {
    object Back : GalleryUiEvent()
    data class ImageClick(val photoId: String) : GalleryUiEvent()
    data class AddPhoto(@Photo.Type val type: Int) : GalleryUiEvent()
    object StartMultiSelect : GalleryUiEvent()
    data class SelectionUpdated(val selectedIds: ArrayList<String>) : GalleryUiEvent()
    object EndActionMode : GalleryUiEvent()
    data class Share(val selectedIds: ArrayList<String>) : GalleryUiEvent()
    data class Delete(val selectedIds: ArrayList<String>) : GalleryUiEvent()
}

sealed class GalleryUiState {
    data class Loaded(val type: Int, val initialDay: Long, val showAds: Boolean) : GalleryUiState()
    data class Selection(val type: Int, val initialDay: Long,
                         val selectedIds: ArrayList<String>,
                         val showAds: Boolean) : GalleryUiState()

    companion object {
        fun getInitialDay(state: GalleryUiState) = when (state) {
            is GalleryUiState.Loaded -> state.initialDay
            is GalleryUiState.Selection -> state.initialDay
        }

        fun getShowAds(state: GalleryUiState): Boolean = when (state) {
            is GalleryUiState.Loaded -> state.showAds
            is GalleryUiState.Selection -> state.showAds
        }

        @Photo.Type
        fun getType(state: GalleryUiState) = when (state) {
            is Loaded -> state.type
            is Selection -> state.type
        }
    }
}

class GalleryView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.gallery_toolbar)
    private val title: TextView by bindView(R.id.gallery_title)

    private val recyclerView: RecyclerView by bindView(R.id.gallery_recycler_view)
    private val emptyMessage: TextView by bindView(R.id.gallery_empty_message)
    private val emptyAdd: View by bindView(R.id.gallery_empty_add)

    private val adViewLayout: FrameLayout by bindView(R.id.gallery_ad_layout)

    private var layoutManager = GridLayoutManager(context, GRID_SPAN)

    private val eventRelay: PublishRelay<GalleryUiEvent> = PublishRelay.create()
    val events: Observable<GalleryUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge(toolbar.navigationClicks().map<GalleryUiEvent> { GalleryUiEvent.Back },
                         toolbar.itemClicks().map<GalleryUiEvent> { item ->
                             return@map when (item.itemId) {
                                 R.id.gallery_menu_add -> GalleryUiEvent.AddPhoto(type)
                                 R.id.gallery_menu_share -> GalleryUiEvent.StartMultiSelect
                                 else -> throw IllegalArgumentException("Unhandled menu item id")
                             }
                         },
                         emptyAdd.clicks().map { GalleryUiEvent.AddPhoto(type) },
                         eventRelay)
    }

    @Photo.Type
    private var type = Photo.TYPE_FACE

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        toolbar.inflateMenu(R.menu.gallery)

        layoutManager.spanSizeLookup = AdapterSpanSizeLookup(recyclerView)
        recyclerView.layoutManager = layoutManager
    }

    fun display(state: GalleryUiState) {
        type = GalleryUiState.getType(state)

        @StringRes val titleRes: Int = when (type) {
            Photo.TYPE_FACE -> R.string.face_gallery
            Photo.TYPE_BODY -> R.string.body_gallery
            else -> throw IllegalArgumentException("Unhandled type")
        }

        title.setText(titleRes)

        val shouldShowActionMode = state is GalleryUiState.Selection

        if (shouldShowActionMode && !actionModeHandler.isActive()) {
            toolbar.startActionMode(actionModeHandler)
        } else if (!shouldShowActionMode && actionModeHandler.isActive()) {
            actionModeHandler.finish()
        }

        val selectedIds: ArrayList<String> = when (state) {
            is GalleryUiState.Selection -> state.selectedIds
            else -> ArrayList()
        }

        if (shouldShowActionMode) {
            actionModeHandler.setTitle((state as GalleryUiState.Selection).selectedIds.size.toString())
        }

        if (recyclerView.adapter == null) {
            val adapter = GalleryAdapter(
                type,
                eventRelay,
                state is GalleryUiState.Selection,
                selectedIds,
                postInitialLoad = { adapter ->
                    val scrollTo = adapter.getPositionOfDay(GalleryUiState.getInitialDay(state))
                    if (scrollTo != -1) {
                        Handler(Looper.getMainLooper()).post {
                            layoutManager.scrollToPositionWithOffset(scrollTo, 0)
                        }
                    }
                },
                postLoad = { adapter ->
                    if (adapter.itemCount > 0) {
                        setVisible(recyclerView)
                        setGone(emptyMessage, emptyAdd)
                    } else {
                        setVisible(emptyMessage, emptyAdd)
                        setGone(recyclerView)
                    }
                }
            )
            recyclerView.adapter = adapter
        } else {
            val adapter: GalleryAdapter = recyclerView.adapter!! as GalleryAdapter

            adapter.selectionMode = state is GalleryUiState.Selection

            if (adapter.selectionMode) {
                adapter.updateSelectedIds(selectedIds)
            }
        }

        if (GalleryUiState.getShowAds(state)) {
            adViewLayout.visible()

            if (adViewLayout.childCount <= 0) {
                AdView(context).apply {
                    adUnitId = getString(R.string.ADS_GALLERY_AD_ID)
                    adViewLayout.addView(this)
                    loadAd(context)
                    adListener = object : AdListener() {
                        override fun onAdFailedToLoad(code: Int) {
                            adViewLayout.gone()
                        }
                    }
                }
            }
        } else {
            adViewLayout.gone()
        }
    }

    private val actionModeHandler = object : ActionMode.Callback {
        private var modeRef = WeakReference<ActionMode>(null)
        private var titleText = ""

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val adapter: GalleryAdapter = recyclerView.adapter as GalleryAdapter? ?: return false

            val event: GalleryUiEvent = when (item.itemId) {
                R.id.gallery_menu_selection_share -> GalleryUiEvent.Share(adapter.getSelectedIds())
                R.id.gallery_menu_selection_delete -> GalleryUiEvent.Delete(adapter.getSelectedIds())
                else -> throw IllegalArgumentException("Unhandled item")
            }

            eventRelay.accept(event)

            return true
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            modeRef = WeakReference(mode)
            mode.menuInflater.inflate(R.menu.gallery_selection, menu)
            mode.title = titleText
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.title = titleText
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            modeRef = WeakReference<ActionMode>(null)
            eventRelay.accept(GalleryUiEvent.EndActionMode)
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
