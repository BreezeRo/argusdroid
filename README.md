# Argusdroid

Argusdroid is a local-first Android RF and telemetry intelligence system. It ingests multi-source detections, normalizes them into a single encounter model, and provides on-device analytics, map visualization, and authenticated LAN mesh synchronization.

## System Scope

- Platform: Android app module plus Wear companion.
- Storage model: Room-backed encounter timeline with shared schema across sources.
- Processing model: source scanners plus ingest readers feed a unified encounter pipeline.
- Sync model: authenticated peer-to-peer Chain Link mesh over LAN with provenance tagging.

## Core Detection Sources

- Wi-Fi and Wi-Fi sweep aggregate mode.
- Wi-Fi Direct.
- Bluetooth LE and Bluetooth LE sweep aggregate mode.
- Bluetooth Classic.
- Cellular.
- NFC ingest path (intent-driven ingest + scanner integration).
- Aircraft telemetry (ADS-B/public feed).
- Remote ID.
- UWB ingest.
- SDR ingest.
- Direct acoustic channel.
- Direct magnetometer channel.
- Camera events via SDR-style camera payloads and public camera POI fallback.

## Encounter Pipeline

- Canonical model: timestamp, source, IDs, radio metadata, optional location, raw payload, and provenance metadata.
- Provenance tracks local versus chain-linked origin and relay path metadata.
- Source intervals are managed per source; global worker cadence aligns to fastest enabled source interval.
- Work scheduling uses chained one-time work below 15 minutes and periodic work at or above 15 minutes.
- App startup enqueues a bootstrap one-time scan so first-run state is populated before runtime release.

## Analytics and Alerting

- Approach detection with confidence and trend outputs.
- Tracker suspicion scoring for repeated co-movement behavior.
- Flock graph analysis for repeated co-travel correlation across trusted moving sources.
- Background flock alert monitor with persisted signature/cooldown state to avoid alert storms.
- Foreign signal risk scoring across channel indicators with configurable threshold.
- Signal Intel includes direct acoustic and direct magnetometer diagnostics.
- NFC alert surfacing for newly observed NFC encounters.
- Magnetometer disturbance increase alerts.
- Application-level uncaught-exception capture into operational error logs.
- Aircraft no-fly zone pass-through detection:
	- Detects aircraft transitions from outside to inside no-fly polygons.
	- Emits dedicated notification channel alerts with cooldown guards.
	- Writes structured alert log entries for event auditability.

## Map Engine

- Detection map supports device and aircraft-focused sub-maps.
- Source-colored pins with filtering, live controls, diagnostics, and pin budget controls.
- Aircraft map includes heading-aware markers and large-radius filtering.
- No-fly zone overlays:
	- Local ingest: ingest/no_fly_zones.geojson.
	- Public fallback: FAA ArcGIS UAS Facility Map and National Security UAS restrictions.
	- Location-bounded fetch and on-device cache reuse.
	- Render-quality controls with adaptive polygon simplification and marker limits.
	- Overlay visibility and diagnostics integrated into map controls.

## Mesh (Chain Link)

- Shared-secret authenticated sync payloads.
- Peer discovery, connectivity state, and optional persistent channel behavior.
- Manual peer refresh and sync-now workflows.
- Mesh wipe lifecycle controls with notification surfacing.
- Optional precise-location sharing policy.

## UI Surfaces

- Home: sensor gates and operational controls.
- Detection: readiness, devices, flocks, signal diagnostics, maps, and mesh operations.
- Logs: alerts, operational errors, and encounter timeline.
- Settings: scheduling, detection gates, alert policies, map behavior, and data reset/backup workflows.

## Ingest Interfaces

- Remote ID JSONL: ingest/remote_id.jsonl
- ADS-B JSONL: ingest/adsb.jsonl
- UWB JSONL: ingest/uwb.jsonl
- SDR JSONL: ingest/sdr.jsonl
- No-fly overlay GeoJSON: ingest/no_fly_zones.geojson
- Public aircraft feed: OpenSky-compatible endpoint (configured in settings)

See docs/remote-id-ingest.md for payload expectations and companion ingest contract.

## Build and Run

1. Install Android Studio and Android SDK Platform 36.
2. Open repository and allow Gradle sync.
3. Configure MAPS_API_KEY in local.properties or environment.
4. Connect a physical device and run the app module.
5. Grant runtime permissions requested by enabled sources.

MAPS_API_KEY is consumed via Gradle manifest placeholders by phone and wear modules.

## Runtime Permissions

- ACCESS_FINE_LOCATION
- READ_PHONE_STATE
- RECORD_AUDIO
- BLUETOOTH_SCAN and BLUETOOTH_CONNECT (API-level dependent)
- NEARBY_WIFI_DEVICES (API-level dependent)
- POST_NOTIFICATIONS (Android 13+)

Disabled source gates prevent that source from participating in both scheduled and live pipelines.

## Operational Notes

- Scanner noise controls support aggregate-only sweeps and one-off randomized ID suppression.
- Operational logs and source timing diagnostics are first-class in settings and logs surfaces.
- Summary metrics include all source types across the 24h reporting window.

## Today’s Delivered Changes (2026-08-15)

- Detection tab topology refactor:
	- Added a dedicated Flocks tab in Detection and moved flock analytics out of Devices.
	- Reindexed Detection tab routing (Mesh index shift) to preserve deep-link behavior.
- Flock intelligence pipeline:
	- Added a standalone flock analyzer module with connected-component grouping over co-travel links.
	- Hardened false-positive controls by restricting flock location evidence to trusted moving-source coordinates.
	- Added per-member movement proof gates before pair linkage and span qualification.
- Flock alerting and runtime integration:
	- Added Settings-gated flock notification policy and dedicated notification channel.
	- Added foreground flock alert emission with signature/cooldown dedup.
	- Added worker-driven background flock monitoring with throttled analysis cadence and persisted dedup state.
	- Added worker-side operational error logging for flock monitor failures.
- Operational fault instrumentation:
	- Added application-level default uncaught-exception handler to persist crash context in operational error logs.
- Cellular and BLE telemetry enrichment:
	- Upgraded cellular secondary labels to operator-aware "<Operator> Cell (<Radio> tower)" identities.
	- Expanded BLE payload classification with tracker-family heuristics and manufacturer/service UUID evidence.
	- Hardened BLE scan callback handling for resilient result aggregation.
- Detection-map runtime performance hardening:
	- Moved heavy list/map analysis workloads off the main thread.
	- Isolated radial sweep rendering from heavyweight recomposition paths.
	- Added additional null-safety and map rendering guardrails in compose pipelines.
- Startup/interval heuristics:
	- Added home-point-aware heuristics and conditional startup bootstrap controls.
	- Maintained source-cadence alignment behavior across worker and UI-scheduler control paths.
- Repository/platform scope simplification:
	- Removed dashboard static UI, Node bridge, and dashboard scripts from the repo.
	- Updated docs and wear bridge payload behavior to remove dashboard URL dependencies.

## Repository Layout

- app: Android application module
- wear: Wear OS companion module
- docs: architecture and operational documentation

## Stack

- Kotlin
- Jetpack Compose
- Room
- WorkManager
- Coroutines
- Google Maps Compose

## Additional Documentation

- docs/architecture.md
- docs/capabilities-and-limits.md
- docs/remote-id-ingest.md

## License

See LICENSE.
