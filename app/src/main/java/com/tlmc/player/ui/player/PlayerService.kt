package com.tlmc.player.ui.player

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.tlmc.player.data.repository.ConfigManager
import com.tlmc.player.data.model.ServerConfig
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.Credentials
import okhttp3.OkHttpClient
import javax.inject.Inject

@AndroidEntryPoint
class PlayerService : MediaSessionService() {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var configManager: ConfigManager

    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val saveProgressRunnable = object : Runnable {
        override fun run() {
            mediaSession?.player?.let { savePlaybackState(it) }
            handler.postDelayed(this, SAVE_PROGRESS_INTERVAL_MS)
        }
    }
    private var skipNextAutomaticPositionRestore = false

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val config = configManager.getConfig()

        // Create authenticated OkHttpClient for media streaming
        val authenticatedClient = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", Credentials.basic(config.username, config.password))
                    .build()
                chain.proceed(request)
            }
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(authenticatedClient)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, false)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                saveQueueState(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (skipNextAutomaticPositionRestore) {
                    skipNextAutomaticPositionRestore = false
                } else {
                    restoreSavedPositionForCurrentItem(player)
                }
                savePlaybackState(player)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    clearCompletedPositionForIndex(player, oldPosition.mediaItemIndex)
                }
                savePlaybackState(player)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    clearCompletedPositionForCurrentItem(player)
                }
                savePlaybackState(player)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                saveQueueState(player)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                saveQueueState(player)
            }
        })

        restoreQueueState(player)

        mediaSession = MediaSession.Builder(this, player)
            .build()

        handler.post(saveProgressRunnable)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(saveProgressRunnable)
        mediaSession?.run {
            savePlaybackState(player)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun savePlaybackState(player: Player) {
        saveQueueState(player)

        val mediaItem = player.currentMediaItem ?: return
        val playbackKey = mediaItem.toPlaybackKey()
        if (playbackKey.isBlank()) return

        val position = player.currentPosition.coerceAtLeast(0L)
        configManager.saveAudioPlaybackPosition(playbackKey, position)
    }

    private fun saveQueueState(player: Player) {
        if (player.mediaItemCount == 0) {
            configManager.clearAudioQueueState()
            return
        }

        val items = buildList {
            for (index in 0 until player.mediaItemCount) {
                add(player.getMediaItemAt(index).toSavedAudioItem())
            }
        }

        configManager.saveAudioQueueState(
            items = items,
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        )
    }

    private fun restoreQueueState(player: ExoPlayer) {
        val queueState = configManager.getAudioQueueState() ?: return
        if (queueState.items.isEmpty()) return

        val mediaItems = queueState.items.map { it.toMediaItem() }
        val currentIndex = queueState.currentIndex.coerceIn(0, mediaItems.lastIndex)
        val currentItem = mediaItems[currentIndex]
        val savedPosition = configManager.getAudioPlaybackPosition(currentItem.toPlaybackKey())

        skipNextAutomaticPositionRestore = savedPosition > 0L
        player.setMediaItems(mediaItems, currentIndex, savedPosition)
        player.prepare()
    }

    private fun restoreSavedPositionForCurrentItem(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        if (player.currentPosition > POSITION_RESTORE_MAX_CURRENT_MS) return

        val savedPosition = configManager.getAudioPlaybackPosition(mediaItem.toPlaybackKey())
        if (savedPosition > 0L) {
            player.seekTo(savedPosition)
        }
    }

    private fun clearCompletedPositionForCurrentItem(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        if (player.playbackState == Player.STATE_ENDED) {
            configManager.clearAudioPlaybackPosition(mediaItem.toPlaybackKey())
        }
    }

    private fun clearCompletedPositionForIndex(player: Player, mediaItemIndex: Int) {
        if (mediaItemIndex !in 0 until player.mediaItemCount) return
        val mediaItem = player.getMediaItemAt(mediaItemIndex)
        configManager.clearAudioPlaybackPosition(mediaItem.toPlaybackKey())
    }

    private fun MediaItem.toSavedAudioItem(): ConfigManager.SavedAudioItem {
        val clip = clippingConfiguration
        return ConfigManager.SavedAudioItem(
            mediaId = mediaId,
            uri = localConfiguration?.uri?.toString().orEmpty(),
            title = mediaMetadata.title?.toString().orEmpty(),
            artist = mediaMetadata.artist?.toString().orEmpty(),
            albumTitle = mediaMetadata.albumTitle?.toString().orEmpty(),
            trackNumber = mediaMetadata.trackNumber,
            clipStartMs = clip.startPositionMs,
            clipEndMs = clip.endPositionMs
        )
    }

    private fun ConfigManager.SavedAudioItem.toMediaItem(): MediaItem {
        val builder = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(albumTitle)
                    .apply {
                        trackNumber?.let { setTrackNumber(it) }
                    }
                    .build()
            )

        if (clipStartMs > 0L || clipEndMs != C.TIME_END_OF_SOURCE) {
            val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clipStartMs.coerceAtLeast(0L))
            if (clipEndMs != C.TIME_END_OF_SOURCE) {
                clippingBuilder.setEndPositionMs(clipEndMs)
            }
            builder.setClippingConfiguration(clippingBuilder.build())
        }

        return builder.build()
    }

    private fun MediaItem.toPlaybackKey(): String {
        val clip = clippingConfiguration
        val base = if (mediaId.isNotBlank()) mediaId else localConfiguration?.uri?.toString().orEmpty()
        return listOf(base, clip.startPositionMs.toString(), clip.endPositionMs.toString()).joinToString("|")
    }

    companion object {
        private const val SAVE_PROGRESS_INTERVAL_MS = 5_000L
        private const val POSITION_RESTORE_MAX_CURRENT_MS = 1_000L
    }
}

