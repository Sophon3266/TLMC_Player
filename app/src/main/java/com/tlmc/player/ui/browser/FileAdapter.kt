package com.tlmc.player.ui.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tlmc.player.data.model.WebDavFile
import com.tlmc.player.databinding.ItemFileBinding
import com.tlmc.player.util.FileUtils

class FileAdapter(
    private val onFileClick: (WebDavFile) -> Unit,
    private val onFileLongClick: (WebDavFile) -> Boolean
) : ListAdapter<WebDavFile, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileViewHolder(
        private val binding: ItemFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: WebDavFile) {
            binding.fileName.text = file.name
            binding.fileIcon.setImageResource(FileUtils.getFileIcon(file))

            binding.fileSize.text = if (file.isDirectory) {
                "文件夹"
            } else {
                FileUtils.formatFileSize(file.size)
            }

            binding.root.setOnClickListener { onFileClick(file) }
            binding.root.setOnLongClickListener { onFileLongClick(file) }
        }
    }

    class FileDiffCallback : DiffUtil.ItemCallback<WebDavFile>() {
        override fun areItemsTheSame(oldItem: WebDavFile, newItem: WebDavFile): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: WebDavFile, newItem: WebDavFile): Boolean {
            return oldItem == newItem
        }
    }
}

