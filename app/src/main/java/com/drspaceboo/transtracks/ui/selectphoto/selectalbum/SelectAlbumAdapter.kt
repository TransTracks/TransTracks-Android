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
import com.drspaceboo.transtracks.ui.widget.CursorRecyclerViewAdapter
import com.jakewharton.rxrelay2.PublishRelay
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import kotterknife.bindView
import java.io.File

class SelectAlbumAdapter(context: Context) : CursorRecyclerViewAdapter<SelectAlbumAdapter.Holder>(
        context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID,
                        MediaStore.Files.FileColumns.DATA,
                        MediaStore.Images.Media.BUCKET_ID,
                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                        "COUNT(${MediaStore.Images.Media._ID}) AS $COLUMN_BUCKET_IMAGE_COUNT"),
                //The selection is a bit hacky but it is the best way to do a group by count in a query
                "${MediaStore.Images.Media.BUCKET_ID} LIKE '%') GROUP BY (${MediaStore.Images.Media.BUCKET_ID}",
                null,
                "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} ASC")) {

    private val itemClickRelay: PublishRelay<String> = PublishRelay.create<String>()
    val itemClick: Observable<String> = itemClickRelay

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.select_albums_adapter_item, parent,
                                                               false)
        return Holder(view, itemClickRelay)
    }

    override fun onBindViewHolder(viewHolder: Holder, cursor: Cursor) {
        val bucketIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val countIndex = cursor.getColumnIndexOrThrow(COLUMN_BUCKET_IMAGE_COUNT)

        val bucketId = cursor.getString(bucketIdIndex)
        val data = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA))
        val imageUri = Uri.fromFile(File(data))
        val name = cursor.getString(nameIndex)
        val count = cursor.getInt(countIndex)

        viewHolder.bind(bucketId, imageUri, name, count)
    }

    class Holder(itemView: View, private val itemClickRelay: PublishRelay<String>) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView by bindView(R.id.select_album_adapter_item_image)
        private val name: TextView by bindView(R.id.select_album_adapter_item_name)
        private val count: TextView by bindView(R.id.select_album_adapter_item_count)

        private var currentBucketId: String? = null

        init {
            //Avoiding subscription so we don't need to dispose it
            itemView.setOnClickListener { if (currentBucketId != null) itemClickRelay.accept(currentBucketId) }
        }

        fun bind(bucketId: String, uri: Uri, nameText: String, countNumber: Int) {
            currentBucketId = bucketId
            Picasso.get()
                    .load(uri)
                    .fit()
                    .centerCrop()
                    .into(image)

            name.text = nameText
            count.text = String.format("%1\$d", countNumber)
        }
    }

    companion object {
        private const val COLUMN_BUCKET_IMAGE_COUNT = "bucket_image_count"
    }
}
