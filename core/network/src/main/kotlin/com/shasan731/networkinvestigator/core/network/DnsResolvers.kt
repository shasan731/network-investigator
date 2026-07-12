package com.shasan731.networkinvestigator.core.network

import com.shasan731.networkinvestigator.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.IDN
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.SSLSocketFactory

private object DnsWire {
    fun query(name: String, type: DnsRecordType): ByteArray {
        val id = SecureRandom().nextInt(65536); val output = java.io.ByteArrayOutputStream(); val data = DataOutputStream(output)
        data.writeShort(id); data.writeShort(0x0100); data.writeShort(1); data.writeShort(0); data.writeShort(0); data.writeShort(0)
        IDN.toASCII(name.trimEnd('.')).split('.').forEach { label -> val bytes = label.toByteArray(Charsets.US_ASCII); require(bytes.size in 1..63); data.writeByte(bytes.size); data.write(bytes) }
        data.writeByte(0); data.writeShort(type.code); data.writeShort(1); return output.toByteArray()
    }
    val DnsRecordType.code: Int get() = when (this) { DnsRecordType.A -> 1; DnsRecordType.NS -> 2; DnsRecordType.CNAME -> 5; DnsRecordType.SOA -> 6; DnsRecordType.PTR -> 12; DnsRecordType.MX -> 15; DnsRecordType.TXT -> 16; DnsRecordType.AAAA -> 28; DnsRecordType.SRV -> 33; DnsRecordType.CAA -> 257 }
    fun validate(query: ByteArray, response: ByteArray) { require(response.size >= 12 && query[0] == response[0] && query[1] == response[1]) { "DNS transaction ID mismatch" } }
}

class UdpTcpDnsResolver(private val server: String, private val port: Int = 53, private val timeoutMs: Int = 3_000) : DnsResolver {
    override val name = "$server:$port UDP/TCP"
    override suspend fun query(query: DnsQuery): DiagnosticResult<ResolverAnswer> = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis(); val nano = System.nanoTime()
        try {
            val request = DnsWire.query(query.name, query.type)
            val udpResponse = DatagramSocket().use { socket -> socket.soTimeout = timeoutMs; val address = InetSocketAddress(server, port); socket.send(DatagramPacket(request, request.size, address)); val bytes = ByteArray(65_535); val packet = DatagramPacket(bytes, bytes.size); socket.receive(packet); bytes.copyOf(packet.length) }
            DnsWire.validate(request, udpResponse)
            val response = if (DnsResponseParser.isTruncated(udpResponse)) tcp(request) else udpResponse
            answer(query, response, started, nano, name)
        } catch (error: java.net.SocketTimeoutException) { DiagnosticResult.Failure(DiagnosticErrorCode.DNS_TIMEOUT, "DNS resolver timed out.", error.message, true) }
        catch (error: Exception) { DiagnosticResult.Failure(DiagnosticErrorCode.DNS_SERVFAIL, "DNS query failed.", error.message, true) }
    }
    private fun tcp(request: ByteArray): ByteArray = Socket().use { socket -> socket.connect(InetSocketAddress(server, port), timeoutMs); socket.soTimeout = timeoutMs; val output = DataOutputStream(socket.getOutputStream()); output.writeShort(request.size); output.write(request); output.flush(); val input = DataInputStream(socket.getInputStream()); val length = input.readUnsignedShort(); require(length in 12..65_535); ByteArray(length).also(input::readFully) }
}

class DnsOverTlsResolver(private val server: String, private val port: Int = 853, private val timeoutMs: Int = 4_000) : DnsResolver {
    override val name = "$server:$port DNS over TLS"
    override suspend fun query(query: DnsQuery): DiagnosticResult<ResolverAnswer> = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis(); val nano = System.nanoTime()
        try {
            val request = DnsWire.query(query.name, query.type)
            val socket = SSLSocketFactory.getDefault().createSocket() as javax.net.ssl.SSLSocket
            socket.use { it.soTimeout = timeoutMs; it.connect(InetSocketAddress(server, port), timeoutMs); it.sslParameters = it.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS"; if (TargetParser.parseIpv4(server) == null && TargetParser.parseIpv6(server) == null) serverNames = listOf(javax.net.ssl.SNIHostName(server)) }; it.startHandshake(); val output = DataOutputStream(it.outputStream); output.writeShort(request.size); output.write(request); output.flush(); val input = DataInputStream(it.inputStream); val length = input.readUnsignedShort(); require(length in 12..65_535); val response = ByteArray(length).also { bytes -> input.readFully(bytes) }; DnsWire.validate(request, response); answer(query, response, started, nano, name) }
        } catch (error: Exception) { DiagnosticResult.Failure(DiagnosticErrorCode.DNS_SERVFAIL, "DNS-over-TLS query failed.", error.message, true) }
    }
}

class DnsOverHttpsResolver(private val endpoint: String, private val client: OkHttpClient = HttpInspector.defaultClient()) : DnsResolver {
    override val name = "$endpoint DNS over HTTPS"
    override suspend fun query(query: DnsQuery): DiagnosticResult<ResolverAnswer> = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis(); val nano = System.nanoTime()
        try {
            val wire = DnsWire.query(query.name, query.type)
            val request = Request.Builder().url(endpoint).header("Accept", "application/dns-message").post(wire.toRequestBody("application/dns-message".toMediaType())).build()
            client.newCall(request).execute().use { response -> require(response.isSuccessful) { "DoH HTTP ${response.code}" }; val bytes = requireNotNull(response.body).bytes(); DnsWire.validate(wire, bytes); answer(query, bytes, started, nano, name) }
        } catch (error: Exception) { DiagnosticResult.Failure(DiagnosticErrorCode.DNS_SERVFAIL, "DNS-over-HTTPS query failed.", error.message, true) }
    }
}

private fun answer(query: DnsQuery, bytes: ByteArray, started: Long, nano: Long, resolver: String): DiagnosticResult<ResolverAnswer> {
    val code = DnsResponseParser.responseCode(bytes)
    if (code == 3) return DiagnosticResult.Failure(DiagnosticErrorCode.DNS_NXDOMAIN, "The resolver returned NXDOMAIN.", "rcode=3", true)
    if (code != 0) return DiagnosticResult.Failure(DiagnosticErrorCode.DNS_SERVFAIL, "The resolver returned DNS error $code.", "rcode=$code", true)
    return DiagnosticResult.Success(ResolverAnswer(resolver, DnsResponseParser.parse(bytes), (System.nanoTime() - nano) / 1_000_000, null), started, System.currentTimeMillis(), ResultSource.DIRECT_TEST)
}
