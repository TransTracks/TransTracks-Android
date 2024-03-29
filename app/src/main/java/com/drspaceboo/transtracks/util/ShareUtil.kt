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

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.data.TransTracksFileProvider
import java.io.File

object ShareUtil {
    fun sharePhoto(imageToShare: File, context: Context) {
        val share = Intent(Intent.ACTION_SEND)
        share.type = JPEG_MIME_TYPE

        val uri = FileProvider.getUriForFile(
            context, TransTracksFileProvider::class.java.name, imageToShare
        )
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        share.putExtra(Intent.EXTRA_STREAM, uri)

        context.startActivity(
            Intent.createChooser(share, context.getString(R.string.share_image_using))
        )
    }

    fun sharePhotos(imagesToShare: List<File>, context: Context) {
        val share = Intent(Intent.ACTION_SEND_MULTIPLE)
        share.type = JPEG_MIME_TYPE

        val files = ArrayList<Uri>()
        imagesToShare.forEach { file ->
            files.add(
                FileProvider.getUriForFile(context, TransTracksFileProvider::class.java.name, file)
            )
        }

        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files)

        context.startActivity(
            Intent.createChooser(share, context.getString(R.string.share_image_using))
        )
    }

    private const val JPEG_MIME_TYPE: String = "image/jpeg"
}
