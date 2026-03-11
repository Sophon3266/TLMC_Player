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
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()

    val isAudio: Boolean
        get() = extension in listOf("flac", "mp3", "wav", "ogg", "m4a", "aac")

    val isImage: Boolean
        get() = extension in listOf("png", "jpg", "jpeg", "tif", "tiff", "bmp", "webp")

    val isText: Boolean
        get() = extension in listOf("txt", "log", "md", "nfo", "ini", "cfg")

    val isCue: Boolean
        get() = extension == "cue"

    val nameWithoutExtension: String
        get() = name.substringBeforeLast('.')
}

