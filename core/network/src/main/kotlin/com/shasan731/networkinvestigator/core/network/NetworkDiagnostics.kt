package com.shasan731.networkinvestigator.core.network

import com.shasan731.networkinvestigator.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.coroutines.coroutineContext

class SystemDnsTask : DiagnosticTask<String, DnsObservation> {
    override val taskType = DiagnosticTaskType.DNS
    override suspend fun execute(input: String): DiagnosticResult<DnsObservation> = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis(); val nano = System.nanoTime()
        try {
            val addresses = InetAddress.getAllByName(input).map { it.hostAddress }.distinct()
            val completed = System.currentTimeMillis()
            DiagnosticResult.Success(DnsObservation(input, addresses, (System.nanoTime() - nano) / 1_000_000, "Android/JVM system resolver"), started, completed, ResultSource.ANDROID_SYSTEM)
        } catch (error: java.net.UnknownHostException) {
            DiagnosticResult.Failure(DiagnosticErrorCode.DNS_NXDOMAIN, "No address records were returned.", error.message, true, ResultSource.ANDROID_SYSTEM)
        } catch (error: Exception) {
            DiagnosticResult.Failure(DiagnosticErrorCode.DNS_TIMEOUT, "The DNS lookup failed.", error.message, true, ResultSource.ANDROID_SYSTEM)
        }
    }
}

class TcpTask(private val timeoutMs: Int = 3_000) : DiagnosticTask<Pair<String, Int>, TcpObservation> {
    override val taskType = DiagnosticTaskType.TCP
    override suspend fun execute(input: Pair<String, Int>): DiagnosticResult<TcpObservation> = withContext(Dispatchers.IO) {
        val (host, port) = input; val started = System.currentTimeMillis(); val nano = System.nanoTime()
        try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            val completed = System.currentTimeMillis()
            DiagnosticResult.Success(TcpObservation(host, port, true, (System.nanoTime() - nano) / 1_000_000, "TCP connection completed"), started, completed, ResultSource.DIRECT_TEST)
        } catch (error: ConnectException) {
            DiagnosticResult.Failure(DiagnosticErrorCode.TCP_REFUSED, "The host refused the TCP connection.", error.message, true)
        } catch (error: SocketTimeoutException) {
            DiagnosticResult.Failure(DiagnosticErrorCode.TCP_TIMEOUT, "The TCP connection timed out.", error.message, true)
        } catch (error: Exception) {
            DiagnosticResult.Failure(DiagnosticErrorCode.HOST_UNREACHABLE, "The TCP connection could not be completed.", error.message, true)
        }
    }
}

class HttpInspector(private val client: OkHttpClient = defaultClient()) : DiagnosticTask<String, HttpObservation> {
    override val taskType = DiagnosticTaskType.HTTP
    override suspend fun execute(input: String): DiagnosticResult<HttpObservation> = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis(); val nano = System.nanoTime()
        try {
            val request = Request.Builder().url(input).header("User-Agent", "Network-Investigator/0.1 diagnostic").get().build()
            client.newCall(request).execute().use { response ->
                coroutineContext.ensureActive()
                val chain = generateSequence(response.priorResponse) { it.priorResponse }.map { it.request.url.toString() }.toList().reversed() + response.request.url.toString()
                val headers = response.headers.names().associateWith { response.header(it).orEmpty() }
                val observation = HttpObservation(input, response.code, response.protocol.toString(), (System.nanoTime() - nano) / 1_000_000, response.body?.contentLength() ?: 0, headers, chain)
                DiagnosticResult.Success(observation, started, System.currentTimeMillis(), ResultSource.DIRECT_TEST)
            }
        } catch (error: SocketTimeoutException) {
            DiagnosticResult.Failure(DiagnosticErrorCode.HTTP_TIMEOUT, "The HTTP request timed out.", error.message, true)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            DiagnosticResult.Failure(DiagnosticErrorCode.NETWORK_UNAVAILABLE, "The HTTP request failed.", error.message, true)
        }
    }

    companion object {
        fun defaultClient() = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).followRedirects(true).followSslRedirects(true).build()
    }
}

class TlsInspector(private val port: Int = 443, private val timeoutMs: Int = 5_000) : DiagnosticTask<String, TlsObservation> {
    override val taskType = DiagnosticTaskType.TLS
    override suspend fun execute(input: String): DiagnosticResult<TlsObservation> = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        try {
            val socket = (SSLSocketFactory.getDefault().createSocket() as SSLSocket).apply {
                sslParameters = sslParameters.apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                    if (TargetParser.parseIpv4(input) == null && TargetParser.parseIpv6(input) == null) serverNames = listOf(javax.net.ssl.SNIHostName(input))
                }
                soTimeout = timeoutMs
                connect(InetSocketAddress(input, port), timeoutMs)
                startHandshake()
            }
            socket.use {
                val session = it.session
                val certificate = session.peerCertificates.first() as X509Certificate
                val sans = certificate.subjectAlternativeNames.orEmpty().mapNotNull { entry -> entry.getOrNull(1)?.toString() }
                val fingerprint = MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { b -> "%02X".format(b) }
                DiagnosticResult.Success(TlsObservation(input, session.protocol, session.cipherSuite, certificate.subjectX500Principal.name, certificate.issuerX500Principal.name, certificate.notBefore.time, certificate.notAfter.time, sans, fingerprint), started, System.currentTimeMillis(), ResultSource.DIRECT_TEST)
            }
        } catch (error: java.security.cert.CertificateExpiredException) {
            DiagnosticResult.Failure(DiagnosticErrorCode.TLS_EXPIRED, "The TLS certificate is expired.", error.message, false)
        } catch (error: javax.net.ssl.SSLHandshakeException) {
            val mismatch = error.message?.contains("hostname", true) == true
            DiagnosticResult.Failure(if (mismatch) DiagnosticErrorCode.TLS_HOSTNAME_MISMATCH else DiagnosticErrorCode.TLS_HANDSHAKE_FAILED, "TLS validation failed.", error.message, false)
        } catch (error: Exception) {
            DiagnosticResult.Failure(DiagnosticErrorCode.TLS_HANDSHAKE_FAILED, "The TLS handshake could not be completed.", error.message, true)
        }
    }
}

object SecurityHeaderAnalyzer {
    private val checks = linkedMapOf(
        "strict-transport-security" to "HSTS",
        "content-security-policy" to "Content Security Policy",
        "x-content-type-options" to "MIME sniffing protection",
        "referrer-policy" to "Referrer Policy",
        "permissions-policy" to "Permissions Policy"
    )
    fun missing(headers: Map<String, String>): List<String> {
        val names = headers.keys.map(String::lowercase).toSet()
        val missing = checks.filterKeys { it !in names }.values.toMutableList()
        if ("x-frame-options" !in names && "content-security-policy" !in names) missing += "Frame protection"
        if ("cache-control" !in names) missing += "Cache Control"
        return missing
    }
}
