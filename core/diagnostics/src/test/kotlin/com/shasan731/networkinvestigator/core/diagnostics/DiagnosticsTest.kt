package com.shasan731.networkinvestigator.core.diagnostics
import com.shasan731.networkinvestigator.core.model.*
import org.junit.Assert.*
import org.junit.Test
class DiagnosticsTest {
    @Test fun `calculates ipv4 subnet`() { val s = SubnetCalculator.calculate("192.168.1.42", 24); assertEquals("192.168.1.0", s.networkAddress); assertEquals("192.168.1.255", s.broadcastAddress); assertEquals("192.168.1.1", s.firstUsable); assertEquals("254", s.usableHosts.toString()) }
    @Test fun `port parser deduplicates and bounds`() { assertEquals(listOf(22, 80, 81, 82, 443), PortRangeParser.parse("443,80-82,22,80").getOrThrow()); assertTrue(PortRangeParser.parse("1-300").isFailure); assertTrue(PortRangeParser.parse("0").isFailure) }
    @Test fun `diagnosis cites exact evidence`() { val diagnosis = requireNotNull(DiagnosisEngine.diagnose(DiagnosisEvidence(gatewayReachable = true, dnsSucceeded = true, tcpSucceeded = true, tlsSucceeded = true, httpStatus = 503))); assertEquals(NetworkLayer.APPLICATION, diagnosis.probableLayer); assertEquals(ConfidenceLevel.HIGH, diagnosis.confidence); assertTrue(diagnosis.observedFacts.any { it.label == "HTTP status" && it.value == "503" }) }
    @Test fun `silent route information alone produces no diagnosis`() { assertNull(DiagnosisEngine.diagnose(DiagnosisEvidence())) }
}

