package dev.argus.tracker.sensing.remoteid

import android.bluetooth.le.ScanRecord
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject

object RemoteIdBleDecoder {

    fun decodeFromScanRecord(record: ScanRecord?): RemoteIdDecoded? {
        if (record == null) return null

        val serviceDataDecoded = record.serviceData?.values
            ?.asSequence()
            ?.mapNotNull { decodeFromBytes(it, "service_data") }
            ?.firstOrNull()

        if (serviceDataDecoded != null) return serviceDataDecoded

        val manufacturerData = record.manufacturerSpecificData
        for (i in 0 until manufacturerData.size()) {
            val bytes = manufacturerData.valueAt(i) ?: continue
            val decoded = decodeFromBytes(bytes, "manufacturer_data")
            if (decoded != null) return decoded
        }

        return null
    }

    fun decodeFromBytes(bytes: ByteArray?, sourceLabel: String = "unknown"): RemoteIdDecoded? {
        if (bytes == null || bytes.isEmpty()) return null

        val jsonDecoded = decodeAsciiJson(bytes)
        if (jsonDecoded != null) {
            val parsed = RemoteIdPayloadParser.parseFromJson(jsonDecoded)
            if (parsed != null) {
                return parsed.copy(
                    parseNotes = parsed.parseNotes + "decoded_from_$sourceLabel"
                )
            }
        }

        val frameDecoded = decodeSimpleOpenDroneIdFrame(bytes)
        if (frameDecoded != null) {
            return frameDecoded.copy(
                parseNotes = frameDecoded.parseNotes + "decoded_from_$sourceLabel"
            )
        }

        return null
    }

    private fun decodeAsciiJson(bytes: ByteArray): JSONObject? {
        val ascii = runCatching { String(bytes, Charsets.UTF_8).trim() }.getOrNull() ?: return null
        if (!ascii.startsWith("{") || !ascii.endsWith("}")) return null
        return runCatching { JSONObject(ascii) }.getOrNull()
    }

    private fun decodeSimpleOpenDroneIdFrame(bytes: ByteArray): RemoteIdDecoded? {
        if (bytes.size < 6) return null
        val messageType = bytes[0].toInt() and 0x0F

        return when (messageType) {
            0x00 -> decodeBasicId(bytes)
            0x01 -> decodeLocation(bytes)
            0x05 -> decodeOperatorId(bytes)
            else -> null
        }
    }

    private fun decodeBasicId(bytes: ByteArray): RemoteIdDecoded? {
        if (bytes.size < 23) return null
        val uasIdBytes = bytes.copyOfRange(2, minOf(bytes.size, 22))
        val uasId = uasIdBytes
            .takeWhile { it.toInt() != 0 }
            .toByteArray()
            .toString(Charsets.US_ASCII)
            .trim()
            .ifBlank { null }
            ?: return null

        return RemoteIdDecoded(
            messageType = "basic_id",
            uasId = uasId,
            operatorId = null,
            operatorLat = null,
            operatorLon = null,
            droneLat = null,
            droneLon = null,
            altitudeMeters = null,
            speedMetersPerSecond = null,
            headingDegrees = null,
            emergencyStatus = null,
            messageTimestampEpochMs = System.currentTimeMillis(),
            parseConfidence = RemoteIdParseConfidence.MEDIUM,
            parserVersion = RemoteIdPayloadParser.PARSER_VERSION,
            parseNotes = listOf("basic_id_frame")
        )
    }

    private fun decodeLocation(bytes: ByteArray): RemoteIdDecoded? {
        if (bytes.size < 18) return null
        val latRaw = readSignedInt32Flexible(bytes, 2)
        val lonRaw = readSignedInt32Flexible(bytes, 6)
        val altRaw = readSignedInt16Flexible(bytes, 10)
        val speedRaw = bytes.getOrNull(12)?.toInt()?.and(0xFF)
        val headingRaw = bytes.getOrNull(13)?.toInt()?.and(0xFF)

        val lat = (latRaw / 1e7).takeIf { it in -90.0..90.0 }
        val lon = (lonRaw / 1e7).takeIf { it in -180.0..180.0 }
        if (lat == null || lon == null) return null

        return RemoteIdDecoded(
            messageType = "location_vector",
            uasId = null,
            operatorId = null,
            operatorLat = null,
            operatorLon = null,
            droneLat = lat,
            droneLon = lon,
            altitudeMeters = altRaw / 10.0,
            speedMetersPerSecond = speedRaw?.div(4.0),
            headingDegrees = headingRaw?.times(2.0),
            emergencyStatus = null,
            messageTimestampEpochMs = System.currentTimeMillis(),
            parseConfidence = RemoteIdParseConfidence.LOW,
            parserVersion = RemoteIdPayloadParser.PARSER_VERSION,
            parseNotes = listOf("location_frame")
        )
    }

    private fun decodeOperatorId(bytes: ByteArray): RemoteIdDecoded? {
        if (bytes.size < 24) return null
        val opBytes = bytes.copyOfRange(2, minOf(bytes.size, 22))
        val operatorId = opBytes
            .takeWhile { it.toInt() != 0 }
            .toByteArray()
            .toString(Charsets.US_ASCII)
            .trim()
            .ifBlank { null }
            ?: return null

        return RemoteIdDecoded(
            messageType = "operator_id",
            uasId = null,
            operatorId = operatorId,
            operatorLat = null,
            operatorLon = null,
            droneLat = null,
            droneLon = null,
            altitudeMeters = null,
            speedMetersPerSecond = null,
            headingDegrees = null,
            emergencyStatus = null,
            messageTimestampEpochMs = System.currentTimeMillis(),
            parseConfidence = RemoteIdParseConfidence.LOW,
            parserVersion = RemoteIdPayloadParser.PARSER_VERSION,
            parseNotes = listOf("operator_id_frame")
        )
    }

    private fun readSignedInt32Flexible(bytes: ByteArray, offset: Int): Double {
        if (offset + 4 > bytes.size) return 0.0
        val slice = bytes.copyOfRange(offset, offset + 4)
        val little = ByteBuffer.wrap(slice).order(ByteOrder.LITTLE_ENDIAN).int.toDouble()
        val big = ByteBuffer.wrap(slice).order(ByteOrder.BIG_ENDIAN).int.toDouble()

        val littleScaled = little / 1e7
        return if (littleScaled in -180.0..180.0) little else big
    }

    private fun readSignedInt16Flexible(bytes: ByteArray, offset: Int): Double {
        if (offset + 2 > bytes.size) return 0.0
        val slice = bytes.copyOfRange(offset, offset + 2)
        val little = ByteBuffer.wrap(slice).order(ByteOrder.LITTLE_ENDIAN).short.toDouble()
        val big = ByteBuffer.wrap(slice).order(ByteOrder.BIG_ENDIAN).short.toDouble()
        return if (kotlin.math.abs(little) <= 10000) little else big
    }
}
