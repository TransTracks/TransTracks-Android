/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.annotation.NonNull
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.drspaceboo.transtracks.BuildConfig
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.TransTracksApp
import com.drspaceboo.transtracks.data.TransTracksFileProvider
import com.drspaceboo.transtracks.domain.SettingsAction
import com.drspaceboo.transtracks.domain.SettingsAction.SettingsUpdated
import com.drspaceboo.transtracks.domain.SettingsDomain
import com.drspaceboo.transtracks.domain.SettingsResult
import com.drspaceboo.transtracks.domain.SettingsViewEffect
import com.drspaceboo.transtracks.ui.widget.SimpleTextWatcher
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.EncryptionUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.ProgressDialog
import com.drspaceboo.transtracks.util.RxSchedulers
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.isNotDisposed
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.settings.LockDelay
import com.drspaceboo.transtracks.util.settings.LockType
import com.drspaceboo.transtracks.util.settings.PrefUtil
import com.drspaceboo.transtracks.util.settings.SettingsManager
import com.drspaceboo.transtracks.util.settings.Theme
import com.drspaceboo.transtracks.util.simpleIsEmail
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.UserProfileChangeRequest
import io.reactivex.Completable
import io.reactivex.ObservableTransformer
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import org.threeten.bp.LocalDate
import java.util.Calendar

class SettingsController : Controller() {
    private var resultsDisposable: Disposable = Disposables.disposed()
    private var viewDisposables: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(@NonNull inflater: LayoutInflater, @NonNull container: ViewGroup): View {
        return inflater.inflate(R.layout.settings, container, false)
    }

    override fun onAttach(view: View) {
        if (view !is SettingsView) throw AssertionError("View must be SettingsView")

        AnalyticsUtil.logEvent(Event.SettingsControllerShown)

        val domain: SettingsDomain = TransTracksApp.instance.domainManager.settingsDomain

        if (resultsDisposable.isDisposed) {
            resultsDisposable = domain.results.subscribe()
        }

        viewDisposables += domain.results
            .compose(settingsResultsToStates(view.context))
            .subscribe { state -> view.display(state) }

        viewDisposables += domain.viewEffects
            .ofType<SettingsViewEffect.ShowBackupResult>()
            .subscribe { effect ->
                when (val zip = effect.zipFile) {
                    null -> AlertDialog.Builder(view.context)
                        .setTitle(R.string.error)
                        .setMessage(R.string.error_creating_backup_file)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()

                    else -> {
                        val uri =
                            FileProvider.getUriForFile(view.context, TransTracksFileProvider::class.java.name, zip)
                        val shareIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, uri)
                            type = "application/zip"
                        }
                        startActivity(Intent.createChooser(shareIntent, view.resources.getText(R.string.send_to)))
                    }
                }
            }

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents.ofType<SettingsUiEvent.Export>()
            .map { SettingsAction.Export }
            .subscribe(domain.actions)

        viewDisposables += sharedEvents
            .ofType<SettingsUiEvent.Back>()
            .subscribe { router.handleBack() }

        viewDisposables += sharedEvents
            .ofType<SettingsUiEvent.SignIn>()
            .subscribe { showAuth() }

        viewDisposables += sharedEvents
            .ofType<SettingsUiEvent.SignOut>()
            .subscribe {
                val context = activity ?: return@subscribe

                AuthUI.getInstance()
                    .signOut(context)
                    .addOnCompleteListener { result ->
                        if (result.isSuccessful) {
                            SettingsManager.disableFirebaseSync()
                        } else {
                            Snackbar.make(view, R.string.sign_out_error, Snackbar.LENGTH_LONG).show()
                        }

                        domain.actions.accept(SettingsUpdated)
                    }
            }

        viewDisposables += sharedEvents
            .ofType<SettingsUiEvent.ChangePassword>()
            .subscribe {
                val email = FirebaseAuth.getInstance().currentUser?.email
                if (email != null) {
                    FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener { result ->
                        if (result.isSuccessful) {
                            Snackbar.make(view, R.string.passwordResetSuccess, Snackbar.LENGTH_LONG).show()
                        } else {
                            Snackbar.make(view, R.string.passwordResetFailed, Snackbar.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Snackbar.make(view, R.string.unableToResetPassword, Snackbar.LENGTH_LONG).show()
                }
            }

        viewDisposables += sharedEvents
            .ofType<SettingsUiEvent.ChangeName>()
            .subscribe { showChangeNameDialog(view) }

        viewDisposables += sharedEvents
            .ofType<SettingsUiEvent.ChangeEmail>()
            .subscribe { showChangeEmailDialog(view) }

        viewDisposables += sharedEvents
            .ofType<SettingsUiEvent.ChangeStartDate>()
            .subscribe {
                val startDate = SettingsManager.getStartDate(activity!!)

                //Note: The DatePickerDialog uses 0 based months
                DatePickerDialog(
                    view.context,
                    { _, year, month, dayOfMonth ->
                        SettingsManager.setStartDate(LocalDate.of(year, month + 1, dayOfMonth), activity!!)
                    },
                    startDate.year, startDate.monthValue - 1, startDate.dayOfMonth
                ).show()
            }

        viewDisposables += sharedEvents
            .ofType<SettingsUiEvent.ChangeTheme>()
            .subscribe {
                val theme = SettingsManager.getTheme()

                AlertDialog.Builder(view.context)
                    .setTitle(R.string.select_theme)
                    .setSingleChoiceItems(
                        arrayOf(
                            view.getString(R.string.pink),
                            view.getString(R.string.blue),
                            view.getString(R.string.purple),
                            view.getString(R.string.green)
                        ),
                        theme.ordinal
                    ) { dialog: DialogInterface, index: Int ->
                        if (theme.ordinal != index) {
                            SettingsManager.setTheme(Theme.values()[index], activity!!)
                            router.replaceTopController(RouterTransaction.with(SettingsController()))
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }

        viewDisposables += sharedEvents
            .ofType<SettingsUiEvent.ChangeLockMode>()
            .subscribe { showChangeLockModeDialog(view) }

        viewDisposables += sharedEvents
            .ofType<SettingsUiEvent.ChangeLockDelay>()
            .subscribe {
                val delay = SettingsManager.getLockDelay()

                AlertDialog.Builder(view.context)
                    .setTitle(R.string.select_lock_delay)
                    .setSingleChoiceItems(
                        arrayOf(
                            view.getString(R.string.instant),
                            view.getString(R.string.one_minute),
                            view.getString(R.string.two_minutes),
                            view.getString(R.string.five_minutes),
                            view.getString(R.string.fifteen_minutes)
                        ),
                        delay.ordinal
                    ) { dialog: DialogInterface, index: Int ->
                        if (delay.ordinal != index) {
                            SettingsManager.setLockDelay(LockDelay.values()[index], activity!!)
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }

        viewDisposables += sharedEvents.ofType<SettingsUiEvent.Import>()
            .subscribe {
                AlertDialog.Builder(view.context)
                    .setTitle(R.string.import_info_title)
                    .setMessage(R.string.import_info_message)
                    .setPositiveButton(R.string.ok, null)
                    .setNeutralButton(R.string.go_to_play_store) { dialog, _ ->
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=com.File.Manager.Filemanager")
                            )
                        )
                        dialog.dismiss()
                    }
                    .show()
            }

        viewDisposables += sharedEvents.ofType<SettingsUiEvent.PrivacyPolicy>()
            .subscribe {
                val activity = activity ?: return@subscribe

                val webpage = Uri.parse("http://www.drspaceboo.com/privacy-policy/")
                val intent = Intent(Intent.ACTION_VIEW, webpage)
                if (intent.resolveActivity(activity.packageManager) != null) {
                    startActivity(intent)
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_FIREBASE_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)

            if (resultCode == Activity.RESULT_OK) {
                SettingsManager.attemptFirebaseAutoSetup(activity!!)
            } else {
                view?.let { view ->
                    response?.error?.let { Snackbar.make(view, R.string.sign_in_error, Snackbar.LENGTH_SHORT) }
                }
            }

            TransTracksApp.instance.domainManager.settingsDomain.actions.accept(SettingsUpdated)
        }
    }

    override fun onDetach(view: View) {
        viewDisposables.clear()
    }

    override fun onDestroy() {
        if (resultsDisposable.isNotDisposed()) {
            resultsDisposable.dispose()
        }
    }

    private fun showAppNameChangeSnackbar(view: View, @StringRes newAppName: Int) {
        //Don't try to show the Snackbar if the Controller isn't currently attached or is being destroyed
        if (!isAttached || isDestroyed || isBeingDestroyed) {
            return
        }

        val snackbar = Snackbar.make(
            view, view.getString(
                R.string.changing_app_name,
                view.getString(newAppName)
            ),
            Snackbar.LENGTH_LONG
        )
        snackbar.setAction(R.string.more_info) {
            AlertDialog.Builder(view.context).setMessage(R.string.changing_app_name_more_info)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
        snackbar.show()
    }

    private fun showChangeLockModeDialog(view: View) {
        fun showRemovePasswordDialog() {
            val builder = AlertDialog.Builder(view.context).setTitle(R.string.enter_password_to_disable_lock)

            @SuppressLint("InflateParams") // Unable to provide root
            val dialogView = LayoutInflater.from(builder.context).inflate(R.layout.enter_password_dialog, null)
            val password: EditText = dialogView.findViewById(R.id.set_password_code)

            val passwordDialog = builder.setView(dialogView)
                .setPositiveButton(R.string.disable, null)
                .setNegativeButton(R.string.cancel, null)
                .create()


            password.addTextChangedListener(object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable) {
                    passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !s.isBlank()
                }
            })
            passwordDialog.setOnShowListener { dialog ->
                val positiveButton = passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                positiveButton.isEnabled = false

                positiveButton.setOnClickListener {
                    if (SettingsManager.getLockCode()
                        != EncryptionUtil.encryptAndEncode(password.text.toString(), PrefUtil.CODE_SALT)
                    ) {
                        Snackbar.make(view, R.string.incorrect_password, Snackbar.LENGTH_LONG).show()
                        return@setOnClickListener
                    }

                    SettingsManager.setLockCode("", activity!!)
                    SettingsManager.setLockType(LockType.off, activity!!)
                    dialog.dismiss()
                }
            }

            passwordDialog.show()

            passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        }

        fun showSetPasswordDialog(newLockType: LockType) {
            val builder = AlertDialog.Builder(view.context).setTitle(R.string.set_password)

            @SuppressLint("InflateParams") // Unable to provide root
            val dialogView = LayoutInflater.from(builder.context)
                .inflate(R.layout.set_password_dialog, null)
            val password: EditText = dialogView.findViewById(R.id.set_password_code)
            val confirm: EditText = dialogView.findViewById(R.id.confirm_password_code)

            val passwordDialog = builder.setView(dialogView)
                .setPositiveButton(R.string.set_password, null)
                .setNegativeButton(R.string.cancel, null)
                .create()

            password.addTextChangedListener(object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable) {
                    passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !s.isBlank()
                }
            })

            passwordDialog.setOnShowListener { dialog ->
                val positiveButton = passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                positiveButton.isEnabled = false
                positiveButton.setOnClickListener {
                    val passwordText = password.text.toString()
                    val confirmText = confirm.text.toString()

                    if (passwordText.isEmpty()) {
                        Snackbar.make(view, R.string.password_cannot_be_empty, Snackbar.LENGTH_LONG).show()
                        return@setOnClickListener
                    } else if (passwordText != confirmText) {
                        Snackbar.make(view, R.string.password_and_confirm, Snackbar.LENGTH_LONG).show()
                        return@setOnClickListener
                    }

                    SettingsManager.setLockCode(
                        EncryptionUtil.encryptAndEncode(password.text.toString(), PrefUtil.CODE_SALT), activity!!
                    )
                    SettingsManager.setLockType(newLockType, activity!!)

                    if (newLockType == LockType.trains) {
                        showAppNameChangeSnackbar(view, R.string.train_tracks_title)
                    }

                    if (FirebaseAuth.getInstance().currentUser == null) {
                        AlertDialog.Builder(view.context)
                            .setTitle(R.string.warning_title)
                            .setMessage(R.string.warning_message)
                            .setPositiveButton(R.string.create_account) { dialog, _ ->
                                SettingsManager.setAccountWarning(false, view.context)
                                dialog.dismiss()
                                showAuth()
                            }
                            .setNegativeButton(R.string.risk_it) { dialog, _ ->
                                SettingsManager.setAccountWarning(false, view.context)
                                dialog.dismiss()
                            }
                            .show()
                    }

                    dialog.dismiss()
                }
            }
            passwordDialog.show()
        }

        val lockMode = SettingsManager.getLockType()

        AlertDialog.Builder(view.context)
            .setTitle(R.string.select_lock_mode)
            .setSingleChoiceItems(
                arrayOf(
                    view.getString(R.string.disabled),
                    view.getString(R.string.enabled_normal),
                    view.getString(R.string.enabled_trains)
                ),
                lockMode.ordinal
            ) { dialog: DialogInterface, index: Int ->
                val newLockType = LockType.values()[index]

                if (lockMode != newLockType) {
                    val hasCode = SettingsManager.getLockCode().isNotEmpty()

                    when {
                        newLockType == LockType.off -> {
                            //Turn off lock, and remove the code
                            showRemovePasswordDialog()
                        }

                        hasCode -> {
                            //Changing to another type with the code on, just update type
                            SettingsManager.setLockType(newLockType, activity!!)

                            if (newLockType == LockType.trains) {
                                showAppNameChangeSnackbar(view, R.string.train_tracks_title)
                            } else {
                                showAppNameChangeSnackbar(view, R.string.app_name)
                            }
                        }

                        else -> {
                            //Changing to a type with a code, let's ask for the code
                            showSetPasswordDialog(newLockType)
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showChangeNameDialog(view: View) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val name = currentUser.displayName ?: return

        val builder = AlertDialog.Builder(view.context).setTitle(R.string.update_account_name)

        @SuppressLint("InflateParams") // Unable to provide root
        val dialogView = LayoutInflater.from(builder.context).inflate(R.layout.update_name_dialog, null)
        val nameEditText: EditText = dialogView.findViewById(R.id.set_account_name)

        val nameDialog = builder.setView(dialogView)
            .setPositiveButton(R.string.update, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        nameEditText.setText(name)
        nameEditText.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) {
                nameDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !s.isBlank()
            }
        })

        nameDialog.setOnShowListener { dialog ->
            val positiveButton = nameDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.isEnabled = false
            positiveButton.setOnClickListener {
                val nameText = nameEditText.text.toString()

                if (nameText.isEmpty()) {
                    Snackbar.make(view, R.string.name_cannot_be_empty, Snackbar.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val progressDialog = ProgressDialog.make(R.string.updating_name, view.context)
                progressDialog.show()

                Completable
                    .fromAction {
                        val profileChangeRequest = UserProfileChangeRequest.Builder().setDisplayName(nameText).build()
                        try {
                            currentUser.updateProfile(profileChangeRequest)
                                .addOnCompleteListener { result ->
                                    result.exception?.let { exception ->
                                        if (exception is FirebaseAuthInvalidUserException) {
                                            showReauth(view)
                                        }
                                    }

                                    if (!result.isSuccessful) {
                                        Snackbar.make(view, R.string.unableToUpdateName, Snackbar.LENGTH_LONG)
                                            .show()
                                    }

                                    TransTracksApp.instance.domainManager.settingsDomain.actions.accept(SettingsUpdated)
                                    progressDialog.dismiss()
                                }
                        } catch (e: FirebaseAuthInvalidUserException) {
                            e.printStackTrace()
                            showReauth(view)
                            progressDialog.dismiss()
                        }
                    }
                    .subscribeOn(RxSchedulers.io())
                    .observeOn(RxSchedulers.main())
                    .subscribe()



                dialog.dismiss()
            }
        }
        nameDialog.show()
    }

    private fun showChangeEmailDialog(view: View) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val email = currentUser.email ?: return

        val builder = AlertDialog.Builder(view.context).setTitle(R.string.update_email_address)

        @SuppressLint("InflateParams") // Unable to provide root
        val dialogView = LayoutInflater.from(builder.context).inflate(R.layout.update_email_dialog, null)
        val emailEditText: EditText = dialogView.findViewById(R.id.set_email_address)

        val emailDialog = builder.setView(dialogView)
            .setPositiveButton(R.string.update, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        emailEditText.setText(email)
        emailEditText.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) {
                emailDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = s.toString().simpleIsEmail()
            }
        })

        emailDialog.setOnShowListener { dialog ->
            val positiveButton = emailDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.isEnabled = false
            positiveButton.setOnClickListener {
                val emailText = emailEditText.text.toString()

                if (emailText.isEmpty()) {
                    Snackbar.make(view, R.string.email_must_be_valid, Snackbar.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val progressDialog = ProgressDialog.make(R.string.updating_email, view.context)
                progressDialog.show()

                Completable
                    .fromAction {
                        try {
                            currentUser.updateEmail(emailText).addOnCompleteListener { result ->
                                result.exception?.let { handleEmailChangeException(it, view) }

                                TransTracksApp.instance.domainManager.settingsDomain.actions.accept(SettingsUpdated)
                                progressDialog.dismiss()
                            }
                        } catch (e: FirebaseAuthException) {
                            handleEmailChangeException(e, view)
                            progressDialog.dismiss()
                        }
                    }
                    .subscribeOn(RxSchedulers.io())
                    .observeOn(RxSchedulers.main())
                    .subscribe()

                dialog.dismiss()
            }
        }
        emailDialog.show()
    }

    private fun handleEmailChangeException(e: Exception, view: View) {
        e.printStackTrace()

        @StringRes var messageRes: Int = R.string.unableToUpdateEmail
        when (e) {
            is FirebaseAuthInvalidCredentialsException -> messageRes = R.string.email_must_be_valid
            is FirebaseAuthUserCollisionException -> messageRes = R.string.email_in_use
            is FirebaseAuthInvalidUserException,
            is FirebaseAuthRecentLoginRequiredException -> showReauth(view)
        }
        Snackbar.make(view, messageRes, Snackbar.LENGTH_LONG).show()
    }

    private fun showAuth() {
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(), AuthUI.IdpConfig.GoogleBuilder().build(),
            AuthUI.IdpConfig.TwitterBuilder().build()
        )

        startActivityForResult(
            AuthUI.getInstance().createSignInIntentBuilder().setAvailableProviders(providers).build(),
            REQUEST_FIREBASE_SIGN_IN
        )
    }

    private fun showReauth(view: View) {
        AlertDialog.Builder(view.context)
            .setTitle(R.string.session_expired)
            .setMessage(R.string.session_expired_message)
            .setNegativeButton(R.string.later, null)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                showAuth()
                dialog.dismiss()
            }
            .show()
    }

    companion object {
        private const val REQUEST_FIREBASE_SIGN_IN = 6524
    }
}

fun settingsResultsToStates(context: Context) = ObservableTransformer<SettingsResult, SettingsUiState> { results ->
    fun contentToLoaded(content: SettingsResult.Content): SettingsUiState.Content {
        val themeName = context.getString(content.theme.displayNameRes())
        val lockName = context.getString(content.lockType.displayNameRes())
        val lockDelayName = context.getString(content.lockDelay.displayNameRes())

        return SettingsUiState.Content(
            content.userDetails, content.startDate, themeName, lockName,
            enableLockDelay = content.lockType != LockType.off, lockDelay = lockDelayName,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            copyright = context.getString(R.string.copyright, Calendar.getInstance().get(Calendar.YEAR).toString()),
            showAds = SettingsManager.showAds()
        )
    }

    return@ObservableTransformer results.map { result ->
        return@map when (result) {
            is SettingsResult.Content -> contentToLoaded(result)
            is SettingsResult.Loading ->
                SettingsUiState.Loading(contentToLoaded(result.content), result.overallProgress, result.stepProgress)
        }
    }
}
