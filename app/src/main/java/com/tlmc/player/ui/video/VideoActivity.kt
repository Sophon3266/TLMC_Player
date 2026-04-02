package com.tlmc.player.ui.video

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tlmc.player.data.repository.ConfigManager
import com.tlmc.player.data.repository.WebDavRepository
import com.tlmc.player.databinding.ActivityVideoBinding
import com.tlmc.player.ui.player.PlayerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VideoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_FILE_NAME = "extra_file_name"
        private const val RESUME_CLEAR_THRESHOLD_MS = 3_000L
    }

    @Inject
    lateinit var repository: WebDavRepository

    @Inject
    lateinit var configManager: ConfigManager

    private lateinit var binding: ActivityVideoBinding
    private var player: ExoPlayer? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private lateinit var filePath: String
    private var shouldResumeInternalAudio = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run {
            finish()
            return
        }
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "Video"

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = fileName
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupWindowInsets()
        setupSpeedControl()
        setupControllerVisibility()
        connectToPlayerService()
        initializePlayer()
    }

    override fun onPause() {
        super.onPause()
        persistPlaybackPosition()
    }

    override fun onDestroy() {
        persistPlaybackPosition()
        resumeInternalAudioIfNeeded()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        controllerFuture = null
        releasePlayer()
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) {
            if (player?.isPlaying == true) {
                enterPipMode()
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        binding.toolbar.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        binding.btnSpeed.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        if (!isInPictureInPictureMode) {
            applyImmersiveMode()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isInPictureInPictureMode) {
            applyImmersiveMode()
        }
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        val dataSourceFactory = OkHttpDataSource.Factory(repository.getAuthenticatedOkHttpClient())
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .build()
            .also { exoPlayer ->
                binding.playerView.player = exoPlayer
                binding.playerView.controllerAutoShow = true

                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                binding.progressBar.visibility = View.VISIBLE
                                binding.errorText.visibility = View.GONE
                            }

                            Player.STATE_READY -> {
                                binding.progressBar.visibility = View.GONE
                                binding.errorText.visibility = View.GONE
                            }

                            Player.STATE_ENDED -> {
                                binding.progressBar.visibility = View.GONE
                                configManager.clearVideoPlaybackPosition(filePath)
                            }

                            else -> Unit
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        binding.progressBar.visibility = View.GONE
                        binding.errorText.visibility = View.VISIBLE
                        binding.errorText.text = "播放失败: ${error.errorCodeName}"
                        Toast.makeText(this@VideoActivity, "视频播放失败", Toast.LENGTH_SHORT).show()
                    }
                })

                val mediaUrl = repository.getFileUrl(filePath)
                exoPlayer.setMediaItem(MediaItem.fromUri(mediaUrl))

                val savedPosition = configManager.getVideoPlaybackPosition(filePath)
                if (savedPosition > 0) {
                    exoPlayer.seekTo(savedPosition)
                }

                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
    }

    private fun connectToPlayerService() {
        val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync().also { future ->
            future.addListener({
                try {
                    val controller = future.get()
                    mediaController = controller
                    pauseInternalAudioIfNeeded(controller)
                } catch (_: Exception) {
                    mediaController = null
                }
            }, MoreExecutors.directExecutor())
        }
    }

    private fun pauseInternalAudioIfNeeded(controller: MediaController) {
        if (shouldResumeInternalAudio) return
        if (controller.mediaItemCount == 0 || !controller.isPlaying) return
        shouldResumeInternalAudio = true
        controller.pause()
    }

    private fun resumeInternalAudioIfNeeded() {
        if (!shouldResumeInternalAudio || isInPictureInPictureMode) return
        mediaController?.takeIf { it.mediaItemCount > 0 }?.play()
        shouldResumeInternalAudio = false
    }

    private fun setupControllerVisibility() {
        val visibilityListener = PlayerView.ControllerVisibilityListener { visibility ->
            if (isInPictureInPictureMode) {
                return@ControllerVisibilityListener
            }
            val controlsVisible = visibility == View.VISIBLE
            binding.toolbar.visibility = if (controlsVisible) View.VISIBLE else View.GONE
            binding.btnSpeed.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        }
        binding.playerView.setControllerVisibilityListener(visibilityListener)
    }

    private fun setupSpeedControl() {
        val speedOptions = arrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        binding.btnSpeed.setOnClickListener {
            val currentSpeed = player?.playbackParameters?.speed ?: 1.0f
            val selectedIndex = speedOptions.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.01f }
                .coerceAtLeast(0)
            val labels = speedOptions.map { "${it}x" }.toTypedArray()

            MaterialAlertDialogBuilder(this)
                .setTitle("播放速度")
                .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                    val speed = speedOptions[which]
                    player?.playbackParameters = PlaybackParameters(speed)
                    binding.btnSpeed.text = "${speed}x"
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun persistPlaybackPosition() {
        val exoPlayer = player ?: return
        val duration = exoPlayer.duration
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)

        if (duration > 0 && position >= duration - RESUME_CLEAR_THRESHOLD_MS) {
            configManager.clearVideoPlaybackPosition(filePath)
            return
        }
        configManager.saveVideoPlaybackPosition(filePath, position)
    }

    private fun releasePlayer() {
        binding.playerView.player = null
        player?.release()
        player = null
    }

    private fun setupWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            binding.toolbar.setPadding(
                binding.toolbar.paddingLeft,
                statusBars.top,
                binding.toolbar.paddingRight,
                binding.toolbar.paddingBottom
            )
            ViewCompat.onApplyWindowInsets(view, insets)
        }
    }

    private fun applyImmersiveMode() {
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val videoSize = player?.videoSize
        val aspectRatio = if (videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
            Rational(videoSize.width, videoSize.height)
        } else {
            Rational(16, 9)
        }

        val params = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .build()

        enterPictureInPictureMode(params)
    }
}
