# Argus Android Architecture

Argus is designed as an on-device signal encounter pipeline:

1. Sensor adapters pull observations from available Android radios and APIs.
2. Observations are normalized into a shared `Encounter` model.
3. Encounters are persisted in a Room database for long-term historical analysis.
4. WorkManager schedules periodic collection jobs.
5. UI surfaces summary counts and recent encounter activity.

## Layers

- `domain/`: Core model and scanner contracts.
- `sensing/`: Source-specific scanners (Wi-Fi, BLE, Remote ID hook).
- `data/db/`: Room entities, DAO, and database.
- `data/`: Repository and dependency container.
- `worker/`: Background scheduling and periodic collection worker.
- `ui/`: Compose dashboard and view model.

## Data Model

An encounter records:

- Source type (`WIFI`, `BLUETOOTH_LE`, `REMOTE_ID`, etc.)
- Timestamp
- Primary and secondary identifiers
- Signal metadata (RSSI, frequency)
- Optional location
- Raw payload JSON for later feature extraction

## Scheduling Model

Current implementation uses a periodic WorkManager task with 15-minute cadence. For true high-frequency active scans, move sensing into a foreground service with user-visible notification and battery-aware constraints.

## Extensibility Points

- Replace placeholder BLE behavior with callback-driven scan sessions.
- Add cell-network scanner (TelephonyManager + permission strategy).
- Add external SDR ingestion module (USB OTG) for broader RF spectrum coverage.
- Add OEM/device specific Remote ID integrations where available.
- Add encrypted at-rest storage, export tooling, and anomaly detection jobs.
