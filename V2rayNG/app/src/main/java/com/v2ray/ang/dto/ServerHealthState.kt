package com.v2ray.ang.dto

enum class ServerHealthPhase {
    CHECKING,
    REFRESHING,
    READY,
    NO_WORKING_SERVERS,
}

data class ServerHealthState(
    val subscriptionId: String,
    val phase: ServerHealthPhase,
    val workingCount: Int = 0,
    val totalCount: Int = 0,
)
