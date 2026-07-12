package com.shasan731.networkinvestigator.feature.compare
import com.shasan731.networkinvestigator.core.model.*
object NetworkCompareFeature { val spec = FeatureSpec("network-compare", "Network Compare", "Evidence", "Compare two saved runs as added, removed, changed, improved, degraded, unchanged, or incomparable values using explicit thresholds.", listOf(DiagnosticTaskType.DNS, DiagnosticTaskType.TCP, DiagnosticTaskType.HTTP, DiagnosticTaskType.TLS)) }
