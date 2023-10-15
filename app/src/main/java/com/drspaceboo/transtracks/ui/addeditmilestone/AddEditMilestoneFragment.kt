/*
 * Copyright © 2018-2023 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.addeditmilestone

import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Handler
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.TransTracksApp
import com.drspaceboo.transtracks.data.Milestone
import com.drspaceboo.transtracks.domain.AddEditMilestoneAction
import com.drspaceboo.transtracks.domain.AddEditMilestoneAction.InitialAdd
import com.drspaceboo.transtracks.domain.AddEditMilestoneAction.InitialEdit
import com.drspaceboo.transtracks.domain.AddEditMilestoneDomain
import com.drspaceboo.transtracks.domain.AddEditMilestoneResult
import com.drspaceboo.transtracks.ui.addeditmilestone.AddEditMilestoneUiEvent.DescriptionUpdated
import com.drspaceboo.transtracks.ui.addeditmilestone.AddEditMilestoneUiEvent.TitleUpdated
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.dismissIfShowing
import com.drspaceboo.transtracks.util.isNotDisposed
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.openDefault
import com.drspaceboo.transtracks.util.plusAssign
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.core.ObservableTransformer
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import java.time.LocalDate

class AddEditMilestoneFragment : Fragment(R.layout.add_milestone) {
    val args: AddEditMilestoneFragmentArgs by navArgs()

    private var resultsDisposable: Disposable = Disposable.disposed()
    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    private var confirmDeleteDialog: AlertDialog? = null

    override fun onStart() {
        super.onStart()
        val view = view as? AddEditMilestoneView
            ?: throw AssertionError("View must be AddEditMilestoneView")

        AnalyticsUtil.logEvent(Event.AddEditMilestoneControllerShown)

        val domain: AddEditMilestoneDomain =
            TransTracksApp.instance.domainManager.addEditMilestoneDomain

        if (resultsDisposable.isDisposed) {
            resultsDisposable = domain.results
                .doOnSubscribe {
                    Handler().postDelayed(
                        {
                            val initialDay = args.initialDay
                            val milestoneId = args.milestoneId
                            domain.actions.accept(
                                when {
                                    initialDay != -1L -> InitialAdd(initialDay)
                                    milestoneId != null -> InitialEdit(milestoneId)
                                    else -> throw IllegalArgumentException("One of the required arguments was not set")
                                }
                            )
                        }, 200
                    )
                }
                .subscribe()
        }

        viewDisposables += domain.results
            .compose(addEditMilestoneResultToViewState(view.context, findNavController()))
            .subscribe { state -> view.display(state) }

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents.filter { it is TitleUpdated || it is DescriptionUpdated }
            .map { event ->
                when (event) {
                    is TitleUpdated -> AddEditMilestoneAction.TitleUpdate(event.newTitle)
                    is DescriptionUpdated -> AddEditMilestoneAction.DescriptionUpdate(event.newDescription)
                    else -> throw IllegalArgumentException("Unhandled event ${event.javaClass.simpleName}")
                }
            }
            .subscribe(domain.actions)

        viewDisposables += sharedEvents.ofType<AddEditMilestoneUiEvent.Back>()
            .subscribe { requireActivity().onBackPressed() }

        viewDisposables += sharedEvents.ofType<AddEditMilestoneUiEvent.Delete>()
            .subscribe { _ ->
                confirmDeleteDialog?.dismissIfShowing()

                if (args.milestoneId != null) {
                    confirmDeleteDialog = AlertDialog.Builder(view.context)
                        .setTitle(R.string.are_you_sure)
                        .setMessage(R.string.confirm_delete_milestone)
                        .setPositiveButton(R.string.delete) { dialog: DialogInterface, _: Int ->
                            var success = false
                            val realm = Realm.openDefault()
                            realm.writeBlocking {
                                val milestoneToDelete: Milestone = query(
                                    Milestone::class,
                                    "${Milestone.FIELD_ID} == '${args.milestoneId}'"
                                )
                                    .first()
                                    .find() ?: return@writeBlocking

                                delete(milestoneToDelete)
                                success = true
                            }

                            dialog.dismiss()
                            if (success) {
                                findNavController().popBackStack()
                            } else {
                                Snackbar.make(
                                    view,
                                    R.string.error_deleting_milestone,
                                    Snackbar.LENGTH_LONG
                                ).show()
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
                val dialog = DatePickerDialog(
                    view.context,
                    { _, year, month, dayOfMonth ->
                        val date = LocalDate.of(
                            year, month + 1,
                            dayOfMonth
                        )
                        domain.actions.accept(AddEditMilestoneAction.DateUpdated(date.toEpochDay()))
                    },
                    initialDate.year,
                    initialDate.monthValue - 1,
                    initialDate.dayOfMonth
                )
                dialog.datePicker.maxDate = System.currentTimeMillis()
                dialog.show()
            }

        viewDisposables += sharedEvents.ofType<AddEditMilestoneUiEvent.Save>()
            .subscribe { event ->
                val realm = Realm.openDefault()

                if (args.milestoneId == null) {
                    realm.writeBlocking {
                        val milestone = Milestone()
                        milestone.timestamp = System.currentTimeMillis()
                        milestone.epochDay = event.day
                        milestone.title = event.title
                        milestone.description = event.description
                        copyToRealm(milestone, UpdatePolicy.ALL)
                    }
                } else {
                    val milestone: Milestone? =
                        realm.query(
                            Milestone::class,
                            "${Milestone.FIELD_ID} == '${args.milestoneId}'"
                        )
                            .first()
                            .find()

                    if (milestone == null) {
                        AlertDialog.Builder(view.context)
                            .setTitle(R.string.unable_to_find_milestone)
                            .setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int ->
                                dialog.dismiss()
                                findNavController().popBackStack()
                            }
                            .setCancelable(false)
                            .show()
                        realm.close()
                        return@subscribe
                    }

                    realm.writeBlocking {
                        findLatest(milestone)?.let {
                            it.timestamp = System.currentTimeMillis()
                            it.epochDay = event.day
                            it.title = event.title
                            it.description = event.description
                            copyToRealm(it, UpdatePolicy.ALL)
                        }
                    }
                    realm.close()
                }

                @StringRes
                val messageRes: Int = when (args.milestoneId) {
                    null -> R.string.milestone_saved
                    else -> R.string.milestone_updated
                }

                Snackbar.make(view, messageRes, Snackbar.LENGTH_LONG).show()
                findNavController().popBackStack()
            }
    }

    override fun onDetach() {
        viewDisposables.clear()
        super.onDetach()
    }

    override fun onDestroyView() {
        if (resultsDisposable.isNotDisposed()) {
            resultsDisposable.dispose()
        }
        super.onDestroyView()
    }

    companion object {
        fun addEditMilestoneResultToViewState(
            context: Context,
            navController: NavController
        ): ObservableTransformer<AddEditMilestoneResult, AddEditMilestoneUiState> {
            return ObservableTransformer { results ->
                results.map { result ->
                    return@map when (result) {
                        AddEditMilestoneResult.Loading -> AddEditMilestoneUiState.Loading

                        is AddEditMilestoneResult.Display -> AddEditMilestoneUiState.Display(
                            result.epochDay,
                            result.title,
                            result.description,
                            isAdd = result.milestoneId == null
                        )

                        AddEditMilestoneResult.UnableToFindMilestone -> {
                            AlertDialog.Builder(context)
                                .setTitle(R.string.unable_to_find_milestone)
                                .setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int ->
                                    dialog.dismiss()
                                    navController.popBackStack()
                                }
                                .setCancelable(false)
                                .show()

                            AddEditMilestoneUiState.Loading
                        }
                    }
                }
            }
        }
    }
}
