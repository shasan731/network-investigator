package com.shasan731.networkinvestigator.core.network

import com.shasan731.networkinvestigator.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.*
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class CertificateDetails(val subject: String, val issuer: String, val serialHex: String, val notBeforeEpochMs: Long, val notAfterEpochMs: Long, val sans: List<String>, val signatureAlgorithm: String, val publicKeyAlgorithm: String, val publicKeyBits: Int?, val sha256: String)
data class TlsEndpointDetails(val address: String, val protocol: String, val cipherSuite: String, val certificates: List<CertificateDetails>, val hostnameVerified: Boolean)

class MultiAddressTlsInspector(private val timeoutMs: Int = 5_000) {
    suspend fun inspect(host: String, port: Int = 443): Map<String, DiagnosticResult<TlsEndpointDetails>> = coroutineScope {
        InetAddress.getAllByName(host).distinctBy { it.hostAddress }.map { address -> async(Dispatchers.IO) { address.hostAddress to inspectOne(host, address, port) } }.awaitAll().toMap()
    }
    private fun inspectOne(host: String, address: InetAddress, port: Int): DiagnosticResult<TlsEndpointDetails> {
        val started = System.currentTimeMillis()
        return try {
            val socket = SSLSocketFactory.getDefault().createSocket() as SSLSocket
            socket.use { ssl ->
                ssl.soTimeout = timeoutMs; ssl.connect(InetSocketAddress(address, port), timeoutMs)
                ssl.sslParameters = ssl.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS"; if (TargetParser.parseIpv4(host) == null && TargetParser.parseIpv6(host) == null) serverNames = listOf(javax.net.ssl.SNIHostName(host)) }
                ssl.startHandshake(); val session = ssl.session
                val chain = session.peerCertificates.map { certificate(it as X509Certificate) }
                DiagnosticResult.Success(TlsEndpointDetails(address.hostAddress, session.protocol, session.cipherSuite, chain, true), started, System.currentTimeMillis(), ResultSource.DIRECT_TEST)
            }
        } catch (error: javax.net.ssl.SSLHandshakeException) { DiagnosticResult.Failure(if (error.message?.contains("hostname", true) == true) DiagnosticErrorCode.TLS_HOSTNAME_MISMATCH else DiagnosticErrorCode.TLS_HANDSHAKE_FAILED, "TLS validation failed for ${address.hostAddress}.", error.message, false) }
        catch (error: Exception) { DiagnosticResult.Failure(DiagnosticErrorCode.TLS_HANDSHAKE_FAILED, "TLS connection failed for ${address.hostAddress}.", error.message, true) }
    }
    private fun certificate(value: X509Certificate): CertificateDetails {
        val sans = value.subjectAlternativeNames.orEmpty().mapNotNull { it.getOrNull(1)?.toString() }
        val encodedKey = value.publicKey.encoded; val bits = when (value.publicKey.algorithm.uppercase()) { "RSA" -> (value.publicKey as? java.security.interfaces.RSAPublicKey)?.modulus?.bitLength(); "EC" -> (value.publicKey as? java.security.interfaces.ECPublicKey)?.params?.curve?.field?.fieldSize; else -> encodedKey.size * 8 }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(value.encoded).joinToString(":") { "%02X".format(it) }
        return CertificateDetails(value.subjectX500Principal.name, value.issuerX500Principal.name, value.serialNumber.toString(16), value.notBefore.time, value.notAfter.time, sans, value.sigAlgName, value.publicKey.algorithm, bits, fingerprint)
    }
}

data class ServiceInspection(val port: Int, val state: PortState, val latencyMs: Long?, val tls: Boolean, val http: Boolean, val serviceHint: String?, val bannerPreview: String?)

class ServiceInspector(private val timeoutMs: Int = 1_500, private val concurrency: Int = 12) {
    suspend fun inspect(host: String, ports: List<Int>): List<ServiceInspection> = coroutineScope {
        require(ports.size <= 256); val semaphore = Semaphore(concurrency.coerceIn(1, 32))
        ports.distinct().map { port -> async(Dispatchers.IO) { semaphore.withPermit { inspectOne(host, port) } } }.awaitAll().sortedBy { it.port }
    }
    private fun inspectOne(host: String, port: Int): ServiceInspection {
        val start = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs); socket.soTimeout = 300
                val latency = (System.nanoTime() - start) / 1_000_000
                val httpCandidate = port in setOf(80, 8080, 8000, 3000); val tlsVerified = detectTls(host, port)
                val banner = if (!tlsVerified) readBanner(socket, host, httpCandidate) else null
                ServiceInspection(port, PortState.OPEN, latency, tlsVerified, banner?.startsWith("HTTP/") == true, service(port), banner)
            }
        } catch (_: ConnectException) { ServiceInspection(port, PortState.REFUSED, null, false, false, service(port), null) }
        catch (_: SocketTimeoutException) { ServiceInspection(port, PortState.TIMEOUT, null, false, false, service(port), null) }
        catch (_: NoRouteToHostException) { ServiceInspection(port, PortState.UNREACHABLE, null, false, false, service(port), null) }
        catch (_: Exception) { ServiceInspection(port, PortState.ERROR, null, false, false, service(port), null) }
    }
    private fun readBanner(socket: Socket, host: String, sendHttp: Boolean): String? {
        if (sendHttp) { socket.getOutputStream().write("HEAD / HTTP/1.0\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray()); socket.getOutputStream().flush() }
        return runCatching { val bytes = ByteArray(512); val count = socket.getInputStream().read(bytes); if (count > 0) bytes.copyOf(count).toString(Charsets.UTF_8).replace(Regex("[\\p{Cntrl}&&[^\\r\\n\\t]]"), "�") else null }.getOrNull()
    }
    private fun detectTls(host: String, port: Int): Boolean = runCatching { val ssl = SSLSocketFactory.getDefault().createSocket() as SSLSocket; ssl.use { it.connect(InetSocketAddress(host, port), timeoutMs); it.soTimeout = timeoutMs; it.sslParameters = it.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS"; if (TargetParser.parseIpv4(host) == null && TargetParser.parseIpv6(host) == null) serverNames = listOf(javax.net.ssl.SNIHostName(host)) }; it.startHandshake() }; true }.getOrDefault(false)
    private fun service(port: Int) = mapOf(22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS", 80 to "HTTP", 110 to "POP3", 143 to "IMAP", 443 to "HTTPS", 445 to "SMB", 465 to "SMTPS", 587 to "SMTP submission", 993 to "IMAPS", 995 to "POP3S", 1433 to "SQL Server", 3306 to "MySQL", 3389 to "RDP", 5432 to "PostgreSQL", 6379 to "Redis", 7547 to "TR-069", 8291 to "Winbox", 8728 to "RouterOS API", 8729 to "RouterOS API TLS", 27017 to "MongoDB")[port]
}
