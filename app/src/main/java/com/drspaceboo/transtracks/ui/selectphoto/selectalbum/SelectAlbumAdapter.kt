/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.selectphoto.selectalbum

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.ui.selectphoto.selectalbum.SelectAlbumAdapter.Album
import com.drspaceboo.transtracks.ui.selectphoto.selectalbum.SelectAlbumAdapter.Holder
import com.jakewharton.rxrelay3.PublishRelay
import com.squareup.picasso.Picasso
import io.reactivex.rxjava3.core.Observable
import kotterknife.bindView

class SelectAlbumAdapter() : ListAdapter<Album, Holder>(DiffCallback) {
    private val itemClickRelay: PublishRelay<String> = PublishRelay.create<String>()
    val itemClick: Observable<String> = itemClickRelay

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.select_albums_adapter_item, parent, false
        )
        return Holder(view, itemClickRelay)
    }

    override fun onBindViewHolder(viewHolder: Holder, position: Int) {
        viewHolder.bind(getItem(position))
    }

    fun fetchData(context: Context) {
        val folders = LinkedHashMap<String, Album>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            ),
            null,
            null,
            "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} ASC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val bucketIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                do {
                    val id = cursor.getLong(idIndex)
                    val bucketId = cursor.getString(bucketIdIndex)

                    val album = folders[bucketId]
                        ?: Album(
                            bucketId = bucketId,
                            uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                            name = cursor.getString(nameIndex),
                            count = 0
                        )
                    album.count += 1

                    folders[bucketId] = album
                } while (cursor.moveToNext())
            }
        }
        submitList(folders.values.toList())
    }

    class Holder(itemView: View, private val itemClickRelay: PublishRelay<String>) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView by bindView(R.id.select_album_adapter_item_image)
        private val name: TextView by bindView(R.id.select_album_adapter_item_name)
        private val count: TextView by bindView(R.id.select_album_adapter_item_count)

        private var currentBucketId: String? = null

        init {
            //Avoiding subscription so we don't need to dispose it
            itemView.setOnClickListener {
                val currentBucketId = currentBucketId 
                if (currentBucketId != null) itemClickRelay.accept(currentBucketId)
            }
        }

        fun bind(album: Album) {
            currentBucketId = album.bucketId
            Picasso.get().load(album.uri).fit().centerCrop().into(image)

            name.text = album.name
            count.text = String.format("%1\$d", album.count)
        }
    }

    data class Album(val bucketId: String, val uri: Uri, val name: String, var count: Int)

    private object DiffCallback : DiffUtil.ItemCallback<Album>() {
        override fun areItemsTheSame(oldItem: Album, newItem: Album): Boolean = oldItem.bucketId == newItem.bucketId
        override fun areContentsTheSame(oldItem: Album, newItem: Album): Boolean = oldItem == newItem
    }
}
