/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracker.background

import android.Manifest
import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import com.jakewharton.rxrelay2.BehaviorRelay
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable

class StoragePermissionHandler : Fragment() {
    init {
        retainInstance = true
    }

    override fun onResume() {
        super.onResume()
        checkPermissionGranted()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION && grantResults.isNotEmpty()) {
            val permissionGranted = (grantResults[0] == PackageManager.PERMISSION_GRANTED)
            storagePermissionRelay.accept(permissionGranted)

            if (!permissionGranted) {
                permissionBlockedRelay.accept(
                        ActivityCompat.shouldShowRequestPermissionRationale(activity!!,
                                                                            Manifest.permission.READ_EXTERNAL_STORAGE))
            }
        }
    }

    private fun checkPermissionGranted(): Boolean {
        val readPermissionStatus = ContextCompat.checkSelfPermission(
                context!!, Manifest.permission.READ_EXTERNAL_STORAGE)
        val writePermissionStatus = ContextCompat.checkSelfPermission(
                context!!, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        val permissionGranted = readPermissionStatus == PackageManager.PERMISSION_GRANTED
                && writePermissionStatus == PackageManager.PERMISSION_GRANTED

        storagePermissionRelay.accept(permissionGranted)

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
        if (storagePermissionEnabled.blockingLatest().first()) throw IllegalStateException(
                "StoragePermissionHandler.request() called when Storage permission already granted")
        val storagePermissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                                         Manifest.permission.WRITE_EXTERNAL_STORAGE)
        requestPermissions(storagePermissions, REQUEST_CODE_STORAGE_PERMISSION)
    }

    companion object {
        private const val TAG: String = "StoragePermissionHandler"
        private const val REQUEST_CODE_STORAGE_PERMISSION = 568

        private val storagePermissionRelay = BehaviorRelay.createDefault(false)
        val storagePermissionEnabled: Observable<Boolean> = storagePermissionRelay.distinctUntilChanged()

        private val permissionBlockedRelay = PublishRelay.create<Boolean>()
        val storagePermissionBlocked: Observable<Boolean> = permissionBlockedRelay

        fun install(activity: AppCompatActivity) {
            val handler = activity.supportFragmentManager.findFragmentByTag(TAG)

            if (handler == null) {
                val fragmentManager = activity.supportFragmentManager
                fragmentManager.beginTransaction().add(StoragePermissionHandler(), TAG).commit()
            }
        }

        fun requestIfNeeded(activity: AppCompatActivity): Boolean =
                from(activity).makeRequestsIfNeeded()

        fun from(activity: AppCompatActivity): StoragePermissionHandler {
            try {
                return activity.supportFragmentManager.findFragmentByTag(TAG) as StoragePermissionHandler
            } catch (e: Exception) {
                throw IllegalStateException("install() not called before from()!", e)
            }
        }
    }
}
