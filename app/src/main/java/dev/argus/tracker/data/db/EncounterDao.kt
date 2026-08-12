package dev.argus.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EncounterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EncounterEntity>)

    @Query("SELECT * FROM encounters ORDER BY timestampEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EncounterEntity>>

    @Query(
        """
        SELECT source, COUNT(*) as count
        FROM encounters
        WHERE timestampEpochMs >= :sinceEpochMs
        GROUP BY source
        ORDER BY count DESC
        """
    )
    suspend fun aggregateBySourceSince(sinceEpochMs: Long): List<SourceCountRow>
}

data class SourceCountRow(
    val source: String,
    val count: Int
)
