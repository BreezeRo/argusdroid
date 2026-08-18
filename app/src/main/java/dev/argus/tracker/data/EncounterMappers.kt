package dev.argus.tracker.data

import dev.argus.tracker.data.db.EncounterEntity
import dev.argus.tracker.data.db.DeviceLatestStateEntity
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterProvenance
import dev.argus.tracker.domain.EncounterSource
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject

fun Encounter.toEntity(): EncounterEntity = EncounterEntity(
    id = id,
    timestampEpochMs = timestampEpochMs,
    source = source.name,
    primaryId = primaryId,
    secondaryId = secondaryId,
    rssiDbm = rssiDbm,
    frequencyMhz = frequencyMhz,
    lat = lat,
    lon = lon,
    rawPayloadJson = rawPayloadJson,
    encounterFingerprint = encounterFingerprint ?: computeEncounterFingerprint(this),
    provenance = provenance.name,
    provenanceNodeId = provenanceNodeId,
    provenanceOriginNodeId = provenanceOriginNodeId,
    provenancePathNodeIds = provenancePathNodeIds,
    provenanceReceivedAtEpochMs = provenanceReceivedAtEpochMs,
    provenanceHopCount = provenanceHopCount
)

fun EncounterEntity.toDomain(): Encounter = Encounter(
    id = id,
    timestampEpochMs = timestampEpochMs,
    source = runCatching { EncounterSource.valueOf(source) }.getOrDefault(EncounterSource.UNKNOWN_RF),
    primaryId = primaryId,
    secondaryId = secondaryId,
    rssiDbm = rssiDbm,
    frequencyMhz = frequencyMhz,
    lat = lat,
    lon = lon,
    rawPayloadJson = rawPayloadJson,
    encounterFingerprint = encounterFingerprint,
    provenance = runCatching { EncounterProvenance.valueOf(provenance) }
        .getOrDefault(EncounterProvenance.LOCAL),
    provenanceNodeId = provenanceNodeId,
    provenanceOriginNodeId = provenanceOriginNodeId,
    provenancePathNodeIds = provenancePathNodeIds,
    provenanceReceivedAtEpochMs = provenanceReceivedAtEpochMs,
    provenanceHopCount = provenanceHopCount
)

fun Encounter.toLatestStateEntity(): DeviceLatestStateEntity = DeviceLatestStateEntity(
    source = source.name,
    primaryId = primaryId,
    timestampEpochMs = timestampEpochMs,
    secondaryId = secondaryId,
    rssiDbm = rssiDbm,
    frequencyMhz = frequencyMhz,
    lat = lat,
    lon = lon,
    rawPayloadJson = rawPayloadJson,
    encounterFingerprint = encounterFingerprint ?: computeEncounterFingerprint(this),
    provenance = provenance.name,
    provenanceNodeId = provenanceNodeId,
    provenanceOriginNodeId = provenanceOriginNodeId,
    provenancePathNodeIds = provenancePathNodeIds,
    provenanceReceivedAtEpochMs = provenanceReceivedAtEpochMs,
    provenanceHopCount = provenanceHopCount
)

fun DeviceLatestStateEntity.toDomain(): Encounter = Encounter(
    timestampEpochMs = timestampEpochMs,
    source = runCatching { EncounterSource.valueOf(source) }.getOrDefault(EncounterSource.UNKNOWN_RF),
    primaryId = primaryId,
    secondaryId = secondaryId,
    rssiDbm = rssiDbm,
    frequencyMhz = frequencyMhz,
    lat = lat,
    lon = lon,
    rawPayloadJson = rawPayloadJson,
    encounterFingerprint = encounterFingerprint,
    provenance = runCatching { EncounterProvenance.valueOf(provenance) }
        .getOrDefault(EncounterProvenance.LOCAL),
    provenanceNodeId = provenanceNodeId,
    provenanceOriginNodeId = provenanceOriginNodeId,
    provenancePathNodeIds = provenancePathNodeIds,
    provenanceReceivedAtEpochMs = provenanceReceivedAtEpochMs,
    provenanceHopCount = provenanceHopCount
)

fun computeEncounterFingerprint(encounter: Encounter): String {
    val normalizedPayload = encounter.rawPayloadJson.trim()
    val remoteIdIdentityKey = if (encounter.source == EncounterSource.REMOTE_ID) {
        buildRemoteIdIdentityKey(encounter, normalizedPayload)
    } else {
        null
    }

    val normalized = listOf(
        encounter.source.name,
        remoteIdIdentityKey ?: encounter.timestampEpochMs.toString(),
        encounter.primaryId,
        encounter.secondaryId.orEmpty(),
        encounter.rssiDbm?.toString().orEmpty(),
        encounter.frequencyMhz?.toString().orEmpty(),
        encounter.lat?.let { String.format(Locale.US, "%.6f", it) }.orEmpty(),
        encounter.lon?.let { String.format(Locale.US, "%.6f", it) }.orEmpty(),
        normalizedPayload
    ).joinToString("|")
    val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { "%02x".format(it) }
}

private fun buildRemoteIdIdentityKey(encounter: Encounter, normalizedPayload: String): String? {
    val payload = runCatching { JSONObject(normalizedPayload) }.getOrNull() ?: return null

    val decoded = payload.optJSONObject("remoteIdDecoded")
    val uasId = decoded?.optString("uasId", "")?.trim().orEmpty()
        .ifBlank { payload.optString("remoteIdPrimaryId", "").trim() }
        .ifBlank { encounter.primaryId.trim() }
    if (uasId.isBlank()) return null

    val ts = decoded?.optLong("messageTimestampEpochMs")
        ?.takeIf { it > 0L }
        ?: payload.optLong("timestampEpochMs")
            .takeIf { it > 0L }
        ?: encounter.timestampEpochMs

    return "rid:$uasId:$ts"
}
