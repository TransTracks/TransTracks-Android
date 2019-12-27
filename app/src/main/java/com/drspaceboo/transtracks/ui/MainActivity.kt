/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.crashlytics.android.core.CrashlyticsCore
import com.drspaceboo.transtracks.BuildConfig
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.background.CameraHandler
import com.drspaceboo.transtracks.background.StoragePermissionHandler
import com.drspaceboo.transtracks.ui.home.HomeController
import com.drspaceboo.transtracks.ui.lock.LockController
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.settings.LockType
import com.drspaceboo.transtracks.util.settings.SettingsManager
import com.drspaceboo.transtracks.util.using
import io.fabric.sdk.android.Fabric
import io.reactivex.disposables.CompositeDisposable
import kotterknife.bindView

class MainActivity : AppCompatActivity() {
    private var router: Router? = null
    private val container: ViewGroup by bindView(R.id.controller_container)

    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(SettingsManager.getTheme().styleRes())
        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG) {
            AnalyticsUtil.disable()
        }

        Fabric.with(this, CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build())

        StoragePermissionHandler.install(this)
        CameraHandler.install(this)

        router = Conductor.attachRouter(this, container, savedInstanceState)
        if (!router!!.hasRootController()) {
            if (SettingsManager.getLockType() == LockType.off) {
                router!!.setRoot(RouterTransaction.with(HomeController()).tag(HomeController.TAG))
            } else {
                router!!.setBackstack(
                    listOf(
                        RouterTransaction.with(HomeController()).tag(HomeController.TAG),
                        RouterTransaction.with(LockController()).tag(LockController.TAG).using(VerticalChangeHandler())
                    ),
                    null
                )
            }
        }
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
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
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
                        "com.drspaceboo.transtracks", "com.drspaceboo.transtracks.MainActivityDefault"
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
        val timeToLock = SettingsManager.getUserLastSeen() + SettingsManager.getLockDelay().getMilli() + 1000L
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewDisposables.clear()
    }
}
