/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.ui.home

import android.os.Handler
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.drspaceboo.transtracks.R
import com.drspaceboo.transtracks.data.Photo
import com.drspaceboo.transtracks.util.RxSchedulers
import com.drspaceboo.transtracks.util.isNotDisposed
import com.drspaceboo.transtracks.util.openDefault
import com.jakewharton.rxrelay3.PublishRelay
import com.squareup.picasso.Picasso
import io.reactivex.rxjava3.disposables.Disposable
import io.realm.kotlin.Realm
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.rx3.asObservable
import kotterknife.bindView
import java.io.File
import java.lang.ref.WeakReference
import java.time.LocalDate

class HomeGalleryAdapter(
    private val currentDate: LocalDate, @Photo.Type private val type: Int,
    eventRelay: PublishRelay<HomeUiEvent>
) : RecyclerView.Adapter<HomeGalleryAdapter.BaseViewHolder>() {
    private var photosDisposable = Disposable.disposed()
    private var result = emptyList<Photo>()

    private val eventRelayRef = WeakReference(eventRelay)

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (photosDisposable.isNotDisposed()) {
            photosDisposable.dispose()
        }

        val mainThreadHandler = Handler(recyclerView.context.mainLooper)

        photosDisposable = Realm.openDefault()
            .query(
                Photo::class,
                "${Photo.FIELD_TYPE} == $type && ${Photo.FIELD_EPOCH_DAY} ==  ${currentDate.toEpochDay()}"
            )
            .sort(Photo.FIELD_TIMESTAMP, Sort.DESCENDING)
            .find()
            .asFlow()
            .asObservable()
            .observeOn(RxSchedulers.io())
            .subscribe {
                result = it.list
                mainThreadHandler.post(::notifyDataSetChanged)
            }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        photosDisposable.dispose()
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        when (holder) {
            is AddViewHolder -> holder.bind(currentDate, type)
            is PhotoViewHolder -> holder.bind(result[position - 1])
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)

        return when (viewType) {
            TYPE_ADD -> AddViewHolder(view, eventRelayRef.get())
            TYPE_PHOTO -> PhotoViewHolder(view, eventRelayRef.get())
            else -> throw IllegalArgumentException("Unhandled item type")
        }
    }

    override fun getItemCount(): Int = result.count() + 1

    override fun getItemViewType(position: Int): Int = when (position) {
        0 -> TYPE_ADD
        else -> TYPE_PHOTO
    }

    abstract class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class AddViewHolder(itemView: View, eventRelay: PublishRelay<HomeUiEvent>?) :
        BaseViewHolder(itemView) {
        private val add: AppCompatImageButton by bindView(R.id.home_adapter_add_button)

        private val eventRelayRef = WeakReference(eventRelay)

        private var currentDate: LocalDate = LocalDate.MIN

        @Photo.Type
        private var type: Int = Photo.TYPE_FACE

        init {
            add.setOnClickListener {
                val popup = PopupMenu(it.context, it)
                popup.menuInflater.inflate(R.menu.popup_media_source, popup.menu)
                popup.setOnMenuItemClickListener { menuItem: MenuItem ->
                    when (menuItem.itemId) {
                        R.id.media_source_camera -> eventRelayRef.get()
                            ?.accept(HomeUiEvent.AddPhotoCamera(currentDate, type))

                        R.id.media_source_gallery -> eventRelayRef.get()
                            ?.accept(HomeUiEvent.AddPhotoGallery(currentDate, type))

                        else -> return@setOnMenuItemClickListener false
                    }

                    return@setOnMenuItemClickListener true
                }
                popup.show()
            }
        }

        fun bind(currentDate: LocalDate, @Photo.Type type: Int) {
            this.currentDate = currentDate
            this.type = type
        }
    }

    class PhotoViewHolder(itemView: View, eventRelay: PublishRelay<HomeUiEvent>?) :
        BaseViewHolder(itemView) {
        private val image: ImageView by bindView(R.id.home_adapter_item_image)

        private val eventRelayRef = WeakReference(eventRelay)

        private var currentPhotoId = ""

        init {
            itemView.setOnClickListener {
                eventRelayRef.get()?.accept(HomeUiEvent.ImageClick(currentPhotoId))
            }
        }

        fun bind(photo: Photo) {
            currentPhotoId = photo.id

            Picasso.get()
                .load(File(photo.filePath))
                .fit()
                .centerCrop()
                .into(image)
        }
    }

    @Suppress("MayBeConstant")
    companion object {
        private val TYPE_PHOTO = R.layout.home_adapter_item
        private val TYPE_ADD = R.layout.home_adapter_add_item
    }
}
