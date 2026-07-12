package com.shasan731.networkinvestigator.core.reporting
import com.shasan731.networkinvestigator.core.model.*
import org.junit.Assert.*
import org.junit.Test
class ReportExporterTest {
    @Test fun `json and csv serialize evidence without credentials`() { val snapshot = InvestigationSnapshot("id", "example.com?api_key=secret", InvestigationProfile.QUICK_CHECK, 1, 2, listOf(DiagnosticCard(DiagnosticTaskType.HTTP, DiagnosticStatus.SUCCESS, "HTTP", "HTTP 200", "Authorization: Bearer abc", null, ResultSource.DIRECT_TEST, 1, 1)), null); val json = ReportExporter.json(snapshot); assertFalse(json.contains("secret")); assertFalse(json.contains("Bearer abc")); assertTrue(ReportExporter.csv(snapshot).contains("tool,status,source")) }
}
