package dev.argus.tracker.sensing

import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class TowerLocationEstimate(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val provider: String = "Mozilla Location Service"
)

sealed class TowerLookupResult {
    data class Success(val estimate: TowerLocationEstimate) : TowerLookupResult()
    data class Failure(val reason: String) : TowerLookupResult()
}

object CellTowerLookupService {
    private const val MLS_LOOKUP_URL = "https://location.services.mozilla.com/v1/geolocate?key=test"

    suspend fun lookup(encounter: Encounter): TowerLookupResult = withContext(Dispatchers.IO) {
        if (encounter.source != EncounterSource.CELL) {
            return@withContext TowerLookupResult.Failure("Lookup is only supported for CELL encounters.")
        }

        val requestBody = runCatching { buildRequestBody(encounter) }
            .getOrElse { error ->
                val reason = error.message ?: "Unable to build tower lookup payload."
                val isMissingNrIds = reason.contains("Missing NR tower identifiers", ignoreCase = true)
                if (isMissingNrIds && encounter.lat != null && encounter.lon != null) {
                    return@withContext TowerLookupResult.Success(
                        TowerLocationEstimate(
                            latitude = encounter.lat,
                            longitude = encounter.lon,
                            accuracyMeters = null,
                            provider = "Encounter location fallback (NR IDs unavailable)"
                        )
                    )
                }
                return@withContext TowerLookupResult.Failure(reason)
            }

        val connection = (URL(MLS_LOOKUP_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        runCatching {
            connection.outputStream.use { stream ->
                stream.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.use(::readFully)
            } else {
                connection.errorStream?.use(::readFully).orEmpty()
            }

            if (responseCode !in 200..299) {
                return@runCatching TowerLookupResult.Failure(
                    "Lookup failed (HTTP $responseCode). ${responseBody.take(180)}"
                )
            }

            val responseJson = JSONObject(responseBody)
            val location = responseJson.optJSONObject("location")
                ?: return@runCatching TowerLookupResult.Failure(
                    "Lookup response did not include a location."
                )

            val lat = location.optDouble("lat", Double.NaN)
            val lon = location.optDouble("lng", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) {
                return@runCatching TowerLookupResult.Failure("Lookup returned invalid coordinates.")
            }

            val accuracy = responseJson.optDouble("accuracy", Double.NaN)
                .takeIf { it.isFinite() }

            TowerLookupResult.Success(
                TowerLocationEstimate(
                    latitude = lat,
                    longitude = lon,
                    accuracyMeters = accuracy
                )
            )
        }.getOrElse { error ->
            TowerLookupResult.Failure(error.message ?: "Lookup request failed.")
        }.also {
            connection.disconnect()
        }
    }

    private fun buildRequestBody(encounter: Encounter): JSONObject {
        val payload = JSONObject(encounter.rawPayloadJson)
        val radio = payload.optString("radio", "").uppercase()

        val radioType = when (radio) {
            "LTE" -> "lte"
            "NR" -> "lte"
            "WCDMA" -> "wcdma"
            "GSM" -> "gsm"
            "CDMA" -> "cdma"
            else -> throw IllegalArgumentException("Unsupported or missing radio type: $radio")
        }

        val tower = JSONObject()
        val primaryIdParts = encounter.primaryId.split(':')

        when (radio) {
            "LTE" -> {
                val ci = extractLong(payload, "ci")
                    ?: primaryIdParts.getOrNull(1)?.toLongOrNull()
                val tac = extractInt(payload, "tac")
                    ?: primaryIdParts.getOrNull(3)?.toIntOrNull()

                if (ci != null) {
                    tower.put("cellId", ci)
                }
                if (tac != null) {
                    tower.put("locationAreaCode", tac)
                }
                if (ci == null && tac == null) {
                    throw IllegalArgumentException("Missing LTE tower identifiers (ci/tac).")
                }
            }

            "NR" -> {
                val nci = extractLong(payload, "nci")
                    ?: primaryIdParts.getOrNull(1)?.toLongOrNull()
                val tac = extractInt(payload, "tac")
                    ?: primaryIdParts.getOrNull(3)?.toIntOrNull()

                if (nci != null) {
                    tower.put("cellId", nci)
                }
                if (tac != null) {
                    tower.put("locationAreaCode", tac)
                }
                if (nci == null && tac == null) {
                    throw IllegalArgumentException(
                        "Missing NR tower identifiers (nci/tac). primaryId=${encounter.primaryId}, payload.nci=${payload.opt("nci")}, payload.tac=${payload.opt("tac")}."
                    )
                }
            }

            "WCDMA" -> {
                val cid = extractInt(payload, "cid")
                    ?: primaryIdParts.getOrNull(1)?.toIntOrNull()
                val lac = extractInt(payload, "lac")
                    ?: primaryIdParts.getOrNull(3)?.toIntOrNull()

                if (cid != null) {
                    tower.put("cellId", cid)
                }
                if (lac != null) {
                    tower.put("locationAreaCode", lac)
                }
                if (cid == null && lac == null) {
                    throw IllegalArgumentException("Missing WCDMA tower identifiers (cid/lac).")
                }
            }

            "GSM" -> {
                val cid = extractInt(payload, "cid")
                    ?: primaryIdParts.getOrNull(1)?.toIntOrNull()
                val lac = extractInt(payload, "lac")
                    ?: primaryIdParts.getOrNull(2)?.toIntOrNull()

                if (cid != null) {
                    tower.put("cellId", cid)
                }
                if (lac != null) {
                    tower.put("locationAreaCode", lac)
                }
                if (cid == null && lac == null) {
                    throw IllegalArgumentException("Missing GSM tower identifiers (cid/lac).")
                }
            }

            "CDMA" -> {
                val systemId = extractInt(payload, "systemId")
                val networkId = extractInt(payload, "networkId")
                val baseStationId = extractInt(payload, "basestationId")
                if (systemId == null || networkId == null || baseStationId == null) {
                    throw IllegalArgumentException("Missing CDMA identifiers (systemId/networkId/basestationId).")
                }
                tower.put("systemId", systemId)
                tower.put("networkId", networkId)
                tower.put("baseStationId", baseStationId)
            }
        }

        extractInt(payload, "mcc")?.let { tower.put("mobileCountryCode", it) }
        extractInt(payload, "mnc")?.let { tower.put("mobileNetworkCode", it) }
        encounter.rssiDbm?.let { tower.put("signalStrength", it) }

        return JSONObject()
            .put("radioType", radioType)
            .put("considerIp", false)
            .put("cellTowers", JSONArray().put(tower))
    }

    private fun extractLong(payload: JSONObject, key: String): Long? {
        val value = payload.opt(key)
        if (value == null || value == JSONObject.NULL) return null
        val asLong = when (value) {
            is Number -> value.toLong()
            else -> value.toString().toLongOrNull()
        } ?: return null
        if (asLong == Long.MAX_VALUE || asLong < 0L) return null
        return asLong
    }

    private fun extractInt(payload: JSONObject, key: String): Int? {
        val value = payload.opt(key)
        if (value == null || value == JSONObject.NULL) return null
        val asInt = when (value) {
            is Number -> value.toInt()
            else -> value.toString().toIntOrNull()
        } ?: return null
        if (asInt == Int.MAX_VALUE || asInt == Int.MIN_VALUE || asInt < 0) return null
        return asInt
    }

    private fun readFully(stream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        return buildString {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                append(line)
            }
        }
    }
}
