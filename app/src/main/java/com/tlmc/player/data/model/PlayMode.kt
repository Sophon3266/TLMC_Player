package com.tlmc.player.data.model

enum class PlayMode {
    SEQUENTIAL,   // 顺序播放 (播完停止)
    REPEAT_ALL,   // 列表循环
    REPEAT_ONE,   // 单曲循环
    SHUFFLE;      // 随机播放

    fun next(): PlayMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}

