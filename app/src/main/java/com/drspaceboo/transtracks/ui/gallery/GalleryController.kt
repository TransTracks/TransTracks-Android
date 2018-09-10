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

import android.os.Bundle
import android.support.annotation.NonNull
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.ui.singlephoto.SinglePhotoController
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import io.reactivex.disposables.CompositeDisposable

class GalleryController(args: Bundle) : Controller(args) {
    constructor(isFaceGallery: Boolean, initialDay: Long) : this(Bundle().apply {
        putBoolean(KEY_IS_FACE_GALLERY, isFaceGallery)
        putLong(KEY_INITIAL_DAY, initialDay)
    })

    private val isFaceGallery: Boolean = args.getBoolean(KEY_IS_FACE_GALLERY)
    private val initialDay: Long = args.getLong(KEY_INITIAL_DAY)

    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(@NonNull inflater: LayoutInflater, @NonNull container: ViewGroup): View {
        return inflater.inflate(R.layout.gallery, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is GalleryView) throw AssertionError("View must be GalleryView")

        val initialState: GalleryUiState = when (isFaceGallery) {
            true -> GalleryUiState.FaceGallery(initialDay)
            false -> GalleryUiState.BodyGallery(initialDay)
        }
        view.display(initialState)

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents.ofType<GalleryUiEvent.Back>()
                .subscribe { router.handleBack() }

        viewDisposables += sharedEvents.ofType<GalleryUiEvent.ImageClick>()
                .subscribe { router.pushController(RouterTransaction.with(SinglePhotoController())) }
    }

    override fun onDetach(view: View) {
        viewDisposables.clear()
    }

    companion object {
        private const val KEY_IS_FACE_GALLERY = "isFaceGallery"
        private const val KEY_INITIAL_DAY = "initialDay"
    }
}
