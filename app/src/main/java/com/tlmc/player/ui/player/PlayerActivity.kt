package com.tlmc.player.ui.player

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.tlmc.player.R
import com.tlmc.player.data.model.PlayMode
import com.tlmc.player.databinding.ActivityPlayerBinding
import com.tlmc.player.ui.browser.BrowserPlaylistAdapter
import com.tlmc.player.ui.browser.PlaylistItem
import com.tlmc.player.util.LrcParser
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_CUE_PATH = "extra_cue_path"
        const val EXTRA_DIRECTORY_PATH = "extra_directory_path"
    }

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private lateinit var trackAdapter: TrackAdapter
    private lateinit var lyricsAdapter: LyricsAdapter
    private var currentLyricsLineIndex = -1

    private var currentPlayMode: PlayMode = PlayMode.SEQUENTIAL
    private var isSeeking = false

    // true when opened from CUE file (needs to load & set media items)
    private var isCueMode = false

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val cuePath = intent.getStringExtra(EXTRA_CUE_PATH)
        isCueMode = cuePath != null

        if (isCueMode) {
            val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
            val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "Unknown"
            val directoryPath = intent.getStringExtra(EXTRA_DIRECTORY_PATH) ?: "/"
            supportActionBar?.title = fileName
            viewModel.initialize(filePath, cuePath, directoryPath)
        } else {
            supportActionBar?.title = "正在播放"
        }

        setupTrackList()
        setupControls()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        connectToService()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateProgressRunnable)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }

    private fun connectToService() {
        val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync().also { future ->
            future.addListener({
                try {
                    mediaController = future.get()
                    onControllerConnected()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, MoreExecutors.directExecutor())
        }
    }

    private fun onControllerConnected() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                runOnUiThread { updatePlayPauseButton(isPlaying) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                runOnUiThread { updateCurrentTrackInfo() }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                runOnUiThread { updatePlaybackState(playbackState) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                runOnUiThread { syncPlayModeFromController() }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                runOnUiThread { syncPlayModeFromController() }
            }
        })

        if (isCueMode) {
            // CUE mode: if ViewModel already has media items, set them
            viewModel.mediaItems.value?.let { items ->
                if (items.isNotEmpty()) {
                    setMediaItems(items)
                }
            }
        } else {
            // Pure UI mode: just read current state from controller
            runOnUiThread {
                updateCurrentTrackInfo()
                mediaController?.let { updatePlayPauseButton(it.isPlaying) }
            }
        }

        runOnUiThread { syncPlayModeFromController() }
        handler.post(updateProgressRunnable)
    }

    private fun setupTrackList() {
        trackAdapter = TrackAdapter { position ->
            mediaController?.let { controller ->
                controller.seekTo(position, 0)
                controller.play()
            }
        }
        binding.trackRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlayerActivity)
            adapter = trackAdapter
        }

        // Setup lyrics RecyclerView
        lyricsAdapter = LyricsAdapter { lineIndex ->
            // Click on a lyrics line to seek to that time
            val lines = viewModel.lyricsLines.value ?: return@LyricsAdapter
            if (lineIndex in lines.indices) {
                mediaController?.seekTo(lines[lineIndex].timeMs)
            }
        }
        binding.lyricsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlayerActivity)
            adapter = lyricsAdapter
        }
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            mediaController?.let { controller ->
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    controller.play()
                }
            }
        }

        binding.btnPrevious.setOnClickListener {
            mediaController?.seekToPreviousMediaItem()
        }

        binding.btnNext.setOnClickListener {
            mediaController?.seekToNextMediaItem()
        }

        binding.btnPlayMode.setOnClickListener {
            togglePlayMode()
        }

        binding.btnPlaylist.setOnClickListener {
            showPlaylistDialog()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.currentTime.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                seekBar?.let {
                    mediaController?.seekTo(it.progress.toLong())
                }
            }
        })
    }

    private fun observeViewModel() {
        viewModel.tracks.observe(this) { tracks ->
            if (tracks.isNotEmpty()) {
                binding.trackRecyclerView.visibility = View.VISIBLE
                trackAdapter.submitList(tracks)
            } else {
                binding.trackRecyclerView.visibility = View.GONE
            }
        }

        viewModel.mediaItems.observe(this) { items ->
            if (isCueMode && items.isNotEmpty()) {
                mediaController?.let { setMediaItems(items) }
            }
        }

        viewModel.albumTitle.observe(this) { title ->
            binding.albumTitle.text = title
        }

        viewModel.albumArtist.observe(this) { artist ->
            binding.albumArtist.text = artist
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                binding.errorText.text = it
                binding.errorText.visibility = View.VISIBLE
            } ?: run {
                binding.errorText.visibility = View.GONE
            }
        }

        // Observe lyrics
        viewModel.lyricsLines.observe(this) { lines ->
            if (lines.isNotEmpty()) {
                binding.lyricsRecyclerView.visibility = View.VISIBLE
                lyricsAdapter.setLyrics(lines)
                currentLyricsLineIndex = -1
            } else {
                binding.lyricsRecyclerView.visibility = View.GONE
                lyricsAdapter.setLyrics(emptyList())
                currentLyricsLineIndex = -1
            }
        }
    }

    private fun setMediaItems(items: List<MediaItem>) {
        mediaController?.let { controller ->
            controller.setMediaItems(items)
            controller.prepare()
            controller.play()
        }
    }

    private fun updateProgress() {
        mediaController?.let { controller ->
            val position = controller.currentPosition
            val duration = controller.duration
            if (duration > 0 && !isSeeking) {
                binding.seekBar.max = duration.toInt()
                binding.seekBar.progress = position.toInt()
                binding.currentTime.text = formatTime(position)
                binding.totalTime.text = formatTime(duration)
            }
            // Update lyrics highlight
            updateLyricsPosition(position)
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun updateCurrentTrackInfo() {
        mediaController?.let { controller ->
            val mediaItem = controller.currentMediaItem
            val title = mediaItem?.mediaMetadata?.title?.toString() ?: ""
            val artist = mediaItem?.mediaMetadata?.artist?.toString() ?: ""
            binding.trackTitle.text = title.ifEmpty { "Unknown" }
            binding.trackArtist.text = artist

            // Update toolbar title
            if (!isCueMode) {
                supportActionBar?.title = title.ifEmpty { "正在播放" }
            }

            // Highlight current track in list (CUE mode)
            trackAdapter.setCurrentTrack(controller.currentMediaItemIndex)

            // Load lyrics for current track
            viewModel.loadLyricsFromMediaItem(mediaItem)
        }
    }

    private fun updatePlaybackState(state: Int) {
        when (state) {
            Player.STATE_BUFFERING -> binding.progressBar.visibility = View.VISIBLE
            else -> binding.progressBar.visibility = View.GONE
        }
    }

    // ==================== Play Mode ====================

    private fun togglePlayMode() {
        currentPlayMode = currentPlayMode.next()
        applyPlayModeToController()
        updatePlayModeIcon()
        val modeName = when (currentPlayMode) {
            PlayMode.SEQUENTIAL -> "顺序播放"
            PlayMode.REPEAT_ALL -> "列表循环"
            PlayMode.REPEAT_ONE -> "单曲循环"
            PlayMode.SHUFFLE -> "随机播放"
        }
        Toast.makeText(this, modeName, Toast.LENGTH_SHORT).show()
    }

    private fun applyPlayModeToController() {
        mediaController?.let { controller ->
            when (currentPlayMode) {
                PlayMode.SEQUENTIAL -> {
                    controller.repeatMode = Player.REPEAT_MODE_OFF
                    controller.shuffleModeEnabled = false
                }
                PlayMode.REPEAT_ALL -> {
                    controller.repeatMode = Player.REPEAT_MODE_ALL
                    controller.shuffleModeEnabled = false
                }
                PlayMode.REPEAT_ONE -> {
                    controller.repeatMode = Player.REPEAT_MODE_ONE
                    controller.shuffleModeEnabled = false
                }
                PlayMode.SHUFFLE -> {
                    controller.repeatMode = Player.REPEAT_MODE_ALL
                    controller.shuffleModeEnabled = true
                }
            }
        }
    }

    private fun syncPlayModeFromController() {
        mediaController?.let { controller ->
            currentPlayMode = when {
                controller.shuffleModeEnabled -> PlayMode.SHUFFLE
                controller.repeatMode == Player.REPEAT_MODE_ONE -> PlayMode.REPEAT_ONE
                controller.repeatMode == Player.REPEAT_MODE_ALL -> PlayMode.REPEAT_ALL
                else -> PlayMode.SEQUENTIAL
            }
            updatePlayModeIcon()
        }
    }

    private fun updatePlayModeIcon() {
        val iconRes = when (currentPlayMode) {
            PlayMode.SEQUENTIAL -> R.drawable.ic_sequential
            PlayMode.REPEAT_ALL -> R.drawable.ic_repeat
            PlayMode.REPEAT_ONE -> R.drawable.ic_repeat_one
            PlayMode.SHUFFLE -> R.drawable.ic_shuffle
        }
        binding.btnPlayMode.setImageResource(iconRes)
    }

    // ==================== Playlist Dialog ====================

    private fun showPlaylistDialog() {
        val controller = mediaController ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_playlist, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.playlistRecyclerView)
        val emptyView = dialogView.findViewById<TextView>(R.id.playlistEmpty)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("播放列表 (${controller.mediaItemCount})")
            .setView(dialogView)
            .setPositiveButton("关闭", null)
            .create()

        val playlistAdapter = BrowserPlaylistAdapter(
            onItemClick = { index ->
                controller.seekTo(index, 0)
                controller.play()
                dialog.dismiss()
            },
            onRemoveClick = { index ->
                controller.removeMediaItem(index)
                refreshPlaylistDialog(controller, recyclerView, emptyView)
                dialog.setTitle("播放列表 (${controller.mediaItemCount})")
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlayerActivity)
            adapter = playlistAdapter
        }

        refreshPlaylistDialog(controller, recyclerView, emptyView)
        dialog.show()
    }

    private fun refreshPlaylistDialog(
        controller: MediaController,
        recyclerView: RecyclerView,
        emptyView: TextView
    ) {
        val items = mutableListOf<PlaylistItem>()
        for (i in 0 until controller.mediaItemCount) {
            val mediaItem = controller.getMediaItemAt(i)
            items.add(
                PlaylistItem(
                    index = i,
                    title = mediaItem.mediaMetadata.title?.toString() ?: "未知",
                    artist = mediaItem.mediaMetadata.artist?.toString() ?: "",
                    isCurrent = i == controller.currentMediaItemIndex
                )
            )
        }

        if (items.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }

        (recyclerView.adapter as? BrowserPlaylistAdapter)?.submitList(items)
    }

    // ==================== Lyrics ====================

    private fun updateLyricsPosition(positionMs: Long) {
        val lines = viewModel.lyricsLines.value
        if (lines.isNullOrEmpty()) return

        val newIndex = LrcParser.findCurrentLineIndex(lines, positionMs)
        if (newIndex != currentLyricsLineIndex) {
            currentLyricsLineIndex = newIndex
            lyricsAdapter.setCurrentLine(newIndex)

            // Smooth scroll to keep the current line centered
            if (newIndex >= 0) {
                val layoutManager = binding.lyricsRecyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.let { lm ->
                    // Scroll so the current line is roughly in the center of the view
                    val recyclerHeight = binding.lyricsRecyclerView.height
                    val offset = recyclerHeight / 2 - 40 // approximate half-line height offset
                    lm.scrollToPositionWithOffset(newIndex, offset)
                }
            }
        }
    }

    // ==================== Utility ====================

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
