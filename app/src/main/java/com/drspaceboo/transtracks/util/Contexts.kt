/*
 * Copyright © 2023 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.FileOutputStream
import java.io.InputStream

@SuppressLint("Recycle") //Closing quietly which doesn't get picked up by lint
fun Context.copyPhotoToTempFiles(imageUri: Uri): String? {
    var inputStream: InputStream? = null
    val selectedImage: Bitmap
    try {
        inputStream = contentResolver.openInputStream(imageUri)
        selectedImage = BitmapFactory.decodeStream(inputStream)
    } finally {
        inputStream?.quietlyClose()
    }

    val imageFile = FileUtil.getTempImageFile()
    val outStream = FileOutputStream(imageFile)
    try {
        selectedImage.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
    } finally {
        outStream.quietlyClose()
    }

    var exifInputStream: InputStream? = null
    try {
        exifInputStream = contentResolver.openInputStream(imageUri)
        val inExif = ExifInterface(exifInputStream!!)
        ExifInterface(imageFile.absolutePath).copyFrom(inExif)
    } finally {
        exifInputStream?.quietlyClose()
    }

    return if (imageFile.exists() && imageFile.length() > 0) {
        imageFile.absolutePath
    } else {
        null
    }
}