package dev.argus.tracker.sensing

import android.content.Context
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import dev.argus.tracker.sensing.remoteid.RemoteIdPayloadParser
import dev.argus.tracker.worker.ScanSettings
import java.util.Locale
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

        val out = mutableListOf<Encounter>()
        runCatching {
            feedFile.bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, rawLine ->
                    val line = rawLine.trim()
                    if (line.isEmpty()) return@forEachIndexed
                    val payload = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachIndexed

                    val normalizedRemoteId = if (source == EncounterSource.REMOTE_ID) {
                        RemoteIdPayloadParser.normalizeIncomingPayload(
                            input = payload,
                            fallbackTimestampEpochMs = System.currentTimeMillis(),
                            fallbackPrimaryId = "external-${source.name.lowercase()}"
                        )
                    } else {
                        null
                    }

                    val timestampEpochMs = normalizedRemoteId?.timestampEpochMs
                        ?: payload.optLong("timestampEpochMs", System.currentTimeMillis())
                    val lat = payload.optDouble("lat").takeIf { payload.has("lat") } ?: location?.lat
                    val lon = payload.optDouble("lon").takeIf { payload.has("lon") } ?: location?.lon

                    if (source == EncounterSource.SDR && isCameraLikeSdrPayload(payload)) {
                        val cameraType = inferCameraType(payload)
                        val idBase = payload.optString("id", "")
                            .ifBlank { payload.optString("primaryId", "") }
                            .ifBlank { "external-camera-$cameraType-$index" }
                        val normalizedPayloadJson = JSONObject(payload.toString())
                            .put("cameraSchema", "argus.camera.v1")
                            .put("cameraType", cameraType)
                            .put("evidenceType", payload.optString("evidenceType", "sdr_radar_hit"))
                            .put("provider", payload.optString("provider", "SDR"))
                            .toString()

                        out += Encounter(
                            timestampEpochMs = timestampEpochMs,
                            source = EncounterSource.CAMERA,
                            primaryId = "camera:$idBase",
                            secondaryId = payload.optString("label", "").ifBlank { cameraType.uppercase(Locale.US) },
                            rssiDbm = payload.optInt("rssiDbm").takeIf { payload.has("rssiDbm") },
                            frequencyMhz = payload.optInt("frequencyMhz").takeIf { payload.has("frequencyMhz") },
                            lat = lat,
                            lon = lon,
                            rawPayloadJson = normalizedPayloadJson
                        )
                        return@forEachIndexed
                    }

                    val primaryId = normalizedRemoteId?.primaryId
                        ?: payload.optString("id", "")
                            .ifBlank { payload.optString("primaryId", "") }
                            .ifBlank { "external-${source.name.lowercase()}" }

                    val secondaryId = normalizedRemoteId?.secondaryId
                        ?: payload.optString("label", null)

                    val normalizedPayloadJson = normalizedRemoteId?.normalizedPayloadJson ?: payload.toString()

                    out += Encounter(
                        timestampEpochMs = timestampEpochMs,
                        source = source,
                        primaryId = primaryId,
                        secondaryId = secondaryId,
                        rssiDbm = payload.optInt("rssiDbm").takeIf { payload.has("rssiDbm") },
                        frequencyMhz = payload.optInt("frequencyMhz").takeIf { payload.has("frequencyMhz") },
                        lat = lat,
                        lon = lon,
                        rawPayloadJson = normalizedPayloadJson
                    )
                }
            }
        }

        return out
    }

    private fun isSourceEnabled(): Boolean = when (source) {
        EncounterSource.UWB -> ScanSettings.isUwbSensorEnabled(context)
        EncounterSource.SDR -> ScanSettings.isSdrSensorEnabled(context)
        else -> true
    }

    private fun isCameraLikeSdrPayload(payload: JSONObject): Boolean {
        val cameraType = payload.optString("cameraType", "").trim().lowercase(Locale.US)
        if (cameraType in setOf("speed", "redlight", "speed_redlight", "avg_speed_zone")) return true

        val signalClass = payload.optString("signalClass", "").trim().lowercase(Locale.US)
        if (
            signalClass.contains("speed_camera") ||
            signalClass.contains("redlight") ||
            signalClass.contains("red_light") ||
            signalClass.contains("speed_enforcement")
        ) {
            return true
        }

        val evidenceType = payload.optString("evidenceType", "").trim().lowercase(Locale.US)
        return evidenceType.contains("camera")
    }

    private fun inferCameraType(payload: JSONObject): String {
        val directType = payload.optString("cameraType", "").trim().lowercase(Locale.US)
        if (directType in setOf("speed", "redlight", "speed_redlight", "avg_speed_zone")) {
            return directType
        }

        val signalClass = payload.optString("signalClass", "").trim().lowercase(Locale.US)
        if (signalClass.contains("redlight") || signalClass.contains("red_light")) return "redlight"
        if (signalClass.contains("speed")) return "speed"

        return "speed"
    }
}
