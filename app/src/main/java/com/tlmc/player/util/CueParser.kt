package com.tlmc.player.util

import com.tlmc.player.data.model.CueSheet
import com.tlmc.player.data.model.CueTrack

object CueParser {

    fun parse(content: String): CueSheet {
        val lines = content.lines().map { it.trim() }

        var globalPerformer = ""
        var globalTitle = ""
        var fileName = ""
        val tracks = mutableListOf<CueTrack>()

        var currentTrackNumber = 0
        var currentTrackTitle = ""
        var currentTrackPerformer = ""
        var currentTrackStartMs: Long = -1
        var inTrack = false

        for (line in lines) {
            when {
                line.startsWith("PERFORMER ", ignoreCase = true) && !inTrack -> {
                    globalPerformer = extractQuotedValue(line)
                }
                line.startsWith("TITLE ", ignoreCase = true) && !inTrack -> {
                    globalTitle = extractQuotedValue(line)
                }
                line.startsWith("FILE ", ignoreCase = true) -> {
                    fileName = extractQuotedValue(line)
                }
                line.startsWith("TRACK ", ignoreCase = true) -> {
                    // Save previous track if exists
                    if (inTrack && currentTrackStartMs >= 0) {
                        tracks.add(
                            CueTrack(
                                number = currentTrackNumber,
                                title = currentTrackTitle,
                                performer = currentTrackPerformer.ifEmpty { globalPerformer },
                                startTimeMs = currentTrackStartMs
                            )
                        )
                    }
                    inTrack = true
                    currentTrackNumber = extractTrackNumber(line)
                    currentTrackTitle = ""
                    currentTrackPerformer = ""
                    currentTrackStartMs = -1
                }
                line.startsWith("TITLE ", ignoreCase = true) && inTrack -> {
                    currentTrackTitle = extractQuotedValue(line)
                }
                line.startsWith("PERFORMER ", ignoreCase = true) && inTrack -> {
                    currentTrackPerformer = extractQuotedValue(line)
                }
                line.startsWith("INDEX 01 ", ignoreCase = true) -> {
                    currentTrackStartMs = parseTimestamp(line.substringAfter("INDEX 01 ").trim())
                }
            }
        }

        // Add last track
        if (inTrack && currentTrackStartMs >= 0) {
            tracks.add(
                CueTrack(
                    number = currentTrackNumber,
                    title = currentTrackTitle,
                    performer = currentTrackPerformer.ifEmpty { globalPerformer },
                    startTimeMs = currentTrackStartMs
                )
            )
        }

        // Set end times: each track ends where the next begins
        val tracksWithEndTimes = tracks.mapIndexed { index, track ->
            val endTime = if (index < tracks.size - 1) {
                tracks[index + 1].startTimeMs
            } else {
                -1L // Last track plays to end of file
            }
            track.copy(endTimeMs = endTime)
        }

        return CueSheet(
            file = fileName,
            performer = globalPerformer,
            title = globalTitle,
            tracks = tracksWithEndTimes
        )
    }

    private fun extractQuotedValue(line: String): String {
        val startQuote = line.indexOf('"')
        val endQuote = line.lastIndexOf('"')
        return if (startQuote >= 0 && endQuote > startQuote) {
            line.substring(startQuote + 1, endQuote)
        } else {
            line.substringAfter(' ').trim()
        }
    }

    private fun extractTrackNumber(line: String): Int {
        val parts = line.split("\\s+".toRegex())
        return if (parts.size >= 2) parts[1].toIntOrNull() ?: 0 else 0
    }

    /**
     * Parse CUE timestamp in MM:SS:FF format (FF = frames, 75 frames per second)
     * Returns time in milliseconds
     */
    private fun parseTimestamp(timestamp: String): Long {
        val parts = timestamp.split(":")
        if (parts.size != 3) return 0

        val minutes = parts[0].toLongOrNull() ?: 0
        val seconds = parts[1].toLongOrNull() ?: 0
        val frames = parts[2].toLongOrNull() ?: 0

        return (minutes * 60 * 1000) + (seconds * 1000) + (frames * 1000 / 75)
    }
}

