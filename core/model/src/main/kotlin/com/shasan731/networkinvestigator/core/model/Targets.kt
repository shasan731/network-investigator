package com.shasan731.networkinvestigator.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface InvestigationTarget {
    val displayValue: String

    @Serializable @SerialName("domain")
    data class Domain(val value: String) : InvestigationTarget { override val displayValue = value }
    @Serializable @SerialName("url")
    data class Url(val value: String) : InvestigationTarget { override val displayValue = value }
    @Serializable @SerialName("ipv4")
    data class Ipv4(val value: String) : InvestigationTarget { override val displayValue = value }
    @Serializable @SerialName("ipv6")
    data class Ipv6(val value: String) : InvestigationTarget { override val displayValue = value }
    @Serializable @SerialName("hostname")
    data class Hostname(val value: String) : InvestigationTarget { override val displayValue = value }
    @Serializable @SerialName("host_port")
    data class HostPort(val host: String, val port: Int) : InvestigationTarget {
        override val displayValue = if (host.contains(':')) "[$host]:$port" else "$host:$port"
    }
    @Serializable @SerialName("cidr")
    data class Cidr(val address: String, val prefixLength: Int) : InvestigationTarget {
        override val displayValue = "$address/$prefixLength"
    }
}

@Serializable
enum class AddressScope { PRIVATE, LOOPBACK, LINK_LOCAL, MULTICAST, DOCUMENTATION, UNSPECIFIED, PUBLIC }

@Serializable
data class ParsedTarget(val target: InvestigationTarget, val scope: AddressScope? = null)

@Serializable
sealed interface TargetParseResult {
    @Serializable data class Valid(val parsed: ParsedTarget) : TargetParseResult
    @Serializable data class Invalid(val error: ValidationError) : TargetParseResult
}

@Serializable
data class ValidationError(val code: DiagnosticErrorCode, val message: String, val input: String)

@Serializable
enum class DiagnosticErrorCode {
    INVALID_TARGET, INVALID_PORT, INVALID_CIDR, PERMISSION_DENIED, NETWORK_UNAVAILABLE,
    DNS_TIMEOUT, DNS_NXDOMAIN, DNS_SERVFAIL, TCP_REFUSED, TCP_TIMEOUT, HOST_UNREACHABLE,
    TLS_HOSTNAME_MISMATCH, TLS_EXPIRED, TLS_HANDSHAKE_FAILED, HTTP_TIMEOUT,
    HTTP_REDIRECT_LOOP, HTTP_BODY_TOO_LARGE, SCAN_CANCELLED, FEATURE_UNSUPPORTED,
    RATE_LIMITED, STORAGE_ERROR, EXPORT_ERROR, CANCELLED, UNKNOWN
}

