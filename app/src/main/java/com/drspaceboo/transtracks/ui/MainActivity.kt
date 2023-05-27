/*
 * Copyright © 2018-2022 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VisualMediaType
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.drspaceboo.transtracks.BuildConfig
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.background.CameraHandler
import com.drspaceboo.transtracks.background.StoragePermissionHandler
import com.drspaceboo.transtracks.data.Milestone
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.databinding.ActivityMainBinding
import com.drspaceboo.transtracks.ui.assignphoto.AssignPhotosController
import com.drspaceboo.transtracks.ui.home.HomeController
import com.drspaceboo.transtracks.ui.lock.LockController
import com.drspaceboo.transtracks.util.FileUtil
import com.drspaceboo.transtracks.util.RxSchedulers
import com.drspaceboo.transtracks.util.copyPhotoToTempFiles
import com.drspaceboo.transtracks.util.fileName
import com.drspaceboo.transtracks.util.gone
import com.drspaceboo.transtracks.util.openDefault
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.settings.LockType
import com.drspaceboo.transtracks.util.settings.SettingsManager
import com.drspaceboo.transtracks.util.using
import com.drspaceboo.transtracks.util.visible
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.stream.JsonReader
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import kotterknife.bindView
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {
    companion object {
        private const val BUFFER_SIZE = 8_192
    }

    private var router: Router? = null
    private lateinit var binding: ActivityMainBinding
    private val container: ViewGroup by bindView(R.id.controller_container)

    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    private lateinit var pickMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private var pickMediaHandlingData = PickMediaHandlingData()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(SettingsManager.getTheme().styleRes())

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        if (!BuildConfig.DEBUG) {
            FirebaseAnalytics.getInstance(this)
                .setAnalyticsCollectionEnabled(SettingsManager.getEnableAnalytics())
            FirebaseCrashlytics.getInstance()
                .setCrashlyticsCollectionEnabled(SettingsManager.getEnableCrashReports())
        }

        StoragePermissionHandler.install(this)
        CameraHandler.install(this)

        pickMediaLauncher = registerForActivityResult(PickMultipleVisualMedia()) { uris ->
            if (uris.isNullOrEmpty()) {
                return@registerForActivityResult
            }

            val photos = uris.mapNotNull { copyPhotoToTempFiles(it) }
                .map { Uri.fromFile(File(it)) }
                .let { ArrayList(it) }

            router?.pushController(
                RouterTransaction
                    .with(
                        AssignPhotosController(
                            uris = photos,
                            epochDay = pickMediaHandlingData.epochDay,
                            type = pickMediaHandlingData.type,
                            tagOfControllerToPopTo = pickMediaHandlingData.popToTag
                        )
                    )
                    .using(HorizontalChangeHandler())
            )
        }

        router = Conductor.attachRouter(this, container, savedInstanceState)
        if (!router!!.hasRootController()) {
            if (SettingsManager.getLockType() == LockType.off) {
                router!!.setRoot(RouterTransaction.with(HomeController()).tag(HomeController.TAG))
            } else {
                router!!.setBackstack(
                    listOf(
                        RouterTransaction.with(HomeController()).tag(HomeController.TAG),
                        RouterTransaction.with(LockController()).tag(LockController.TAG)
                            .using(VerticalChangeHandler())
                    ),
                    null
                )
            }
        }

        processIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        viewDisposables += SettingsManager.themeUpdated
            .map { it.styleRes() }
            .subscribe { themeRes ->
                setTheme(themeRes)
                val value = TypedValue()
                if (theme.resolveAttribute(R.attr.colorPrimaryDark, value, true)) {
                    window.statusBarColor = value.data
                }
            }

        viewDisposables += SettingsManager.lockTypeUpdated
            .subscribe { lockType ->
                if (lockType == LockType.off) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                }

                val defaultLauncherState: Int
                val trainLauncherState: Int

                if (lockType != LockType.trains) {
                    defaultLauncherState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    trainLauncherState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                } else {
                    defaultLauncherState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    trainLauncherState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                }

                packageManager.setComponentEnabledSetting(
                    ComponentName(
                        "com.drspaceboo.transtracks",
                        "com.drspaceboo.transtracks.MainActivityDefault"
                    ),
                    defaultLauncherState, PackageManager.DONT_KILL_APP
                )

                packageManager.setComponentEnabledSetting(
                    ComponentName(
                        "com.drspaceboo.transtracks", "com.drspaceboo.transtracks.MainActivityTrain"
                    ),
                    trainLauncherState, PackageManager.DONT_KILL_APP
                )
            }
    }

    override fun onBackPressed() {
        val localRouter = router

        if (localRouter == null || !localRouter.handleBack()) {
            super.onBackPressed()
        }
    }

    override fun onStart() {
        super.onStart()

        //Minimum 1000ms to step issues with rotation
        val timeToLock =
            SettingsManager.getUserLastSeen() + SettingsManager.getLockDelay().getMilli() + 1000L
        if (SettingsManager.getLockType() != LockType.off && timeToLock <= System.currentTimeMillis()) {
            showLockControllerIfNotAlreadyShowing()
        }
    }

    override fun onStop() {
        super.onStop()

        SettingsManager.updateUserLastSeen()
    }

    private fun showLockControllerIfNotAlreadyShowing() {
        val lockController = router?.getControllerWithTag(LockController.TAG)

        if (lockController == null) {
            router?.pushController(
                RouterTransaction.with(LockController())
                    .tag(LockController.TAG)
                    .popChangeHandler(VerticalChangeHandler())
            )
        }
    }

    private fun processIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return

        val fileUri: Uri? = intent.data
        if (fileUri == null) {
            AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(R.string.import_error)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.import_warning_title)
                .setMessage(R.string.import_warning_message)
                .setPositiveButton(android.R.string.yes) { _, _ -> processImport(fileUri) }
                .setNegativeButton(android.R.string.no, null)
                .show()
        }
    }

    fun launchPickMedia(type: VisualMediaType, handlingData: PickMediaHandlingData) {
        pickMediaHandlingData = handlingData
        pickMediaLauncher.launch(PickVisualMediaRequest(type))
    }

    sealed class ImportResult {
        object Failure : ImportResult()
        data class Success(val photoIssues: Int, val milestoneIssues: Int) : ImportResult()
    }

    private fun processImport(fileUri: Uri) {
        binding.loadingProgress.progress = 0
        binding.loadingLayout.visible()

        val ignored = Observable.just(fileUri)
            .subscribeOn(RxSchedulers.io())
            .map<ImportResult> { uri ->
                return@map try {
                    var tempDataFile: File? = null

                    contentResolver.openInputStream(uri).use { inputStream ->
                        ZipInputStream(inputStream).use { zipInputStream ->
                            val data = ByteArray(BUFFER_SIZE)
                            var zipEntry: ZipEntry? = zipInputStream.nextEntry
                            while (zipEntry != null) {
                                val tempFile: File = when (val fileName = zipEntry.fileName()) {
                                    "data.json" -> FileUtil.getTempFile(fileName)
                                        .also { tempDataFile = it }

                                    else -> FileUtil.getImageFile(fileName)
                                }
                                try {
                                    FileOutputStream(tempFile).use { fileOutputStream ->
                                        var count: Int = zipInputStream.read(data)
                                        while (count != -1) {
                                            fileOutputStream.write(data, 0, count)
                                            count = zipInputStream.read(data)
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                zipEntry = zipInputStream.nextEntry
                            }
                        }
                    }

                    runOnUiThread {
                        binding.loadingProgress.progress = 50
                    }

                    val dataFile = tempDataFile
                    var photoImportIssues = 0
                    var milestoneImportIssues = 0

                    if (dataFile != null && dataFile.exists() && dataFile.length() > 0) {
                        try {
                            FileReader(tempDataFile!!).use { fileReader ->
                                BufferedReader(fileReader).use { bufferedReader ->
                                    JsonReader(bufferedReader).use { jsonReader ->
                                        val realm = Realm.openDefault()
                                        realm.writeBlocking {
                                            jsonReader.beginObject()
                                            while (jsonReader.hasNext()) {
                                                when (jsonReader.nextName()) {
                                                    "settings" -> {
                                                        jsonReader.beginObject()
                                                        SettingsManager.getSettingsFromJson(
                                                            jsonReader
                                                        )
                                                        jsonReader.endObject()
                                                    }

                                                    "photos" -> {
                                                        jsonReader.beginArray()
                                                        while (jsonReader.hasNext()) {
                                                            jsonReader.beginObject()
                                                            val photo = Photo.fromJson(jsonReader)
                                                            if (photo != null) {
                                                                copyToRealm(photo, UpdatePolicy.ALL)
                                                            } else {
                                                                photoImportIssues++
                                                            }
                                                            jsonReader.endObject()
                                                        }
                                                        jsonReader.endArray()
                                                    }

                                                    "milestones" -> {
                                                        jsonReader.beginArray()
                                                        while (jsonReader.hasNext()) {
                                                            jsonReader.beginObject()
                                                            val milestone =
                                                                Milestone.fromJson(jsonReader)
                                                            if (milestone != null) {
                                                                copyToRealm(
                                                                    milestone, UpdatePolicy.ALL
                                                                )
                                                            } else {
                                                                milestoneImportIssues++
                                                            }
                                                            jsonReader.endObject()
                                                        }
                                                        jsonReader.endArray()
                                                    }

                                                    else -> jsonReader.skipValue()
                                                }
                                            }
                                        }

                                        realm.close()
                                    }
                                }
                            }

                            return@map ImportResult.Success(
                                photoImportIssues, milestoneImportIssues
                            )
                        } catch (e: Exception) {
                            return@map ImportResult.Failure
                        }
                    } else {
                        return@map ImportResult.Failure
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    ImportResult.Failure
                }
            }
            .observeOn(RxSchedulers.main())
            .subscribe { result ->
                binding.loadingLayout.gone()
                when (result) {
                    ImportResult.Failure -> {
                        AlertDialog.Builder(this)
                            .setTitle(R.string.error)
                            .setMessage(R.string.import_failure)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }

                    is ImportResult.Success -> {
                        val photoIssues = result.photoIssues
                        val milestoneIssues = result.milestoneIssues
                        when {
                            photoIssues > 0 || milestoneIssues > 0 -> {
                                val errors = StringBuilder()
                                if (photoIssues > 0) {
                                    errors.append(
                                        resources.getQuantityString(
                                            R.plurals.photos, photoIssues, photoIssues
                                        )
                                    )
                                }
                                if (milestoneIssues > 0) {
                                    errors.append(
                                        resources.getQuantityString(
                                            R.plurals.milestones, milestoneIssues, milestoneIssues
                                        )
                                    )
                                }
                                val message = getString(
                                    R.string.import_partial_success_description, errors.toString()
                                )

                                AlertDialog.Builder(this)
                                    .setTitle(R.string.import_partial_success_title)
                                    .setMessage(message)
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }

                            else -> {
                                Snackbar.make(
                                    findViewById(R.id.controller_container),
                                    R.string.import_complete_success,
                                    LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewDisposables.clear()
    }
}

data class PickMediaHandlingData(
    val epochDay: Long? = null,
    @Photo.Type val type: Int = Photo.TYPE_BODY,
    val popToTag: String = HomeController.TAG
)