package com.tlmc.player.data.model

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

/**
 * 可序列化的曲目记录，用于红心收藏、自定义歌单与播放历史。
 */
data class TrackRecord(
    val key: String,
    val mediaId: String,
    val uri: String,
    val title: String,
    val artist: String = "",
    val albumTitle: String = "",
    val trackNumber: Int? = null,
    val clipStartMs: Long = 0L,
    val clipEndMs: Long = C.TIME_END_OF_SOURCE,
    val directoryPath: String = "",
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun toMediaItem(): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title.ifEmpty { "Unknown" })
        if (artist.isNotEmpty()) metadataBuilder.setArtist(artist)
        if (albumTitle.isNotEmpty()) metadataBuilder.setAlbumTitle(albumTitle)
        if (trackNumber != null && trackNumber > 0) metadataBuilder.setTrackNumber(trackNumber)
        if (directoryPath.isNotEmpty()) {
            metadataBuilder.setExtras(Bundle().apply {
                putString(KEY_DIRECTORY_PATH, directoryPath)
            })
        }

        val builder = MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setMediaMetadata(metadataBuilder.build())

        if (mediaId.isNotEmpty()) {
            builder.setMediaId(mediaId)
        }
        if (clipStartMs > 0L || (clipEndMs != C.TIME_END_OF_SOURCE && clipEndMs > clipStartMs)) {
            val clipBuilder = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clipStartMs.coerceAtLeast(0L))
            if (clipEndMs != C.TIME_END_OF_SOURCE && clipEndMs > clipStartMs) {
                clipBuilder.setEndPositionMs(clipEndMs)
            }
            builder.setClippingConfiguration(clipBuilder.build())
        }
        return builder.build()
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("mediaId", mediaId)
        put("uri", uri)
        put("title", title)
        put("artist", artist)
        put("albumTitle", albumTitle)
        if (trackNumber != null) put("trackNumber", trackNumber)
        put("clipStartMs", clipStartMs)
        put("clipEndMs", clipEndMs)
        if (directoryPath.isNotEmpty()) put("directoryPath", directoryPath)
        put("timestampMs", timestampMs)
    }

    companion object {
        private const val KEY_DIRECTORY_PATH = "tlmc_directory_path"

        fun buildKey(mediaItem: MediaItem): String {
            val mediaId = mediaItem.mediaId
            if (mediaId.isNotEmpty()) return mediaId
            val uri = mediaItem.localConfiguration?.uri?.toString() ?: ""
            val clip = mediaItem.clippingConfiguration
            val title = mediaItem.mediaMetadata.title?.toString() ?: ""
            return "$uri|${clip.startPositionMs}|${clip.endPositionMs}|$title"
        }

        fun fromMediaItem(mediaItem: MediaItem): TrackRecord? {
            val uri = mediaItem.localConfiguration?.uri?.toString() ?: return null
            val clip = mediaItem.clippingConfiguration
            val meta = mediaItem.mediaMetadata
            val dirPath = meta.extras?.getString(KEY_DIRECTORY_PATH)
                ?: mediaItem.mediaId.takeIf { it.startsWith("/") }?.substringBeforeLast('/', "")
                ?: ""
            return TrackRecord(
                key = buildKey(mediaItem),
                mediaId = mediaItem.mediaId,
                uri = uri,
                title = meta.title?.toString()?.ifEmpty { "Unknown" } ?: "Unknown",
                artist = meta.artist?.toString() ?: "",
                albumTitle = meta.albumTitle?.toString() ?: "",
                trackNumber = meta.trackNumber,
                clipStartMs = clip.startPositionMs,
                clipEndMs = clip.endPositionMs,
                directoryPath = dirPath,
                timestampMs = System.currentTimeMillis()
            )
        }

        fun fromJson(obj: JSONObject): TrackRecord? {
            val uri = obj.optString("uri")
            if (uri.isBlank()) return null
            val key = obj.optString("key").ifBlank { uri }
            return TrackRecord(
                key = key,
                mediaId = obj.optString("mediaId"),
                uri = uri,
                title = obj.optString("title", "Unknown"),
                artist = obj.optString("artist"),
                albumTitle = obj.optString("albumTitle"),
                trackNumber = if (obj.has("trackNumber")) obj.optInt("trackNumber") else null,
                clipStartMs = obj.optLong("clipStartMs", 0L).coerceAtLeast(0L),
                clipEndMs = obj.optLong("clipEndMs", C.TIME_END_OF_SOURCE),
                directoryPath = obj.optString("directoryPath"),
                timestampMs = obj.optLong("timestampMs", 0L)
            )
        }
    }
}

data class CustomPlaylist(
    val id: String,
    val name: String,
    val tracks: List<TrackRecord>,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAtMs", createdAtMs)
        put("tracks", JSONArray().apply {
            tracks.forEach { put(it.toJson()) }
        })
    }

    companion object {
        fun fromJson(obj: JSONObject): CustomPlaylist? {
            val id = obj.optString("id")
            val name = obj.optString("name")
            if (id.isBlank() || name.isBlank()) return null
            val tracksArray = obj.optJSONArray("tracks") ?: JSONArray()
            val tracks = buildList {
                for (i in 0 until tracksArray.length()) {
                    tracksArray.optJSONObject(i)?.let { TrackRecord.fromJson(it)?.let(::add) }
                }
            }
            return CustomPlaylist(
                id = id,
                name = name,
                tracks = tracks,
                createdAtMs = obj.optLong("createdAtMs", 0L)
            )
        }
    }
}
