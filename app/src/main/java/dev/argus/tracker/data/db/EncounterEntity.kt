package dev.argus.tracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "encounters",
    indices = [
        Index(value = ["timestampEpochMs"]),
        Index(value = ["primaryId"]),
        Index(value = ["source"])
    ]
)
data class EncounterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMs: Long,
    val source: String,
    val primaryId: String,
    val secondaryId: String?,
    val rssiDbm: Int?,
    val frequencyMhz: Int?,
    val lat: Double?,
    val lon: Double?,
    val rawPayloadJson: String
)
