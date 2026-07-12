# Architecture

The app has a single composition root and independent feature modules. `InvestigationEngine` transforms a validated target into a bounded concurrent task set. Each task returns `Success`, `Partial`, `Failure`, `Unsupported`, or `Cancelled` with a `ResultSource`. The engine correlates cards into `DiagnosisEvidence`; `DiagnosisEngine` applies deterministic ordered rules and embeds the exact observed facts.

`core:model` contains serializable domain contracts and the strict target parser. `core:network` owns direct DNS/TCP/HTTP/TLS observations and DNS wire parsing. `core:diagnostics` owns orchestration, subnet/port calculations and rules. `core:database` stores summaries and serialized snapshots in Room; large bodies are designed to live in private files referenced by entities. `core:security` owns redaction and Keystore primitives. `core:reporting` builds local reports. `core:datastore` stores privacy/theme/retention preferences. `core:ui` provides shared theme/result UI.

Room uses foreign keys, indices, transactions, pagination, and an explicit 1→2 migration. Feature modules do not depend on one another. UI state is exposed from a Hilt view model through StateFlow. Network and file work runs off the main thread and structured concurrency propagates cancellation.

