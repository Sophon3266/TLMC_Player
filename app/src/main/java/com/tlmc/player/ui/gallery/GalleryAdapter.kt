package com.tlmc.player.ui.gallery

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.recyclerview.widget.RecyclerView
import com.tlmc.player.data.model.WebDavFile
import com.tlmc.player.data.repository.WebDavRepository
import com.tlmc.player.databinding.ItemGalleryImageBinding
import com.tlmc.player.databinding.ItemGalleryVideoBinding
import com.tlmc.player.util.TiffDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class GalleryAdapter(
    private val repository: WebDavRepository,
    private val okHttpClient: OkHttpClient,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val playbackPositions: MutableMap<String, Long>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_IMAGE = 0
        const val VIEW_TYPE_VIDEO = 1
        const val LOOP_MULTIPLIER = 1000
    }

    private var files: List<WebDavFile> = emptyList()
    private var attachedRecyclerView: RecyclerView? = null

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

    fun onPageChanged(oldPosition: Int, newPosition: Int) {
        if (oldPosition < 0 || files.isEmpty()) return
        val realOldPos = getRealPosition(oldPosition)
        val oldFile = files.getOrNull(realOldPos) ?: return
        if (!oldFile.isVideo) return
        val rv = attachedRecyclerView ?: return
        val holder = rv.findViewHolderForAdapterPosition(oldPosition)
        if (holder is VideoViewHolder) {
            holder.pauseAndSavePosition(playbackPositions)
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        attachedRecyclerView = null
    }

    override fun getItemViewType(position: Int): Int {
        val file = files[getRealPosition(position)]
        return if (file.isImage) VIEW_TYPE_IMAGE else VIEW_TYPE_VIDEO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_IMAGE -> {
                val binding = ItemGalleryImageBinding.inflate(inflater, parent, false)
                ImageViewHolder(binding, repository, lifecycleScope)
            }
            VIEW_TYPE_VIDEO -> {
                val binding = ItemGalleryVideoBinding.inflate(inflater, parent, false)
                VideoViewHolder(binding, repository, okHttpClient)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val file = files[getRealPosition(position)]
        when (holder) {
            is ImageViewHolder -> holder.bind(file)
            is VideoViewHolder -> holder.bind(file, playbackPositions)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoViewHolder) {
            holder.pauseAndSavePosition(playbackPositions)
            holder.releasePlayer()
        }
        if (holder is ImageViewHolder) {
            holder.binding.root.photoView = null
        }
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

    @OptIn(UnstableApi::class)
    class VideoViewHolder(
        val binding: ItemGalleryVideoBinding,
        private val repository: WebDavRepository,
        private val okHttpClient: OkHttpClient
    ) : RecyclerView.ViewHolder(binding.root) {

        var player: ExoPlayer? = null
        var currentFile: WebDavFile? = null
        private var playbackPositionsRef: MutableMap<String, Long>? = null

        fun bind(file: WebDavFile, playbackPositions: MutableMap<String, Long>) {
            currentFile = file
            playbackPositionsRef = playbackPositions
            releasePlayer()

            binding.tvError.visibility = View.GONE
            binding.btnPlay.visibility = View.VISIBLE
            binding.progressBar.visibility = View.VISIBLE

            val dataSourceFactory =
                OkHttpDataSource.Factory(repository.getAuthenticatedOkHttpClient())
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            player = ExoPlayer.Builder(binding.root.context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .also { exoPlayer ->
                    binding.playerView.player = exoPlayer

                    exoPlayer.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_BUFFERING -> {
                                    binding.progressBar.visibility = View.VISIBLE
                                }
                                Player.STATE_READY -> {
                                    binding.progressBar.visibility = View.GONE
                                    binding.tvError.visibility = View.GONE
                                }
                                Player.STATE_ENDED -> {
                                    binding.progressBar.visibility = View.GONE
                                }
                                else -> Unit
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            binding.progressBar.visibility = View.GONE
                            binding.btnPlay.visibility = View.GONE
                            binding.tvError.visibility = View.VISIBLE
                            binding.tvError.setOnClickListener {
                                currentFile?.let { file ->
                                    playbackPositionsRef?.let { positions ->
                                        bind(file, positions)
                                    }
                                }
                            }
                        }
                    })

                    val mediaUrl = repository.getFileUrl(file.path)
                    exoPlayer.setMediaItem(MediaItem.fromUri(mediaUrl))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = false

                    val savedPosition = playbackPositions[file.path]
                    if (savedPosition != null && savedPosition > 0) {
                        exoPlayer.seekTo(savedPosition)
                    }

                    binding.btnPlay.setOnClickListener {
                        binding.btnPlay.visibility = View.GONE
                        exoPlayer.playWhenReady = true
                    }
                }
        }

        fun pauseAndSavePosition(positions: MutableMap<String, Long>) {
            val file = currentFile ?: return
            val p = player ?: return
            val pos = p.currentPosition
            if (pos > 0) {
                positions[file.path] = pos
            }
            p.playWhenReady = false
        }

        fun releasePlayer() {
            binding.btnPlay.setOnClickListener(null)
            binding.tvError.setOnClickListener(null)
            binding.playerView.player = null
            player?.release()
            player = null
        }
    }
}
