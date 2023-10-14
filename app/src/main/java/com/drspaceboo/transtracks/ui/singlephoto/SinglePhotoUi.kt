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
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.util.toV3
import com.jakewharton.rxbinding3.appcompat.itemClicks
import com.jakewharton.rxbinding3.appcompat.navigationClicks
import com.squareup.picasso.Picasso
import io.reactivex.rxjava3.core.Observable
import kotterknife.bindView
import java.io.File

sealed class SinglePhotoUiEvent {
    object Back : SinglePhotoUiEvent()
    data class Edit(val photoId: String) : SinglePhotoUiEvent()
    data class Share(val photoId: String) : SinglePhotoUiEvent()
    data class Delete(val photoId: String) : SinglePhotoUiEvent()
}

sealed class SinglePhotoUiState {
    data class Loaded(
        val photoPath: String, val details: String, val photoId: String
    ) : SinglePhotoUiState()
}

class SinglePhotoView(
    context: Context, attributeSet: AttributeSet
) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.single_photo_toolbar)

    private val image: ImageView by bindView(R.id.single_photo_image)
    private val details: TextView by bindView(R.id.single_photo_details)

    val events: Observable<SinglePhotoUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge(
            toolbar.navigationClicks().toV3().map<SinglePhotoUiEvent> { SinglePhotoUiEvent.Back },
            toolbar.itemClicks().toV3().map { item ->
                return@map when (item.itemId) {
                    R.id.single_photo_menu_edit -> SinglePhotoUiEvent.Edit(photoId)
                    R.id.single_photo_menu_share -> SinglePhotoUiEvent.Share(photoId)
                    R.id.single_photo_menu_delete -> SinglePhotoUiEvent.Delete(photoId)
                    else -> throw IllegalArgumentException("Unhandled menu item")
                }
            })
    }

    private var photoId: String = ""

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        toolbar.inflateMenu(R.menu.single_photo)
    }

    fun display(state: SinglePhotoUiState) {
        when (state) {
            is SinglePhotoUiState.Loaded -> {
                photoId = state.photoId

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
