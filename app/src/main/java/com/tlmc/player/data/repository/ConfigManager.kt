package com.tlmc.player.data.repository

import android.content.SharedPreferences
import androidx.media3.common.C
import com.tlmc.player.data.model.ServerConfig
import org.json.JSONArray
import org.json.JSONObject
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
        private const val KEY_AUDIO_QUEUE_STATE = "audio_queue_state"
        private const val KEY_AUDIO_POSITION_PREFIX = "audio_pos_"
    }

    data class SavedAudioItem(
        val mediaId: String,
        val uri: String,
        val title: String,
        val artist: String,
        val albumTitle: String,
        val trackNumber: Int?,
        val clipStartMs: Long,
        val clipEndMs: Long
    )

    data class AudioQueueState(
        val items: List<SavedAudioItem>,
        val currentIndex: Int
    )

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

    fun saveAudioQueueState(items: List<SavedAudioItem>, currentIndex: Int) {
        if (items.isEmpty()) {
            clearAudioQueueState()
            return
        }

        val queueJson = JSONObject().apply {
            put("currentIndex", currentIndex.coerceAtLeast(0))
            put("items", JSONArray().apply {
                items.forEach { item ->
                    put(JSONObject().apply {
                        put("mediaId", item.mediaId)
                        put("uri", item.uri)
                        put("title", item.title)
                        put("artist", item.artist)
                        put("albumTitle", item.albumTitle)
                        if (item.trackNumber != null) {
                            put("trackNumber", item.trackNumber)
                        }
                        put("clipStartMs", item.clipStartMs)
                        put("clipEndMs", item.clipEndMs)
                    })
                }
            })
        }.toString()

        prefs.edit().putString(KEY_AUDIO_QUEUE_STATE, queueJson).apply()
    }

    fun getAudioQueueState(): AudioQueueState? {
        val queueJson = prefs.getString(KEY_AUDIO_QUEUE_STATE, null) ?: return null
        return try {
            val root = JSONObject(queueJson)
            val itemsJson = root.optJSONArray("items") ?: return null
            val items = buildList {
                for (index in 0 until itemsJson.length()) {
                    val item = itemsJson.optJSONObject(index) ?: continue
                    val uri = item.optString("uri")
                    if (uri.isBlank()) continue
                    add(
                        SavedAudioItem(
                            mediaId = item.optString("mediaId"),
                            uri = uri,
                            title = item.optString("title"),
                            artist = item.optString("artist"),
                            albumTitle = item.optString("albumTitle"),
                            trackNumber = if (item.has("trackNumber")) item.optInt("trackNumber") else null,
                            clipStartMs = item.optLong("clipStartMs", 0L).coerceAtLeast(0L),
                            clipEndMs = item.optLong("clipEndMs", C.TIME_END_OF_SOURCE)
                        )
                    )
                }
            }

            if (items.isEmpty()) {
                null
            } else {
                AudioQueueState(
                    items = items,
                    currentIndex = root.optInt("currentIndex", 0)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clearAudioQueueState() {
        prefs.edit().remove(KEY_AUDIO_QUEUE_STATE).apply()
    }

    fun saveAudioPlaybackPosition(playbackKey: String, positionMs: Long) {
        if (playbackKey.isBlank()) return
        prefs.edit()
            .putLong(audioPositionKey(playbackKey), positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun getAudioPlaybackPosition(playbackKey: String): Long {
        if (playbackKey.isBlank()) return 0L
        return prefs.getLong(audioPositionKey(playbackKey), 0L)
    }

    fun clearAudioPlaybackPosition(playbackKey: String) {
        if (playbackKey.isBlank()) return
        prefs.edit().remove(audioPositionKey(playbackKey)).apply()
    }

    private fun videoPositionKey(path: String): String {
        val encodedPath = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(path.toByteArray(Charsets.UTF_8))
        return "$KEY_VIDEO_POSITION_PREFIX$encodedPath"
    }

    private fun audioPositionKey(playbackKey: String): String {
        val encodedKey = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(playbackKey.toByteArray(Charsets.UTF_8))
        return "$KEY_AUDIO_POSITION_PREFIX$encodedKey"
    }
}

