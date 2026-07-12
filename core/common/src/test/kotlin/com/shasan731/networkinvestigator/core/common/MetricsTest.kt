package com.shasan731.networkinvestigator.core.common
import org.junit.Assert.assertEquals
import org.junit.Test
class MetricsTest {
    @Test fun `calculates loss and jitter`() { assertEquals(25.0, NetworkMetrics.packetLossPercent(4, 3), 0.001); assertEquals(15.0, NetworkMetrics.jitterMs(listOf(10, 30, 40)), 0.001) }
    @Test fun `retention cutoff supports keep forever`() { assertEquals(null, RetentionPolicy.cutoffEpochMs(100, null)); assertEquals(100L - 7 * 86_400_000L, RetentionPolicy.cutoffEpochMs(100, 7)) }
}

