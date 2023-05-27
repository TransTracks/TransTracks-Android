/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.editphoto

import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.bluelinelabs.conductor.Controller
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.TransTracksApp
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.domain.EditPhotoAction
import com.drspaceboo.transtracks.domain.EditPhotoDomain
import com.drspaceboo.transtracks.domain.EditPhotoResult
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.ProgressDialog
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.toFullDateString
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.core.ObservableTransformer
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable

import java.time.LocalDate

class EditPhotoController(args: Bundle) : Controller(args) {
    constructor(photoId: String) : this(Bundle().apply {
        putString(KEY_PHOTO_ID, photoId)
    })

    private var resultsDisposable: Disposable = Disposable.disposed()
    private val viewDisposables = CompositeDisposable()

    private var savingDialog: AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.edit_photo, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is EditPhotoView) throw AssertionError("View must be EditPhotoView")

        AnalyticsUtil.logEvent(Event.EditPhotoControllerShown)

        val domain: EditPhotoDomain = TransTracksApp.instance.domainManager.editPhotoDomain

        if (resultsDisposable.isDisposed) {
            resultsDisposable = domain.results
                    .doOnSubscribe {
                        Handler().postDelayed(
                                {
                                    domain.actions.accept(EditPhotoAction.InitialLoad(
                                            args.getString(KEY_PHOTO_ID)!!))
                                }, 200)
                    }.subscribe()
        }

        viewDisposables += domain.results
                .filter { result ->
                    result !== EditPhotoResult.ErrorLoadingPhoto
                            && result !== EditPhotoResult.UpdatingImage
                            && result !== EditPhotoResult.UpdateSuccess
                }
                .compose(editPhotoResultsToUiState(view.context))
                .subscribe { state -> view.display(state) }

        viewDisposables += domain.results
                .ofType<EditPhotoResult.ErrorLoadingPhoto>()
                .subscribe {
                    AlertDialog.Builder(view.context)
                            .setTitle(R.string.error_loading_photo)
                            .setMessage(R.string.error_loading_photo_message)
                            .setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int ->
                                dialog.dismiss()
                                router.handleBack()
                            }
                            .setCancelable(false)
                            .show()
                }

        viewDisposables += domain.results
                .ofType<EditPhotoResult.ShowDateDialog>()
                .subscribe { result ->
                    //Note: The DatePickerDialog uses 0 based months
                    val dialog = DatePickerDialog(view.context, { _, year, month, dayOfMonth ->
                        domain.actions.accept(
                                EditPhotoAction.ChangeDate(
                                        LocalDate.of(year, month + 1, dayOfMonth)))
                    }, result.date.year, result.date.monthValue - 1, result.date.dayOfMonth)

                    dialog.datePicker.maxDate = System.currentTimeMillis()
                    dialog.show()
                }

        viewDisposables += domain.results
                .ofType<EditPhotoResult.ShowTypeDialog>()
                .subscribe { result ->
                    AlertDialog.Builder(view.context)
                            .setTitle(R.string.select_type)
                            .setSingleChoiceItems(arrayOf(view.getString(R.string.face), view.getString(R.string.body)),
                                                  result.type) { dialog: DialogInterface, index: Int ->
                                if (result.type != index) {
                                    domain.actions.accept(EditPhotoAction.ChangeType(index))
                                }
                                dialog.dismiss()
                            }
                            .show()
                }

        viewDisposables += domain.results
            .ofType<EditPhotoResult.UpdatingImage>()
            .subscribe {
                savingDialog?.dismiss()

                savingDialog = ProgressDialog.make(R.string.updating_photo, view.context)
                savingDialog!!.show()
            }

        viewDisposables += domain.results
                .ofType<EditPhotoResult.UpdateSuccess>()
                .subscribe {
                    savingDialog?.dismiss()
                    savingDialog = null

                    router.handleBack()
                }

        viewDisposables += domain.results
                .ofType<EditPhotoResult.ErrorUpdatingImage>()
                .subscribe {
                    savingDialog?.dismiss()
                    savingDialog = null

                    Snackbar.make(view, R.string.error_updating_photo, Snackbar.LENGTH_LONG).show()
                }

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents
                .ofType<EditPhotoUiEvent.Back>()
                .subscribe { router.handleBack() }

        viewDisposables += sharedEvents
                .filter { event -> event !== EditPhotoUiEvent.Back }
                .map<EditPhotoAction> { event ->
                    return@map when (event) {
                        EditPhotoUiEvent.ChangeDate -> EditPhotoAction.ShowDateDialog
                        EditPhotoUiEvent.ChangeType -> EditPhotoAction.ShowTypeDialog
                        EditPhotoUiEvent.Update -> EditPhotoAction.Update
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
        private const val KEY_PHOTO_ID = "photoId"
    }
}

fun editPhotoResultsToUiState(context: Context) = ObservableTransformer<EditPhotoResult, EditPhotoUiState> { results ->
    results.map { result ->
        return@map when (result) {
            EditPhotoResult.Loading -> EditPhotoUiState.Loading

            is EditPhotoResult.Display ->
                EditPhotoUiState.Loaded(result.path, result.date.toFullDateString(context),
                                        Photo.getTypeName(result.type, context))

            is EditPhotoResult.ShowDateDialog ->
                EditPhotoUiState.Loaded(result.path, result.date.toFullDateString(context),
                                        Photo.getTypeName(result.type, context))

            is EditPhotoResult.ShowTypeDialog ->
                EditPhotoUiState.Loaded(result.path, result.date.toFullDateString(context),
                                        Photo.getTypeName(result.type, context))

            is EditPhotoResult.ErrorUpdatingImage ->
                EditPhotoUiState.Loaded(result.path, result.date.toFullDateString(context),
                                        Photo.getTypeName(result.type, context))

            else -> throw IllegalArgumentException("Unhandled result '${result.javaClass.simpleName}'")
        }
    }
}
