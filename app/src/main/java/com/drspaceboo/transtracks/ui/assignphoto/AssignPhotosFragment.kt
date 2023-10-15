/*
 * Copyright © 2018-2023 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.assignphoto

import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Handler
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.TransTracksApp
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.domain.AssignPhotosAction
import com.drspaceboo.transtracks.domain.AssignPhotosDomain
import com.drspaceboo.transtracks.domain.AssignPhotosResult
import com.drspaceboo.transtracks.ui.assignphoto.AssignPhotosFragmentArgs
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.ProgressDialog
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.isNotDisposed
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.toFullDateString
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.core.ObservableTransformer
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import java.time.LocalDate

class AssignPhotosFragment : Fragment(R.layout.assign_photo) {
    val args: AssignPhotosFragmentArgs by navArgs()

    private var resultsDisposable: Disposable = Disposable.disposed()
    private val viewDisposables = CompositeDisposable()

    private var savingDialog: AlertDialog? = null

    override fun onStart() {
        super.onStart()
        val view = view as? AssignPhotoView ?: throw AssertionError("View must be AssignPhotoView")

        AnalyticsUtil.logEvent(Event.AssignPhotoControllerShown)

        val domain: AssignPhotosDomain = TransTracksApp.instance.domainManager.assignPhotosDomain

        if (resultsDisposable.isDisposed) {
            resultsDisposable = domain.results
                .doOnSubscribe {
                    Handler().postDelayed(
                        {
                            val day: Long? = args.epochDay?.value
                            domain.actions.accept(
                                AssignPhotosAction.InitialData(args.uris, day, args.type)
                            )
                        }, 200
                    )
                }.subscribe()
        }

        viewDisposables += domain.results
            .filter { result ->
                result !is AssignPhotosResult.SavingImage
                        && result !is AssignPhotosResult.SaveSuccess
            }
            .compose(assignPhotoResultsToUiState(view.context))
            .subscribe { state -> view.display(state) }

        viewDisposables += domain.results
            .ofType<AssignPhotosResult.ShowDateDialog>()
            .subscribe { result ->
                //Note: The DatePickerDialog uses 0 based months
                val dialog = DatePickerDialog(
                    view.context, { _, year, month, dayOfMonth ->
                        domain.actions.accept(
                            AssignPhotosAction.ChangeDate(
                                result.index, LocalDate.of(year, month + 1, dayOfMonth)
                            )
                        )
                    },
                    result.date.year,
                    result.date.monthValue - 1,
                    result.date.dayOfMonth
                )
                dialog.datePicker.maxDate = System.currentTimeMillis()

                dialog.show()
            }

        viewDisposables += domain.results
            .ofType<AssignPhotosResult.ShowTypeDialog>()
            .subscribe { result ->
                AlertDialog.Builder(view.context)
                    .setTitle(R.string.select_type)
                    .setSingleChoiceItems(
                        arrayOf(view.getString(R.string.face), view.getString(R.string.body)),
                        result.type
                    ) { dialog: DialogInterface, type: Int ->
                        if (result.type != type) {
                            domain.actions.accept(AssignPhotosAction.ChangeType(result.index, type))
                        }
                        dialog.dismiss()
                    }
                    .show()
            }

        viewDisposables += domain.results
            .ofType<AssignPhotosResult.SavingImage>()
            .subscribe {
                savingDialog?.dismiss()

                savingDialog = ProgressDialog.make(R.string.saving_photo, view.context)
                savingDialog!!.show()
            }

        viewDisposables += domain.results
            .ofType<AssignPhotosResult.SaveSuccess>()
            .subscribe { result ->
                savingDialog?.dismiss()
                savingDialog = null

                Snackbar.make(view, R.string.saved_photo, Snackbar.LENGTH_SHORT).show()

                if (result.index + 1 < result.count) {
                    domain.actions.accept(AssignPhotosAction.LoadImage(result.index + 1))
                } else {
                    findNavController().popBackStack(
                        destinationId = args.destinationToPopTo, inclusive = false
                    )
                }
            }

        viewDisposables += domain.results
            .ofType<AssignPhotosResult.ErrorSavingImage>()
            .subscribe {
                savingDialog?.dismiss()
                savingDialog = null

                Snackbar.make(view, R.string.error_saving_photo, Snackbar.LENGTH_LONG).show()
            }

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents
            .ofType<AssignPhotoUiEvent.Back>()
            .subscribe { requireActivity().onBackPressed() }

        viewDisposables += sharedEvents.ofType<AssignPhotoUiEvent.Skip>()
            .subscribe { event ->
                Snackbar.make(view, R.string.skipped_photo, Snackbar.LENGTH_SHORT).show()

                if (event.index + 1 < event.count) {
                    domain.actions.accept(AssignPhotosAction.LoadImage(event.index + 1))
                } else {
                    findNavController().popBackStack(
                        destinationId = args.destinationToPopTo, inclusive = false
                    )
                }
            }

        viewDisposables += sharedEvents
            .filter { event ->
                event !== AssignPhotoUiEvent.Back && event !is AssignPhotoUiEvent.Skip
            }
            .map<AssignPhotosAction> { event ->
                return@map when (event) {
                    is AssignPhotoUiEvent.ChangeDate ->
                        AssignPhotosAction.ShowDateDialog(event.index)

                    is AssignPhotoUiEvent.ChangeType ->
                        AssignPhotosAction.ShowTypeDialog(event.index)

                    is AssignPhotoUiEvent.UsePhotoDate ->
                        AssignPhotosAction.ChangeDate(event.index, event.photoDate)

                    is AssignPhotoUiEvent.Save -> AssignPhotosAction.Save(event.index)

                    else -> throw IllegalArgumentException("Unhandled event '${event.javaClass.simpleName}'")
                }
            }
            .subscribe(domain.actions)
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
}

fun assignPhotoResultsToUiState(context: Context) =
    ObservableTransformer<AssignPhotosResult, AssignPhotoUiState> { results ->
        results.map { result ->
            fun getTitle(result: AssignPhotosResult): String {
                val index = AssignPhotosResult.getIndex(result)
                val count = AssignPhotosResult.getCount(result)

                return when (count) {
                    1 -> context.getString(R.string.assign_photo)
                    else -> context.getString(R.string.assign_photo_count, index + 1, count)
                }
            }

            fun shouldShowSkip(result: AssignPhotosResult): Boolean {
                val count = AssignPhotosResult.getCount(result)
                return count != 1
            }

            return@map when (result) {
                is AssignPhotosResult.Loading -> AssignPhotoUiState.Loading

                is AssignPhotosResult.Display -> {
                    val photoDateToUse: LocalDate? = when {
                        result.date == result.photoDate -> null
                        else -> result.photoDate
                    }

                    AssignPhotoUiState.Loaded(
                        result.index, result.count, result.uri,
                        getTitle(result), result.date.toFullDateString(context),
                        photoDateToUse,
                        Photo.getTypeName(result.type, context),
                        shouldShowSkip(result)
                    )
                }

                is AssignPhotosResult.ShowDateDialog -> {
                    val photoDateToUse: LocalDate? = when {
                        result.date == result.photoDate -> null
                        else -> result.photoDate
                    }

                    AssignPhotoUiState.Loaded(
                        result.index, result.count, result.uri,
                        getTitle(result), result.date.toFullDateString(context),
                        photoDateToUse,
                        Photo.getTypeName(result.type, context),
                        shouldShowSkip(result)
                    )
                }

                is AssignPhotosResult.ShowTypeDialog -> {
                    val photoDateToUse: LocalDate? = when {
                        result.date == result.photoDate -> null
                        else -> result.photoDate
                    }

                    AssignPhotoUiState.Loaded(
                        result.index, result.count, result.uri,
                        getTitle(result), result.date.toFullDateString(context),
                        photoDateToUse,
                        Photo.getTypeName(result.type, context),
                        shouldShowSkip(result)
                    )
                }

                is AssignPhotosResult.ErrorSavingImage -> {
                    val photoDateToUse: LocalDate? = when {
                        result.date == result.photoDate -> null
                        else -> result.photoDate
                    }

                    AssignPhotoUiState.Loaded(
                        result.index, result.count, result.uri,
                        getTitle(result), result.date.toFullDateString(context),
                        photoDateToUse,
                        Photo.getTypeName(result.type, context),
                        shouldShowSkip(result)
                    )
                }

                else -> throw IllegalArgumentException("Unhandled result '${result.javaClass.simpleName}'")
            }
        }
    }
