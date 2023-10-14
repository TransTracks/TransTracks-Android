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

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.lang.ref.WeakReference

class AdapterSpanSizeLookup(
    recyclerView: RecyclerView, private val defaultSpan: Int = 1
) : GridLayoutManager.SpanSizeLookup() {
    private val recyclerViewRef: WeakReference<RecyclerView> = WeakReference(recyclerView)

    override fun getSpanSize(position: Int): Int {
        val recyclerView = recyclerViewRef.get() ?: return defaultSpan
        val adapter = recyclerView.adapter ?: return defaultSpan

        return (adapter as Interface).getSpanSize(position)
    }

    interface Interface {
        fun getSpanSize(position: Int): Int
    }
}
