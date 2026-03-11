package com.tlmc.player.ui.player

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.tlmc.player.R
import com.tlmc.player.data.model.CueTrack
import com.tlmc.player.databinding.ActivityPlayerBinding
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

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "Unknown"
        val cuePath = intent.getStringExtra(EXTRA_CUE_PATH)
        val directoryPath = intent.getStringExtra(EXTRA_DIRECTORY_PATH) ?: "/"

        supportActionBar?.title = fileName

        setupTrackList()
        setupControls()
        observeViewModel()

        viewModel.initialize(filePath, cuePath, directoryPath)
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
        })

        // If ViewModel already has media items ready, set them
        viewModel.mediaItems.value?.let { items ->
            if (items.isNotEmpty()) {
                setMediaItems(items)
            }
        }

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

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.currentTime.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
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
            if (items.isNotEmpty()) {
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
            if (duration > 0) {
                binding.seekBar.max = duration.toInt()
                binding.seekBar.progress = position.toInt()
                binding.currentTime.text = formatTime(position)
                binding.totalTime.text = formatTime(duration)
            }
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

            // Highlight current track in list
            trackAdapter.setCurrentTrack(controller.currentMediaItemIndex)
        }
    }

    private fun updatePlaybackState(state: Int) {
        when (state) {
            Player.STATE_BUFFERING -> binding.progressBar.visibility = View.VISIBLE
            else -> binding.progressBar.visibility = View.GONE
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}

