/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.selectphoto.singlealbum

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.ui.widget.AdapterSpanSizeLookup
import com.drspaceboo.transtracks.ui.widget.CursorRecyclerViewAdapter
import com.drspaceboo.transtracks.util.FileUtil
import com.drspaceboo.transtracks.util.getString
import com.drspaceboo.transtracks.util.setVisibleOrGone
import com.jakewharton.rxrelay3.PublishRelay
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import io.reactivex.rxjava3.core.Observable
import kotterknife.bindView
import java.io.FileNotFoundException
import java.lang.ref.WeakReference

class SingleAlbumAdapter(context: Context, bucketId: String)
    : CursorRecyclerViewAdapter<SingleAlbumAdapter.BaseHolder>(getSingleAlbumCursor(context, bucketId)),
      AdapterSpanSizeLookup.Interface {
    private val eventRelay: PublishRelay<SingleAlbumUiEvent> = PublishRelay.create()
    val events: Observable<SingleAlbumUiEvent> = eventRelay

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

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            (itemCount - 1) -> TYPE_COUNT

            else -> TYPE_IMAGE
        }
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + 1 //The last item is the photo count
    }

    override fun getSpanSize(position: Int): Int = when (position) {
        itemCount - 1 -> SingleAlbumView.GRID_SPAN
        else -> 1
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

    private fun getUri(cursor: Cursor): Uri {
        val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
        if (idIndex < 0){
            throw IllegalStateException("couldn't find the column index of 'MediaStore.Images.Media._ID'")
        }
        val id = cursor.getLong(idIndex)
        return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseHolder {
        return when (viewType) {
            TYPE_COUNT -> {
                CountHolder(LayoutInflater.from(parent.context)
                                    .inflate(R.layout.single_album_adapter_count_item,
                                             parent, false))
            }

            else -> {
                ImageHolder(LayoutInflater.from(parent.context)
                                    .inflate(R.layout.single_album_adapter_image_item,
                                             parent, false),
                            this)
            }
        }
    }

    override fun onBindViewHolder(viewHolder: BaseHolder, position: Int) {
        when (viewHolder) {
            is ImageHolder -> {
                //Will call the bellow bind function with the correct cursor position
                super.onBindViewHolder(viewHolder, position)
            }

            is CountHolder -> {
                //Calling the super to avoid including the count view being counted
                viewHolder.bind(super.getItemCount())
            }
        }
    }

    override fun onBindViewHolder(viewHolder: BaseHolder, cursor: Cursor) {
        when (viewHolder) {
            is ImageHolder -> {
                val uri = getUri(cursor)
                viewHolder.bind(uri, selectionMode, selectedUris.contains(uri))
            }
        }
    }

    fun updateSelectedUris(newSelectedUris: ArrayList<Uri>) {
        selectedUris = newSelectedUris
        notifyDataSetChanged()
    }

    open class BaseHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class ImageHolder(itemView: View, creatingAdapter: SingleAlbumAdapter?) : BaseHolder(itemView) {
        private val image: ImageView by bindView(R.id.single_album_image_item_image)
        private val selection: ImageView by bindView(R.id.single_album_image_item_selection)

        private val adapterRef = WeakReference(creatingAdapter)

        private var currentUri: Uri? = null

        init {
            //Avoiding subscription so we don't need to dispose it
            itemView.setOnClickListener {
                val uri = currentUri ?: return@setOnClickListener
                val adapter = adapterRef.get() ?: return@setOnClickListener

                val event: SingleAlbumUiEvent = when (adapter.selectionMode) {
                    true -> {
                        if (adapter.selectedUris.contains(uri)) {
                            adapter.selectedUris.remove(uri)
                        } else {
                            adapter.selectedUris.add(uri)
                        }

                        SingleAlbumUiEvent.SelectionUpdate(adapter.getSelectedUris())
                    }

                    false -> SingleAlbumUiEvent.SelectPhoto(uri)
                }

                adapter.eventRelay.accept(event)
            }

            itemView.setOnLongClickListener {
                val uri = currentUri ?: return@setOnLongClickListener false
                val adapter = adapterRef.get() ?: return@setOnLongClickListener false

                if (!adapter.selectionMode) {
                    adapter.eventRelay.accept(
                            SingleAlbumUiEvent.SelectionUpdate(arrayListOf(uri)))
                } else {
                    itemView.performClick()
                }

                return@setOnLongClickListener true
            }
        }

        fun bind(uri: Uri, selectionMode: Boolean, isSelected: Boolean) {
            currentUri = uri
            val adapter = adapterRef.get() ?: return

            Picasso.get()
                    .load(uri)
                    .fit()
                    .centerCrop()
                    .into(image, object : Callback {
                        override fun onSuccess() {
                        }

                        override fun onError(e: Exception?) {
                            if (e != null && e is FileNotFoundException) {
                                FileUtil.removeImageFromGallery(uri.path!!)
                                adapter.notifyDataSetChanged()
                            }
                        }
                    })

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

    class CountHolder(itemView: View) : BaseHolder(itemView) {
        private val text: TextView by bindView(R.id.single_album_adapter_count_item_text)

        fun bind(count: Int) {
            text.text = text.resources.getQuantityString(R.plurals.photos, count, count)
        }
    }

    companion object {
        private const val TYPE_IMAGE = 0
        private const val TYPE_COUNT = 1

        fun getSingleAlbumCursor(context: Context, bucketId: String): Cursor {
            return context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.BUCKET_ID}=?",
                arrayOf(bucketId),
                MediaStore.Images.Media._ID + " DESC"
            )!!
        }
    }
}
