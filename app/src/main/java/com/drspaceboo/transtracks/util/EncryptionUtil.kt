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

import android.util.Base64
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

object EncryptionUtil {
    fun encryptAndEncode(data: String, salt: String): String {
        val dataArray = "$salt$data".toByteArray(UTF_8)

        val messageDigest = MessageDigest.getInstance("SHA-512")
        messageDigest.update(dataArray)

        return Base64.encodeToString(dataArray, Base64.NO_WRAP)
    }
}
