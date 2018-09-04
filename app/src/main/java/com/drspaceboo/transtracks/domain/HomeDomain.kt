/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.domain

import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.util.PrefUtil
import com.drspaceboo.transtracks.util.RxSchedulers
import com.drspaceboo.transtracks.util.getDisplayString
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.realm.Realm
import io.realm.Sort
import org.threeten.bp.LocalDate
import org.threeten.bp.Period

sealed class HomeAction {
    object PreviousDay : HomeAction()
    data class LoadDay(val day: LocalDate = LocalDate.now()) : HomeAction()
    object NextDay : HomeAction()
}

sealed class HomeResult {
    data class Loading(val day: LocalDate) : HomeResult()
    data class Loaded(val dayString: String,
                      val showPreviousRecord: Boolean,
                      val showNextRecord: Boolean,
                      val startDate: LocalDate,
                      val currentDate: LocalDate,
                      val facePhotos: List<String>,
                      val bodyPhotos: List<String>,
                      val showAds: Boolean) : HomeResult()
}

class HomeDomain {
    val actions: PublishRelay<HomeAction> = PublishRelay.create()
    val results: Observable<HomeResult> = actions
            .startWith(HomeAction.LoadDay(LocalDate.now()))
            .compose(homeActionsToResults(this))
            .subscribeOn(RxSchedulers.io())
            .observeOn(RxSchedulers.main())
            .replay(1)
            .refCount()
}

fun homeActionsToResults(homeDomain: HomeDomain): ObservableTransformer<HomeAction, HomeResult> {
    fun getCurrentDate(result: HomeResult): LocalDate = when (result) {
        is HomeResult.Loading -> result.day
        is HomeResult.Loaded -> result.currentDate
    }

    fun getLoadedResult(currentDate: LocalDate): HomeResult.Loaded {
        Realm.getDefaultInstance().use { realm ->
            val startDate = PrefUtil.startDate.get()

            val period: Period = startDate.until(currentDate)

            val previousRecordCount = realm.where(Photo::class.java)
                    .lessThan(Photo.FIELD_EPOCH_DAY, currentDate.toEpochDay()).count()
            val nextRecordCount = realm.where(Photo::class.java)
                    .greaterThan(Photo.FIELD_EPOCH_DAY, currentDate.toEpochDay()).count()

            val showPreviousRecord = previousRecordCount > 0 || currentDate.isAfter(LocalDate.now())
                    || currentDate.isAfter(startDate)
            val showNextRecord = nextRecordCount > 0 || currentDate.isBefore(LocalDate.now())
                    || currentDate.isBefore(startDate)

            val facePhotos = realm.where(Photo::class.java).equalTo(Photo.FIELD_EPOCH_DAY, currentDate.toEpochDay())
                    .equalTo(Photo.FIELD_TYPE, Photo.TYPE_BODY).sort(Photo.FIELD_TIMESTAMP).findAll()
                    .map { photo -> photo.filename }

            val bodyPhotos = realm.where(Photo::class.java).equalTo(Photo.FIELD_EPOCH_DAY, currentDate.toEpochDay())
                    .equalTo(Photo.FIELD_TYPE, Photo.TYPE_BODY).sort(Photo.FIELD_TIMESTAMP).findAll()
                    .map { photo -> photo.filename }

            return HomeResult.Loaded(period.getDisplayString(), showPreviousRecord, showNextRecord, startDate, currentDate,
                                     facePhotos, bodyPhotos, PrefUtil.showAds.get())
        }
    }

    fun getNextDate(currentDate: LocalDate): LocalDate {
        Realm.getDefaultInstance().use { realm ->
            val nextPhoto: Photo? = realm.where(Photo::class.java)
                    .greaterThan(Photo.FIELD_EPOCH_DAY, currentDate.toEpochDay())
                    .sort(Photo.FIELD_EPOCH_DAY)
                    .findFirst()

            if (nextPhoto != null) {
                return LocalDate.ofEpochDay(nextPhoto.epochDay)
            }

            val startDate = PrefUtil.startDate.get()
            val today = LocalDate.now()

            @Suppress("LiftReturnOrAssignment") //Reads better without lifting out the returns
            if (startDate.isAfter(currentDate) && today.isAfter(currentDate)) {
                if (startDate.isBefore(today)) {
                    return startDate
                } else {
                    return today
                }
            } else if (startDate.isAfter(currentDate)) {
                return startDate
            } else if (today.isAfter(currentDate)) {
                return today
            } else {
                return currentDate
            }
        }
    }

    fun getPreviousDate(currentDate: LocalDate): LocalDate {
        Realm.getDefaultInstance().use { realm ->
            val previousPhoto: Photo? = realm.where(Photo::class.java)
                    .lessThan(Photo.FIELD_EPOCH_DAY, currentDate.toEpochDay())
                    .sort(Photo.FIELD_EPOCH_DAY, Sort.DESCENDING)
                    .findFirst()

            if (previousPhoto != null) {
                return LocalDate.ofEpochDay(previousPhoto.epochDay)
            }

            val startDate = PrefUtil.startDate.get()
            val today = LocalDate.now()

            @Suppress("LiftReturnOrAssignment") //Reads better without lifting out the returns
            if (startDate.isBefore(currentDate) && today.isBefore(currentDate)) {
                if (startDate.isAfter(today)) {
                    return startDate
                } else {
                    return today
                }
            } else if (startDate.isBefore(currentDate)) {
                return startDate
            } else if (today.isBefore(currentDate)) {
                return today
            } else {
                return currentDate
            }
        }
    }

    return ObservableTransformer { actions ->
        actions.scan<HomeResult>(HomeResult.Loading(LocalDate.now())) { previousResult, action ->
            return@scan when (action) {
                HomeAction.PreviousDay -> getLoadedResult(getPreviousDate(getCurrentDate(previousResult)))

                is HomeAction.LoadDay -> getLoadedResult(getCurrentDate(previousResult))

                HomeAction.NextDay -> getLoadedResult(getNextDate(getCurrentDate(previousResult)))
            }
        }
    }
}
