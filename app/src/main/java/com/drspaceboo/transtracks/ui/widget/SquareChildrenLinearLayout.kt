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

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout


class SquareChildrenLinearLayout(
    context: Context, attributeSet: AttributeSet
    ) : LinearLayout(context, attributeSet) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var maxDimension = measuredWidth

        for (i in 0 until childCount) {
            val layoutParams = getChildAt(i).layoutParams as LinearLayout.LayoutParams
            maxDimension -= when (orientation) {
                LinearLayout.HORIZONTAL -> layoutParams.marginStart + layoutParams.marginEnd
                LinearLayout.VERTICAL -> layoutParams.topMargin + layoutParams.bottomMargin
                else -> throw IllegalArgumentException("Unhanded Orientation")
            }
        }

        maxDimension /= childCount

        val comparisonDimension = when (orientation) {
            LinearLayout.HORIZONTAL -> measuredHeight
            LinearLayout.VERTICAL -> measuredWidth
            else -> throw IllegalArgumentException("Unhanded Orientation")
        }

        val size = Math.min(maxDimension, comparisonDimension)
        val measureSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val layoutParams = child.layoutParams as LinearLayout.LayoutParams
            layoutParams.width = measureSpec
            layoutParams.height = measureSpec
            child.layoutParams = layoutParams
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
