/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.selectphoto

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.ui.widget.CursorRecyclerViewAdapter
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.setVisibleOrGone
import com.jakewharton.rxrelay2.PublishRelay
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import kotterknife.bindView
import java.io.File
import java.lang.ref.WeakReference

class SelectPhotoAdapter(context: Context)
    : CursorRecyclerViewAdapter<SelectPhotoAdapter.BaseHolder>(getGalleryCursor(context)) {

    private val eventRelay: PublishRelay<SelectPhotoUiEvent> = PublishRelay.create()
    val events: Observable<SelectPhotoUiEvent> = eventRelay

    var selectionMode: Boolean = false
        set(value) {
            val didChange = field != value
            field = value

            if (!field) {
                selectedUris.clear()
            }

            if (didChange) {
                notifyDataSetChanged()
            }
        }

    private var selectedUris = ArrayList<Uri>()

    override fun getItemCount(): Int = super.getItemCount() + 1

    override fun getItemViewType(position: Int): Int = when (position) {
        0 -> R.layout.select_photo_adapter_add_image_item
        else -> R.layout.select_photo_adapter_item
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseHolder {
        val layoutInflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            R.layout.select_photo_adapter_add_image_item ->
                TakePhotoHolder(layoutInflater.inflate(R.layout.select_photo_adapter_add_image_item,
                                                       parent, false),
                                eventRelay)

            else ->
                ImageHolder(layoutInflater.inflate(R.layout.select_photo_adapter_item, parent, false),
                            this)
        }
    }

    override fun onBindViewHolder(viewHolder: BaseHolder, position: Int) {
        if (position != 0) {
            //Adjust the bind call for the position ignoring the Take Photo item we have added
            super.onBindViewHolder(viewHolder, position - 1)
        }
    }

    override fun onBindViewHolder(viewHolder: BaseHolder, cursor: Cursor) {
        when (viewHolder) {
            is ImageHolder -> {
                val uri = getUri(cursor)!!
                viewHolder.bind(uri, selectionMode, selectedUris.contains(uri))
            }
        }
    }

    fun getItemPosition(uri: Uri): Int {
        val localCursor = cursor ?: return RecyclerView.NO_POSITION

        for (i in 0 until localCursor.count) {
            if (uri.path == getUri(i)?.path) {
                return i
            }
        }

        return RecyclerView.NO_POSITION
    }

    fun getSelectedUris(): ArrayList<Uri> {
        return ArrayList(selectedUris)
    }

    fun getUri(position: Int): Uri? {
        val localCursor = cursor ?: return null

        if (!localCursor.moveToPosition(position)) {
            throw IllegalStateException("couldn't move cursor to position $position")
        }

        return getUri(localCursor)
    }

    private fun getUri(cursor: Cursor): Uri? {
        val data = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA))
                ?: return null

        return Uri.fromFile(File(data))
    }

    fun updateSelectedUris(newSelectedUris: ArrayList<Uri>) {
        selectedUris = newSelectedUris
        notifyDataSetChanged()
    }

    open class BaseHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class TakePhotoHolder(itemView: View, itemClickRelay: PublishRelay<SelectPhotoUiEvent>) : BaseHolder(itemView) {
        init {
            //Avoiding subscription so we don't need to dispose it
            itemView.setOnClickListener { itemClickRelay.accept(SelectPhotoUiEvent.TakePhoto) }
        }
    }

    class ImageHolder(itemView: View, creatingAdapter: SelectPhotoAdapter?) : BaseHolder(itemView) {
        private val image: ImageView by bindView(R.id.select_photo_adapter_item_image)
        private val selection: ImageView by bindView(R.id.select_photo_adapter_item_selection)

        private val adapterRef = WeakReference(creatingAdapter)

        private var currentUri: Uri? = null

        init {
            //Avoiding subscription so we don't need to dispose it
            itemView.setOnClickListener {
                val uri = currentUri ?: return@setOnClickListener
                val adapter = adapterRef.get() ?: return@setOnClickListener

                val event: SelectPhotoUiEvent = when (adapter.selectionMode) {
                    true -> {
                        if (adapter.selectedUris.contains(uri)) {
                            adapter.selectedUris.remove(uri)
                        } else {
                            adapter.selectedUris.add(uri)
                        }

                        SelectPhotoUiEvent.SelectionUpdate(adapter.getSelectedUris())
                    }

                    false -> SelectPhotoUiEvent.PhotoSelected(uri)
                }

                adapter.eventRelay.accept(event)
            }

            itemView.setOnLongClickListener {
                val uri = currentUri ?: return@setOnLongClickListener false
                val adapter = adapterRef.get() ?: return@setOnLongClickListener false

                if (!adapter.selectionMode) {
                    adapter.eventRelay.accept(
                            SelectPhotoUiEvent.SelectionUpdate(arrayListOf(uri)))
                } else {
                    itemView.performClick()
                }

                return@setOnLongClickListener true
            }
        }

        fun bind(uri: Uri, selectionMode: Boolean, isSelected: Boolean) {
            currentUri = uri
            Picasso.get()
                    .load(uri)
                    .fit()
                    .centerCrop()
                    .into(image)

            selection.setVisibleOrGone(selectionMode)

            if (selectionMode) {
                val selectionRes = when (isSelected) {
                    true -> R.drawable.ic_selected_primary_36dp
                    false -> R.drawable.ic_unselected_primary_36dp
                }
                selection.setImageResource(selectionRes)

                selection.contentDescription = when (isSelected) {
                    true -> itemView.getString(R.string.selected)
                    false -> itemView.getString(R.string.not_selected)
                }
            }
        }
    }

    companion object {
        fun getGalleryCursor(context: Context): Cursor {
            return context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                                 arrayOf(MediaStore.Images.Media._ID, MediaStore.Files.FileColumns.DATA),
                                                 null, null, MediaStore.Images.Media._ID + " DESC")!!
        }
    }
}
