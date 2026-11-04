package com.tlmc.player.ui.gallery

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.tlmc.player.data.model.WebDavFile
import com.tlmc.player.data.repository.WebDavRepository
import com.tlmc.player.databinding.ItemGalleryImageBinding
import com.tlmc.player.util.TiffDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryAdapter(
    private val repository: WebDavRepository,
    private val lifecycleScope: LifecycleCoroutineScope
) : RecyclerView.Adapter<GalleryAdapter.ImageViewHolder>() {

    companion object {
        const val LOOP_MULTIPLIER = 1000
    }

    private var files: List<WebDavFile> = emptyList()

    fun submitList(newFiles: List<WebDavFile>) {
        files = newFiles
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = if (files.size > 1) files.size * LOOP_MULTIPLIER else files.size

    fun getRealPosition(position: Int): Int = if (files.isEmpty()) 0 else position % files.size

    fun getLoopStartPosition(initialIndex: Int): Int {
        if (files.isEmpty()) return 0
        return initialIndex + (files.size * (LOOP_MULTIPLIER / 2))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemGalleryImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding, repository, lifecycleScope)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(files[getRealPosition(position)])
    }

    override fun onViewRecycled(holder: ImageViewHolder) {
        super.onViewRecycled(holder)
        holder.binding.root.photoView = null
    }

    class ImageViewHolder(
        val binding: ItemGalleryImageBinding,
        private val repository: WebDavRepository,
        private val lifecycleScope: LifecycleCoroutineScope
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentFile: WebDavFile? = null

        fun bind(file: WebDavFile) {
            currentFile = file
            
            binding.photoView.visibility = View.GONE
            binding.progressBar.visibility = View.VISIBLE
            binding.tvError.visibility = View.GONE
            
            val isTiffFile = file.extension in setOf("tif", "tiff")

            lifecycleScope.launch {
                val result = repository.downloadFile(file.path)
                result.onSuccess { bytes ->
                    withContext(Dispatchers.Main) {
                        try {
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                ?: if (isTiffFile || TiffDecoder.looksLikeTiff(bytes)) {
                                    TiffDecoder.decode(bytes)
                                } else {
                                    null
                                }
                            if (bitmap != null) {
                                binding.photoView.setImageBitmap(bitmap)
                                binding.photoView.visibility = View.VISIBLE
                                binding.progressBar.visibility = View.GONE
                                binding.tvError.visibility = View.GONE

                                // 将 PhotoView 关联到自定义容器，用于处理与 ViewPager2 的触摸冲突
                                binding.root.photoView = binding.photoView
                            } else {
                                binding.progressBar.visibility = View.GONE
                                binding.tvError.visibility = View.VISIBLE
                                binding.tvError.setOnClickListener {
                                    currentFile?.let { bind(it) }
                                }
                            }
                        } catch (e: Exception) {
                            binding.progressBar.visibility = View.GONE
                            binding.tvError.visibility = View.VISIBLE
                            binding.tvError.setOnClickListener {
                                currentFile?.let { bind(it) }
                            }
                        }
                    }
                }.onFailure { e ->
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.visibility = View.VISIBLE
                        binding.tvError.setOnClickListener {
                            currentFile?.let { bind(it) }
                        }
                    }
                }
            }
        }
    }
}
