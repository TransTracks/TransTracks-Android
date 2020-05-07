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

import com.google.gson.JsonObject
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.UUID

open class Milestone : RealmObject() {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()

    var epochDay: Long = 0
    var timestamp: Long = 0

    var title: String = ""
    var description: String = ""

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty(FIELD_ID, id)
        addProperty(FIELD_EPOCH_DAY, epochDay)
        addProperty(FIELD_TIMESTAMP, timestamp)
        addProperty(FIELD_TITLE, title)
        addProperty(FIELD_DESCRIPTION, description)
    }

    companion object {
        const val FIELD_ID = "id"
        const val FIELD_EPOCH_DAY = "epochDay"
        const val FIELD_TIMESTAMP = "timestamp"
        const val FIELD_TITLE = "title"
        const val FIELD_DESCRIPTION = "description"

        fun fromJson(json: JsonObject): Milestone? {
            return try {
                Milestone().apply {
                    id = json[FIELD_ID].asString
                    epochDay = json[FIELD_EPOCH_DAY].asLong
                    timestamp = json[FIELD_TIMESTAMP].asLong
                    title = json[FIELD_TITLE].asString
                    description = json[FIELD_DESCRIPTION].asString
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
