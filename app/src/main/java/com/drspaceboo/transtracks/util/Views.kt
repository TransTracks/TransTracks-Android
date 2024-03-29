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

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.NonNull
import androidx.annotation.StringRes

@ColorInt
fun View.getColor(@ColorRes colorRes: Int): Int = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> context.getColor(colorRes)

    else -> {
        @Suppress("DEPRECATION") //We are correctly handling this deprecation
        resources.getColor(colorRes)
    }
}

fun View.getIdName() = resources.getResourceEntryName(id)

fun View.getString(@StringRes resId: Int) = context.getString(resId)

fun View.getString(@StringRes resId: Int, @NonNull vararg formatArgs: Any) =
    context.getString(resId, *formatArgs)

fun View.gone() {
    visibility = View.GONE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.visible() {
    visibility = View.VISIBLE
}

@Suppress("LiftReturnOrAssignment") //Lifting it out wouldn't look as clean
fun View.setVisibleOrGone(show: Boolean) = when (show) {
    true -> visibility = View.VISIBLE
    false -> visibility = View.GONE
}

@Suppress("LiftReturnOrAssignment") //Lifting it out wouldn't look as clean
fun View.setVisibleOrInvisible(show: Boolean) = when (show) {
    true -> visibility = View.VISIBLE
    false -> visibility = View.INVISIBLE
}

fun View.showKeyboard() {
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
        .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}

fun View.hideKeyboard() {
    (context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager)
        .hideSoftInputFromWindow(windowToken, 0)
}

fun setVisible(vararg views: View) = views.forEach { it.visibility = View.VISIBLE }

fun setGone(vararg views: View) = views.forEach { it.visibility = View.GONE }
