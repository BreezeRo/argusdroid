package dev.argus.tracker.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "device_latest_state",
    primaryKeys = ["source", "primaryId"],
    indices = [
        Index(value = ["timestampEpochMs"])
    ]
)
data class DeviceLatestStateEntity(
    val source: String,
    val primaryId: String,
    val timestampEpochMs: Long,
    val secondaryId: String?,
    val rssiDbm: Int?,
    val frequencyMhz: Int?,
    val lat: Double?,
    val lon: Double?,
    val rawPayloadJson: String,
    val encounterFingerprint: String?,
    val provenance: String,
    val provenanceNodeId: String?,
    val provenanceOriginNodeId: String?,
    val provenancePathNodeIds: String?,
    val provenanceReceivedAtEpochMs: Long?,
    val provenanceHopCount: Int
)
