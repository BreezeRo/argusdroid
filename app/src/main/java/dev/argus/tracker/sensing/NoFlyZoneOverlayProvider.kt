package dev.argus.tracker.sensing

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object NoFlyZoneOverlayProvider {
    data class NoFlyZonePolygon(
        val id: String,
        val label: String,
        val source: String,
        val regulationHint: String?,
        val lowerAltitudeFeet: Int?,
        val upperAltitudeFeet: Int?,
        val boundary: List<DetectionLocation>
    )

    private const val INGEST_DIR = "ingest"
    private const val FILE_NAME = "no_fly_zones.geojson"
    private const val PUBLIC_CACHE_PREFIX = "no_fly_zones_public_"
    private const val PUBLIC_CACHE_SUFFIX = ".geojson"
    private const val NETWORK_TTL_MS = 6L * 60L * 60L * 1000L
    private const val NETWORK_CONNECT_TIMEOUT_MS = 12_000
    private const val NETWORK_READ_TIMEOUT_MS = 18_000
    private const val PUBLIC_RADIUS_MILES = 120.0
    private const val PUBLIC_SOURCE_FAA_FACILITY_MAP = "FAA UAS Facility Map"
    private const val PUBLIC_SOURCE_FAA_NSUFR = "FAA National Security UAS Flight Restrictions"
    private val PUBLIC_SOURCE_ENDPOINTS = listOf(
        PUBLIC_SOURCE_FAA_FACILITY_MAP to "https://services6.arcgis.com/ssFJjBXIUyZDrSYZ/arcgis/rest/services/FAA_UAS_FacilityMap_Data/FeatureServer/0/query",
        PUBLIC_SOURCE_FAA_NSUFR to "https://services6.arcgis.com/ssFJjBXIUyZDrSYZ/arcgis/rest/services/DoD_Mar_13/FeatureServer/0/query"
    )

    @Volatile
    private var cachedMtimeMs: Long = -1L

    @Volatile
    private var cachedPolygons: List<NoFlyZonePolygon> = emptyList()

    @Volatile
    private var cachedPublicCacheKey: String? = null

    @Volatile
    private var cachedPublicPolygons: List<NoFlyZonePolygon> = emptyList()

    fun read(context: Context, near: DetectionLocation? = null): List<NoFlyZonePolygon> {
        val publicPolygons = readPublicFallback(context, near)

        val file = context.filesDir.resolve(INGEST_DIR).resolve(FILE_NAME)
        if (!file.exists() || !file.isFile) return publicPolygons

        val mtime = file.lastModified()
        if (mtime == cachedMtimeMs) {
            return publicPolygons + cachedPolygons
        }

        val parsed = runCatching {
            parseGeoJson(file.readText())
        }.getOrDefault(emptyList())

        cachedMtimeMs = mtime
        cachedPolygons = parsed
        return publicPolygons + parsed
    }

    private fun readPublicFallback(context: Context, near: DetectionLocation?): List<NoFlyZonePolygon> {
        val cacheKey = publicCacheKey(near)
        if (cacheKey == cachedPublicCacheKey && cachedPublicPolygons.isNotEmpty()) {
            return cachedPublicPolygons
        }

        val cacheFile = context.filesDir
            .resolve(INGEST_DIR)
            .resolve("$PUBLIC_CACHE_PREFIX$cacheKey$PUBLIC_CACHE_SUFFIX")

        val cachedFresh = if (cacheFile.exists() && cacheFile.isFile) {
            val ageMs = System.currentTimeMillis() - cacheFile.lastModified()
            if (ageMs in 0..NETWORK_TTL_MS) {
                runCatching { parseGeoJson(cacheFile.readText()) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        if (cachedFresh.isNotEmpty()) {
            cachedPublicCacheKey = cacheKey
            cachedPublicPolygons = cachedFresh
            return cachedFresh
        }

        val fetched = fetchPublicGeoJson(near)
        if (fetched != null) {
            val parsed = parseGeoJson(fetched)
            if (parsed.isNotEmpty()) {
                runCatching {
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeText(fetched)
                }
                cachedPublicCacheKey = cacheKey
                cachedPublicPolygons = parsed
                return parsed
            }
        }

        val staleFallback = if (cacheFile.exists() && cacheFile.isFile) {
            runCatching { parseGeoJson(cacheFile.readText()) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        cachedPublicCacheKey = cacheKey
        cachedPublicPolygons = staleFallback
        return staleFallback
    }

    private fun publicCacheKey(near: DetectionLocation?): String {
        if (near == null) return "global"
        val latBucket = kotlin.math.round(near.lat * 2.0) / 2.0
        val lonBucket = kotlin.math.round(near.lon * 2.0) / 2.0
        val latLabel = String.format(Locale.US, "%.1f", latBucket).replace('.', '_')
        val lonLabel = String.format(Locale.US, "%.1f", lonBucket).replace('.', '_')
        return "${latLabel}_${lonLabel}"
    }

    private fun fetchPublicGeoJson(near: DetectionLocation?): String? {
        val mergedFeatures = JSONArray()
        PUBLIC_SOURCE_ENDPOINTS.forEach { (sourceLabel, endpoint) ->
            val response = runCatching {
                fetchArcGisGeoJson(endpoint = endpoint, near = near)
            }.getOrNull() ?: return@forEach

            val root = runCatching { JSONObject(response) }.getOrNull() ?: return@forEach
            val features = root.optJSONArray("features") ?: return@forEach
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val properties = feature.optJSONObject("properties") ?: JSONObject().also {
                    feature.put("properties", it)
                }
                if (properties.optString("source", "").isBlank()) {
                    properties.put("source", sourceLabel)
                }
                mergedFeatures.put(feature)
            }
        }

        if (mergedFeatures.length() == 0) return null
        return JSONObject()
            .put("type", "FeatureCollection")
            .put("features", mergedFeatures)
            .toString()
    }

    @Throws(IOException::class)
    private fun fetchArcGisGeoJson(endpoint: String, near: DetectionLocation?): String {
        val url = buildArcGisQueryUrl(endpoint = endpoint, near = near)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NETWORK_CONNECT_TIMEOUT_MS
            readTimeout = NETWORK_READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/geo+json, application/json")
        }

        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("HTTP $code from ArcGIS endpoint")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun buildArcGisQueryUrl(endpoint: String, near: DetectionLocation?): String {
        val builder = Uri.parse(endpoint).buildUpon()
            .appendQueryParameter("where", "1=1")
            .appendQueryParameter("outFields", "*")
            .appendQueryParameter("returnGeometry", "true")
            .appendQueryParameter("outSR", "4326")
            .appendQueryParameter("f", "geojson")

        if (near != null) {
            val latDelta = PUBLIC_RADIUS_MILES / 69.0
            val lonScale = kotlin.math.cos(Math.toRadians(near.lat)).let { if (it < 0.01) 0.01 else it }
            val lonDelta = PUBLIC_RADIUS_MILES / (69.0 * lonScale)
            val minLon = (near.lon - lonDelta).coerceIn(-180.0, 180.0)
            val minLat = (near.lat - latDelta).coerceIn(-90.0, 90.0)
            val maxLon = (near.lon + lonDelta).coerceIn(-180.0, 180.0)
            val maxLat = (near.lat + latDelta).coerceIn(-90.0, 90.0)
            val envelope = String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f", minLon, minLat, maxLon, maxLat)
            builder
                .appendQueryParameter("geometry", envelope)
                .appendQueryParameter("geometryType", "esriGeometryEnvelope")
                .appendQueryParameter("spatialRel", "esriSpatialRelIntersects")
                .appendQueryParameter("inSR", "4326")
        }

        return builder.build().toString()
    }

    private fun parseGeoJson(raw: String): List<NoFlyZonePolygon> {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val features = when (root.optString("type", "").lowercase(Locale.US)) {
            "featurecollection" -> root.optJSONArray("features") ?: JSONArray()
            "feature" -> JSONArray().put(root)
            else -> JSONArray()
        }

        val zones = mutableListOf<NoFlyZonePolygon>()
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val geometry = feature.optJSONObject("geometry") ?: continue
            val properties = feature.optJSONObject("properties") ?: JSONObject()
            val polygons = geometryToRings(geometry)
            if (polygons.isEmpty()) continue

            val label = firstString(
                properties,
                keys = listOf("name", "title", "label", "designator", "site_name", "facility", "identifier")
            ) ?: "No-fly zone"
            val source = firstString(
                properties,
                keys = listOf("source", "provider", "authority")
            ) ?: "GeoJSON"
            val regulationHint = firstString(
                properties,
                keys = listOf("regulation", "rules", "class", "category", "laanc", "type", "restriction")
            )
            val lowerAltitudeFeet = firstInt(
                properties,
                keys = listOf("lower_alt_ft", "floor_ft", "altitude_floor_ft", "loweraltfeet", "lower_limit", "lower")
            )
            val upperAltitudeFeet = firstInt(
                properties,
                keys = listOf("upper_alt_ft", "ceiling_ft", "altitude_ceiling_ft", "upperaltfeet", "upper_limit", "upper", "altitude")
            )

            polygons.forEachIndexed { idx, ring ->
                val normalizedRing = normalizeRing(ring)
                if (normalizedRing.size < 3) return@forEachIndexed
                val suffix = if (polygons.size > 1) "-$idx" else ""
                zones += NoFlyZonePolygon(
                    id = "${label.lowercase(Locale.US).replace(" ", "-")}$suffix",
                    label = label,
                    source = source,
                    regulationHint = regulationHint,
                    lowerAltitudeFeet = lowerAltitudeFeet,
                    upperAltitudeFeet = upperAltitudeFeet,
                    boundary = normalizedRing
                )
            }
        }

        return zones
    }

    private fun geometryToRings(geometry: JSONObject): List<List<DetectionLocation>> {
        val type = geometry.optString("type", "").lowercase(Locale.US)
        val coordinates = geometry.optJSONArray("coordinates") ?: return emptyList()
        return when (type) {
            "polygon" -> parsePolygonCoordinates(coordinates)
            "multipolygon" -> parseMultiPolygonCoordinates(coordinates)
            else -> emptyList()
        }
    }

    private fun parsePolygonCoordinates(coordinates: JSONArray): List<List<DetectionLocation>> {
        if (coordinates.length() == 0) return emptyList()
        val exteriorRing = coordinates.optJSONArray(0) ?: return emptyList()
        val parsedRing = parseRing(exteriorRing)
        return if (parsedRing.size >= 3) listOf(parsedRing) else emptyList()
    }

    private fun parseMultiPolygonCoordinates(coordinates: JSONArray): List<List<DetectionLocation>> {
        val rings = mutableListOf<List<DetectionLocation>>()
        for (i in 0 until coordinates.length()) {
            val polygon = coordinates.optJSONArray(i) ?: continue
            rings += parsePolygonCoordinates(polygon)
        }
        return rings
    }

    private fun parseRing(ring: JSONArray): List<DetectionLocation> {
        val points = mutableListOf<DetectionLocation>()
        for (i in 0 until ring.length()) {
            val pair = ring.optJSONArray(i) ?: continue
            val lon = pair.optDouble(0, Double.NaN)
            val lat = pair.optDouble(1, Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) continue
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            points += DetectionLocation(lat = lat, lon = lon)
        }
        return points
    }

    private fun normalizeRing(ring: List<DetectionLocation>): List<DetectionLocation> {
        if (ring.size < 3) return emptyList()
        val deduped = ring.distinctBy { point ->
            String.format(Locale.US, "%.6f,%.6f", point.lat, point.lon)
        }
        if (deduped.size < 3) return emptyList()

        val first = deduped.first()
        val last = deduped.last()
        return if (first.lat == last.lat && first.lon == last.lon) {
            deduped.dropLast(1)
        } else {
            deduped
        }
    }

    private fun firstString(properties: JSONObject, keys: List<String>): String? {
        val lookup = propertyLookup(properties)
        keys.forEach { key ->
            val raw = lookup[key.lowercase(Locale.US)] ?: return@forEach
            val value = raw.toString().trim()
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun firstInt(properties: JSONObject, keys: List<String>): Int? {
        val lookup = propertyLookup(properties)
        keys.forEach { key ->
            val raw = lookup[key.lowercase(Locale.US)] ?: return@forEach
            val parsed = when (raw) {
                is Number -> raw.toDouble().toInt()
                is String -> raw.trim().toDoubleOrNull()?.toInt()
                else -> null
            }
            if (parsed != null) return parsed
        }
        return null
    }

    private fun propertyLookup(properties: JSONObject): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        val iterator = properties.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            map[key.lowercase(Locale.US)] = properties.opt(key)
        }
        return map
    }
}
