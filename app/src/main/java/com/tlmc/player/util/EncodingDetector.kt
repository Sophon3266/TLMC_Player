package com.tlmc.player.util

object EncodingDetector {

    /**
     * Detect the encoding of a byte array.
     * Supports: UTF-8 (with/without BOM), UTF-16 (LE/BE), GBK, Shift-JIS
     */
    fun detect(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "UTF-8"

        // Check BOM (Byte Order Mark)
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return "UTF-8"
        }

        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return "UTF-16LE"
            }
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return "UTF-16BE"
            }
        }

        // Try UTF-8 validation
        if (isValidUtf8(bytes)) {
            // Check if it contains any multi-byte UTF-8 sequences
            val hasMultiByte = bytes.any { it.toInt() and 0x80 != 0 }
            if (!hasMultiByte) return "UTF-8" // Pure ASCII
            
            // Has multi-byte and is valid UTF-8
            return "UTF-8"
        }

        // Try to detect Shift-JIS vs GBK
        val shiftJisScore = scoreShiftJis(bytes)
        val gbkScore = scoreGbk(bytes)

        return when {
            shiftJisScore > gbkScore -> "Shift_JIS"
            gbkScore > shiftJisScore -> "GBK"
            shiftJisScore > 0 -> "Shift_JIS"
            else -> "GBK" // Default fallback for non-UTF8 CJK text
        }
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b <= 0x7F -> i++
                b in 0xC2..0xDF -> {
                    if (i + 1 >= bytes.size) return false
                    if (bytes[i + 1].toInt() and 0xC0 != 0x80) return false
                    i += 2
                }
                b in 0xE0..0xEF -> {
                    if (i + 2 >= bytes.size) return false
                    if (bytes[i + 1].toInt() and 0xC0 != 0x80) return false
                    if (bytes[i + 2].toInt() and 0xC0 != 0x80) return false
                    i += 3
                }
                b in 0xF0..0xF4 -> {
                    if (i + 3 >= bytes.size) return false
                    if (bytes[i + 1].toInt() and 0xC0 != 0x80) return false
                    if (bytes[i + 2].toInt() and 0xC0 != 0x80) return false
                    if (bytes[i + 3].toInt() and 0xC0 != 0x80) return false
                    i += 4
                }
                else -> return false
            }
        }
        return true
    }

    private fun scoreShiftJis(bytes: ByteArray): Int {
        var score = 0
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b <= 0x7F -> i++ // ASCII
                b in 0xA1..0xDF -> { // Half-width katakana
                    score += 1
                    i++
                }
                (b in 0x81..0x9F || b in 0xE0..0xEF) && i + 1 < bytes.size -> {
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    if (b2 in 0x40..0x7E || b2 in 0x80..0xFC) {
                        score += 2
                        i += 2
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }
        return score
    }

    private fun scoreGbk(bytes: ByteArray): Int {
        var score = 0
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b <= 0x7F -> i++ // ASCII
                b in 0x81..0xFE && i + 1 < bytes.size -> {
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    if (b2 in 0x40..0xFE && b2 != 0x7F) {
                        score += 2
                        i += 2
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }
        return score
    }
}

