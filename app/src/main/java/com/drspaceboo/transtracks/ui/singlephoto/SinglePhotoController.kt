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
import com.drspaceboo.transtracks.ui.editphoto.EditPhotoController
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.toFullDateString
import com.drspaceboo.transtracks.util.using
import io.reactivex.disposables.CompositeDisposable
import io.realm.Realm
import io.realm.RealmObjectChangeListener
import org.threeten.bp.LocalDate

class SinglePhotoController(args: Bundle) : Controller(args) {
    constructor(photoId: String) : this(Bundle().apply {
        putString(KEY_PHOTO_ID, photoId)
    })

    private val photoId: String = args.getString(KEY_PHOTO_ID)!!

    private val realm: Realm by lazy(LazyThreadSafetyMode.NONE) {
        Realm.getDefaultInstance()
    }
    private var photo: Photo? = null

    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(@NonNull inflater: LayoutInflater, @NonNull container: ViewGroup): View {
        return inflater.inflate(R.layout.single_photo, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is SinglePhotoView) throw AssertionError("View must be SinglePhotoView")

        if (photo == null || !photo!!.isLoaded) {
            photo = realm.where(Photo::class.java).equalTo(Photo.FIELD_ID, photoId).findFirstAsync()
        } else {
            val details = view.getString(
                    R.string.photo_detail_replacement,
                    LocalDate.ofEpochDay(photo!!.epochDay).toFullDateString(view.context),
                    Photo.getTypeName(photo!!.type, view.context))

            view.display(SinglePhotoUiState.Loaded(photo!!.filename, details, photo!!.id))
        }

        photo?.addChangeListener(RealmObjectChangeListener<Photo> { innerPhoto, _ ->
            val details = view.getString(
                    R.string.photo_detail_replacement,
                    LocalDate.ofEpochDay(innerPhoto.epochDay).toFullDateString(view.context),
                    Photo.getTypeName(innerPhoto.type, view.context))

            view.display(SinglePhotoUiState.Loaded(innerPhoto.filename, details, innerPhoto.id))
        })

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents.ofType<SinglePhotoUiEvent.Back>()
                .subscribe { router.handleBack() }

        viewDisposables += sharedEvents.ofType<SinglePhotoUiEvent.Edit>()
                .subscribe { event ->
                    router.pushController(RouterTransaction.with(EditPhotoController(event.photoId))
                                                  .using(HorizontalChangeHandler()))
                }
    }

    override fun onDetach(view: View) {
        photo?.removeAllChangeListeners()

        viewDisposables.clear()
    }

    companion object {
        private const val KEY_PHOTO_ID = "photoId"
    }
}
