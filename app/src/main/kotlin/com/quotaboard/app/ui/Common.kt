package com.quotaboard.app.ui

import androidx.compose.ui.graphics.Color

enum class Level { NORMAL, WARN, DANGER }

fun levelFor(percent: Double?): Level = when {
    percent == null -> Level.NORMAL
    percent >= 95 -> Level.DANGER
    percent >= 80 -> Level.WARN
    else -> Level.NORMAL
}

fun levelColor(level: Level): Color = when (level) {
    Level.NORMAL -> Color(0xFF1A73E8)
    Level.WARN -> Color(0xFFF9A825)
    Level.DANGER -> Color(0xFFE53935)
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "即将重置"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h >= 24 -> "${h / 24} 天 ${h % 24} 小时"
        h > 0 -> "$h 小时 $m 分"
        m > 0 -> "$m 分 ${s % 60} 秒"
        else -> "$s 秒"
    }
}

fun formatMoney(amount: Double, currency: String): String = when (currency.uppercase()) {
    "CNY", "¥" -> "¥%.2f".format(amount)
    "USD", "$" -> "$%.2f".format(amount)
    "EUR" -> "€%.2f".format(amount)
    else -> "%.2f %s".format(amount, currency)
}
