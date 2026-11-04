package com.tlmc.player.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object ArtworkUtils {

    /**
     * 将 MediaMetadata.artworkData 解码为 Bitmap；数据缺失或损坏时返回 null。
     */
    fun decodeArtwork(data: ByteArray?): Bitmap? {
        if (data == null || data.isEmpty()) return null
        return try {
            BitmapFactory.decodeByteArray(data, 0, data.size)
        } catch (_: Exception) {
            null
        }
    }
}
