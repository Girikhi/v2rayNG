package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.PanelSubscriptionMetadata
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.Utils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToLong

object SubscriptionMetadataManager {
    private const val HEADER_EXPIRES_AT = "x-panel-expires-at"
    private const val HEADER_SUBSCRIPTION_USERINFO = "subscription-userinfo"
    private const val HEADER_WORKSPACE = "x-panel-workspace"
    private const val HEADER_USER = "x-panel-user"
    private const val HEADER_STATUS = "x-panel-status"
    private const val HEADER_STARTS_ON = "x-panel-starts-on"
    private const val HEADER_TELEGRAM_URL = "x-panel-telegram-url"
    private const val HEADER_METADATA_VERSION = "x-panel-metadata-version"
    private const val HEADER_PROFILE_UPDATE_INTERVAL = "profile-update-interval"
    private val dataUsedHeaders = listOf(
        "x-panel-data-used", "x-panel-traffic-used", "x-panel-used-bytes", "x-panel-used",
    )
    private val dataLimitHeaders = listOf(
        "x-panel-data-limit", "x-panel-traffic-limit", "x-panel-total-bytes", "x-panel-total",
    )
    private val uploadHeaders = listOf("x-panel-upload", "x-panel-upload-bytes")
    private val downloadHeaders = listOf("x-panel-download", "x-panel-download-bytes")

    private val supportedStatuses = setOf("active", "scheduled", "disabled", "expired")

    /** Updates persisted panel metadata from case-normalized HTTP response headers. */
    fun updateFromHeaders(
        subscription: SubscriptionItem,
        headers: Map<String, String>,
        replaceMissingFields: Boolean = true,
    ): Boolean {
        val normalizedHeaders = headers.mapKeys { it.key.lowercase(Locale.ROOT) }
        val hasPanelMetadata = normalizedHeaders.keys.any { it.startsWith("x-panel-") }
        val userInfo = parseSubscriptionUserinfo(
            normalizedHeaders[HEADER_SUBSCRIPTION_USERINFO]
        )
        val refreshIntervalMinutes = parseRefreshIntervalMinutes(
            normalizedHeaders[HEADER_PROFILE_UPDATE_INTERVAL]
        )

        if (!hasPanelMetadata && userInfo == null && refreshIntervalMinutes == null) {
            if (replaceMissingFields && subscription.panelMetadata != null) {
                subscription.panelMetadata = null
                return true
            }
            return false
        }

        val old = subscription.panelMetadata
        val headerExpiresAt = normalizedHeaders[HEADER_EXPIRES_AT]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val parsedHeaderExpiry = headerExpiresAt?.let(::parseExpiryEpochSeconds)
        val upload = headerByteCount(normalizedHeaders, uploadHeaders) ?: userInfo?.uploadBytes
        val download = headerByteCount(normalizedHeaders, downloadHeaders) ?: userInfo?.downloadBytes
        val used = headerByteCount(normalizedHeaders, dataUsedHeaders)
            ?: addByteCounts(upload, download)
            ?: old?.dataUsedBytes.takeUnless { replaceMissingFields }
        val total = headerByteCount(normalizedHeaders, dataLimitHeaders)
            ?: userInfo?.totalBytes
            ?: old?.dataLimitBytes.takeUnless { replaceMissingFields }

        val updated = PanelSubscriptionMetadata(
            expiresAt = headerExpiresAt ?: old?.expiresAt.takeUnless { replaceMissingFields },
            expireEpochSeconds = parsedHeaderExpiry
                ?: userInfo?.expireEpochSeconds
                ?: old?.expireEpochSeconds.takeUnless { replaceMissingFields },
            workspace = decodedHeader(normalizedHeaders[HEADER_WORKSPACE])
                ?: old?.workspace.takeUnless { replaceMissingFields },
            user = decodedHeader(normalizedHeaders[HEADER_USER])
                ?: old?.user.takeUnless { replaceMissingFields },
            status = normalizedHeaders[HEADER_STATUS]
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it in supportedStatuses }
                ?: old?.status.takeUnless { replaceMissingFields },
            startsOn = normalizedHeaders[HEADER_STARTS_ON]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: old?.startsOn.takeUnless { replaceMissingFields },
            telegramUrl = telegramUrl(normalizedHeaders[HEADER_TELEGRAM_URL])
                ?: old?.telegramUrl.takeUnless { replaceMissingFields },
            metadataVersion = normalizedHeaders[HEADER_METADATA_VERSION]
                ?.trim()
                ?.toIntOrNull()
                ?: old?.metadataVersion.takeUnless { replaceMissingFields },
            refreshIntervalMinutes = refreshIntervalMinutes
                ?: old?.refreshIntervalMinutes.takeUnless { replaceMissingFields },
            dataUsedBytes = used,
            dataLimitBytes = total,
            receivedAt = System.currentTimeMillis(),
        )

        val metadataChanged = old?.copy(receivedAt = updated.receivedAt) != updated
        val guidedInterval = refreshIntervalMinutes?.coerceAtLeast(
            AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES
        )
        val intervalChanged = guidedInterval != null && subscription.updateInterval != guidedInterval
        if (intervalChanged) {
            subscription.updateInterval = guidedInterval!!
        }
        subscription.panelMetadata = updated
        return metadataChanged || intervalChanged
    }

    private fun decodedHeader(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return Utils.decodeURIComponent(raw).trim().takeIf { it.isNotEmpty() }
    }

    private fun telegramUrl(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val decoded = Utils.decodeURIComponent(raw).trim()
        return decoded.takeIf {
            it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("http://", ignoreCase = true) ||
                it.startsWith("tg://", ignoreCase = true)
        }
    }

    private data class SubscriptionUserinfo(
        val expireEpochSeconds: Long?,
        val uploadBytes: Long?,
        val downloadBytes: Long?,
        val totalBytes: Long?,
    )

    private fun parseSubscriptionUserinfo(value: String?): SubscriptionUserinfo? {
        val values = value?.split(';')?.mapNotNull { part ->
            part.trim().split('=', limit = 2).takeIf { it.size == 2 }
                ?.let { it[0].trim().lowercase(Locale.ROOT) to it[1].trim() }
        }?.toMap().orEmpty()
        if (values.isEmpty()) return null
        val expiry = values["expire"]?.toLongOrNull()?.takeIf { it > 0L }
        val upload = parseByteCount(values["upload"])
        val download = parseByteCount(values["download"])
        val total = parseByteCount(values["total"])
        return SubscriptionUserinfo(expiry, upload, download, total)
            .takeIf { listOf(expiry, upload, download, total).any { it != null } }
    }

    private fun headerByteCount(headers: Map<String, String>, names: List<String>): Long? =
        names.firstNotNullOfOrNull { parseByteCount(headers[it]) }

    private fun parseByteCount(value: String?): Long? = value?.trim()?.toLongOrNull()?.takeIf { it >= 0L }

    private fun addByteCounts(upload: Long?, download: Long?): Long? {
        if (upload == null && download == null) return null
        return runCatching { Math.addExact(upload ?: 0L, download ?: 0L) }.getOrDefault(Long.MAX_VALUE)
    }

    private fun parseRefreshIntervalMinutes(value: String?): Long? {
        val hours = value?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        return (hours * 60.0).roundToLong().coerceAtLeast(1L)
    }

    private fun parseExpiryEpochSeconds(value: String): Long? {
        value.toLongOrNull()?.let { numeric ->
            if (numeric <= 0L) return null
            return if (numeric > 10_000_000_000L) numeric / 1000L else numeric
        }
        runCatching { Instant.parse(value).epochSecond }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(value).toEpochSecond() }.getOrNull()?.let { return it }
        runCatching { LocalDateTime.parse(value).toEpochSecond(ZoneOffset.UTC) }.getOrNull()?.let { return it }
        return runCatching {
            LocalDate.parse(value).plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        }.getOrNull()
    }
}
