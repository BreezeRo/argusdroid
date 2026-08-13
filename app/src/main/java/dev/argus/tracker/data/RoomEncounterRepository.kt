package dev.argus.tracker.data

import dev.argus.tracker.data.db.EncounterDao
import dev.argus.tracker.domain.Encounter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomEncounterRepository(
    private val dao: EncounterDao
) : EncounterRepository {
    override suspend fun insertBatch(encounters: List<Encounter>): Int {
        if (encounters.isEmpty()) return 0
        val results = dao.insertAll(encounters.map { it.toEntity() })
        return results.count { it != -1L }
    }

    override fun observeRecent(limit: Int): Flow<List<Encounter>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Encounter>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun listAll(limit: Int): List<Encounter> =
        dao.listAllForExport(limit).map { it.toDomain() }

    override suspend fun listSince(sinceEpochMs: Long, limit: Int): List<Encounter> =
        dao.listSince(sinceEpochMs, limit).map { it.toDomain() }

    override suspend fun sourceSummarySince(sinceEpochMs: Long): Map<String, Int> =
        dao.aggregateBySourceSince(sinceEpochMs).associate { it.source to it.count }

    override suspend fun clearEncounters() {
        dao.clearAllEncounters()
    }

    override suspend fun clearDevices() {
        // Devices are currently inferred from encounter history, so this clears device-derived rows.
        dao.clearAllEncounters()
    }
}
