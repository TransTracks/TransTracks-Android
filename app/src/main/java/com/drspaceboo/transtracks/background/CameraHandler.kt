/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.background

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import com.drspaceboo.transtracks.data.TransTracksFileProvider
import com.drspaceboo.transtracks.util.FileUtil
import com.jakewharton.rxrelay2.BehaviorRelay
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import java.io.File

class CameraHandler : Fragment() {
    private var currentFile: File? = null

    init {
        retainInstance = true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_TAKE_PHOTO && resultCode == Activity.RESULT_OK && currentFile != null) {

            val imageFile = currentFile!!
            if (imageFile.exists() && imageFile.length() > 0) {
                photoTakenRelay.accept(imageFile.absolutePath)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissionGranted()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION && grantResults.isNotEmpty()) {
            val permissionGranted = (grantResults[0] == PackageManager.PERMISSION_GRANTED)
            cameraPermissionRelay.accept(permissionGranted)

            if (!permissionGranted) {
                permissionBlockedRelay.accept(
                        ActivityCompat.shouldShowRequestPermissionRationale(activity!!, Manifest.permission.CAMERA))
            }
        }
    }

    private fun checkPermissionGranted(): Boolean {
        val cameraPermissionStatus = ContextCompat.checkSelfPermission(context!!, Manifest.permission.CAMERA)

        val permissionGranted = cameraPermissionStatus == PackageManager.PERMISSION_GRANTED
        cameraPermissionRelay.accept(permissionGranted)

        return permissionGranted
    }

    private fun makeRequestsIfNeeded(): Boolean {
        if (!checkPermissionGranted()) {
            request()
            return true
        }

        return false
    }

    private fun request() {
        if (cameraPermissionEnabled.blockingLatest().first()) {
            throw IllegalStateException("CameraHandler.request() called when Camera permission already granted")
        }

        requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_CAMERA_PERMISSION)
    }

    private fun takePhoto() {
        val localContext = context!!
        currentFile = FileUtil.getTempImageFile()
        val uri = FileProvider.getUriForFile(localContext, TransTracksFileProvider::class.java.name, currentFile!!)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        startActivityForResult(intent, REQUEST_CODE_TAKE_PHOTO)
    }

    companion object {
        private const val TAG: String = "CameraHandler"
        private const val REQUEST_CODE_CAMERA_PERMISSION = 578
        private const val REQUEST_CODE_TAKE_PHOTO = 579

        private val cameraPermissionRelay = BehaviorRelay.createDefault(false)
        val cameraPermissionEnabled: Observable<Boolean> = cameraPermissionRelay.distinctUntilChanged()

        private val permissionBlockedRelay = PublishRelay.create<Boolean>()
        val cameraPermissionBlocked: Observable<Boolean> = permissionBlockedRelay

        private val photoTakenRelay = PublishRelay.create<String>()
        val photoTaken: Observable<String> = photoTakenRelay

        fun install(activity: AppCompatActivity) {
            val handler = activity.supportFragmentManager.findFragmentByTag(TAG)

            if (handler == null) {
                val fragmentManager = activity.supportFragmentManager
                fragmentManager.beginTransaction().add(CameraHandler(), TAG).commit()
            }
        }

        fun requestIfNeeded(activity: AppCompatActivity): Boolean = from(activity).makeRequestsIfNeeded()

        fun takePhoto(activity: AppCompatActivity) = from(activity).takePhoto()

        fun from(activity: AppCompatActivity): CameraHandler {
            try {
                return activity.supportFragmentManager.findFragmentByTag(TAG) as CameraHandler
            } catch (e: Exception) {
                throw IllegalStateException("install() not called before from()!", e)
            }
        }
    }
}
