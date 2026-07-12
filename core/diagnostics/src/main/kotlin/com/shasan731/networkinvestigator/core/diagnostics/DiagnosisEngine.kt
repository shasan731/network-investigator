package com.shasan731.networkinvestigator.core.diagnostics

import com.shasan731.networkinvestigator.core.model.*

data class DiagnosisEvidence(
    val gatewayReachable: Boolean? = null,
    val dnsSucceeded: Boolean? = null,
    val tcpSucceeded: Boolean? = null,
    val tlsSucceeded: Boolean? = null,
    val httpStatus: Int? = null,
    val httpLatencyMs: Long? = null,
    val ipv4Succeeded: Boolean? = null,
    val ipv6Succeeded: Boolean? = null,
    val resolverMismatch: Boolean = false
)

object DiagnosisEngine {
    fun diagnose(e: DiagnosisEvidence): Diagnosis? {
        val facts = mutableListOf<ObservedFact>()
        fun fact(label: String, value: Any?) { if (value != null) facts += ObservedFact(label, value.toString(), ResultSource.DIRECT_TEST) }
        fact("Gateway reachable", e.gatewayReachable); fact("DNS succeeded", e.dnsSucceeded)
        fact("TCP succeeded", e.tcpSucceeded); fact("TLS succeeded", e.tlsSucceeded)
        fact("HTTP status", e.httpStatus); fact("HTTP latency (ms)", e.httpLatencyMs)
        fact("IPv4 succeeded", e.ipv4Succeeded); fact("IPv6 succeeded", e.ipv6Succeeded)
        if (e.resolverMismatch) facts += ObservedFact("Resolver result sets differ", "true", ResultSource.DIRECT_TEST)

        val finding = when {
            e.gatewayReachable == false -> Finding("Local gateway is unreachable", NetworkLayer.LOCAL_LINK, ConfidenceLevel.HIGH, "Local network or gateway issue", listOf("Confirm Wi-Fi/mobile link and gateway configuration"))
            e.gatewayReachable == true && e.dnsSucceeded == false -> Finding("Name resolution failed", NetworkLayer.DNS, ConfidenceLevel.HIGH, "DNS resolver or DNS path issue", listOf("Compare the system and a user-selected resolver"))
            e.dnsSucceeded == true && e.tcpSucceeded == false -> Finding("Target port is not reachable", NetworkLayer.TRANSPORT, ConfidenceLevel.MEDIUM, "Target service, route, or firewall issue", listOf("Check another expected port and the route"))
            e.tcpSucceeded == true && e.tlsSucceeded == false -> Finding("TLS negotiation failed", NetworkLayer.TLS, ConfidenceLevel.HIGH, "Certificate, hostname, protocol, or TLS configuration issue", listOf("Inspect the certificate chain and SNI hostname"))
            e.tlsSucceeded == true && e.httpStatus != null && e.httpStatus >= 500 -> Finding("Remote service returned a server error", NetworkLayer.APPLICATION, ConfidenceLevel.HIGH, "Remote application or server issue", listOf("Review response headers and retry once"))
            e.httpStatus in 200..499 && (e.httpLatencyMs ?: 0) > 2_000 -> Finding("Service is reachable but degraded", NetworkLayer.HTTP, ConfidenceLevel.MEDIUM, "Slow server, route, or network", listOf("Repeat samples and compare DNS, TCP, TLS, and TTFB timing"))
            e.ipv4Succeeded == true && e.ipv6Succeeded == false -> Finding("IPv6 path differs from IPv4", NetworkLayer.IP, ConfidenceLevel.MEDIUM, "IPv6 path or configuration issue", listOf("Compare resolved AAAA records and IPv6 route availability"))
            e.resolverMismatch -> Finding("DNS resolvers disagree", NetworkLayer.DNS, ConfidenceLevel.MEDIUM, "Split DNS, stale cache, or resolver inconsistency", listOf("Inspect TTLs and authoritative answers"))
            else -> null
        } ?: return null

        return Diagnosis(finding.title, finding.layer, finding.confidence, facts, listOf(ProbableCause(finding.cause, finding.layer)), listOf("Transient loss or policy filtering may produce similar evidence"), finding.checks)
    }

    private data class Finding(val title: String, val layer: NetworkLayer, val confidence: ConfidenceLevel, val cause: String, val checks: List<String>)
}

