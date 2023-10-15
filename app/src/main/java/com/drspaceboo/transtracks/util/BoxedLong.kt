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

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

/**
 * This Boxed long class is used to allow nullable long values as arguments for navigation.
 * @param value The non-null value
 */
@Keep
@Parcelize
data class BoxedLong(val value: Long) : Parcelable {
    operator fun invoke(): Long = value
}

fun Long.boxed(): BoxedLong = BoxedLong(this)