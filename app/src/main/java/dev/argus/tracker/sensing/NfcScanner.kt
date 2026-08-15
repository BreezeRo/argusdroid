package dev.argus.tracker.sensing

import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import dev.argus.tracker.worker.ScanSettings
import org.json.JSONObject

class NfcScanner(
    private val context: android.content.Context
) : SignalScanner {

    override suspend fun scanOnce(): List<Encounter> {
        if (!ScanSettings.isNfcSensorEnabled(context)) return emptyList()

        val observations = NfcTagIngestStore.drain(context)
        if (observations.isEmpty()) return emptyList()

        val location = LocationSnapshotProvider.read(context)

        return observations.mapIndexed { index, payload ->
            buildEncounterFromPayload(
                payload = payload,
                location = location,
                fallbackIndex = index,
                ingestedBy = "NfcScanner"
            )
        }
    }

    companion object {
        fun buildEncounterFromPayload(
            payload: JSONObject,
            location: DetectionLocation?,
            fallbackIndex: Int,
            ingestedBy: String
        ): Encounter {
            val timestampEpochMs = payload.optLong("timestampEpochMs", System.currentTimeMillis())
            val primaryId = payload.optString("primaryId", "")
                .takeIf { it.isNotBlank() }
                ?: payload.optString("tagIdHex", "")
                    .takeIf { it.isNotBlank() }
                ?: "nfc-tag-$fallbackIndex"
            val secondaryId = payload.optString("secondaryId", null)
                ?: payload.optString("action", null)

            return Encounter(
                timestampEpochMs = timestampEpochMs,
                source = EncounterSource.NFC,
                primaryId = primaryId,
                secondaryId = secondaryId,
                rssiDbm = null,
                frequencyMhz = null,
                lat = location?.lat,
                lon = location?.lon,
                rawPayloadJson = JSONObject(payload.toString()).put("ingestedBy", ingestedBy).toString()
            )
        }
    }
}
