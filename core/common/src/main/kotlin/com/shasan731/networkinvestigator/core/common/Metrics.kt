package com.shasan731.networkinvestigator.core.common

import kotlin.math.abs

object NetworkMetrics {
    fun packetLossPercent(attempts: Int, successes: Int): Double {
        require(attempts >= 0 && successes in 0..attempts)
        return if (attempts == 0) 0.0 else (attempts - successes) * 100.0 / attempts
    }

    fun jitterMs(samples: List<Long>): Double {
        if (samples.size < 2) return 0.0
        return samples.zipWithNext { a, b -> abs(b - a).toDouble() }.average()
    }
}

object RetentionPolicy {
    fun cutoffEpochMs(nowEpochMs: Long, days: Int?): Long? {
        require(days == null || days > 0)
        return days?.let { nowEpochMs - it * 86_400_000L }
    }
}

