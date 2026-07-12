package com.shasan731.networkinvestigator.core.common

import java.util.ArrayDeque

data class SafeLogEntry(val timestampEpochMs: Long, val category: String, val code: String)
object PrivacySafeLog {
    private const val CAPACITY = 200
    private val entries = ArrayDeque<SafeLogEntry>(CAPACITY)
    @Synchronized fun record(category: String, code: String) { if (entries.size == CAPACITY) entries.removeFirst(); entries.addLast(SafeLogEntry(System.currentTimeMillis(), category.take(40), code.take(80))) }
    @Synchronized fun snapshot(): List<SafeLogEntry> = entries.toList()
    @Synchronized fun clear() = entries.clear()
}
