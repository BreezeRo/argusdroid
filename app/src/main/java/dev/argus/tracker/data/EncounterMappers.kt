package dev.argus.tracker.data

import dev.argus.tracker.data.db.EncounterEntity
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource

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
    rawPayloadJson = rawPayloadJson
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
    rawPayloadJson = rawPayloadJson
)
