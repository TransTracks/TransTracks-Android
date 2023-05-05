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
import androidx.annotation.IntDef
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.util.FileUtil.getImageFile
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.io.File
import java.util.UUID

class Photo : RealmObject {
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

    fun toJson(): JsonObject? {
        return try {
            JsonObject().apply {
                addProperty(FIELD_ID, id)
                addProperty(FIELD_EPOCH_DAY, epochDay)
                addProperty(FIELD_TIMESTAMP, timestamp)
                addProperty(FIELD_FILE_NAME, File(filePath).name)
                addProperty(FIELD_TYPE, type)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        const val TYPE_FACE = 0
        const val TYPE_BODY = 1

        const val FIELD_ID = "id"
        const val FIELD_EPOCH_DAY = "epochDay"
        const val FIELD_TIMESTAMP = "timestamp"
        const val FIELD_TYPE = "type"

        const val FIELD_FILE_NAME = "fileName"

        fun getTypeName(@Type type: Int, context: Context) = when (type) {
            TYPE_FACE -> context.getString(R.string.face)
            TYPE_BODY -> context.getString(R.string.body)
            else -> throw IllegalArgumentException("Unhandled Type '$type'")
        }

        fun fromJson(jsonReader: JsonReader): Photo? {
            return try {
                Photo().apply {
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

                            FIELD_FILE_NAME -> {
                                filePath = getImageFile(jsonReader.nextString()).absolutePath
                            }

                            FIELD_TYPE -> {
                                type = jsonReader.nextInt()
                                if (type !in arrayOf(TYPE_FACE, TYPE_BODY)) {
                                    type = TYPE_FACE
                                }
                            }

                            else -> jsonReader.skipValue()
                        }
                    }

                    val file = File(filePath)
                    if (!file.exists() || file.length() <= 0) {
                        throw IllegalStateException("Photo file doesn't exist or has no content")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
