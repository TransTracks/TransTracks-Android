/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.lock

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.bluelinelabs.conductor.Controller
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.EncryptionUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.hideKeyboard
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.settings.LockType
import com.drspaceboo.transtracks.util.settings.PrefUtil
import com.drspaceboo.transtracks.util.settings.SettingsManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.reactivex.rxjava3.disposables.CompositeDisposable

class LockController : Controller() {
    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        @LayoutRes val layoutRes: Int = when (SettingsManager.getLockType()) {
            LockType.normal -> R.layout.normal_lock
            else -> R.layout.train_lock
        }

        return inflater.inflate(layoutRes, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is LockView) throw AssertionError("View must be LockView")

        AnalyticsUtil.logEvent(Event.LockControllerShown(SettingsManager.getLockType()))

        viewDisposables += view.events
            .ofType<LockUiEvent.Unlock>()
            .subscribe { event ->
                if (SettingsManager.getLockCode() == EncryptionUtil
                        .encryptAndEncode(event.code, PrefUtil.CODE_SALT)
                ) {
                    view.hideKeyboard()
                    router.popCurrentController()
                    activity?.let { SettingsManager.resetIncorrectPasswordCount(it) }
                } else if (SettingsManager.getLockCode() == EncryptionUtil
                        .encryptAndEncode(event.code, "tzDEzR6dHptPbKwgkvdCIsY1NPT9YZ6c")
                ) {
                    // Also checking the example salt... for that time we accidentally sent it to production...
                    view.hideKeyboard()
                    router.popCurrentController()
                    activity?.let { SettingsManager.resetIncorrectPasswordCount(it) }

                    //Recording non-fatal to see how many people are effected
                    FirebaseCrashlytics.getInstance()
                        .recordException(Exception("Using the example salt"))
                    //TODO We may want to notify users to update their passcodes in this case
                } else {
                    @StringRes val messageRes: Int = when (SettingsManager.getLockType()) {
                        LockType.normal -> R.string.incorrect_password
                        else -> R.string.train_incorrect
                    }

                    Snackbar.make(view, messageRes, Snackbar.LENGTH_LONG).show()
                    activity?.let { SettingsManager.incrementIncorrectPasswordCount(it) }

                    if (SettingsManager.showAccountWarning() && SettingsManager.getIncorrectPasswordCount() >= 25) {
                        showOneChanceDialog(view)
                    }
                }
            }
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        SettingsManager.resetIncorrectPasswordCount(activity)
    }

    override fun onDetach(view: View) {
        viewDisposables.clear()
    }

    //Block back presses
    override fun handleBack(): Boolean {
        activity?.finish()
        return true
    }

    private fun showOneChanceDialog(view: View) {
        AlertDialog.Builder(activity!!)
            .setTitle(R.string.one_chance_title)
            .setMessage(R.string.one_chance_message)
            .setPositiveButton(R.string.yes) { dialog, _ ->
                SettingsManager.setAccountWarning(false, activity!!)
                SettingsManager.setLockType(LockType.off, activity!!)
                SettingsManager.setLockCode("", activity!!)

                dialog.dismiss()
                view.hideKeyboard()
                router.popCurrentController()
            }
            .setNegativeButton(R.string.no) { dialog, _ ->
                SettingsManager.setAccountWarning(false, activity!!)
                dialog.dismiss()
            }
            .show()
    }

    companion object {
        const val TAG = "LockController"
    }
}
