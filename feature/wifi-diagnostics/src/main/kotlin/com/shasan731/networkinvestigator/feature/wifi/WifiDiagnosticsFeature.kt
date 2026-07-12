package com.shasan731.networkinvestigator.feature.wifi
import com.shasan731.networkinvestigator.core.model.*
object WifiDiagnosticsFeature { val spec = FeatureSpec("wifi-diagnostics", "Wi-Fi Diagnostics", "Wi-Fi", "Read permission-available connection properties and save labelled signal/latency measurements.", listOf(DiagnosticTaskType.WIFI, DiagnosticTaskType.NETWORK_INFO), "SSID, BSSID and scans are permission-, location-service-, device-, and throttling-dependent; unavailable values remain unknown.") }
