package dev.argus.tracker.sensing

import android.content.Context
import android.util.Log
import dev.argus.tracker.data.chain.MeshPeerSnapshotStore
import dev.argus.tracker.data.SecureSettingsStore
import dev.argus.tracker.data.OperationalErrorLogStore
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import dev.argus.tracker.worker.ScanSettings
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class AviationScanner(
    private val context: Context
) : SignalScanner {
    companion object {
        private const val TAG = "AviationScanner"
        private const val PREFS_NAME = "argus_settings"
        private const val KEY_PUBLIC_FEED_CACHE_URL = "aircraft_public_feed_cache_url"
        private const val KEY_PUBLIC_FEED_CACHE_FETCHED_AT_MS = "aircraft_public_feed_cache_fetched_at_ms"
        private const val KEY_OPENSKY_LAST_RATE_LIMIT_LOG_EPOCH_MS = "opensky_last_rate_limit_log_epoch_ms"
        private const val KEY_OPENSKY_DAY_INDEX_UTC = "opensky_day_index_utc"
        private const val KEY_OPENSKY_REQUEST_COUNT_TODAY = "opensky_request_count_today"
        private const val KEY_OPENSKY_LAST_REQUEST_EPOCH_MS = "opensky_last_request_epoch_ms"
        private const val KEY_OPENSKY_LAST_MESH_SKIP_LOG_EPOCH_MS = "opensky_last_mesh_skip_log_epoch_ms"
        private const val OPENSKY_CACHE_DIR = "aviation"
        private const val OPENSKY_CACHE_FILE_NAME = "opensky_cache.json"
        private const val OPENSKY_SAFE_CACHE_TTL_MS = 4 * 60_000L
        private const val OPENSKY_MIN_REQUEST_INTERVAL_MS = 4 * 60_000L
        private const val OPENSKY_DAILY_REQUEST_BUDGET = 380
        private const val OPENSKY_RATE_LIMIT_LOG_INTERVAL_MS = 15 * 60_000L
        private const val OPENSKY_MESH_GATE_MAX_AGE_MS = 3 * 60_000L
        private const val PUBLIC_FEED_STALE_CACHE_MAX_AGE_MS = 5 * 60_000L
        private const val PUBLIC_FEED_MAX_PAYLOAD_BYTES = 4 * 1024 * 1024
        private const val PUBLIC_FEED_READ_CHUNK_BYTES = 8 * 1024
    }

    private data class CachedPublicFeed(
        val body: String,
        val fetchedAtEpochMs: Long
    )

    private data class ParseOutcome(
        val encounters: List<Encounter>,
        val filteredOutOfRadius: Int,
        val droppedMissingCoordinates: Int
    )

    override suspend fun scanOnce(): List<Encounter> {
        return withContext(Dispatchers.IO) {
            val adsbEnabled = ScanSettings.isAviationAdsbSensorEnabled(context)
            val publicEnabled = ScanSettings.isAviationPublicSensorEnabled(context)
            if (!adsbEnabled && !publicEnabled) {
                OperationalErrorLogStore.append(
                    context = context,
                    category = "SCAN_SKIPPED",
                    source = "aircraft",
                    message = "Aircraft scan skipped: ADS-B and Public Flight Radar sensors are both disabled"
                )
                return@withContext emptyList()
            }

            val location = LocationSnapshotProvider.read(context)
            val now = System.currentTimeMillis()
            val combined = mutableListOf<Encounter>()

            if (adsbEnabled) {
                combined += readAdsbJsonlFeed(location, now)
            }
            if (publicEnabled) {
                combined += fetchPublicAviationFeed(location, now)
            }

            val latestByPrimaryId = LinkedHashMap<String, Encounter>()
            combined.forEach { encounter ->
                val previous = latestByPrimaryId[encounter.primaryId]
                if (previous == null || encounter.timestampEpochMs >= previous.timestampEpochMs) {
                    latestByPrimaryId[encounter.primaryId] = encounter
                }
            }
            latestByPrimaryId.values.toList()
        }
    }

    private fun readAdsbJsonlFeed(location: DetectionLocation?, fallbackNowEpochMs: Long): List<Encounter> {
        val file = context.filesDir.resolve("ingest").resolve("adsb.jsonl")
        if (!file.exists() || !file.isFile) return emptyList()

        val latestByPrimaryId = LinkedHashMap<String, Encounter>()
        runCatching {
            file.bufferedReader().useLines { lines ->
                lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.isEmpty()) return@forEach
                    val payload = runCatching { JSONObject(line) }.getOrNull() ?: return@forEach
                    val encounter = payloadToEncounter(
                        payload = payload,
                        provider = "ADSB_JSONL",
                        fallbackLocation = location,
                        fallbackNowEpochMs = fallbackNowEpochMs
                    ) ?: return@forEach
                    val previous = latestByPrimaryId[encounter.primaryId]
                    if (previous == null || encounter.timestampEpochMs >= previous.timestampEpochMs) {
                        latestByPrimaryId[encounter.primaryId] = encounter
                    }
                }
            }
        }

        return latestByPrimaryId.values.toList()
    }

    private fun fetchPublicAviationFeed(location: DetectionLocation?, fallbackNowEpochMs: Long): List<Encounter> {
        val configured = ScanSettings.getAviationPublicFeedUrl(context)
        if (configured.isBlank()) {
            OperationalErrorLogStore.append(
                context = context,
                category = "CONFIG",
                source = "aircraft_public",
                message = "Public Flight Radar enabled but feed URL is blank"
            )
            return emptyList()
        }

        val requestUrl = resolvePublicFeedUrl(configured)
        val nowEpochMs = System.currentTimeMillis()
        val cached = readPublicFeedCache(requestUrl)
        val freshCachedBody = cached
            ?.takeIf { nowEpochMs - it.fetchedAtEpochMs in 0..OPENSKY_SAFE_CACHE_TTL_MS }
            ?.body

        if (freshCachedBody != null) {
            AviationPerfStatsStore.recordCacheHit(
                context = context,
                source = "cache_fresh",
                payloadBytes = freshCachedBody.length
            )
            return parsePublicFeedBody(
                body = freshCachedBody,
                requestUrl = requestUrl,
                fallbackLocation = location,
                fallbackNowEpochMs = fallbackNowEpochMs,
                fromCache = true,
                emitIngestStatsLogs = false
            )
        }

        val meshGateDecision = evaluateMeshPublicFeedGate(nowEpochMs)
        if (!meshGateDecision.allowed) {
            val staleCachedBody = cached
                ?.takeIf { nowEpochMs - it.fetchedAtEpochMs in 0..PUBLIC_FEED_STALE_CACHE_MAX_AGE_MS }
                ?.body
            AviationPerfStatsStore.recordRateLimitedSkip(context)
            if (shouldEmitMeshGateLog(nowEpochMs)) {
                OperationalErrorLogStore.append(
                    context = context,
                    category = "INGEST_MESH_GATE",
                    source = "aircraft_public",
                    message = meshGateDecision.reason ?: "Public aviation fetch skipped by mesh leader gate"
                )
                markMeshGateLogEmitted(nowEpochMs)
            }
            if (staleCachedBody != null) {
                AviationPerfStatsStore.recordCacheHit(
                    context = context,
                    source = "cache_stale",
                    payloadBytes = staleCachedBody.length
                )
                return parsePublicFeedBody(
                    body = staleCachedBody,
                    requestUrl = requestUrl,
                    fallbackLocation = location,
                    fallbackNowEpochMs = fallbackNowEpochMs,
                    fromCache = true,
                    emitIngestStatsLogs = false
                )
            }
            return emptyList()
        }

        val gateDecision = evaluateOpenSkyRequestGate(nowEpochMs)
        if (!gateDecision.allowed) {
            val staleCachedBody = cached
                ?.takeIf { nowEpochMs - it.fetchedAtEpochMs in 0..PUBLIC_FEED_STALE_CACHE_MAX_AGE_MS }
                ?.body
            AviationPerfStatsStore.recordRateLimitedSkip(context)
            if (shouldEmitRateLimitLog(nowEpochMs)) {
                OperationalErrorLogStore.append(
                    context = context,
                    category = "INGEST_RATE_LIMIT",
                    source = "aircraft_public",
                    message = gateDecision.reason ?: "OpenSky request skipped by rate limiter"
                )
                markRateLimitLogEmitted(nowEpochMs)
            }
            if (staleCachedBody != null) {
                AviationPerfStatsStore.recordCacheHit(
                    context = context,
                    source = "cache_stale",
                    payloadBytes = staleCachedBody.length
                )
                return parsePublicFeedBody(
                    body = staleCachedBody,
                    requestUrl = requestUrl,
                    fallbackLocation = location,
                    fallbackNowEpochMs = fallbackNowEpochMs,
                    fromCache = true,
                    emitIngestStatsLogs = false
                )
            }
            return emptyList()
        }

        val body = runCatching {
            val conn = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Argus/1.0 (Android)")
            }
            conn.readResponseTextChunked(PUBLIC_FEED_MAX_PAYLOAD_BYTES)
        }.getOrElse {
            Log.w(TAG, "Public aviation fetch failed: ${it.message}")
            AviationPerfStatsStore.recordHttpFailure(context)
            val staleCachedBody = cached
                ?.takeIf { nowEpochMs - it.fetchedAtEpochMs in 0..PUBLIC_FEED_STALE_CACHE_MAX_AGE_MS }
                ?.body
            if (staleCachedBody != null) {
                AviationPerfStatsStore.recordCacheHit(
                    context = context,
                    source = "cache_stale",
                    payloadBytes = staleCachedBody.length
                )
                OperationalErrorLogStore.append(
                    context = context,
                    category = "INGEST_HTTP",
                    source = "aircraft_public",
                    message = "Fetch failed for $requestUrl; using cached payload (${(nowEpochMs - cached.fetchedAtEpochMs) / 1000}s old)"
                )
                return parsePublicFeedBody(
                    body = staleCachedBody,
                    requestUrl = requestUrl,
                    fallbackLocation = location,
                    fallbackNowEpochMs = fallbackNowEpochMs,
                    fromCache = true,
                    emitIngestStatsLogs = false
                )
            }
            OperationalErrorLogStore.append(
                context = context,
                category = "INGEST_HTTP",
                source = "aircraft_public",
                message = "Fetch failed for $requestUrl: ${it.message ?: "unknown error"}"
            )
            return emptyList()
        }

        markOpenSkyRequestMade(nowEpochMs)
        AviationPerfStatsStore.recordNetworkFetch(context = context, payloadBytes = body.length)

        writePublicFeedCache(requestUrl, body, nowEpochMs)
        return parsePublicFeedBody(
            body = body,
            requestUrl = requestUrl,
            fallbackLocation = location,
            fallbackNowEpochMs = fallbackNowEpochMs,
            fromCache = false,
            emitIngestStatsLogs = true
        )
    }

    private fun parsePublicFeedBody(
        body: String,
        requestUrl: String,
        fallbackLocation: DetectionLocation?,
        fallbackNowEpochMs: Long,
        fromCache: Boolean,
        emitIngestStatsLogs: Boolean
    ): List<Encounter> {
        val parseStartedAt = System.currentTimeMillis()

        val rootNode = runCatching { JSONTokener(body).nextValue() }.getOrElse {
            Log.w(TAG, "Public aviation payload is not valid JSON: ${it.message}")
            AviationPerfStatsStore.recordParseFailure(context)
            OperationalErrorLogStore.append(
                context = context,
                category = "INGEST_PARSE",
                source = "aircraft_public",
                message = "Invalid JSON payload from $requestUrl${if (fromCache) " (cache)" else ""}"
            )
            return emptyList()
        }

        val outcome = when (rootNode) {
            is JSONObject -> parseRootObject(
                root = rootNode,
                fallbackLocation = fallbackLocation,
                fallbackNowEpochMs = fallbackNowEpochMs,
                emitIngestStatsLogs = emitIngestStatsLogs
            )
            is JSONArray -> parseObjectArray(
                rows = rootNode,
                provider = "PUBLIC_RADAR",
                fallbackLocation = fallbackLocation,
                fallbackNowEpochMs = fallbackNowEpochMs,
                emitIngestStatsLogs = emitIngestStatsLogs
            )
            else -> {
                Log.w(TAG, "Public aviation payload has unsupported JSON root type")
                AviationPerfStatsStore.recordParseFailure(context)
                OperationalErrorLogStore.append(
                    context = context,
                    category = "INGEST_PARSE",
                    source = "aircraft_public",
                    message = "Unsupported JSON root type from $requestUrl${if (fromCache) " (cache)" else ""}"
                )
                ParseOutcome(emptyList(), filteredOutOfRadius = 0, droppedMissingCoordinates = 0)
            }
        }

        val parseDurationMs = (System.currentTimeMillis() - parseStartedAt).coerceAtLeast(0L)
        AviationPerfStatsStore.recordParseResult(
            context = context,
            parsedAircraftCount = outcome.encounters.size,
            filteredOutOfRadius = outcome.filteredOutOfRadius,
            droppedMissingCoordinates = outcome.droppedMissingCoordinates,
            parseDurationMs = parseDurationMs,
            source = if (fromCache) "cache_parse" else "network_parse"
        )

        if (outcome.encounters.isEmpty()) {
            OperationalErrorLogStore.append(
                context = context,
                category = "INGEST_EMPTY",
                source = "aircraft_public",
                message = "Public aviation feed returned no usable aircraft records${if (fromCache) " (cache)" else ""}"
            )
        }
        return outcome.encounters
    }

    private fun parseRootObject(
        root: JSONObject,
        fallbackLocation: DetectionLocation?,
        fallbackNowEpochMs: Long,
        emitIngestStatsLogs: Boolean
    ): ParseOutcome {
        val states = root.optJSONArray("states")
        if (states != null) {
            return parseOpenSkyStates(
                states = states,
                fallbackLocation = fallbackLocation,
                fallbackNowEpochMs = fallbackNowEpochMs,
                emitIngestStatsLogs = emitIngestStatsLogs
            )
        }

        val objectsArray = root.optJSONArray("aircraft")
            ?: root.optJSONArray("results")
            ?: root.optJSONArray("data")
            ?: root.optJSONArray("encounters")
            ?: root.optJSONObject("response")?.optJSONArray("aircraft")
            ?: root.optJSONObject("result")?.optJSONArray("aircraft")
            ?: root.optJSONObject("data")?.optJSONArray("aircraft")
            ?: root.optJSONObject("payload")?.optJSONArray("aircraft")

        if (objectsArray == null) {
            Log.w(TAG, "Public aviation JSON object missing states/aircraft/results/data arrays")
            OperationalErrorLogStore.append(
                context = context,
                category = "INGEST_PARSE",
                source = "aircraft_public",
                message = "No states/aircraft/results/data arrays found in response"
            )
            AviationPerfStatsStore.recordParseFailure(context)
            return ParseOutcome(emptyList(), filteredOutOfRadius = 0, droppedMissingCoordinates = 0)
        }

        return parseObjectArray(
            rows = objectsArray,
            provider = "PUBLIC_RADAR",
            fallbackLocation = fallbackLocation,
            fallbackNowEpochMs = fallbackNowEpochMs,
            emitIngestStatsLogs = emitIngestStatsLogs
        )
    }

    private fun parseOpenSkyStates(
        states: JSONArray,
        fallbackLocation: DetectionLocation?,
        fallbackNowEpochMs: Long,
        emitIngestStatsLogs: Boolean
    ): ParseOutcome {
        val latestByPrimaryId = LinkedHashMap<String, Encounter>()
        val radiusMiles = ScanSettings.getAviationPublicRadiusMiles(context).coerceIn(10, 300)
        val radiusMeters = radiusMiles * 1609.344
        var skippedOutOfRadius = 0
        var droppedMissingCoordinates = 0
        for (i in 0 until states.length()) {
            val row = states.optJSONArray(i) ?: continue
            val rowLon = row.optDoubleOrNull(5)
            val rowLat = row.optDoubleOrNull(6)
            if (rowLat == null || rowLon == null) {
                droppedMissingCoordinates += 1
                continue
            }
            if (fallbackLocation != null && rowLat != null && rowLon != null) {
                val distanceMeters = distanceMeters(
                    lat1 = fallbackLocation.lat,
                    lon1 = fallbackLocation.lon,
                    lat2 = rowLat,
                    lon2 = rowLon
                )
                if (distanceMeters > radiusMeters) {
                    skippedOutOfRadius += 1
                    continue
                }
            }
            val payload = JSONObject().apply {
                put("icao24", row.optString(0).trim())
                put("callsign", row.optString(1).trim())
                put("originCountry", row.optString(2).trim())
                put("timePositionSeconds", row.optLongOrNull(3))
                put("lastContactSeconds", row.optLongOrNull(4))
                put("lon", rowLon)
                put("lat", rowLat)
                put("baroAltitudeMeters", row.optDoubleOrNull(7))
                put("onGround", row.optBoolean(8))
                put("speedMetersPerSecond", row.optDoubleOrNull(9))
                put("headingDegrees", row.optDoubleOrNull(10))
                put("verticalRateMps", row.optDoubleOrNull(11))
                put("geoAltitudeMeters", row.optDoubleOrNull(13))
                put("squawk", row.optString(14).trim())
            }
            val encounter = payloadToEncounter(
                payload = payload,
                provider = "PUBLIC_OPENSKY",
                fallbackLocation = fallbackLocation,
                fallbackNowEpochMs = fallbackNowEpochMs
            )
            if (encounter != null) {
                val previous = latestByPrimaryId[encounter.primaryId]
                if (previous == null || encounter.timestampEpochMs >= previous.timestampEpochMs) {
                    latestByPrimaryId[encounter.primaryId] = encounter
                }
            }
        }
        if (emitIngestStatsLogs && skippedOutOfRadius > 0) {
            OperationalErrorLogStore.append(
                context = context,
                category = "INGEST_FILTERED",
                source = "aircraft_public",
                message = "OpenSky filtered out $skippedOutOfRadius aircraft outside ${radiusMiles}mi radius",
                severity = "WARNING"
            )
        }
        return ParseOutcome(
            encounters = latestByPrimaryId.values.toList(),
            filteredOutOfRadius = skippedOutOfRadius,
            droppedMissingCoordinates = droppedMissingCoordinates
        )
    }

    private fun parseObjectArray(
        rows: JSONArray,
        provider: String,
        fallbackLocation: DetectionLocation?,
        fallbackNowEpochMs: Long,
        emitIngestStatsLogs: Boolean
    ): ParseOutcome {
        val latestByPrimaryId = LinkedHashMap<String, Encounter>()
        val radiusMiles = ScanSettings.getAviationPublicRadiusMiles(context).coerceIn(10, 300)
        val radiusMeters = radiusMiles * 1609.344
        var skippedOutOfRadius = 0
        var droppedMissingCoordinates = 0
        for (i in 0 until rows.length()) {
            val payload = rows.optJSONObject(i) ?: continue
            val lat = payload.optDoubleOrNull("lat")
                ?: payload.optDoubleOrNull("latitude")
            val lon = payload.optDoubleOrNull("lon")
                ?: payload.optDoubleOrNull("lng")
                ?: payload.optDoubleOrNull("longitude")
            if (lat == null || lon == null) {
                droppedMissingCoordinates += 1
                continue
            }
            if (fallbackLocation != null && lat != null && lon != null) {
                val distanceMeters = distanceMeters(
                    lat1 = fallbackLocation.lat,
                    lon1 = fallbackLocation.lon,
                    lat2 = lat,
                    lon2 = lon
                )
                if (distanceMeters > radiusMeters) {
                    skippedOutOfRadius += 1
                    continue
                }
            }
            val encounter = payloadToEncounter(
                payload = payload,
                provider = provider,
                fallbackLocation = fallbackLocation,
                fallbackNowEpochMs = fallbackNowEpochMs
            )
            if (encounter != null) {
                val previous = latestByPrimaryId[encounter.primaryId]
                if (previous == null || encounter.timestampEpochMs >= previous.timestampEpochMs) {
                    latestByPrimaryId[encounter.primaryId] = encounter
                }
            }
        }
        if (emitIngestStatsLogs && skippedOutOfRadius > 0) {
            OperationalErrorLogStore.append(
                context = context,
                category = "INGEST_FILTERED",
                source = "aircraft_public",
                message = "$provider filtered out $skippedOutOfRadius aircraft outside ${radiusMiles}mi radius",
                severity = "WARNING"
            )
        }
        return ParseOutcome(
            encounters = latestByPrimaryId.values.toList(),
            filteredOutOfRadius = skippedOutOfRadius,
            droppedMissingCoordinates = droppedMissingCoordinates
        )
    }

    private fun payloadToEncounter(
        payload: JSONObject,
        provider: String,
        fallbackLocation: DetectionLocation?,
        fallbackNowEpochMs: Long
    ): Encounter? {
        val primaryId = payload.optString("icao24", "")
            .ifBlank { payload.optString("hex", "") }
            .ifBlank { payload.optString("id", "") }
            .ifBlank { payload.optString("primaryId", "") }
            .trim()
            .lowercase()
            .ifBlank { return null }

        val secondaryId = payload.optString("callsign", "")
            .ifBlank { payload.optString("flight", "") }
            .ifBlank { payload.optString("registration", "") }
            .ifBlank { payload.optString("label", "") }
            .trim()
            .ifBlank { null }

        val altitudeMeters = payload.optDoubleOrNull("baroAltitudeMeters")
            ?: payload.optDoubleOrNull("geoAltitudeMeters")
            ?: payload.optDoubleOrNull("altitudeMeters")
            ?: payload.optDoubleOrNull("alt_baro")
            ?: payload.optDoubleOrNull("alt_geom")
            ?: payload.optDoubleOrNull("altitude")

        val speedMetersPerSecond = payload.optDoubleOrNull("speedMetersPerSecond")
            ?: payload.optDoubleOrNull("gs")
            ?: payload.optDoubleOrNull("speed")

        val headingDegrees = payload.optDoubleOrNull("headingDegrees")
            ?: payload.optDoubleOrNull("track")
            ?: payload.optDoubleOrNull("trueTrack")

        val lat = payload.optDoubleOrNull("lat")
            ?: payload.optDoubleOrNull("latitude")
        val lon = payload.optDoubleOrNull("lon")
            ?: payload.optDoubleOrNull("lng")
            ?: payload.optDoubleOrNull("longitude")
        val resolvedLat = lat
        val resolvedLon = lon

        val timestampEpochMs = payload.optLongOrNull("timestampEpochMs")
            ?: payload.optLongOrNull("lastContactEpochMs")
            ?: payload.optLongOrNull("lastContactSeconds")?.times(1000L)
            ?: payload.optLongOrNull("timePositionSeconds")?.times(1000L)
            ?: fallbackNowEpochMs

        val aircraftType = classifyAircraftType(payload)

        val normalizedPayload = JSONObject(payload.toString()).apply {
            put("aviationSchema", "argus.aviation.v1")
            put("aviationParserVersion", "1.0")
            put("provider", provider)
            put("deviceClassHint", "aircraft")
            put("aircraftTypeHint", aircraftType)
            put("icao24", primaryId)
            put("callsign", secondaryId)
            put("lat", resolvedLat)
            put("lon", resolvedLon)
            put("altitudeMeters", altitudeMeters)
            put("speedMetersPerSecond", speedMetersPerSecond)
            put("headingDegrees", headingDegrees)
            put("sourceInternet", provider.startsWith("PUBLIC_"))
        }

        return Encounter(
            timestampEpochMs = timestampEpochMs,
            source = EncounterSource.AIRCRAFT,
            primaryId = primaryId,
            secondaryId = secondaryId,
            rssiDbm = null,
            frequencyMhz = null,
            lat = resolvedLat,
            lon = resolvedLon,
            rawPayloadJson = normalizedPayload.toString()
        )
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2.0) * kotlin.math.sin(dLat / 2.0) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2.0) * kotlin.math.sin(dLon / 2.0)
        val c = 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
        return earthRadiusMeters * c
    }

    private fun classifyAircraftType(payload: JSONObject): String {
        val rawType = payload.optString("aircraftType", "")
            .ifBlank { payload.optString("type", "") }
            .ifBlank { payload.optString("category", "") }
            .ifBlank { payload.optString("species", "") }
            .trim()
            .lowercase()

        if (rawType.isBlank()) return "aircraft"
        return when {
            rawType.contains("heli") -> "helicopter"
            rawType.contains("glider") -> "glider"
            rawType.contains("balloon") -> "balloon"
            rawType.contains("uav") || rawType.contains("drone") -> "uas"
            rawType.contains("jet") -> "jet"
            rawType.contains("prop") || rawType.contains("turboprop") -> "prop"
            else -> rawType
        }
    }

    private fun resolvePublicFeedUrl(base: String): String {
        val trimmed = base.trim()
        if (trimmed.isEmpty()) return trimmed
        return trimmed
    }

    private fun readPublicFeedCache(requestUrl: String): CachedPublicFeed? {
        if (!requestUrl.contains("opensky-network.org")) return null
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val cachedUrl = prefs.getString(KEY_PUBLIC_FEED_CACHE_URL, "").orEmpty()
        if (cachedUrl != requestUrl) return null
        val cacheFile = context.cacheDir.resolve(OPENSKY_CACHE_DIR).resolve(OPENSKY_CACHE_FILE_NAME)
        val body = runCatching { cacheFile.takeIf { it.exists() && it.isFile }?.readText().orEmpty() }
            .getOrDefault("")
        if (body.isBlank()) return null
        val fetchedAt = prefs.getLong(KEY_PUBLIC_FEED_CACHE_FETCHED_AT_MS, 0L)
        if (fetchedAt <= 0L) return null
        return CachedPublicFeed(body = body, fetchedAtEpochMs = fetchedAt)
    }

    private fun writePublicFeedCache(requestUrl: String, body: String, fetchedAtEpochMs: Long) {
        if (!requestUrl.contains("opensky-network.org")) return
        val cacheDir = context.cacheDir.resolve(OPENSKY_CACHE_DIR)
        runCatching { cacheDir.mkdirs() }
        val cacheFile = cacheDir.resolve(OPENSKY_CACHE_FILE_NAME)
        runCatching { cacheFile.writeText(body) }
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_PUBLIC_FEED_CACHE_URL, requestUrl)
            .putLong(KEY_PUBLIC_FEED_CACHE_FETCHED_AT_MS, fetchedAtEpochMs)
            .apply()
    }

    private data class RequestGateDecision(
        val allowed: Boolean,
        val reason: String? = null
    )

    private fun evaluateMeshPublicFeedGate(nowEpochMs: Long): RequestGateDecision {
        if (!ScanSettings.isChainLinkEnabled(context)) {
            return RequestGateDecision(allowed = true)
        }
        if (ScanSettings.getChainSharedSecret(context).isBlank()) {
            return RequestGateDecision(allowed = true)
        }

        val selfNodeId = ScanSettings.getChainNodeId(context).trim()
        if (selfNodeId.isBlank()) {
            return RequestGateDecision(allowed = true)
        }

        val snapshot = MeshPeerSnapshotStore.read(context)
            ?: return RequestGateDecision(allowed = true)
        if (nowEpochMs - snapshot.lastUpdatedEpochMs > OPENSKY_MESH_GATE_MAX_AGE_MS) {
            return RequestGateDecision(allowed = true)
        }

        val connectedPeers = snapshot.connectedPeerNodeIds
        if (connectedPeers.isEmpty()) {
            return RequestGateDecision(allowed = true)
        }

        val contenders = (connectedPeers + selfNodeId)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val electedLeader = contenders.firstOrNull()
            ?: return RequestGateDecision(allowed = true)

        if (electedLeader == selfNodeId) {
            return RequestGateDecision(allowed = true)
        }

        return RequestGateDecision(
            allowed = false,
            reason = "Mesh gate active: node $selfNodeId skipped OpenSky request; elected leader is $electedLeader"
        )
    }

    private fun evaluateOpenSkyRequestGate(nowEpochMs: Long): RequestGateDecision {
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val dayIndexUtc = nowEpochMs / 86_400_000L
        val storedDayIndexUtc = prefs.getLong(KEY_OPENSKY_DAY_INDEX_UTC, dayIndexUtc)
        val requestsToday = if (storedDayIndexUtc == dayIndexUtc) {
            prefs.getInt(KEY_OPENSKY_REQUEST_COUNT_TODAY, 0)
        } else {
            0
        }
        val lastRequestEpochMs = if (storedDayIndexUtc == dayIndexUtc) {
            prefs.getLong(KEY_OPENSKY_LAST_REQUEST_EPOCH_MS, 0L)
        } else {
            0L
        }

        if (requestsToday >= OPENSKY_DAILY_REQUEST_BUDGET) {
            return RequestGateDecision(
                allowed = false,
                reason = "OpenSky budget reached: $requestsToday/$OPENSKY_DAILY_REQUEST_BUDGET requests today"
            )
        }

        if (lastRequestEpochMs > 0L) {
            val elapsedMs = nowEpochMs - lastRequestEpochMs
            if (elapsedMs in 0 until OPENSKY_MIN_REQUEST_INTERVAL_MS) {
                val waitMinutes = ((OPENSKY_MIN_REQUEST_INTERVAL_MS - elapsedMs + 59_999L) / 60_000L).coerceAtLeast(1L)
                return RequestGateDecision(
                    allowed = false,
                    reason = "OpenSky pacing active: wait about ${waitMinutes}m before next request"
                )
            }
        }

        return RequestGateDecision(allowed = true)
    }

    private fun markOpenSkyRequestMade(nowEpochMs: Long) {
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val dayIndexUtc = nowEpochMs / 86_400_000L
        val storedDayIndexUtc = prefs.getLong(KEY_OPENSKY_DAY_INDEX_UTC, dayIndexUtc)
        val previousCount = if (storedDayIndexUtc == dayIndexUtc) {
            prefs.getInt(KEY_OPENSKY_REQUEST_COUNT_TODAY, 0)
        } else {
            0
        }
        val updatedCount = (previousCount + 1).coerceAtLeast(1)

        prefs.edit()
            .putLong(KEY_OPENSKY_DAY_INDEX_UTC, dayIndexUtc)
            .putInt(KEY_OPENSKY_REQUEST_COUNT_TODAY, updatedCount)
            .putLong(KEY_OPENSKY_LAST_REQUEST_EPOCH_MS, nowEpochMs)
            .apply()
    }

    private fun shouldEmitRateLimitLog(nowEpochMs: Long): Boolean {
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val lastLoggedAt = prefs.getLong(KEY_OPENSKY_LAST_RATE_LIMIT_LOG_EPOCH_MS, 0L)
        if (lastLoggedAt <= 0L) return true
        return nowEpochMs - lastLoggedAt >= OPENSKY_RATE_LIMIT_LOG_INTERVAL_MS
    }

    private fun markRateLimitLogEmitted(nowEpochMs: Long) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putLong(KEY_OPENSKY_LAST_RATE_LIMIT_LOG_EPOCH_MS, nowEpochMs)
            .apply()
    }

    private fun shouldEmitMeshGateLog(nowEpochMs: Long): Boolean {
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val lastLoggedAt = prefs.getLong(KEY_OPENSKY_LAST_MESH_SKIP_LOG_EPOCH_MS, 0L)
        if (lastLoggedAt <= 0L) return true
        return nowEpochMs - lastLoggedAt >= OPENSKY_RATE_LIMIT_LOG_INTERVAL_MS
    }

    private fun markMeshGateLogEmitted(nowEpochMs: Long) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putLong(KEY_OPENSKY_LAST_MESH_SKIP_LOG_EPOCH_MS, nowEpochMs)
            .apply()
    }

    private fun HttpURLConnection.readResponseTextChunked(maxBytes: Int): String {
        return try {
            val code = responseCode
            val stream = if (code in 200..299) inputStream else errorStream
            val text = stream?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(PUBLIC_FEED_READ_CHUNK_BYTES)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) {
                        throw IllegalStateException("Payload too large: $total bytes (max $maxBytes)")
                    }
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code ${responseMessage ?: ""} ${text.take(180)}")
            }
            text
        } finally {
            disconnect()
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = optDouble(key, Double.NaN)
        return value.takeIf { it.isFinite() }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return optLong(key)
    }

    private fun JSONArray.optLongOrNull(index: Int): Long? {
        if (isNull(index)) return null
        val value = optLong(index, Long.MIN_VALUE)
        return value.takeIf { it != Long.MIN_VALUE }
    }

    private fun JSONArray.optDoubleOrNull(index: Int): Double? {
        if (isNull(index)) return null
        val value = optDouble(index, Double.NaN)
        return value.takeIf { it.isFinite() }
    }
}


