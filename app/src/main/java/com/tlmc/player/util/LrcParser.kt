package com.tlmc.player.util

import com.tlmc.player.data.model.LrcLine

object LrcParser {

    /**
     * Parse LRC lyrics content into a sorted list of LrcLine.
     * Supports standard LRC format: [mm:ss.xx] text
     * Also supports [mm:ss.xxx] and [mm:ss] variations.
     * Multiple timestamps per line are supported: [00:01.00][00:15.00] text
     */
    fun parse(content: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        val timeRegex = Regex("""\[(\d{1,3}):(\d{2})([.:]\d{1,3})?]""")

        for (rawLine in content.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            // Find all timestamps in the line
            val matches = timeRegex.findAll(trimmed).toList()
            if (matches.isEmpty()) continue

            // Text is everything after the last timestamp tag
            val lastMatch = matches.last()
            val text = trimmed.substring(lastMatch.range.last + 1).trim()

            // Create an LrcLine for each timestamp
            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                val fracStr = match.groupValues[3] // e.g. ".50" or ":123" or ""
                val fracMs = parseFraction(fracStr)

                val timeMs = minutes * 60_000 + seconds * 1000 + fracMs
                lines.add(LrcLine(timeMs, text))
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    /**
     * Parse the fractional part like ".50", ".500", ":12", "" into milliseconds.
     */
    private fun parseFraction(frac: String): Long {
        if (frac.isEmpty()) return 0
        // Remove leading '.' or ':'
        val digits = frac.substring(1)
        val value = digits.toLongOrNull() ?: return 0
        return when (digits.length) {
            1 -> value * 100   // e.g. ".5" -> 500ms
            2 -> value * 10    // e.g. ".50" -> 500ms
            3 -> value         // e.g. ".500" -> 500ms
            else -> value
        }
    }

    /**
     * Find the index of the current lyrics line for the given playback position.
     * Returns -1 if no line is active yet.
     */
    fun findCurrentLineIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        // Binary search for the last line whose timeMs <= positionMs
        var low = 0
        var high = lines.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}


