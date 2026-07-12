package com.shasan731.networkinvestigator.feature.lan
import com.shasan731.networkinvestigator.core.model.*
object LanExplorerFeature { val spec = FeatureSpec("lan-explorer", "LAN Explorer", "LAN", "Discover a confirmed local subnet with bounded normal TCP probes and platform mDNS/SSDP hooks.", listOf(DiagnosticTaskType.LAN, DiagnosticTaskType.PORTS), "Discovery is limited to 256 addresses, rate-limited, visible, cancellable, and does not imply missing devices are offline. MAC addresses are often unavailable.") }
