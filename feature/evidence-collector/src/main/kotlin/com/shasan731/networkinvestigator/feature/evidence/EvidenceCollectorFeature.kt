package com.shasan731.networkinvestigator.feature.evidence
import com.shasan731.networkinvestigator.core.model.*
object EvidenceCollectorFeature { val spec = FeatureSpec("evidence-collector", "Support Evidence Collector", "Evidence", "Create local incidents, link saved runs, preview redaction, and export JSON, CSV, text, PDF, or ZIP through the system document picker.", listOf(DiagnosticTaskType.EXPORT), "Attachments are user-selected URIs; exports redact common credentials and user-configured secret field names.") }
