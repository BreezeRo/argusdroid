package dev.argus.tracker.sensing.remoteid

import org.json.JSONArray
import org.json.JSONObject

object RemoteIdPayloadParser {
    const val SCHEMA_VERSION = "argus.remote_id.v1"
    const val PARSER_VERSION = "1.0"

    fun normalizeIncomingPayload(
        input: JSONObject,
        fallbackTimestampEpochMs: Long = System.currentTimeMillis(),
        fallbackPrimaryId: String = "remote-id-unknown"
    ): RemoteIdNormalizedPayload {
        val decoded = parseFromJson(input)
        val resolvedPrimaryId = decoded?.uasId
            ?.takeIf { it.isNotBlank() }
            ?: input.optString("uasId", "")
                .trim()
                .takeIf { it.isNotBlank() }
            ?: input.optString("id", "")
                .trim()
                .takeIf { it.isNotBlank() }
            ?: input.optString("primaryId", "")
                .trim()
                .takeIf { it.isNotBlank() }
            ?: fallbackPrimaryId

        val resolvedSecondaryId = decoded?.operatorId
            ?.takeIf { it.isNotBlank() }
            ?: input.optString("operatorId", "")
                .trim()
                .takeIf { it.isNotBlank() }
            ?: input.optString("label", "")
                .trim()
                .takeIf { it.isNotBlank() }

        val resolvedTimestamp = decoded?.messageTimestampEpochMs
            ?.takeIf { it > 0L }
            ?: input.optLong("timestampEpochMs", fallbackTimestampEpochMs)

        val normalizedRoot = JSONObject(input.toString())
        normalizedRoot.put("remoteIdSchema", SCHEMA_VERSION)
        normalizedRoot.put("remoteIdParserVersion", PARSER_VERSION)
        normalizedRoot.put("remoteIdPrimaryId", resolvedPrimaryId)
        normalizedRoot.put("remoteIdSecondaryId", resolvedSecondaryId)
        if (decoded != null) {
            normalizedRoot.put("remoteIdDecoded", decoded.toJson())
        }

        return RemoteIdNormalizedPayload(
            primaryId = resolvedPrimaryId,
            secondaryId = resolvedSecondaryId,
            timestampEpochMs = resolvedTimestamp,
            normalizedPayloadJson = normalizedRoot.toString(),
            decoded = decoded
        )
    }

    fun parseFromJson(input: JSONObject): RemoteIdDecoded? {
        val decodedNode = input.optJSONObject("remoteIdDecoded")
        val source = decodedNode ?: input

        val messageType = source.optString("messageType", "")
            .ifBlank { source.optString("message_type", "") }
            .ifBlank { source.optString("frameType", "") }
            .ifBlank { "unknown" }

        val uasId = source.optString("uasId", "")
            .ifBlank { source.optString("uas_id", "") }
            .ifBlank { source.optString("id", "") }
            .trim()
            .ifBlank { null }

        val operatorId = source.optString("operatorId", "")
            .ifBlank { source.optString("operator_id", "") }
            .trim()
            .ifBlank { null }

        val operatorLat = source.optDoubleOrNull("operatorLat")
            ?: source.optDoubleOrNull("operator_lat")
        val operatorLon = source.optDoubleOrNull("operatorLon")
            ?: source.optDoubleOrNull("operator_lon")
        val droneLat = source.optDoubleOrNull("droneLat")
            ?: source.optDoubleOrNull("lat")
        val droneLon = source.optDoubleOrNull("droneLon")
            ?: source.optDoubleOrNull("lon")
        val altitudeMeters = source.optDoubleOrNull("altitudeMeters")
            ?: source.optDoubleOrNull("aircraftAltitudeMeters")
            ?: source.optDoubleOrNull("aircraft_altitude_m")
            ?: source.optDoubleOrNull("aircraftAltitude")
            ?: source.optDoubleOrNull("aircraft_altitude")
            ?: source.optDoubleOrNull("droneAltitudeMeters")
            ?: source.optDoubleOrNull("drone_altitude_m")
            ?: source.optDoubleOrNull("heightMeters")
            ?: source.optDoubleOrNull("height_m")
            ?: source.optDoubleOrNull("altitude_m")
            ?: source.optDoubleOrNull("alt")
        val speedMps = source.optDoubleOrNull("speedMetersPerSecond")
            ?: source.optDoubleOrNull("speed_mps")
            ?: source.optDoubleOrNull("speed")
        val heading = source.optDoubleOrNull("headingDegrees")
            ?: source.optDoubleOrNull("heading_deg")
            ?: source.optDoubleOrNull("heading")

        val messageTimestamp = source.optLongOrNull("messageTimestampEpochMs")
            ?: source.optLongOrNull("message_timestamp_epoch_ms")
            ?: source.optLongOrNull("timestampEpochMs")

        val emergency = source.optString("emergencyStatus", "")
            .ifBlank { source.optString("emergency_status", "") }
            .trim()
            .ifBlank { null }

        val notes = mutableListOf<String>()
        if (uasId == null) notes += "uasId_missing"
        if (droneLat == null || droneLon == null) notes += "drone_position_missing"

        val confidence = when {
            uasId != null && droneLat != null && droneLon != null -> RemoteIdParseConfidence.HIGH
            uasId != null -> RemoteIdParseConfidence.MEDIUM
            droneLat != null && droneLon != null -> RemoteIdParseConfidence.LOW
            else -> RemoteIdParseConfidence.NONE
        }

        if (confidence == RemoteIdParseConfidence.NONE && decodedNode == null) {
            return null
        }

        return RemoteIdDecoded(
            messageType = messageType,
            uasId = uasId,
            operatorId = operatorId,
            operatorLat = operatorLat,
            operatorLon = operatorLon,
            droneLat = droneLat,
            droneLon = droneLon,
            altitudeMeters = altitudeMeters,
            speedMetersPerSecond = speedMps,
            headingDegrees = heading,
            emergencyStatus = emergency,
            messageTimestampEpochMs = messageTimestamp,
            parseConfidence = confidence,
            parserVersion = PARSER_VERSION,
            parseNotes = notes
        )
    }

    private fun RemoteIdDecoded.toJson(): JSONObject = JSONObject()
        .put("messageType", messageType)
        .put("uasId", uasId)
        .put("operatorId", operatorId)
        .put("operatorLat", operatorLat)
        .put("operatorLon", operatorLon)
        .put("droneLat", droneLat)
        .put("droneLon", droneLon)
        .put("altitudeMeters", altitudeMeters)
        .put("speedMetersPerSecond", speedMetersPerSecond)
        .put("headingDegrees", headingDegrees)
        .put("emergencyStatus", emergencyStatus)
        .put("messageTimestampEpochMs", messageTimestampEpochMs)
        .put("parseConfidence", parseConfidence.name)
        .put("parserVersion", parserVersion)
        .put("parseNotes", JSONArray(parseNotes))

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return optLong(key)
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = optDouble(key)
        if (!value.isFinite()) return null
        return value
    }
}
