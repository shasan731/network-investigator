package com.shasan731.networkinvestigator.feature.route
import com.shasan731.networkinvestigator.core.model.*
object RouteInvestigatorFeature { val spec = FeatureSpec("route-investigator", "Route Investigator", "Routing", "Use an interchangeable route-probe abstraction and retain partial hops with evidence sources.", listOf(DiagnosticTaskType.ROUTE, DiagnosticTaskType.TCP), "Unprivileged Android cannot guarantee classic ICMP traceroute. Silent intermediate hops are not treated as route failure.") }
