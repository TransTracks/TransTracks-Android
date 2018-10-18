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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.ui.assignphoto.AssignPhotosController
import com.drspaceboo.transtracks.ui.home.HomeController
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.RxSchedulers
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.using
import io.reactivex.disposables.CompositeDisposable

class SingleAlbumController(args: Bundle) : Controller(args) {
    constructor(bucketId: String, epochDay: Long? = null, @Photo.Type type: Int = Photo.TYPE_FACE,
                tagOfControllerToPopTo: String = HomeController.TAG) : this(Bundle().apply {
        putString(KEY_BUCKET_ID, bucketId)

        if (epochDay != null) {
            putLong(KEY_EPOCH_DAY, epochDay)
        }

        putInt(KEY_TYPE, type)
        putString(KEY_TAG_OF_CONTROLLER_TO_POP_TO, tagOfControllerToPopTo)
    })

    private val bucketId: String = args.getString(KEY_BUCKET_ID)!!
    private val epochDay: Long? = when (args.containsKey(KEY_EPOCH_DAY)) {
        true -> args.getLong(KEY_EPOCH_DAY)
        false -> null
    }
    private val type: Int = args.getInt(KEY_TYPE)

    private var viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.single_album, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is SingleAlbumView) throw AssertionError("View must be SingleAlbumView")
        AnalyticsUtil.logEvent(Event.SingleAlbumControllerShown)

        view.display(SingleAlbumUiState.Loaded(bucketId))

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents.ofType<SingleAlbumUiEvent.Back>()
                .subscribe { router.handleBack() }

        viewDisposables += sharedEvents.ofType<SingleAlbumUiEvent.SelectPhoto>()
                .subscribe { event ->
                    val popTo = args.getString(KEY_TAG_OF_CONTROLLER_TO_POP_TO)!!
                    router.pushController(RouterTransaction.with(
                            AssignPhotosController(arrayListOf(event.uri), epochDay, type, popTo))
                                                  .using(HorizontalChangeHandler()))
                }

        viewDisposables += sharedEvents.ofType<SingleAlbumUiEvent.SelectionUpdate>()
                .observeOn(RxSchedulers.main())
                .subscribe { event ->
                    val state = when {
                        event.uris.isEmpty() -> SingleAlbumUiState.Loaded(bucketId)
                        else -> SingleAlbumUiState.Selection(bucketId, event.uris)
                    }
                    view.display(state)
                }

        viewDisposables += sharedEvents.ofType<SingleAlbumUiEvent.EndMultiSelect>()
                .observeOn(RxSchedulers.main())
                .subscribe { view.display(SingleAlbumUiState.Loaded(bucketId)) }

        viewDisposables += sharedEvents.ofType<SingleAlbumUiEvent.SaveMultiple>()
                .subscribe { event ->
                    router.pushController(RouterTransaction.with(
                            AssignPhotosController(event.uris, epochDay, type,
                                                   args.getString(KEY_TAG_OF_CONTROLLER_TO_POP_TO)!!))
                                                  .using(HorizontalChangeHandler()))
                }
    }

    override fun onDetach(view: View) {
        viewDisposables.clear()
    }

    companion object {
        private const val KEY_BUCKET_ID = "bucketId"
        private const val KEY_EPOCH_DAY = "epochDay"
        private const val KEY_TYPE = "type"
        private const val KEY_TAG_OF_CONTROLLER_TO_POP_TO = "tagOfControllerToPopTo"
    }
}
