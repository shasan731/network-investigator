# Network Investigator

Network Investigator is a local-first Android troubleshooting app for collecting evidence across DNS, TCP, HTTP, TLS, local networks, Wi-Fi, routes, ports, and connectivity changes. It uses deterministic rules: every result identifies its source, and an unavailable Android capability is shown as partial or unsupported rather than fabricated.

The application ID is `com.shasan731.networkinvestigator`; version `0.1.0` targets API 37 and supports Android 8.0 (API 26) and newer.

## Implemented capabilities

- Unified parser for URLs, domains, hostnames, IPv4, IPv6, host/port, and CIDR, including scope classification and structured validation errors.
- Concurrent, cancellable quick investigations with system DNS, normal TCP reachability, HTTP redirects/timing/metadata, verified SNI TLS handshakes, and subnet calculations.
- Deterministic evidence-based diagnosis and per-result source, duration, status, technical detail, and limitations.
- Bounded port/service inspection (normal TCP only), UDP/TCP/DoH/DoT DNS, verified multi-address TLS, advanced HTTP/API requests, route fallbacks, bounded LAN/mDNS/SSDP discovery, Wi-Fi scans/measurements, and latency/loss sampling.
- Room history that can save and reopen investigations; two-run comparison; incident creation linked to an investigation.
- User-started foreground connectivity recording and Android-compliant periodic WorkManager monitoring.
- Redacted JSON, CSV, plain text, PDF, and structured ZIP reporting with linked attachments; every destination uses the Storage Access Framework.
- Offline-only privacy default, DataStore preferences, Android Keystore encryption architecture, no backend/account/telemetry/ads.

All twelve requested feature areas have navigable functional screens. See [implementation status](docs/IMPLEMENTATION_STATUS.md) for exact depth and remaining work; the project does not label milestone-depth gaps as complete.

## Architecture

The repository uses Kotlin, Compose Material 3, MVVM, Hilt, coroutines/Flow, Room, DataStore, OkHttp, WorkManager, and clean dependency direction. Feature modules depend on shared model/UI contracts; diagnostics depend on reusable model/network code; the app composes and injects implementations. Convention plugins in `build-logic` centralize Android/JVM/Compose defaults, and all dependency versions live in `gradle/libs.versions.toml`.

```text
app and feature modules
        ↓
core:model, core:ui, core:diagnostics
        ↓
core:network, core:database, core:datastore, core:security, core:reporting
```

More detail: [architecture](docs/ARCHITECTURE.md), [features](docs/FEATURES.md), [privacy](docs/DATA_PRIVACY.md), and [Android limitations](docs/ANDROID_LIMITATIONS.md).

## Repository structure

`app/` is the Android composition root. `build-logic/` contains convention plugins. `core/` contains common, model, database, datastore, network, diagnostics, reporting, security, and UI modules. `feature/` contains dashboard, investigate, target intelligence, network tools, website, DNS, LAN, Wi-Fi, route, TLS, ports, recorder, comparison, and evidence modules. `.github/workflows/` contains clean-runner CI/release automation, while `docs/` records architecture, privacy, platform limitations, features, build instructions, and exact implementation status.

## GitHub-only build process

No Android Studio, local Android SDK, local JDK, emulator, local Gradle, or `local.properties` is required. The Gradle 9.4.1 wrapper is committed. CI installs Temurin JDK 17, API 37, and Build Tools 36.0.0, then runs deterministic unit tests, lint, and debug assembly.

1. Commit and push this repository to GitHub.
2. Open **Actions** and select **Android CI**, or let a push to `main`/`develop` trigger it.
3. Open the successful run and download **network-investigator-debug** from Artifacts.
4. Extract the artifact ZIP and transfer the APK to the Android device.
5. Allow installs from the chosen browser/file-manager source and open the APK.

The expected input path is `app/build/outputs/apk/debug/`; the artifact name is `network-investigator-debug`. GitHub Actions is the build source of truth. See [exact build/download instructions](docs/GITHUB_ACTIONS_BUILD.md).

## Signed releases

Add these repository secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Push a `v*` tag or manually run **Signed Android Release**. The runner decodes the keystore into its temporary directory, passes signing values as environment-backed Gradle properties, builds signed APK/AAB files, uploads them, and deletes the temporary keystore. Debug CI never reads or needs these secrets.

## Permissions

`INTERNET`, network/Wi-Fi state, multicast state, nearby Wi-Fi/location (version dependent), notifications, foreground service/data sync, and API-guarded local-network access are declared. Runtime permissions are requested at point of use; denial leaves unrelated diagnostics available. See [Android limitations](docs/ANDROID_LIMITATIONS.md) for the purpose and behavior of each permission.

## Privacy and safety

The app is local-first and performs no automatic upload or third-party lookup. It never performs stealth SYN scans, authentication attempts, exploitation, firewall bypass, or aggressive scanning. LAN/port work is bounded, explicit, rate-conscious, and cancellable. Authorization/cookie/token/password-like values are redacted from exports. Request credentials and bodies remain session-only; saved templates omit them. Retention, biometric/device lock, clear-all, export-all, incident attachments, and redaction preview are available in the app.
