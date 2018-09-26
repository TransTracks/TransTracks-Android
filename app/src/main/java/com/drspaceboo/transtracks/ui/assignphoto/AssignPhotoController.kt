/*
 * Copyright © 2018 TransTracks. All rights reserved.
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
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import com.bluelinelabs.conductor.Controller
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.TransTracksApp
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.domain.AssignPhotoAction
import com.drspaceboo.transtracks.domain.AssignPhotoDomain
import com.drspaceboo.transtracks.domain.AssignPhotoResult
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.toFullDateString
import io.reactivex.ObservableTransformer
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import org.threeten.bp.LocalDate

class AssignPhotoController(args: Bundle) : Controller(args) {
    constructor(uri: Uri, epochDay: Long?, @Photo.Type type: Int,
                tagOfControllerToPopTo: String) : this(Bundle().apply {
        putParcelable(KEY_URI, uri)
        if (epochDay != null) {
            putLong(KEY_EPOCH_DAY, epochDay)
        }
        putInt(KEY_TYPE, type)
        putString(KEY_TAG_OF_CONTROLLER_TO_POP_TO, tagOfControllerToPopTo)
    })

    private var resultsDisposable: Disposable = Disposables.disposed()
    private val viewDisposables = CompositeDisposable()

    private var savingDialog: AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.assign_photo, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is AssignPhotoView) throw AssertionError("View must be AssignPhotoView")

        AnalyticsUtil.logEvent(Event.AssignPhotoControllerShown)

        val domain: AssignPhotoDomain = TransTracksApp.instance.domainManager.assignPhotoDomain

        if (resultsDisposable.isDisposed) {
            resultsDisposable = domain.results
                    .doOnSubscribe {
                        Handler().postDelayed(
                                {
                                    val day: Long? = when (args.containsKey(KEY_EPOCH_DAY)) {
                                        true -> args.getLong(KEY_EPOCH_DAY)
                                        else -> null
                                    }

                                    domain.actions.accept(AssignPhotoAction.InitialData(
                                            args.getParcelable(KEY_URI) as Uri, day,
                                            args.getInt(KEY_TYPE)))
                                }, 200)
                    }.subscribe()


        }

        viewDisposables += domain.results
                .filter { result ->
                    result !== AssignPhotoResult.SavingImage
                            && result !== AssignPhotoResult.SaveSuccess
                }
                .compose(assignPhotoResultsToUiState(view.context))
                .subscribe { state -> view.display(state) }

        viewDisposables += domain.results
                .ofType<AssignPhotoResult.ShowDateDialog>()
                .subscribe { result ->
                    //Note: The DatePickerDialog uses 0 based months
                    val dialog = DatePickerDialog(view.context,
                                                  { _, year, month, dayOfMonth ->
                                                      domain.actions.accept(
                                                              AssignPhotoAction.ChangeDate(
                                                                      LocalDate.of(year, month + 1, dayOfMonth)))
                                                  },
                                                  result.date.year, result.date.monthValue - 1, result.date.dayOfMonth)
                    dialog.datePicker.maxDate = System.currentTimeMillis()
                    dialog.show()
                }

        viewDisposables += domain.results
                .ofType<AssignPhotoResult.ShowTypeDialog>()
                .subscribe { result ->
                    AlertDialog.Builder(view.context)
                            .setTitle(R.string.select_type)
                            .setSingleChoiceItems(arrayOf(view.getString(R.string.face), view.getString(R.string.body)),
                                                  result.type) { dialog: DialogInterface, index: Int ->
                                if (result.type != index) {
                                    domain.actions.accept(AssignPhotoAction.ChangeType(index))
                                }
                                dialog.dismiss()
                            }
                            .show()
                }

        viewDisposables += domain.results
                .ofType<AssignPhotoResult.SavingImage>()
                .subscribe {
                    savingDialog?.dismiss()

                    savingDialog = AlertDialog.Builder(view.context)
                            .setTitle(R.string.saving_photo)
                            .setView(ProgressBar(view.context))
                            .create()
                    savingDialog!!.show()
                }

        viewDisposables += domain.results
                .ofType<AssignPhotoResult.SaveSuccess>()
                .subscribe {
                    savingDialog?.dismiss()
                    savingDialog = null

                    router.popToTag(args.getString(KEY_TAG_OF_CONTROLLER_TO_POP_TO)!!)
                }

        viewDisposables += domain.results
                .ofType<AssignPhotoResult.ErrorSavingImage>()
                .subscribe {
                    savingDialog?.dismiss()
                    savingDialog = null

                    Snackbar.make(view, R.string.error_saving_photo, Snackbar.LENGTH_LONG).show()
                }

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents
                .ofType<AssignPhotoUiEvent.Back>()
                .subscribe { router.handleBack() }

        viewDisposables += sharedEvents
                .filter { event -> event !== AssignPhotoUiEvent.Back }
                .map<AssignPhotoAction> { event ->
                    return@map when (event) {
                        AssignPhotoUiEvent.ChangeDate -> AssignPhotoAction.ShowDateDialog
                        AssignPhotoUiEvent.ChangeType -> AssignPhotoAction.ShowTypeDialog
                        AssignPhotoUiEvent.Save -> AssignPhotoAction.Save
                        else -> throw IllegalArgumentException("Unhandled event '${event.javaClass.simpleName}'")
                    }
                }
                .subscribe(domain.actions)
    }

    override fun onDestroyView(view: View) {
        viewDisposables.clear()
    }

    override fun onDetach(view: View) {
        resultsDisposable.dispose()
    }

    companion object {
        private const val KEY_URI = "uri"
        private const val KEY_EPOCH_DAY = "epochDay"
        private const val KEY_TYPE = "type"
        private const val KEY_TAG_OF_CONTROLLER_TO_POP_TO = "tagOfControllerToPopTo"
    }
}

fun assignPhotoResultsToUiState(context: Context) = ObservableTransformer<AssignPhotoResult, AssignPhotoUiState> { results ->
    results.map { result ->
        return@map when (result) {
            AssignPhotoResult.Loading -> AssignPhotoUiState.Loading

            is AssignPhotoResult.Display -> AssignPhotoUiState.Loaded(result.uri, result.date.toFullDateString(context),
                                                                      Photo.getTypeName(result.type, context))

            is AssignPhotoResult.ShowDateDialog -> AssignPhotoUiState.Loaded(result.uri,
                                                                             result.date.toFullDateString(context),
                                                                             Photo.getTypeName(result.type, context))

            is AssignPhotoResult.ShowTypeDialog -> AssignPhotoUiState.Loaded(result.uri,
                                                                             result.date.toFullDateString(context),
                                                                             Photo.getTypeName(result.type, context))

            is AssignPhotoResult.ErrorSavingImage -> AssignPhotoUiState.Loaded(result.uri,
                                                                               result.date.toFullDateString(context),
                                                                               Photo.getTypeName(result.type, context))

            else -> throw IllegalArgumentException("Unhandled result '${result.javaClass.simpleName}'")
        }
    }
}
