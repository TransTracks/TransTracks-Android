package com.drspaceboo.transtracks.ui.widget

/*
 * Copyright (C) 2014 skyfish.jy@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import android.database.Cursor
import android.database.DataSetObserver
import androidx.recyclerview.widget.RecyclerView

/**
 * Created by skyfishjy on 10/31/14.
 */

abstract class CursorRecyclerViewAdapter<VH : RecyclerView.ViewHolder>(cursor: Cursor?) : RecyclerView.Adapter<VH>() {

    var cursor: Cursor? = null
        private set

    private var mDataValid: Boolean = false

    private var mRowIdColumn: Int = 0

    private val mDataSetObserver: DataSetObserver?

    init {
        this.cursor = cursor
        mDataValid = cursor != null
        mRowIdColumn = if (mDataValid) this.cursor!!.getColumnIndex("_id") else -1
        mDataSetObserver = NotifyingDataSetObserver()
        if (this.cursor != null) {
            this.cursor!!.registerDataSetObserver(mDataSetObserver)
        }
    }

    override fun getItemCount(): Int {
        return if (mDataValid && cursor != null) {
            cursor!!.count
        } else 0
    }

    override fun getItemId(position: Int): Long {
        return if (mDataValid && cursor != null && cursor!!.moveToPosition(position)) {
            cursor!!.getLong(mRowIdColumn)
        } else 0
    }

    override fun setHasStableIds(hasStableIds: Boolean) {
        super.setHasStableIds(true)
    }

    abstract fun onBindViewHolder(viewHolder: VH, cursor: Cursor)

    override fun onBindViewHolder(viewHolder: VH, position: Int) {
        if (!mDataValid) {
            throw IllegalStateException("this should only be called when the cursor is valid")
        }

        val localCursor = cursor

        if (!localCursor!!.moveToPosition(position)) {
            throw IllegalStateException("couldn't move cursor to position $position")
        }
        onBindViewHolder(viewHolder, localCursor)
    }

    /**
     * Change the underlying cursor to a new cursor. If there is an existing cursor it will be
     * closed.
     */
    fun changeCursor(cursor: Cursor) {
        val old = swapCursor(cursor)
        old?.close()
    }

    /**
     * Swap in a new Cursor, returning the old Cursor.  Unlike
     * [.changeCursor], the returned old Cursor is *not*
     * closed.
     */
    fun swapCursor(newCursor: Cursor): Cursor? {
        if (newCursor === cursor) {
            return null
        }
        val oldCursor = cursor
        if (oldCursor != null && mDataSetObserver != null) {
            oldCursor.unregisterDataSetObserver(mDataSetObserver)
        }
        cursor = newCursor
        if (cursor != null) {
            if (mDataSetObserver != null) {
                cursor!!.registerDataSetObserver(mDataSetObserver)
            }
            mRowIdColumn = newCursor.getColumnIndexOrThrow("_id")
            mDataValid = true
            notifyDataSetChanged()
        } else {
            mRowIdColumn = -1
            mDataValid = false
            notifyDataSetChanged()
            //There is no notifyDataSetInvalidated() method in RecyclerView.Adapter
        }
        return oldCursor
    }

    private inner class NotifyingDataSetObserver : DataSetObserver() {
        override fun onChanged() {
            super.onChanged()
            mDataValid = true
            notifyDataSetChanged()
        }

        override fun onInvalidated() {
            super.onInvalidated()
            mDataValid = false
            notifyDataSetChanged()
            //There is no notifyDataSetInvalidated() method in RecyclerView.Adapter
        }
    }
}
