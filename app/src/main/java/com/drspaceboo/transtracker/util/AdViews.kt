/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracker.util

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.support.annotation.NonNull
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView

private const val PREFS_AD_SETTINGS: String = "prefsAdSettings"
//Google recommends no reloading ads quicker than this https://support.google.com/admob/answer/2936217?hl=en-GB&ref_topic=2745287
private const val MIN_SECONDS_BETWEEN_AD_LOADS: Long = 60

fun AdView.safeLoadAd() {
    //TODO Need to see if there is a way to tell if there in no ad loaded in case the view gets recreated
    if (shouldLoadAd(context, getIdName())) {
        loadAd(AdRequest.Builder().build())
        recordAdLoadTime(context, getIdName())
    }
}

private fun recordAdLoadTime(@NonNull context: Context, @NonNull key: String) {
    context.getSharedPreferences(PREFS_AD_SETTINGS, MODE_PRIVATE).edit().putLong(key, currentTimeSeconds()).apply()
}

private fun shouldLoadAd(@NonNull context: Context, @NonNull key: String): Boolean {
    val sharedPrefs = context.getSharedPreferences(PREFS_AD_SETTINGS, MODE_PRIVATE)
    val timeSinceLastAdLoaded = (currentTimeSeconds() - sharedPrefs.getLong(key, 0))
    return timeSinceLastAdLoaded >= MIN_SECONDS_BETWEEN_AD_LOADS
}
