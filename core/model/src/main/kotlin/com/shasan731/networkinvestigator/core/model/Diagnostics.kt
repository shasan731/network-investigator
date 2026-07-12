package com.shasan731.networkinvestigator.core.model

import kotlinx.serialization.Serializable

@Serializable enum class ResultSource { DIRECT_TEST, LOCAL_CALCULATION, ANDROID_SYSTEM, CACHE, THIRD_PARTY_PROVIDER, USER_INPUT }
@Serializable enum class DiagnosticStatus { SUCCESS, DEGRADED, FAILED, PARTIAL, UNSUPPORTED, CANCELLED, PERMISSION_REQUIRED }
@Serializable enum class DiagnosticTaskType { NETWORK_INFO, DNS, TCP, HTTP, TLS, ROUTE, PORTS, WIFI, LAN, PUBLIC_IP, SUBNET, EXPORT }

@Serializable
data class DiagnosticWarning(val code: String, val message: String)

@Serializable
sealed interface DiagnosticResult<out T> {
    val source: ResultSource
    @Serializable data class Success<T>(val data: T, val startedAtEpochMs: Long, val completedAtEpochMs: Long, override val source: ResultSource) : DiagnosticResult<T>
    @Serializable data class Partial<T>(val data: T?, val warnings: List<DiagnosticWarning>, val startedAtEpochMs: Long, val completedAtEpochMs: Long, override val source: ResultSource) : DiagnosticResult<T>
    @Serializable data class Failure(val code: DiagnosticErrorCode, val message: String, val technicalDetails: String? = null, val recoverable: Boolean, override val source: ResultSource = ResultSource.DIRECT_TEST) : DiagnosticResult<Nothing>
    @Serializable data class Unsupported(val reason: String, override val source: ResultSource = ResultSource.ANDROID_SYSTEM) : DiagnosticResult<Nothing>
    @Serializable data class Cancelled(val reason: String = "Cancelled by user", override val source: ResultSource = ResultSource.USER_INPUT) : DiagnosticResult<Nothing>
}

interface DiagnosticTask<I, O> {
    val taskType: DiagnosticTaskType
    suspend fun execute(input: I): DiagnosticResult<O>
}

@Serializable data class DnsObservation(val hostname: String, val addresses: List<String>, val durationMs: Long, val resolver: String)
@Serializable data class TcpObservation(val host: String, val port: Int, val reachable: Boolean, val durationMs: Long, val detail: String)
@Serializable data class HttpObservation(val url: String, val statusCode: Int, val protocol: String, val durationMs: Long, val responseBytes: Long, val headers: Map<String, String>, val redirectChain: List<String>)
@Serializable data class TlsObservation(val host: String, val protocol: String, val cipherSuite: String, val subject: String, val issuer: String, val notBeforeEpochMs: Long, val notAfterEpochMs: Long, val sans: List<String>, val fingerprintSha256: String)
@Serializable data class PortObservation(val port: Int, val state: PortState, val latencyMs: Long?, val serviceHint: String?)
@Serializable enum class PortState { OPEN, REFUSED, TIMEOUT, UNREACHABLE, ERROR }

@Serializable
data class DiagnosticCard(
    val taskType: DiagnosticTaskType,
    val status: DiagnosticStatus,
    val title: String,
    val primaryResult: String,
    val technicalDetails: String,
    val limitation: String? = null,
    val source: ResultSource,
    val startedAtEpochMs: Long,
    val durationMs: Long
)

@Serializable enum class NetworkLayer { LOCAL_LINK, IP, DNS, TRANSPORT, TLS, HTTP, APPLICATION }
@Serializable enum class ConfidenceLevel { LOW, MEDIUM, HIGH }
@Serializable data class ObservedFact(val label: String, val value: String, val source: ResultSource)
@Serializable data class ProbableCause(val description: String, val layer: NetworkLayer?)
@Serializable data class Diagnosis(val title: String, val probableLayer: NetworkLayer?, val confidence: ConfidenceLevel, val observedFacts: List<ObservedFact>, val probableCauses: List<ProbableCause>, val alternativeExplanations: List<String>, val recommendedChecks: List<String>)

@Serializable
data class InvestigationSnapshot(
    val id: String,
    val target: String,
    val profile: InvestigationProfile,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val cards: List<DiagnosticCard>,
    val diagnosis: Diagnosis?
)

@Serializable enum class InvestigationProfile { QUICK_CHECK, WEBSITE_DOWN, DNS_PROBLEM, INTERNET_PROBLEM, LOCAL_DEVICE, TLS_PROBLEM }

@Serializable
data class FeatureSpec(val route: String, val title: String, val category: String, val description: String, val tasks: List<DiagnosticTaskType>, val limitation: String? = null)

