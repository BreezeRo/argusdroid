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
                return@withContext TowerLookupResult.Failure(
                    error.message ?: "Unable to build tower lookup payload."
                )
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

        when (radio) {
            "LTE" -> {
                val ci = payload.optLong("ci", Long.MIN_VALUE)
                val tac = payload.optInt("tac", Int.MIN_VALUE)
                if (ci == Long.MIN_VALUE || tac == Int.MIN_VALUE) {
                    throw IllegalArgumentException("Missing LTE tower identifiers (ci/tac).")
                }
                tower.put("cellId", ci)
                tower.put("locationAreaCode", tac)
            }

            "NR" -> {
                val nci = payload.optLong("nci", Long.MIN_VALUE)
                val tac = payload.optInt("tac", Int.MIN_VALUE)
                if (nci == Long.MIN_VALUE || tac == Int.MIN_VALUE) {
                    throw IllegalArgumentException("Missing NR tower identifiers (nci/tac).")
                }
                tower.put("cellId", nci)
                tower.put("locationAreaCode", tac)
            }

            "WCDMA" -> {
                val cid = payload.optInt("cid", Int.MIN_VALUE)
                val lac = payload.optInt("lac", Int.MIN_VALUE)
                if (cid == Int.MIN_VALUE || lac == Int.MIN_VALUE) {
                    throw IllegalArgumentException("Missing WCDMA tower identifiers (cid/lac).")
                }
                tower.put("cellId", cid)
                tower.put("locationAreaCode", lac)
            }

            "GSM" -> {
                val cid = payload.optInt("cid", Int.MIN_VALUE)
                val lac = payload.optInt("lac", Int.MIN_VALUE)
                if (cid == Int.MIN_VALUE || lac == Int.MIN_VALUE) {
                    throw IllegalArgumentException("Missing GSM tower identifiers (cid/lac).")
                }
                tower.put("cellId", cid)
                tower.put("locationAreaCode", lac)
            }

            "CDMA" -> {
                val systemId = payload.optInt("systemId", Int.MIN_VALUE)
                val networkId = payload.optInt("networkId", Int.MIN_VALUE)
                val baseStationId = payload.optInt("basestationId", Int.MIN_VALUE)
                if (systemId == Int.MIN_VALUE || networkId == Int.MIN_VALUE || baseStationId == Int.MIN_VALUE) {
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

    private fun extractInt(payload: JSONObject, key: String): Int? {
        val direct = payload.optInt(key, Int.MIN_VALUE)
        if (direct != Int.MIN_VALUE) return direct
        val asString = payload.optString(key, "")
        if (asString.isBlank() || asString == "null") return null
        return asString.toIntOrNull()
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
