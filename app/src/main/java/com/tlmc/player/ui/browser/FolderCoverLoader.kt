package com.tlmc.player.ui.browser

import android.content.Context
import android.util.LruCache
import com.tlmc.player.data.repository.OfflineManager
import com.tlmc.player.data.repository.WebDavRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 异步加载并缓存 WebDAV 专辑目录的 cover / folder 封面缩略图。
 */
@Singleton
class FolderCoverLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WebDavRepository,
    private val offlineManager: OfflineManager
) {
    companion object {
        private val COVER_NAMES = setOf("cover", "folder", "front", "album", "albumart", "artwork")
        private const val NO_COVER_SENTINEL = "__NO_COVER__"
        private const val MAX_CONCURRENT_LOOKUPS = 6
    }

    private val memoryCache = LruCache<String, String>(512)
    private val semaphore = Semaphore(MAX_CONCURRENT_LOOKUPS)

    suspend fun getCoverFile(dirPath: String): File? {
        val cached = memoryCache.get(dirPath)
        if (cached == NO_COVER_SENTINEL) return null
        if (cached != null) {
            val file = File(cached)
            if (file.exists()) return file
        }

        val diskFile = File(offlineManager.getFolderCoverCacheDir(), "${md5(dirPath)}.img")
        if (diskFile.exists() && diskFile.length() > 0) {
            memoryCache.put(dirPath, diskFile.absolutePath)
            return diskFile
        }

        val noCoverMarker = File(offlineManager.getFolderCoverCacheDir(), "${md5(dirPath)}.none")
        if (noCoverMarker.exists()) {
            memoryCache.put(dirPath, NO_COVER_SENTINEL)
            return null
        }

        return withContext(Dispatchers.IO) {
            semaphore.withPermit {
                // 二次检查，避免并发重复请求
                if (diskFile.exists() && diskFile.length() > 0) {
                    memoryCache.put(dirPath, diskFile.absolutePath)
                    return@withPermit diskFile
                }

                val entries = repository.listFiles(dirPath).getOrNull() ?: return@withPermit null
                val coverEntry = entries.firstOrNull { file ->
                    !file.isDirectory && file.isImage &&
                        file.nameWithoutExtension.lowercase() in COVER_NAMES
                } ?: entries.firstOrNull { file ->
                    !file.isDirectory && file.isImage
                }

                if (coverEntry == null) {
                    try {
                        noCoverMarker.createNewFile()
                    } catch (_: Exception) {
                        // 忽略标记写入失败
                    }
                    memoryCache.put(dirPath, NO_COVER_SENTINEL)
                    return@withPermit null
                }

                val bytes = repository.downloadFile(coverEntry.path).getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    try {
                        diskFile.writeBytes(bytes)
                        memoryCache.put(dirPath, diskFile.absolutePath)
                        return@withPermit diskFile
                    } catch (_: Exception) {
                        return@withPermit null
                    }
                }
                null
            }
        }
    }

    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
