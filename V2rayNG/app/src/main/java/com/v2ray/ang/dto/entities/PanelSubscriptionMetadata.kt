package com.v2ray.ang.dto.entities

data class PanelSubscriptionMetadata(
    var expiresAt: String? = null,
    var expireEpochSeconds: Long? = null,
    var workspace: String? = null,
    var user: String? = null,
    var status: String? = null,
    var startsOn: String? = null,
    var telegramUrl: String? = null,
    var metadataVersion: Int? = null,
    var refreshIntervalMinutes: Long? = null,
    var receivedAt: Long = System.currentTimeMillis(),
)
