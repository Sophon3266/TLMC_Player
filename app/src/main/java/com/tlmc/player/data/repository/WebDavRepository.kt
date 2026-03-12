package com.tlmc.player.data.repository

import com.tlmc.player.data.model.CueSheet
import com.tlmc.player.data.model.LrcLine
import com.tlmc.player.data.model.WebDavFile
import com.tlmc.player.data.webdav.WebDavClient
import com.tlmc.player.util.CueParser
import com.tlmc.player.util.EncodingDetector
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

