package com.tlmc.player.ui.player

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.tlmc.player.data.model.CueSheet
import com.tlmc.player.data.model.CueTrack
import com.tlmc.player.data.repository.WebDavRepository
import com.tlmc.player.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: WebDavRepository
) : ViewModel() {

    private val _tracks = MutableLiveData<List<CueTrack>>(emptyList())
    val tracks: LiveData<List<CueTrack>> = _tracks

    private val _mediaItems = MutableLiveData<List<MediaItem>>(emptyList())
    val mediaItems: LiveData<List<MediaItem>> = _mediaItems

    private val _albumTitle = MutableLiveData("")
    val albumTitle: LiveData<String> = _albumTitle

    private val _albumArtist = MutableLiveData("")
    val albumArtist: LiveData<String> = _albumArtist

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun initialize(filePath: String?, cuePath: String?, directoryPath: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                when {
                    // CUE file specified
                    cuePath != null -> loadWithCue(cuePath, filePath, directoryPath)
                    // Audio file only
                    filePath != null -> loadSingleFile(filePath)
                    else -> _error.value = "无效的文件路径"
                }
            } catch (e: Exception) {
                _error.value = "加载失败: ${e.message}"
            }

            _isLoading.value = false
        }
    }

    private suspend fun loadWithCue(cuePath: String, audioFilePath: String?, directoryPath: String) {
        val cueResult = repository.loadCueSheet(cuePath)
        cueResult.onSuccess { cueSheet ->
            _albumTitle.value = cueSheet.title
            _albumArtist.value = cueSheet.performer
            _tracks.value = cueSheet.tracks

            // Determine audio file URL
            val audioPath = if (audioFilePath != null) {
                audioFilePath
            } else {
                // Find audio file referenced in CUE
                val dir = FileUtils.getDirectoryPath(cuePath)
                "$dir/${cueSheet.file}"
            }

            val audioUrl = repository.getFileUrl(audioPath)
            val items = buildCueMediaItems(cueSheet, audioUrl)
            _mediaItems.value = items
        }.onFailure { e ->
            _error.value = "CUE 解析失败: ${e.message}"
            // Fall back to single file if audio path is available
            if (audioFilePath != null) {
                loadSingleFile(audioFilePath)
            }
        }
    }

    private fun loadSingleFile(filePath: String) {
        val url = repository.getFileUrl(filePath)
        val fileName = filePath.substringAfterLast('/')

        _albumTitle.value = fileName
        _albumArtist.value = ""
        _tracks.value = emptyList()

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(fileName)
                    .build()
            )
            .build()

        _mediaItems.value = listOf(mediaItem)
    }

    private fun buildCueMediaItems(cueSheet: CueSheet, audioUrl: String): List<MediaItem> {
        return cueSheet.tracks.map { track ->
            val builder = MediaItem.Builder()
                .setUri(audioUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.performer)
                        .setAlbumTitle(cueSheet.title)
                        .setTrackNumber(track.number)
                        .build()
                )

            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(track.startTimeMs)
            if (track.endTimeMs > 0) {
                clipping.setEndPositionMs(track.endTimeMs)
            }
            builder.setClippingConfiguration(clipping.build())

            builder.build()
        }
    }
}

