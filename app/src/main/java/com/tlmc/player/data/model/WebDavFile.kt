package com.tlmc.player.data.model

import java.util.Date

data class WebDavFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val contentType: String = "",
    val lastModified: Date? = null
) {
    companion object {
        private val AUDIO_EXTENSIONS = setOf("flac", "mp3", "wav", "ogg", "m4a", "aac")
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "tif", "tiff", "bmp", "webp")
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "mov", "avi", "m4v", "mpg", "mpeg", "vob", "ts", "m2ts", "mts"
        )
        private val TEXT_EXTENSIONS = setOf("txt", "log", "md", "nfo", "ini", "cfg", "lrc")
    }

    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()

    val isAudio: Boolean
        get() = extension in AUDIO_EXTENSIONS

    val isImage: Boolean
        get() = extension in IMAGE_EXTENSIONS

    val isVideo: Boolean
        get() = extension in VIDEO_EXTENSIONS

    val isText: Boolean
        get() = extension in TEXT_EXTENSIONS

    val isCue: Boolean
        get() = extension == "cue"

    val nameWithoutExtension: String
        get() = name.substringBeforeLast('.')
}

