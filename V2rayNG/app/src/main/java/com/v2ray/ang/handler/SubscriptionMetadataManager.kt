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

    private val supportedStatuses = setOf("active", "scheduled", "disabled", "expired")

    /** Updates persisted panel metadata from case-normalized HTTP response headers. */
    fun updateFromHeaders(subscription: SubscriptionItem, headers: Map<String, String>): Boolean {
        val normalizedHeaders = headers.mapKeys { it.key.lowercase(Locale.ROOT) }
        val hasPanelMetadata = normalizedHeaders.keys.any { it.startsWith("x-panel-") }
        val userInfoExpiry = parseSubscriptionUserinfoExpiry(
            normalizedHeaders[HEADER_SUBSCRIPTION_USERINFO]
        )
        val refreshIntervalMinutes = parseRefreshIntervalMinutes(
            normalizedHeaders[HEADER_PROFILE_UPDATE_INTERVAL]
        )

        if (!hasPanelMetadata && userInfoExpiry == null && refreshIntervalMinutes == null) {
            return false
        }

        val old = subscription.panelMetadata
        val expiresAt = normalizedHeaders[HEADER_EXPIRES_AT]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: old?.expiresAt
        val parsedHeaderExpiry = expiresAt?.let(::parseExpiryEpochSeconds)

        val updated = PanelSubscriptionMetadata(
            expiresAt = expiresAt,
            expireEpochSeconds = parsedHeaderExpiry ?: userInfoExpiry ?: old?.expireEpochSeconds,
            workspace = decodedHeader(normalizedHeaders[HEADER_WORKSPACE]) ?: old?.workspace,
            user = decodedHeader(normalizedHeaders[HEADER_USER]) ?: old?.user,
            status = normalizedHeaders[HEADER_STATUS]
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it in supportedStatuses }
                ?: old?.status,
            startsOn = normalizedHeaders[HEADER_STARTS_ON]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: old?.startsOn,
            telegramUrl = telegramUrl(normalizedHeaders[HEADER_TELEGRAM_URL]) ?: old?.telegramUrl,
            metadataVersion = normalizedHeaders[HEADER_METADATA_VERSION]
                ?.trim()
                ?.toIntOrNull()
                ?: old?.metadataVersion,
            refreshIntervalMinutes = refreshIntervalMinutes ?: old?.refreshIntervalMinutes,
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

    private fun parseSubscriptionUserinfoExpiry(value: String?): Long? {
        val expiry = value
            ?.split(';')
            ?.asSequence()
            ?.map { it.trim().split('=', limit = 2) }
            ?.firstOrNull { it.size == 2 && it[0].equals("expire", ignoreCase = true) }
            ?.get(1)
            ?.trim()
            ?.toLongOrNull()
        return expiry?.takeIf { it > 0L }
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
