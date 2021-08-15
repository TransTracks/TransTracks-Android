/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.ui.widget.AdapterSpanSizeLookup
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.setVisibleOrGone
import com.drspaceboo.transtracks.util.toFullDateString
import com.jakewharton.rxrelay2.PublishRelay
import com.squareup.picasso.Picasso
import io.realm.Realm
import io.realm.Sort
import kotterknife.bindView
import java.time.LocalDate
import java.io.File
import java.lang.ref.WeakReference

class GalleryAdapter(@Photo.Type private val type: Int, eventRelay: PublishRelay<GalleryUiEvent>,
                     initialSelectionMode: Boolean, private val selectedIds: ArrayList<String>,
                     private val postInitialLoad: (adapter: GalleryAdapter) -> Unit,
                     private val postLoad: (adapter: GalleryAdapter) -> Unit)
    : RecyclerView.Adapter<GalleryAdapter.BaseViewHolder>(), AdapterSpanSizeLookup.Interface {
    private val realm = Realm.getDefaultInstance()
    private val result = realm.where(Photo::class.java).equalTo(Photo.FIELD_TYPE, type)
            .sort(Photo.FIELD_EPOCH_DAY, Sort.DESCENDING).findAllAsync()
            .apply {
                addChangeListener { _ ->
                    generateItems()
                    if (initialLoad) {
                        initialLoad = false
                        postInitialLoad.invoke(this@GalleryAdapter)
                    }

                    postLoad.invoke(this@GalleryAdapter)
                }
            }

    private val eventRelayRef = WeakReference(eventRelay)

    private var items = ArrayList<GalleryAdapterItem>()

    var selectionMode: Boolean = initialSelectionMode
        set(value) {
            val didChange = field != value

            field = value

            if (didChange) {
                generateItems(true)
            }
        }

    private var initialLoad = true

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        when (holder) {
            is TitleViewHolder -> holder.bind(items[position])
            is PhotoViewHolder -> holder.bind(items[position], selectionMode)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)

        return when (viewType) {
            TYPE_TITLE -> TitleViewHolder(view)
            TYPE_PHOTO -> PhotoViewHolder(view, this)
            else -> throw IllegalArgumentException("Unhandled item type")
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when {
        items[position].epochDay != null -> TYPE_TITLE
        items[position].photo != null -> TYPE_PHOTO
        else -> throw IllegalArgumentException("Unhandled item type")
    }

    override fun getSpanSize(position: Int): Int = when (getItemViewType(position)) {
        TYPE_TITLE -> GalleryView.GRID_SPAN
        TYPE_PHOTO -> 1
        else -> throw IllegalArgumentException("Unhandled item type")
    }

    fun getPositionOfDay(day: Long): Int {
        items.forEachIndexed { index, item ->
            if (item.epochDay != null && item.epochDay == day) {
                return index
            }
        }

        return -1
    }

    private fun generateItems(selectionModeChanged: Boolean = false) {
        val newItems = ArrayList<GalleryAdapterItem>()

        result.forEach { photo ->
            var indexOfTitle = newItems.indexOfFirst { item -> photo.epochDay == item.epochDay }

            if (indexOfTitle == -1) {
                newItems.add(GalleryAdapterItem(photo.epochDay))
                indexOfTitle = newItems.lastIndex
            }

            var indexToInsertAt: Int = newItems.size

            for (i in (indexOfTitle + 1) until newItems.size) {
                //Find the next title, and we will insert at that index
                if (newItems[i].epochDay != null) {
                    indexToInsertAt = i
                }
            }

            newItems.add(indexToInsertAt, GalleryAdapterItem(photo, selectedIds.contains(photo.id)))
        }

        if (selectionModeChanged) {
            items = newItems
            notifyDataSetChanged()
            return
        }

        val results = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = items[oldItemPosition]
                val new = newItems[newItemPosition]

                if (old.epochDay != null) {
                    return old.epochDay == new.epochDay
                }

                if (old.photo == null || !old.photo.isValid || new.photo == null
                        || !new.photo.isValid) {
                    return false
                }

                return old.photo.id == new.photo.id
            }

            override fun getOldListSize(): Int = items.size

            override fun getNewListSize(): Int = newItems.size

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = items[oldItemPosition]
                val new = newItems[newItemPosition]

                if (old.epochDay != null) {
                    return old.epochDay == new.epochDay
                }

                val oldPhoto = old.photo!!
                val newPhoto = new.photo!!

                return old.selected == new.selected && oldPhoto == newPhoto
                        && oldPhoto.id == newPhoto.id && oldPhoto.epochDay == newPhoto.epochDay
                        && oldPhoto.timestamp == newPhoto.timestamp
                        && oldPhoto.filePath == newPhoto.filePath && oldPhoto.type == newPhoto.type
            }
        }, true)

        items = newItems
        results.dispatchUpdatesTo(this)
    }

    fun getSelectedIds() = selectedIds

    fun updateSelectedIds(newSelectedIds: ArrayList<String>) {
        selectedIds.clear()
        selectedIds.addAll(newSelectedIds)

        generateItems()
    }

    class GalleryAdapterItem {
        val photo: Photo?
        val epochDay: Long?
        var selected = false

        constructor(photo: Photo, isSelected: Boolean) {
            this.photo = photo
            this.selected = isSelected
            epochDay = null
        }

        constructor(epochDay: Long) {
            photo = null
            this.epochDay = epochDay
        }
    }

    abstract class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class TitleViewHolder(itemView: View) : BaseViewHolder(itemView) {
        private val title: TextView by bindView(R.id.gallery_adapter_item_title)

        fun bind(item: GalleryAdapterItem) {
            title.text = LocalDate.ofEpochDay(item.epochDay!!).toFullDateString(itemView.context)
        }
    }

    class PhotoViewHolder(itemView: View, creatingAdapter: GalleryAdapter?) : BaseViewHolder(itemView) {
        private val image: ImageView by bindView(R.id.gallery_adapter_item_image)
        private val selection: ImageView by bindView(R.id.gallery_adapter_item_selection)

        private val adapterRef = WeakReference(creatingAdapter)

        private var currentPhotoId = ""

        init {
            itemView.setOnClickListener {
                val adapter = adapterRef.get() ?: return@setOnClickListener

                val event: GalleryUiEvent = when (adapter.selectionMode) {
                    true -> {
                        if (adapter.selectedIds.contains(currentPhotoId)) {
                            adapter.selectedIds.remove(currentPhotoId)
                        } else {
                            adapter.selectedIds.add(currentPhotoId)
                        }

                        val returnList = ArrayList<String>(adapter.selectedIds.size)
                        returnList.addAll(adapter.selectedIds)

                        GalleryUiEvent.SelectionUpdated(returnList)
                    }

                    false -> GalleryUiEvent.ImageClick(currentPhotoId)
                }

                adapter.eventRelayRef.get()?.accept(event)
            }

            itemView.setOnLongClickListener {
                val adapter = adapterRef.get() ?: return@setOnLongClickListener false

                if (!adapter.selectionMode) {
                    adapter.eventRelayRef.get()?.accept(
                            GalleryUiEvent.SelectionUpdated(arrayListOf(currentPhotoId)))
                } else {
                    itemView.performClick()
                }

                return@setOnLongClickListener true
            }
        }

        fun bind(item: GalleryAdapterItem, selectionMode: Boolean) {
            currentPhotoId = item.photo!!.id

            Picasso.get()
                    .load(File(item.photo.filePath))
                    .fit()
                    .centerCrop()
                    .into(image)

            selection.setVisibleOrGone(selectionMode)

            if (selectionMode) {
                val selectionRes = when (item.selected) {
                    true -> R.drawable.ic_selected_primary_36dp
                    false -> R.drawable.ic_unselected_primary_36dp
                }

                selection.setImageResource(selectionRes)
                selection.contentDescription = when (item.selected) {
                    true -> itemView.getString(R.string.selected)
                    false -> itemView.getString(R.string.not_selected)
                }
            }
        }
    }

    companion object {
        private const val TYPE_TITLE = R.layout.gallery_adapter_title_item
        private const val TYPE_PHOTO = R.layout.gallery_adapter_item
    }
}
