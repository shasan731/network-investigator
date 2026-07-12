package com.shasan731.networkinvestigator.core.reporting

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.shasan731.networkinvestigator.core.model.InvestigationSnapshot
import com.shasan731.networkinvestigator.core.security.SecretRedactor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ReportExporter {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun json(snapshot: InvestigationSnapshot): String = SecretRedactor.redact(json.encodeToString(snapshot))
    fun plainText(snapshot: InvestigationSnapshot): String = SecretRedactor.redact(buildString {
        appendLine("Network Investigator evidence report")
        appendLine("Target: ${snapshot.target}"); appendLine("Profile: ${snapshot.profile}")
        appendLine("Started: ${snapshot.startedAtEpochMs}"); appendLine()
        snapshot.cards.forEach { appendLine("${it.title}: ${it.status} — ${it.primaryResult}"); appendLine("Source: ${it.source}"); if (it.limitation != null) appendLine("Limitation: ${it.limitation}"); appendLine() }
        snapshot.diagnosis?.let { appendLine("Diagnosis: ${it.title} (${it.confidence})"); it.observedFacts.forEach { fact -> appendLine("Evidence: ${fact.label} = ${fact.value} [${fact.source}]") } }
    })
    fun csv(snapshot: InvestigationSnapshot): String = buildString {
        appendLine("tool,status,source,duration_ms,result")
        snapshot.cards.forEach { appendLine(listOf(it.title, it.status.name, it.source.name, it.durationMs.toString(), it.primaryResult).joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" }) }
    }

    fun writeZip(snapshot: InvestigationSnapshot, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            fun entry(path: String, contents: String) { zip.putNextEntry(ZipEntry(path)); zip.write(SecretRedactor.redact(contents).toByteArray()); zip.closeEntry() }
            entry("incident.json", json(snapshot)); entry("summary.txt", plainText(snapshot))
            entry("investigations/${snapshot.id}.json", json(snapshot))
            listOf("dns", "http", "tls", "routes", "ports", "attachments").forEach { path -> zip.putNextEntry(ZipEntry("$path/")); zip.closeEntry() }
        }
    }

    fun writeAllZip(snapshots: List<InvestigationSnapshot>, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("summary.txt")); zip.write("Network Investigator local export\nInvestigations: ${snapshots.size}\n".toByteArray()); zip.closeEntry()
            snapshots.forEach { snapshot -> zip.putNextEntry(ZipEntry("investigations/${snapshot.id}.json")); zip.write(json(snapshot).toByteArray()); zip.closeEntry() }
        }
    }
    fun writeAllDataZip(snapshots: List<InvestigationSnapshot>, sections: Map<String, String>, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            fun entry(path: String, content: String) { zip.putNextEntry(ZipEntry(path)); zip.write(SecretRedactor.redact(content).toByteArray()); zip.closeEntry() }
            entry("summary.txt", "Network Investigator complete local export\nInvestigations: ${snapshots.size}\nSections: ${sections.keys.joinToString()}\n")
            snapshots.forEach { entry("investigations/${it.id}.json", json(it)) }
            sections.forEach { (path, content) -> entry(path.replace(Regex("[^A-Za-z0-9._/-]"), "_"), content) }
        }
    }

    data class IncidentReport(val id: String, val title: String, val customerOrSite: String?, val target: String?, val problem: String, val status: String, val severity: String)
    data class ReportAttachment(val displayName: String, val uri: String)
    fun writeIncidentZip(incident: IncidentReport, snapshots: List<InvestigationSnapshot>, attachments: List<ReportAttachment>, output: OutputStream, openAttachment: (String) -> InputStream?) {
        ZipOutputStream(output).use { zip ->
            val incidentJson = json.encodeToString(mapOf("id" to incident.id, "title" to incident.title, "customerOrSite" to incident.customerOrSite.orEmpty(), "target" to incident.target.orEmpty(), "problem" to incident.problem, "status" to incident.status, "severity" to incident.severity))
            fun textEntry(path: String, value: String) { zip.putNextEntry(ZipEntry(path)); zip.write(SecretRedactor.redact(value).toByteArray()); zip.closeEntry() }
            textEntry("incident.json", incidentJson); textEntry("summary.txt", "${incident.title}\n${incident.problem}\nStatus: ${incident.status}\nSeverity: ${incident.severity}\n")
            snapshots.forEach { snapshot -> textEntry("investigations/${snapshot.id}.json", json(snapshot)) }
            listOf("dns", "http", "tls", "routes", "ports").forEach { folder -> zip.putNextEntry(ZipEntry("$folder/")); zip.closeEntry() }
            attachments.forEachIndexed { index, attachment -> openAttachment(attachment.uri)?.use { input -> val safeName = attachment.displayName.replace(Regex("[^A-Za-z0-9._-]"), "_"); zip.putNextEntry(ZipEntry("attachments/${index + 1}-$safeName")); input.copyTo(zip); zip.closeEntry() } }
            if (attachments.isEmpty()) { zip.putNextEntry(ZipEntry("attachments/")); zip.closeEntry() }
        }
    }

    fun writePdf(snapshot: InvestigationSnapshot, output: OutputStream) {
        val document = PdfDocument(); val paint = Paint().apply { textSize = 11f; isAntiAlias = true }
        val lines = plainText(snapshot).lineSequence().flatMap { line -> if (line.length <= 92) sequenceOf(line) else line.chunked(92).asSequence() }.toList()
        var pageNumber = 1
        lines.chunked(48).forEach { pageLines ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber++).create())
            pageLines.forEachIndexed { index, line -> page.canvas.drawText(line, 36f, 48f + index * 15f, paint) }
            document.finishPage(page)
        }
        document.writeTo(output); document.close()
    }
}
