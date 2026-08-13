# Argusdroid

Argusdroid is an Android mobile app for long-term device encounter intelligence. It captures what supported phone radios can observe, stores normalized encounter records on-device, and enables ongoing analysis of activity patterns over time.

## What It Does

- Detects nearby broadcast sources through available Android sensor/radio APIs.
- Logs encounters in a persistent local datastore.
- Aggregates encounter history for trend and source analysis.
- Provides a modular architecture for advanced integrations.

## Current Capability Snapshot

- Kotlin + Jetpack Compose Android app with Room-backed encounter persistence.
- Multi-source sensing pipeline for Wi-Fi, BLE, and cellular data collection.
- Location snapshot enrichment attached to supported encounter records.
- Home, Detection, Devices, Encounters, and Settings app sections.
- Tracking controls with status feedback, real-time status refresh, and last scan timestamp.
- Per-sensor collection toggles on Home (Wi-Fi, Bluetooth LE, Cellular, Remote ID), persisted in app settings.
- Detection readiness checks with recommended settings and deep links to system settings.
- Detection map suite with Device Encounters Map and Device Location Map.
- Device Location Map supports approximate locations across device types:
	- CELL uses tower lookup with observed-location fallback.
	- Wi-Fi/BLE use multi-observation range inference with observed-location fallback.
	- Other sources use best available observed encounter location.
- Source-colored map pins with in-app color legend by detected source type.
- Map controls for live updates, pin limits, and optional diagnostics panel.
- Live map mode defaults on and includes a compact in-header LIVE badge.
- Devices and Encounters filtering by scope (Recent 100 or All), source, and text query.
- Devices sorting by Last Seen or Most Seen.
- Devices and Encounters support optional distance display and distance-based sorting.
- Optional secondary-ID visibility toggles on Devices and Encounters lists.
- Cellular entries labeled with explicit tower context (for example, CELL TOWER (LTE), CELL TOWER (NR)).
- CELL encounter details parse and display tower/radio fields (radio type, operator, IDs, signal fields).
- Optional cell tower location estimation via Mozilla Location Service with range estimate from device in miles/feet.
- Approach detection analytics identify likely inbound devices from recent distance trends.
- Approach detection controls in Settings, including optional local approach notifications.
- Configurable scan interval in Settings, with WorkManager scheduling.

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

## Android Install Guide (Short)

1. On your Android phone, open Settings, then About phone, and tap Build number 7 times to enable Developer options.
2. In Developer options, enable USB debugging.
3. Connect the phone by USB and accept the RSA debug prompt on-device.
4. In Android Studio, select your device and run the app module.
5. If prompted, grant location, Bluetooth, Nearby Wi-Fi devices, and notification permissions so scanning can work.

## Google Maps API Key (Do Not Commit)

The detection map uses Google Maps SDK and needs an API key, but the key should not be hardcoded in source.

Use either of these options:

1. Add this to your local `local.properties` (not tracked by git):
	`MAPS_API_KEY=your_real_key`
2. Or set an environment variable before running Gradle/Android Studio:
	`MAPS_API_KEY=your_real_key`

The manifest reads `${MAPS_API_KEY}` through Gradle manifest placeholders, so no secret key needs to be committed.

## Runtime Permissions

Argusdroid requires runtime grants for key permissions, including location and radio access. Collection results are dependent on user grants, OEM behavior, and Android power policy.

Notable current runtime asks include:

- ACCESS_FINE_LOCATION
- BLUETOOTH_SCAN / BLUETOOTH_CONNECT (API-dependent)
- NEARBY_WIFI_DEVICES (API-dependent)
- READ_PHONE_STATE (for cellular detail access)
- POST_NOTIFICATIONS (for approach and status alerts on supported Android versions)

If a sensor is disabled via Home toggles, that sensor is skipped during scheduled and live scans.

For optional tower geolocation lookup, network access is required and location is best-effort based on external cell-ID databases.

## Data and Privacy Guidance

Before production deployment, implement:

- Clear user consent and transparent policy disclosures.
- Retention limits and user-controlled deletion/export.
- Encryption at rest for sensitive encounter payloads.
- Audit-safe handling for collected telemetry.

## Roadmap

- Expand Remote ID ingestion and decoding for supported sources/feeds.
- Add advanced map tooling (clustering, heatmaps, and time-window playback).
- Add richer device history views and encounter drilldowns.
- Add secure export workflows for encounter datasets.
- Add anomaly detection and scheduled analytics summaries.
- Add optional external SDR integration for expanded RF visibility.

## Documentation

- Architecture: docs/architecture.md
- Capabilities and limits: docs/capabilities-and-limits.md

## License

See LICENSE.
