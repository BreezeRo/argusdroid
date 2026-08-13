package dev.argus.tracker.domain

enum class EncounterProvenance {
    LOCAL,
    CHAIN_LINKED
}

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
    val rawPayloadJson: String,
    val encounterFingerprint: String? = null,
    val provenance: EncounterProvenance = EncounterProvenance.LOCAL,
    val provenanceNodeId: String? = null,
    val provenanceOriginNodeId: String? = null,
    val provenancePathNodeIds: String? = null,
    val provenanceReceivedAtEpochMs: Long? = null,
    val provenanceHopCount: Int = 0
)
