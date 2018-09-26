/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.data

import android.content.Context
import android.support.annotation.IntDef
import com.drspaceboo.transtracks.R
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import java.util.UUID

open class Photo : RealmObject() {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()

    var epochDay: Long = 0
    var timestamp: Long = 0

    var filePath: String = ""

    @Photo.Type
    var type: Int = TYPE_FACE

    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    @IntDef(TYPE_FACE, TYPE_BODY)
    annotation class Type

    companion object {
        const val TYPE_FACE = 0
        const val TYPE_BODY = 1

        const val FIELD_ID = "id"
        const val FIELD_EPOCH_DAY = "epochDay"
        const val FIELD_TIMESTAMP = "timestamp"
        const val FIELD_TYPE = "type"

        fun getTypeName(@Type type: Int, context: Context) = when (type) {
            TYPE_FACE -> context.getString(R.string.face)
            TYPE_BODY -> context.getString(R.string.body)
            else -> throw IllegalArgumentException("Unhandled Type '$type'")
        }!!
    }
}
