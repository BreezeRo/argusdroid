package dev.argus.tracker.data

import dev.argus.tracker.domain.Encounter
import kotlinx.coroutines.flow.Flow

interface EncounterRepository {
    suspend fun insertBatch(encounters: List<Encounter>): Int
    fun observeRecent(limit: Int): Flow<List<Encounter>>
    fun observeAll(): Flow<List<Encounter>>
    fun observeLatestByDevice(): Flow<List<Encounter>>
    suspend fun listAll(limit: Int): List<Encounter>
    suspend fun listLatestByDevice(limit: Int): List<Encounter>
    suspend fun listSince(sinceEpochMs: Long, limit: Int): List<Encounter>
    suspend fun sourceSummarySince(sinceEpochMs: Long): Map<String, Int>
    suspend fun distinctPrimaryCountSince(source: String, sinceEpochMs: Long): Int
    suspend fun clearEncounters()
    suspend fun clearDevices()
}
