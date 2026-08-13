# Remote ID Ingest Contract

This document defines the current Remote ID ingest API and payload schema used by Argus.

## 1) Normalized schema

Argus writes/reads normalized Remote ID payloads with:

- remoteIdSchema: argus.remote_id.v1
- remoteIdParserVersion: current parser version string
- remoteIdPrimaryId: resolved stable identity (typically UAS ID)
- remoteIdSecondaryId: optional operator identifier or label
- remoteIdDecoded: optional semantic decode object

`remoteIdDecoded` fields (best effort):

- messageType
- uasId
- operatorId
- operatorLat / operatorLon
- droneLat / droneLon
- altitudeMeters
- speedMetersPerSecond
- headingDegrees
- emergencyStatus
- messageTimestampEpochMs
- parseConfidence: NONE, LOW, MEDIUM, HIGH
- parserVersion
- parseNotes

## 2) Feed file path

Argus ingest file path:

- app internal files directory: ingest/remote_id.jsonl

Each line must be valid JSON.

## 3) Companion broadcast ingest

Action:

- dev.argus.tracker.action.INGEST_REMOTE_ID

Receiver permission:

- dev.argus.tracker.permission.INGEST_REMOTE_ID

Intent extras:

- payloadJson: single JSON object string (one sighting)
- payloadsJsonl: newline-delimited JSON strings (batch)
- token: auth token; required when app setting remote_id_ingest_token is configured, or when chain shared passphrase is set

Notes:

- Invalid JSON lines are dropped.
- Feed file rotates after about 5 MB.
- Sender should include a stable UAS ID when available to improve deduplication.

## 4) Minimal example payload

{
  "messageType": "basic_id",
  "uasId": "DRONE-ABC-42",
  "operatorId": "OP-19",
  "timestampEpochMs": 1765700000000,
  "droneLat": 37.422,
  "droneLon": -122.084,
  "altitudeMeters": 51.3,
  "speedMetersPerSecond": 9.1,
  "headingDegrees": 84
}
