/*
 * Copyright © 2018-2023 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.selectphoto.selectalbum

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.ui.selectphoto.selectalbum.SelectAlbumFragmentArgs
import com.drspaceboo.transtracks.ui.selectphoto.selectalbum.SelectAlbumFragmentDirections
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import io.reactivex.rxjava3.disposables.CompositeDisposable

class SelectAlbumFragment : Fragment(R.layout.select_album) {
    val args: SelectAlbumFragmentArgs by navArgs()

    private var viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()

        val view = view as? AlbumView ?: throw AssertionError("View must be AlbumView")
        AnalyticsUtil.logEvent(Event.SelectAlbumControllerShown)

        view.display(SelectAlbumUiState.Loaded)

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents
            .ofType<SelectAlbumUiEvent.Back>()
            .subscribe { requireActivity().onBackPressed() }

        viewDisposables += sharedEvents
            .ofType<SelectAlbumUiEvent.SelectAlbum>()
            .subscribe { event ->
                findNavController().navigate(
                    SelectAlbumFragmentDirections.actionSingleAlbum(
                        bucketId = event.bucketId,
                        type = args.type,
                        destinationToPopTo = args.destinationToPopTo,
                        epochDay = args.epochDay
                    )
                )
            }
    }

    override fun onDetach() {
        viewDisposables.clear()
        super.onDetach()
    }
}
