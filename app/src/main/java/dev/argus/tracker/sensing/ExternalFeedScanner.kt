package dev.argus.tracker.sensing

import android.content.Context
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import org.json.JSONObject

class ExternalFeedScanner(
    private val context: Context,
    val source: EncounterSource,
    private val feedName: String
) : SignalScanner {

    val sourceTypeKey: String = source.name.lowercase()

    override suspend fun scanOnce(): List<Encounter> {
        val location = LocationSnapshotProvider.read(context)
        val feedFile = context.filesDir.resolve("ingest").resolve("$feedName.jsonl")
        if (!feedFile.exists() || !feedFile.isFile) return emptyList()

        val lines = runCatching { feedFile.readLines() }.getOrDefault(emptyList())
        if (lines.isEmpty()) return emptyList()

        return lines.mapNotNull { line ->
            val payload = runCatching { JSONObject(line) }.getOrNull() ?: return@mapNotNull null
            val primaryId = payload.optString("id", "")
                .ifBlank { payload.optString("primaryId", "") }
                .ifBlank { "external-${source.name.lowercase()}" }

            Encounter(
                timestampEpochMs = payload.optLong("timestampEpochMs", System.currentTimeMillis()),
                source = source,
                primaryId = primaryId,
                secondaryId = payload.optString("label", null),
                rssiDbm = payload.optInt("rssiDbm").takeIf { payload.has("rssiDbm") },
                frequencyMhz = payload.optInt("frequencyMhz").takeIf { payload.has("frequencyMhz") },
                lat = payload.optDouble("lat").takeIf { payload.has("lat") } ?: location?.lat,
                lon = payload.optDouble("lon").takeIf { payload.has("lon") } ?: location?.lon,
                rawPayloadJson = payload.toString()
            )
        }
    }
}
