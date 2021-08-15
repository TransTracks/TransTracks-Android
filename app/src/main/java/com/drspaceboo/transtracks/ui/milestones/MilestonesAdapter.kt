/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.milestones

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.data.Milestone
import com.drspaceboo.transtracks.util.setVisibleOrGone
import com.drspaceboo.transtracks.util.toFullDateString
import com.jakewharton.rxrelay2.PublishRelay
import io.realm.Realm
import io.realm.Sort
import kotterknife.bindView
import java.time.LocalDate
import java.lang.ref.WeakReference

class MilestonesAdapter(eventRelay: PublishRelay<MilestonesUiEvent>,
                        private val postInitialLoad: (adapter: MilestonesAdapter) -> Unit,
                        private val postLoad: (adapter: MilestonesAdapter) -> Unit) : RecyclerView.Adapter<MilestonesAdapter.BaseViewHolder>() {
    private val realm = Realm.getDefaultInstance()
    private val result = realm.where(Milestone::class.java)
            .sort(Milestone.FIELD_EPOCH_DAY, Sort.DESCENDING).findAllAsync().apply {
                addChangeListener { _ ->
                    generateItems()
                    if (initialLoad) {
                        initialLoad = false
                        postInitialLoad.invoke(this@MilestonesAdapter)
                    }

                    postLoad.invoke(this@MilestonesAdapter)
                }
            }

    private var items = ArrayList<MilestonesAdapterItem>()
    private var initialLoad = true

    private val eventRelayRef = WeakReference(eventRelay)

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when {
        items[position].epochDay != null -> TYPE_TITLE
        items[position].milestone != null -> TYPE_MILESTONE
        else -> throw IllegalArgumentException("Unhandled item type")
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        when (holder) {
            is DayTitleViewHolder -> holder.bind(items[position])
            is MilestoneViewHolder -> holder.bind(items[position])
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)

        return when (viewType) {
            TYPE_TITLE -> DayTitleViewHolder(view)
            TYPE_MILESTONE -> MilestoneViewHolder(view, eventRelayRef.get())
            else -> throw IllegalArgumentException("Unhandled item type")
        }
    }

    fun getPositionOfDay(day: Long): Int {
        items.forEachIndexed { index, item ->
            if (item.epochDay != null && item.epochDay == day) {
                return index
            }
        }

        return -1
    }

    private fun generateItems() {
        val newItems = ArrayList<MilestonesAdapterItem>()

        result.forEach { milestone ->
            var indexOfTitle = newItems.indexOfFirst { item -> milestone.epochDay == item.epochDay }

            if (indexOfTitle == -1) {
                newItems.add(MilestonesAdapterItem(milestone.epochDay))
                indexOfTitle = newItems.lastIndex
            }

            var indexToInsertAt: Int = newItems.size

            for (i in (indexOfTitle + 1) until newItems.size) {
                //Find the next title, and we will insert at that index
                if (newItems[i].epochDay != null) {
                    indexToInsertAt = i
                }
            }

            newItems.add(indexToInsertAt, MilestonesAdapterItem(milestone))
        }

        val results = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = items[oldItemPosition]
                val new = newItems[newItemPosition]

                if (old.epochDay != null) {
                    return old.epochDay == new.epochDay
                }

                if (old.milestone == null || !old.milestone.isValid || new.milestone == null
                        || !new.milestone.isValid) {
                    return false
                }

                return old.milestone.id == new.milestone.id
            }

            override fun getOldListSize(): Int = items.size

            override fun getNewListSize(): Int = newItems.size

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = items[oldItemPosition]
                val new = newItems[newItemPosition]

                if (old.epochDay != null) {
                    return old.epochDay == new.epochDay
                }

                if (old.milestone == null || new.milestone == null) {
                    return false
                }

                return old.milestone == new.milestone && old.milestone.id == new.milestone.id
                        && old.milestone.epochDay == new.milestone.epochDay
                        && old.milestone.timestamp == new.milestone.timestamp
                        && old.milestone.title == new.milestone.title
                        && old.milestone.description == new.milestone.description
            }
        }, true)

        items = newItems
        results.dispatchUpdatesTo(this)
    }

    class MilestonesAdapterItem {
        val epochDay: Long?

        val milestone: Milestone?

        constructor(epochDay: Long) {
            this.epochDay = epochDay

            milestone = null
        }

        constructor(milestone: Milestone) {
            this.milestone = milestone

            epochDay = null
        }
    }

    abstract class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class DayTitleViewHolder(itemView: View) : BaseViewHolder(itemView) {
        private val title: TextView by bindView(R.id.milestones_adapter_item_title)

        fun bind(item: MilestonesAdapterItem) {
            title.text = LocalDate.ofEpochDay(item.epochDay!!).toFullDateString(itemView.context)
        }
    }

    class MilestoneViewHolder(itemView: View, eventRelay: PublishRelay<MilestonesUiEvent>?) : BaseViewHolder(itemView) {
        private val title: TextView by bindView(R.id.milestones_adapter_item_title)
        private val descriptionIcon: View by bindView(R.id.milestones_adapter_item_description_icon)
        private val description: TextView by bindView(R.id.milestones_adapter_item_description)

        private val eventRelayRef = WeakReference(eventRelay)
        private var milestoneId: String = ""

        init {
            itemView.setOnClickListener {
                eventRelayRef.get()?.accept(MilestonesUiEvent.EditMilestone(milestoneId))
            }
        }

        fun bind(item: MilestonesAdapterItem) {
            milestoneId = item.milestone!!.id

            title.text = item.milestone.title

            description.text = item.milestone.description
            val showDescription = item.milestone.description.isNotEmpty()
            descriptionIcon.setVisibleOrGone(showDescription)
            description.setVisibleOrGone(showDescription)
        }
    }

    companion object {
        private const val TYPE_TITLE = R.layout.milestones_adapter_title_item
        private const val TYPE_MILESTONE = R.layout.milestones_adapter_item
    }
}
