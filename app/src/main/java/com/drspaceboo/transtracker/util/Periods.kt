/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracker.util

import android.content.Context
import com.drspaceboo.transtracker.R
import com.drspaceboo.transtracker.TransTrackerApp
import org.threeten.bp.Period

fun Period.getDisplayString(): String {
    val context: Context = TransTrackerApp.instance
    val period = normalized()

    val builder = StringBuilder()
    if (period.years != 0) {
        builder.append(context.resources.getQuantityString(R.plurals.years, period.years, period.years))
    }
    if (period.months != 0) {
        if (builder.isNotEmpty()) {
            builder.append(", ")
        }

        builder.append(context.resources.getQuantityString(R.plurals.months, period.months, period.months))
    }
    if (period.days != 0) {
        if (builder.isNotEmpty()) {
            builder.append(", ")
        }

        builder.append(context.resources.getQuantityString(R.plurals.days, period.days, period.days))
    }
    return if (builder.isNotEmpty()) builder.toString() else context.getString(R.string.start_day)
}
