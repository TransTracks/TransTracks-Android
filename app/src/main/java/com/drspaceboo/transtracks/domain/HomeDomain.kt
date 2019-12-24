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

import com.drspaceboo.transtracks.data.Milestone
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.util.settings.PrefUtil
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
    object ReloadDay : HomeAction()
}

sealed class HomeResult {
    data class Loading(val day: LocalDate) : HomeResult()
    data class Loaded(val dayString: String,
                      val showPreviousRecord: Boolean,
                      val showNextRecord: Boolean,
                      val startDate: LocalDate,
                      val currentDate: LocalDate,
                      val hasMilestones: Boolean,
                      val showAds: Boolean) : HomeResult()
}

class HomeDomain {
    val actions: PublishRelay<HomeAction> = PublishRelay.create()
    val results: Observable<HomeResult> = actions
            .startWith(HomeAction.LoadDay(LocalDate.now()))
            .compose(homeActionsToResults())
            .subscribeOn(RxSchedulers.io())
            .observeOn(RxSchedulers.main())
            .replay(1)
            .refCount()
}

fun homeActionsToResults(): ObservableTransformer<HomeAction, HomeResult> {
    fun getCurrentDate(result: HomeResult): LocalDate = when (result) {
        is HomeResult.Loading -> result.day
        is HomeResult.Loaded -> result.currentDate
    }

    fun getLoadedResult(currentDate: LocalDate): HomeResult.Loaded {
        Realm.getDefaultInstance().use { realm ->
            val startDate = PrefUtil.startDate.get()

            val period: Period = startDate.until(currentDate)
            val currentDateEpochDay = currentDate.toEpochDay()

            val previousRecordCount = realm.where(Photo::class.java)
                    .lessThan(Photo.FIELD_EPOCH_DAY, currentDateEpochDay).count() +
                    realm.where(Milestone::class.java)
                            .lessThan(Milestone.FIELD_EPOCH_DAY, currentDateEpochDay).count()
            val nextRecordCount = realm.where(Photo::class.java)
                    .greaterThan(Photo.FIELD_EPOCH_DAY, currentDateEpochDay).count() +
                    realm.where(Milestone::class.java)
                            .greaterThan(Milestone.FIELD_EPOCH_DAY, currentDateEpochDay).count()

            val showPreviousRecord = previousRecordCount > 0 || currentDate.isAfter(LocalDate.now())
                    || currentDate.isAfter(startDate)
            val showNextRecord = nextRecordCount > 0 || currentDate.isBefore(LocalDate.now())
                    || currentDate.isBefore(startDate)

            val hasMilestones = realm.where(Milestone::class.java)
                    .equalTo(Milestone.FIELD_EPOCH_DAY, currentDateEpochDay).findFirst() != null

            return HomeResult.Loaded(period.getDisplayString(), showPreviousRecord, showNextRecord,
                                     startDate, currentDate, hasMilestones, PrefUtil.showAds.get())
        }
    }

    fun getNextDate(currentDate: LocalDate): LocalDate {
        Realm.getDefaultInstance().use { realm ->
            val nextPhoto: Photo? = realm.where(Photo::class.java)
                    .greaterThan(Photo.FIELD_EPOCH_DAY, currentDate.toEpochDay())
                    .sort(Photo.FIELD_EPOCH_DAY)
                    .findFirst()

            val nextMilestone: Milestone? = realm.where(Milestone::class.java)
                    .greaterThan(Milestone.FIELD_EPOCH_DAY, currentDate.toEpochDay())
                    .sort(Milestone.FIELD_EPOCH_DAY)
                    .findFirst()

            if (nextPhoto != null || nextMilestone != null) {
                return when {
                    nextPhoto != null && nextMilestone == null ->
                        LocalDate.ofEpochDay(nextPhoto.epochDay)

                    nextPhoto == null && nextMilestone != null ->
                        LocalDate.ofEpochDay(nextMilestone.epochDay)

                    nextPhoto!!.epochDay < nextMilestone!!.epochDay ->
                        LocalDate.ofEpochDay(nextPhoto.epochDay)

                    else -> LocalDate.ofEpochDay(nextMilestone.epochDay)
                }
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

            val previousMilestone: Milestone? = realm.where(Milestone::class.java)
                    .lessThan(Milestone.FIELD_EPOCH_DAY, currentDate.toEpochDay())
                    .sort(Milestone.FIELD_EPOCH_DAY, Sort.DESCENDING)
                    .findFirst()

            if (previousPhoto != null || previousMilestone != null) {
                return when {
                    previousPhoto != null && previousMilestone == null ->
                        LocalDate.ofEpochDay(previousPhoto.epochDay)

                    previousPhoto == null && previousMilestone != null ->
                        LocalDate.ofEpochDay(previousMilestone.epochDay)

                    previousPhoto!!.epochDay > previousMilestone!!.epochDay ->
                        LocalDate.ofEpochDay(previousPhoto.epochDay)

                    else -> LocalDate.ofEpochDay(previousMilestone.epochDay)
                }
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

                is HomeAction.LoadDay -> getLoadedResult(action.day)

                HomeAction.NextDay -> getLoadedResult(getNextDate(getCurrentDate(previousResult)))

                HomeAction.ReloadDay -> getLoadedResult(getCurrentDate(previousResult))
            }
        }
    }
}
