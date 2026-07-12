package com.shasan731.networkinvestigator.core.diagnostics

import com.shasan731.networkinvestigator.core.model.TargetParser
import java.net.InetAddress
import java.math.BigInteger

data class SubnetInfo(val networkAddress: String, val broadcastAddress: String?, val firstUsable: String, val lastUsable: String, val totalAddresses: BigInteger, val usableHosts: BigInteger)

object SubnetCalculator {
    fun calculate(address: String, prefixLength: Int): SubnetInfo {
        val normalized4 = TargetParser.parseIpv4(address)
        if (normalized4 != null) return ipv4(normalized4, prefixLength)
        val normalized6 = TargetParser.parseIpv6(address) ?: throw IllegalArgumentException("Invalid IP address")
        require(prefixLength in 0..128)
        val raw = InetAddress.getByName(normalized6).address
        val value = BigInteger(1, raw)
        val hostBits = 128 - prefixLength
        val size = BigInteger.ONE.shiftLeft(hostBits)
        val network = value.shiftRight(hostBits).shiftLeft(hostBits)
        val last = network + size - BigInteger.ONE
        return SubnetInfo(toIpv6(network), null, toIpv6(network), toIpv6(last), size, size)
    }

    private fun ipv4(address: String, prefix: Int): SubnetInfo {
        require(prefix in 0..32)
        val value = address.split('.').fold(0L) { acc, part -> (acc shl 8) or part.toLong() }
        val mask = if (prefix == 0) 0L else (0xffffffffL shl (32 - prefix)) and 0xffffffffL
        val network = value and mask
        val broadcast = network or (mask xor 0xffffffffL)
        val total = BigInteger.ONE.shiftLeft(32 - prefix)
        val usable = if (prefix <= 30) total - BigInteger.valueOf(2) else total
        val first = if (prefix <= 30) network + 1 else network
        val last = if (prefix <= 30) broadcast - 1 else broadcast
        return SubnetInfo(toIpv4(network), toIpv4(broadcast), toIpv4(first), toIpv4(last), total, usable)
    }

    private fun toIpv4(value: Long) = (3 downTo 0).joinToString(".") { ((value shr (it * 8)) and 255).toString() }
    private fun toIpv6(value: BigInteger): String {
        val source = value.toByteArray()
        val bytes = ByteArray(16)
        source.copyInto(bytes, destinationOffset = (16 - source.size).coerceAtLeast(0), startIndex = (source.size - 16).coerceAtLeast(0))
        return InetAddress.getByAddress(bytes).hostAddress
    }
}
