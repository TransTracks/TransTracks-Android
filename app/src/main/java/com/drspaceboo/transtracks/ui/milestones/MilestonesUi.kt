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
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.gone
import com.drspaceboo.transtracks.util.loadAd
import com.drspaceboo.transtracks.util.setGone
import com.drspaceboo.transtracks.util.setVisible
import com.drspaceboo.transtracks.util.toV3
import com.drspaceboo.transtracks.util.visible
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.jakewharton.rxbinding3.appcompat.itemClicks
import com.jakewharton.rxbinding3.appcompat.navigationClicks
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxrelay3.PublishRelay
import io.reactivex.rxjava3.core.Observable
import kotterknife.bindView

sealed class MilestonesUiEvent {
    object Back : MilestonesUiEvent()
    data class AddMilestone(val day: Long) : MilestonesUiEvent()
    data class EditMilestone(val id: String) : MilestonesUiEvent()
}

sealed class MilestonesUiState {
    data class Loaded(val initialDay: Long, val showAds: Boolean) : MilestonesUiState()

    companion object {
        fun getInitialDay(state: MilestonesUiState): Long = when (state) {
            is Loaded -> state.initialDay
        }
    }
}

class MilestonesView(
    context: Context, attributeSet: AttributeSet
) : ConstraintLayout(context, attributeSet) {
    private val toolbar: Toolbar by bindView(R.id.milestones_toolbar)

    private val recyclerView: RecyclerView by bindView(R.id.milestones_recycler_view)

    private val emptyMessage: TextView by bindView(R.id.milestones_empty_message)
    private val emptyAdd: View by bindView(R.id.milestones_empty_add)

    private val adViewLayout: FrameLayout by bindView(R.id.milestones_ad_layout)

    private val eventRelay: PublishRelay<MilestonesUiEvent> = PublishRelay.create()
    val events: Observable<MilestonesUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge<MilestonesUiEvent>(
            toolbar.navigationClicks().toV3().map { MilestonesUiEvent.Back },
            toolbar.itemClicks().toV3().map<MilestonesUiEvent> { item ->
                return@map when (item.itemId) {
                    R.id.milestones_menu_add -> MilestonesUiEvent.AddMilestone(day)
                    else -> throw IllegalArgumentException("Unhandled menu item id")
                }
            },
            emptyAdd.clicks().toV3().map { MilestonesUiEvent.AddMilestone(day) },
            eventRelay
        )
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
                    recyclerView.adapter = MilestonesAdapter(
                        eventRelay,
                        postInitialLoad = { adapter ->
                            val scrollTo =
                                adapter.getPositionOfDay(MilestonesUiState.getInitialDay(state))
                            if (scrollTo != -1) {
                                Handler(Looper.getMainLooper()).post {
                                    layoutManager.scrollToPositionWithOffset(scrollTo, 0)
                                }
                            }
                        },
                        postLoad = { adapter ->
                            if (adapter.itemCount > 0) {
                                setVisible(recyclerView)
                                setGone(emptyMessage, emptyAdd)
                            } else {
                                setVisible(emptyMessage, emptyAdd)
                                setGone(recyclerView)
                            }
                        })
                }

                if (state.showAds) {
                    adViewLayout.visible()

                    if (adViewLayout.childCount <= 0) {
                        AdView(context).apply {
                            adUnitId = getString(R.string.ADS_MILESTONES_AD_ID)
                            adViewLayout.addView(this)
                            loadAd(context)
                            adListener = object : AdListener() {
                                override fun onAdFailedToLoad(error: LoadAdError) {
                                    adViewLayout.gone()
                                }
                            }
                        }
                    }
                } else {
                    adViewLayout.gone()
                }
            }
        }
    }
}
