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

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.ui.widget.AdapterSpanSizeLookup
import com.drspaceboo.transtracks.ui.widget.CursorRecyclerViewAdapter
import com.drspaceboo.transtracks.ui.widget.SquareImageView
import com.drspaceboo.transtracks.util.FileUtil
import com.jakewharton.rxrelay2.PublishRelay
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import kotterknife.bindView
import java.io.File
import java.io.FileNotFoundException

class SingleAlbumAdapter(context: Context, bucketId: String)
    : CursorRecyclerViewAdapter<SingleAlbumAdapter.BaseHolder>(getSingleAlbumCursor(context, bucketId)), AdapterSpanSizeLookup.Interface {
    private val itemClickRelay: PublishRelay<Int> = PublishRelay.create<Int>()
    val itemClick: Observable<Int> = itemClickRelay

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
                            itemClickRelay)
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
                viewHolder.bind(getUri(cursor)!!, cursor.position, this)
            }
        }
    }

    open class BaseHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class ImageHolder(itemView: View, private val itemClickRelay: PublishRelay<Int>) : BaseHolder(itemView) {
        private val image: SquareImageView by bindView(R.id.single_album_image_item_image)

        private var currentIndex: Int? = null

        init {
            //Avoiding subscription so we don't need to dispose it
            itemView.setOnClickListener { if (currentIndex != null) itemClickRelay.accept(currentIndex) }
        }

        fun bind(uri: Uri, index: Int, adapter: SingleAlbumAdapter) {
            currentIndex = index
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
            return context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                                 arrayOf(MediaStore.Images.Media._ID,
                                                         MediaStore.Files.FileColumns.DATA),
                                                 "${MediaStore.Images.Media.BUCKET_ID}=?",
                                                 arrayOf(bucketId),
                                                 MediaStore.Images.Media._ID + " DESC")!!
        }
    }
}
