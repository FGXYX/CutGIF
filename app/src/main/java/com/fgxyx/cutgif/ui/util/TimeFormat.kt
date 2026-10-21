package com.fgxyx.cutgif.ui.util

import java.util.Locale
import kotlin.math.roundToLong

fun formatTime(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalTenths = (safeMs + 50L) / 100L
    val tenths = totalTenths % 10
    val totalSeconds = totalTenths / 10
    val seconds = totalSeconds % 60
    val totalMinutes = totalSeconds / 60
    val minutes = totalMinutes % 60
    val hours = totalMinutes / 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d.%d", hours, minutes, seconds, tenths)
    } else {
        String.format(Locale.US, "%d:%02d.%d", minutes, seconds, tenths)
    }
}

fun formatClock(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatRuler(seconds: Float): String {
    if (seconds < 60f) {
        return if (seconds % 1f < 0.01f) {
            "${seconds.roundToLong()}s"
        } else {
            String.format(Locale.US, "%.1fs", seconds)
        }
    }
    val total = seconds.roundToLong()
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}

fun formatSpeed(speed: Float): String {
    return if (speed % 1f == 0f) {
        String.format(Locale.US, "%.1f", speed)
    } else {
        String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
    }
}
