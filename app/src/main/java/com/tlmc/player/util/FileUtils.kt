package com.tlmc.player.util

import com.tlmc.player.data.model.WebDavFile

object FileUtils {

    fun getFileIcon(file: WebDavFile): Int {
        return when {
            file.isDirectory -> com.tlmc.player.R.drawable.ic_folder
            file.isAudio -> com.tlmc.player.R.drawable.ic_music
            file.isVideo -> com.tlmc.player.R.drawable.ic_video
            file.isImage -> com.tlmc.player.R.drawable.ic_image
            file.isText || file.isCue -> com.tlmc.player.R.drawable.ic_text
            else -> com.tlmc.player.R.drawable.ic_file
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        return "%.1f %s".format(size, units[unitIndex])
    }

    fun getParentPath(path: String): String {
        val trimmed = path.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        return if (lastSlash > 0) trimmed.substring(0, lastSlash) else "/"
    }

    fun getDirectoryPath(filePath: String): String {
        val lastSlash = filePath.lastIndexOf('/')
        return if (lastSlash >= 0) filePath.substring(0, lastSlash) else ""
    }

    fun getMimeType(file: WebDavFile): String {
        return when (file.extension) {
            "flac" -> "audio/flac"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mpg", "mpeg" -> "video/mpeg"
            "vob" -> "video/dvd"
            "ts" -> "video/mp2t"
            "m2ts", "mts" -> "video/mp2t"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "tif", "tiff" -> "image/tiff"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}

