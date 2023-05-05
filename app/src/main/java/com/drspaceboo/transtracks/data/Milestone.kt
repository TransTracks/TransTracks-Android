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
import com.google.gson.stream.JsonReader
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.UUID

class Milestone : RealmObject {
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

        fun fromJson(jsonReader: JsonReader): Milestone? {
            return try {
                Milestone().apply {
                    while (jsonReader.hasNext()) {
                        when (jsonReader.nextName()) {
                            FIELD_ID -> {
                                id = try {
                                    UUID.fromString(jsonReader.nextString())
                                } catch (e: IllegalArgumentException) {
                                    e.printStackTrace()
                                    UUID.randomUUID()
                                }.toString()
                            }

                            FIELD_EPOCH_DAY -> {
                                epochDay = jsonReader.nextLong()
                            }

                            FIELD_TIMESTAMP -> {
                                timestamp = jsonReader.nextLong()
                            }

                            FIELD_TITLE -> {
                                title = jsonReader.nextString()
                            }

                            FIELD_DESCRIPTION -> {
                                description = jsonReader.nextString()
                            }

                            else -> jsonReader.skipValue()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
