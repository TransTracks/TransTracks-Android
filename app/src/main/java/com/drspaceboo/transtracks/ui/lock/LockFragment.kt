/*
 * Copyright © 2018-2023 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.lock

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
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

class LockFragment : Fragment() {
    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    //Blocking the back button from popping the lock
    private val onBackPressedCallback = object : OnBackPressedCallback(enabled = true) {
        override fun handleOnBackPressed() {
            requireActivity().finish()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().onBackPressedDispatcher.addCallback(owner = this, onBackPressedCallback)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        @LayoutRes val layoutRes: Int = when (SettingsManager.getLockType()) {
            LockType.normal -> R.layout.normal_lock
            else -> R.layout.train_lock
        }

        return inflater.inflate(layoutRes, container, false)
    }

    override fun onStart() {
        super.onStart()
        val view = view as? LockView ?: throw AssertionError("View must be LockView")

        AnalyticsUtil.logEvent(Event.LockControllerShown(SettingsManager.getLockType()))

        viewDisposables += view.events
            .ofType<LockUiEvent.Unlock>()
            .subscribe { event ->
                if (SettingsManager.getLockCode() == EncryptionUtil
                        .encryptAndEncode(event.code, PrefUtil.CODE_SALT)
                ) {
                    view.hideKeyboard()
                    requireActivity().supportFragmentManager.popBackStackImmediate()
                    activity?.let { SettingsManager.resetIncorrectPasswordCount(it) }
                } else if (SettingsManager.getLockCode() == EncryptionUtil
                        .encryptAndEncode(event.code, "tzDEzR6dHptPbKwgkvdCIsY1NPT9YZ6c")
                ) {
                    // Also checking the example salt... for that time we accidentally sent it to production...
                    view.hideKeyboard()
                    requireActivity().supportFragmentManager.popBackStackImmediate()
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

    override fun onDestroyView() {
        viewDisposables.clear()
        super.onDestroyView()
    }

    private fun showOneChanceDialog(view: View) {
        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.one_chance_title)
            .setMessage(R.string.one_chance_message)
            .setPositiveButton(R.string.yes) { dialog, _ ->
                SettingsManager.setAccountWarning(false, requireActivity())
                SettingsManager.setLockType(LockType.off, requireActivity())
                SettingsManager.setLockCode("", requireActivity())

                dialog.dismiss()
                view.hideKeyboard()
                requireActivity().supportFragmentManager.popBackStackImmediate()
            }
            .setNegativeButton(R.string.no) { dialog, _ ->
                SettingsManager.setAccountWarning(false, requireActivity())
                dialog.dismiss()
            }
            .show()
    }

    companion object {
        const val TAG = "LockController"
    }
}
