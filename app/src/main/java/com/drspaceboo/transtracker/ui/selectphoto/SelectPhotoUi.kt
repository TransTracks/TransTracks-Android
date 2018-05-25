/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracker.ui.selectphoto

import android.content.Context
import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.widget.TextView
import android.widget.Toolbar
import com.drspaceboo.transtracker.R
import com.jakewharton.rxbinding2.widget.navigationClicks
import io.reactivex.Observable
import kotterknife.bindView

sealed class SelectPhotoUiEvent {
    object Back : SelectPhotoUiEvent()
}

class SelectPhotoView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.select_photo_toolbar)
    private val title: TextView by bindView(R.id.select_photo_title)
    private val recyclerView: RecyclerView by bindView(R.id.select_photo_recycler_view)

    val events: Observable<SelectPhotoUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        toolbar.navigationClicks().map<SelectPhotoUiEvent> { SelectPhotoUiEvent.Back }
    }
}
