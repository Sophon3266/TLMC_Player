package com.tlmc.player.data.repository

import android.content.SharedPreferences
import com.tlmc.player.data.model.ServerConfig
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigManager @Inject constructor(
    private val prefs: SharedPreferences
) {
    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_VIDEO_POSITION_PREFIX = "video_pos_"
    }

    fun getConfig(): ServerConfig {
        return ServerConfig(
            url = prefs.getString(KEY_SERVER_URL, ServerConfig().url) ?: ServerConfig().url,
            username = prefs.getString(KEY_USERNAME, ServerConfig().username) ?: ServerConfig().username,
            password = prefs.getString(KEY_PASSWORD, ServerConfig().password) ?: ServerConfig().password
        )
    }

    fun saveConfig(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_SERVER_URL, config.url)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .apply()
    }

    fun isConfigured(): Boolean {
        return prefs.contains(KEY_SERVER_URL)
    }

    fun saveVideoPlaybackPosition(path: String, positionMs: Long) {
        val key = videoPositionKey(path)
        prefs.edit().putLong(key, positionMs.coerceAtLeast(0L)).apply()
    }

    fun getVideoPlaybackPosition(path: String): Long {
        return prefs.getLong(videoPositionKey(path), 0L)
    }

    fun clearVideoPlaybackPosition(path: String) {
        prefs.edit().remove(videoPositionKey(path)).apply()
    }

    private fun videoPositionKey(path: String): String {
        val encodedPath = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(path.toByteArray(Charsets.UTF_8))
        return "$KEY_VIDEO_POSITION_PREFIX$encodedPath"
    }
}

