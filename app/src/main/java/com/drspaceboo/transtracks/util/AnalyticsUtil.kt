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

import android.os.Bundle
import com.drspaceboo.transtracks.TransTracksApp
import com.drspaceboo.transtracks.util.settings.LockType
import com.google.firebase.analytics.FirebaseAnalytics

sealed class Event {
    object AddEditMilestoneControllerShown : Event()

    object AssignPhotoControllerShown : Event()

    object EditPhotoControllerShown : Event()

    data class GalleryControllerShown(val isFace: Boolean) : Event()

    object HomeControllerShown : Event()

    data class LockControllerShown(val type: LockType) : Event()

    object MilestonesControllerShown : Event()

    object SelectPhotoControllerShown : Event()

    object SelectAlbumControllerShown : Event()

    object SingleAlbumControllerShown : Event()

    object SettingsControllerShown : Event()

    object SinglePhotoControllerShown : Event()
}

object AnalyticsUtil {

    fun logEvent(event: Event) {
        FirebaseAnalytics
            .getInstance(TransTracksApp.instance)
            .logEvent(getEventName(event), getEventBundle(event))
    }

    private fun getEventName(event: Event): String = when (event) {
        Event.AddEditMilestoneControllerShown -> "AddEditMilestoneControllerShown"
        Event.AssignPhotoControllerShown -> "AssignPhotoControllerShown"
        Event.EditPhotoControllerShown -> "EditPhotoControllerShown"
        is Event.GalleryControllerShown -> "GalleryControllerShown"
        Event.HomeControllerShown -> "HomeControllerShown"
        is Event.LockControllerShown -> "LockControllerShown"
        Event.MilestonesControllerShown -> "MilestonesControllerShown"
        Event.SelectPhotoControllerShown -> "SelectPhotoControllerShown"
        Event.SelectAlbumControllerShown -> "SelectAlbumControllerShown"
        Event.SingleAlbumControllerShown -> "SingleAlbumControllerShown"
        Event.SettingsControllerShown -> "SettingsControllerShown"
        Event.SinglePhotoControllerShown -> "SinglePhotoControllerShown"
    }

    private fun getEventBundle(event: Event): Bundle? = when (event) {
        is Event.GalleryControllerShown -> Bundle().apply {
            val type = when (event.isFace) {
                true -> "Face"
                false -> "Body"
            }
            putString(TYPE, type)
        }

        is Event.LockControllerShown -> Bundle().apply {
            val type = when (event.type) {
                LockType.off -> "Off"
                LockType.normal -> "Normal"
                LockType.trains -> "Trains"
            }
            putString(TYPE, type)
        }

        else -> null
    }

    private const val TYPE = "type"
}
