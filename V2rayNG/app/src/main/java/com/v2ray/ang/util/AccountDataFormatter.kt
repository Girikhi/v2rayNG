package com.v2ray.ang.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/** Formats panel byte counts with the binary units used by subscription providers. */
internal object AccountDataFormatter {
    private const val UNIT_SIZE = 1024.0
    private val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")

    fun formatBytes(bytes: Long, locale: Locale): String {
        val safeBytes = bytes.coerceAtLeast(0L)
        var value = safeBytes.toDouble()
        var unitIndex = 0
        while (value >= UNIT_SIZE && unitIndex < units.lastIndex) {
            value /= UNIT_SIZE
            unitIndex += 1
        }

        val isWhole = abs(value - value.toLong()) < 0.000_000_1
        val pattern = if (isWhole) "#,##0" else "#,##0.#"
        val number = DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale)).format(value)
        return "$number ${units[unitIndex]}"
    }

    fun remainingPercent(totalBytes: Long?, usedBytes: Long?): Int? {
        val total = totalBytes?.takeIf { it > 0L } ?: return null
        val used = usedBytes?.takeIf { it >= 0L } ?: return null
        val remaining = total - used.coerceAtMost(total)
        return ((remaining.toDouble() / total) * 100)
            .toInt()
            .coerceIn(0, 100)
    }
}
