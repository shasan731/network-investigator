package com.shasan731.networkinvestigator.core.network

import java.nio.ByteBuffer

data class DnsRecord(val name: String, val type: Int, val ttl: Long, val value: String)

object DnsResponseParser {
    fun isTruncated(bytes: ByteArray): Boolean = bytes.size >= 4 && (u16(bytes, 2) and 0x0200) != 0
    fun responseCode(bytes: ByteArray): Int = if (bytes.size >= 4) u16(bytes, 2) and 0x000f else -1
    fun parse(bytes: ByteArray): List<DnsRecord> {
        require(bytes.size >= 12) { "DNS message is shorter than its header" }
        val buffer = ByteBuffer.wrap(bytes)
        val flags = buffer.getShort(2).toInt() and 0xffff
        require(flags and 0x8000 != 0) { "DNS message is not a response" }
        val questionCount = buffer.getShort(4).toInt() and 0xffff
        val answerCount = buffer.getShort(6).toInt() and 0xffff
        var offset = 12
        repeat(questionCount) { offset = skipName(bytes, offset); require(offset + 4 <= bytes.size); offset += 4 }
        return buildList {
            repeat(answerCount) {
                val (name, nameEnd) = readName(bytes, offset); offset = nameEnd
                require(offset + 10 <= bytes.size) { "Truncated DNS answer" }
                val type = u16(bytes, offset); val ttl = u32(bytes, offset + 4); val length = u16(bytes, offset + 8); offset += 10
                require(offset + length <= bytes.size) { "Truncated DNS RDATA" }
                val value = when (type) {
                    1 -> require(length == 4).let { (0 until 4).joinToString(".") { (bytes[offset + it].toInt() and 255).toString() } }
                    28 -> require(length == 16).let { java.net.InetAddress.getByAddress(bytes.copyOfRange(offset, offset + length)).hostAddress }
                    2, 5, 12 -> readName(bytes, offset).first
                    15 -> "${u16(bytes, offset)} ${readName(bytes, offset + 2).first}"
                    16 -> parseTxt(bytes, offset, length)
                    6 -> parseSoa(bytes, offset)
                    33 -> "${u16(bytes, offset)} ${u16(bytes, offset + 2)} ${u16(bytes, offset + 4)} ${readName(bytes, offset + 6).first}"
                    257 -> { val flags = bytes[offset].toInt() and 255; val tagLength = bytes[offset + 1].toInt() and 255; require(tagLength + 2 <= length); "$flags ${bytes.copyOfRange(offset + 2, offset + 2 + tagLength).toString(Charsets.US_ASCII)} ${bytes.copyOfRange(offset + 2 + tagLength, offset + length).toString(Charsets.US_ASCII)}" }
                    else -> bytes.copyOfRange(offset, offset + length).joinToString("") { "%02x".format(it) }
                }
                add(DnsRecord(name, type, ttl, value)); offset += length
            }
        }
    }

    private fun parseSoa(bytes: ByteArray, start: Int): String {
        val (mname, afterMname) = readName(bytes, start); val (rname, afterRname) = readName(bytes, afterMname)
        require(afterRname + 20 <= bytes.size)
        return "$mname $rname ${u32(bytes, afterRname)} ${u32(bytes, afterRname + 4)} ${u32(bytes, afterRname + 8)} ${u32(bytes, afterRname + 12)} ${u32(bytes, afterRname + 16)}"
    }

    private fun parseTxt(bytes: ByteArray, start: Int, length: Int): String {
        var offset = start; val end = start + length; val chunks = mutableListOf<String>()
        while (offset < end) { val count = bytes[offset++].toInt() and 255; require(offset + count <= end); chunks += bytes.copyOfRange(offset, offset + count).toString(Charsets.UTF_8); offset += count }
        return chunks.joinToString("")
    }
    private fun skipName(bytes: ByteArray, start: Int) = readName(bytes, start).second
    private fun readName(bytes: ByteArray, start: Int): Pair<String, Int> {
        var offset = start; var consumedEnd = -1; var jumps = 0; val labels = mutableListOf<String>()
        while (true) {
            require(offset < bytes.size && jumps++ < 128) { "Invalid DNS name" }
            val length = bytes[offset].toInt() and 255
            if (length == 0) { if (consumedEnd < 0) consumedEnd = offset + 1; break }
            if (length and 0xc0 == 0xc0) { require(offset + 1 < bytes.size); val pointer = ((length and 0x3f) shl 8) or (bytes[offset + 1].toInt() and 255); if (consumedEnd < 0) consumedEnd = offset + 2; offset = pointer; continue }
            require(length <= 63 && offset + 1 + length <= bytes.size)
            labels += bytes.copyOfRange(offset + 1, offset + 1 + length).toString(Charsets.US_ASCII); offset += 1 + length
        }
        return labels.joinToString(".") to consumedEnd
    }
    private fun u16(bytes: ByteArray, at: Int) = ((bytes[at].toInt() and 255) shl 8) or (bytes[at + 1].toInt() and 255)
    private fun u32(bytes: ByteArray, at: Int) = ((0 until 4).fold(0L) { acc, i -> (acc shl 8) or (bytes[at + i].toLong() and 255) })
}

data class ResolverDifference(val added: Set<String>, val removed: Set<String>, val ttlChanged: Set<String>)
object ResolverComparator {
    fun compare(baseline: List<DnsRecord>, candidate: List<DnsRecord>): ResolverDifference {
        val a = baseline.associateBy { "${it.type}:${it.value}" }; val b = candidate.associateBy { "${it.type}:${it.value}" }
        return ResolverDifference(b.keys - a.keys, a.keys - b.keys, (a.keys intersect b.keys).filterTo(mutableSetOf()) { a[it]?.ttl != b[it]?.ttl })
    }
}
