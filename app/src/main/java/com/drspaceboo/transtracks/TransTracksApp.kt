/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks

import android.app.Application
import com.drspaceboo.transtracks.domain.DomainManager
import com.drspaceboo.transtracks.util.FileUtil
import com.drspaceboo.transtracks.util.PrefUtil
import com.google.android.gms.ads.MobileAds
import com.jakewharton.threetenabp.AndroidThreeTen
import com.squareup.leakcanary.LeakCanary
import io.realm.Realm

class TransTracksApp : Application() {
    val domainManager = DomainManager()

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return
        }
        LeakCanary.install(this)

        MobileAds.initialize(this, BuildConfig.ADS_APP_ID)

        AndroidThreeTen.init(this)
        Realm.init(this)

        if (!PrefUtil.startDate.isSet) {
            //Make sure that the startDate
            PrefUtil.startDate.set(PrefUtil.startDate.defaultValue())
        }

        FileUtil.clearTempFolder()
    }

    companion object {
        lateinit var instance: TransTracksApp
            private set
    }
}
