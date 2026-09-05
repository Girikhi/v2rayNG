package com.v2ray.ang.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AccountDataFormatterTest {
    @Test
    fun panelGibibytesAreNotInflatedByDecimalUnits() {
        assertEquals("10 GB", AccountDataFormatter.formatBytes(10L * 1024 * 1024 * 1024, Locale.US))
    }

    @Test
    fun panelTebibytesKeepTheirExpectedValue() {
        assertEquals("2 TB", AccountDataFormatter.formatBytes(2L * 1024 * 1024 * 1024 * 1024, Locale.US))
    }

    @Test
    fun usefulFractionalPrecisionIsPreserved() {
        assertEquals("9.5 GB", AccountDataFormatter.formatBytes(19L * 512 * 1024 * 1024, Locale.US))
        assertEquals("1.5 KB", AccountDataFormatter.formatBytes(1536, Locale.US))
    }

    @Test
    fun remainingDataPercentageUsesConsumedBytes() {
        assertEquals(75, AccountDataFormatter.remainingPercent(100, 25))
        assertEquals(0, AccountDataFormatter.remainingPercent(100, 150))
    }

    @Test
    fun remainingDataPercentageRequiresFiniteQuotaAndUsage() {
        assertEquals(null, AccountDataFormatter.remainingPercent(0, 0))
        assertEquals(null, AccountDataFormatter.remainingPercent(100, null))
        assertEquals(null, AccountDataFormatter.remainingPercent(100, -1))
    }
}
