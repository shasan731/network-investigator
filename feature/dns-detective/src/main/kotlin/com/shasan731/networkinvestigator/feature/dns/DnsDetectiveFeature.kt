package com.shasan731.networkinvestigator.feature.dns
import com.shasan731.networkinvestigator.core.model.*
object DnsDetectiveFeature { val spec = FeatureSpec("dns-detective", "DNS Detective", "DNS", "Resolve through Android, parse DNS wire responses, and compare record sets and TTLs across pluggable resolvers.", listOf(DiagnosticTaskType.DNS), "DNSSEC status depends on resolver/provider support and is never inferred from an ordinary lookup.") }
