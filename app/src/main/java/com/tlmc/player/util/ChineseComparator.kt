package com.tlmc.player.util

import java.text.Collator
import java.util.Locale

/**
 * 中文拼音排序比较器。
 *
 * 使用 Collator(Locale.CHINA) 对字符串进行比较：
 * - 中文字符（简体、繁体、日文汉字）统一按拼音排序
 * - 拉丁字母按字母顺序排序，不区分大小写
 * - 数字、符号等按默认 Unicode 规则排序
 *
 * 注意：Collator 不是线程安全的，因此每次需要时创建新实例。
 */
object ChineseComparator {

    /**
     * 创建一个按中文拼音排序的字符串比较器（不区分大小写）。
     */
    fun create(): Comparator<String> {
        val collator = Collator.getInstance(Locale.CHINA).apply {
            strength = Collator.SECONDARY // 不区分大小写
        }
        return Comparator { s1, s2 -> collator.compare(s1, s2) }
    }
}

