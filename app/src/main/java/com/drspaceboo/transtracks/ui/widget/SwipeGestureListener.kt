/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.widget

import android.view.GestureDetector
import android.view.MotionEvent

open class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
    override fun onFling(
        e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float
    ): Boolean {
        val dx = Math.abs(e1.x - e2.x)
        val dy = Math.abs(e1.y - e2.y)
        val absVelocityX = Math.abs(velocityX)
        val absVelocityY = Math.abs(velocityY)

        if ((dx < MIN_DISTANCE && dy < MIN_DISTANCE)
            || (absVelocityX < THRESHOLD && absVelocityY < THRESHOLD)
        ) {
            return false
        }

        return when {
            //Exactly diagonal fling, let's ignore it
            absVelocityX == absVelocityY -> return false

            absVelocityX > absVelocityY -> {
                when {
                    velocityX < 0 -> swipeRight()
                    velocityX > 0 -> swipeLeft()

                    //Shouldn't happen but let's make sure
                    else -> false
                }
            }

            //absVelocityX > absVelocityY
            else -> {
                when {
                    velocityY < 0 -> swipeDown()
                    velocityY > 0 -> swipeUp()

                    //Shouldn't happen but let's make sure
                    else -> false
                }
            }
        }
    }

    open fun swipeDown(): Boolean {
        return false
    }

    open fun swipeLeft(): Boolean {
        return false
    }

    open fun swipeRight(): Boolean {
        return false
    }

    open fun swipeUp(): Boolean {
        return false
    }

    companion object {
        private const val MIN_DISTANCE = 120
        private const val THRESHOLD = 500
    }
}
