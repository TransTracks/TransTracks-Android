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
import android.support.constraint.ConstraintLayout
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.util.getColor
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.gone
import com.drspaceboo.transtracks.util.loadAd
import com.drspaceboo.transtracks.util.nullAllElements
import com.drspaceboo.transtracks.util.setVisibleOrInvisible
import com.drspaceboo.transtracks.util.toFullDateString
import com.drspaceboo.transtracks.util.visible
import com.google.android.gms.ads.AdView
import com.jakewharton.rxbinding2.view.clicks
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import kotterknife.bindView
import org.threeten.bp.LocalDate
import java.io.File

sealed class HomeUiEvent {
    object SelectPhoto : HomeUiEvent()
    object Settings : HomeUiEvent()
    object PreviousRecord : HomeUiEvent()
    object NextRecord : HomeUiEvent()
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
                      val facePhotos: List<Pair<String, String>>,
                      val bodyPhotos: List<Pair<String, String>>,
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

    private val faceGallery: Button by bindView(R.id.home_face_gallery)
    private val faceFirstImage: ImageView by bindView(R.id.home_face_first_image)
    private val faceSecondImage: ImageView by bindView(R.id.home_face_second_image)
    private val faceThirdLayout: ViewGroup by bindView(R.id.home_face_third_image_layout)
    private val faceThirdImage: ImageView by bindView(R.id.home_face_third_image)
    private val faceExtraImages: TextView by bindView(R.id.home_face_extra_images)

    private val bodyGallery: Button by bindView(R.id.home_body_gallery)
    private val bodyFirstImage: ImageView by bindView(R.id.home_body_first_image)
    private val bodySecondImage: ImageView by bindView(R.id.home_body_second_image)
    private val bodyThirdLayout: ViewGroup by bindView(R.id.home_body_third_image_layout)
    private val bodyThirdImage: ImageView by bindView(R.id.home_body_third_image)
    private val bodyExtraImages: TextView by bindView(R.id.home_body_extra_images)

    private val adViewLayout: ViewGroup by bindView(R.id.home_ad_layout)
    private val adView: AdView by bindView(R.id.home_ad_view)

    val events: Observable<HomeUiEvent> by lazy(LazyThreadSafetyMode.NONE) {
        Observable.mergeArray(
                takePhoto.clicks().map { HomeUiEvent.SelectPhoto },
                settings.clicks().map { HomeUiEvent.Settings },
                previousRecord.clicks().map { HomeUiEvent.PreviousRecord },
                nextRecord.clicks().map { HomeUiEvent.NextRecord },
                faceGallery.clicks().map { HomeUiEvent.FaceGallery(date.toEpochDay()) },
                faceFirstImage.clicks().filter { facePhotoIds[0] != null }.map {
                    HomeUiEvent.ImageClick(facePhotoIds[0]!!)
                },
                faceSecondImage.clicks().filter { facePhotoIds[1] != null }.map {
                    HomeUiEvent.ImageClick(facePhotoIds[1]!!)
                },
                faceThirdImage.clicks().map {
                    return@map when (facePhotoIds[2]) {
                        null -> HomeUiEvent.AddPhoto(date, Photo.TYPE_FACE)
                        else -> HomeUiEvent.ImageClick(facePhotoIds[2]!!)
                    }
                },
                bodyGallery.clicks().map { HomeUiEvent.BodyGallery(date.toEpochDay()) },
                bodyFirstImage.clicks().filter { bodyPhotoIds[0] != null }.map {
                    HomeUiEvent.ImageClick(bodyPhotoIds[0]!!)
                },
                bodySecondImage.clicks().filter { bodyPhotoIds[1] != null }.map {
                    HomeUiEvent.ImageClick(bodyPhotoIds[1]!!)
                },
                bodyThirdImage.clicks().map {
                    return@map when (bodyPhotoIds[2]) {
                        null -> HomeUiEvent.AddPhoto(date, Photo.TYPE_BODY)
                        else -> HomeUiEvent.ImageClick(bodyPhotoIds[2]!!)
                    }
                })
    }

    private val facePhotoIds = Array<String?>(3) { _ -> null }
    private val bodyPhotoIds = Array<String?>(3) { _ -> null }

    private var date = LocalDate.now()

    fun display(state: HomeUiState) {
        fun setAddAnotherBodyImage() {
            bodyThirdLayout.setBackgroundColor(getColor(R.color.transparent))
            Picasso.get().cancelRequest(bodyThirdImage)
            bodyThirdImage.setImageResource(R.drawable.add)
            bodyThirdImage.visible()
            bodyExtraImages.gone()
        }

        fun setAddAnotherFaceImage() {
            faceThirdLayout.setBackgroundColor(getColor(R.color.transparent))
            Picasso.get().cancelRequest(faceThirdImage)
            faceThirdImage.setImageResource(R.drawable.add)
            faceThirdImage.visible()
            faceExtraImages.gone()
        }

        facePhotoIds.nullAllElements()
        bodyPhotoIds.nullAllElements()

        when (state) {
            is HomeUiState.Loaded -> {
                date = state.currentDate

                day.text = state.dayString

                previousRecord.setVisibleOrInvisible(state.showPreviousRecord)
                nextRecord.setVisibleOrInvisible(state.showNextRecord)

                startDate.text = startDate.getString(R.string.start_date,
                                                     state.startDate.toFullDateString(startDate.context))
                currentDate.text = currentDate.getString(R.string.current_date,
                                                         state.currentDate.toFullDateString(currentDate.context))

                if (state.facePhotos.isNotEmpty()) {
                    val (facePhoto0Id, facePhoto0Path) = state.facePhotos[0]
                    facePhotoIds[0] = facePhoto0Id

                    Picasso.get().load(File(facePhoto0Path)).fit().centerCrop()
                            .into(faceFirstImage)
                    faceFirstImage.visible()

                    if (state.facePhotos.size > 1) {
                        val (facePhoto1Id, facePhoto1Path) = state.facePhotos[1]
                        facePhotoIds[1] = facePhoto1Id

                        Picasso.get().load(File(facePhoto1Path)).fit().centerCrop()
                                .into(faceSecondImage)
                        faceSecondImage.visible()

                        if (state.facePhotos.size > 2) {
                            val (facePhoto2Id, facePhoto2Path) = state.facePhotos[2]
                            facePhotoIds[2] = facePhoto2Id

                            faceThirdLayout.setBackgroundColor(getColor(R.color.transparent_white_25))
                            Picasso.get().load(File(facePhoto2Path)).fit().centerCrop()
                                    .into(faceThirdImage)
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
                    val (bodyPhoto0Id, bodyPhoto0Path) = state.bodyPhotos[0]
                    bodyPhotoIds[0] = bodyPhoto0Id

                    Picasso.get().load(File(bodyPhoto0Path)).fit().centerCrop()
                            .into(bodyFirstImage)
                    bodyFirstImage.visible()

                    if (state.bodyPhotos.size > 1) {
                        val (bodyPhoto1Id, bodyPhoto1Path) = state.bodyPhotos[1]
                        bodyPhotoIds[1] = bodyPhoto1Id

                        Picasso.get().load(File(bodyPhoto1Path)).fit().centerCrop()
                                .into(bodySecondImage)
                        bodySecondImage.visible()

                        if (state.bodyPhotos.size > 2) {
                            val (bodyPhoto2Id, bodyPhoto2Path) = state.bodyPhotos[2]
                            bodyPhotoIds[2] = bodyPhoto2Id

                            bodyThirdLayout.setBackgroundColor(getColor(R.color.transparent_white_25))
                            Picasso.get().load(File(bodyPhoto2Path)).fit().centerCrop()
                                    .into(bodyThirdImage)
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
                    adView.loadAd()
                } else {
                    adViewLayout.gone()
                }
            }
        }
    }
}
