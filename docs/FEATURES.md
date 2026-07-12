# Feature behavior

1. **Target Intelligence** — normalization, IP scope, DNS, HTTP/TLS metadata, subnet, local history, and explicitly enabled RDAP bootstrap enrichment with provider/source labelling.
2. **Network Toolkit** — system DNS, TCP and HTTP reachability, subnet/CIDR, loss/jitter and bounded port primitives. ICMP/MTU remains best-effort/unsupported.
3. **Website Investigator** — all required methods, headers/query/body kinds, ephemeral basic/bearer auth, redirect control, mobile/desktop and IP-family selection, phase timings, headers/security checks, bounded preview, metadata and SHA-256. Explicit HTTP uses a request-scoped raw connection.
4. **DNS Detective** — Android address resolver plus selectable UDP/TCP (with truncation fallback), DoH and verified DoT; A/AAAA/CNAME/MX/NS/TXT/SOA/SRV/CAA/PTR parsing and record/TTL comparison contracts. DNSSEC remains `null` unless a selected provider supplies authenticated status.
5. **LAN Explorer** — explicit authorization, API 37 local-network permission, /24-or-smaller bound, 256-address maximum, bounded normal TCP discovery, reverse names, NSD/mDNS, SSDP, persistence and cancellation.
6. **Wi-Fi Diagnostics** — current SSID/BSSID/RSSI/frequency/channel/band/security/link rates, gateway/DNS/validation/captive/VPN/metered state, one-shot nearby scans, overlap summary and labelled Room measurements.
7. **Route Investigator** — interchangeable probe contract, system traceroute, TTL-capable ping fallback, hop UI, cancellation and explicit unsupported result where commands are absent.
8. **TLS Investigator** — verified SNI/hostname, full presented chain, SAN/dates/issuer/key details/protocol/cipher/fingerprint and multi-address comparison.
9. **Port Inspector** — single/list/range and named profiles, 256-port cap, bounded concurrency/cancellation, state distinctions, verified TLS detection and safe bounded banner/HTTP preview. No stealth or exploitation behavior.
10. **Connectivity Recorder** — foreground sessions with explicit probe target/interval, network callbacks, latency/loss samples, WorkManager monitoring, and full/5/15/30-minute CSV saves.
11. **Network Compare** — selectable recent runs, added/removed/changed/unchanged and threshold-controlled improved/degraded duration classification.
12. **Evidence Collector** — incident fields/statuses, linked runs, persisted user-selected attachments, redaction preview and SAF JSON/CSV/text/PDF/structured ZIP export including attachments.
