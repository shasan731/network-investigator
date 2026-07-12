package com.shasan731.networkinvestigator.core.diagnostics

import com.shasan731.networkinvestigator.core.model.PortObservation
import com.shasan731.networkinvestigator.core.model.PortState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class PortScanner(private val concurrency: Int = 12, private val timeoutMs: Int = 1_500) {
    init { require(concurrency in 1..32); require(timeoutMs in 100..30_000) }
    suspend fun scan(host: String, ports: List<Int>): List<PortObservation> = coroutineScope {
        require(ports.size <= PortRangeParser.MAX_PORTS)
        val semaphore = Semaphore(concurrency)
        ports.distinct().map { port -> async { semaphore.withPermit { probe(host, port) } } }.awaitAll().sortedBy { it.port }
    }

    private suspend fun probe(host: String, port: Int): PortObservation = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            PortObservation(port, PortState.OPEN, (System.nanoTime() - start) / 1_000_000, service(port))
        } catch (_: ConnectException) { PortObservation(port, PortState.REFUSED, null, service(port)) }
        catch (_: SocketTimeoutException) { PortObservation(port, PortState.TIMEOUT, null, service(port)) }
        catch (_: java.net.NoRouteToHostException) { PortObservation(port, PortState.UNREACHABLE, null, service(port)) }
        catch (_: Exception) { PortObservation(port, PortState.ERROR, null, service(port)) }
    }

    private fun service(port: Int) = mapOf(22 to "SSH", 25 to "SMTP", 53 to "DNS", 80 to "HTTP", 443 to "HTTPS/TLS", 3389 to "RDP", 5432 to "PostgreSQL", 7547 to "TR-069", 8291 to "RouterOS Winbox")[port]
}
