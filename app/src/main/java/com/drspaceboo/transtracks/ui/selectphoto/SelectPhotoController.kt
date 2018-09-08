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

import android.os.Bundle
import android.support.annotation.NonNull
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.ui.assignphoto.AssignPhotoController
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.using
import io.reactivex.disposables.CompositeDisposable

class SelectPhotoController(args: Bundle) : Controller(args) {
    constructor(epochDay: Long? = null,
                @Photo.Type type: Int = Photo.TYPE_FACE) : this(Bundle().apply {
        if (epochDay != null) {
            putLong(KEY_EPOCH_DAY, epochDay)
        }
        putInt(KEY_TYPE, type)
    })

    private val epochDay: Long? = when (args.containsKey(KEY_EPOCH_DAY)) {
        true -> args.getLong(KEY_EPOCH_DAY)
        false -> null
    }

    private val type: Int = args.getInt(KEY_TYPE)

    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(@NonNull inflater: LayoutInflater, @NonNull container: ViewGroup): View {
        return inflater.inflate(R.layout.select_photo, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is SelectPhotoView) throw AssertionError("View must be SelectPhotoView")

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents
                .ofType<SelectPhotoUiEvent.Back>()
                .subscribe { router.handleBack() }

        viewDisposables += sharedEvents
                .ofType<SelectPhotoUiEvent.PhotoSelected>()
                .subscribe { event ->
                    router.pushController(RouterTransaction.with(AssignPhotoController(event.uri, epochDay, type))
                                                  .using(HorizontalChangeHandler()))
                }
    }

    override fun onDetach(view: View) {
        viewDisposables.clear()
    }

    companion object {
        private const val KEY_EPOCH_DAY = "epochDay"
        private const val KEY_TYPE = "type"
    }
}
