/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.addeditmilestone

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.bluelinelabs.conductor.Controller
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.data.Milestone
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.dismissIfShowing
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import com.google.android.material.snackbar.Snackbar
import io.reactivex.disposables.CompositeDisposable
import io.realm.Realm
import org.threeten.bp.LocalDate

class AddEditMilestoneController(args: Bundle) : Controller(args) {
    constructor(initialDay: Long) : this(Bundle().apply {
        putLong(KEY_INITIAL_DAY, initialDay)
    })

    constructor(milestoneId: String) : this(Bundle().apply {
        putString(KEY_MILESTONE_ID, milestoneId)
    })

    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    private var confirmDeleteDialog: AlertDialog? = null

    private val initialDay: Long = args.getLong(KEY_INITIAL_DAY, -1)
    private val milestoneId: String? = args.getString(KEY_MILESTONE_ID, null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.add_milestone, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is AddEditMilestoneView) throw AssertionError("View must be AddEditMilestoneView")

        AnalyticsUtil.logEvent(Event.AddEditMilestoneControllerShown)

        if (initialDay != -1L) {
            view.display(AddEditMilestoneUiState.Add(initialDay, "", "",
                                                     isAdd = milestoneId == null))
        } else {
            Realm.getDefaultInstance().use { realm ->
                val milestone: Milestone? = realm.where(Milestone::class.java)
                        .equalTo(Milestone.FIELD_ID, milestoneId).findFirst()

                if (milestone == null) {
                    AlertDialog.Builder(view.context)
                            .setTitle(R.string.unable_to_find_milestone)
                            .setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int ->
                                dialog.dismiss()
                                router.handleBack()
                            }
                            .setCancelable(false)
                            .show()
                    return@use
                }

                view.display(AddEditMilestoneUiState.Add(milestone.epochDay, milestone.title,
                                                         milestone.description,
                                                         isAdd = milestoneId == null))
            }
        }

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents.ofType<AddEditMilestoneUiEvent.Back>()
                .subscribe { router.handleBack() }

        viewDisposables += sharedEvents.ofType<AddEditMilestoneUiEvent.Delete>()
                .subscribe { _ ->
                    confirmDeleteDialog?.dismissIfShowing()

                    if (milestoneId != null) {
                        confirmDeleteDialog = AlertDialog.Builder(view.context)
                                .setTitle(R.string.are_you_sure)
                                .setMessage(R.string.confirm_delete_milestone)
                                .setPositiveButton(R.string.delete) { dialog: DialogInterface, _: Int ->
                                    var success = false
                                    Realm.getDefaultInstance().use { realm ->
                                        realm.executeTransaction {
                                            val milestoneToDelete: Milestone? = realm.where(Milestone::class.java)
                                                    .equalTo(Milestone.FIELD_ID, milestoneId)
                                                    .findFirst()

                                            if (milestoneToDelete == null) {
                                                success = false
                                                return@executeTransaction
                                            }

                                            milestoneToDelete.deleteFromRealm()
                                            success = true
                                        }
                                    }

                                    dialog.dismiss()
                                    if (success) {
                                        router.handleBack()
                                    } else {
                                        Snackbar.make(view, R.string.error_deleting_milestone,
                                                      Snackbar.LENGTH_LONG)
                                                .show()
                                    }
                                }
                                .setNegativeButton(R.string.cancel, null)
                                .setOnDismissListener { confirmDeleteDialog = null }
                                .create()

                        confirmDeleteDialog!!.show()
                    }
                }

        viewDisposables += sharedEvents.ofType<AddEditMilestoneUiEvent.ChangeDate>()
                .subscribe { event ->
                    val initialDate = LocalDate.ofEpochDay(event.day)

                    //Note: The DatePickerDialog uses 0 based months
                    val dialog = DatePickerDialog(view.context, { _, year, month, dayOfMonth ->
                        val date = LocalDate.of(year, month + 1,
                                                dayOfMonth)

                        view.display(AddEditMilestoneUiState.Add(date.toEpochDay(), event.title,
                                                                 event.description,
                                                                 isAdd = milestoneId == null))
                    }, initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth)
                    dialog.datePicker.maxDate = System.currentTimeMillis()
                    dialog.show()
                }

        viewDisposables += sharedEvents.ofType<AddEditMilestoneUiEvent.Save>()
                .subscribe { event ->
                    Realm.getDefaultInstance().use { realm ->
                        if (milestoneId == null) {
                            realm.executeTransaction { innerRealm ->
                                val milestone = Milestone()
                                milestone.timestamp = System.currentTimeMillis()
                                milestone.epochDay = event.day
                                milestone.title = event.title
                                milestone.description = event.description
                                innerRealm.copyToRealmOrUpdate(milestone)
                            }
                        } else {
                            val milestone: Milestone? = realm.where(Milestone::class.java)
                                    .equalTo(Milestone.FIELD_ID, milestoneId).findFirst()

                            if (milestone == null) {
                                AlertDialog.Builder(view.context)
                                        .setTitle(R.string.unable_to_find_milestone)
                                        .setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int ->
                                            dialog.dismiss()
                                            router.handleBack()
                                        }
                                        .setCancelable(false)
                                        .show()
                                return@use
                            }

                            realm.executeTransaction { innerRealm ->
                                milestone.timestamp = System.currentTimeMillis()
                                milestone.epochDay = event.day
                                milestone.title = event.title
                                milestone.description = event.description
                                innerRealm.copyToRealmOrUpdate(milestone)
                            }

                        }

                        @StringRes
                        val messageRes: Int = when (milestoneId) {
                            null -> R.string.milestone_saved
                            else -> R.string.milestone_updated
                        }

                        Snackbar.make(view, messageRes, Snackbar.LENGTH_LONG).show()
                        router.handleBack()
                    }
                }
    }

    override fun onDetach(view: View) {
        viewDisposables.clear()
    }

    companion object {
        private const val KEY_INITIAL_DAY = "initialDay"
        private const val KEY_MILESTONE_ID = "milestoneId"
    }
}
