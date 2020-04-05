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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.drspaceboo.transtracks.TransTracksApp
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.util.FileUtil
import com.drspaceboo.transtracks.util.RxSchedulers
import com.drspaceboo.transtracks.util.compatGetDateTime
import com.drspaceboo.transtracks.util.copyFrom
import com.drspaceboo.transtracks.util.dateCreated
import com.drspaceboo.transtracks.util.localDateFromEpochMilli
import com.drspaceboo.transtracks.util.quietlyClose
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.realm.Realm
import org.threeten.bp.LocalDate
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream

sealed class AssignPhotosAction {
    data class InitialData(val uris: ArrayList<Uri>, val epochDay: Long?, @Photo.Type val type: Int) : AssignPhotosAction()
    data class LoadImage(val index: Int) : AssignPhotosAction()
    data class ShowDateDialog(val index: Int) : AssignPhotosAction()
    data class ChangeDate(val index: Int, val newDate: LocalDate) : AssignPhotosAction()
    data class ShowTypeDialog(val index: Int) : AssignPhotosAction()
    data class ChangeType(val index: Int, @Photo.Type val newType: Int) : AssignPhotosAction()
    data class Save(val index: Int) : AssignPhotosAction()
}

sealed class AssignPhotosResult {
    data class Loading(val index: Int, val count: Int) : AssignPhotosResult()
    data class Display(val uri: Uri, val date: LocalDate, val photoDate: LocalDate,
                       @Photo.Type val type: Int, val index: Int, val count: Int) : AssignPhotosResult()

    data class ShowDateDialog(val uri: Uri, val date: LocalDate, val photoDate: LocalDate,
                              @Photo.Type val type: Int, val index: Int, val count: Int) : AssignPhotosResult()

    data class ShowTypeDialog(val uri: Uri, val date: LocalDate, val photoDate: LocalDate,
                              @Photo.Type val type: Int, val index: Int, val count: Int) : AssignPhotosResult()

    data class SavingImage(val index: Int, val count: Int) : AssignPhotosResult()
    data class SaveSuccess(val index: Int, val count: Int) : AssignPhotosResult()

    data class ErrorSavingImage(val uri: Uri, val date: LocalDate, val photoDate: LocalDate,
                                @Photo.Type val type: Int, val index: Int, val count: Int) : AssignPhotosResult()

    companion object {
        fun getIndex(result: AssignPhotosResult) = when (result) {
            is AssignPhotosResult.Loading -> result.index
            is AssignPhotosResult.Display -> result.index
            is AssignPhotosResult.ShowDateDialog -> result.index
            is AssignPhotosResult.ShowTypeDialog -> result.index
            is AssignPhotosResult.SavingImage -> result.index
            is AssignPhotosResult.SaveSuccess -> result.index
            is AssignPhotosResult.ErrorSavingImage -> result.index
        }

        fun getCount(result: AssignPhotosResult) = when (result) {
            is AssignPhotosResult.Loading -> result.count
            is AssignPhotosResult.Display -> result.count
            is AssignPhotosResult.ShowDateDialog -> result.count
            is AssignPhotosResult.ShowTypeDialog -> result.count
            is AssignPhotosResult.SavingImage -> result.count
            is AssignPhotosResult.SaveSuccess -> result.count
            is AssignPhotosResult.ErrorSavingImage -> result.count
        }
    }
}

class AssignPhotosDomain {
    private var uris: ArrayList<Uri> = ArrayList()
    private var type: Int = Photo.TYPE_FACE
    private var epochDay: Long? = null

    private var date: LocalDate = LocalDate.now()
    private var photoDate: LocalDate = LocalDate.now()

    val actions: PublishRelay<AssignPhotosAction> = PublishRelay.create()
    val results: Observable<AssignPhotosResult> = actions
            .compose(assignPhotoActionsToResults())
            .startWith(AssignPhotosResult.Loading(0, 1))
            .subscribeOn(RxSchedulers.io())
            .observeOn(RxSchedulers.main())
            .replay(1)
            .refCount()


    private fun assignPhotoActionsToResults() = ObservableTransformer<AssignPhotosAction, AssignPhotosResult> { actions ->
        actions.switchMap { action ->
            return@switchMap when (action) {
                is AssignPhotosAction.InitialData -> {
                    uris = action.uris
                    type = action.type
                    epochDay = action.epochDay

                    this.actions.accept(AssignPhotosAction.LoadImage(0))
                    Observable.just(AssignPhotosResult.Loading(0, uris.size))
                }

                is AssignPhotosAction.LoadImage -> {
                    Observable.just(Unit)
                            .map<AssignPhotosResult> {
                                date = when {
                                    epochDay != null -> LocalDate.ofEpochDay(epochDay!!)
                                    else -> LocalDate.now()
                                }
                                val uri = uris[action.index]

                                val contentResolver = TransTracksApp.instance.contentResolver
                                var newDateTime = -1L

                                //Let's try to get the date from the exif
                                var exifInputStream: InputStream? = null
                                try {
                                    exifInputStream = contentResolver.openInputStream(uri)
                                    if (exifInputStream != null) {
                                        val exif = ExifInterface(exifInputStream)

                                        newDateTime = exif.compatGetDateTime()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    exifInputStream?.quietlyClose()
                                }

                                if (newDateTime != -1L) {
                                    photoDate = localDateFromEpochMilli(newDateTime)
                                    if (epochDay == null) {
                                        date = photoDate
                                    }

                                    return@map AssignPhotosResult.Display(uri, date, photoDate,
                                                                          type, action.index,
                                                                          uris.size)
                                }

                                photoDate = localDateFromEpochMilli(File(uri.path).dateCreated())
                                if (epochDay == null) {
                                    date = photoDate
                                }

                                return@map AssignPhotosResult.Display(uri, date, photoDate, type,
                                                                      action.index, uris.size)
                            }
                            .startWith(AssignPhotosResult.Loading(action.index, uris.size))
                }

                is AssignPhotosAction.ShowDateDialog -> Observable.just(
                        AssignPhotosResult.ShowDateDialog(uris[action.index], date, photoDate, type,
                                                          action.index, uris.size))

                is AssignPhotosAction.ChangeDate -> {
                    date = action.newDate
                    Observable.just(AssignPhotosResult.Display(uris[action.index], date, photoDate,
                                                               type, action.index, uris.size))
                }

                is AssignPhotosAction.ShowTypeDialog -> Observable.just(
                        AssignPhotosResult.ShowTypeDialog(uris[action.index], date, photoDate, type,
                                                          action.index, uris.size))

                is AssignPhotosAction.ChangeType -> {
                    type = action.newType
                    Observable.just(AssignPhotosResult.Display(uris[action.index], date, photoDate,
                                                               type, action.index, uris.size))
                }

                is AssignPhotosAction.Save -> Observable.just(Unit)
                        .observeOn(RxSchedulers.io())
                        .map<Pair<AssignPhotosAction.Save, Boolean>> {
                            val contentResolver = TransTracksApp.instance.contentResolver
                            val uri = uris[action.index]

                            var bitmapInputStream: InputStream? = null
                            val bitmap: Bitmap
                            try {
                                bitmapInputStream = contentResolver.openInputStream(uri)
                                bitmap = BitmapFactory.decodeStream(bitmapInputStream)
                            } catch (e: Exception) {
                                when (e) {
                                    is IllegalArgumentException,
                                    is IllegalStateException,
                                    is FileNotFoundException,
                                    is SecurityException -> e.printStackTrace()

                                    else -> throw e
                                }

                                return@map action to false
                            } finally {
                                bitmapInputStream?.quietlyClose()
                            }

                            val imageFile = FileUtil.getNewImageFile(date)
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100,
                                            FileOutputStream(imageFile))

                            var exifInputStream: InputStream? = null
                            try {
                                exifInputStream = contentResolver.openInputStream(uri)
                                val inExif = ExifInterface(exifInputStream!!)
                                ExifInterface(imageFile.absolutePath).copyFrom(inExif)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                exifInputStream?.quietlyClose()
                            }

                            Realm.getDefaultInstance().use { realm ->
                                realm.executeTransaction { innerRealm ->
                                    val photo = Photo()
                                    photo.epochDay = date.toEpochDay()
                                    photo.timestamp = System.currentTimeMillis()
                                    photo.filePath = imageFile.absolutePath
                                    photo.type = type

                                    innerRealm.insertOrUpdate(photo)
                                }
                            }

                            return@map action to true
                        }
                        .observeOn(RxSchedulers.main())
                        .map<AssignPhotosResult> { (action, success) ->
                            return@map when (success) {
                                true -> AssignPhotosResult.SaveSuccess(action.index, uris.size)

                                false -> AssignPhotosResult.ErrorSavingImage(uris[action.index],
                                                                             date, photoDate, type,
                                                                             action.index, uris.size)
                            }
                        }
                        .startWith(AssignPhotosResult.SavingImage(action.index, uris.size))
            }
        }
    }
}
