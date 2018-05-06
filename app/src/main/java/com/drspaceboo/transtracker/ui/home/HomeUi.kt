/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracker.ui.home

import android.content.Context
import android.net.Uri
import android.support.constraint.ConstraintLayout
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.drspaceboo.transtracker.R
import com.drspaceboo.transtracker.util.*
import com.google.android.gms.ads.AdView
import com.jakewharton.rxbinding2.view.clicks
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import kotterknife.bindView
import org.threeten.bp.LocalDate

sealed class HomeUiEvent {
    object SelectPhoto : HomeUiEvent()
    object Gallery : HomeUiEvent()
    object Settings : HomeUiEvent()
}

sealed class HomeUiState {
    data class Loaded(val dayNumber: Int,
                      val startDate: LocalDate,
                      val currentDate: LocalDate,
                      val facePhotos: List<Uri>,
                      val bodyPhotos: List<Uri>,
                      val showAds: Boolean) : HomeUiState()
}

class HomeView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {

    private val takePhoto: ImageButton by bindView(R.id.home_take_photo)
    private val settings: ImageButton by bindView(R.id.home_settings)

    private val day: TextView by bindView(R.id.home_day_title)

    private val startDate: TextView by bindView(R.id.home_start_date)
    private val currentDate: TextView by bindView(R.id.home_current_date)

    private val faceGallery: Button by bindView(R.id.home_face_gallery)
    private val faceFirstImage: ImageView by bindView(R.id.home_face_first_image)
    private val faceSecondImage: ImageView by bindView(R.id.home_face_second_image)
    private val faceThirdImage: ImageView by bindView(R.id.home_face_third_image)
    private val faceExtraImages: TextView by bindView(R.id.home_face_extra_images)

    private val bodyGallery: Button by bindView(R.id.home_body_gallery)
    private val bodyFirstImage: ImageView by bindView(R.id.home_body_first_image)
    private val bodySecondImage: ImageView by bindView(R.id.home_body_second_image)
    private val bodyThirdImage: ImageView by bindView(R.id.home_body_third_image)
    private val bodyExtraImages: TextView by bindView(R.id.home_body_extra_images)

    private val adViewLayout: ViewGroup by bindView(R.id.home_ad_layout)
    private val adView: AdView by bindView(R.id.home_ad_view)

    val events: Observable<HomeUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.merge(takePhoto.clicks().map { HomeUiEvent.SelectPhoto },
                         faceGallery.clicks().map { HomeUiEvent.Gallery },
                         bodyGallery.clicks().map { HomeUiEvent.Gallery },
                         settings.clicks().map { HomeUiEvent.Settings })
    }

    fun display(state: HomeUiState) {
        fun setAddAnotherBodyImage() {
            Picasso.get().load(R.drawable.ic_add_circle_white_24dp).into(bodyThirdImage)
            bodyThirdImage.visible()
            bodyExtraImages.gone()
        }

        fun setAddAnotherFaceImage() {
            Picasso.get().load(R.drawable.ic_add_circle_white_24dp).into(faceThirdImage)
            faceThirdImage.visible()
            faceExtraImages.gone()
        }

        when (state) {
            is HomeUiState.Loaded -> {
                day.text = day.getString(R.string.day_number, state.dayNumber)
                startDate.text = startDate.getString(R.string.start_date,
                                                     state.startDate.toFullDateString(startDate.context))
                currentDate.text = currentDate.getString(R.string.current_date,
                                                         state.currentDate.toFullDateString(currentDate.context))

                if (state.facePhotos.isNotEmpty()) {
                    Picasso.get().load(state.facePhotos[0]).into(faceFirstImage)
                    faceFirstImage.visible()

                    if (state.facePhotos.size > 1) {
                        Picasso.get().load(state.facePhotos[1]).into(faceSecondImage)
                        faceSecondImage.visible()

                        if (state.facePhotos.size > 2) {
                            Picasso.get().load(state.facePhotos[1]).into(faceThirdImage)
                            faceThirdImage.visible()

                            if (state.facePhotos.size > 3) {
                                val extraImages = state.facePhotos.size - 3
                                faceExtraImages.text = getString(R.string.extra_photos, extraImages)
                                faceExtraImages.visible()
                            } else {
                                faceExtraImages.gone()
                            }
                        } else {
                            setAddAnotherFaceImage()
                        }
                    } else {
                        faceSecondImage.gone()
                        setAddAnotherFaceImage()
                    }
                } else {
                    faceFirstImage.gone()
                    faceSecondImage.gone()
                    setAddAnotherFaceImage()
                }

                if (state.bodyPhotos.isNotEmpty()) {
                    Picasso.get().load(state.bodyPhotos[0]).into(bodyFirstImage)
                    bodyFirstImage.visible()

                    if (state.bodyPhotos.size > 1) {
                        Picasso.get().load(state.bodyPhotos[1]).into(bodySecondImage)
                        bodySecondImage.visible()

                        if (state.bodyPhotos.size > 2) {
                            Picasso.get().load(state.bodyPhotos[1]).into(bodyThirdImage)
                            bodyThirdImage.visible()

                            if (state.bodyPhotos.size > 3) {
                                val extraImages = state.bodyPhotos.size - 3
                                bodyExtraImages.text = getString(R.string.extra_photos, extraImages)
                                bodyExtraImages.visible()
                            } else {
                                bodyExtraImages.gone()
                            }
                        } else {
                            setAddAnotherBodyImage()
                        }
                    } else {
                        bodySecondImage.gone()
                        setAddAnotherBodyImage()
                    }
                } else {
                    bodyFirstImage.gone()
                    bodySecondImage.gone()
                    setAddAnotherBodyImage()
                }

                if (state.showAds) {
                    adViewLayout.visible()
                    adView.safeLoadAd()
                } else {
                    adViewLayout.gone()
                }
            }
        }
    }
}
