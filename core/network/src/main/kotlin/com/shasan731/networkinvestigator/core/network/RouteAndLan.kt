package com.shasan731.networkinvestigator.core.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.shasan731.networkinvestigator.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.withPermit
import java.net.*
import java.util.concurrent.ConcurrentHashMap

class ProcessRouteProbe(private val maxHops: Int = 20, private val timeoutSeconds: Int = 2) : RouteProbe {
    override val name = "System traceroute/ping TTL"
    override suspend fun trace(host: String): DiagnosticResult<RouteObservation> = withContext(Dispatchers.IO) {
        val parsed = TargetParser.parse(host)
        if (parsed !is TargetParseResult.Valid || parsed.parsed.target is InvestigationTarget.Cidr || parsed.parsed.target is InvestigationTarget.Url || parsed.parsed.target is InvestigationTarget.HostPort) return@withContext DiagnosticResult.Failure(DiagnosticErrorCode.INVALID_TARGET, "Route target must be a host or IP address.", null, false)
        val started = System.currentTimeMillis()
        var output = runCommand(listOf("traceroute", "-n", "-m", maxHops.toString(), "-w", timeoutSeconds.toString(), host))
        if (output == null) { val builder = StringBuilder()
            for (ttl in 1..maxHops) {
                currentCoroutineContext().ensureActive(); val line = runCommand(listOf("ping", "-c", "1", "-W", timeoutSeconds.toString(), "-t", ttl.toString(), host)) ?: break
                builder.appendLine(line.lineSequence().firstOrNull { "From " in it || "bytes from" in it }.orEmpty())
                if ("bytes from" in line) break
            }
            output = builder.toString().takeIf { it.isNotBlank() }
        }
        if (output == null) DiagnosticResult.Unsupported("Neither traceroute nor a TTL-capable ping command is available on this Android device.")
        else {
            val hops = parseHops(output); val reached = hops.lastOrNull()?.address?.let { runCatching { InetAddress.getByName(it) == InetAddress.getByName(host) }.getOrDefault(false) } ?: false
            DiagnosticResult.Success(RouteObservation(name, hops, reached, "Intermediate silence can be ICMP filtering and is not diagnosed as route failure."), started, System.currentTimeMillis(), ResultSource.ANDROID_SYSTEM)
        }
    }
    private suspend fun runCommand(arguments: List<String>): String? = try { val process = ProcessBuilder(arguments).redirectErrorStream(true).start(); try { val deadline = System.currentTimeMillis() + (timeoutSeconds * maxHops + 3) * 1000L; while (process.isAlive && System.currentTimeMillis() < deadline) { currentCoroutineContext().ensureActive(); delay(100) }; if (process.isAlive) { process.destroyForcibly(); null } else process.inputStream.bufferedReader().readText().takeIf(String::isNotBlank) } finally { if (process.isAlive) process.destroyForcibly() } } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
    private fun parseHops(output: String): List<RouteHop> = output.lineSequence().mapIndexedNotNull { index, line ->
        val address = Regex("(?<![0-9a-fA-F:])(?:\\d{1,3}\\.){3}\\d{1,3}(?![0-9])|(?<![0-9a-fA-F:])(?:[0-9a-fA-F]{1,4}:){2,}[0-9a-fA-F:]+", RegexOption.IGNORE_CASE).find(line)?.value
        val latency = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*ms", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.toDoubleOrNull()?.toLong()
        if (address == null && '*' !in line && "From " !in line) null else RouteHop(Regex("^\\s*(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: index + 1, address, latency, address == null)
    }.toList()
}

data class LanHostObservation(val address: String, val reverseName: String?, val openPorts: List<Int>, val firstObservedAt: Long)

class BoundedLanScanner(private val concurrency: Int = 16, private val timeoutMs: Int = 350, private val ports: List<Int> = listOf(22, 53, 80, 443, 445, 554, 631, 8291)) {
    suspend fun scan(cidr: InvestigationTarget.Cidr): List<LanHostObservation> = coroutineScope {
        val addresses = ipv4Addresses(cidr); require(addresses.size <= 256) { "LAN scans are limited to 256 addresses" }
        val semaphore = kotlinx.coroutines.sync.Semaphore(concurrency.coerceIn(1, 24))
        addresses.map { address -> async(Dispatchers.IO) { semaphore.withPermit { probe(address) } } }.awaitAll().filterNotNull()
    }
    private fun probe(address: String): LanHostObservation? {
        val open = ports.filter { port -> runCatching { Socket().use { it.connect(InetSocketAddress(address, port), timeoutMs) }; true }.getOrDefault(false) }
        if (open.isEmpty()) return null
        val reverse = runCatching { InetAddress.getByName(address).canonicalHostName.takeUnless { it == address } }.getOrNull()
        return LanHostObservation(address, reverse, open, System.currentTimeMillis())
    }
    private fun ipv4Addresses(cidr: InvestigationTarget.Cidr): List<String> {
        require(TargetParser.parseIpv4(cidr.address) != null && cidr.prefixLength in 24..32) { "Only explicitly confirmed IPv4 ranges of /24 or smaller are accepted" }
        val raw = cidr.address.split('.').fold(0L) { acc, part -> (acc shl 8) or part.toLong() }; val hostBits = 32 - cidr.prefixLength; val network = raw shr hostBits shl hostBits; val count = 1 shl hostBits
        return (0 until count).map { value -> (network + value).let { ip -> (3 downTo 0).joinToString(".") { ((ip shr (it * 8)) and 255).toString() } } }
    }
}

data class MdnsService(val name: String, val type: String, val host: String?, val port: Int?)

class AndroidMdnsDiscovery(context: Context) {
    private val manager = context.getSystemService(NsdManager::class.java)
    @Suppress("DEPRECATION")
    fun discover(serviceType: String): Flow<MdnsService> = callbackFlow {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onServiceFound(info: NsdServiceInfo) { manager.resolveService(info, object : NsdManager.ResolveListener { override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { trySend(MdnsService(info.serviceName, info.serviceType, null, null)) }; override fun onServiceResolved(serviceInfo: NsdServiceInfo) { trySend(MdnsService(serviceInfo.serviceName, serviceInfo.serviceType, serviceInfo.host?.hostAddress, serviceInfo.port.takeIf { it > 0 })) } }) }
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { close(IllegalStateException("NSD start failed: $code")) }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
        }
        manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { runCatching { manager.stopServiceDiscovery(listener) } }
    }
}

data class SsdpDevice(val location: String?, val server: String?, val usn: String?, val sourceAddress: String)
class SsdpDiscovery(private val timeoutMs: Int = 2_000) {
    suspend fun discover(): List<SsdpDevice> = withContext(Dispatchers.IO) {
        val request = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n".toByteArray()
        val results = ConcurrentHashMap<String, SsdpDevice>(); DatagramSocket().use { socket ->
            socket.soTimeout = 250; socket.send(DatagramPacket(request, request.size, InetAddress.getByName("239.255.255.250"), 1900)); val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) try { val buffer = ByteArray(8192); val packet = DatagramPacket(buffer, buffer.size); socket.receive(packet); val text = buffer.copyOf(packet.length).toString(Charsets.UTF_8); val headers = text.lineSequence().mapNotNull { line -> line.indexOf(':').takeIf { it > 0 }?.let { line.substring(0, it).trim().lowercase() to line.substring(it + 1).trim() } }.toMap(); val device = SsdpDevice(headers["location"], headers["server"], headers["usn"], packet.address.hostAddress); results[device.usn ?: "${device.sourceAddress}:${device.location}"] = device } catch (_: SocketTimeoutException) { }
        }; results.values.toList()
    }
}
