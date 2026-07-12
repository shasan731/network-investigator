package com.shasan731.networkinvestigator.feature.targetintelligence
import com.shasan731.networkinvestigator.core.model.*
object TargetIntelligenceFeature { val spec = FeatureSpec("target-intelligence", "Target Intelligence", "IP and Subnet", "Normalize targets, resolve addresses, inspect HTTP/TLS, and attach local history. Online RDAP/ASN enrichment remains opt-in.", listOf(DiagnosticTaskType.DNS, DiagnosticTaskType.HTTP, DiagnosticTaskType.TLS, DiagnosticTaskType.SUBNET), "ASN and RDAP data require an explicitly enabled external provider; geographic data is never presented as exact.") }
