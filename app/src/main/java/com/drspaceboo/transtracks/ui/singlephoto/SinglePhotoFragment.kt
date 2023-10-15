/*
 * Copyright © 2018-2023 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.singlephoto

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.ui.singlephoto.SinglePhotoFragmentArgs
import com.drspaceboo.transtracks.ui.singlephoto.SinglePhotoFragmentDirections
import com.drspaceboo.transtracks.util.AnalyticsUtil
import com.drspaceboo.transtracks.util.Event
import com.drspaceboo.transtracks.util.ShareUtil
import com.drspaceboo.transtracks.util.dismissIfShowing
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.ofType
import com.drspaceboo.transtracks.util.openDefault
import com.drspaceboo.transtracks.util.plusAssign
import com.drspaceboo.transtracks.util.toFullDateString
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.realm.kotlin.Realm
import java.io.File
import java.time.LocalDate

class SinglePhotoFragment : Fragment(R.layout.single_photo) {
    val args: SinglePhotoFragmentArgs by navArgs()

    private val viewDisposables: CompositeDisposable = CompositeDisposable()

    private var confirmDeleteDialog: AlertDialog? = null

    override fun onStart() {
        super.onStart()

        val view = view as? SinglePhotoView ?: throw AssertionError("View must be SinglePhotoView")

        AnalyticsUtil.logEvent(Event.SinglePhotoControllerShown)

        val loadRealm = Realm.openDefault()
        val photo = loadRealm.query(Photo::class, "${Photo.FIELD_ID} == '${args.photoId}'")
            .first()
            .find()

        if (photo == null) {
            AlertDialog.Builder(view.context)
                .setTitle(R.string.error_loading_photo)
                .setMessage(R.string.error_loading_photo_message)
                .setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int ->
                    dialog.dismiss()
                    findNavController().popBackStack()
                }
                .setCancelable(false)
                .show()
            return
        } else {
            val details = view.getString(
                R.string.photo_detail_replacement,
                LocalDate.ofEpochDay(photo.epochDay).toFullDateString(view.context),
                Photo.getTypeName(photo.type, view.context)
            )

            view.display(SinglePhotoUiState.Loaded(photo.filePath, details, photo.id))
        }
        loadRealm.close()

        val sharedEvents = view.events.share()

        viewDisposables += sharedEvents.ofType<SinglePhotoUiEvent.Back>()
            .subscribe { requireActivity().onBackPressed() }

        viewDisposables += sharedEvents.ofType<SinglePhotoUiEvent.Edit>()
            .subscribe { event ->
                findNavController().navigate(
                    SinglePhotoFragmentDirections.actionEditPhoto(photoId = event.photoId)
                )
            }

        viewDisposables += sharedEvents.ofType<SinglePhotoUiEvent.Share>()
            .subscribe { event ->
                val realm = Realm.openDefault()
                val filePath: String? = realm
                    .query(clazz = Photo::class, "${Photo.FIELD_ID} == '${event.photoId}'")
                    .first()
                    .find()
                    ?.filePath
                realm.close()

                if (filePath == null) {
                    Snackbar.make(view, R.string.error_sharing_photo, Snackbar.LENGTH_LONG)
                        .show()
                    return@subscribe
                }

                ShareUtil.sharePhoto(File(filePath), view.context)
            }

        viewDisposables += sharedEvents.ofType<SinglePhotoUiEvent.Delete>()
            .subscribe { event ->
                confirmDeleteDialog?.dismissIfShowing()

                confirmDeleteDialog = AlertDialog.Builder(view.context)
                    .setTitle(R.string.are_you_sure)
                    .setMessage(R.string.confirm_delete_photo)
                    .setPositiveButton(R.string.delete) { dialog: DialogInterface, _: Int ->
                        var success = false

                        val realm = Realm.openDefault()

                        val photoToDelete =
                            realm.query(Photo::class, "${Photo.FIELD_ID} == '${event.photoId}'")
                                .first()
                                .find()

                        if (photoToDelete == null) {
                            success = false
                            realm.close()
                            return@setPositiveButton
                        }

                        val image = File(photoToDelete.filePath)

                        if (image.exists()) {
                            image.delete()
                        }

                        realm.writeBlocking {
                            findLatest(photoToDelete)?.let { delete(it) }
                            success = true
                        }
                        realm.close()

                        dialog.dismiss()
                        if (success) {
                            findNavController().popBackStack()
                        } else {
                            Snackbar.make(
                                view, R.string.error_deleting_photo,
                                Snackbar.LENGTH_LONG
                            )
                                .show()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setOnDismissListener { confirmDeleteDialog = null }
                    .create()

                confirmDeleteDialog!!.show()
            }
    }

    override fun onDetach() {
        viewDisposables.clear()
        super.onDetach()
    }
}
