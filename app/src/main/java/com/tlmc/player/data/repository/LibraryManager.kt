package com.tlmc.player.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tlmc.player.data.model.CustomPlaylist
import com.tlmc.player.data.model.TrackRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地音乐库管理：负责红心收藏、自定义歌单与最近播放历史的持久化。
 */
@Singleton
class LibraryManager @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val FILE_NAME = "tlmc_library.json"
        private const val MAX_HISTORY_SIZE = 100
    }

    private val storageFile = File(context.filesDir, FILE_NAME)
    private val lock = Any()

    private var favoritesList = mutableListOf<TrackRecord>()
    private var playlistsList = mutableListOf<CustomPlaylist>()
    private var historyList = mutableListOf<TrackRecord>()

    private val _favorites = MutableLiveData<List<TrackRecord>>(emptyList())
    val favorites: LiveData<List<TrackRecord>> = _favorites

    private val _playlists = MutableLiveData<List<CustomPlaylist>>(emptyList())
    val playlists: LiveData<List<CustomPlaylist>> = _playlists

    private val _history = MutableLiveData<List<TrackRecord>>(emptyList())
    val history: LiveData<List<TrackRecord>> = _history

    init {
        loadFromDisk()
    }

    fun isFavorite(key: String): Boolean {
        if (key.isBlank()) return false
        synchronized(lock) {
            return favoritesList.any { it.key == key }
        }
    }

    /** 切换红心收藏状态，返回切换后的收藏状态。 */
    fun toggleFavorite(track: TrackRecord): Boolean {
        val nowFavorite: Boolean
        synchronized(lock) {
            val existingIndex = favoritesList.indexOfFirst { it.key == track.key }
            if (existingIndex >= 0) {
                favoritesList.removeAt(existingIndex)
                nowFavorite = false
            } else {
                favoritesList.add(0, track.copy(timestampMs = System.currentTimeMillis()))
                nowFavorite = true
            }
            saveToDiskLocked()
        }
        publishAll()
        return nowFavorite
    }

    fun removeFavorite(key: String) {
        synchronized(lock) {
            if (favoritesList.removeAll { it.key == key }) {
                saveToDiskLocked()
            }
        }
        publishAll()
    }

    /** 批量添加到红心收藏，返回新加入的曲目数量。 */
    fun addFavorites(tracks: List<TrackRecord>): Int {
        if (tracks.isEmpty()) return 0
        var addedCount = 0
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val existingKeys = favoritesList.mapTo(HashSet()) { it.key }
            val toAdd = mutableListOf<TrackRecord>()
            for (track in tracks) {
                if (existingKeys.add(track.key)) {
                    toAdd.add(track.copy(timestampMs = now))
                    addedCount++
                }
            }
            if (toAdd.isNotEmpty()) {
                favoritesList.addAll(0, toAdd)
                saveToDiskLocked()
            }
        }
        if (addedCount > 0) publishAll()
        return addedCount
    }

    fun getFavorites(): List<TrackRecord> = synchronized(lock) { favoritesList.toList() }

    fun getPlaylists(): List<CustomPlaylist> = synchronized(lock) { playlistsList.toList() }

    fun createPlaylist(name: String): CustomPlaylist {
        val trimmed = name.trim().ifEmpty { "未命名歌单" }
        val playlist = CustomPlaylist(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            tracks = emptyList()
        )
        synchronized(lock) {
            playlistsList.add(0, playlist)
            saveToDiskLocked()
        }
        publishAll()
        return playlist
    }

    fun deletePlaylist(playlistId: String) {
        synchronized(lock) {
            if (playlistsList.removeAll { it.id == playlistId }) {
                saveToDiskLocked()
            }
        }
        publishAll()
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            val index = playlistsList.indexOfFirst { it.id == playlistId }
            if (index >= 0) {
                playlistsList[index] = playlistsList[index].copy(name = trimmed)
                saveToDiskLocked()
            }
        }
        publishAll()
    }

    /** 将曲目添加到指定歌单（若已存在则不重复添加），返回是否成功添加。 */
    fun addTrackToPlaylist(playlistId: String, track: TrackRecord): Boolean {
        return addTracksToPlaylist(playlistId, listOf(track)) > 0
    }

    /** 批量将曲目添加到指定歌单（自动去重），返回新加入的曲目数量。 */
    fun addTracksToPlaylist(playlistId: String, tracks: List<TrackRecord>): Int {
        if (tracks.isEmpty()) return 0
        var addedCount = 0
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val index = playlistsList.indexOfFirst { it.id == playlistId }
            if (index < 0) return 0
            val current = playlistsList[index]
            val existingKeys = current.tracks.mapTo(HashSet()) { it.key }
            val toAdd = mutableListOf<TrackRecord>()
            for (track in tracks) {
                if (existingKeys.add(track.key)) {
                    toAdd.add(track.copy(timestampMs = now))
                    addedCount++
                }
            }
            if (toAdd.isNotEmpty()) {
                playlistsList[index] = current.copy(tracks = current.tracks + toAdd)
                saveToDiskLocked()
            }
        }
        if (addedCount > 0) publishAll()
        return addedCount
    }

    fun removeTrackFromPlaylist(playlistId: String, trackKey: String) {
        synchronized(lock) {
            val index = playlistsList.indexOfFirst { it.id == playlistId }
            if (index < 0) return
            val current = playlistsList[index]
            val updated = current.tracks.filterNot { it.key == trackKey }
            if (updated.size != current.tracks.size) {
                playlistsList[index] = current.copy(tracks = updated)
                saveToDiskLocked()
            }
        }
        publishAll()
    }

    fun recordHistory(track: TrackRecord) {
        synchronized(lock) {
            historyList.removeAll { it.key == track.key }
            historyList.add(0, track.copy(timestampMs = System.currentTimeMillis()))
            if (historyList.size > MAX_HISTORY_SIZE) {
                historyList = historyList.take(MAX_HISTORY_SIZE).toMutableList()
            }
            saveToDiskLocked()
        }
        publishAll()
    }

    fun clearHistory() {
        synchronized(lock) {
            historyList.clear()
            saveToDiskLocked()
        }
        publishAll()
    }

    fun getHistory(): List<TrackRecord> = synchronized(lock) { historyList.toList() }

    private fun loadFromDisk() {
        synchronized(lock) {
            if (!storageFile.exists()) {
                publishAll()
                return
            }
            try {
                val root = JSONObject(storageFile.readText())
                favoritesList = parseTrackArray(root.optJSONArray("favorites"))
                historyList = parseTrackArray(root.optJSONArray("history"))
                val playlistsArr = root.optJSONArray("playlists") ?: JSONArray()
                playlistsList = buildList {
                    for (i in 0 until playlistsArr.length()) {
                        playlistsArr.optJSONObject(i)?.let { CustomPlaylist.fromJson(it)?.let(::add) }
                    }
                }.toMutableList()
            } catch (_: Exception) {
                favoritesList = mutableListOf()
                playlistsList = mutableListOf()
                historyList = mutableListOf()
            }
        }
        publishAll()
    }

    private fun parseTrackArray(array: JSONArray?): MutableList<TrackRecord> {
        if (array == null) return mutableListOf()
        return buildList {
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { TrackRecord.fromJson(it)?.let(::add) }
            }
        }.toMutableList()
    }

    private fun saveToDiskLocked() {
        try {
            val root = JSONObject().apply {
                put("favorites", JSONArray().apply { favoritesList.forEach { put(it.toJson()) } })
                put("playlists", JSONArray().apply { playlistsList.forEach { put(it.toJson()) } })
                put("history", JSONArray().apply { historyList.forEach { put(it.toJson()) } })
            }
            storageFile.writeText(root.toString())
        } catch (_: Exception) {
            // 忽略磁盘写入异常
        }
    }

    private fun publishAll() {
        _favorites.postValue(synchronized(lock) { favoritesList.toList() })
        _playlists.postValue(synchronized(lock) { playlistsList.toList() })
        _history.postValue(synchronized(lock) { historyList.toList() })
    }
}
