# Data privacy

Network Investigator has no account, backend, cloud synchronization, advertisements, analytics, telemetry, or automatic upload. Offline-only mode is enabled by default. A direct diagnostic contacts the target explicitly entered by the user; optional enrichment is disabled and no provider is currently bundled.

Room and private app files hold investigations, incidents, connectivity samples, measurements, safe request templates, and preferences. Explicitly saved sensitive values can use an Android Keystore AES-GCM key, and an optional biometric/device-credential gate protects app entry. Common authorization, cookie, bearer/basic token, API-key, password, session, and custom secret fields are redacted from reports. Saved request templates deliberately omit authorization and bodies.

Exports leave the private sandbox only after the user chooses a destination through Android's document picker. Uninstalling the app removes private data because cloud/ADB backup is disabled. Retention can be applied for 7/30/90/365 days or indefinitely; settings also provide confirmed clear-all and export-all flows.
