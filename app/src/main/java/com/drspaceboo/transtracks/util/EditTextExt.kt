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

import android.widget.EditText

fun EditText.setTextRetainingSelection(text: CharSequence?) {
    if (text == getText().toString()) {
        return
    }

    var selectionStart = selectionStart
    var selectionEnd = selectionEnd

    setText(text)

    if (selectionStart != -1) {
        //Making sure the selection isn't out of bounds
        if (selectionStart > length()) {
            selectionStart = length()
        }
        if (selectionEnd > length()) {
            selectionEnd = length()
        } else if (selectionEnd < selectionStart) {
            selectionEnd = -1
        }

        if (selectionEnd == -1) {
            setSelection(selectionStart)
        } else {
            setSelection(selectionStart, selectionEnd)
        }
    }
}
