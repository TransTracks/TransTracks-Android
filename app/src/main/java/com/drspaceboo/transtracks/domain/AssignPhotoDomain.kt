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
import android.support.media.ExifInterface
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

sealed class AssignPhotoAction {
    data class InitialData(val uri: Uri, val epochDay: Long?, @Photo.Type val type: Int) : AssignPhotoAction()
    object ShowDateDialog : AssignPhotoAction()
    data class ChangeDate(val newDate: LocalDate) : AssignPhotoAction()
    object ShowTypeDialog : AssignPhotoAction()
    data class ChangeType(@Photo.Type val newType: Int) : AssignPhotoAction()
    object Save : AssignPhotoAction()
}

sealed class AssignPhotoResult {
    object Loading : AssignPhotoResult()
    data class Display(val uri: Uri, val date: LocalDate, @Photo.Type val type: Int) : AssignPhotoResult()
    data class ShowDateDialog(val uri: Uri, val date: LocalDate, @Photo.Type val type: Int) : AssignPhotoResult()
    data class ShowTypeDialog(val uri: Uri, val date: LocalDate, @Photo.Type val type: Int) : AssignPhotoResult()
    object SavingImage : AssignPhotoResult()
    object SaveSuccess : AssignPhotoResult()
    data class ErrorSavingImage(val uri: Uri, val date: LocalDate, @Photo.Type val type: Int) : AssignPhotoResult()
}

class AssignPhotoDomain {
    private var uri: Uri = Uri.parse("")
    private var date: LocalDate = LocalDate.now()
    private var type: Int = Photo.TYPE_FACE

    val actions: PublishRelay<AssignPhotoAction> = PublishRelay.create()
    val results: Observable<AssignPhotoResult> = actions
            .compose(assignPhotoActionsToResults())
            .startWith(AssignPhotoResult.Loading)
            .subscribeOn(RxSchedulers.io())
            .observeOn(RxSchedulers.main())
            .replay(1)
            .refCount()


    private fun assignPhotoActionsToResults() = ObservableTransformer<AssignPhotoAction, AssignPhotoResult> { actions ->
        actions.switchMap { action ->
            return@switchMap when (action) {
                is AssignPhotoAction.InitialData -> {
                    uri = action.uri
                    type = action.type

                    Observable.just(Unit)
                            .map<AssignPhotoResult> {
                                if (action.epochDay != null) {
                                    date = LocalDate.ofEpochDay(action.epochDay)
                                    return@map AssignPhotoResult.Display(uri, date, type)
                                }

                                val contentResolver = TransTracksApp.instance.contentResolver
                                var newDateTime = -1L

                                //Let's try to get the date from the exif
                                var exifInputStream: InputStream? = null
                                try {
                                    exifInputStream = contentResolver.openInputStream(uri)
                                    val exif = ExifInterface(exifInputStream)

                                    newDateTime = exif.compatGetDateTime()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    exifInputStream?.quietlyClose()
                                }

                                if (newDateTime != -1L) {
                                    date = localDateFromEpochMilli(newDateTime)
                                    return@map AssignPhotoResult.Display(uri, date, type)
                                }

                                date = localDateFromEpochMilli(File(uri.path).dateCreated())
                                return@map AssignPhotoResult.Display(uri, date, type)
                            }
                            .startWith(AssignPhotoResult.Loading)
                }

                AssignPhotoAction.ShowDateDialog -> Observable.just(AssignPhotoResult.ShowDateDialog(uri, date, type))

                is AssignPhotoAction.ChangeDate -> {
                    date = action.newDate
                    Observable.just(AssignPhotoResult.Display(uri, date, type))
                }

                AssignPhotoAction.ShowTypeDialog -> Observable.just(AssignPhotoResult.ShowTypeDialog(uri, date, type))

                is AssignPhotoAction.ChangeType -> {
                    type = action.newType
                    Observable.just(AssignPhotoResult.Display(uri, date, type))
                }

                AssignPhotoAction.Save -> Observable.just(Unit)
                        .observeOn(RxSchedulers.io())
                        .map<Boolean> {
                            val contentResolver = TransTracksApp.instance.contentResolver

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

                                return@map false
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

                            return@map true
                        }
                        .observeOn(RxSchedulers.main())
                        .map<AssignPhotoResult> { success ->
                            return@map when (success) {
                                true -> AssignPhotoResult.SaveSuccess
                                false -> AssignPhotoResult.ErrorSavingImage(uri, date, type)
                            }
                        }
                        .startWith(AssignPhotoResult.SavingImage)
            }
        }
    }
}
