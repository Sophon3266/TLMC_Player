package com.tlmc.player.ui.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tlmc.player.data.model.WebDavFile
import com.tlmc.player.databinding.ItemSearchResultBinding
import com.tlmc.player.util.FileUtils

class SearchResultAdapter(
    private val onResultClick: (WebDavFile) -> Unit,
    private val onNavigateClick: (WebDavFile) -> Unit
) : ListAdapter<WebDavFile, SearchResultAdapter.SearchResultViewHolder>(SearchResultDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SearchResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SearchResultViewHolder(
        private val binding: ItemSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: WebDavFile) {
            binding.searchResultName.text = file.name
            binding.searchResultPath.text = FileUtils.getDirectoryPath(file.path)
            binding.searchResultIcon.setImageResource(FileUtils.getFileIcon(file))

            binding.root.setOnClickListener { onResultClick(file) }
            binding.btnNavigateToFolder.setOnClickListener { onNavigateClick(file) }
        }
    }

    class SearchResultDiffCallback : DiffUtil.ItemCallback<WebDavFile>() {
        override fun areItemsTheSame(oldItem: WebDavFile, newItem: WebDavFile): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: WebDavFile, newItem: WebDavFile): Boolean {
            return oldItem == newItem
        }
    }
}

