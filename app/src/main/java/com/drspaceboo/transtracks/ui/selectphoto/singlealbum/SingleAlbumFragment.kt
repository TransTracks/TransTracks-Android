/*
 * Copyright © 2018-2023 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.selectphoto.singlealbum

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.ui.selectphoto.singlealbum.SingleAlbumFragmentArgs
import com.drspaceboo.transtracks.ui.selectphoto.singlealbum.SingleAlbumFragmentDirections
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.RxSchedulers
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import io.reactivex.rxjava3.disposables.CompositeDisposable

class SingleAlbumFragment : Fragment(R.layout.single_album) {
    val args: SingleAlbumFragmentArgs by navArgs()

    private var viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()
        val view = view as? SingleAlbumView ?: throw AssertionError("View must be SingleAlbumView")
        AnalyticsUtil.logEvent(Event.SingleAlbumControllerShown)

        view.display(SingleAlbumUiState.Loaded(args.bucketId))

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents.ofType<SingleAlbumUiEvent.Back>()
            .subscribe { requireActivity().onBackPressed() }

        viewDisposables += sharedEvents.ofType<SingleAlbumUiEvent.SelectPhoto>()
            .subscribe { event ->
                findNavController().navigate(
                    SingleAlbumFragmentDirections.actionGlobalAssignPhotos(
                        uris = arrayOf(event.uri),
                        type = args.type,
                        destinationToPopTo = args.destinationToPopTo,
                        epochDay = args.epochDay,
                    )
                )
            }

        viewDisposables += sharedEvents.ofType<SingleAlbumUiEvent.SelectionUpdate>()
            .observeOn(RxSchedulers.main())
            .subscribe { event ->
                val state = when {
                    event.uris.isEmpty() -> SingleAlbumUiState.Loaded(args.bucketId)
                    else -> SingleAlbumUiState.Selection(args.bucketId, event.uris)
                }
                view.display(state)
            }

        viewDisposables += sharedEvents.ofType<SingleAlbumUiEvent.EndMultiSelect>()
            .observeOn(RxSchedulers.main())
            .subscribe { view.display(SingleAlbumUiState.Loaded(args.bucketId)) }

        viewDisposables += sharedEvents.ofType<SingleAlbumUiEvent.SaveMultiple>()
            .subscribe { event ->
                findNavController().navigate(
                    SingleAlbumFragmentDirections.actionGlobalAssignPhotos(
                        uris = event.uris.toTypedArray(),
                        type = args.type,
                        destinationToPopTo = args.destinationToPopTo,
                        epochDay = args.epochDay,
                    )
                )
            }
    }

    override fun onDetach() {
        viewDisposables.clear()
        super.onDetach()
    }
}
