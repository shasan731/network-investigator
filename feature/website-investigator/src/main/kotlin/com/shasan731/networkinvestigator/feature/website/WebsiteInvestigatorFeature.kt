package com.shasan731.networkinvestigator.feature.website
import com.shasan731.networkinvestigator.core.model.*
object WebsiteInvestigatorFeature { val spec = FeatureSpec("website-investigator", "Website Investigator", "Web and API", "Inspect status, redirects, protocol, response size, headers, timing, and security-header posture without persisting secrets.", listOf(DiagnosticTaskType.DNS, DiagnosticTaskType.TCP, DiagnosticTaskType.HTTP, DiagnosticTaskType.TLS)) }
