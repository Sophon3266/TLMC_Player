package com.tlmc.player.data.repository

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import com.bumptech.glide.Glide
import com.tlmc.player.data.model.WebDavFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * 缓存统计、清理及专辑目录批量离线下载管理。
 */
@OptIn(UnstableApi::class)
@Singleton
class OfflineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WebDavRepository,
    private val mediaCache: SimpleCache
) {
    data class StorageStats(
        val mediaCacheBytes: Long,
        val mediaCacheLimitBytes: Long,
        val artworkCacheBytes: Long,
        val offlineLibraryBytes: Long
    )

    data class BatchDownloadProgress(
        val active: Boolean,
        val albumName: String = "",
        val completed: Int = 0,
        val total: Int = 0,
        val error: String? = null
    )

    data class OfflineAlbum(
        val name: String,
        val directory: File,
        val files: List<File>,
        val totalBytes: Long,
        val coverFile: File?
    )

    companion object {
        const val MEDIA_CACHE_LIMIT_BYTES = 512L * 1024 * 1024
        private val COVER_NAMES = setOf("cover", "folder", "front", "album", "albumart", "artwork")
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "tif", "tiff", "bmp", "webp")
        private val AUDIO_EXTENSIONS = setOf("flac", "mp3", "wav", "ogg", "m4a", "aac")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _downloadProgress = MutableLiveData(BatchDownloadProgress(active = false))
    val downloadProgress: LiveData<BatchDownloadProgress> = _downloadProgress

    fun getOfflineRootDir(): File {
        return File(context.filesDir, "offline_music").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getFolderCoverCacheDir(): File {
        return File(context.cacheDir, "folder_covers").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        val mediaBytes = calculateDirSize(File(context.cacheDir, "media_cache"))
        val glideDir = Glide.getPhotoCacheDir(context)
        val glideBytes = if (glideDir != null) calculateDirSize(glideDir) else 0L
        val folderCoverBytes = calculateDirSize(getFolderCoverCacheDir())
        val offlineBytes = calculateDirSize(getOfflineRootDir())
        StorageStats(
            mediaCacheBytes = mediaBytes,
            mediaCacheLimitBytes = MEDIA_CACHE_LIMIT_BYTES,
            artworkCacheBytes = glideBytes + folderCoverBytes,
            offlineLibraryBytes = offlineBytes
        )
    }

    suspend fun clearMediaCache() = withContext(Dispatchers.IO) {
        try {
            mediaCache.keys.toList().forEach { key ->
                mediaCache.removeResource(key)
            }
        } catch (_: Exception) {
            // 忽略正在使用中的缓存键
        }
    }

    suspend fun clearArtworkCache() {
        withContext(Dispatchers.Main) {
            Glide.get(context).clearMemory()
        }
        withContext(Dispatchers.IO) {
            try {
                Glide.get(context).clearDiskCache()
            } catch (_: Exception) {
                // 忽略清理异常
            }
            getFolderCoverCacheDir().listFiles()?.forEach { it.deleteRecursively() }
        }
    }

    /** 批量离线下载整个 WebDAV 目录（包含音频、CUE、LRC、封面图片等非子目录文件）。 */
    fun downloadDirectory(dir: WebDavFile) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            _downloadProgress.postValue(
                BatchDownloadProgress(active = true, albumName = dir.name, completed = 0, total = 0)
            )
            try {
                val filesToDownload = collectDownloadableFiles(dir.path)
                if (filesToDownload.isEmpty()) {
                    _downloadProgress.postValue(
                        BatchDownloadProgress(
                            active = false,
                            albumName = dir.name,
                            error = "目录中没有可下载的文件"
                        )
                    )
                    return@launch
                }

                val rootDirName = dir.name.ifBlank { "Album" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val targetBase = File(getOfflineRootDir(), rootDirName)
                if (!targetBase.exists()) targetBase.mkdirs()

                val baseNormalized = dir.path.trimEnd('/')
                var completed = 0
                val total = filesToDownload.size
                _downloadProgress.postValue(
                    BatchDownloadProgress(active = true, albumName = dir.name, completed = 0, total = total)
                )

                for (remoteFile in filesToDownload) {
                    coroutineContext.ensureActive()
                    val relative = remoteFile.path.removePrefix(baseNormalized).trimStart('/')
                    val localFile = File(targetBase, relative)
                    localFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

                    if (!localFile.exists() || localFile.length() != remoteFile.size) {
                        val result = repository.downloadFileToStream(
                            remoteFile.path,
                            FileOutputStream(localFile)
                        )
                        if (result.isFailure) {
                            localFile.delete()
                        }
                    }
                    completed++
                    _downloadProgress.postValue(
                        BatchDownloadProgress(
                            active = true,
                            albumName = dir.name,
                            completed = completed,
                            total = total
                        )
                    )
                }

                _downloadProgress.postValue(
                    BatchDownloadProgress(
                        active = false,
                        albumName = dir.name,
                        completed = completed,
                        total = total
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                _downloadProgress.postValue(BatchDownloadProgress(active = false))
                throw e
            } catch (e: Exception) {
                _downloadProgress.postValue(
                    BatchDownloadProgress(
                        active = false,
                        albumName = dir.name,
                        error = e.message ?: "下载失败"
                    )
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadProgress.postValue(BatchDownloadProgress(active = false))
    }

    private suspend fun collectDownloadableFiles(path: String): List<WebDavFile> {
        val entries = repository.listFiles(path).getOrNull() ?: return emptyList()
        val result = mutableListOf<WebDavFile>()
        for (entry in entries) {
            coroutineContext.ensureActive()
            if (entry.isDirectory) {
                result.addAll(collectDownloadableFiles(entry.path))
            } else if (entry.isAudio || entry.isCue || entry.isImage || entry.isText) {
                result.add(entry)
            }
        }
        return result
    }

    suspend fun getOfflineAlbums(): List<OfflineAlbum> = withContext(Dispatchers.IO) {
        val root = getOfflineRootDir()
        val dirs = root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() }
            ?: return@withContext emptyList()

        dirs.mapNotNull { albumDir ->
            val allFiles = albumDir.walkTopDown().filter { it.isFile }.toList()
            val playableFiles = allFiles.filter {
                it.extension.lowercase() in AUDIO_EXTENSIONS || it.extension.lowercase() == "cue"
            }
            if (allFiles.isEmpty()) return@mapNotNull null

            val cover = allFiles.firstOrNull { file ->
                val ext = file.extension.lowercase()
                val base = file.nameWithoutExtension.lowercase()
                ext in IMAGE_EXTENSIONS && base in COVER_NAMES
            } ?: allFiles.firstOrNull { it.extension.lowercase() in IMAGE_EXTENSIONS }

            OfflineAlbum(
                name = albumDir.name,
                directory = albumDir,
                files = (if (playableFiles.isNotEmpty()) playableFiles else allFiles)
                    .sortedBy { it.name.lowercase() },
                totalBytes = allFiles.sumOf { it.length() },
                coverFile = cover
            )
        }
    }

    suspend fun deleteOfflineAlbum(album: OfflineAlbum) = withContext(Dispatchers.IO) {
        album.directory.deleteRecursively()
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        dir.listFiles()?.forEach { child ->
            total += calculateDirSize(child)
        }
        return total
    }
}
