/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.home

import android.content.Context
import android.transition.ChangeBounds
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.ui.widget.SwipeGestureListener
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.gone
import com.drspaceboo.transtracks.util.loadAd
import com.drspaceboo.transtracks.util.nullAllElements
import com.drspaceboo.transtracks.util.setVisibleOrInvisible
import com.drspaceboo.transtracks.util.toFullDateString
import com.drspaceboo.transtracks.util.toV3
import com.drspaceboo.transtracks.util.visible
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxrelay3.PublishRelay
import io.reactivex.rxjava3.core.Observable
import kotterknife.bindView
import java.time.LocalDate

sealed class HomeUiEvent {
    object SelectPhoto : HomeUiEvent()
    object Settings : HomeUiEvent()
    object PreviousRecord : HomeUiEvent()
    object NextRecord : HomeUiEvent()
    data class Milestones(val day: Long) : HomeUiEvent()
    data class FaceGallery(val day: Long) : HomeUiEvent()
    data class BodyGallery(val day: Long) : HomeUiEvent()
    data class ImageClick(val photoId: String) : HomeUiEvent()
    data class AddPhoto(val currentDate: LocalDate, @Photo.Type val type: Int) : HomeUiEvent()
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Loaded(val dayString: String,
                      val showPreviousRecord: Boolean,
                      val showNextRecord: Boolean,
                      val startDate: LocalDate,
                      val currentDate: LocalDate,
                      val hasMilestones: Boolean,
                      val showAds: Boolean) : HomeUiState()
}

class HomeView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val takePhoto: ImageButton by bindView(R.id.home_take_photo)
    private val settings: ImageButton by bindView(R.id.home_settings)

    private val day: TextView by bindView(R.id.home_day_title)

    private val previousRecord: ImageButton by bindView(R.id.home_previous_record)
    private val nextRecord: ImageButton by bindView(R.id.home_next_record)

    private val startDate: TextView by bindView(R.id.home_start_date)
    private val currentDate: TextView by bindView(R.id.home_current_date)

    private val milestones: ImageButton by bindView(R.id.home_milestones)

    private val faceGallery: Button by bindView(R.id.home_face_gallery)
    private val faceRecyclerView: RecyclerView by bindView(R.id.home_face_images)

    private val bodyGallery: Button by bindView(R.id.home_body_gallery)
    private val bodyRecyclerView: RecyclerView by bindView(R.id.home_body_images)

    private val adViewLayout: ViewGroup by bindView(R.id.home_ad_layout)

    private val eventRelay: PublishRelay<HomeUiEvent> = PublishRelay.create()
    val events: Observable<HomeUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.mergeArray(
                takePhoto.clicks().toV3().map { HomeUiEvent.SelectPhoto },
                settings.clicks().toV3().map { HomeUiEvent.Settings },
                previousRecord.clicks().toV3().map { HomeUiEvent.PreviousRecord },
                nextRecord.clicks().toV3().map { HomeUiEvent.NextRecord },
                milestones.clicks().toV3().map { HomeUiEvent.Milestones(date.toEpochDay()) },
                faceGallery.clicks().toV3().map { HomeUiEvent.FaceGallery(date.toEpochDay()) },
                bodyGallery.clicks().toV3().map { HomeUiEvent.BodyGallery(date.toEpochDay()) },
                eventRelay)
    }

    private val facePhotoIds = Array<String?>(3) { _ -> null }
    private val bodyPhotoIds = Array<String?>(3) { _ -> null }

    private var date = LocalDate.MIN
    private var hasPrevious = false
    private var hasNext = true

    private val swipeListener = object : SwipeGestureListener() {
        override fun swipeLeft(): Boolean {
            if (hasPrevious) {
                eventRelay.accept(HomeUiEvent.PreviousRecord)
            }

            return hasPrevious
        }

        override fun swipeRight(): Boolean {
            if (hasNext) {
                eventRelay.accept(HomeUiEvent.NextRecord)
            }
            return hasNext
        }
    }
    private val gestureDetector = GestureDetector(context, swipeListener)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }

        faceRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL,
                                                             false)
        bodyRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL,
                                                             false)
    }

    fun display(state: HomeUiState) {
        facePhotoIds.nullAllElements()
        bodyPhotoIds.nullAllElements()

        when (state) {
            is HomeUiState.Loading -> {
                //TODO update to set to loading state instead of resetting and setting
            }

            is HomeUiState.Loaded -> {
                date = state.currentDate

                day.text = state.dayString

                hasPrevious = state.showPreviousRecord
                hasNext = state.showNextRecord

                previousRecord.setVisibleOrInvisible(state.showPreviousRecord)
                nextRecord.setVisibleOrInvisible(state.showNextRecord)

                startDate.text = startDate.getString(
                    R.string.start_date, state.startDate.toFullDateString(startDate.context)
                )
                currentDate.text = currentDate.getString(
                    R.string.current_date, state.currentDate.toFullDateString(currentDate.context)
                )

                val milestonesRes = when (state.hasMilestones) {
                    true -> R.drawable.ic_milestone_selected
                    false -> R.drawable.ic_milestone_unselected
                }
                milestones.setImageResource(milestonesRes)

                faceRecyclerView.adapter = HomeGalleryAdapter(
                    state.currentDate, Photo.TYPE_FACE,
                    eventRelay
                )
                bodyRecyclerView.adapter = HomeGalleryAdapter(
                    state.currentDate, Photo.TYPE_BODY,
                    eventRelay
                )

                if (state.showAds) {
                    adViewLayout.visible()

                    if (adViewLayout.childCount <= 0) {
                        AdView(context).apply {
                            adUnitId = getString(R.string.ADS_HOME_AD_ID)
                            adViewLayout.addView(this)
                            loadAd(context)
                            adListener = object : AdListener() {
                                override fun onAdFailedToLoad(error: LoadAdError) {
                                    val transitionSet = TransitionSet()
                                        .addTransition(
                                            Slide().addTarget(adViewLayout)
                                                .addTarget(faceGallery)
                                                .addTarget(bodyRecyclerView)
                                        )
                                        .addTransition(
                                            ChangeBounds().addTarget(faceRecyclerView)
                                                .addTarget(bodyRecyclerView)
                                        )

                                    TransitionManager.beginDelayedTransition(this@HomeView, transitionSet)
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
