package dev.argus.tracker.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceLatestStateDao {
    @Query(
        """
        SELECT *
        FROM device_latest_state
        WHERE NOT (source = 'REMOTE_ID' AND primaryId = 'remote-id-unavailable')
        ORDER BY timestampEpochMs DESC
        """
    )
    fun observeAll(): Flow<List<DeviceLatestStateEntity>>

    @Query(
        """
        SELECT *
        FROM device_latest_state
        WHERE NOT (source = 'REMOTE_ID' AND primaryId = 'remote-id-unavailable')
        ORDER BY timestampEpochMs DESC
        LIMIT :limit
        """
    )
    suspend fun listAll(limit: Int): List<DeviceLatestStateEntity>

    @Query(
        """
        INSERT INTO device_latest_state (
            source,
            primaryId,
            timestampEpochMs,
            secondaryId,
            rssiDbm,
            frequencyMhz,
            lat,
            lon,
            rawPayloadJson,
            encounterFingerprint,
            provenance,
            provenanceNodeId,
            provenanceOriginNodeId,
            provenancePathNodeIds,
            provenanceReceivedAtEpochMs,
            provenanceHopCount
        )
        VALUES (
            :source,
            :primaryId,
            :timestampEpochMs,
            :secondaryId,
            :rssiDbm,
            :frequencyMhz,
            :lat,
            :lon,
            :rawPayloadJson,
            :encounterFingerprint,
            :provenance,
            :provenanceNodeId,
            :provenanceOriginNodeId,
            :provenancePathNodeIds,
            :provenanceReceivedAtEpochMs,
            :provenanceHopCount
        )
        ON CONFLICT(source, primaryId) DO UPDATE SET
            timestampEpochMs = excluded.timestampEpochMs,
            secondaryId = excluded.secondaryId,
            rssiDbm = excluded.rssiDbm,
            frequencyMhz = excluded.frequencyMhz,
            lat = excluded.lat,
            lon = excluded.lon,
            rawPayloadJson = excluded.rawPayloadJson,
            encounterFingerprint = excluded.encounterFingerprint,
            provenance = excluded.provenance,
            provenanceNodeId = excluded.provenanceNodeId,
            provenanceOriginNodeId = excluded.provenanceOriginNodeId,
            provenancePathNodeIds = excluded.provenancePathNodeIds,
            provenanceReceivedAtEpochMs = excluded.provenanceReceivedAtEpochMs,
            provenanceHopCount = excluded.provenanceHopCount
        WHERE excluded.timestampEpochMs >= device_latest_state.timestampEpochMs
        """
    )
    suspend fun upsertLatestState(
        source: String,
        primaryId: String,
        timestampEpochMs: Long,
        secondaryId: String?,
        rssiDbm: Int?,
        frequencyMhz: Int?,
        lat: Double?,
        lon: Double?,
        rawPayloadJson: String,
        encounterFingerprint: String?,
        provenance: String,
        provenanceNodeId: String?,
        provenanceOriginNodeId: String?,
        provenancePathNodeIds: String?,
        provenanceReceivedAtEpochMs: Long?,
        provenanceHopCount: Int
    )

    @Query("DELETE FROM device_latest_state")
    suspend fun clearAll()
}
