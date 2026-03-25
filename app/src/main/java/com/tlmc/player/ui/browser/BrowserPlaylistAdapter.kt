package com.tlmc.player.ui.browser

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tlmc.player.R
import com.tlmc.player.databinding.ItemPlaylistBinding

class BrowserPlaylistAdapter(
    private val onItemClick: (Int) -> Unit,
    private val onRemoveClick: (Int) -> Unit
) : ListAdapter<PlaylistItem, BrowserPlaylistAdapter.PlaylistViewHolder>(PlaylistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlaylistViewHolder(
        private val binding: ItemPlaylistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PlaylistItem) {
            binding.playlistTitle.text = item.title
            binding.playlistArtist.text = item.artist
            binding.playlistIndex.text = "%02d".format(item.index + 1)

            val titleColor = if (item.isCurrent) {
                binding.root.context.getColor(R.color.md_theme_primary)
            } else {
                binding.root.context.getColor(R.color.md_theme_onSurface)
            }
            binding.playlistTitle.setTextColor(titleColor)
            binding.playlistIndex.setTextColor(titleColor)
            binding.playlistTitle.setTypeface(null, if (item.isCurrent) Typeface.BOLD else Typeface.NORMAL)

            binding.root.setOnClickListener { onItemClick(item.index) }
            binding.removeButton.setOnClickListener { onRemoveClick(item.index) }
        }
    }

    class PlaylistDiffCallback : DiffUtil.ItemCallback<PlaylistItem>() {
        override fun areItemsTheSame(oldItem: PlaylistItem, newItem: PlaylistItem): Boolean {
            return oldItem.index == newItem.index
        }

        override fun areContentsTheSame(oldItem: PlaylistItem, newItem: PlaylistItem): Boolean {
            return oldItem == newItem
        }
    }
}
