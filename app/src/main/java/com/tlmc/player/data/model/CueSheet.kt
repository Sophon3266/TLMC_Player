package com.tlmc.player.data.model

data class CueSheet(
    val file: String,
    val performer: String,
    val title: String,
    val tracks: List<CueTrack>
)

data class CueTrack(
    val number: Int,
    val title: String,
    val performer: String,
    val startTimeMs: Long,
    val endTimeMs: Long = -1L // -1 means end of file
) {
    val displayTitle: String
        get() = if (performer.isNotEmpty()) "$performer - $title" else title

    val formattedStart: String
        get() = formatTime(startTimeMs)

    val formattedDuration: String
        get() = if (endTimeMs > 0) formatTime(endTimeMs - startTimeMs) else "??:??"

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}

