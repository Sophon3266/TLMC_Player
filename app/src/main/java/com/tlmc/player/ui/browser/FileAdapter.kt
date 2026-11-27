package com.tlmc.player.ui.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tlmc.player.R
import com.tlmc.player.data.model.WebDavFile
import com.tlmc.player.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FileAdapter(
    private val folderCoverLoader: FolderCoverLoader,
    private val coroutineScope: CoroutineScope,
    private val onFileClick: (WebDavFile) -> Unit,
    private val onFileLongClick: (WebDavFile) -> Boolean
) : ListAdapter<WebDavFile, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
    }

    var isGridMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val layoutRes = if (viewType == VIEW_TYPE_GRID) {
            R.layout.item_file_grid
        } else {
            R.layout.item_file
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: FileViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelCoverJob()
    }

    inner class FileViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        private val fileIcon: ImageView = itemView.findViewById(R.id.fileIcon)
        private val folderCover: ImageView? = itemView.findViewById(R.id.folderCover)
        private val fileName: TextView = itemView.findViewById(R.id.fileName)
        private val fileSize: TextView = itemView.findViewById(R.id.fileSize)
        private var coverJob: Job? = null

        fun cancelCoverJob() {
            coverJob?.cancel()
            coverJob = null
        }

        fun bind(file: WebDavFile) {
            cancelCoverJob()

            fileName.text = file.name
            fileIcon.setImageResource(FileUtils.getFileIcon(file))
            fileIcon.visibility = View.VISIBLE
            folderCover?.visibility = View.GONE

            val typedValue = android.util.TypedValue()
            itemView.context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorPrimary,
                typedValue,
                true
            )
            fileIcon.imageTintList = android.content.res.ColorStateList.valueOf(typedValue.data)

            // 在列表模式下也支持清除之前的 tint/图片残留
            if (folderCover == null) {
                Glide.with(fileIcon).clear(fileIcon)
            } else {
                Glide.with(folderCover).clear(folderCover)
            }

            fileSize.text = if (file.isDirectory) {
                "文件夹"
            } else {
                FileUtils.formatFileSize(file.size)
            }

            itemView.setOnClickListener { onFileClick(file) }
            itemView.setOnLongClickListener { onFileLongClick(file) }

            if (file.isDirectory) {
                val expectedPath = file.path
                coverJob = coroutineScope.launch {
                    val coverFile = folderCoverLoader.getCoverFile(expectedPath) ?: return@launch
                    if (adapterPosition == RecyclerView.NO_POSITION) return@launch
                    if (folderCover != null) {
                        folderCover.visibility = View.VISIBLE
                        fileIcon.visibility = View.GONE
                        Glide.with(folderCover)
                            .load(coverFile)
                            .centerCrop()
                            .into(folderCover)
                    } else {
                        fileIcon.imageTintList = null
                        Glide.with(fileIcon)
                            .load(coverFile)
                            .centerCrop()
                            .into(fileIcon)
                    }
                }
            }
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
