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
import android.text.format.DateFormat
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter

fun localDateFromEpochMilli(millis: Long): LocalDate {
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
}

fun LocalDate.toFullDateString(context: Context): String = format(getFullDateFormat(context))

private fun getFullDateFormat(context: Context): DateTimeFormatter {
    return when (isDayBeforeMonth(context)) {
        true -> DateTimeFormatter.ofPattern("dd/MM/yyyy")
        false -> DateTimeFormatter.ofPattern("MM/dd/yyyy")
    }
}

private fun isDayBeforeMonth(context: Context): Boolean {
    DateFormat.getDateFormatOrder(context).forEach { char ->
        if (char.toLowerCase() == 'd')
            return true
        else if (char.toLowerCase() == 'm')
            return false
    }
    return false
}
