/*
 * Copyright © 2019-2022 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.domain

import com.drspaceboo.transtracks.BuildConfig
import com.drspaceboo.transtracks.data.Milestone
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.domain.SettingsAction.Export
import com.drspaceboo.transtracks.domain.SettingsAction.SettingsUpdated
import com.drspaceboo.transtracks.domain.SettingsResult.Content
import com.drspaceboo.transtracks.domain.SettingsResult.Loading
import com.drspaceboo.transtracks.domain.SettingsViewEffect.ShowBackupResult
import com.drspaceboo.transtracks.ui.settings.SettingsUIUserDetails
import com.drspaceboo.transtracks.util.FileUtil
import com.drspaceboo.transtracks.util.RxSchedulers
import com.drspaceboo.transtracks.util.hasPasswordProvider
import com.drspaceboo.transtracks.util.openDefault
import com.drspaceboo.transtracks.util.settings.LockDelay
import com.drspaceboo.transtracks.util.settings.LockType
import com.drspaceboo.transtracks.util.settings.SettingsManager
import com.drspaceboo.transtracks.util.settings.Theme
import com.drspaceboo.transtracks.util.writeFile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import com.google.gson.stream.JsonWriter
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.realm.kotlin.Realm
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.zip.ZipOutputStream

sealed class SettingsAction {
    object SettingsUpdated : SettingsAction()
    object Export : SettingsAction()
}

sealed class SettingsResult {
    data class Content(
        val userDetails: SettingsUIUserDetails?, val startDate: LocalDate, val theme: Theme,
        val lockType: LockType, val lockDelay: LockDelay, val enableAnalytics: Boolean,
        val enableCrashReports: Boolean, val showAds: Boolean
    ) : SettingsResult()

    data class Loading(val content: Content, val overallProgress: Int, val stepProgress: Int) :
        SettingsResult()
}

sealed class SettingsViewEffect {
    data class ShowBackupResult(val zipFile: File?) : SettingsViewEffect()
}

class SettingsDomain {
    private val settingsUpdatedActions: Observable<SettingsUpdated> =
        SettingsManager.userSettingsUpdated.map { SettingsUpdated }

    private val viewEffectRelay: PublishRelay<SettingsViewEffect> = PublishRelay.create()
    val viewEffects: Observable<SettingsViewEffect> = viewEffectRelay

    val actions: PublishRelay<SettingsAction> = PublishRelay.create()
    private val mergedActions: Observable<SettingsAction> =
        Observable.merge(actions, settingsUpdatedActions)

    val results: Observable<SettingsResult> = mergedActions
        .startWith(SettingsUpdated)
        .compose(settingsActionsToResults(viewEffectRelay))
        .subscribeOn(RxSchedulers.io())
        .observeOn(RxSchedulers.main())
        .replay(1)
        .refCount()

    companion object {
        private fun settingsActionsToResults(viewEffectRelay: PublishRelay<SettingsViewEffect>): ObservableTransformer<SettingsAction, SettingsResult> {
            return ObservableTransformer { actions ->
                fun getContent(): Content {
                    val userDetails = FirebaseAuth.getInstance().currentUser?.let {
                        SettingsUIUserDetails(it.displayName, it.email, it.hasPasswordProvider())
                    }

                    return Content(
                        userDetails, SettingsManager.getStartDate(context = null),
                        SettingsManager.getTheme(), SettingsManager.getLockType(),
                        SettingsManager.getLockDelay(), SettingsManager.getEnableAnalytics(),
                        SettingsManager.getEnableCrashReports(), SettingsManager.showAds()
                    )
                }

                actions.switchMap { action ->
                    return@switchMap when (action) {
                        SettingsUpdated -> Observable.just(getContent())

                        Export -> {
                            val content = getContent()
                            val progressRelay = PublishRelay.create<SettingsResult>()
                            var overallProgress = 0
                            var stepProgress = 0
                            fun updateProgress() = progressRelay.accept(
                                Loading(content, overallProgress, stepProgress)
                            )

                            fun incrementOverall() {
                                stepProgress = 0
                                overallProgress += 50
                                updateProgress()
                            }

                            val export = Observable
                                .fromCallable<SettingsResult> {
                                    val zipFile: File? = try {
                                        //region Data file creation
                                        val dataFile = FileUtil.getTempFile("data.json")

                                        FileWriter(dataFile).use { fileWriter ->
                                            BufferedWriter(fileWriter).use { bufferedWriter ->
                                                JsonWriter(bufferedWriter).use { jsonWriter ->
                                                    val gson = Gson()
                                                    jsonWriter.beginObject()
                                                    val realm = Realm.openDefault()

                                                    val stepMax = realm.query(Photo::class)
                                                        .count()
                                                        .find() + realm.query(Milestone::class)
                                                        .count()
                                                        .find() + 1
                                                    var stepCount = 0.0
                                                    fun incrementStep() {
                                                        stepCount++
                                                        stepProgress =
                                                            (stepCount / stepMax * 100.0).toInt()
                                                        updateProgress()
                                                    }
                                                    jsonWriter.name("settings")
                                                    Gson().toJson(
                                                        SettingsManager.getSettingsAsJson(),
                                                        jsonWriter
                                                    )
                                                    incrementStep()

                                                    jsonWriter.name("photos")
                                                    jsonWriter.beginArray()
                                                    realm.query(Photo::class).find()
                                                        .forEach { photo ->
                                                            photo.toJson()?.let {
                                                                gson.toJson(it, jsonWriter)
                                                            }
                                                            incrementStep()
                                                        }
                                                    jsonWriter.endArray()

                                                    jsonWriter.name("milestones")
                                                    jsonWriter.beginArray()
                                                    realm.query(Milestone::class).find()
                                                        .forEach { milestone ->
                                                            gson.toJson(
                                                                milestone.toJson(), jsonWriter
                                                            )
                                                            incrementStep()
                                                        }
                                                    jsonWriter.endArray()

                                                    jsonWriter.endObject()
                                                }
                                            }
                                        }
                                        incrementOverall()
                                        //endregion

                                        //region Zip file creation
                                        val timeStamp =
                                            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                                                .format(Date())
                                        val zipFile = FileUtil.getTempFile("$timeStamp.ttbackup")

                                        FileOutputStream(zipFile).use { fileOutputStream ->
                                            BufferedOutputStream(fileOutputStream).use { bufferedOutputStream ->
                                                ZipOutputStream(bufferedOutputStream).use { zipOutputStream ->
                                                    val photosDir = FileUtil.getPhotosDirectory()
                                                    val stepMax =
                                                        (photosDir.takeIf { it.exists() && it.isDirectory }
                                                            ?.listFiles()?.size ?: 0) + 1
                                                    var stepCount = 0.0
                                                    fun incrementStep() {
                                                        stepCount++
                                                        stepProgress =
                                                            (stepCount / stepMax * 100.0).toInt()
                                                        updateProgress()
                                                    }

                                                    zipOutputStream.writeFile(dataFile)
                                                    incrementStep()

                                                    photosDir.takeIf { it.exists() && it.isDirectory }
                                                        ?.listFiles()
                                                        ?.forEach {
                                                            try {
                                                                zipOutputStream.writeFile(
                                                                    it, "photos/"
                                                                )
                                                            } catch (e: IOException) {
                                                                if (!BuildConfig.DEBUG) {
                                                                    FirebaseCrashlytics.getInstance()
                                                                        .recordException(e)
                                                                }
                                                                e.printStackTrace()
                                                            } finally {
                                                                incrementStep()
                                                            }
                                                        }
                                                }
                                            }
                                        }
                                        incrementOverall()
                                        //endregion

                                        zipFile
                                    } catch (e: Exception) {
                                        if (!BuildConfig.DEBUG) {
                                            FirebaseCrashlytics.getInstance().recordException(e)
                                        }
                                        e.printStackTrace()
                                        null
                                    }

                                    viewEffectRelay.accept(ShowBackupResult(zipFile))
                                    return@fromCallable content
                                }
                                .subscribeOn(RxSchedulers.io())

                            Observable.merge(progressRelay, export)
                                .startWith(Loading(content, overallProgress, stepProgress))
                        }
                    }
                }
            }
        }
    }
}
