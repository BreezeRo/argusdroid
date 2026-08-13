# Argusdroid

Argusdroid is an Android app for on-device radio encounter intelligence. It collects detections from supported phone radios, normalizes them into a shared encounter model, stores them locally, and surfaces trends through map and list workflows.

## Why this app exists

- Turn raw RF-adjacent phone telemetry into usable operational context.
- Keep the default pipeline local-first and device-resident.
- Provide a foundation for future advanced ingest sources (OEM SDKs, SDR, external decoders).

## Current feature set

### Core collection and storage

- Multi-source sensing for Wi-Fi, Wi-Fi Direct peers, Bluetooth LE, Bluetooth Classic, and Cellular.
- BLE heuristics now classify likely device classes (for example tracker-tag, wearable, audio, sensor) and can promote likely Remote ID BLE signatures.
- Encounter normalization into a shared model with timestamp, IDs, signal metadata, optional location, and raw payload JSON.
- Room-backed local persistence for historical analysis.
- Configurable scan interval with WorkManager-based scheduling.
- Optional Chain Linking across devices on the same LAN, including deduplicated peer synchronization.
- Encounter provenance tracking for local vs chain-linked observations.
- Secure chain-link authentication using a shared passphrase across participating devices.
- Configurable chain auto-sync interval for periodic peer data pulls while chain linking is enabled.
- Optional persistent channel heartbeat mode for near-live peer connectivity state when enabled on both devices.

### Home and readiness

- Tracking controls: start, stop, refresh, and last scan visibility.
- Tracking status now shows both last scan-cycle total duration and per-source last durations.
- Home warning cards are freshness-aware and per-source (current overruns vs previous overruns).
- Sensor-level gating (Wi-Fi, Bluetooth LE, Cellular, Remote ID) persisted in app settings and enforced in scanners.
- Readiness advisor with deep links to system settings for missing prerequisites.
- Clear encounters and clear devices actions for rapid reset.

### Detection and mapping

- Detection tabs:
	- Readiness
	- Device Encounters Map
	- Device Location Map
	- Alert Logs
	- Mesh Network
- Device Encounters Map shows direct encounter points.
- Device Location Map shows best-effort approximate device locations:
	- Cellular: tower lookup estimate with observed-location fallback.
	- Wi-Fi and BLE: multi-observation range inference with observed-location fallback.
	- Other sources: best observed encounter location.
- Source-colored map pins and an in-app pin color legend.
- Map controls:
	- Live map updates are active on Device Location Map (default ON there)
	- Live map update interval selection (1s through 1h)
	- Pin limit selection (default 1000)
	- Optional diagnostics panel (default OFF)
	- Compact LIVE badge in header when live updates are enabled
- Device Location Map supports a Moving Only filter toggle.
- Tapping a moving device pin opens a dedicated single-device map with that device's historical path.
- Device-location pin info windows use compact single-line summaries to reduce clipping on smaller displays.

### Mesh Network tab

- Chain linking controls were moved from Settings into Detection > Mesh Network.
- Peer operations include refresh, manual link requests, sync now, and peer-state inspection.
- Mesh visualizer now overlays on Google Maps and shows peer state with link lines.
- Mesh supports optional accurate location sharing between linked peers:
	- New toggle: Share Precise Location
	- Shared coordinates are included in peer hello/status payloads when enabled.
	- Mesh map uses shared precise peer coordinates when available and relative placement fallback for peers that do not share location.
- Foreground mesh service keeps persistent channel behavior active more reliably while backgrounded when chain linking and persistent channel are both enabled.

### Additional source hooks

- Remote ID ingest hook is active via JSONL feed file: app internal files path ingest/remote_id.jsonl.
- UWB ingest hook is active via JSONL feed file: app internal files path ingest/uwb.jsonl.
- SDR ingest hook is active via JSONL feed file: app internal files path ingest/sdr.jsonl.
- Each JSON line should be a single object containing at minimum an id (or primaryId) and optional fields such as timestampEpochMs, label, rssiDbm, frequencyMhz, lat, and lon.

### Devices and encounters workflows

- Unified Devices and Encounters page with tabs replaces separate top-level menu items.
- Scope filters (Recent 100 or All).
- Source and text filtering.
- Optional secondary ID visibility.
- Optional distance display.
- Optional distance-based sorting.
- Device list sorting by last seen or most seen.
- Chain-linked detections are clearly marked in map pins, list cards, and detail pages with peer attribution.
- Chain settings now support manual Sync Now and background auto-sync.
- Chain settings include peer refresh, link requests, connected vs unconnected peer counts, and a mesh visualizer.
- Chain peers can be assigned human-readable device names, propagated across the mesh.

### Scan interval tuning and telemetry

- Global scan interval supports fast options including 1s and 3s.
- Per-source scan intervals are configurable independently (Wi-Fi, BLE, Cellular, Remote ID).
- Per-source timing telemetry tracks last, avg, p50, p95, and max durations.
- Auto-adjust mode can increase/decrease source intervals based on overrun/stability behavior.
- Settings include a recent auto-adjust/manual interval change activity log.

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
- Scheduler alignment may set the global scheduler tick to the fastest enabled source interval when per-source intervals are used.

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

### Chain peers remain 0/0

- Confirm both devices are on the same Wi-Fi LAN/subnet and not on a guest-isolated network.
- Confirm Chain Linking is enabled on both devices.
- Confirm both devices use the exact same Chain Shared Passphrase.
- Use Settings > Refresh Peers to force discovery.
- If using persistent channel, enable it on both devices and verify heartbeat interval is set.
- In Mesh Network, confirm the foreground mesh chip shows active when persistent channel is enabled.
- Try sending a Link Request using peer host IP from one device to the other.

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
