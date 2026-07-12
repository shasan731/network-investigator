package com.shasan731.networkinvestigator.feature.recorder
import com.shasan731.networkinvestigator.core.model.*
object ConnectivityRecorderFeature { val spec = FeatureSpec("recorder", "Connectivity Recorder", "Recording", "Record user-started live connectivity samples with a visible foreground notification; schedule compliant deferred checks with WorkManager.", listOf(DiagnosticTaskType.NETWORK_INFO, DiagnosticTaskType.DNS, DiagnosticTaskType.TCP), "Android may delay periodic WorkManager jobs and restrict foreground services; the app reports those restrictions.") }
