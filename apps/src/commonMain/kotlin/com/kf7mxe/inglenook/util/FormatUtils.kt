package com.kf7mxe.inglenook.util

/**
 * Format ticks (10,000,000 ticks = 1 second) into "H:MM:SS" or "M:SS" format.
 */
fun formatDuration(ticks: Long): String {
    val totalSeconds = ticks / 10_000_000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

/**
 * Format ticks into short form: "Xh Ym" or "Ym".
 */
fun formatDurationShort(ticks: Long): String {
    val totalSeconds = ticks / 10_000_000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * Calculate progress percentage from position and duration ticks.
 */
fun calculateProgressPercent(positionTicks: Long, durationTicks: Long): Int {
    return if (durationTicks > 0) ((positionTicks.toFloat() / durationTicks) * 100).toInt().coerceAtMost(100) else 0
}

/**
 * Calculate progress ratio (0f..1f) from position and duration ticks.
 */
fun calculateProgressRatio(positionTicks: Long, durationTicks: Long): Float {
    return if (durationTicks > 0) (positionTicks.toFloat() / durationTicks).coerceAtMost(1f) else 0f
}

/**
 * Format a book count as "1 book" or "N books".
 */
fun formatBookCount(count: Int): String {
    return if (count == 1) "1 book" else "$count books"
}

/**
 * Format byte count as human-readable file size.
 */
fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> {
            val gb = bytes.toDouble() / (1024 * 1024 * 1024)
            "${(gb * 100).toLong() / 100.0} GB"
        }
    }
}
