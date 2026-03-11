package com.tlmc.player.ui.player

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tlmc.player.R
import com.tlmc.player.data.model.CueTrack
import com.tlmc.player.databinding.ItemTrackBinding

class TrackAdapter(
    private val onTrackClick: (Int) -> Unit
) : ListAdapter<CueTrack, TrackAdapter.TrackViewHolder>(TrackDiffCallback()) {

    private var currentTrackIndex: Int = -1

    fun setCurrentTrack(index: Int) {
        val previousIndex = currentTrackIndex
        currentTrackIndex = index
        if (previousIndex >= 0) notifyItemChanged(previousIndex)
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ItemTrackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class TrackViewHolder(
        private val binding: ItemTrackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(track: CueTrack, position: Int) {
            binding.trackNumber.text = "%02d".format(track.number)
            binding.trackTitle.text = track.title
            binding.trackDuration.text = track.formattedStart

            val isCurrentTrack = position == currentTrackIndex
            binding.trackTitle.setTypeface(null, if (isCurrentTrack) Typeface.BOLD else Typeface.NORMAL)

            val textColor = if (isCurrentTrack) {
                binding.root.context.getColor(R.color.md_theme_primary)
            } else {
                binding.root.context.getColor(R.color.md_theme_onSurface)
            }
            binding.trackTitle.setTextColor(textColor)
            binding.trackNumber.setTextColor(textColor)

            binding.root.setOnClickListener { onTrackClick(position) }
        }
    }

    class TrackDiffCallback : DiffUtil.ItemCallback<CueTrack>() {
        override fun areItemsTheSame(oldItem: CueTrack, newItem: CueTrack): Boolean {
            return oldItem.number == newItem.number
        }

        override fun areContentsTheSame(oldItem: CueTrack, newItem: CueTrack): Boolean {
            return oldItem == newItem
        }
    }
}

