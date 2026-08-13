package dev.argus.tracker.data.chain

import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterProvenance
import dev.argus.tracker.domain.EncounterSource
import org.json.JSONArray
import org.json.JSONObject

data class ChainHello(
    val nodeId: String,
    val app: String,
    val persistentChannelEnabled: Boolean,
    val deviceName: String?
)

data class ChainSyncRequest(
    val requesterNodeId: String,
    val sinceEpochMs: Long,
    val encounters: List<Encounter>
)

data class ChainSyncResponse(
    val responderNodeId: String,
    val responderDeviceName: String?,
    val importedCount: Int,
    val encounters: List<Encounter>
)

object ChainLinkJson {
    fun encodeHello(nodeId: String, persistentChannelEnabled: Boolean, deviceName: String?): String = JSONObject().apply {
        put("nodeId", nodeId)
        put("app", "argus")
        put("persistentChannelEnabled", persistentChannelEnabled)
        if (!deviceName.isNullOrBlank()) put("deviceName", deviceName)
    }.toString()

    fun decodeHello(raw: String): ChainHello? {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val nodeId = obj.optString("nodeId", "").trim()
        if (nodeId.isBlank()) return null
        return ChainHello(
            nodeId = nodeId,
            app = obj.optString("app", "argus"),
            persistentChannelEnabled = obj.optBoolean("persistentChannelEnabled", false),
            deviceName = obj.optString("deviceName", null)
        )
    }

    fun encodeSyncRequest(request: ChainSyncRequest): String = JSONObject().apply {
        put("requesterNodeId", request.requesterNodeId)
        put("sinceEpochMs", request.sinceEpochMs)
        put("encounters", encodeEncounters(request.encounters))
    }.toString()

    fun decodeSyncRequest(raw: String): ChainSyncRequest? {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val requesterNodeId = obj.optString("requesterNodeId", "").trim()
        if (requesterNodeId.isBlank()) return null
        val sinceEpochMs = obj.optLong("sinceEpochMs", 0L)
        val encounters = decodeEncounters(obj.optJSONArray("encounters") ?: JSONArray())
        return ChainSyncRequest(
            requesterNodeId = requesterNodeId,
            sinceEpochMs = sinceEpochMs,
            encounters = encounters
        )
    }

    fun encodeSyncResponse(response: ChainSyncResponse): String = JSONObject().apply {
        put("responderNodeId", response.responderNodeId)
        if (!response.responderDeviceName.isNullOrBlank()) put("responderDeviceName", response.responderDeviceName)
        put("importedCount", response.importedCount)
        put("encounters", encodeEncounters(response.encounters))
    }.toString()

    fun decodeSyncResponse(raw: String): ChainSyncResponse? {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val responderNodeId = obj.optString("responderNodeId", "").trim()
        if (responderNodeId.isBlank()) return null
        val encounters = decodeEncounters(obj.optJSONArray("encounters") ?: JSONArray())
        return ChainSyncResponse(
            responderNodeId = responderNodeId,
            responderDeviceName = obj.optString("responderDeviceName", null),
            importedCount = obj.optInt("importedCount", 0),
            encounters = encounters
        )
    }

    private fun encodeEncounters(encounters: List<Encounter>): JSONArray {
        val array = JSONArray()
        encounters.forEach { encounter ->
            array.put(JSONObject().apply {
                put("timestampEpochMs", encounter.timestampEpochMs)
                put("source", encounter.source.name)
                put("primaryId", encounter.primaryId)
                put("secondaryId", encounter.secondaryId)
                put("rssiDbm", encounter.rssiDbm)
                put("frequencyMhz", encounter.frequencyMhz)
                put("lat", encounter.lat)
                put("lon", encounter.lon)
                put("rawPayloadJson", encounter.rawPayloadJson)
                put("encounterFingerprint", encounter.encounterFingerprint)
                put("provenance", encounter.provenance.name)
                put("provenanceNodeId", encounter.provenanceNodeId)
                put("provenanceOriginNodeId", encounter.provenanceOriginNodeId)
                put("provenancePathNodeIds", encounter.provenancePathNodeIds)
                put("provenanceReceivedAtEpochMs", encounter.provenanceReceivedAtEpochMs)
                put("provenanceHopCount", encounter.provenanceHopCount)
            })
        }
        return array
    }

    private fun decodeEncounters(array: JSONArray): List<Encounter> = buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val source = runCatching { EncounterSource.valueOf(obj.optString("source", "UNKNOWN_RF")) }
                .getOrDefault(EncounterSource.UNKNOWN_RF)
            add(
                Encounter(
                    timestampEpochMs = obj.optLong("timestampEpochMs", 0L),
                    source = source,
                    primaryId = obj.optString("primaryId", "unknown"),
                    secondaryId = if (obj.has("secondaryId") && !obj.isNull("secondaryId")) obj.optString("secondaryId") else null,
                    rssiDbm = if (obj.has("rssiDbm") && !obj.isNull("rssiDbm")) obj.optInt("rssiDbm") else null,
                    frequencyMhz = if (obj.has("frequencyMhz") && !obj.isNull("frequencyMhz")) obj.optInt("frequencyMhz") else null,
                    lat = if (obj.has("lat") && !obj.isNull("lat")) obj.optDouble("lat") else null,
                    lon = if (obj.has("lon") && !obj.isNull("lon")) obj.optDouble("lon") else null,
                    rawPayloadJson = obj.optString("rawPayloadJson", "{}"),
                    encounterFingerprint = if (obj.has("encounterFingerprint") && !obj.isNull("encounterFingerprint")) obj.optString("encounterFingerprint") else null,
                    provenance = runCatching {
                        EncounterProvenance.valueOf(obj.optString("provenance", EncounterProvenance.LOCAL.name))
                    }.getOrDefault(EncounterProvenance.LOCAL),
                    provenanceNodeId = if (obj.has("provenanceNodeId") && !obj.isNull("provenanceNodeId")) obj.optString("provenanceNodeId") else null,
                    provenanceOriginNodeId = if (obj.has("provenanceOriginNodeId") && !obj.isNull("provenanceOriginNodeId")) obj.optString("provenanceOriginNodeId") else null,
                    provenancePathNodeIds = if (obj.has("provenancePathNodeIds") && !obj.isNull("provenancePathNodeIds")) obj.optString("provenancePathNodeIds") else null,
                    provenanceReceivedAtEpochMs = if (obj.has("provenanceReceivedAtEpochMs") && !obj.isNull("provenanceReceivedAtEpochMs")) obj.optLong("provenanceReceivedAtEpochMs") else null,
                    provenanceHopCount = obj.optInt("provenanceHopCount", 0)
                )
            )
        }
    }
}
