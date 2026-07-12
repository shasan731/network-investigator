package com.shasan731.networkinvestigator.core.network

import com.shasan731.networkinvestigator.core.model.DiagnosticErrorCode
import com.shasan731.networkinvestigator.core.model.DiagnosticResult
import com.shasan731.networkinvestigator.core.model.ResultSource
import com.shasan731.networkinvestigator.core.security.SecretRedactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class HttpMethod { GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS }
enum class BodyKind { NONE, JSON, FORM_URL_ENCODED, RAW_TEXT }
enum class UserAgentProfile { MOBILE, DESKTOP }
enum class AddressFamily { SYSTEM, IPV4, IPV6 }
data class HttpRequestSpec(
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val headers: Map<String, String> = emptyMap(),
    val queryParameters: Map<String, String> = emptyMap(),
    val body: String? = null,
    val bodyKind: BodyKind = BodyKind.NONE,
    val basicCredentials: Pair<String, String>? = null,
    val bearerToken: String? = null,
    val timeoutMs: Long = 15_000,
    val followRedirects: Boolean = true,
    val userAgent: UserAgentProfile = UserAgentProfile.MOBILE,
    val addressFamily: AddressFamily = AddressFamily.SYSTEM
)
data class HttpTiming(val dnsMs: Long?, val connectMs: Long?, val tlsMs: Long?, val requestMs: Long?, val timeToFirstByteMs: Long?, val totalMs: Long)
data class DetailedHttpObservation(
    val requestUrl: String, val finalUrl: String, val statusCode: Int, val protocol: String,
    val redirectChain: List<String>, val responseHeaders: Map<String, String>, val responseSize: Long,
    val contentType: String?, val compression: String?, val timing: HttpTiming, val bodyPreview: String,
    val bodyTruncated: Boolean, val bodySha256: String, val missingSecurityHeaders: List<String>, val pageMetadata: HtmlMetadata?
)
data class HtmlMetadata(val title: String?, val description: String?, val openGraphTitle: String?, val openGraphDescription: String?)
object HtmlMetadataParser {
    fun parse(html: String): HtmlMetadata { fun content(name: String, attribute: String = "name"): String? { val tag = Regex("<meta\\s+[^>]*$attribute\\s*=\\s*['\"]${Regex.escape(name)}['\"][^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.value ?: return null; return Regex("content\\s*=\\s*['\"](.*?)['\"]", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(tag)?.groupValues?.get(1)?.trim() }; return HtmlMetadata(Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1)?.replace(Regex("\\s+"), " ")?.trim(), content("description"), content("og:title", "property"), content("og:description", "property")) }
}

class AdvancedHttpInspector {
    suspend fun execute(spec: HttpRequestSpec): DiagnosticResult<DetailedHttpObservation> = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis(); val listener = TimingListener()
        try {
            require(spec.timeoutMs in 100..120_000)
            val base = spec.url.toHttpUrl(); val url = base.newBuilder().apply { spec.queryParameters.forEach { (name, value) -> addQueryParameter(name, value) } }.build()
            if (!url.isHttps) return@withContext ExplicitCleartextHttpInspector.execute(spec.copy(url = url.toString()), started)
            val request = Request.Builder().url(url).apply {
                header("User-Agent", if (spec.userAgent == UserAgentProfile.MOBILE) "Network-Investigator/0.1 Android" else "Mozilla/5.0 Network-Investigator/0.1 Desktop")
                spec.headers.forEach { (name, value) -> header(name, value) }
                spec.basicCredentials?.let { header("Authorization", Credentials.basic(it.first, it.second)) }
                spec.bearerToken?.let { header("Authorization", "Bearer $it") }
                method(spec.method.name, body(spec))
            }.build()
            val client = HttpInspector.defaultClient().newBuilder().followRedirects(spec.followRedirects).followSslRedirects(spec.followRedirects).callTimeout(spec.timeoutMs, TimeUnit.MILLISECONDS).eventListener(listener).apply { if (spec.addressFamily != AddressFamily.SYSTEM) dns { hostname -> java.net.InetAddress.getAllByName(hostname).filter { spec.addressFamily == AddressFamily.IPV4 && it is java.net.Inet4Address || spec.addressFamily == AddressFamily.IPV6 && it is java.net.Inet6Address }.ifEmpty { throw java.net.UnknownHostException("No ${spec.addressFamily} address for $hostname") } } }.build()
            client.newCall(request).execute().use { response ->
                val bytes = readBounded(response.body, 5L * 1024 * 1024)
                val previewLength = minOf(bytes.size, 64 * 1024); val preview = bytes.copyOf(previewLength).toString(Charsets.UTF_8)
                val rawHeaders = response.headers.names().associateWith { response.header(it).orEmpty() }
                val chain = generateSequence(response.priorResponse) { it.priorResponse }.map { it.request.url.toString() }.toList().reversed() + response.request.url.toString()
                val total = (System.nanoTime() - listener.callStart) / 1_000_000
                val timing = HttpTiming(listener.duration(listener.dnsStart, listener.dnsEnd), listener.duration(listener.connectStart, listener.connectEnd), listener.duration(listener.tlsStart, listener.tlsEnd), listener.duration(listener.requestStart, listener.requestEnd), listener.duration(listener.callStart, listener.responseHeadersStart), total)
                val safePreview = SecretRedactor.redact(preview); val observation = DetailedHttpObservation(spec.url, response.request.url.toString(), response.code, response.protocol.toString(), chain, SecretRedactor.redactHeaders(rawHeaders), bytes.size.toLong(), response.body?.contentType()?.toString(), response.header("Content-Encoding"), timing, safePreview, bytes.size > previewLength, sha256(bytes), SecurityHeaderAnalyzer.missing(rawHeaders), if (response.body?.contentType()?.subtype == "html") HtmlMetadataParser.parse(safePreview) else null)
                DiagnosticResult.Success(observation, started, System.currentTimeMillis(), ResultSource.DIRECT_TEST)
            }
        } catch (tooLarge: BodyTooLargeException) { DiagnosticResult.Failure(DiagnosticErrorCode.HTTP_BODY_TOO_LARGE, "Response body exceeded the 5 MiB diagnostic limit.", null, false) }
        catch (invalid: IllegalArgumentException) { DiagnosticResult.Failure(DiagnosticErrorCode.INVALID_TARGET, "HTTP request configuration is invalid.", invalid.message, false) }
        catch (timeout: java.net.SocketTimeoutException) { DiagnosticResult.Failure(DiagnosticErrorCode.HTTP_TIMEOUT, "HTTP request timed out.", timeout.message, true) }
        catch (error: Exception) { val redirectLoop = error.message?.contains("follow-up", true) == true || error.message?.contains("redirect", true) == true; DiagnosticResult.Failure(if (redirectLoop) DiagnosticErrorCode.HTTP_REDIRECT_LOOP else DiagnosticErrorCode.NETWORK_UNAVAILABLE, if (redirectLoop) "HTTP redirect loop or limit detected." else "HTTP request failed.", error.message, true) }
    }

    private fun body(spec: HttpRequestSpec): RequestBody? {
        if (spec.method in setOf(HttpMethod.GET, HttpMethod.HEAD)) return null
        val value = spec.body.orEmpty()
        val media = when (spec.bodyKind) { BodyKind.JSON -> "application/json; charset=utf-8"; BodyKind.FORM_URL_ENCODED -> "application/x-www-form-urlencoded; charset=utf-8"; BodyKind.RAW_TEXT -> "text/plain; charset=utf-8"; BodyKind.NONE -> null }
        return value.toRequestBody(media?.toMediaTypeOrNull())
    }
    private fun readBounded(body: ResponseBody?, max: Long): ByteArray {
        if (body == null) return byteArrayOf(); if (body.contentLength() > max) throw BodyTooLargeException()
        val input = body.byteStream(); val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0L
        while (true) { val read = input.read(buffer); if (read == -1) break; total += read; if (total > max) throw BodyTooLargeException(); output.write(buffer, 0, read) }
        return output.toByteArray()
    }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private class BodyTooLargeException : Exception()

    private class TimingListener : EventListener() {
        var callStart = 0L; var dnsStart = 0L; var dnsEnd = 0L; var connectStart = 0L; var connectEnd = 0L
        var tlsStart = 0L; var tlsEnd = 0L; var requestStart = 0L; var requestEnd = 0L; var responseHeadersStart = 0L
        override fun callStart(call: Call) { callStart = System.nanoTime() }
        override fun dnsStart(call: Call, domainName: String) { dnsStart = System.nanoTime() }
        override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<java.net.InetAddress>) { dnsEnd = System.nanoTime() }
        override fun connectStart(call: Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy) { connectStart = System.nanoTime() }
        override fun connectEnd(call: Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy, protocol: Protocol?) { connectEnd = System.nanoTime() }
        override fun secureConnectStart(call: Call) { tlsStart = System.nanoTime() }
        override fun secureConnectEnd(call: Call, handshake: Handshake?) { tlsEnd = System.nanoTime() }
        override fun requestHeadersStart(call: Call) { requestStart = System.nanoTime() }
        override fun requestBodyEnd(call: Call, byteCount: Long) { requestEnd = System.nanoTime() }
        override fun requestHeadersEnd(call: Call, request: Request) { if (requestEnd == 0L) requestEnd = System.nanoTime() }
        override fun responseHeadersStart(call: Call) { responseHeadersStart = System.nanoTime() }
        fun duration(start: Long, end: Long): Long? = if (start > 0 && end >= start) (end - start) / 1_000_000 else null
    }
}

private object ExplicitCleartextHttpInspector {
    fun execute(spec: HttpRequestSpec, started: Long): DiagnosticResult<DetailedHttpObservation> = try {
        var current = java.net.URI(spec.url); val redirects = mutableListOf<String>(); var response: RawResponse? = null; val totalStart = System.nanoTime(); var hops = 0
        while (hops < 10) {
            redirects += current.toString(); val next = request(current, spec); response = next
            val location = next.headers.entries.firstOrNull { it.key.equals("location", true) }?.value
            if (!spec.followRedirects || next.code !in 300..399 || location == null) break
            current = current.resolve(location); hops++
        }
        val value = requireNotNull(response); if (value.code in 300..399 && redirects.size >= 10) return DiagnosticResult.Failure(DiagnosticErrorCode.HTTP_REDIRECT_LOOP, "Cleartext redirect limit exceeded.", null, false)
        val total = (System.nanoTime() - totalStart) / 1_000_000; val previewBytes = value.body.copyOf(minOf(value.body.size, 64 * 1024)); val redactedHeaders = SecretRedactor.redactHeaders(value.headers)
        val preview = SecretRedactor.redact(previewBytes.toString(Charsets.UTF_8)); val contentType = value.headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value
        DiagnosticResult.Success(DetailedHttpObservation(spec.url, current.toString(), value.code, "HTTP/${value.version}", redirects, redactedHeaders, value.body.size.toLong(), contentType, value.headers.entries.firstOrNull { it.key.equals("content-encoding", true) }?.value, HttpTiming(value.dnsMs, value.connectMs, null, value.requestMs, value.ttfbMs, total), preview, value.body.size > previewBytes.size, MessageDigest.getInstance("SHA-256").digest(value.body).joinToString("") { "%02x".format(it) }, SecurityHeaderAnalyzer.missing(value.headers), if (contentType?.contains("html", true) == true) HtmlMetadataParser.parse(preview) else null), started, System.currentTimeMillis(), ResultSource.DIRECT_TEST)
    } catch (_: BodyTooLargeRaw) { DiagnosticResult.Failure(DiagnosticErrorCode.HTTP_BODY_TOO_LARGE, "Response body exceeded the 5 MiB diagnostic limit.", null, false) }
    catch (error: java.net.SocketTimeoutException) { DiagnosticResult.Failure(DiagnosticErrorCode.HTTP_TIMEOUT, "Cleartext HTTP request timed out.", error.message, true) }
    catch (error: Exception) { DiagnosticResult.Failure(DiagnosticErrorCode.NETWORK_UNAVAILABLE, "Explicit cleartext HTTP diagnostic failed.", error.message, true) }

    private fun request(uri: java.net.URI, spec: HttpRequestSpec): RawResponse {
        require(uri.scheme == "http" && uri.host != null); val port = if (uri.port > 0) uri.port else 80; val dnsStart = System.nanoTime(); val addresses = java.net.InetAddress.getAllByName(uri.host).filter { spec.addressFamily == AddressFamily.SYSTEM || spec.addressFamily == AddressFamily.IPV4 && it is java.net.Inet4Address || spec.addressFamily == AddressFamily.IPV6 && it is java.net.Inet6Address }; val address = addresses.firstOrNull() ?: throw java.net.UnknownHostException("No ${spec.addressFamily} address"); val dnsMs = (System.nanoTime() - dnsStart) / 1_000_000
        val socket = java.net.Socket(); val connectStart = System.nanoTime(); socket.connect(java.net.InetSocketAddress(address, port), spec.timeoutMs.toInt()); val connectMs = (System.nanoTime() - connectStart) / 1_000_000; socket.soTimeout = spec.timeoutMs.toInt()
        socket.use {
            val body = spec.body?.toByteArray().orEmpty(); val path = (uri.rawPath?.ifEmpty { "/" } ?: "/") + uri.rawQuery?.let { "?$it" }.orEmpty()
            val headers = linkedMapOf("Host" to uri.host + if (uri.port > 0) ":$port" else "", "User-Agent" to "Network-Investigator/0.1 explicit-cleartext", "Connection" to "close", "Accept-Encoding" to "identity")
            headers.putAll(spec.headers); spec.basicCredentials?.let { headers["Authorization"] = Credentials.basic(it.first, it.second) }; spec.bearerToken?.let { headers["Authorization"] = "Bearer $it" }; if (body.isNotEmpty()) { headers["Content-Length"] = body.size.toString(); headers["Content-Type"] = when (spec.bodyKind) { BodyKind.JSON -> "application/json"; BodyKind.FORM_URL_ENCODED -> "application/x-www-form-urlencoded"; else -> "text/plain" } }
            val requestStart = System.nanoTime(); val output = it.getOutputStream(); output.write("${spec.method.name} $path HTTP/1.1\r\n".toByteArray()); headers.forEach { (name, value) -> output.write("$name: $value\r\n".toByteArray()) }; output.write("\r\n".toByteArray()); output.write(body); output.flush(); val requestMs = (System.nanoTime() - requestStart) / 1_000_000
            val input = it.getInputStream().buffered(); val firstStart = System.nanoTime(); val status = readLine(input); val ttfb = (System.nanoTime() - firstStart) / 1_000_000; val match = Regex("HTTP/(\\S+)\\s+(\\d{3})").find(status) ?: error("Malformed HTTP status line")
            val responseHeaders = linkedMapOf<String, String>(); while (true) { val line = readLine(input); if (line.isEmpty()) break; val colon = line.indexOf(':'); if (colon > 0) responseHeaders[line.substring(0, colon).trim()] = line.substring(colon + 1).trim() }
            val bytes = readBody(input, responseHeaders, 5 * 1024 * 1024)
            return RawResponse(match.groupValues[1], match.groupValues[2].toInt(), responseHeaders, bytes, dnsMs, connectMs, requestMs, ttfb)
        }
    }
    private fun readBody(input: java.io.BufferedInputStream, headers: Map<String, String>, max: Int): ByteArray {
        val chunked = headers.entries.any { it.key.equals("transfer-encoding", true) && it.value.contains("chunked", true) }; val expected = headers.entries.firstOrNull { it.key.equals("content-length", true) }?.value?.toIntOrNull(); val sink = java.io.ByteArrayOutputStream()
        if (chunked) { while (true) { val size = readLine(input).substringBefore(';').trim().toInt(16); if (size == 0) { while (readLine(input).isNotEmpty()) Unit; break }; if (sink.size() + size > max) throw BodyTooLargeRaw(); val bytes = ByteArray(size); var offset = 0; while (offset < size) { val count = input.read(bytes, offset, size - offset); if (count == -1) error("Truncated chunked body"); offset += count }; sink.write(bytes); readLine(input) } }
        else { val buffer = ByteArray(8192); var remaining = expected ?: Int.MAX_VALUE; while (remaining > 0) { val count = input.read(buffer, 0, minOf(buffer.size, remaining)); if (count == -1) break; sink.write(buffer, 0, count); remaining -= count; if (sink.size() > max) throw BodyTooLargeRaw() } }
        return sink.toByteArray()
    }
    private fun readLine(input: java.io.BufferedInputStream): String { val bytes = java.io.ByteArrayOutputStream(); while (true) { val value = input.read(); if (value == -1 || value == '\n'.code) break; if (value != '\r'.code) bytes.write(value); if (bytes.size() > 8192) error("HTTP header line too large") }; return bytes.toString("ISO-8859-1") }
    private data class RawResponse(val version: String, val code: Int, val headers: Map<String, String>, val body: ByteArray, val dnsMs: Long, val connectMs: Long, val requestMs: Long, val ttfbMs: Long)
    private class BodyTooLargeRaw : Exception()
}
