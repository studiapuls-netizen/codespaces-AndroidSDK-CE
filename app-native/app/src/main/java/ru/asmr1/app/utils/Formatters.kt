package ru.asmr1.app.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun formatViews(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM просмотров", n / 1_000_000.0)
    n >= 1_000 -> String.format("%.1fK просмотров", n / 1_000.0)
    else -> "$n просмотров"
}

fun formatDuration(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "$m:${if (s < 10) "0$s" else s}"
}

fun timeAgo(dateStr: String): String {
    if (dateStr.isEmpty()) return ""
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = fmt.parse(dateStr) ?: return ""
        val diff = (System.currentTimeMillis() - date.time) / 1000
        when {
            diff < 60 -> "только что"
            diff < 3600 -> "${diff / 60} мин назад"
            diff < 86400 -> "${diff / 3600} ч назад"
            diff < 2592000 -> "${diff / 86400} дн назад"
            diff < 31536000 -> "${diff / 2592000} мес назад"
            else -> "${diff / 31536000} г назад"
        }
    } catch (_: Exception) {
        ""
    }
}


/**
 * Короткий формат БЕЗ слова "просмотров" — для мест, где рядом уже есть подпись.
 * Пример: 1500 -> "1.5K", 1500000 -> "1.5M", 42 -> "42"
 */
fun formatViewsShort(n: Int): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", n / 1_000.0)
    else -> n.toString()
}
