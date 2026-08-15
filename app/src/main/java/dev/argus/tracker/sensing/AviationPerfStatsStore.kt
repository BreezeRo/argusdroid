package dev.argus.tracker.sensing

import android.content.Context

data class AviationPerfSnapshot(
    val lastUpdatedEpochMs: Long,
    val lastSource: String,
    val lastPayloadBytes: Int,
    val lastParsedAircraftCount: Int,
    val lastFilteredOutOfRadius: Int,
    val lastParseDurationMs: Long,
    val cacheHits: Int,
    val networkFetches: Int,
    val rateLimitedSkips: Int,
    val httpFailures: Int,
    val parseFailures: Int
)

object AviationPerfStatsStore {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_LAST_UPDATED_EPOCH_MS = "aviation_perf_last_updated_epoch_ms"
    private const val KEY_LAST_SOURCE = "aviation_perf_last_source"
    private const val KEY_LAST_PAYLOAD_BYTES = "aviation_perf_last_payload_bytes"
    private const val KEY_LAST_PARSED_AIRCRAFT_COUNT = "aviation_perf_last_parsed_aircraft_count"
    private const val KEY_LAST_FILTERED_OUT_OF_RADIUS = "aviation_perf_last_filtered_out_of_radius"
    private const val KEY_LAST_PARSE_DURATION_MS = "aviation_perf_last_parse_duration_ms"
    private const val KEY_CACHE_HITS = "aviation_perf_cache_hits"
    private const val KEY_NETWORK_FETCHES = "aviation_perf_network_fetches"
    private const val KEY_RATE_LIMITED_SKIPS = "aviation_perf_rate_limited_skips"
    private const val KEY_HTTP_FAILURES = "aviation_perf_http_failures"
    private const val KEY_PARSE_FAILURES = "aviation_perf_parse_failures"

    fun snapshot(context: Context): AviationPerfSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AviationPerfSnapshot(
            lastUpdatedEpochMs = prefs.getLong(KEY_LAST_UPDATED_EPOCH_MS, 0L),
            lastSource = prefs.getString(KEY_LAST_SOURCE, "none").orEmpty(),
            lastPayloadBytes = prefs.getInt(KEY_LAST_PAYLOAD_BYTES, 0),
            lastParsedAircraftCount = prefs.getInt(KEY_LAST_PARSED_AIRCRAFT_COUNT, 0),
            lastFilteredOutOfRadius = prefs.getInt(KEY_LAST_FILTERED_OUT_OF_RADIUS, 0),
            lastParseDurationMs = prefs.getLong(KEY_LAST_PARSE_DURATION_MS, 0L),
            cacheHits = prefs.getInt(KEY_CACHE_HITS, 0),
            networkFetches = prefs.getInt(KEY_NETWORK_FETCHES, 0),
            rateLimitedSkips = prefs.getInt(KEY_RATE_LIMITED_SKIPS, 0),
            httpFailures = prefs.getInt(KEY_HTTP_FAILURES, 0),
            parseFailures = prefs.getInt(KEY_PARSE_FAILURES, 0)
        )
    }

    fun recordCacheHit(context: Context, source: String, payloadBytes: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentHits = prefs.getInt(KEY_CACHE_HITS, 0)
        prefs.edit()
            .putLong(KEY_LAST_UPDATED_EPOCH_MS, System.currentTimeMillis())
            .putString(KEY_LAST_SOURCE, source)
            .putInt(KEY_LAST_PAYLOAD_BYTES, payloadBytes.coerceAtLeast(0))
            .putInt(KEY_CACHE_HITS, currentHits + 1)
            .apply()
    }

    fun recordNetworkFetch(context: Context, payloadBytes: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt(KEY_NETWORK_FETCHES, 0)
        prefs.edit()
            .putLong(KEY_LAST_UPDATED_EPOCH_MS, System.currentTimeMillis())
            .putString(KEY_LAST_SOURCE, "network")
            .putInt(KEY_LAST_PAYLOAD_BYTES, payloadBytes.coerceAtLeast(0))
            .putInt(KEY_NETWORK_FETCHES, currentCount + 1)
            .apply()
    }

    fun recordRateLimitedSkip(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt(KEY_RATE_LIMITED_SKIPS, 0)
        prefs.edit()
            .putLong(KEY_LAST_UPDATED_EPOCH_MS, System.currentTimeMillis())
            .putString(KEY_LAST_SOURCE, "rate_limited")
            .putInt(KEY_RATE_LIMITED_SKIPS, currentCount + 1)
            .apply()
    }

    fun recordHttpFailure(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt(KEY_HTTP_FAILURES, 0)
        prefs.edit()
            .putLong(KEY_LAST_UPDATED_EPOCH_MS, System.currentTimeMillis())
            .putString(KEY_LAST_SOURCE, "http_failure")
            .putInt(KEY_HTTP_FAILURES, currentCount + 1)
            .apply()
    }

    fun recordParseFailure(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt(KEY_PARSE_FAILURES, 0)
        prefs.edit()
            .putLong(KEY_LAST_UPDATED_EPOCH_MS, System.currentTimeMillis())
            .putString(KEY_LAST_SOURCE, "parse_failure")
            .putInt(KEY_PARSE_FAILURES, currentCount + 1)
            .apply()
    }

    fun recordParseResult(
        context: Context,
        parsedAircraftCount: Int,
        filteredOutOfRadius: Int,
        parseDurationMs: Long,
        source: String
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_UPDATED_EPOCH_MS, System.currentTimeMillis())
            .putString(KEY_LAST_SOURCE, source)
            .putInt(KEY_LAST_PARSED_AIRCRAFT_COUNT, parsedAircraftCount.coerceAtLeast(0))
            .putInt(KEY_LAST_FILTERED_OUT_OF_RADIUS, filteredOutOfRadius.coerceAtLeast(0))
            .putLong(KEY_LAST_PARSE_DURATION_MS, parseDurationMs.coerceAtLeast(0L))
            .apply()
    }
}
