package com.tlmc.player.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.min
import java.util.zip.DataFormatException
import java.util.zip.Inflater

object TiffDecoder {

    fun looksLikeTiff(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val little = bytes[0] == 0x49.toByte() && bytes[1] == 0x49.toByte()
        val big = bytes[0] == 0x4D.toByte() && bytes[1] == 0x4D.toByte()
        if (!little && !big) return false
        val reader = ByteReader(bytes)
        reader.littleEndian = little
        return reader.u16(2) == 42
    }

    fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.size < 8) return null
        val reader = ByteReader(bytes)

        val littleEndian = when {
            bytes[0] == 0x49.toByte() && bytes[1] == 0x49.toByte() -> true
            bytes[0] == 0x4D.toByte() && bytes[1] == 0x4D.toByte() -> false
            else -> return null
        }
        reader.littleEndian = littleEndian

        if (reader.u16(2) != 42) return null
        val ifdOffset = reader.u32(4)
        if (!reader.isInRange(ifdOffset, 2)) return null

        val entryCount = reader.u16(ifdOffset)
        val ifdStart = ifdOffset + 2
        val ifdSize = entryCount * 12
        if (!reader.isInRange(ifdStart, ifdSize)) return null

        var width = 0
        var height = 0
        var bitsPerSample = intArrayOf(8)
        var compression = 1
        var photometric = 2
        var samplesPerPixel = 1
        var rowsPerStrip = Int.MAX_VALUE
        var stripOffsets = IntArray(0)
        var stripByteCounts = IntArray(0)
        var planarConfig = 1
        var predictor = 1
        var jpegOffset = -1
        var jpegLength = -1
        var jpegTables: ByteArray? = null

        for (i in 0 until entryCount) {
            val entryOffset = ifdStart + i * 12
            val tag = reader.u16(entryOffset)
            val type = reader.u16(entryOffset + 2)
            val count = reader.u32(entryOffset + 4)
            val valueOffset = entryOffset + 8

            when (tag) {
                256 -> width = readFirstInt(reader, type, count, valueOffset) ?: return null
                257 -> height = readFirstInt(reader, type, count, valueOffset) ?: return null
                258 -> bitsPerSample = readIntArray(reader, type, count, valueOffset) ?: return null
                259 -> compression = readFirstInt(reader, type, count, valueOffset) ?: return null
                262 -> photometric = readFirstInt(reader, type, count, valueOffset) ?: return null
                273 -> stripOffsets = readIntArray(reader, type, count, valueOffset) ?: return null
                277 -> samplesPerPixel = readFirstInt(reader, type, count, valueOffset) ?: return null
                278 -> rowsPerStrip = readFirstInt(reader, type, count, valueOffset) ?: return null
                279 -> stripByteCounts = readIntArray(reader, type, count, valueOffset) ?: return null
                284 -> planarConfig = readFirstInt(reader, type, count, valueOffset) ?: return null
                317 -> predictor = readFirstInt(reader, type, count, valueOffset) ?: return null
                513 -> jpegOffset = readFirstInt(reader, type, count, valueOffset) ?: return null
                514 -> jpegLength = readFirstInt(reader, type, count, valueOffset) ?: return null
                347 -> jpegTables = readByteArray(reader, type, count, valueOffset)
            }
        }

        if (width <= 0 || height <= 0) return null
        if (compression != 1 && compression != 5 && compression != 6 && compression != 7 && compression != 8 && compression != 32946 && compression != 32773) return null

        if (compression == 6 || compression == 7) {
            val jpgBitmap = decodeEmbeddedJpeg(
                bytes = bytes,
                width = width,
                height = height,
                rowsPerStrip = rowsPerStrip,
                stripOffsets = stripOffsets,
                stripByteCounts = stripByteCounts,
                jpegOffset = jpegOffset,
                jpegLength = jpegLength,
                jpegTables = jpegTables
            )
            if (jpgBitmap != null) return jpgBitmap
            return null
        }

        if (planarConfig != 1) return null
        if (samplesPerPixel != 1 && samplesPerPixel != 3 && samplesPerPixel != 4) return null
        if (bitsPerSample.isEmpty()) return null

        val normalizedBits = if (bitsPerSample.size == 1) {
            IntArray(samplesPerPixel) { bitsPerSample[0] }
        } else {
            bitsPerSample
        }
        if (normalizedBits.size < samplesPerPixel) return null
        if (normalizedBits.any { it != normalizedBits[0] }) return null
        val bitsPerChannel = normalizedBits[0]
        if (bitsPerChannel != 8 && bitsPerChannel != 16) return null
        val bytesPerChannel = bitsPerChannel / 8

        if (stripOffsets.isEmpty()) return null

        if (rowsPerStrip <= 0 || rowsPerStrip == Int.MAX_VALUE) {
            rowsPerStrip = height
        }

        if (stripByteCounts.isEmpty()) {
            stripByteCounts = deriveStripByteCounts(bytes.size, stripOffsets)
        } else if (stripByteCounts.size < stripOffsets.size) {
            val derived = deriveStripByteCounts(bytes.size, stripOffsets)
            val merged = IntArray(stripOffsets.size)
            for (i in merged.indices) {
                merged[i] = if (i < stripByteCounts.size) stripByteCounts[i] else derived[i]
            }
            stripByteCounts = merged
        }

        val pixels = IntArray(width * height)
        val bytesPerPixel = samplesPerPixel * bytesPerChannel
        val expectedRowBytes = width * bytesPerPixel
        var y = 0

        val stripCount = min(stripOffsets.size, stripByteCounts.size)
        for (stripIndex in 0 until stripCount) {
            if (y >= height) break
            val offset = stripOffsets[stripIndex]
            val byteCount = stripByteCounts[stripIndex]
            if (!reader.isInRange(offset, byteCount)) return null

            val rowsInThisStrip = min(rowsPerStrip, height - y)
            if (rowsInThisStrip <= 0) continue
            val expectedBytes = rowsInThisStrip * expectedRowBytes

            var stripData = when (compression) {
                1 -> {
                    if (byteCount < expectedBytes) return null
                    bytes.copyOfRange(offset, offset + expectedBytes)
                }
                32773 -> decodePackBits(bytes, offset, byteCount, expectedBytes) ?: return null
                5 -> decodeLzw(bytes, offset, byteCount, expectedBytes) ?: return null
                8, 32946 -> decodeDeflate(bytes, offset, byteCount, expectedBytes) ?: return null
                else -> return null
            }

            if (predictor == 2) {
                stripData = applyHorizontalPredictor(
                    data = stripData,
                    width = width,
                    rows = rowsInThisStrip,
                    samplesPerPixel = samplesPerPixel,
                    bytesPerChannel = bytesPerChannel,
                    littleEndian = littleEndian
                )
                    ?: return null
            }

            var consumed = 0
            while (consumed + expectedRowBytes <= stripData.size && y < height) {
                val rowStart = consumed
                var x = 0
                while (x < width) {
                    val pixelOffset = rowStart + x * bytesPerPixel
                    val color = if (samplesPerPixel == 1) {
                        val v = readChannel(stripData, pixelOffset, bytesPerChannel, littleEndian)
                        val gray = when (photometric) {
                            0 -> 255 - v
                            else -> v
                        }
                        0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
                    } else {
                        if (photometric != 2) return null
                        val r = readChannel(stripData, pixelOffset, bytesPerChannel, littleEndian)
                        val g = readChannel(stripData, pixelOffset + bytesPerChannel, bytesPerChannel, littleEndian)
                        val b = readChannel(stripData, pixelOffset + bytesPerChannel * 2, bytesPerChannel, littleEndian)
                        val a = if (samplesPerPixel == 4) {
                            readChannel(stripData, pixelOffset + bytesPerChannel * 3, bytesPerChannel, littleEndian)
                        } else {
                            0xFF
                        }
                        (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    pixels[y * width + x] = color
                    x++
                }

                consumed += expectedRowBytes
                y++
            }
        }

        if (y <= 0) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun decodeEmbeddedJpeg(
        bytes: ByteArray,
        width: Int,
        height: Int,
        rowsPerStrip: Int,
        stripOffsets: IntArray,
        stripByteCounts: IntArray,
        jpegOffset: Int,
        jpegLength: Int,
        jpegTables: ByteArray?
    ): Bitmap? {
        if (stripOffsets.isNotEmpty()) {
            val normalizedRowsPerStrip = if (rowsPerStrip <= 0 || rowsPerStrip == Int.MAX_VALUE) height else rowsPerStrip
            val normalizedCounts = if (stripByteCounts.isEmpty()) {
                deriveStripByteCounts(bytes.size, stripOffsets)
            } else {
                stripByteCounts
            }

            val stripCount = min(stripOffsets.size, normalizedCounts.size)
            if (stripCount > 0) {
                val outPixels = IntArray(width * height)
                var y = 0

                for (i in 0 until stripCount) {
                    if (y >= height) break

                    val offset = stripOffsets[i]
                    val len = normalizedCounts[i]
                    if (offset < 0 || len <= 0 || offset + len > bytes.size) return null

                    val stripData = bytes.copyOfRange(offset, offset + len)
                    val rowsInStrip = min(normalizedRowsPerStrip, height - y)

                    val stripBitmap = decodeJpegSegment(stripData, jpegTables) ?: return null
                    val copyWidth = min(width, stripBitmap.width)
                    val copyHeight = min(rowsInStrip, stripBitmap.height)
                    if (copyWidth <= 0 || copyHeight <= 0) return null

                    val lineBuffer = IntArray(copyWidth)
                    for (row in 0 until copyHeight) {
                        stripBitmap.getPixels(lineBuffer, 0, copyWidth, 0, row, copyWidth, 1)
                        System.arraycopy(lineBuffer, 0, outPixels, (y + row) * width, copyWidth)
                        if (copyWidth < width) {
                            val fill = lineBuffer[copyWidth - 1]
                            for (x in copyWidth until width) {
                                outPixels[(y + row) * width + x] = fill
                            }
                        }
                    }

                    y += copyHeight
                }

                if (y > 0) {
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
                    return bitmap
                }
            }
        }

        if (jpegOffset >= 0) {
            val len = if (jpegLength > 0) jpegLength else bytes.size - jpegOffset
            if (jpegOffset + len <= bytes.size && len > 0) {
                BitmapFactory.decodeByteArray(bytes, jpegOffset, len)?.let { return it }
            }
        }

        if (stripOffsets.isNotEmpty()) {
            val offset = stripOffsets[0]
            val len = if (stripByteCounts.isNotEmpty()) stripByteCounts[0] else (bytes.size - offset)
            if (offset >= 0 && len > 0 && offset + len <= bytes.size) {
                BitmapFactory.decodeByteArray(bytes, offset, len)?.let { return it }
            }
        }

        return null
    }

    private fun decodeJpegSegment(segment: ByteArray, jpegTables: ByteArray?): Bitmap? {
        BitmapFactory.decodeByteArray(segment, 0, segment.size)?.let { return it }

        if (jpegTables != null && jpegTables.isNotEmpty()) {
            val combined = combineJpegTablesAndSegment(jpegTables, segment)
            if (combined != null) {
                BitmapFactory.decodeByteArray(combined, 0, combined.size)?.let { return it }
            }
        }

        return null
    }

    private fun combineJpegTablesAndSegment(tables: ByteArray, segment: ByteArray): ByteArray? {
        if (tables.size < 4 || segment.isEmpty()) return null

        val tableBody = when {
            isJpegStart(tables, 0) && isJpegEnd(tables) -> tables.copyOfRange(2, tables.size - 2)
            else -> tables
        }

        val hasSoi = isJpegStart(segment, 0)
        val hasEoi = isJpegEnd(segment)

        val scanBody = when {
            hasSoi && hasEoi && segment.size >= 4 -> segment.copyOfRange(2, segment.size - 2)
            hasSoi -> segment.copyOfRange(2, segment.size)
            hasEoi -> segment.copyOfRange(0, segment.size - 2)
            else -> segment
        }

        val out = ByteArray(2 + tableBody.size + scanBody.size + 2)
        out[0] = 0xFF.toByte()
        out[1] = 0xD8.toByte()
        System.arraycopy(tableBody, 0, out, 2, tableBody.size)
        System.arraycopy(scanBody, 0, out, 2 + tableBody.size, scanBody.size)
        out[out.size - 2] = 0xFF.toByte()
        out[out.size - 1] = 0xD9.toByte()
        return out
    }

    private fun isJpegStart(data: ByteArray, start: Int): Boolean {
        return data.size >= start + 2 && data[start] == 0xFF.toByte() && data[start + 1] == 0xD8.toByte()
    }

    private fun isJpegEnd(data: ByteArray): Boolean {
        return data.size >= 2 && data[data.size - 2] == 0xFF.toByte() && data[data.size - 1] == 0xD9.toByte()
    }

    private fun deriveStripByteCounts(fileSize: Int, stripOffsets: IntArray): IntArray {
        val out = IntArray(stripOffsets.size)
        for (i in stripOffsets.indices) {
            val start = stripOffsets[i]
            val end = if (i + 1 < stripOffsets.size) stripOffsets[i + 1] else fileSize
            out[i] = (end - start).coerceAtLeast(0)
        }
        return out
    }

    private fun decodePackBits(
        source: ByteArray,
        offset: Int,
        length: Int,
        expectedSize: Int
    ): ByteArray? {
        val output = ByteArray(expectedSize)
        var inPos = offset
        val inEnd = offset + length
        var outPos = 0

        while (inPos < inEnd && outPos < expectedSize) {
            val n = source[inPos].toInt()
            inPos++
            when {
                n in 0..127 -> {
                    val count = n + 1
                    if (inPos + count > inEnd || outPos + count > expectedSize) return null
                    System.arraycopy(source, inPos, output, outPos, count)
                    inPos += count
                    outPos += count
                }
                n in -127..-1 -> {
                    val count = 1 - n
                    if (inPos >= inEnd || outPos + count > expectedSize) return null
                    val value = source[inPos]
                    inPos++
                    repeat(count) {
                        output[outPos++] = value
                    }
                }
                else -> {
                    // -128 is a no-op per PackBits specification.
                }
            }
        }

        return if (outPos == expectedSize) output else null
    }

    private fun decodeLzw(
        source: ByteArray,
        offset: Int,
        length: Int,
        expectedSize: Int
    ): ByteArray? {
        val bitReader = BitReaderMsb(source, offset, length)
        val output = ByteArray(expectedSize)

        val dictionary = arrayOfNulls<ByteArray>(4096)
        fun resetDictionary() {
            for (i in 0..255) {
                dictionary[i] = byteArrayOf(i.toByte())
            }
            for (i in 256 until dictionary.size) {
                dictionary[i] = null
            }
        }

        resetDictionary()
        val clearCode = 256
        val endCode = 257
        var codeSize = 9
        var nextCode = 258
        var outPos = 0
        var previous: ByteArray? = null

        while (outPos < expectedSize) {
            val code = bitReader.read(codeSize)
            if (code < 0) break

            if (code == clearCode) {
                resetDictionary()
                codeSize = 9
                nextCode = 258
                previous = null
                continue
            }

            if (code == endCode) break

            val entry = when {
                code < nextCode && dictionary[code] != null -> dictionary[code]!!
                code == nextCode && previous != null -> {
                    val p = previous!!
                    p + p[0]
                }
                else -> return null
            }

            val writable = min(entry.size, expectedSize - outPos)
            System.arraycopy(entry, 0, output, outPos, writable)
            outPos += writable

            if (previous != null && nextCode < 4096) {
                val prev = previous!!
                dictionary[nextCode] = prev + entry[0]
                nextCode++
                if (nextCode == (1 shl codeSize) && codeSize < 12) {
                    codeSize++
                }
            }

            previous = entry
        }

        return if (outPos == expectedSize) output else null
    }

    private fun decodeDeflate(
        source: ByteArray,
        offset: Int,
        length: Int,
        expectedSize: Int
    ): ByteArray? {
        val inflater = Inflater()
        val output = ByteArray(expectedSize)
        return try {
            inflater.setInput(source, offset, length)
            var outPos = 0
            while (outPos < expectedSize && !inflater.finished()) {
                val read = inflater.inflate(output, outPos, expectedSize - outPos)
                if (read <= 0) {
                    if (inflater.needsInput()) break
                    return null
                }
                outPos += read
            }
            if (outPos == expectedSize) output else null
        } catch (_: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun applyHorizontalPredictor(
        data: ByteArray,
        width: Int,
        rows: Int,
        samplesPerPixel: Int,
        bytesPerChannel: Int,
        littleEndian: Boolean
    ): ByteArray? {
        val bytesPerPixel = samplesPerPixel * bytesPerChannel
        val rowSize = width * bytesPerPixel
        if (data.size != rows * rowSize) return null

        val maxValue = if (bytesPerChannel == 1) 0x100 else 0x10000

        for (row in 0 until rows) {
            val rowStart = row * rowSize
            for (x in 1 until width) {
                val current = rowStart + x * bytesPerPixel
                val left = current - bytesPerPixel
                for (s in 0 until samplesPerPixel) {
                    val currentOffset = current + s * bytesPerChannel
                    val leftOffset = left + s * bytesPerChannel
                    val currentValue = readChannel(data, currentOffset, bytesPerChannel, littleEndian, normalize = false)
                    val leftValue = readChannel(data, leftOffset, bytesPerChannel, littleEndian, normalize = false)
                    val recovered = (currentValue + leftValue) % maxValue
                    writeChannel(data, currentOffset, bytesPerChannel, littleEndian, recovered)
                }
            }
        }

        return data
    }

    private fun readChannel(
        data: ByteArray,
        offset: Int,
        bytesPerChannel: Int,
        littleEndian: Boolean,
        normalize: Boolean = true
    ): Int {
        val raw = if (bytesPerChannel == 1) {
            data[offset].toInt() and 0xFF
        } else {
            val b0 = data[offset].toInt() and 0xFF
            val b1 = data[offset + 1].toInt() and 0xFF
            if (littleEndian) {
                (b1 shl 8) or b0
            } else {
                (b0 shl 8) or b1
            }
        }

        return if (!normalize || bytesPerChannel == 1) raw else raw ushr 8
    }

    private fun writeChannel(
        data: ByteArray,
        offset: Int,
        bytesPerChannel: Int,
        littleEndian: Boolean,
        value: Int
    ) {
        if (bytesPerChannel == 1) {
            data[offset] = (value and 0xFF).toByte()
            return
        }

        if (littleEndian) {
            data[offset] = (value and 0xFF).toByte()
            data[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        } else {
            data[offset] = ((value ushr 8) and 0xFF).toByte()
            data[offset + 1] = (value and 0xFF).toByte()
        }
    }

    private fun readFirstInt(
        reader: ByteReader,
        type: Int,
        count: Int,
        valueOffset: Int
    ): Int? {
        return readIntArray(reader, type, count, valueOffset)?.firstOrNull()
    }

    private fun readIntArray(
        reader: ByteReader,
        type: Int,
        count: Int,
        valueOffset: Int
    ): IntArray? {
        if (count <= 0) return null
        val typeSize = typeSize(type) ?: return null
        val totalSize = count * typeSize

        val dataOffset = if (totalSize <= 4) {
            valueOffset
        } else {
            reader.u32(valueOffset)
        }

        if (!reader.isInRange(dataOffset, totalSize)) return null

        val out = IntArray(count)
        for (i in 0 until count) {
            val p = dataOffset + i * typeSize
            out[i] = when (type) {
                3 -> reader.u16(p)
                4 -> reader.u32(p)
                else -> return null
            }
        }
        return out
    }

    private fun readByteArray(
        reader: ByteReader,
        type: Int,
        count: Int,
        valueOffset: Int
    ): ByteArray? {
        if (count <= 0) return null
        val typeSize = when (type) {
            1, 2, 7 -> 1
            else -> return null
        }

        val totalSize = count * typeSize
        val dataOffset = if (totalSize <= 4) {
            valueOffset
        } else {
            reader.u32(valueOffset)
        }

        if (!reader.isInRange(dataOffset, totalSize)) return null
        return reader.copyOfRange(dataOffset, totalSize)
    }

    private fun typeSize(type: Int): Int? {
        return when (type) {
            3 -> 2
            4 -> 4
            else -> null
        }
    }

    private class ByteReader(private val bytes: ByteArray) {
        var littleEndian: Boolean = true

        fun u16(offset: Int): Int {
            if (!isInRange(offset, 2)) return -1
            val b0 = bytes[offset].toInt() and 0xFF
            val b1 = bytes[offset + 1].toInt() and 0xFF
            return if (littleEndian) (b1 shl 8) or b0 else (b0 shl 8) or b1
        }

        fun u32(offset: Int): Int {
            if (!isInRange(offset, 4)) return -1
            val b0 = bytes[offset].toInt() and 0xFF
            val b1 = bytes[offset + 1].toInt() and 0xFF
            val b2 = bytes[offset + 2].toInt() and 0xFF
            val b3 = bytes[offset + 3].toInt() and 0xFF
            return if (littleEndian) {
                (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
            } else {
                (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            }
        }

        fun isInRange(offset: Int, length: Int): Boolean {
            if (offset < 0 || length < 0) return false
            val end = offset.toLong() + length.toLong()
            return end <= bytes.size.toLong()
        }

        fun copyOfRange(offset: Int, length: Int): ByteArray {
            return bytes.copyOfRange(offset, offset + length)
        }
    }

    private class BitReaderMsb(
        private val data: ByteArray,
        offset: Int,
        length: Int
    ) {
        private val startBit = offset * 8
        private val endBit = (offset + length) * 8
        private var bitPos = startBit

        fun read(count: Int): Int {
            if (count <= 0 || bitPos + count > endBit) return -1
            var value = 0
            for (i in 0 until count) {
                val absoluteBit = bitPos + i
                val byteIndex = absoluteBit / 8
                val bitIndex = 7 - (absoluteBit % 8)
                val bit = (data[byteIndex].toInt() ushr bitIndex) and 1
                value = (value shl 1) or bit
            }
            bitPos += count
            return value
        }
    }
}