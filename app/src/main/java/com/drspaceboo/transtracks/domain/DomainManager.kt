/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.domain

class DomainManager {
    val addEditMilestoneDomain: AddEditMilestoneDomain by lazy(LazyThreadSafetyMode.NONE) { AddEditMilestoneDomain() }

    val assignPhotosDomain: AssignPhotosDomain by lazy(LazyThreadSafetyMode.NONE) { AssignPhotosDomain() }

    val editPhotoDomain: EditPhotoDomain by lazy(LazyThreadSafetyMode.NONE) { EditPhotoDomain() }

    val homeDomain: HomeDomain by lazy(LazyThreadSafetyMode.NONE) { HomeDomain() }

    val settingsDomain: SettingsDomain by lazy(LazyThreadSafetyMode.NONE) { SettingsDomain() }
}
