package com.tlmc.player.data.repository

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import com.bumptech.glide.Glide
import com.tlmc.player.data.model.WebDavFile
import com.tlmc.player.util.DownloadNotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * 缓存统计、清理、离线目录迁移及专辑目录批量离线下载管理（同时支持本地 File 路径与 SAF 目录 Uri）。
 */
@OptIn(UnstableApi::class)
@Singleton
class OfflineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WebDavRepository,
    private val mediaCache: SimpleCache,
    private val configManager: ConfigManager
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

    data class OfflineFileItem(
        val name: String,
        val uri: Uri,
        val size: Long,
        val localFile: File? = null
    ) {
        val extension: String get() = name.substringAfterLast('.', "").lowercase()
        val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    }

    data class OfflineAlbum(
        val name: String,
        val id: String,
        val files: List<OfflineFileItem>,
        val totalBytes: Long,
        val coverUri: Uri?,
        val localDir: File? = null,
        val treeDirUri: Uri? = null
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

    fun getDefaultOfflineRootDir(): File {
        return File(context.filesDir, "offline_music").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getExternalAppOfflineDir(): File? {
        return context.getExternalFilesDir("offline_music")?.apply {
            if (!exists()) mkdirs()
        }
    }

    fun getOfflineRootDir(): File {
        val customPath = configManager.getOfflineDirPath()
        if (customPath != null && !customPath.startsWith("content://")) {
            val customDir = File(customPath)
            if ((customDir.exists() || customDir.mkdirs()) && customDir.isDirectory && customDir.canWrite()) {
                return customDir
            }
        }
        return getDefaultOfflineRootDir()
    }

    fun getOfflineTreeDocument(): DocumentFile? {
        val customPath = configManager.getOfflineDirPath() ?: return null
        if (!customPath.startsWith("content://")) return null
        val treeUri = Uri.parse(customPath)
        val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        return if (doc.exists() && doc.isDirectory && doc.canWrite()) doc else null
    }

    fun getOfflineLocationDisplay(): String {
        val customPath = configManager.getOfflineDirPath()
        if (customPath != null && customPath.startsWith("content://")) {
            val treeUri = Uri.parse(customPath)
            val doc = DocumentFile.fromTreeUri(context, treeUri)
            val lastSeg = treeUri.lastPathSegment?.substringAfter(':', treeUri.lastPathSegment ?: "")
            val readable = lastSeg?.ifBlank { doc?.name } ?: doc?.name ?: customPath
            return "已选目录：$readable"
        }
        return getOfflineRootDir().absolutePath
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
        val treeDoc = getOfflineTreeDocument()
        val offlineBytes = if (treeDoc != null) {
            calculateDocumentSize(treeDoc)
        } else {
            calculateDirSize(getOfflineRootDir())
        }
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

    /** 将旧位置的离线文件全部迁移到新位置，并清理旧位置文件。 */
    suspend fun migrateOfflineFiles(
        oldLocation: String?,
        newLocation: String?
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedOld = resolveLocationKey(oldLocation)
            val resolvedNew = resolveLocationKey(newLocation)
            if (resolvedOld == resolvedNew) return@runCatching 0

            var movedCount = 0
            val oldIsTree = oldLocation?.startsWith("content://") == true
            val newIsTree = newLocation?.startsWith("content://") == true

            val oldTreeDoc = if (oldIsTree) {
                DocumentFile.fromTreeUri(context, Uri.parse(oldLocation))
            } else null
            val oldFileDir = if (!oldIsTree) {
                if (oldLocation != null) File(oldLocation) else getDefaultOfflineRootDir()
            } else null

            val newTreeDoc = if (newIsTree) {
                DocumentFile.fromTreeUri(context, Uri.parse(newLocation))
                    ?: throw IllegalStateException("无法访问目标文件夹")
            } else null
            val newFileDir = if (!newIsTree) {
                (if (newLocation != null) File(newLocation) else getDefaultOfflineRootDir()).apply {
                    if (!exists()) mkdirs()
                }
            } else null

            if (oldFileDir != null && oldFileDir.exists()) {
                val children = oldFileDir.listFiles()?.toList().orEmpty()
                for (child in children) {
                    movedCount += copyFileTreeToTarget(child, newFileDir, newTreeDoc)
                }
                for (child in children) {
                    child.deleteRecursively()
                }
            } else if (oldTreeDoc != null && oldTreeDoc.exists()) {
                val children = oldTreeDoc.listFiles().toList()
                for (child in children) {
                    movedCount += copyDocTreeToTarget(child, newFileDir, newTreeDoc)
                }
                for (child in children) {
                    child.delete()
                }
            }

            movedCount
        }
    }

    private fun resolveLocationKey(location: String?): String {
        if (location.isNullOrBlank()) return getDefaultOfflineRootDir().absolutePath
        return if (location.startsWith("content://")) location else File(location).absolutePath
    }

    private fun copyFileTreeToTarget(
        source: File,
        targetFileDir: File?,
        targetTreeDir: DocumentFile?
    ): Int {
        if (source.isDirectory) {
            val subFileDir = targetFileDir?.let { File(it, source.name).apply { mkdirs() } }
            val subTreeDir = targetTreeDir?.let { findOrCreateDir(it, source.name) }
            var count = 0
            source.listFiles()?.forEach { child ->
                count += copyFileTreeToTarget(child, subFileDir, subTreeDir)
            }
            return count
        }
        if (targetFileDir != null) {
            val dest = File(targetFileDir, source.name)
            FileInputStream(source).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output, bufferSize = 16_384)
                }
            }
            return 1
        }
        if (targetTreeDir != null) {
            targetTreeDir.findFile(source.name)?.delete()
            val doc = targetTreeDir.createFile("application/octet-stream", source.name) ?: return 0
            FileInputStream(source).use { input ->
                context.contentResolver.openOutputStream(doc.uri)?.use { output ->
                    input.copyTo(output, bufferSize = 16_384)
                }
            }
            return 1
        }
        return 0
    }

    private fun copyDocTreeToTarget(
        source: DocumentFile,
        targetFileDir: File?,
        targetTreeDir: DocumentFile?
    ): Int {
        val name = source.name ?: return 0
        if (source.isDirectory) {
            val subFileDir = targetFileDir?.let { File(it, name).apply { mkdirs() } }
            val subTreeDir = targetTreeDir?.let { findOrCreateDir(it, name) }
            var count = 0
            source.listFiles().forEach { child ->
                count += copyDocTreeToTarget(child, subFileDir, subTreeDir)
            }
            return count
        }
        if (targetFileDir != null) {
            val dest = File(targetFileDir, name)
            context.contentResolver.openInputStream(source.uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output, bufferSize = 16_384)
                }
            }
            return 1
        }
        if (targetTreeDir != null) {
            targetTreeDir.findFile(name)?.delete()
            val doc = targetTreeDir.createFile("application/octet-stream", name) ?: return 0
            context.contentResolver.openInputStream(source.uri)?.use { input ->
                context.contentResolver.openOutputStream(doc.uri)?.use { output ->
                    input.copyTo(output, bufferSize = 16_384)
                }
            }
            return 1
        }
        return 0
    }

    /** 批量离线下载整个 WebDAV 目录（仅下载音频、CUE 与一张匹配封面图）。 */
    fun downloadDirectory(dir: WebDavFile) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            _downloadProgress.postValue(
                BatchDownloadProgress(active = true, albumName = dir.name, completed = 0, total = 0)
            )
            DownloadNotificationHelper.showProgress(
                context = context,
                notificationId = DownloadNotificationHelper.NOTIFICATION_ID_BATCH,
                title = "离线下载：${dir.name}",
                content = "正在获取文件列表...",
                progress = 0,
                max = 0,
                indeterminate = true
            )
            try {
                val filesToDownload = collectDownloadableFiles(dir.path)
                if (filesToDownload.isEmpty()) {
                    DownloadNotificationHelper.showCompleted(
                        context = context,
                        notificationId = DownloadNotificationHelper.NOTIFICATION_ID_BATCH,
                        title = "离线下载：${dir.name}",
                        content = "目录中没有可下载的文件"
                    )
                    _downloadProgress.postValue(
                        BatchDownloadProgress(
                            active = false,
                            albumName = dir.name,
                            error = "目录中没有可下载的文件"
                        )
                    )
                    return@launch
                }

                val treeRoot = getOfflineTreeDocument()
                val fileRoot = if (treeRoot == null) getOfflineRootDir() else null

                var completed = 0
                val total = filesToDownload.size
                _downloadProgress.postValue(
                    BatchDownloadProgress(active = true, albumName = dir.name, completed = 0, total = total)
                )
                DownloadNotificationHelper.showProgress(
                    context = context,
                    notificationId = DownloadNotificationHelper.NOTIFICATION_ID_BATCH,
                    title = "离线下载：${dir.name}",
                    content = "准备下载 $total 个文件",
                    progress = 0,
                    max = total,
                    indeterminate = false
                )

                for (remoteFile in filesToDownload) {
                    coroutineContext.ensureActive()
                    val segments = remoteFile.path.trim('/').split('/')
                        .filter { it.isNotEmpty() }
                        .map { sanitizeSegment(it) }
                    if (segments.isEmpty()) continue

                    if (treeRoot != null) {
                        var parentDoc: DocumentFile = treeRoot
                        for (i in 0 until segments.lastIndex) {
                            parentDoc = findOrCreateDir(parentDoc, segments[i])
                        }
                        val targetName = segments.last()
                        val existing = parentDoc.findFile(targetName)
                        if (existing == null || existing.length() != remoteFile.size) {
                            existing?.delete()
                            val doc = parentDoc.createFile("application/octet-stream", targetName)
                            val outStream = doc?.let { context.contentResolver.openOutputStream(it.uri) }
                            if (outStream != null) {
                                val result = repository.downloadFileToStream(remoteFile.path, outStream)
                                if (result.isFailure) {
                                    doc.delete()
                                }
                            }
                        }
                    } else if (fileRoot != null) {
                        val localFile = File(fileRoot, segments.joinToString(File.separator))
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
                    DownloadNotificationHelper.showProgress(
                        context = context,
                        notificationId = DownloadNotificationHelper.NOTIFICATION_ID_BATCH,
                        title = "离线下载：${dir.name}",
                        content = "已完成 $completed / $total：${remoteFile.name}",
                        progress = completed,
                        max = total,
                        indeterminate = false
                    )
                }

                DownloadNotificationHelper.showCompleted(
                    context = context,
                    notificationId = DownloadNotificationHelper.NOTIFICATION_ID_BATCH,
                    title = "离线下载完成：${dir.name}",
                    content = "共下载 $completed / $total 个文件"
                )
                _downloadProgress.postValue(
                    BatchDownloadProgress(
                        active = false,
                        albumName = dir.name,
                        completed = completed,
                        total = total
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                DownloadNotificationHelper.showCompleted(
                    context = context,
                    notificationId = DownloadNotificationHelper.NOTIFICATION_ID_BATCH,
                    title = "离线下载已取消",
                    content = dir.name
                )
                _downloadProgress.postValue(BatchDownloadProgress(active = false))
                throw e
            } catch (e: Exception) {
                DownloadNotificationHelper.showCompleted(
                    context = context,
                    notificationId = DownloadNotificationHelper.NOTIFICATION_ID_BATCH,
                    title = "离线下载失败：${dir.name}",
                    content = e.message ?: "下载失败"
                )
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
            } else if (entry.isAudio || entry.isCue) {
                result.add(entry)
            }
        }
        val coverPriority = listOf("folder", "album", "cover", "front", "albumart", "artwork")
        val imageEntries = entries.filter { !it.isDirectory && it.isImage }
        val matchedCover = coverPriority.firstNotNullOfOrNull { targetName ->
            imageEntries.firstOrNull { it.nameWithoutExtension.equals(targetName, ignoreCase = true) }
        }
        if (matchedCover != null) {
            result.add(matchedCover)
        }
        return result
    }

    suspend fun getOfflineAlbums(): List<OfflineAlbum> = withContext(Dispatchers.IO) {
        val treeRoot = getOfflineTreeDocument()
        if (treeRoot != null) {
            val albums = mutableListOf<OfflineAlbum>()
            collectDocumentAlbums(treeRoot, albums)
            return@withContext albums.sortedBy { it.name.lowercase() }
        }

        val root = getOfflineRootDir()
        val albums = mutableListOf<OfflineAlbum>()
        collectFileAlbums(root, root, albums)
        albums.sortedBy { it.name.lowercase() }
    }

    private fun collectDocumentAlbums(dir: DocumentFile, out: MutableList<OfflineAlbum>) {
        val children = dir.listFiles()
        val directFiles = children.filter { it.isFile }
        if (directFiles.isNotEmpty()) {
            val allItems = directFiles.map { fileDoc ->
                OfflineFileItem(
                    name = fileDoc.name ?: "file",
                    uri = fileDoc.uri,
                    size = fileDoc.length()
                )
            }
            val playable = allItems.filter {
                it.extension in AUDIO_EXTENSIONS || it.extension == "cue"
            }
            val cover = findBestCoverItem(allItems)
            out.add(
                OfflineAlbum(
                    name = dir.name ?: "Album",
                    id = dir.uri.toString(),
                    files = (if (playable.isNotEmpty()) playable else allItems).sortedBy { it.name.lowercase() },
                    totalBytes = allItems.sumOf { it.size },
                    coverUri = cover?.uri,
                    treeDirUri = dir.uri
                )
            )
        }
        for (child in children) {
            if (child.isDirectory) {
                collectDocumentAlbums(child, out)
            }
        }
    }

    private fun collectFileAlbums(root: File, dir: File, out: MutableList<OfflineAlbum>) {
        val children = dir.listFiles() ?: return
        val directFiles = children.filter { it.isFile }
        if (directFiles.isNotEmpty() && dir != root) {
            val allItems = directFiles.map { file ->
                OfflineFileItem(
                    name = file.name,
                    uri = Uri.fromFile(file),
                    size = file.length(),
                    localFile = file
                )
            }
            val playable = allItems.filter {
                it.extension in AUDIO_EXTENSIONS || it.extension == "cue"
            }
            val cover = findBestCoverItem(allItems)
            out.add(
                OfflineAlbum(
                    name = dir.name,
                    id = dir.absolutePath,
                    files = (if (playable.isNotEmpty()) playable else allItems).sortedBy { it.name.lowercase() },
                    totalBytes = allItems.sumOf { it.size },
                    coverUri = cover?.uri,
                    localDir = dir
                )
            )
        }
        for (child in children) {
            if (child.isDirectory) {
                collectFileAlbums(root, child, out)
            }
        }
    }

    suspend fun deleteOfflineAlbum(album: OfflineAlbum) = withContext(Dispatchers.IO) {
        album.localDir?.let { dir ->
            dir.deleteRecursively()
            val root = getOfflineRootDir()
            var parent = dir.parentFile
            while (parent != null && parent != root && parent.listFiles()?.isEmpty() == true) {
                parent.delete()
                parent = parent.parentFile
            }
        }
        album.treeDirUri?.let { uri ->
            DocumentFile.fromTreeUri(context, uri)?.delete()
        }
    }

    private fun sanitizeSegment(segment: String): String {
        return segment.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "_" }
    }

    private fun findBestCoverItem(items: List<OfflineFileItem>): OfflineFileItem? {
        return items.firstOrNull { item ->
            item.extension in IMAGE_EXTENSIONS && item.nameWithoutExtension.lowercase() in COVER_NAMES
        } ?: items.firstOrNull { it.extension in IMAGE_EXTENSIONS }
    }

    private fun findOrCreateDir(parent: DocumentFile, name: String): DocumentFile {
        return parent.findFile(name)?.takeIf { it.isDirectory }
            ?: parent.createDirectory(name)
            ?: parent
    }

    private fun collectDocumentItems(dir: DocumentFile): List<OfflineFileItem> {
        val list = mutableListOf<OfflineFileItem>()
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                list.addAll(collectDocumentItems(child))
            } else if (child.isFile) {
                list.add(
                    OfflineFileItem(
                        name = child.name ?: "file",
                        uri = child.uri,
                        size = child.length()
                    )
                )
            }
        }
        return list
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

    private fun calculateDocumentSize(dir: DocumentFile): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        for (child in dir.listFiles()) {
            total += calculateDocumentSize(child)
        }
        return total
    }
}
