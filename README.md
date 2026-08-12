# Argusdroid

Argusdroid is an Android mobile app for long-term device encounter intelligence. It captures what supported phone radios can observe, stores normalized encounter records on-device, and enables ongoing analysis of activity patterns over time.

## What It Does

- Detects nearby broadcast sources through available Android sensor/radio APIs.
- Logs encounters in a persistent local datastore.
- Aggregates encounter history for trend and source analysis.
- Provides a modular architecture for advanced integrations.

## Current Capability Snapshot

- Android app scaffold using Kotlin and Jetpack Compose.
- Room-backed encounter persistence for long-term retention.
- Periodic background collection with WorkManager.
- Pluggable scanner layer for source-specific ingestion.
- Initial dashboard for source summaries and recent encounters.

## Reality Check: Platform Limits

Stock Android phones can reliably support Wi-Fi and BLE environment scanning with proper permissions, but there are hard limits:

- Broad arbitrary RF spectrum scanning is not universally exposed by Android APIs.
- Remote ID decoding is not available through one standard Android API across all devices.

Argusdroid includes integration hooks so external SDR hardware, OEM SDKs, and dedicated decoders can be added in later phases.

## Tech Stack

- Kotlin 2.x
- Android Gradle Plugin 8.x
- Jetpack Compose
- Room
- WorkManager
- Coroutines

## Repository Layout

- app: Android application module
- docs/architecture.md: technical architecture and data flow
- docs/capabilities-and-limits.md: practical sensing constraints and expansion path
- src: reserved for shared or non-Android code
- tests: repository-level test assets

## Getting Started

1. Install Android Studio (latest stable).
2. Ensure Android SDK Platform 36 is installed.
3. Open this repository in Android Studio.
4. Let Gradle sync complete.
5. Connect a physical Android device.
6. Run the app module.

## Runtime Permissions

Argusdroid requires runtime grants for key permissions, including location and radio access. Collection results are dependent on user grants, OEM behavior, and Android power policy.

## Data and Privacy Guidance

Before production deployment, implement:

- Clear user consent and transparent policy disclosures.
- Retention limits and user-controlled deletion/export.
- Encryption at rest for sensitive encounter payloads.
- Audit-safe handling for collected telemetry.

## Roadmap

- Replace BLE placeholder with callback-driven active scan sessions.
- Add cellular metadata ingestion where permitted.
- Add optional location enrichment and quality scoring.
- Add external SDR ingestion for extended RF visibility.
- Add Remote ID parsing pipeline (for supported sources/feeds).
- Add anomaly detection and scheduled analytics reports.

## Documentation

- Architecture: docs/architecture.md
- Capabilities and limits: docs/capabilities-and-limits.md

## License

See LICENSE.
