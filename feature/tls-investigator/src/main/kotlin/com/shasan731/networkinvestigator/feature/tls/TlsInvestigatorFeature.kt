package com.shasan731.networkinvestigator.feature.tls
import com.shasan731.networkinvestigator.core.model.*
object TlsInvestigatorFeature { val spec = FeatureSpec("tls-investigator", "SSL/TLS Investigator", "TLS", "Perform an SNI and hostname-verified handshake and show the certificate, SANs, validity, protocol, cipher and fingerprint.", listOf(DiagnosticTaskType.DNS, DiagnosticTaskType.TCP, DiagnosticTaskType.TLS), "Invalid-certificate testing never changes the app-wide trust manager.") }
