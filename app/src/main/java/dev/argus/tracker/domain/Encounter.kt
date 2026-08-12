package dev.argus.tracker.domain

data class Encounter(
    val id: Long = 0,
    val timestampEpochMs: Long,
    val source: EncounterSource,
    val primaryId: String,
    val secondaryId: String?,
    val rssiDbm: Int?,
    val frequencyMhz: Int?,
    val lat: Double?,
    val lon: Double?,
    val rawPayloadJson: String
)
