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
import android.widget.TextView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.util.isNotDisposed
import com.jakewharton.rxbinding2.support.v7.widget.navigationClicks
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import kotterknife.bindView

sealed class SelectPhotoUiEvent {
    object Back : SelectPhotoUiEvent()
    object TakePhoto : SelectPhotoUiEvent()
    data class PhotoSelected(val uri: Uri) : SelectPhotoUiEvent()
}

class SelectPhotoView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.select_photo_toolbar)
    private val title: TextView by bindView(R.id.select_photo_title)
    private val recyclerView: RecyclerView by bindView(R.id.select_photo_recycler_view)

    private var adapterDisposable: Disposable = Disposables.disposed()

    private val eventRelay: PublishRelay<SelectPhotoUiEvent> = PublishRelay.create()
    val events: Observable<SelectPhotoUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge(toolbar.navigationClicks().map<SelectPhotoUiEvent> { SelectPhotoUiEvent.Back },
                         eventRelay)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (adapterDisposable.isNotDisposed()) {
            adapterDisposable.dispose()
        }

        val adapter = SelectPhotoAdapter(context)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = GridLayoutManager(context, 3)
        adapterDisposable = adapter.itemClick.subscribe(eventRelay)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (adapterDisposable.isNotDisposed()) {
            adapterDisposable.dispose()
        }
    }
}
