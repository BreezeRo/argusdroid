package dev.argus.tracker.data

import dev.argus.tracker.data.db.EncounterDao
import dev.argus.tracker.domain.Encounter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomEncounterRepository(
    private val dao: EncounterDao
) : EncounterRepository {
    override suspend fun insertBatch(encounters: List<Encounter>) {
        if (encounters.isEmpty()) return
        dao.insertAll(encounters.map { it.toEntity() })
    }

    override fun observeRecent(limit: Int): Flow<List<Encounter>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Encounter>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

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
