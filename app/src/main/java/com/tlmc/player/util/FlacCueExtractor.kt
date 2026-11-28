package com.tlmc.player.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.tlmc.player.data.model.CueSheet
import com.tlmc.player.data.model.CueTrack

data class FlacMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = ""
)

data class FlacMetadataParseResult(
    val metadata: FlacMetadata,
    val reachedVorbisOrEnd: Boolean
)

/**
 * 从 FLAC 文件头元数据块中提取内嵌 CUESHEET（支持 VORBIS_COMMENT 的 CUESHEET 标签与二进制 CUESHEET 块）
 * 以及 VORBIS_COMMENT 中的曲目元数据（TITLE、ARTIST、ALBUM）。
 */
object FlacCueExtractor {

    private const val BLOCK_STREAMINFO = 0
    private const val BLOCK_VORBIS_COMMENT = 4
    private const val BLOCK_CUESHEET = 5

    fun extractMetadata(bytes: ByteArray): FlacMetadata? {
        return extractMetadataResult(bytes)?.metadata
    }

    /**
     * 判断 FLAC 文件头中的元数据块是否在给定字节数组末尾被截断（尚未读到最后一个元数据块）。
     */
    fun isMetadataTruncated(bytes: ByteArray): Boolean {
        if (bytes.size < 42) return false
        if (bytes[0] != 0x66.toByte() ||
            bytes[1] != 0x4C.toByte() ||
            bytes[2] != 0x61.toByte() ||
            bytes[3] != 0x43.toByte()
        ) {
            return false
        }
        var pos = 4
        while (pos + 4 <= bytes.size) {
            val header = bytes[pos].toInt() and 0xFF
            val isLast = (header and 0x80) != 0
            val blockLength = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
            pos += 4
            if (blockLength < 0 || pos + blockLength > bytes.size) {
                return true
            }
            pos += blockLength
            if (isLast) return false
        }
        return true
    }

    /**
     * 从本地音频 URI 提取元数据：优先解析 FLAC VORBIS_COMMENT，若缺失则回退到 MediaMetadataRetriever。
     */
    fun extractLocalMetadata(
        context: Context,
        uri: Uri,
        preloadedHeaderBytes: ByteArray? = null
    ): FlacMetadata? {
        val headerBytes = preloadedHeaderBytes ?: try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(2_097_152)
                val read = input.read(buf)
                if (read > 0) buf.copyOf(read) else ByteArray(0)
            }
        } catch (_: Exception) {
            null
        }
        val flacMeta = headerBytes?.let { extractMetadata(it) }
        if (flacMeta != null && (flacMeta.title.isNotEmpty() || flacMeta.artist.isNotEmpty())) {
            return flacMeta
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim().orEmpty()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim().orEmpty()
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim().orEmpty()
            FlacMetadata(title = title, artist = artist, album = album)
        } catch (_: Exception) {
            flacMeta
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // 忽略
            }
        }
    }

    fun extractMetadataResult(bytes: ByteArray): FlacMetadataParseResult? {
        if (bytes.size < 42) return null
        if (bytes[0] != 0x66.toByte() ||
            bytes[1] != 0x4C.toByte() ||
            bytes[2] != 0x61.toByte() ||
            bytes[3] != 0x43.toByte()
        ) {
            return null
        }

        var pos = 4
        var titleTag = ""
        var artistTag = ""
        var albumArtistTag = ""
        var albumTag = ""
        var sawVorbisComment = false
        var truncated = false

        while (pos + 4 <= bytes.size) {
            val header = bytes[pos].toInt() and 0xFF
            val isLast = (header and 0x80) != 0
            val blockType = header and 0x7F
            val blockLength = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
            pos += 4

            if (blockLength < 0 || pos + blockLength > bytes.size) {
                truncated = true
                break
            }

            if (blockType == BLOCK_VORBIS_COMMENT) {
                sawVorbisComment = true
                val comments = parseVorbisComments(bytes, pos, blockLength)
                for ((key, value) in comments) {
                    val trimmed = value.trim()
                    if (trimmed.isEmpty()) continue
                    when {
                        key.equals("TITLE", ignoreCase = true) && titleTag.isEmpty() -> titleTag = trimmed
                        key.equals("ARTIST", ignoreCase = true) && artistTag.isEmpty() -> artistTag = trimmed
                        (key.equals("ALBUMARTIST", ignoreCase = true) ||
                            key.equals("PERFORMER", ignoreCase = true)) && albumArtistTag.isEmpty() -> {
                            albumArtistTag = trimmed
                        }
                        key.equals("ALBUM", ignoreCase = true) && albumTag.isEmpty() -> albumTag = trimmed
                    }
                }
                break
            }

            pos += blockLength
            if (isLast) break
        }

        return FlacMetadataParseResult(
            metadata = FlacMetadata(
                title = titleTag,
                artist = artistTag.ifEmpty { albumArtistTag },
                album = albumTag
            ),
            reachedVorbisOrEnd = sawVorbisComment || !truncated
        )
    }

    fun extract(bytes: ByteArray, fallbackTitle: String = ""): CueSheet? {
        if (bytes.size < 42) return null
        // FLAC magic: "fLaC"
        if (bytes[0] != 0x66.toByte() ||
            bytes[1] != 0x4C.toByte() ||
            bytes[2] != 0x61.toByte() ||
            bytes[3] != 0x43.toByte()
        ) {
            return null
        }

        var pos = 4
        var sampleRate = 44100
        var albumTag = ""
        var artistTag = ""
        var vorbisCueText: String? = null
        var binaryCueTracks: List<CueTrack>? = null

        while (pos + 4 <= bytes.size) {
            val header = bytes[pos].toInt() and 0xFF
            val isLast = (header and 0x80) != 0
            val blockType = header and 0x7F
            val blockLength = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
            pos += 4

            if (blockLength < 0 || pos + blockLength > bytes.size) {
                break
            }

            when (blockType) {
                BLOCK_STREAMINFO -> {
                    if (blockLength >= 13) {
                        val b10 = bytes[pos + 10].toInt() and 0xFF
                        val b11 = bytes[pos + 11].toInt() and 0xFF
                        val b12 = bytes[pos + 12].toInt() and 0xFF
                        val parsedRate = (b10 shl 12) or (b11 shl 4) or (b12 ushr 4)
                        if (parsedRate > 0) sampleRate = parsedRate
                    }
                }
                BLOCK_VORBIS_COMMENT -> {
                    val comments = parseVorbisComments(bytes, pos, blockLength)
                    for ((key, value) in comments) {
                        when {
                            key.equals("CUESHEET", ignoreCase = true) -> vorbisCueText = value
                            key.equals("ALBUM", ignoreCase = true) -> albumTag = value
                            key.equals("ALBUMARTIST", ignoreCase = true) && artistTag.isEmpty() -> artistTag = value
                            key.equals("ARTIST", ignoreCase = true) && artistTag.isEmpty() -> artistTag = value
                        }
                    }
                }
                BLOCK_CUESHEET -> {
                    binaryCueTracks = parseBinaryCueSheet(bytes, pos, blockLength, sampleRate)
                }
            }

            pos += blockLength
            if (isLast) break
        }

        if (!vorbisCueText.isNullOrBlank()) {
            val parsed = runCatching { CueParser.parse(vorbisCueText) }.getOrNull()
            if (parsed != null && parsed.tracks.size > 1) {
                return parsed.copy(
                    title = parsed.title.ifEmpty { albumTag.ifEmpty { fallbackTitle } },
                    performer = parsed.performer.ifEmpty { artistTag }
                )
            }
        }

        val tracks = binaryCueTracks
        if (tracks != null && tracks.size > 1) {
            return CueSheet(
                file = fallbackTitle,
                performer = artistTag,
                title = albumTag.ifEmpty { fallbackTitle },
                tracks = tracks
            )
        }

        return null
    }

    private fun parseVorbisComments(bytes: ByteArray, offset: Int, length: Int): List<Pair<String, String>> {
        val end = offset + length
        var p = offset
        if (p + 4 > end) return emptyList()

        val vendorLen = readInt32LE(bytes, p)
        p += 4
        if (vendorLen < 0 || p + vendorLen + 4 > end) return emptyList()
        p += vendorLen

        val count = readInt32LE(bytes, p)
        p += 4
        if (count <= 0) return emptyList()

        val result = mutableListOf<Pair<String, String>>()
        for (i in 0 until count) {
            if (p + 4 > end) break
            val itemLen = readInt32LE(bytes, p)
            p += 4
            if (itemLen < 0 || p + itemLen > end) break
            val text = String(bytes, p, itemLen, Charsets.UTF_8)
            p += itemLen
            val eqIdx = text.indexOf('=')
            if (eqIdx > 0) {
                result.add(text.substring(0, eqIdx) to text.substring(eqIdx + 1))
            }
        }
        return result
    }

    private fun parseBinaryCueSheet(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        sampleRate: Int
    ): List<CueTrack> {
        val end = offset + length
        // 128 catalog + 8 lead-in + 1 isCD + 258 reserved = 395 bytes
        var p = offset + 395
        if (p >= end) return emptyList()

        val numTracks = bytes[p].toInt() and 0xFF
        p += 1
        val rawTracks = mutableListOf<Pair<Int, Long>>()

        for (i in 0 until numTracks) {
            if (p + 36 > end) break
            val trackOffsetSamples = readInt64BE(bytes, p)
            p += 8
            val trackNumber = bytes[p].toInt() and 0xFF
            p += 1
            // 12 ISRC + 1 flags + 13 reserved = 26 bytes
            p += 26
            val numIndexPoints = bytes[p].toInt() and 0xFF
            p += 1

            var index01OffsetSamples: Long? = null
            for (j in 0 until numIndexPoints) {
                if (p + 12 > end) break
                val idxOffset = readInt64BE(bytes, p)
                val idxNumber = bytes[p + 8].toInt() and 0xFF
                if (idxNumber == 1) {
                    index01OffsetSamples = trackOffsetSamples + idxOffset
                }
                p += 12
            }

            // 170 (0xAA) and 255 are lead-out tracks
            if (trackNumber in 1..99) {
                val finalSamples = index01OffsetSamples ?: trackOffsetSamples
                val startMs = if (sampleRate > 0) finalSamples * 1000L / sampleRate else 0L
                rawTracks.add(trackNumber to startMs)
            }
        }

        val sorted = rawTracks.sortedBy { it.first }
        return sorted.mapIndexed { index, (number, startMs) ->
            val endMs = if (index < sorted.lastIndex) sorted[index + 1].second else -1L
            CueTrack(
                number = number,
                title = "Track %02d".format(number),
                performer = "",
                startTimeMs = startMs,
                endTimeMs = endMs
            )
        }
    }

    private fun readInt32LE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readInt64BE(bytes: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (bytes[offset + i].toLong() and 0xFFL)
        }
        return result
    }
}
