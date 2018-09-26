/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.milestones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.ui.addeditmilestone.AddEditMilestoneController
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import io.reactivex.disposables.CompositeDisposable

class MilestonesController(args: Bundle) : Controller(args) {
    constructor(initialDay: Long) : this(Bundle().apply {
        putLong(KEY_INITIAL_DAY, initialDay)
    })

    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.milestones, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is MilestonesView) throw AssertionError("View must be MilestonesView")

        view.display(MilestonesUiState.Loaded(args.getLong(KEY_INITIAL_DAY)))

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents.ofType<MilestonesUiEvent.Back>()
                .subscribe { router.handleBack() }

        viewDisposables += sharedEvents.ofType<MilestonesUiEvent.AddMilestone>()
                .subscribe { event ->
                    router.pushController(
                            RouterTransaction.with(AddEditMilestoneController(event.day)))
                }

        viewDisposables += sharedEvents.ofType<MilestonesUiEvent.EditMilestone>()
                .subscribe { event ->
                    router.pushController(
                            RouterTransaction.with(AddEditMilestoneController(event.id)))
                }
    }

    override fun onDetach(view: View) {
        viewDisposables.clear()
    }

    companion object {
        private const val KEY_INITIAL_DAY = "initialDay"

        const val TAG = "MilestonesController"
    }
}
