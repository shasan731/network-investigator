package com.shasan731.networkinvestigator.core.model

import org.junit.Assert.*
import org.junit.Test

class TargetParserTest {
    @Test fun `normalizes domains and urls while preserving request components`() {
        val domain = (TargetParser.parse("  WWW.Example.COM. ") as TargetParseResult.Valid).parsed.target
        assertEquals(InvestigationTarget.Domain("www.example.com"), domain)
        val url = (TargetParser.parse("HTTPS://Example.COM:8443/a%20b?q=1#x") as TargetParseResult.Valid).parsed.target as InvestigationTarget.Url
        assertEquals("https://example.com:8443/a%20b?q=1#x", url.value)
    }
    @Test fun `parses ipv4 ipv6 host port and cidr`() {
        assertTrue((TargetParser.parse("8.8.8.8") as TargetParseResult.Valid).parsed.target is InvestigationTarget.Ipv4)
        assertTrue((TargetParser.parse("2001:4860:4860::8888") as TargetParseResult.Valid).parsed.target is InvestigationTarget.Ipv6)
        assertEquals(443, ((TargetParser.parse("[2001:db8::1]:443") as TargetParseResult.Valid).parsed.target as InvestigationTarget.HostPort).port)
        assertEquals(24, ((TargetParser.parse("192.168.1.0/24") as TargetParseResult.Valid).parsed.target as InvestigationTarget.Cidr).prefixLength)
    }
    @Test fun `rejects malformed ports addresses and cidrs`() {
        listOf("example.com:0", "example.com:65536", "01.2.3.4", "256.1.1.1", "192.168.1.1/33", "a b.com", "https://user:pass@example.com").forEach { assertTrue("Expected invalid: $it", TargetParser.parse(it) is TargetParseResult.Invalid) }
    }
    @Test fun `classifies important address scopes`() {
        assertEquals(AddressScope.PRIVATE, IpClassifier.classifyIpv4("192.168.1.2"))
        assertEquals(AddressScope.LOOPBACK, IpClassifier.classifyIpv4("127.0.0.1"))
        assertEquals(AddressScope.DOCUMENTATION, IpClassifier.classifyIpv4("203.0.113.10"))
        assertEquals(AddressScope.PUBLIC, IpClassifier.classifyIpv4("8.8.8.8"))
        assertEquals(AddressScope.PRIVATE, IpClassifier.classifyIpv6("fd00::1"))
        assertEquals(AddressScope.DOCUMENTATION, IpClassifier.classifyIpv6("2001:db8::1"))
    }
}

