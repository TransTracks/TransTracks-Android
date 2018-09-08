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

import android.os.Build
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

/**
 * We only have access to the date created after API 26, otherwise we will need to just look at last modified
 */
fun File.dateCreated(): Long = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
        val attr = Files.readAttributes(toPath(), BasicFileAttributes::class.java)
        attr.creationTime().toMillis()
    }

    else -> lastModified()
}
