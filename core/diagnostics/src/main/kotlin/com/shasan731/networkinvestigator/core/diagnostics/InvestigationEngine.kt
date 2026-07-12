package com.shasan731.networkinvestigator.core.diagnostics

import com.shasan731.networkinvestigator.core.model.*
import com.shasan731.networkinvestigator.core.network.HttpInspector
import com.shasan731.networkinvestigator.core.network.SystemDnsTask
import com.shasan731.networkinvestigator.core.network.TcpTask
import com.shasan731.networkinvestigator.core.network.TlsInspector
import com.shasan731.networkinvestigator.core.network.ProcessRouteProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.URI
import java.util.UUID

class InvestigationEngine(private val concurrency: Int = 4) {
    init { require(concurrency in 1..16) }

    suspend fun investigate(parsed: ParsedTarget, profile: InvestigationProfile): InvestigationSnapshot = coroutineScope {
        val started = System.currentTimeMillis(); val host = hostOf(parsed.target)
        val scheme = (parsed.target as? InvestigationTarget.Url)?.value?.let { URI(it).scheme }
        val explicitPort = when (val target = parsed.target) {
            is InvestigationTarget.HostPort -> target.port
            is InvestigationTarget.Url -> URI(target.value).port.takeIf { it > 0 }
            else -> null
        }
        val port = explicitPort ?: if (scheme == "http") 80 else 443
        val url = when (val target = parsed.target) {
            is InvestigationTarget.Url -> target.value
            is InvestigationTarget.Cidr -> null
            else -> (if (port == 80) "http" else "https") + "://" + if (host.contains(':')) "[$host]:$port/" else "$host:$port/"
        }
        val tasks = buildList<suspend () -> DiagnosticCard> {
            if (parsed.target !is InvestigationTarget.Cidr) {
                add { card(DiagnosticTaskType.DNS, "DNS lookup", SystemDnsTask().execute(host)) }
                add { card(DiagnosticTaskType.TCP, "TCP reachability", TcpTask().execute(host to port)) }
                if (profile == InvestigationProfile.WEBSITE_DOWN && explicitPort == null) add { card(DiagnosticTaskType.TCP, "TCP port 80", TcpTask().execute(host to 80)) }
                if (url != null && profile != InvestigationProfile.DNS_PROBLEM && profile != InvestigationProfile.LOCAL_DEVICE) add { card(DiagnosticTaskType.HTTP, "HTTP inspection", HttpInspector().execute(url)) }
                if (port == 443 || scheme == "https" || profile == InvestigationProfile.TLS_PROBLEM) add { card(DiagnosticTaskType.TLS, "TLS inspection", TlsInspector(port).execute(host)) }
                if (profile == InvestigationProfile.WEBSITE_DOWN || profile == InvestigationProfile.INTERNET_PROBLEM) add { card(DiagnosticTaskType.ROUTE, "Route investigation", ProcessRouteProbe().trace(host)) }
                if (profile == InvestigationProfile.LOCAL_DEVICE) add {
                    val began = System.currentTimeMillis(); val results = PortScanner().scan(host, listOf(22, 53, 80, 443, 445, 554, 631, 8291)); val open = results.filter { it.state == PortState.OPEN }
                    DiagnosticCard(DiagnosticTaskType.PORTS, if (open.isEmpty()) DiagnosticStatus.PARTIAL else DiagnosticStatus.SUCCESS, "Selected local service ports", if (open.isEmpty()) "No selected ports accepted a connection" else "Open: ${open.joinToString { it.port.toString() }}", results.joinToString("\n") { "${it.port}: ${it.state}" }, "A closed selected set does not prove the device is offline.", ResultSource.DIRECT_TEST, began, System.currentTimeMillis() - began)
                }
            } else {
                add {
                    val target = parsed.target as InvestigationTarget.Cidr
                    val startedAt = System.currentTimeMillis()
                    val subnet = SubnetCalculator.calculate(target.address, target.prefixLength)
                    DiagnosticCard(DiagnosticTaskType.SUBNET, DiagnosticStatus.SUCCESS, "Subnet calculation", "${subnet.networkAddress}/${target.prefixLength}", "${subnet.totalAddresses} addresses; usable ${subnet.firstUsable} – ${subnet.lastUsable}", null, ResultSource.LOCAL_CALCULATION, startedAt, System.currentTimeMillis() - startedAt)
                }
            }
        }
        val semaphore = Semaphore(concurrency)
        val cards = try { tasks.map { task -> async { semaphore.withPermit { task() } } }.awaitAll() }
        catch (cancelled: CancellationException) { throw cancelled }
        val evidence = evidenceFrom(cards)
        InvestigationSnapshot(UUID.randomUUID().toString(), parsed.target.displayValue, profile, started, System.currentTimeMillis(), cards, DiagnosisEngine.diagnose(evidence))
    }

    private fun hostOf(target: InvestigationTarget): String = when (target) {
        is InvestigationTarget.Domain -> target.value
        is InvestigationTarget.Hostname -> target.value
        is InvestigationTarget.Ipv4 -> target.value
        is InvestigationTarget.Ipv6 -> target.value
        is InvestigationTarget.HostPort -> target.host
        is InvestigationTarget.Url -> URI(target.value).host
        is InvestigationTarget.Cidr -> target.address
    }

    private fun evidenceFrom(cards: List<DiagnosticCard>): DiagnosisEvidence {
        fun success(type: DiagnosticTaskType) = cards.firstOrNull { it.taskType == type }?.status?.let { it == DiagnosticStatus.SUCCESS }
        val http = cards.firstOrNull { it.taskType == DiagnosticTaskType.HTTP }
        return DiagnosisEvidence(dnsSucceeded = success(DiagnosticTaskType.DNS), tcpSucceeded = success(DiagnosticTaskType.TCP), tlsSucceeded = success(DiagnosticTaskType.TLS), httpStatus = Regex("HTTP (\\d{3})").find(http?.primaryResult.orEmpty())?.groupValues?.get(1)?.toIntOrNull(), httpLatencyMs = http?.durationMs)
    }

    private fun card(type: DiagnosticTaskType, title: String, result: DiagnosticResult<*>): DiagnosticCard = when (result) {
        is DiagnosticResult.Success<*> -> {
            val (primary, details) = summarize(result.data)
            DiagnosticCard(type, DiagnosticStatus.SUCCESS, title, primary, details, null, result.source, result.startedAtEpochMs, result.completedAtEpochMs - result.startedAtEpochMs)
        }
        is DiagnosticResult.Partial<*> -> DiagnosticCard(type, DiagnosticStatus.PARTIAL, title, "Partial result", result.data.toString(), result.warnings.joinToString { it.message }, result.source, result.startedAtEpochMs, result.completedAtEpochMs - result.startedAtEpochMs)
        is DiagnosticResult.Failure -> DiagnosticCard(type, DiagnosticStatus.FAILED, title, result.message, result.technicalDetails.orEmpty(), "Failure does not prove the host is offline; compare other probes.", result.source, System.currentTimeMillis(), 0)
        is DiagnosticResult.Unsupported -> DiagnosticCard(type, DiagnosticStatus.UNSUPPORTED, title, "Unsupported", result.reason, result.reason, result.source, System.currentTimeMillis(), 0)
        is DiagnosticResult.Cancelled -> DiagnosticCard(type, DiagnosticStatus.CANCELLED, title, "Cancelled", result.reason, null, result.source, System.currentTimeMillis(), 0)
    }

    private fun summarize(data: Any?): Pair<String, String> = when (data) {
        is DnsObservation -> "${data.addresses.size} address(es)" to data.addresses.joinToString("\n")
        is TcpObservation -> "Port ${data.port} reachable in ${data.durationMs} ms" to data.detail
        is HttpObservation -> "HTTP ${data.statusCode} in ${data.durationMs} ms" to "${data.protocol}; ${data.responseBytes} bytes\nRedirects: ${data.redirectChain.joinToString(" → ")}"
        is TlsObservation -> "${data.protocol}, expires ${java.time.Instant.ofEpochMilli(data.notAfterEpochMs)}" to "${data.subject}\nIssuer: ${data.issuer}\nCipher: ${data.cipherSuite}\nSHA-256: ${data.fingerprintSha256}"
        else -> data.toString() to ""
    }
}
