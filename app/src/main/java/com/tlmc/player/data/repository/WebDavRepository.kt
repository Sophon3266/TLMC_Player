package com.tlmc.player.data.repository

import com.tlmc.player.data.model.CueSheet
import com.tlmc.player.data.model.WebDavFile
import com.tlmc.player.data.webdav.WebDavClient
import com.tlmc.player.util.CueParser
import com.tlmc.player.util.EncodingDetector
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
}

