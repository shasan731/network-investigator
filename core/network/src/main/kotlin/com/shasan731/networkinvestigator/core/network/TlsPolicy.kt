package com.shasan731.networkinvestigator.core.network

import java.time.Duration
import java.time.Instant

object TlsPolicy {
    fun daysUntilExpiration(notAfterEpochMs: Long, nowEpochMs: Long): Long = Duration.between(Instant.ofEpochMilli(nowEpochMs), Instant.ofEpochMilli(notAfterEpochMs)).toDays()
    fun hostnameMatches(hostname: String, pattern: String): Boolean {
        val host = hostname.trimEnd('.').lowercase(); val candidate = pattern.trimEnd('.').lowercase()
        if (!candidate.startsWith("*.")) return host == candidate
        val suffix = candidate.removePrefix("*.")
        return host.endsWith(".$suffix") && host.count { it == '.' } == suffix.count { it == '.' } + 1
    }
}
