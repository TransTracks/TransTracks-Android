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
import com.drspaceboo.transtracks.util.FileUtil
import com.drspaceboo.transtracks.util.RxSchedulers
import com.drspaceboo.transtracks.util.openDefault
import com.jakewharton.rxrelay3.PublishRelay
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableTransformer
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import java.io.File
import java.time.LocalDate

sealed class EditPhotoAction {
    data class InitialLoad(val photoId: String) : EditPhotoAction()
    object ShowDateDialog : EditPhotoAction()
    data class ChangeDate(val newDate: LocalDate) : EditPhotoAction()
    object ShowTypeDialog : EditPhotoAction()
    data class ChangeType(@Photo.Type val newType: Int) : EditPhotoAction()
    object Update : EditPhotoAction()
}

sealed class EditPhotoResult {
    object Loading : EditPhotoResult()
    data class Display(val path: String, val date: LocalDate, @Photo.Type val type: Int) :
        EditPhotoResult()

    object ErrorLoadingPhoto : EditPhotoResult()
    data class ShowDateDialog(val path: String, val date: LocalDate, @Photo.Type val type: Int) :
        EditPhotoResult()

    data class ShowTypeDialog(val path: String, val date: LocalDate, @Photo.Type val type: Int) :
        EditPhotoResult()

    object UpdatingImage : EditPhotoResult()
    object UpdateSuccess : EditPhotoResult()
    data class ErrorUpdatingImage(
        val path: String, val date: LocalDate, @Photo.Type val type: Int
    ) : EditPhotoResult()
}

class EditPhotoDomain {
    private var id: String = ""
    private var path: String = ""
    private var date: LocalDate = LocalDate.now()
    private var type: Int = Photo.TYPE_FACE

    val actions: PublishRelay<EditPhotoAction> = PublishRelay.create()
    val results: Observable<EditPhotoResult> = actions
        .compose(editPhotoActionsToResults())
        .startWithItem(EditPhotoResult.Loading)
        .subscribeOn(RxSchedulers.io())
        .observeOn(RxSchedulers.main())
        .replay(1)
        .refCount()

    private fun editPhotoActionsToResults() =
        ObservableTransformer<EditPhotoAction, EditPhotoResult> { actions ->
            actions.switchMap<EditPhotoResult> { action ->
                return@switchMap when (action) {
                    is EditPhotoAction.InitialLoad -> {
                        id = action.photoId

                        Observable.just(Unit)
                            .observeOn(RxSchedulers.io())
                            .map<Boolean> {
                                val realm = Realm.openDefault()

                                val photo = realm
                                    .query(Photo::class, "${Photo.FIELD_ID} == '${action.photoId}'")
                                    .first()
                                    .find()

                                if (photo == null) {
                                    realm.close()
                                    return@map false
                                }

                                path = photo.filePath
                                date = LocalDate.ofEpochDay(photo.epochDay)
                                type = photo.type

                                realm.close()
                                return@map true
                            }.observeOn(RxSchedulers.main())
                            .map<EditPhotoResult> { success ->
                                return@map when (success) {
                                    true -> EditPhotoResult.Display(path, date, type)
                                    false -> EditPhotoResult.ErrorLoadingPhoto
                                }
                            }
                            .startWithItem(EditPhotoResult.Loading)
                    }

                    EditPhotoAction.ShowDateDialog ->
                        Observable.just(EditPhotoResult.ShowDateDialog(path, date, type))

                    is EditPhotoAction.ChangeDate -> {
                        date = action.newDate
                        Observable.just(EditPhotoResult.Display(path, date, type))
                    }

                    EditPhotoAction.ShowTypeDialog ->
                        Observable.just(EditPhotoResult.ShowTypeDialog(path, date, type))

                    is EditPhotoAction.ChangeType -> {
                        type = action.newType
                        Observable.just(EditPhotoResult.Display(path, date, type))
                    }

                    EditPhotoAction.Update -> Observable.just(Unit)
                        .observeOn(RxSchedulers.io())
                        .map<Boolean> {
                            val realm = Realm.openDefault()

                            val photo = realm.query(Photo::class, "${Photo.FIELD_ID} == '$id'")
                                .first()
                                .find()

                            if (photo == null) {
                                realm.close()
                                return@map false
                            }

                            val newPath: String? = when {
                                photo.epochDay != date.toEpochDay() -> {
                                    // We need to rename our image file since we keep the day in the
                                    // name so they are ordered correctly in any export
                                    val file = File(path)
                                    if (!file.exists()) {
                                        realm.close()
                                        return@map false
                                    }

                                    val newFile = FileUtil.getNewImageFile(date)

                                    if (!file.renameTo(newFile)) {
                                        realm.close()
                                        return@map false
                                    }

                                    newFile.absolutePath
                                }

                                else -> null
                            }

                            realm.writeBlocking {
                                findLatest(photo)?.let {
                                    if (newPath != null) {
                                        it.filePath = newPath
                                    }

                                    it.epochDay = date.toEpochDay()
                                    it.type = type

                                    copyToRealm(it, UpdatePolicy.ALL)
                                }
                            }
                            realm.close()

                            return@map true
                        }
                        .observeOn(RxSchedulers.main())
                        .map<EditPhotoResult> { success ->
                            return@map when (success) {
                                true -> EditPhotoResult.UpdateSuccess
                                false -> EditPhotoResult.ErrorUpdatingImage(path, date, type)
                            }
                        }
                        .startWithItem(EditPhotoResult.UpdatingImage)
                }
            }
        }
}
