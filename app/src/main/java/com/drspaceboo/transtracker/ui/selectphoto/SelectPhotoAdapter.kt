/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracker.ui.selectphoto

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.drspaceboo.transtracker.R
import com.drspaceboo.transtracker.ui.widget.CursorRecyclerViewAdapter
import com.jakewharton.rxrelay2.PublishRelay
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import kotterknife.bindView
import java.io.File

class SelectPhotoAdapter(context: Context)
    : CursorRecyclerViewAdapter<SelectPhotoAdapter.ImageHolder>(getGalleryCursor(context)) {

    private val itemClickRelay: PublishRelay<Uri> = PublishRelay.create<Uri>()
    val itemClick: Observable<Uri> = itemClickRelay

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageHolder {
        return ImageHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.select_photo_adapter_item, parent, false),
                itemClickRelay)
    }

    override fun onBindViewHolder(viewHolder: ImageHolder, cursor: Cursor) {
        val data = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA))
        val imageUri = Uri.fromFile(File(data))
        viewHolder.bind(imageUri)
    }

    class ImageHolder(itemView: View, private val itemClickRelay: PublishRelay<Uri>) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView by bindView(R.id.select_photo_adapter_item_image)

        private var currentUri: Uri? = null

        init {
            //Avoiding subscription so we don't need to dispose it
            itemView.setOnClickListener { if (currentUri != null) itemClickRelay.accept(currentUri) }
        }

        fun bind(uri: Uri) {
            currentUri = uri
            Picasso.get()
                    .load(uri)
                    .fit()
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .centerCrop()
                    .into(image)
        }
    }

    companion object {
        fun getGalleryCursor(context: Context): Cursor {
            return context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID, MediaStore.Files.FileColumns.DATA),
                    null, null, MediaStore.Images.Media._ID + " DESC")
        }
    }
}
