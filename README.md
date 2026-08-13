# Argusdroid

Argusdroid is an Android app for on-device radio encounter intelligence. It collects detections from supported phone radios, normalizes them into a shared encounter model, stores them locally, and surfaces trends through map and list workflows.

## Why this app exists

- Turn raw RF-adjacent phone telemetry into usable operational context.
- Keep the default pipeline local-first and device-resident.
- Provide a foundation for future advanced ingest sources (OEM SDKs, SDR, external decoders).

## Current feature set

### Core collection and storage

- Multi-source sensing for Wi-Fi, Bluetooth LE, and Cellular.
- Encounter normalization into a shared model with timestamp, IDs, signal metadata, optional location, and raw payload JSON.
- Room-backed local persistence for historical analysis.
- Configurable scan interval with WorkManager-based scheduling.

### Home and readiness

- Tracking controls: start, stop, refresh, and last scan visibility.
- Sensor-level gating (Wi-Fi, Bluetooth LE, Cellular, Remote ID) persisted in app settings and enforced in scanners.
- Readiness advisor with deep links to system settings for missing prerequisites.
- Clear encounters and clear devices actions for rapid reset.

### Detection and mapping

- Detection tabs:
	- Readiness
	- Device Encounters Map
	- Device Location Map
- Device Encounters Map shows direct encounter points.
- Device Location Map shows best-effort approximate device locations:
	- Cellular: tower lookup estimate with observed-location fallback.
	- Wi-Fi and BLE: multi-observation range inference with observed-location fallback.
	- Other sources: best observed encounter location.
- Source-colored map pins and an in-app pin color legend.
- Map controls:
	- Live map updates (default ON)
	- Pin limit selection (default 1000)
	- Optional diagnostics panel (default OFF)
	- Compact LIVE badge in header when live updates are enabled

### Devices and encounters workflows

- Scope filters (Recent 100 or All).
- Source and text filtering.
- Optional secondary ID visibility.
- Optional distance display.
- Optional distance-based sorting.
- Device list sorting by last seen or most seen.

### Cellular enrichment

- Cellular entries labeled as tower context (for example CELL TOWER LTE/NR when available).
- Parsed cellular detail fields in detail pages (radio type, operator, IDs, signal fields).
- Optional tower geolocation lookup through Mozilla Location Service and range-from-device display.

### Approach detection and alerts

- Approach analytics estimate whether a device is moving closer using recent distance trend analysis.
- Devices can be explicitly marked as owned ("My Device") in device details.
- Co-movement analysis scores devices that repeatedly appear across distinct locations while you move.
- Tracker risk levels (Low, Medium, High) are shown for non-owned devices based on spread, repeat presence, and time window.
- Devices page supports ownership and tracker-risk filtering for rapid triage.
- Settings controls:
	- Enable approach detection
	- Enable approach notifications
	- Enable tracker suspicion alerts
- Local notifications fire on transition into approaching state with per-device cooldown to reduce alert spam.
- Local tracker notifications fire when an unknown device transitions into high tracker-risk state.

## Known limits and truth-in-advertising

- Android does not expose universal arbitrary RF spectrum scanning APIs.
- Remote ID scanner is currently a hook point and returns no encounters by default.
- Cellular detail richness and update rate are device/OEM/version dependent.
- Background behavior is constrained by power policy, Doze, and OEM optimizations.
- Distance and approach estimates are best-effort due to RSSI variability and environment effects.

## Quick start

1. Install Android Studio (latest stable).
2. Install Android SDK Platform 36.
3. Open this repository.
4. Let Gradle sync complete.
5. Connect a physical Android device.
6. Run the app module.
7. Grant requested runtime permissions when prompted.

## Android device setup

1. Enable Developer options (tap Build number 7 times).
2. Enable USB debugging.
3. Connect phone by USB and accept RSA prompt.
4. Select device in Android Studio and run.

## Maps API key setup (do not commit secrets)

Google Maps SDK requires a key.

Option A: set local property (recommended)

- Add to local.properties:
	- MAPS_API_KEY=your_real_key

Option B: environment variable

- Set MAPS_API_KEY in your shell/session before launching Gradle/Android Studio.

The app manifest consumes MAPS_API_KEY via Gradle manifest placeholders.

## Runtime permissions

Current runtime asks include:

- ACCESS_FINE_LOCATION
- READ_PHONE_STATE
- BLUETOOTH_SCAN and BLUETOOTH_CONNECT (API-dependent)
- NEARBY_WIFI_DEVICES (API-dependent)
- POST_NOTIFICATIONS (Android 13+ for approach/status alerts)

If a sensor is disabled from Home, that scanner is skipped in both scheduled and live collection.

## Scheduling model

- Under 15 minutes: chained one-time work requests.
- 15 minutes and above: periodic WorkManager requests.

This behavior is intentional to support short intervals while still supporting periodic scheduling for longer cadences.

## Troubleshooting

### Map does not render

- Confirm MAPS_API_KEY is present and not placeholder text.
- Confirm Maps SDK + billing/API restrictions are correct in Google Cloud.
- Confirm network connectivity and Play Services availability.
- Turn on map diagnostics panel in Detection map controls.

### Cellular detections are missing or sparse

- Confirm READ_PHONE_STATE is granted.
- Confirm Cellular sensor toggle is enabled on Home.
- Check readiness items for missing prerequisites.
- Note that some devices/carriers expose limited cell metadata.

### No approach notifications

- Confirm both settings are enabled:
	- Approach detection
	- Approach notifications
- Confirm POST_NOTIFICATIONS permission is granted (Android 13+).
- Confirm detections provide enough recent samples to classify an approach trend.

### Tracker suspicion alerts are not appearing

- Confirm "Enable approach detection" is ON in Settings.
- Confirm "Tracker suspicion alerts" is ON in Settings.
- Confirm target device is not marked as owned in Device Details.
- Confirm detections include diverse locations over time; static single-location data will not reach high tracker risk.

## Repository layout

- app: Android application module
- docs/architecture.md: architecture and data flow
- docs/capabilities-and-limits.md: practical platform constraints
- src: reserved for shared or non-Android code
- tests: repository-level test assets

## Tech stack

- Kotlin 2.x
- Android Gradle Plugin 8.x
- Jetpack Compose
- Room
- WorkManager
- Coroutines

## Security and privacy notes

Before production deployment, implement:

- Explicit user consent and policy disclosures.
- Retention and deletion controls.
- Encryption at rest for sensitive payloads.
- Auditable telemetry handling.

## Documentation

- Architecture: docs/architecture.md
- Capabilities and limits: docs/capabilities-and-limits.md

## License

See LICENSE.
