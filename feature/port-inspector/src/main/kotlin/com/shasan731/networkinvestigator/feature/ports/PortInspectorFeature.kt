package com.shasan731.networkinvestigator.feature.ports
import com.shasan731.networkinvestigator.core.model.*
object PortInspectorFeature { val spec = FeatureSpec("port-inspector", "Port & Service Inspector", "Ports", "Check up to 256 selected ports with bounded, cancellable normal TCP connections and distinguish refused, timeout and unreachable states.", listOf(DiagnosticTaskType.PORTS), "No SYN/stealth scan, authentication, firewall bypass, or exploitation is performed.") }
