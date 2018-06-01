/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracker.ui

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.TypedValue
import android.view.ViewGroup
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.crashlytics.android.Crashlytics
import com.drspaceboo.transtracker.BuildConfig
import com.drspaceboo.transtracker.R
import com.drspaceboo.transtracker.ui.home.HomeController
import com.drspaceboo.transtracker.util.PrefUtil
import com.drspaceboo.transtracker.util.PrefUtil.THEME_BLUE
import com.drspaceboo.transtracker.util.PrefUtil.THEME_PINK
import com.drspaceboo.transtracker.util.plusAssign
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
            else -> throw IllegalArgumentException("Unhandled theme type")
        }
        setTheme(themeRes)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!BuildConfig.DEBUG) {
            Fabric.with(this, Crashlytics())
        }

        router = Conductor.attachRouter(this, container, savedInstanceState)
        if (!router!!.hasRootController()) {
            router!!.setRoot(RouterTransaction.with(HomeController()))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        viewDisposables += PrefUtil.theme.asObservable()
                .map { themeType ->
                    return@map when (themeType) {
                        THEME_PINK -> R.style.PinkAppTheme
                        THEME_BLUE -> R.style.BlueAppTheme
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
    }

    override fun onBackPressed() {
        val localRouter = router

        if (localRouter == null || !localRouter.handleBack()) {
            super.onBackPressed()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewDisposables.clear()
    }
}
