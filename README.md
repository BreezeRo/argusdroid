# Argusdroid

Argusdroid is an Android app for local-first RF encounter intelligence. It collects detections from phone radios and optional ingest feeds, normalizes them into a shared encounter model, and surfaces activity through maps, logs, and device workflows.

## What it does

- Scans Wi-Fi, Wi-Fi Direct, Bluetooth LE, Bluetooth Classic, and Cellular.
- Supports direct-channel acoustic and magnetometer sampling.
- Supports ingest feeds for Remote ID, ADS-B aircraft, UWB, and SDR (JSONL-based).
- Normalizes all detections into one encounter schema and stores history in Room.
- Supports Chain Link mesh sync across LAN peers with authenticated exchange and provenance.
- Includes local analytics for approach detection, tracker-risk scoring, and foreign-signal risk.

## App navigation

- Home
- Detection
- Logs
- Settings

### Detection

- Status tab: readiness, source health, and quick operational state.
- Devices tab: triage and filtering of recent/all observed devices.
- Signal tab: channel-health and knowledge-gap diagnostics.
- Map tab with two sub-tabs:
	- Device Map: nearby inferred device locations, live/moving/snapshot filters.
	- Flight Map: aircraft-focused map from ADS-B/public aviation feeds.
- Mesh tab: peer state, link requests, sync controls, and wipe coordination.

### Logs

- Alerts tab
- Errors tab (operational error stream)
- Encounters tab

## Maps and flight tracking

- Source-colored pins with legend filtering.
- Live updates, pin limits, diagnostics panel, and compact control layout.
- Device Map includes aircraft in a personal radius (25/50/75 mi selector).
- Flight Map supports live-radius filtering up to 1000 mi.
- Aircraft pins support heading rotation and helicopter/plane icon hints.
- Moving-device path map is available from moving pins.

## Mesh features

- Authenticated Chain Link with shared passphrase.
- Peer discovery and state tracking (including persistent channel mode).
- Manual link request and sync-now actions.
- Mesh-wide soft reset workflow with notices and temporary scan gate.
- Optional precise location sharing between peers.

## Ingest sources

- Remote ID JSONL: app internal files path ingest/remote_id.jsonl
- ADS-B JSONL: app internal files path ingest/adsb.jsonl
- UWB JSONL: app internal files path ingest/uwb.jsonl
- SDR JSONL: app internal files path ingest/sdr.jsonl
- Public aviation feed: OpenSky-compatible JSON endpoint (location-bounded when available)

See docs/remote-id-ingest.md for payload expectations and companion intent format.

## Quick start

1. Install Android Studio (latest stable).
2. Install Android SDK Platform 36.
3. Open this repository and allow Gradle sync.
4. Connect a physical Android device and run the app module.
5. Grant runtime permissions when prompted.

## Maps API key setup

Google Maps SDK requires MAPS_API_KEY.

Option A (recommended): add this to local.properties

- MAPS_API_KEY=your_real_key

Option B: set MAPS_API_KEY as an environment variable before launching Gradle/Android Studio.

Phone and wear manifests consume MAPS_API_KEY via Gradle manifest placeholders.

## Runtime permissions

- ACCESS_FINE_LOCATION
- READ_PHONE_STATE
- RECORD_AUDIO
- BLUETOOTH_SCAN and BLUETOOTH_CONNECT (API-dependent)
- NEARBY_WIFI_DEVICES (API-dependent)
- POST_NOTIFICATIONS (Android 13+)

If a source is disabled on Home, its scanner is skipped in scheduled and live collection.

## Scheduling

- Under 15 minutes: chained one-time WorkManager requests.
- 15 minutes and above: periodic WorkManager requests.
- Global worker cadence controls scan-batch wakeups.
- Per-source intervals gate individual sources inside those batches.

## Troubleshooting

### Map does not render

- Confirm MAPS_API_KEY is present and not placeholder text.
- Confirm Maps SDK, billing, and key restrictions are valid.
- Confirm Play Services and network availability.
- Enable the map diagnostics panel in Detection map controls.

### No/limited cellular detections

- Confirm READ_PHONE_STATE permission is granted.
- Confirm Cellular sensor toggle is enabled on Home.
- Some OEM/carrier stacks expose limited cell metadata.

### Mesh peers not connecting

- Confirm both devices are on the same LAN and not guest-isolated.
- Confirm Chain Link is enabled on both devices.
- Confirm shared passphrase is identical on all peers.
- Use Mesh tab controls to refresh peers or send link requests.

## Web dashboard

- Path: dashboard/
- Preferred launcher: ./scripts/run-dashboard-node.ps1
- Optional port: ./scripts/run-dashboard-node.ps1 -Port 8090
- Live mesh config: dashboard/config/mesh-config.json

See docs/web-dashboard.md for full setup and key behavior.

## Repository layout

- app: Android application module
- wear: Wear OS companion module
- dashboard: static web dashboard UI
- dashboard-server: Node server for dashboard and live mesh bridge
- docs: architecture and operational docs

## Tech stack

- Kotlin + Jetpack Compose
- Room
- WorkManager
- Coroutines
- Google Maps Compose

## Documentation

- docs/architecture.md
- docs/capabilities-and-limits.md
- docs/remote-id-ingest.md
- docs/web-dashboard.md

## License

See LICENSE.
