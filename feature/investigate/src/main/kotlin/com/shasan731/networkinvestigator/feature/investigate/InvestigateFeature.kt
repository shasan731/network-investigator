package com.shasan731.networkinvestigator.feature.investigate
import com.shasan731.networkinvestigator.core.model.*
object InvestigateFeature { val spec = FeatureSpec("investigate", "Investigate", "Core", "Parse one target, select a profile, and stream concurrent diagnostic results.", listOf(DiagnosticTaskType.DNS, DiagnosticTaskType.TCP, DiagnosticTaskType.HTTP, DiagnosticTaskType.TLS)) }
