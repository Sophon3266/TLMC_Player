package com.tlmc.player.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tlmc.player.R
import com.tlmc.player.ui.browser.FolderCoverLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

data class LibraryGalleryItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String = "",
    val localCoverFile: File? = null,
    val remoteDirectoryPath: String = "",
    @DrawableRes val placeholderIconRes: Int = R.drawable.ic_music,
    val payload: Any? = null
)

class LibraryGalleryAdapter(
    private val folderCoverLoader: FolderCoverLoader,
    private val coroutineScope: CoroutineScope,
    private val onItemClick: (LibraryGalleryItem, Int) -> Unit,
    private val onItemLongClick: (LibraryGalleryItem, Int) -> Boolean
) : ListAdapter<LibraryGalleryItem, LibraryGalleryAdapter.CardViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
    }

    var isGridMode: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val layoutRes = if (viewType == VIEW_TYPE_GRID) {
            R.layout.item_library_gallery_card
        } else {
            R.layout.item_library_list
        }
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutRes, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: CardViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelCoverJob()
    }

    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val placeholderIcon: ImageView = itemView.findViewById(R.id.cardPlaceholderIcon)
        private val coverImage: ImageView = itemView.findViewById(R.id.cardCover)
        private val badgeText: TextView = itemView.findViewById(R.id.cardBadge)
        private val titleText: TextView = itemView.findViewById(R.id.cardTitle)
        private val subtitleText: TextView = itemView.findViewById(R.id.cardSubtitle)
        private var coverJob: Job? = null

        fun cancelCoverJob() {
            coverJob?.cancel()
            coverJob = null
        }

        fun bind(item: LibraryGalleryItem) {
            cancelCoverJob()
            Glide.with(coverImage).clear(coverImage)

            titleText.text = item.title
            subtitleText.text = item.subtitle.ifEmpty { " " }
            placeholderIcon.setImageResource(item.placeholderIconRes)
            placeholderIcon.visibility = View.VISIBLE
            coverImage.visibility = View.GONE

            if (item.badge.isNotEmpty()) {
                badgeText.text = item.badge
                badgeText.visibility = View.VISIBLE
            } else {
                badgeText.visibility = View.GONE
            }

            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick(item, pos)
                }
            }
            itemView.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemLongClick(item, pos)
                } else {
                    false
                }
            }

            val localCover = item.localCoverFile
            if (localCover != null && localCover.exists()) {
                coverImage.visibility = View.VISIBLE
                placeholderIcon.visibility = View.GONE
                Glide.with(coverImage)
                    .load(localCover)
                    .centerCrop()
                    .into(coverImage)
            } else if (item.remoteDirectoryPath.isNotEmpty()) {
                val dirPath = item.remoteDirectoryPath
                coverJob = coroutineScope.launch {
                    val coverFile = folderCoverLoader.getCoverFile(dirPath) ?: return@launch
                    if (bindingAdapterPosition == RecyclerView.NO_POSITION) return@launch
                    coverImage.visibility = View.VISIBLE
                    placeholderIcon.visibility = View.GONE
                    Glide.with(coverImage)
                        .load(coverFile)
                        .centerCrop()
                        .into(coverImage)
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<LibraryGalleryItem>() {
        override fun areItemsTheSame(oldItem: LibraryGalleryItem, newItem: LibraryGalleryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LibraryGalleryItem, newItem: LibraryGalleryItem): Boolean {
            return oldItem == newItem
        }
    }
}
