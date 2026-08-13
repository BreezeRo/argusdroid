package dev.argus.tracker.remoteid

import dev.argus.tracker.sensing.remoteid.RemoteIdBleDecoder
import dev.argus.tracker.sensing.remoteid.RemoteIdPayloadParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteIdPayloadParserTest {

    @Test
    fun normalizeIncomingPayload_extractsPrimaryAndDecodedFields() {
        val input = JSONObject()
            .put("messageType", "basic_id")
            .put("uasId", "DRONE-1234")
            .put("operatorId", "OP-77")
            .put("lat", 37.4219999)
            .put("lon", -122.0840575)

        val normalized = RemoteIdPayloadParser.normalizeIncomingPayload(
            input = input,
            fallbackTimestampEpochMs = 1_700_000_000_000L,
            fallbackPrimaryId = "fallback"
        )

        assertEquals("DRONE-1234", normalized.primaryId)
        assertEquals("OP-77", normalized.secondaryId)
        assertTrue(normalized.normalizedPayloadJson.contains("argus.remote_id.v1"))
        assertNotNull(normalized.decoded)
        assertEquals("basic_id", normalized.decoded?.messageType)
    }

    @Test
    fun decodeFromBytes_supportsBasicIdFrame() {
        val uas = "RID-TEST-1"
        val bytes = ByteArray(24)
        bytes[0] = 0x00
        bytes[1] = 0x01
        val uasBytes = uas.toByteArray(Charsets.US_ASCII)
        System.arraycopy(uasBytes, 0, bytes, 2, minOf(uasBytes.size, 20))

        val decoded = RemoteIdBleDecoder.decodeFromBytes(bytes)

        assertNotNull(decoded)
        assertEquals("basic_id", decoded?.messageType)
        assertEquals(uas, decoded?.uasId)
    }

    @Test
    fun decodeFromBytes_supportsAsciiJson() {
        val payload = JSONObject()
            .put("messageType", "location_vector")
            .put("uasId", "UAS-JSON-42")
            .put("droneLat", 40.0)
            .put("droneLon", -73.0)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val decoded = RemoteIdBleDecoder.decodeFromBytes(payload)

        assertNotNull(decoded)
        assertEquals("UAS-JSON-42", decoded?.uasId)
    }
}
