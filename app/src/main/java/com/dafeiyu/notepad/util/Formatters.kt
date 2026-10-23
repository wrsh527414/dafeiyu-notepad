package com.dafeiyu.notepad.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {

    private val dt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun dateTime(ms: Long): String = dt.format(Date(ms))
    fun date(ms: Long): String = d.format(Date(ms))

    /** 人类可读的大小：1.2 KB / 340 B */
    fun size(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    /** 相对时间：刚刚 / 3分钟前 / 2小时前 / 3天前 */
    fun relative(ms: Long): String {
        val diff = System.currentTimeMillis() - ms
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3600_000 -> "${diff / 60_000} 分钟前"
            diff < 86_400_000 -> "${diff / 3600_000} 小时前"
            diff < 30L * 86_400_000 -> "${diff / 86_400_000} 天前"
            else -> date(ms)
        }
    }
}
