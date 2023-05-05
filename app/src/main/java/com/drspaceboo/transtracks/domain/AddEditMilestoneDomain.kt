/*
 * Copyright © 2020 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.domain

import com.drspaceboo.transtracks.data.Milestone
import com.drspaceboo.transtracks.domain.AddEditMilestoneAction.DateUpdated
import com.drspaceboo.transtracks.domain.AddEditMilestoneAction.DescriptionUpdate
import com.drspaceboo.transtracks.domain.AddEditMilestoneAction.InitialAdd
import com.drspaceboo.transtracks.domain.AddEditMilestoneAction.InitialEdit
import com.drspaceboo.transtracks.domain.AddEditMilestoneAction.TitleUpdate
import com.drspaceboo.transtracks.domain.AddEditMilestoneResult.Display
import com.drspaceboo.transtracks.domain.AddEditMilestoneResult.Loading
import com.drspaceboo.transtracks.domain.AddEditMilestoneResult.UnableToFindMilestone
import com.drspaceboo.transtracks.util.RxSchedulers
import com.drspaceboo.transtracks.util.default
import com.drspaceboo.transtracks.util.openDefault
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration

sealed class AddEditMilestoneAction {
    data class InitialAdd(val epochDate: Long) : AddEditMilestoneAction()
    data class InitialEdit(val milestoneId: String) : AddEditMilestoneAction()

    data class DateUpdated(val newEpochDate: Long) : AddEditMilestoneAction()
    data class TitleUpdate(val newTitle: String) : AddEditMilestoneAction()
    data class DescriptionUpdate(val newDescription: String) : AddEditMilestoneAction()
}

sealed class AddEditMilestoneResult {
    object Loading : AddEditMilestoneResult()

    data class Display(
        val epochDay: Long,
        val title: String,
        val description: String,
        val milestoneId: String? = null
    ) : AddEditMilestoneResult()

    object UnableToFindMilestone : AddEditMilestoneResult()
}

class AddEditMilestoneDomain {
    val actions: PublishRelay<AddEditMilestoneAction> = PublishRelay.create()
    val results: Observable<AddEditMilestoneResult> = actions
        .compose(addEditMilestoneActionsToResults)
        .startWith(Loading)
        .subscribeOn(RxSchedulers.io())
        .observeOn(RxSchedulers.main())
        .replay(1)
        .refCount()

    companion object {
        val addEditMilestoneActionsToResults =
            ObservableTransformer<AddEditMilestoneAction, AddEditMilestoneResult> { actions ->
                actions.scan<AddEditMilestoneResult>(Loading) { previousResult, action ->
                    return@scan when (action) {
                        is InitialAdd -> Display(action.epochDate, title = "", description = "")

                        is InitialEdit -> {
                            val realm = Realm.openDefault()

                            val milestone: Milestone? = realm.query(
                                Milestone::class, "${Milestone.FIELD_ID} == ${action.milestoneId}"
                            )
                                .first()
                                .find()

                            realm.close()

                            when {
                                milestone != null -> Display(
                                    milestone.epochDay,
                                    milestone.title,
                                    milestone.description,
                                    action.milestoneId
                                )

                                else -> UnableToFindMilestone
                            }
                        }

                        is DateUpdated -> when (previousResult) {
                            is Display -> previousResult.copy(epochDay = action.newEpochDate)
                            else -> previousResult
                        }

                        is TitleUpdate -> when (previousResult) {
                            is Display -> previousResult.copy(title = action.newTitle)
                            else -> previousResult
                        }

                        is DescriptionUpdate -> when (previousResult) {
                            is Display -> previousResult.copy(description = action.newDescription)
                            else -> previousResult
                        }
                    }
                }
            }
    }
}
