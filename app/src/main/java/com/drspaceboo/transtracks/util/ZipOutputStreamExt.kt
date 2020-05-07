/*
 * Copyright © 2020 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.util

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val BUFFER_SIZE = 8192;

@Throws(FileNotFoundException::class, IOException::class)
fun ZipOutputStream.writeFile(file: File, prefix: String = "") {
    FileInputStream(file).use { fileInputStream ->
        BufferedInputStream(fileInputStream, BUFFER_SIZE).use { bufferedInputStream ->
            putNextEntry(ZipEntry("$prefix${file.name}"))
            val data = ByteArray(BUFFER_SIZE)
            var count: Int
            while (bufferedInputStream.read(data, 0, BUFFER_SIZE).also { count = it } != -1) {
                write(data, 0, count)
            }
        }
    }
}
