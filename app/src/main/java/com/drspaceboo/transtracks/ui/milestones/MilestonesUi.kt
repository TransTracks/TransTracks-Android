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

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.support.constraint.ConstraintLayout
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.util.setGone
import com.drspaceboo.transtracks.util.setVisible
import com.jakewharton.rxbinding2.support.v7.widget.itemClicks
import com.jakewharton.rxbinding2.support.v7.widget.navigationClicks
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import kotterknife.bindView

sealed class MilestonesUiEvent {
    object Back : MilestonesUiEvent()
    data class AddMilestone(val day: Long) : MilestonesUiEvent()
    data class EditMilestone(val id: String) : MilestonesUiEvent()
}

sealed class MilestonesUiState {
    data class Loaded(val initialDay: Long) : MilestonesUiState()

    companion object {
        fun getInitialDay(state: MilestonesUiState): Long = when (state) {
            is Loaded -> state.initialDay
        }
    }
}

class MilestonesView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.milestones_toolbar)

    private val recyclerView: RecyclerView by bindView(R.id.milestones_recycler_view)

    private val emptyMessage: TextView by bindView(R.id.milestones_empty_message)
    private val emptyAdd: View by bindView(R.id.milestones_empty_add)

    private val eventRelay: PublishRelay<MilestonesUiEvent> = PublishRelay.create()
    val events: Observable<MilestonesUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge<MilestonesUiEvent>(
                toolbar.navigationClicks().map { MilestonesUiEvent.Back },
                toolbar.itemClicks().map<MilestonesUiEvent> { item ->
                    return@map when (item.itemId) {
                        R.id.milestones_menu_add -> MilestonesUiEvent.AddMilestone(day)
                        else -> throw IllegalArgumentException("Unhandled menu item id")
                    }
                },
                emptyAdd.clicks().map { MilestonesUiEvent.AddMilestone(day) },
                eventRelay)
    }

    private var day: Long = 0L
    private var layoutManager = LinearLayoutManager(context)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        toolbar.inflateMenu(R.menu.milestones)

        recyclerView.layoutManager = layoutManager
    }

    fun display(state: MilestonesUiState) {
        when (state) {
            is MilestonesUiState.Loaded -> {
                day = state.initialDay

                if (recyclerView.adapter == null) {
                    recyclerView.adapter = MilestonesAdapter(eventRelay, postInitialLoad = { adapter ->
                        val scrollTo = adapter.getPositionOfDay(MilestonesUiState.getInitialDay(state))
                        if (scrollTo != -1) {
                            Handler(Looper.getMainLooper()).post {
                                layoutManager.scrollToPositionWithOffset(scrollTo, 0)
                            }
                        }
                    }, postLoad = { adapter ->
                        if (adapter.itemCount > 0) {
                            setVisible(recyclerView)
                            setGone(emptyMessage, emptyAdd)
                        } else {
                            setVisible(emptyMessage, emptyAdd)
                            setGone(recyclerView)
                        }
                    })
                }
            }
        }
    }
}