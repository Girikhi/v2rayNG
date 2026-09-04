package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.PanelSubscriptionMetadata
import com.v2ray.ang.dto.entities.SubscriptionItem
import org.junit.Assert.*
import org.junit.Test

class SubscriptionMetadataManagerTest {
    @Test
    fun standardUserinfoProvidesExpiryUsedBytesAndDataLimit() {
        val subscription = SubscriptionItem(remarks = "Account", url = "https://example.com/sub")

        assertTrue(SubscriptionMetadataManager.updateFromHeaders(subscription, mapOf(
            "Subscription-Userinfo" to "upload=1073741824; download=2147483648; total=10737418240; expire=1900000000",
        )))

        val metadata = requireNotNull(subscription.panelMetadata)
        assertEquals(1900000000L, metadata.expireEpochSeconds)
        assertEquals(3221225472L, metadata.dataUsedBytes)
        assertEquals(10737418240L, metadata.dataLimitBytes)
        assertNull(metadata.status)
        assertNull(metadata.workspace)
    }

    @Test
    fun zeroTotalIsRetainedAsSupportedUnlimitedData() {
        val subscription = SubscriptionItem()

        SubscriptionMetadataManager.updateFromHeaders(subscription, mapOf(
            "subscription-userinfo" to "upload=1024; download=2048; total=0",
        ))

        assertEquals(3072L, subscription.panelMetadata?.dataUsedBytes)
        assertEquals(0L, subscription.panelMetadata?.dataLimitBytes)
    }

    @Test
    fun superAdminUnlimitedModeIsShownEvenWithoutByteHeaders() {
        val subscription = SubscriptionItem()

        SubscriptionMetadataManager.updateFromHeaders(subscription, mapOf(
            "X-Panel-Metadata-Version" to "2",
            "X-Panel-Data-Limit" to "unlimited",
            "subscription-userinfo" to "expire=1900000000",
        ))

        assertEquals(0L, subscription.panelMetadata?.dataLimitBytes)
        assertNull(subscription.panelMetadata?.dataUsedBytes)
    }

    @Test
    fun superAdminLimitedModeReadsItsExactByteHeader() {
        val subscription = SubscriptionItem()

        SubscriptionMetadataManager.updateFromHeaders(subscription, mapOf(
            "X-Panel-Metadata-Version" to "2",
            "X-Panel-Data-Limit" to "limited",
            "X-Panel-Data-Limit-Bytes" to "10737418240",
        ))

        assertEquals(10737418240L, subscription.panelMetadata?.dataLimitBytes)
    }

    @Test
    fun panelQuotaAliasesOverrideUserinfoUsage() {
        val subscription = SubscriptionItem()

        assertTrue(SubscriptionMetadataManager.updateFromHeaders(subscription, mapOf(
            "subscription-userinfo" to "upload=10; download=20; total=1000; expire=1900000000",
            "X-Panel-Data-Used" to "125",
            "X-Panel-Data-Limit" to "500",
            "X-Panel-Status" to "ACTIVE",
            "X-Panel-Workspace" to "Team%20One",
        )))

        val metadata = requireNotNull(subscription.panelMetadata)
        assertEquals(125L, metadata.dataUsedBytes)
        assertEquals(500L, metadata.dataLimitBytes)
        assertEquals("active", metadata.status)
        assertEquals("Team One", metadata.workspace)
    }

    @Test
    fun uploadAndDownloadPanelHeadersAreCombinedSafely() {
        val subscription = SubscriptionItem()
        SubscriptionMetadataManager.updateFromHeaders(subscription, mapOf(
            "X-Panel-Upload-Bytes" to "40",
            "X-Panel-Download-Bytes" to "60",
            "X-Panel-Total-Bytes" to "1000",
        ))

        assertEquals(100L, subscription.panelMetadata?.dataUsedBytes)
        assertEquals(1000L, subscription.panelMetadata?.dataLimitBytes)
    }

    @Test
    fun unsupportedResponseClearsOldPanelSections() {
        val subscription = SubscriptionItem(panelMetadata = PanelSubscriptionMetadata(
            expiresAt = "2030-01-01",
            workspace = "Old workspace",
            status = "active",
            telegramUrl = "https://t.me/old",
            dataUsedBytes = 25,
            dataLimitBytes = 100,
        ))

        assertTrue(SubscriptionMetadataManager.updateFromHeaders(subscription, mapOf(
            "content-type" to "text/plain",
        )))
        assertNull(subscription.panelMetadata)
    }

    @Test
    fun partialMetadataDoesNotLeaveUnsupportedStaleFieldsVisible() {
        val subscription = SubscriptionItem(panelMetadata = PanelSubscriptionMetadata(
            expiresAt = "2030-01-01", workspace = "Old", status = "active", dataLimitBytes = 100,
        ))

        SubscriptionMetadataManager.updateFromHeaders(subscription, mapOf(
            "X-Panel-User" to "New%20User",
        ))

        val metadata = requireNotNull(subscription.panelMetadata)
        assertEquals("New User", metadata.user)
        assertNull(metadata.expiresAt)
        assertNull(metadata.workspace)
        assertNull(metadata.status)
        assertNull(metadata.dataLimitBytes)
    }

    @Test
    fun failedResponseWithoutMetadataKeepsLastSuccessfulFields() {
        val old = PanelSubscriptionMetadata(
            expiresAt = "2030-01-01",
            workspace = "Workspace",
            status = "active",
            dataUsedBytes = 25,
            dataLimitBytes = 100,
        )
        val subscription = SubscriptionItem(panelMetadata = old)

        assertFalse(SubscriptionMetadataManager.updateFromHeaders(
            subscription,
            mapOf("content-type" to "text/plain"),
            replaceMissingFields = false,
        ))
        assertSame(old, subscription.panelMetadata)
    }
}
