package dev.argus.tracker.data

import dev.argus.tracker.data.db.DeviceLatestStateDao
import dev.argus.tracker.data.db.EncounterDao
import dev.argus.tracker.domain.Encounter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomEncounterRepository(
    private val dao: EncounterDao,
    private val deviceLatestStateDao: DeviceLatestStateDao
) : EncounterRepository {
    override suspend fun insertBatch(encounters: List<Encounter>): Int {
        if (encounters.isEmpty()) return 0
        val normalized = encounters.map { encounter ->
            val fingerprint = encounter.encounterFingerprint ?: computeEncounterFingerprint(encounter)
            encounter.copy(encounterFingerprint = fingerprint)
        }
        val results = dao.insertAll(normalized.map { it.toEntity() })
        normalized.forEach { encounter ->
            val latest = encounter.toLatestStateEntity()
            deviceLatestStateDao.upsertLatestState(
                source = latest.source,
                primaryId = latest.primaryId,
                timestampEpochMs = latest.timestampEpochMs,
                secondaryId = latest.secondaryId,
                rssiDbm = latest.rssiDbm,
                frequencyMhz = latest.frequencyMhz,
                lat = latest.lat,
                lon = latest.lon,
                rawPayloadJson = latest.rawPayloadJson,
                encounterFingerprint = latest.encounterFingerprint,
                provenance = latest.provenance,
                provenanceNodeId = latest.provenanceNodeId,
                provenanceOriginNodeId = latest.provenanceOriginNodeId,
                provenancePathNodeIds = latest.provenancePathNodeIds,
                provenanceReceivedAtEpochMs = latest.provenanceReceivedAtEpochMs,
                provenanceHopCount = latest.provenanceHopCount
            )
        }
        return results.count { it != -1L }
    }

    override fun observeRecent(limit: Int): Flow<List<Encounter>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Encounter>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeLatestByDevice(): Flow<List<Encounter>> =
        deviceLatestStateDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun listAll(limit: Int): List<Encounter> =
        dao.listAllForExport(limit).map { it.toDomain() }

    override suspend fun listLatestByDevice(limit: Int): List<Encounter> =
        deviceLatestStateDao.listAll(limit).map { it.toDomain() }

    override suspend fun listSince(sinceEpochMs: Long, limit: Int): List<Encounter> =
        dao.listSince(sinceEpochMs, limit).map { it.toDomain() }

    override suspend fun sourceSummarySince(sinceEpochMs: Long): Map<String, Int> =
        dao.aggregateBySourceSince(sinceEpochMs).associate { it.source to it.count }

    override suspend fun distinctPrimaryCountSince(source: String, sinceEpochMs: Long): Int =
        dao.countDistinctPrimaryIdsSince(source = source, sinceEpochMs = sinceEpochMs)

    override suspend fun clearEncounters() {
        dao.clearAllEncounters()
        deviceLatestStateDao.clearAll()
    }

    override suspend fun clearDevices() {
        // Devices are currently inferred from encounter history, so this clears device-derived rows.
        dao.clearAllEncounters()
        deviceLatestStateDao.clearAll()
    }
}
