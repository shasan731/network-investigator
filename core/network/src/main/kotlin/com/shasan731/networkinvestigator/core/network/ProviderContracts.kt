package com.shasan731.networkinvestigator.core.network

import com.shasan731.networkinvestigator.core.model.DiagnosticResult
import com.shasan731.networkinvestigator.core.model.ResultSource

enum class DnsRecordType { A, AAAA, CNAME, MX, NS, TXT, SOA, SRV, CAA, PTR }
data class DnsQuery(val name: String, val type: DnsRecordType)
data class ResolverAnswer(val resolverName: String, val records: List<DnsRecord>, val durationMs: Long, val dnssecAuthenticated: Boolean?)

interface DnsResolver {
    val name: String
    suspend fun query(query: DnsQuery): DiagnosticResult<ResolverAnswer>
}

data class RdapResult(val provider: String, val handle: String?, val networkName: String?, val cidrs: List<String>, val fetchedAtEpochMs: Long, val expiresAtEpochMs: Long)
interface RdapProvider {
    val name: String
    suspend fun lookup(address: String): DiagnosticResult<RdapResult>
}

data class RouteHop(val number: Int, val address: String?, val latencyMs: Long?, val timedOut: Boolean)
data class RouteObservation(val implementation: String, val hops: List<RouteHop>, val reachedTarget: Boolean, val limitation: String?)
interface RouteProbe {
    val name: String
    suspend fun trace(host: String): DiagnosticResult<RouteObservation>
}

class UnsupportedRouteProbe : RouteProbe {
    override val name = "Unprivileged Android route probe"
    override suspend fun trace(host: String): DiagnosticResult<RouteObservation> = DiagnosticResult.Unsupported(
        "No safe traceroute implementation is available on this device. A silent hop is not treated as failure.",
        ResultSource.ANDROID_SYSTEM
    )
}
