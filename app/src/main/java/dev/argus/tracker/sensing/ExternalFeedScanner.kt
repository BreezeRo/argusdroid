package dev.argus.tracker.sensing

import android.content.Context
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import dev.argus.tracker.sensing.remoteid.RemoteIdPayloadParser
import dev.argus.tracker.worker.ScanSettings
import org.json.JSONObject

class ExternalFeedScanner(
    private val context: Context,
    val source: EncounterSource,
    private val feedName: String
) : SignalScanner {

    val sourceTypeKey: String = source.name.lowercase()

    override suspend fun scanOnce(): List<Encounter> {
        if (!isSourceEnabled()) return emptyList()
        val location = LocationSnapshotProvider.read(context)
        val feedFile = context.filesDir.resolve("ingest").resolve("$feedName.jsonl")
        if (!feedFile.exists() || !feedFile.isFile) return emptyList()

        val lines = runCatching { feedFile.readLines() }.getOrDefault(emptyList())
        if (lines.isEmpty()) return emptyList()

        return lines.mapNotNull { line ->
            val payload = runCatching { JSONObject(line) }.getOrNull() ?: return@mapNotNull null
            val normalizedRemoteId = if (source == EncounterSource.REMOTE_ID) {
                RemoteIdPayloadParser.normalizeIncomingPayload(
                    input = payload,
                    fallbackTimestampEpochMs = System.currentTimeMillis(),
                    fallbackPrimaryId = "external-${source.name.lowercase()}"
                )
            } else {
                null
            }

            val primaryId = normalizedRemoteId?.primaryId
                ?: payload.optString("id", "")
                    .ifBlank { payload.optString("primaryId", "") }
                    .ifBlank { "external-${source.name.lowercase()}" }

            val secondaryId = normalizedRemoteId?.secondaryId
                ?: payload.optString("label", null)

            val timestampEpochMs = normalizedRemoteId?.timestampEpochMs
                ?: payload.optLong("timestampEpochMs", System.currentTimeMillis())

            val normalizedPayloadJson = normalizedRemoteId?.normalizedPayloadJson ?: payload.toString()

            Encounter(
                timestampEpochMs = timestampEpochMs,
                source = source,
                primaryId = primaryId,
                secondaryId = secondaryId,
                rssiDbm = payload.optInt("rssiDbm").takeIf { payload.has("rssiDbm") },
                frequencyMhz = payload.optInt("frequencyMhz").takeIf { payload.has("frequencyMhz") },
                lat = payload.optDouble("lat").takeIf { payload.has("lat") } ?: location?.lat,
                lon = payload.optDouble("lon").takeIf { payload.has("lon") } ?: location?.lon,
                rawPayloadJson = normalizedPayloadJson
            )
        }
    }

    private fun isSourceEnabled(): Boolean = when (source) {
        EncounterSource.UWB -> ScanSettings.isUwbSensorEnabled(context)
        EncounterSource.SDR -> ScanSettings.isSdrSensorEnabled(context)
        else -> true
    }
}
