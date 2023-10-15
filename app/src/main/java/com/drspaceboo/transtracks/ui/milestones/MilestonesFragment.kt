/*
 * Copyright © 2018-2023 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.milestones

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.TransTracksApp
import com.drspaceboo.transtracks.ui.milestones.MilestonesFragmentDirections
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.settings.SettingsManager
import io.reactivex.rxjava3.disposables.CompositeDisposable

class MilestonesFragment : Fragment(R.layout.milestones) {
    val args: MilestonesFragmentArgs by navArgs()

    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()
        val view = view as? MilestonesView ?: throw AssertionError("View must be MilestonesView")

        AnalyticsUtil.logEvent(Event.MilestonesControllerShown)

        view.display(
            MilestonesUiState.Loaded(
                args.initialDay,
                TransTracksApp.hasConsentToShowAds() && SettingsManager.showAds()
            )
        )

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents.ofType<MilestonesUiEvent.Back>()
            .subscribe { requireActivity().onBackPressed() }

        viewDisposables += sharedEvents.ofType<MilestonesUiEvent.AddMilestone>()
            .subscribe { event ->
                findNavController()
                    .navigate(MilestonesFragmentDirections.actionAddMilestone(event.day))
            }

        viewDisposables += sharedEvents.ofType<MilestonesUiEvent.EditMilestone>()
            .subscribe { event ->
                findNavController()
                    .navigate(MilestonesFragmentDirections.actionEditMilestone(event.id))
            }
    }

    override fun onDetach() {
        viewDisposables.clear()
        super.onDetach()
    }
}
