/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.lock

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.util.toV3
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.editorActions
import com.squareup.picasso.Picasso
import io.reactivex.rxjava3.core.Observable
import kotterknife.bindOptionalView
import kotterknife.bindView

sealed class LockUiEvent {
    data class Unlock(val code: String) : LockUiEvent()
}

class LockView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val background: ImageView? by bindOptionalView(R.id.lock_background_image)
    private val code: EditText by bindView(R.id.lock_code)
    private val go: Button by bindView(R.id.lock_go)

    val events: Observable<LockUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge<LockUiEvent>(
                code.editorActions().toV3()
                        .filter { action ->
                            action == EditorInfo.IME_ACTION_SEARCH
                                    || action == EditorInfo.IME_ACTION_DONE
                        }
                        .map { action ->
                            return@map when (action) {
                                EditorInfo.IME_ACTION_SEARCH,
                                EditorInfo.IME_ACTION_DONE -> LockUiEvent.Unlock(code.text.toString())
                                else -> throw IllegalArgumentException("Unhandled IME Action '$action'")
                            }
                        },
                go.clicks().toV3().map { LockUiEvent.Unlock(code.text.toString()) })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (background != null && !isInEditMode) {
            Picasso.get()
                    .load(R.drawable.train_track_background)
                    .placeholder(R.color.black)
                    .fit()
                    .centerCrop()
                    .into(background)
        }
    }
}
