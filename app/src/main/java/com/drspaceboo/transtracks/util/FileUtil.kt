/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.util

import com.drspaceboo.transtracks.TransTracksApp
import org.threeten.bp.LocalDate
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtil {
    private const val TEMP_FOLDER = "temp/"

    fun getNewImageFile(photoDate: LocalDate): File {
        val photoDateString = photoDate.toFileDateFormat()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filesDir = TransTracksApp.instance.filesDir
        val storageDir = File(filesDir, "photos/")
        storageDir.mkdirs()

        val file = File(storageDir, "photo_${photoDateString}_imported_$timeStamp.jpg")
        file.createNewFile()
        return file
    }

    fun clearTempFolder() {
        getTempFolder().deleteRecursively()
    }

    private fun getTempFolder(): File {
        val tempFolder = File(TransTracksApp.instance.filesDir, TEMP_FOLDER)
        tempFolder.mkdirs()

        return tempFolder
    }

    fun getTempImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
        val file = File(getTempFolder(), "$timeStamp.jpg")
        file.createNewFile()
        return file
    }
}
