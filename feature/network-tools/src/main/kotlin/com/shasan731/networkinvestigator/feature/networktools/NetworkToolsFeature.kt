package com.shasan731.networkinvestigator.feature.networktools
import com.shasan731.networkinvestigator.core.model.*
object NetworkToolsFeature { val spec = FeatureSpec("network-tools", "Network Toolkit", "Reachability", "Run DNS, normal TCP connections, HTTP latency, subnet calculations, loss and jitter calculations.", listOf(DiagnosticTaskType.DNS, DiagnosticTaskType.TCP, DiagnosticTaskType.HTTP, DiagnosticTaskType.SUBNET), "ICMP and MTU probes are best-effort on unprivileged Android; TCP/HTTP success takes precedence over ICMP failure.") }
