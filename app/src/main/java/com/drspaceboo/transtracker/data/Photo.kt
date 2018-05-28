/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracker.data

import android.support.annotation.IntDef
import io.realm.RealmObject

class Photo : RealmObject() {
    var timestamp: Long = 0

    var filename: String = ""

    @Type
    var type: Int = TYPE_FACE

    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    @IntDef(TYPE_FACE, TYPE_BODY)
    annotation class Type

    companion object {
        const val TYPE_FACE = 0
        const val TYPE_BODY = 1
    }
}
