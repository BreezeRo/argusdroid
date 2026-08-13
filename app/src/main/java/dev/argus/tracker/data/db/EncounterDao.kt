package dev.argus.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EncounterDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<EncounterEntity>): List<Long>

    @Query(
        """
        SELECT *
        FROM encounters
        WHERE NOT (source = 'REMOTE_ID' AND primaryId = 'remote-id-unavailable')
        ORDER BY timestampEpochMs DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int): Flow<List<EncounterEntity>>

    @Query(
        """
        SELECT *
        FROM encounters
        WHERE NOT (source = 'REMOTE_ID' AND primaryId = 'remote-id-unavailable')
        ORDER BY timestampEpochMs DESC
        """
    )
    fun observeAll(): Flow<List<EncounterEntity>>

    @Query(
        """
        SELECT *
        FROM encounters
        WHERE NOT (source = 'REMOTE_ID' AND primaryId = 'remote-id-unavailable')
        ORDER BY timestampEpochMs DESC
        LIMIT :limit
        """
    )
    suspend fun listAllForExport(limit: Int): List<EncounterEntity>

    @Query(
        """
        SELECT source, COUNT(*) as count
        FROM encounters
        WHERE timestampEpochMs >= :sinceEpochMs
                    AND NOT (source = 'REMOTE_ID' AND primaryId = 'remote-id-unavailable')
        GROUP BY source
        ORDER BY count DESC
        """
    )
    suspend fun aggregateBySourceSince(sinceEpochMs: Long): List<SourceCountRow>

    @Query(
        """
        SELECT *
        FROM encounters
        WHERE timestampEpochMs >= :sinceEpochMs
            AND NOT (source = 'REMOTE_ID' AND primaryId = 'remote-id-unavailable')
        ORDER BY timestampEpochMs DESC
        LIMIT :limit
        """
    )
    suspend fun listSince(sinceEpochMs: Long, limit: Int): List<EncounterEntity>

    @Query("DELETE FROM encounters")
    suspend fun clearAllEncounters()
}

data class SourceCountRow(
    val source: String,
    val count: Int
)
