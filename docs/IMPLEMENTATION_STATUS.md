# Implementation status

“Functional baseline” means usable code exists today; it does not mean every requested milestone-depth capability is finished.

| Feature | Status | Implemented capabilities | Android limitations | Tests | Remaining work |
|---|---|---|---|---|---|
| Build/repository | Prepared for CI | Wrapper, catalog, convention plugins, modules, Hilt/Room/Compose, debug/release workflows | GitHub run is source of truth | Basic model/core suites | Confirm first GitHub run and address runner-specific issues |
| Target Intelligence | Implemented | Parser, normalization/scope, DNS/HTTP/TLS/subnet/history and optional labelled RDAP | ASN depth varies by RDAP response; no exact geolocation claim | Parser/classification | Additional enrichment providers can use the existing interface |
| Network Toolkit | Implemented with platform limits | DNS/TCP/HTTP/TLS, network state, subnet, bounded loss/jitter sampling and ports | Raw ICMP/MTU is unavailable on some unprivileged devices | Metrics/subnet/diagnosis | None beyond device-dependent raw probe availability |
| Website Investigator | Implemented | Methods, headers/query/body/auth, redirects, phase timing, family selection, metadata, preview/hash/security checks | Response preview/body safety caps; explicit HTTP is request-scoped | MockWebServer redirects, methods, headers, metadata | None for core diagnostic workflow |
| DNS Detective | Implemented | System A/AAAA, custom UDP/TCP fallback, DoH, DoT, required record parsing and resolver comparison | DNSSEC only when provider supplies authenticated status | Parser/comparison | Provider-specific DNSSEC adapters |
| LAN Explorer | Implemented with permission limits | Confirmed bounded scan, safe ports, reverse lookup, persistence, mDNS/NSD and SSDP | MAC/vendor often unavailable; permission and multicast/device policies apply | Port/CIDR bounds | None beyond platform availability |
| Wi-Fi Diagnostics | Implemented with permission limits | Full available connection fields, nearby one-shot scan/overlap, saved labelled measurements | IDs/scans may be redacted/throttled and location services may be required | Deterministic channel mapping is runtime code | None beyond OS availability |
| Route Investigator | Implemented with fallback | Probe abstraction, system traceroute, TTL ping fallback, partial hop UI | Commands may be absent; silent hops remain non-diagnostic | Diagnosis rejects silent-hop inference | None beyond platform availability |
| TLS Investigator | Implemented | Verified SNI/hostname, full presented chain, keys/SAN/dates/protocol/cipher/fingerprint and multi-IP comparison | Results depend on server/path and trust store | TLS policy | Long-term snapshot diff can be derived from saved investigations |
| Port Inspector | Implemented | Lists/ranges/profiles, cap, bounded cancellation, state distinctions, verified TLS and safe banners/HTTP | Middleboxes obscure timeout meaning | Port parser | None for safe TCP scope |
| Connectivity Recorder | Implemented with OS limits | Foreground start/stop/timeout, callbacks, target latency/loss, WorkManager and rolling-window export | OS may stop/defer work | Deterministic metrics | None beyond Android scheduling behavior |
| Network Compare | Implemented | Run selectors, structural changes and threshold-controlled metric classification | Missing values remain incomparable | Diagnosis/model tests | Additional domain-specific thresholds can be added |
| Evidence Collector | Implemented | Incident/status/linking, persisted attachments, redaction preview and all SAF formats/structured ZIP | User controls URI grants | Redaction/serialization | None for core incident export |
| Database/privacy | Implemented | 19 entities, FKs/indices/transactions/pagination/migration, retention/clear/export, DataStore, Keystore, biometric gate and bounded safe log | Biometric depends on device enrollment | Serialization/redaction | GitHub runner still must validate generated Room/Hilt code |
