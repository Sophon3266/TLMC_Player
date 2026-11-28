package com.tlmc.player.data.repository

import com.tlmc.player.data.model.CueSheet
import com.tlmc.player.data.model.LrcLine
import com.tlmc.player.data.model.WebDavFile
import com.tlmc.player.data.webdav.WebDavClient
import java.io.OutputStream
import com.tlmc.player.util.CueParser
import com.tlmc.player.util.EncodingDetector
import com.tlmc.player.util.FlacCueExtractor
import com.tlmc.player.util.FlacMetadata
import com.tlmc.player.util.LrcParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavRepository @Inject constructor(
    private val webDavClient: WebDavClient
) {
    suspend fun listFiles(path: String): Result<List<WebDavFile>> {
        return webDavClient.listFiles(path)
    }

    suspend fun downloadFile(path: String): Result<ByteArray> {
        return webDavClient.downloadFile(path)
    }

    suspend fun downloadFileToStream(path: String, outputStream: OutputStream): Result<Unit> {
        return webDavClient.downloadToStream(path, outputStream)
    }

    suspend fun downloadTextFile(path: String): Result<String> {
        val result = webDavClient.downloadFile(path)
        return result.map { bytes ->
            val encoding = EncodingDetector.detect(bytes)
            String(bytes, charset(encoding))
        }
    }

    suspend fun loadCueSheet(cuePath: String): Result<CueSheet> {
        val result = webDavClient.downloadFile(cuePath)
        return result.mapCatching { bytes ->
            val encoding = EncodingDetector.detect(bytes)
            val content = String(bytes, charset(encoding))
            CueParser.parse(content)
        }
    }

    suspend fun loadEmbeddedFlacCueSheet(flacPath: String): Result<CueSheet?> {
        return runCatching {
            val headerBytes = if (flacPath.startsWith("file://") || java.io.File(flacPath).exists()) {
                val localFile = if (flacPath.startsWith("file://")) {
                    java.io.File(android.net.Uri.parse(flacPath).path ?: flacPath)
                } else {
                    java.io.File(flacPath)
                }
                localFile.inputStream().use { input ->
                    val buf = ByteArray(1_048_576)
                    val read = input.read(buf)
                    if (read > 0) buf.copyOf(read) else ByteArray(0)
                }
            } else {
                // 先拉取前 256KB；若内嵌封面较大导致元数据块被截断，再扩展到 2MB
                val firstChunk = webDavClient.downloadFileRange(flacPath, 0L, 262_143L).getOrThrow()
                val fileName = flacPath.substringAfterLast('/')
                val quickParse = com.tlmc.player.util.FlacCueExtractor.extract(
                    firstChunk,
                    fileName.substringBeforeLast('.')
                )
                if (quickParse != null) {
                    return@runCatching quickParse
                }
                webDavClient.downloadFileRange(flacPath, 0L, 2_097_151L).getOrThrow()
            }
            val fileName = flacPath.substringAfterLast('/')
            com.tlmc.player.util.FlacCueExtractor.extract(headerBytes, fileName.substringBeforeLast('.'))
        }
    }

    suspend fun loadFlacMetadata(flacPath: String): Result<FlacMetadata?> {
        return runCatching {
            if (flacPath.startsWith("file://") || java.io.File(flacPath).exists()) {
                val localFile = if (flacPath.startsWith("file://")) {
                    java.io.File(android.net.Uri.parse(flacPath).path ?: flacPath)
                } else {
                    java.io.File(flacPath)
                }
                val headerBytes = localFile.inputStream().use { input ->
                    val buf = ByteArray(2_097_152)
                    val read = input.read(buf)
                    if (read > 0) buf.copyOf(read) else ByteArray(0)
                }
                return@runCatching FlacCueExtractor.extractMetadata(headerBytes)
            }

            // 先拉取前 64KB；若 VORBIS_COMMENT 被前面的大封面块推后，再扩展到 2MB
            val firstChunk = webDavClient.downloadFileRange(flacPath, 0L, 65_535L).getOrThrow()
            val firstResult = FlacCueExtractor.extractMetadataResult(firstChunk)
            if (firstResult != null && firstResult.reachedVorbisOrEnd) {
                return@runCatching firstResult.metadata
            }

            val fullHeader = webDavClient.downloadFileRange(flacPath, 0L, 2_097_151L).getOrThrow()
            FlacCueExtractor.extractMetadata(fullHeader)
        }
    }

    /**
     * 单次读取 FLAC 文件头并同时解析内嵌 CUESHEET 与曲目元数据，避免重复下载。
     */
    suspend fun loadFlacCueAndMetadata(flacPath: String): Result<Pair<CueSheet?, FlacMetadata?>> {
        return runCatching {
            val fileName = flacPath.substringAfterLast('/').substringBeforeLast('.')
            val headerBytes = if (flacPath.startsWith("file://") || java.io.File(flacPath).exists()) {
                val localFile = if (flacPath.startsWith("file://")) {
                    java.io.File(android.net.Uri.parse(flacPath).path ?: flacPath)
                } else {
                    java.io.File(flacPath)
                }
                localFile.inputStream().use { input ->
                    val buf = ByteArray(2_097_152)
                    val read = input.read(buf)
                    if (read > 0) buf.copyOf(read) else ByteArray(0)
                }
            } else {
                val firstChunk = webDavClient.downloadFileRange(flacPath, 0L, 262_143L).getOrThrow()
                if (!FlacCueExtractor.isMetadataTruncated(firstChunk)) {
                    firstChunk
                } else {
                    val quickCue = FlacCueExtractor.extract(firstChunk, fileName)
                    if (quickCue != null && quickCue.tracks.size > 1) {
                        return@runCatching quickCue to FlacCueExtractor.extractMetadata(firstChunk)
                    }
                    webDavClient.downloadFileRange(flacPath, 0L, 2_097_151L).getOrThrow()
                }
            }
            val cue = FlacCueExtractor.extract(headerBytes, fileName)
            val metadata = FlacCueExtractor.extractMetadata(headerBytes)
            cue to metadata
        }
    }

    fun getFileUrl(path: String): String {
        return webDavClient.getAuthenticatedUrl(path)
    }

    fun getAuthenticatedOkHttpClient() = webDavClient.getOkHttpClientWithAuth()

    /**
     * Find a CUE file matching the audio file name in the same directory
     */
    suspend fun findMatchingCue(audioFile: WebDavFile, directoryFiles: List<WebDavFile>): WebDavFile? {
        val audioBaseName = audioFile.nameWithoutExtension
        return directoryFiles.find { it.isCue && it.nameWithoutExtension == audioBaseName }
    }

    /**
     * Load and parse an LRC lyrics file from the given path
     */
    suspend fun loadLrcFile(lrcPath: String): Result<List<LrcLine>> {
        val result = webDavClient.downloadFile(lrcPath)
        return result.mapCatching { bytes ->
            val encoding = EncodingDetector.detect(bytes)
            val content = String(bytes, charset(encoding))
            LrcParser.parse(content)
        }
    }

    /**
     * Try to find an LRC file matching the audio file name in a directory.
     * Returns the LRC path if found, null otherwise.
     */
    suspend fun findMatchingLrcPath(audioFileName: String, directoryPath: String): String? {
        val baseName = audioFileName.substringBeforeLast('.')
        val lrcPath = "${directoryPath.trimEnd('/')}/$baseName.lrc"
        // Try to download a small part to see if the file exists
        return try {
            val result = webDavClient.downloadFile(lrcPath)
            if (result.isSuccess) lrcPath else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Try to find an LRC file matching the audio file name in directory file list.
     */
    fun findMatchingLrcInList(audioFileName: String, directoryFiles: List<WebDavFile>): WebDavFile? {
        val baseName = audioFileName.substringBeforeLast('.')
        return directoryFiles.find {
            !it.isDirectory && it.extension == "lrc" && it.nameWithoutExtension == baseName
        }
    }
}

