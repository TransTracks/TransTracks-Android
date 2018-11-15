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
import com.crashlytics.android.Crashlytics
import com.drspaceboo.transtracks.BuildConfig
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.background.CameraHandler
import com.drspaceboo.transtracks.background.StoragePermissionHandler
import com.drspaceboo.transtracks.ui.home.HomeController
import com.drspaceboo.transtracks.ui.lock.LockController
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.PrefUtil
import com.drspaceboo.transtracks.util.PrefUtil.THEME_BLUE
import com.drspaceboo.transtracks.util.PrefUtil.THEME_GREEN
import com.drspaceboo.transtracks.util.PrefUtil.THEME_PINK
import com.drspaceboo.transtracks.util.PrefUtil.THEME_PURPLE
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.using
import io.fabric.sdk.android.Fabric
import io.reactivex.disposables.CompositeDisposable
import kotterknife.bindView

class MainActivity : AppCompatActivity() {
    private var router: Router? = null
    private val container: ViewGroup by bindView(R.id.controller_container)

    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeRes = when (PrefUtil.theme.get()) {
            THEME_PINK -> R.style.PinkAppTheme
            THEME_BLUE -> R.style.BlueAppTheme
            THEME_PURPLE -> R.style.PurpleAppTheme
            THEME_GREEN -> R.style.GreenAppTheme
            else -> throw IllegalArgumentException("Unhandled theme type")
        }
        setTheme(themeRes)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG) {
            AnalyticsUtil.disable()
        } else {
            Fabric.with(this, Crashlytics())
        }

        StoragePermissionHandler.install(this)
        CameraHandler.install(this)

        router = Conductor.attachRouter(this, container, savedInstanceState)
        if (!router!!.hasRootController()) {
            if (PrefUtil.lockType.get() == PrefUtil.LOCK_OFF) {
                router!!.setRoot(RouterTransaction.with(HomeController()).tag(HomeController.TAG))
            } else {
                router!!.setBackstack(listOf(RouterTransaction.with(HomeController())
                                                     .tag(HomeController.TAG),
                                             RouterTransaction.with(LockController())
                                                     .tag(LockController.TAG)
                                                     .using(VerticalChangeHandler())),
                                      null)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        viewDisposables += PrefUtil.theme.asObservable()
                .map { themeType ->
                    return@map when (themeType) {
                        THEME_PINK -> R.style.PinkAppTheme
                        THEME_BLUE -> R.style.BlueAppTheme
                        THEME_PURPLE -> R.style.PurpleAppTheme
                        THEME_GREEN -> R.style.GreenAppTheme
                        else -> throw IllegalArgumentException("Unhandled theme type")
                    }
                }
                .subscribe { themeRes ->
                    setTheme(themeRes)
                    val value = TypedValue()
                    if (theme.resolveAttribute(R.attr.colorPrimaryDark, value, true)) {
                        window.statusBarColor = value.data
                    }
                }

        viewDisposables += PrefUtil.lockType.asObservable()
                .subscribe { lockType ->
                    if (lockType == PrefUtil.LOCK_OFF) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                                        WindowManager.LayoutParams.FLAG_SECURE)
                    }

                    val defaultLauncherState: Int
                    val trainLauncherState: Int

                    if (lockType != PrefUtil.LOCK_TRAINS) {
                        defaultLauncherState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        trainLauncherState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    } else {
                        defaultLauncherState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        trainLauncherState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    }

                    packageManager.setComponentEnabledSetting(
                            ComponentName("com.drspaceboo.transtracks",
                                          "com.drspaceboo.transtracks.MainActivityDefault"),
                            defaultLauncherState, PackageManager.DONT_KILL_APP)

                    packageManager.setComponentEnabledSetting(
                            ComponentName("com.drspaceboo.transtracks",
                                          "com.drspaceboo.transtracks.MainActivityTrain"),
                            trainLauncherState, PackageManager.DONT_KILL_APP)
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
        val timeToLock = PrefUtil.userLastSeen.get() + PrefUtil.getLockDelayMilli() + 1000L

        if (PrefUtil.lockType.get() != PrefUtil.LOCK_OFF
                && timeToLock <= System.currentTimeMillis()) {
            showLockControllerIfNotAlreadyShowing()
        }
    }

    override fun onStop() {
        super.onStop()

        PrefUtil.userLastSeen.set(System.currentTimeMillis())
    }

    private fun showLockControllerIfNotAlreadyShowing() {
        val lockController = router?.getControllerWithTag(LockController.TAG)

        if (lockController == null) {
            router?.pushController(RouterTransaction.with(LockController())
                                           .tag(LockController.TAG)
                                           .popChangeHandler(VerticalChangeHandler()))
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewDisposables.clear()
    }
}
