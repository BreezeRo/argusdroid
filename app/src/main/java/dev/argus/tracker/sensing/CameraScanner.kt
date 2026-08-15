package dev.argus.tracker.sensing

import android.content.Context
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import dev.argus.tracker.worker.ScanSettings
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class CameraScanner(
    private val context: Context
) : SignalScanner {

    private data class PublicCameraProvider(
        val key: String,
        val endpointUrl: String
    )

    private data class CameraPoi(
        val osmType: String,
        val osmId: Long,
        val lat: Double,
        val lon: Double,
        val cameraType: String,
        val label: String,
        val tags: JSONObject,
        val providerKey: String
    )

    @Volatile
    private var cachedPoisMtimeMs: Long = -1L

    @Volatile
    private var cachedPois: List<CameraPoi> = emptyList()

    companion object {
        private val PUBLIC_CAMERA_PROVIDERS = listOf(
            PublicCameraProvider(
                key = "OSM_OVERPASS_DE",
                endpointUrl = "https://overpass-api.de/api/interpreter"
            ),
            PublicCameraProvider(
                key = "OSM_OVERPASS_FR",
                endpointUrl = "https://overpass.openstreetmap.fr/api/interpreter"
            ),
            PublicCameraProvider(
                key = "OSM_OVERPASS_KUMI",
                endpointUrl = "https://overpass.kumi.systems/api/interpreter"
            )
        )
        private const val CACHE_PROVIDER_KEY = "OSM_OVERPASS_CACHE"
        private const val CACHE_DIR = "camera"
        private const val CACHE_FILE = "osm_camera_cache.json"
        private const val CACHE_TTL_MS = 30L * 60L * 1000L
        private const val FETCH_MIN_INTERVAL_MS = 5L * 60L * 1000L
        private const val MIN_RADIUS_METERS = 1000
        private const val MAX_RADIUS_METERS = 25000
        private const val DEFAULT_RADIUS_METERS = 8000
        private const val MAX_PAYLOAD_BYTES = 3 * 1024 * 1024
        private const val KEY_LAST_FETCH_EPOCH_MS = "camera_public_last_fetch_epoch_ms"
        private const val KEY_LAST_FETCH_LAT = "camera_public_last_fetch_lat"
        private const val KEY_LAST_FETCH_LON = "camera_public_last_fetch_lon"
        private const val PREFS_NAME = "argus_settings"
    }

    override suspend fun scanOnce(): List<Encounter> = withContext(Dispatchers.IO) {
        if (!ScanSettings.isSdrSensorEnabled(context)) {
            return@withContext emptyList()
        }

        val now = System.currentTimeMillis()
        val location = LocationSnapshotProvider.read(context)
        readPublicCameraEncounters(now, location)
    }

    private fun readPublicCameraEncounters(now: Long, location: DetectionLocation?): List<Encounter> {
        val cachedPois = readCachedPoisIfFresh(now)
        val cachedEncounters = cachedPois.map { it.toEncounter(now) }

        if (location == null) {
            return cachedEncounters
        }

        if (!shouldFetchPublic(location, now)) {
            return cachedEncounters
        }

        val query = buildOverpassQuery(location.lat, location.lon, DEFAULT_RADIUS_METERS)
        val fetched = fetchFromPublicProviders(query)

        if (fetched == null || fetched.first.isBlank()) {
            return cachedEncounters
        }

        val fetchedBody = fetched.first
        val providerKey = fetched.second

        writeCache(fetchedBody)
        rememberFetchLocation(location, now)
        val parsedPois = parseOverpassToPois(fetchedBody, providerKey)
        return parsedPois.map { it.toEncounter(now) }
    }

    private fun fetchFromPublicProviders(query: String): Pair<String, String>? {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())

        PUBLIC_CAMERA_PROVIDERS.forEach { provider ->
            val body = runCatching {
                val requestUrl = "${provider.endpointUrl}?data=$encoded"
                val conn = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4500
                    readTimeout = 4500
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Argus/1.0 (Android)")
                }
                conn.readResponseTextChunked(MAX_PAYLOAD_BYTES)
            }.getOrNull()

            if (!body.isNullOrBlank()) {
                return body to provider.key
            }
        }

        return null
    }

    private fun buildOverpassQuery(lat: Double, lon: Double, radiusMeters: Int): String {
        val radius = radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        return """
            [out:json][timeout:20];
            (
              node["highway"="speed_camera"](around:$radius,$lat,$lon);
              node["enforcement"="speed_camera"](around:$radius,$lat,$lon);
              node["enforcement"="maxspeed"](around:$radius,$lat,$lon);
              node["enforcement"="red_light"](around:$radius,$lat,$lon);
              node["camera:type"="red_light"](around:$radius,$lat,$lon);
              way["highway"="speed_camera"](around:$radius,$lat,$lon);
              way["enforcement"="speed_camera"](around:$radius,$lat,$lon);
              way["enforcement"="maxspeed"](around:$radius,$lat,$lon);
              way["enforcement"="red_light"](around:$radius,$lat,$lon);
              way["camera:type"="red_light"](around:$radius,$lat,$lon);
            );
            out center tags;
        """.trimIndent()
    }

    private fun parseOverpassToPois(body: String, providerKey: String = CACHE_PROVIDER_KEY): List<CameraPoi> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val elements = root.optJSONArray("elements") ?: JSONArray()
        val pois = mutableListOf<CameraPoi>()

        for (i in 0 until elements.length()) {
            val element = elements.optJSONObject(i) ?: continue
            val lat = element.optDoubleOrNull("lat")
                ?: element.optJSONObject("center")?.optDoubleOrNull("lat")
                ?: continue
            val lon = element.optDoubleOrNull("lon")
                ?: element.optJSONObject("center")?.optDoubleOrNull("lon")
                ?: continue

            val tags = element.optJSONObject("tags") ?: JSONObject()
            val cameraType = inferCameraType(tags)
            val osmType = element.optString("type", "node")
            val osmId = element.optLong("id", -1L).takeIf { it > 0L } ?: continue
            val label = tags.optString("name", "").ifBlank {
                when (cameraType) {
                    "redlight" -> "Red-light camera"
                    "speed_redlight" -> "Speed/Red-light camera"
                    "avg_speed_zone" -> "Average speed zone camera"
                    else -> "Speed camera"
                }
            }

            pois += CameraPoi(
                osmType = osmType,
                osmId = osmId,
                lat = lat,
                lon = lon,
                cameraType = cameraType,
                label = label,
                tags = tags,
                providerKey = providerKey
            )
        }

        return pois
    }

    private fun shouldFetchPublic(location: DetectionLocation, now: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong(KEY_LAST_FETCH_EPOCH_MS, 0L)
        if (lastFetch > 0L && now - lastFetch < FETCH_MIN_INTERVAL_MS) return false

        val lastLat = prefs.getString(KEY_LAST_FETCH_LAT, null)?.toDoubleOrNull()
        val lastLon = prefs.getString(KEY_LAST_FETCH_LON, null)?.toDoubleOrNull()
        if (lastLat == null || lastLon == null) return true

        val movedMeters = haversineMeters(lastLat, lastLon, location.lat, location.lon)
        return movedMeters >= 1200.0 || now - lastFetch >= CACHE_TTL_MS
    }

    private fun rememberFetchLocation(location: DetectionLocation, now: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_FETCH_EPOCH_MS, now)
            .putString(KEY_LAST_FETCH_LAT, location.lat.toString())
            .putString(KEY_LAST_FETCH_LON, location.lon.toString())
            .apply()
    }

    private fun cacheFile() = context.filesDir.resolve(CACHE_DIR).resolve(CACHE_FILE)

    private fun readCachedPoisIfFresh(now: Long): List<CameraPoi> {
        val file = cacheFile()
        if (!file.exists() || !file.isFile) return emptyList()
        val ageMs = now - file.lastModified()
        if (ageMs !in 0..CACHE_TTL_MS) return emptyList()

        val mtime = file.lastModified()
        if (mtime == cachedPoisMtimeMs) {
            return cachedPois
        }

        val parsed = runCatching {
            val body = file.readText()
            parseOverpassToPois(body)
        }.getOrDefault(emptyList())
        cachedPois = parsed
        cachedPoisMtimeMs = mtime
        return parsed
    }

    private fun writeCache(body: String) {
        val file = cacheFile()
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(body)
            cachedPois = parseOverpassToPois(body)
            cachedPoisMtimeMs = file.lastModified()
        }
    }

    private fun inferCameraType(payload: JSONObject): String {
        val directType = payload.optString("cameraType", "").trim().lowercase(Locale.US)
        if (directType in setOf("speed", "redlight", "speed_redlight", "avg_speed_zone")) {
            return directType
        }

        val signalClass = payload.optString("signalClass", "").trim().lowercase(Locale.US)
        if (signalClass.contains("redlight") || signalClass.contains("red_light")) return "redlight"
        if (signalClass.contains("speed")) return "speed"

        val enforcement = payload.optString("enforcement", "").trim().lowercase(Locale.US)
        val cameraHint = payload.optString("camera:type", "").trim().lowercase(Locale.US)
        val hasSpeed = enforcement.contains("maxspeed") || payload.optString("highway", "").equals("speed_camera", ignoreCase = true)
        val hasRed = enforcement.contains("red_light") || cameraHint.contains("red")

        return when {
            hasSpeed && hasRed -> "speed_redlight"
            hasRed -> "redlight"
            hasSpeed -> "speed"
            else -> "speed"
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2.0) * kotlin.math.sin(dLat / 2.0) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2.0) * kotlin.math.sin(dLon / 2.0)
        val c = 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
        return 6_371_000.0 * c
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = optDouble(key, Double.NaN)
        return value.takeIf { it.isFinite() }
    }

    private fun HttpURLConnection.readResponseTextChunked(maxBytes: Int): String {
        val responseCode = responseCode
        val stream = if (responseCode in 200..299) inputStream else errorStream
            ?: throw IllegalStateException("HTTP $responseCode with no response body")
        stream.use { input ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) {
                        throw IllegalStateException("Response too large: $total bytes")
                    }
                    output.write(buffer, 0, read)
                }
                return output.toString(Charsets.UTF_8.name())
            }
        }
    }

    private fun CameraPoi.toEncounter(now: Long): Encounter {
        val payload = JSONObject()
            .put("cameraSchema", "argus.camera.v1")
            .put("cameraType", cameraType)
            .put("evidenceType", "public_poi")
            .put("provider", providerKey)
            .put("osmType", osmType)
            .put("osmId", osmId)
            .put("lat", lat)
            .put("lon", lon)
            .put("tags", tags)

        return Encounter(
            timestampEpochMs = now,
            source = EncounterSource.CAMERA,
            primaryId = "osm:$osmType:$osmId",
            secondaryId = label,
            rssiDbm = null,
            frequencyMhz = null,
            lat = lat,
            lon = lon,
            rawPayloadJson = payload.toString()
        )
    }
}
