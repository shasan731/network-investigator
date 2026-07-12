package com.shasan731.networkinvestigator.core.model

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

object TargetParser {
    private val hostnameLabel = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?", RegexOption.IGNORE_CASE)

    fun parse(raw: String): TargetParseResult {
        val input = raw.trim()
        if (input.isEmpty()) return invalid(DiagnosticErrorCode.INVALID_TARGET, "Enter a URL, host, IP address, host and port, or CIDR range.", raw)
        if (input.any { it.isWhitespace() || it.code < 32 }) return invalid(DiagnosticErrorCode.INVALID_TARGET, "Targets cannot contain whitespace or control characters.", raw)

        if ('/' in input && !input.contains("://")) return parseCidr(input)
        if (input.startsWith("http://", true) || input.startsWith("https://", true)) return parseUrl(input)
        if (input.startsWith('[')) return parseBracketedHostPort(input)
        parseIpv4(input)?.let { return TargetParseResult.Valid(ParsedTarget(InvestigationTarget.Ipv4(it), IpClassifier.classifyIpv4(it))) }
        parseIpv6(input)?.let { return TargetParseResult.Valid(ParsedTarget(InvestigationTarget.Ipv6(it), IpClassifier.classifyIpv6(it))) }

        val colonCount = input.count { it == ':' }
        if (colonCount == 1) {
            val host = input.substringBeforeLast(':')
            val portText = input.substringAfterLast(':')
            if (portText.isNotEmpty() && portText.all(Char::isDigit)) return parseHostPort(host, portText, input)
        }
        return parseHost(input, input)
    }

    private fun parseUrl(input: String): TargetParseResult = try {
        val uri = URI(input)
        if (uri.scheme.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
            invalid(DiagnosticErrorCode.INVALID_TARGET, "Only absolute HTTP(S) URLs without embedded credentials are accepted.", input)
        } else if (uri.port !in -1..65535 || uri.port == 0) {
            invalid(DiagnosticErrorCode.INVALID_PORT, "Port must be between 1 and 65535.", input)
        } else {
            val asciiHost = normalizeHost(uri.host)
            if (!isValidHost(asciiHost) && parseIpv6(asciiHost) == null) invalid(DiagnosticErrorCode.INVALID_TARGET, "The URL host is malformed.", input)
            else {
                val authority = (if (asciiHost.contains(':')) "[$asciiHost]" else asciiHost) + if (uri.port > 0) ":${uri.port}" else ""
                val normalized = buildString {
                    append(uri.scheme.lowercase()).append("://").append(authority)
                    append(uri.rawPath?.ifEmpty { "/" } ?: "/")
                    uri.rawQuery?.let { append('?').append(it) }
                    uri.rawFragment?.let { append('#').append(it) }
                }
                TargetParseResult.Valid(ParsedTarget(InvestigationTarget.Url(normalized)))
            }
        }
    } catch (_: Exception) { invalid(DiagnosticErrorCode.INVALID_TARGET, "The URL is malformed.", input) }

    private fun parseBracketedHostPort(input: String): TargetParseResult {
        val close = input.indexOf(']')
        if (close < 0 || close + 1 >= input.length || input[close + 1] != ':') return invalid(DiagnosticErrorCode.INVALID_TARGET, "Use [IPv6-address]:port.", input)
        val ipv6 = parseIpv6(input.substring(1, close)) ?: return invalid(DiagnosticErrorCode.INVALID_TARGET, "The bracketed IPv6 address is invalid.", input)
        return parsePort(input.substring(close + 2), input)?.let { port -> TargetParseResult.Valid(ParsedTarget(InvestigationTarget.HostPort(ipv6, port), IpClassifier.classifyIpv6(ipv6))) }
            ?: invalid(DiagnosticErrorCode.INVALID_PORT, "Port must be between 1 and 65535.", input)
    }

    private fun parseHostPort(hostInput: String, portText: String, original: String): TargetParseResult {
        val port = parsePort(portText, original) ?: return invalid(DiagnosticErrorCode.INVALID_PORT, "Port must be between 1 and 65535.", original)
        parseIpv4(hostInput)?.let { return TargetParseResult.Valid(ParsedTarget(InvestigationTarget.HostPort(it, port), IpClassifier.classifyIpv4(it))) }
        val host = normalizeHost(hostInput)
        if (!isValidHost(host)) return invalid(DiagnosticErrorCode.INVALID_TARGET, "The host is malformed.", original)
        return TargetParseResult.Valid(ParsedTarget(InvestigationTarget.HostPort(host, port)))
    }

    private fun parseHost(input: String, original: String): TargetParseResult {
        val host = try { normalizeHost(input) } catch (_: Exception) { return invalid(DiagnosticErrorCode.INVALID_TARGET, "The internationalized host name is malformed.", original) }
        if (!isValidHost(host)) return invalid(DiagnosticErrorCode.INVALID_TARGET, "The host name is malformed.", original)
        val target = if (host == "localhost" || host.endsWith(".local") || !host.contains('.')) InvestigationTarget.Hostname(host) else InvestigationTarget.Domain(host)
        return TargetParseResult.Valid(ParsedTarget(target))
    }

    private fun parseCidr(input: String): TargetParseResult {
        if (input.count { it == '/' } != 1) return invalid(DiagnosticErrorCode.INVALID_CIDR, "CIDR must contain one prefix separator.", input)
        val addressInput = input.substringBefore('/')
        val prefix = input.substringAfter('/').toIntOrNull() ?: return invalid(DiagnosticErrorCode.INVALID_CIDR, "CIDR prefix must be numeric.", input)
        parseIpv4(addressInput)?.let { address ->
            if (prefix !in 0..32) return invalid(DiagnosticErrorCode.INVALID_CIDR, "IPv4 prefix must be 0 through 32.", input)
            return TargetParseResult.Valid(ParsedTarget(InvestigationTarget.Cidr(address, prefix), IpClassifier.classifyIpv4(address)))
        }
        parseIpv6(addressInput)?.let { address ->
            if (prefix !in 0..128) return invalid(DiagnosticErrorCode.INVALID_CIDR, "IPv6 prefix must be 0 through 128.", input)
            return TargetParseResult.Valid(ParsedTarget(InvestigationTarget.Cidr(address, prefix), IpClassifier.classifyIpv6(address)))
        }
        return invalid(DiagnosticErrorCode.INVALID_CIDR, "CIDR address is invalid.", input)
    }

    private fun normalizeHost(host: String): String = IDN.toASCII(host.trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase()
    private fun isValidHost(host: String): Boolean = host.length in 1..253 && host.split('.').all { it.length in 1..63 && hostnameLabel.matches(it) }
    private fun parsePort(text: String, original: String): Int? = text.toIntOrNull()?.takeIf { it in 1..65535 }

    fun parseIpv4(value: String): String? {
        val parts = value.split('.')
        if (parts.size != 4 || parts.any { it.isEmpty() || it.length > 3 || !it.all(Char::isDigit) || (it.length > 1 && it.startsWith('0')) }) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        if (nums.any { it !in 0..255 }) return null
        return nums.joinToString(".")
    }

    fun parseIpv6(value: String): String? {
        if (!value.contains(':') || value.contains('%')) return null
        return try {
            val address = InetAddress.getByName(value)
            if (address is Inet6Address) address.hostAddress.substringBefore('%').lowercase() else null
        } catch (_: Exception) { null }
    }

    private fun invalid(code: DiagnosticErrorCode, message: String, input: String) = TargetParseResult.Invalid(ValidationError(code, message, input))
}

object IpClassifier {
    fun classifyIpv4(value: String): AddressScope {
        val b = value.split('.').map(String::toInt)
        return when {
            b[0] == 10 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) -> AddressScope.PRIVATE
            b[0] == 127 -> AddressScope.LOOPBACK
            b[0] == 169 && b[1] == 254 -> AddressScope.LINK_LOCAL
            b[0] in 224..239 -> AddressScope.MULTICAST
            (b[0] == 192 && b[1] == 0 && b[2] == 2) || (b[0] == 198 && b[1] == 51 && b[2] == 100) || (b[0] == 203 && b[1] == 0 && b[2] == 113) -> AddressScope.DOCUMENTATION
            b.all { it == 0 } -> AddressScope.UNSPECIFIED
            else -> AddressScope.PUBLIC
        }
    }

    fun classifyIpv6(value: String): AddressScope {
        val bytes = InetAddress.getByName(value).address
        return when {
            bytes.all { it.toInt() == 0 } -> AddressScope.UNSPECIFIED
            bytes.dropLast(1).all { it.toInt() == 0 } && bytes.last().toInt() == 1 -> AddressScope.LOOPBACK
            bytes[0].toInt() and 0xfe == 0xfc -> AddressScope.PRIVATE
            bytes[0].toInt() and 0xff == 0xfe && bytes[1].toInt() and 0xc0 == 0x80 -> AddressScope.LINK_LOCAL
            bytes[0].toInt() and 0xff == 0xff -> AddressScope.MULTICAST
            bytes[0].toInt() and 0xff == 0x20 && bytes[1].toInt() and 0xff == 0x01 && bytes[2].toInt() == 0x0d && bytes[3].toInt() and 0xb8 == 0xb8 -> AddressScope.DOCUMENTATION
            else -> AddressScope.PUBLIC
        }
    }
}
