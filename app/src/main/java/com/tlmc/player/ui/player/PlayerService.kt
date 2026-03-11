package com.tlmc.player.ui.player

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .build()
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
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

