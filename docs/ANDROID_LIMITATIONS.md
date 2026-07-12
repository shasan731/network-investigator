# Android limitations and permissions

Android applications are unprivileged. Network APIs cannot guarantee raw ICMP, arbitrary TTL manipulation, classic traceroute, ARP/MAC access, or complete LAN discovery. A missing traceroute hop or undiscovered LAN host is not evidence that the target is offline. The app uses normal TCP and HTTPS results as stronger reachability evidence. Wi-Fi scans are throttled and fields may be redacted by OS/device policy.

| Permission | Requested/used when | Reduced behavior when denied or unavailable |
|---|---|---|
| `INTERNET` | Direct user-started diagnostics | Network probes cannot run; local parser/subnet/history remain usable. |
| `ACCESS_NETWORK_STATE` | Current network and recorder | Transport, validation, VPN and captive-portal fields are unavailable. |
| `ACCESS_WIFI_STATE` | Wi-Fi diagnostics | Wi-Fi connection fields are unavailable. |
| `CHANGE_WIFI_MULTICAST_STATE` | Future explicit mDNS/SSDP discovery | Multicast discovery is unavailable. |
| `NEARBY_WIFI_DEVICES` | Android 13+ Wi-Fi details/scans | Protected Wi-Fi identifiers/scans are unavailable. |
| `ACCESS_FINE_LOCATION` | Older Android Wi-Fi scan rules | Older-device SSID/BSSID/scan fields are unavailable. |
| `POST_NOTIFICATIONS` | User starts live recording on Android 13+ | Android may suppress the notification drawer entry; the foreground-service task remains OS-visible and unrelated tools work. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | User-started live recorder | Live recording cannot run. |
| `ACCESS_LOCAL_NETWORK` | API 37 local-network operations | LAN tools return permission-required/reduced results; internet tools continue. Older versions do not have this permission. |

The strict network-security configuration rejects cleartext in ordinary app clients. When a user explicitly enters an `http://` target, the Website Investigator uses a scoped raw HTTP diagnostic connection for that request only; it does not globally permit cleartext and it never carries state into unrelated requests. TLS trust is never disabled globally. Android may defer WorkManager jobs, stop foreground services, hide SSID/BSSID, or deny protected link properties. The UI reports available system evidence without substituting guessed values.
