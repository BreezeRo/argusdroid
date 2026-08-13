package dev.argus.tracker.worker

import android.content.Context
import android.os.Build
import java.util.UUID

object ScanSettings {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_SCAN_INTERVAL_SECONDS = "scan_interval_seconds"
    private const val LEGACY_KEY_SCAN_INTERVAL_MINUTES = "scan_interval_minutes"
    private const val KEY_TRACKING_ENABLED = "tracking_enabled"
    private const val KEY_SENSOR_WIFI_ENABLED = "sensor_wifi_enabled"
    private const val KEY_SENSOR_BLE_ENABLED = "sensor_ble_enabled"
    private const val KEY_SENSOR_CELLULAR_ENABLED = "sensor_cellular_enabled"
    private const val KEY_SENSOR_REMOTE_ID_ENABLED = "sensor_remote_id_enabled"
    private const val KEY_APPROACH_DETECTION_ENABLED = "approach_detection_enabled"
    private const val KEY_APPROACH_NOTIFICATIONS_ENABLED = "approach_notifications_enabled"
    private const val KEY_TRACKER_NOTIFICATIONS_ENABLED = "tracker_notifications_enabled"
    private const val KEY_CHAIN_LINK_ENABLED = "chain_link_enabled"
    private const val KEY_CHAIN_NODE_ID = "chain_node_id"
    private const val KEY_CHAIN_SYNC_WINDOW_MINUTES = "chain_sync_window_minutes"
    private const val KEY_CHAIN_SHARED_SECRET = "chain_shared_secret"
    private const val KEY_CHAIN_AUTO_SYNC_ENABLED = "chain_auto_sync_enabled"
    private const val KEY_CHAIN_AUTO_SYNC_INTERVAL_SECONDS = "chain_auto_sync_interval_seconds"
    private const val KEY_CHAIN_PERSISTENT_CHANNEL_ENABLED = "chain_persistent_channel_enabled"
    private const val KEY_CHAIN_HEARTBEAT_INTERVAL_SECONDS = "chain_heartbeat_interval_seconds"
    private const val KEY_CHAIN_DEVICE_NAME = "chain_device_name"
    private const val KEY_CHAIN_SHARE_PRECISE_LOCATION_ENABLED = "chain_share_precise_location_enabled"
    private const val KEY_EVASION_PROFILE = "evasion_profile"
    private const val KEY_EVASION_AUTO_ESCALATE_ENABLED = "evasion_auto_escalate_enabled"
    private const val KEY_EVASION_AUTO_ESCALATE_DURATION_SECONDS = "evasion_auto_escalate_duration_seconds"
    private const val KEY_EVASION_JITTER_ENABLED = "evasion_jitter_enabled"
    private const val KEY_EVASION_JITTER_PERCENT = "evasion_jitter_percent"
    private const val KEY_EVASION_BURST_ENABLED = "evasion_burst_enabled"
    private const val KEY_EVASION_BURST_WATCH_SECONDS = "evasion_burst_watch_seconds"
    private const val KEY_EVASION_BURST_COOLDOWN_SECONDS = "evasion_burst_cooldown_seconds"
    private const val KEY_EVASION_ACTION_LOG = "evasion_action_log"
    private const val KEY_LIVE_MAP_UPDATE_INTERVAL_SECONDS = "live_map_update_interval_seconds"
    private const val KEY_LAST_SCAN_DURATION_MS = "last_scan_duration_ms"
    private const val KEY_AUTO_ADJUST_SCAN_INTERVAL_ENABLED = "auto_adjust_scan_interval_enabled"
    private const val KEY_SCAN_INTERVAL_CHANGE_EVENTS = "scan_interval_change_events"
    private const val KEY_SOURCE_TIMING_COUNT_SUFFIX = "_scan_timing_count"
    private const val KEY_SOURCE_TIMING_TOTAL_MS_SUFFIX = "_scan_timing_total_ms"
    private const val KEY_SOURCE_TIMING_MAX_MS_SUFFIX = "_scan_timing_max_ms"
    private const val KEY_SOURCE_TIMING_LAST_MS_SUFFIX = "_scan_timing_last_ms"
    private const val KEY_SOURCE_TIMING_WINDOW_SUFFIX = "_scan_timing_window"
    private const val KEY_SOURCE_SCAN_INTERVAL_SECONDS_SUFFIX = "_scan_interval_seconds"
    private const val KEY_SOURCE_LAST_SCAN_EPOCH_MS_SUFFIX = "_last_scan_epoch_ms"
    private const val SOURCE_TIMING_WINDOW_SIZE = 120
    private const val MAX_INTERVAL_CHANGE_EVENTS = 50
    private const val MAX_EVASION_ACTION_LOG_ENTRIES = 80
    const val DEFAULT_SCAN_INTERVAL_SECONDS = 15L * 60L
    const val MIN_PERIODIC_INTERVAL_SECONDS = 15L * 60L
    const val DEFAULT_CHAIN_SYNC_WINDOW_MINUTES = 120L
    const val DEFAULT_CHAIN_AUTO_SYNC_INTERVAL_SECONDS = 60L
    const val DEFAULT_CHAIN_HEARTBEAT_INTERVAL_SECONDS = 20L
    const val DEFAULT_LIVE_MAP_UPDATE_INTERVAL_SECONDS = 5L
    const val DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS = 5L
    const val DEFAULT_EVASION_PROFILE = "BALANCED"
    const val DEFAULT_EVASION_AUTO_ESCALATE_DURATION_SECONDS = 300L
    const val DEFAULT_EVASION_JITTER_PERCENT = 15
    const val DEFAULT_EVASION_BURST_WATCH_SECONDS = 45L
    const val DEFAULT_EVASION_BURST_COOLDOWN_SECONDS = 300L
    const val MIN_SOURCE_SCAN_INTERVAL_SECONDS = 1L
    const val MAX_SOURCE_SCAN_INTERVAL_SECONDS = 3600L
    val ALLOWED_INTERVALS_SECONDS = listOf(1L, 3L, 5L, 15L, 30L, 60L, 5L * 60L, 15L * 60L, 30L * 60L, 60L * 60L)
    val ALLOWED_CHAIN_AUTO_SYNC_INTERVAL_SECONDS = listOf(15L, 30L, 60L, 120L, 300L, 600L)
    val ALLOWED_CHAIN_HEARTBEAT_INTERVAL_SECONDS = listOf(10L, 15L, 20L, 30L, 60L)
    val ALLOWED_LIVE_MAP_UPDATE_INTERVAL_SECONDS = listOf(1L, 3L, 5L, 15L, 30L, 60L, 300L, 1800L, 3600L)
    val ALLOWED_EVASION_PROFILES = listOf("QUIET", "BALANCED", "WATCH")
    val ALLOWED_EVASION_ESCALATE_DURATION_SECONDS = listOf(60L, 180L, 300L, 600L, 900L, 1800L)
    val ALLOWED_EVASION_JITTER_PERCENT = listOf(5, 10, 15, 20, 25, 30)
    val ALLOWED_EVASION_BURST_WATCH_SECONDS = listOf(15L, 30L, 45L, 60L, 90L, 120L)
    val ALLOWED_EVASION_BURST_COOLDOWN_SECONDS = listOf(60L, 120L, 180L, 300L, 600L, 900L)
    val SOURCE_TYPES = listOf(
        "wifi",
        "wifi_direct",
        "ble",
        "bt_classic",
        "cellular",
        "remote_id",
        "uwb",
        "sdr"
    )
    val ALLOWED_SOURCE_SCAN_INTERVAL_SECONDS: List<Long> =
        (1L..60L).toList() + listOf(120L, 300L, 600L, 1800L, 3600L)

    data class SourceScanTiming(
        val sourceType: String,
        val sampleCount: Long,
        val averageDurationMs: Long,
        val maxDurationMs: Long,
        val lastDurationMs: Long,
        val p50DurationMs: Long,
        val p95DurationMs: Long
    )

    data class IntervalChangeEvent(
        val timestampEpochMs: Long,
        val fromSeconds: Long,
        val toSeconds: Long,
        val reason: String
    )

    data class EvasionActionLogEntry(
        val timestampEpochMs: Long,
        val action: String,
        val detail: String
    )

    fun getScanIntervalSeconds(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val configuredSeconds = if (prefs.contains(KEY_SCAN_INTERVAL_SECONDS)) {
            prefs.getLong(KEY_SCAN_INTERVAL_SECONDS, DEFAULT_SCAN_INTERVAL_SECONDS)
        } else {
            // Migrate previous minute-based setting if present.
            val legacyMinutes = prefs.getLong(LEGACY_KEY_SCAN_INTERVAL_MINUTES, 15L)
            legacyMinutes * 60L
        }
        return configuredSeconds.takeIf { it in ALLOWED_INTERVALS_SECONDS }
            ?: DEFAULT_SCAN_INTERVAL_SECONDS
    }

    fun setScanIntervalSeconds(context: Context, seconds: Long) {
        val safeValue = if (seconds in ALLOWED_INTERVALS_SECONDS) {
            seconds
        } else {
            DEFAULT_SCAN_INTERVAL_SECONDS
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SCAN_INTERVAL_SECONDS, safeValue)
            .apply()
    }

    fun isTrackingEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TRACKING_ENABLED, false)

    fun setTrackingEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TRACKING_ENABLED, enabled)
            .apply()
    }

    fun isWifiSensorEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SENSOR_WIFI_ENABLED, true)

    fun setWifiSensorEnabled(context: Context, enabled: Boolean) {
        setSensorEnabled(context, KEY_SENSOR_WIFI_ENABLED, enabled)
    }

    fun isBleSensorEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SENSOR_BLE_ENABLED, true)

    fun setBleSensorEnabled(context: Context, enabled: Boolean) {
        setSensorEnabled(context, KEY_SENSOR_BLE_ENABLED, enabled)
    }

    fun isCellularSensorEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SENSOR_CELLULAR_ENABLED, true)

    fun setCellularSensorEnabled(context: Context, enabled: Boolean) {
        setSensorEnabled(context, KEY_SENSOR_CELLULAR_ENABLED, enabled)
    }

    fun isRemoteIdSensorEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SENSOR_REMOTE_ID_ENABLED, true)

    fun setRemoteIdSensorEnabled(context: Context, enabled: Boolean) {
        setSensorEnabled(context, KEY_SENSOR_REMOTE_ID_ENABLED, enabled)
    }

    fun isApproachDetectionEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_APPROACH_DETECTION_ENABLED, true)

    fun setApproachDetectionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APPROACH_DETECTION_ENABLED, enabled)
            .apply()
    }

    fun isApproachNotificationsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_APPROACH_NOTIFICATIONS_ENABLED, true)

    fun setApproachNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APPROACH_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun isTrackerNotificationsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TRACKER_NOTIFICATIONS_ENABLED, true)

    fun setTrackerNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TRACKER_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun isChainLinkEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CHAIN_LINK_ENABLED, false)

    fun setChainLinkEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CHAIN_LINK_ENABLED, enabled)
            .apply()
    }

    fun getChainNodeId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_CHAIN_NODE_ID, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing
        val generated = "argus-${UUID.randomUUID().toString().take(12)}"
        prefs.edit().putString(KEY_CHAIN_NODE_ID, generated).apply()
        return generated
    }

    fun getChainSyncWindowMinutes(context: Context): Long {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_CHAIN_SYNC_WINDOW_MINUTES, DEFAULT_CHAIN_SYNC_WINDOW_MINUTES)
        return value.coerceIn(15L, 24L * 60L)
    }

    fun setChainSyncWindowMinutes(context: Context, minutes: Long) {
        val safe = minutes.coerceIn(15L, 24L * 60L)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_CHAIN_SYNC_WINDOW_MINUTES, safe)
            .apply()
    }

    fun getChainSharedSecret(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CHAIN_SHARED_SECRET, "")
            .orEmpty()
            .trim()

    fun setChainSharedSecret(context: Context, secret: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CHAIN_SHARED_SECRET, secret.trim())
            .apply()
    }

    fun isChainAutoSyncEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CHAIN_AUTO_SYNC_ENABLED, true)

    fun setChainAutoSyncEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CHAIN_AUTO_SYNC_ENABLED, enabled)
            .apply()
    }

    fun getChainAutoSyncIntervalSeconds(context: Context): Long {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_CHAIN_AUTO_SYNC_INTERVAL_SECONDS, DEFAULT_CHAIN_AUTO_SYNC_INTERVAL_SECONDS)
        return value.takeIf { it in ALLOWED_CHAIN_AUTO_SYNC_INTERVAL_SECONDS }
            ?: DEFAULT_CHAIN_AUTO_SYNC_INTERVAL_SECONDS
    }

    fun setChainAutoSyncIntervalSeconds(context: Context, seconds: Long) {
        val safeValue = if (seconds in ALLOWED_CHAIN_AUTO_SYNC_INTERVAL_SECONDS) {
            seconds
        } else {
            DEFAULT_CHAIN_AUTO_SYNC_INTERVAL_SECONDS
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_CHAIN_AUTO_SYNC_INTERVAL_SECONDS, safeValue)
            .apply()
    }

    fun isChainPersistentChannelEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CHAIN_PERSISTENT_CHANNEL_ENABLED, false)

    fun setChainPersistentChannelEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CHAIN_PERSISTENT_CHANNEL_ENABLED, enabled)
            .apply()
    }

    fun getChainHeartbeatIntervalSeconds(context: Context): Long {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_CHAIN_HEARTBEAT_INTERVAL_SECONDS, DEFAULT_CHAIN_HEARTBEAT_INTERVAL_SECONDS)
        return value.takeIf { it in ALLOWED_CHAIN_HEARTBEAT_INTERVAL_SECONDS }
            ?: DEFAULT_CHAIN_HEARTBEAT_INTERVAL_SECONDS
    }

    fun setChainHeartbeatIntervalSeconds(context: Context, seconds: Long) {
        val safeValue = if (seconds in ALLOWED_CHAIN_HEARTBEAT_INTERVAL_SECONDS) {
            seconds
        } else {
            DEFAULT_CHAIN_HEARTBEAT_INTERVAL_SECONDS
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_CHAIN_HEARTBEAT_INTERVAL_SECONDS, safeValue)
            .apply()
    }

    fun getChainDeviceName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_CHAIN_DEVICE_NAME, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing

        val defaultName = Build.MODEL.take(40).ifBlank { "Argus Device" }
        prefs.edit().putString(KEY_CHAIN_DEVICE_NAME, defaultName).apply()
        return defaultName
    }

    fun setChainDeviceName(context: Context, name: String) {
        val normalized = name.trim().ifBlank { "Argus Device" }.take(40)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CHAIN_DEVICE_NAME, normalized)
            .apply()
    }

    fun isChainSharePreciseLocationEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CHAIN_SHARE_PRECISE_LOCATION_ENABLED, false)

    fun setChainSharePreciseLocationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CHAIN_SHARE_PRECISE_LOCATION_ENABLED, enabled)
            .apply()
    }

    fun getEvasionProfile(context: Context): String {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EVASION_PROFILE, DEFAULT_EVASION_PROFILE)
            .orEmpty()
            .trim()
            .uppercase()
        return raw.takeIf { it in ALLOWED_EVASION_PROFILES } ?: DEFAULT_EVASION_PROFILE
    }

    fun setEvasionProfile(context: Context, profile: String) {
        val safe = profile.trim().uppercase().takeIf { it in ALLOWED_EVASION_PROFILES }
            ?: DEFAULT_EVASION_PROFILE
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EVASION_PROFILE, safe)
            .apply()
    }

    fun isEvasionAutoEscalateEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_EVASION_AUTO_ESCALATE_ENABLED, true)

    fun setEvasionAutoEscalateEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EVASION_AUTO_ESCALATE_ENABLED, enabled)
            .apply()
    }

    fun getEvasionAutoEscalateDurationSeconds(context: Context): Long {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_EVASION_AUTO_ESCALATE_DURATION_SECONDS, DEFAULT_EVASION_AUTO_ESCALATE_DURATION_SECONDS)
        return value.takeIf { it in ALLOWED_EVASION_ESCALATE_DURATION_SECONDS }
            ?: DEFAULT_EVASION_AUTO_ESCALATE_DURATION_SECONDS
    }

    fun setEvasionAutoEscalateDurationSeconds(context: Context, seconds: Long) {
        val safe = seconds.takeIf { it in ALLOWED_EVASION_ESCALATE_DURATION_SECONDS }
            ?: DEFAULT_EVASION_AUTO_ESCALATE_DURATION_SECONDS
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_EVASION_AUTO_ESCALATE_DURATION_SECONDS, safe)
            .apply()
    }

    fun isEvasionJitterEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_EVASION_JITTER_ENABLED, false)

    fun setEvasionJitterEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EVASION_JITTER_ENABLED, enabled)
            .apply()
    }

    fun getEvasionJitterPercent(context: Context): Int {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_EVASION_JITTER_PERCENT, DEFAULT_EVASION_JITTER_PERCENT)
        return value.takeIf { it in ALLOWED_EVASION_JITTER_PERCENT } ?: DEFAULT_EVASION_JITTER_PERCENT
    }

    fun setEvasionJitterPercent(context: Context, percent: Int) {
        val safe = percent.takeIf { it in ALLOWED_EVASION_JITTER_PERCENT } ?: DEFAULT_EVASION_JITTER_PERCENT
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_EVASION_JITTER_PERCENT, safe)
            .apply()
    }

    fun isEvasionBurstEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_EVASION_BURST_ENABLED, false)

    fun setEvasionBurstEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EVASION_BURST_ENABLED, enabled)
            .apply()
    }

    fun getEvasionBurstWatchSeconds(context: Context): Long {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_EVASION_BURST_WATCH_SECONDS, DEFAULT_EVASION_BURST_WATCH_SECONDS)
        return value.takeIf { it in ALLOWED_EVASION_BURST_WATCH_SECONDS } ?: DEFAULT_EVASION_BURST_WATCH_SECONDS
    }

    fun setEvasionBurstWatchSeconds(context: Context, seconds: Long) {
        val safe = seconds.takeIf { it in ALLOWED_EVASION_BURST_WATCH_SECONDS } ?: DEFAULT_EVASION_BURST_WATCH_SECONDS
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_EVASION_BURST_WATCH_SECONDS, safe)
            .apply()
    }

    fun getEvasionBurstCooldownSeconds(context: Context): Long {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_EVASION_BURST_COOLDOWN_SECONDS, DEFAULT_EVASION_BURST_COOLDOWN_SECONDS)
        return value.takeIf { it in ALLOWED_EVASION_BURST_COOLDOWN_SECONDS } ?: DEFAULT_EVASION_BURST_COOLDOWN_SECONDS
    }

    fun setEvasionBurstCooldownSeconds(context: Context, seconds: Long) {
        val safe = seconds.takeIf { it in ALLOWED_EVASION_BURST_COOLDOWN_SECONDS } ?: DEFAULT_EVASION_BURST_COOLDOWN_SECONDS
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_EVASION_BURST_COOLDOWN_SECONDS, safe)
            .apply()
    }

    fun appendEvasionActionLog(
        context: Context,
        action: String,
        detail: String,
        timestampEpochMs: Long = System.currentTimeMillis()
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = decodeEvasionActionLog(prefs.getString(KEY_EVASION_ACTION_LOG, null))
        val updated = (existing + EvasionActionLogEntry(
            timestampEpochMs = timestampEpochMs,
            action = action.trim().ifBlank { "unknown" },
            detail = detail.trim().ifBlank { "none" }
        )).takeLast(MAX_EVASION_ACTION_LOG_ENTRIES)
        prefs.edit().putString(KEY_EVASION_ACTION_LOG, encodeEvasionActionLog(updated)).apply()
    }

    fun getEvasionActionLog(context: Context, limit: Int = 20): List<EvasionActionLogEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val all = decodeEvasionActionLog(prefs.getString(KEY_EVASION_ACTION_LOG, null))
        return all.takeLast(limit.coerceAtLeast(1)).asReversed()
    }

    fun clearEvasionActionLog(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_EVASION_ACTION_LOG)
            .apply()
    }

    fun getLiveMapUpdateIntervalSeconds(context: Context): Long {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LIVE_MAP_UPDATE_INTERVAL_SECONDS, DEFAULT_LIVE_MAP_UPDATE_INTERVAL_SECONDS)
        return value.takeIf { it in ALLOWED_LIVE_MAP_UPDATE_INTERVAL_SECONDS }
            ?: DEFAULT_LIVE_MAP_UPDATE_INTERVAL_SECONDS
    }

    fun setLiveMapUpdateIntervalSeconds(context: Context, seconds: Long) {
        val safeValue = if (seconds in ALLOWED_LIVE_MAP_UPDATE_INTERVAL_SECONDS) {
            seconds
        } else {
            DEFAULT_LIVE_MAP_UPDATE_INTERVAL_SECONDS
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LIVE_MAP_UPDATE_INTERVAL_SECONDS, safeValue)
            .apply()
    }

    fun getLastScanDurationMs(context: Context): Long? {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SCAN_DURATION_MS, -1L)
        return value.takeIf { it >= 0L }
    }

    fun setLastScanDurationMs(context: Context, durationMs: Long) {
        val safeValue = durationMs.coerceAtLeast(0L)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SCAN_DURATION_MS, safeValue)
            .apply()
    }

    fun appendScanIntervalChangeEvent(
        context: Context,
        fromSeconds: Long,
        toSeconds: Long,
        reason: String,
        timestampEpochMs: Long = System.currentTimeMillis()
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = decodeIntervalChangeEvents(prefs.getString(KEY_SCAN_INTERVAL_CHANGE_EVENTS, null))
        val updated = (existing + IntervalChangeEvent(
            timestampEpochMs = timestampEpochMs,
            fromSeconds = fromSeconds.coerceAtLeast(0L),
            toSeconds = toSeconds.coerceAtLeast(0L),
            reason = reason.trim().ifBlank { "unknown" }
        )).takeLast(MAX_INTERVAL_CHANGE_EVENTS)

        prefs.edit()
            .putString(KEY_SCAN_INTERVAL_CHANGE_EVENTS, encodeIntervalChangeEvents(updated))
            .apply()
    }

    fun getScanIntervalChangeEvents(context: Context, limit: Int = 10): List<IntervalChangeEvent> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val all = decodeIntervalChangeEvents(prefs.getString(KEY_SCAN_INTERVAL_CHANGE_EVENTS, null))
        return all.takeLast(limit.coerceAtLeast(1)).asReversed()
    }

    fun isAutoAdjustScanIntervalEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_ADJUST_SCAN_INTERVAL_ENABLED, false)

    fun setAutoAdjustScanIntervalEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_ADJUST_SCAN_INTERVAL_ENABLED, enabled)
            .apply()
    }

    fun getSourceScanIntervalSeconds(context: Context, sourceType: String): Long {
        val normalizedType = sourceType.trim().lowercase()
        if (normalizedType !in SOURCE_TYPES) return DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = sourceTimingKey(normalizedType, KEY_SOURCE_SCAN_INTERVAL_SECONDS_SUFFIX)
        val value = prefs.getLong(key, DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS)
        return value.coerceIn(MIN_SOURCE_SCAN_INTERVAL_SECONDS, MAX_SOURCE_SCAN_INTERVAL_SECONDS)
    }

    fun setSourceScanIntervalSeconds(context: Context, sourceType: String, seconds: Long) {
        val normalizedType = sourceType.trim().lowercase()
        if (normalizedType !in SOURCE_TYPES) return
        val safeValue = seconds.coerceIn(MIN_SOURCE_SCAN_INTERVAL_SECONDS, MAX_SOURCE_SCAN_INTERVAL_SECONDS)
        val key = sourceTimingKey(normalizedType, KEY_SOURCE_SCAN_INTERVAL_SECONDS_SUFFIX)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(key, safeValue)
            .apply()
    }

    fun getAllSourceScanIntervalSeconds(context: Context): Map<String, Long> =
        SOURCE_TYPES.associateWith { type -> getSourceScanIntervalSeconds(context, type) }

    fun getSourceLastScanEpochMs(context: Context, sourceType: String): Long {
        val normalizedType = sourceType.trim().lowercase()
        if (normalizedType !in SOURCE_TYPES) return 0L
        val key = sourceTimingKey(normalizedType, KEY_SOURCE_LAST_SCAN_EPOCH_MS_SUFFIX)
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(key, 0L)
            .coerceAtLeast(0L)
    }

    fun setSourceLastScanEpochMs(context: Context, sourceType: String, epochMs: Long) {
        val normalizedType = sourceType.trim().lowercase()
        if (normalizedType !in SOURCE_TYPES) return
        val safeValue = epochMs.coerceAtLeast(0L)
        val key = sourceTimingKey(normalizedType, KEY_SOURCE_LAST_SCAN_EPOCH_MS_SUFFIX)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(key, safeValue)
            .apply()
    }

    fun getAllSourceLastScanEpochMs(context: Context): Map<String, Long> =
        SOURCE_TYPES.associateWith { type -> getSourceLastScanEpochMs(context, type) }

    fun recordSourceScanDurationMs(context: Context, sourceType: String, durationMs: Long) {
        val normalizedType = sourceType.trim().lowercase()
        if (normalizedType.isBlank()) return
        val safeValue = durationMs.coerceAtLeast(0L)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val countKey = sourceTimingKey(normalizedType, KEY_SOURCE_TIMING_COUNT_SUFFIX)
        val totalKey = sourceTimingKey(normalizedType, KEY_SOURCE_TIMING_TOTAL_MS_SUFFIX)
        val maxKey = sourceTimingKey(normalizedType, KEY_SOURCE_TIMING_MAX_MS_SUFFIX)
        val lastKey = sourceTimingKey(normalizedType, KEY_SOURCE_TIMING_LAST_MS_SUFFIX)
        val windowKey = sourceTimingKey(normalizedType, KEY_SOURCE_TIMING_WINDOW_SUFFIX)

        val currentCount = prefs.getLong(countKey, 0L)
        val currentTotal = prefs.getLong(totalKey, 0L)
        val currentMax = prefs.getLong(maxKey, 0L)
        val updatedWindow = (parseDurationWindow(prefs.getString(windowKey, null)) + safeValue)
            .takeLast(SOURCE_TIMING_WINDOW_SIZE)

        prefs.edit()
            .putLong(countKey, currentCount + 1L)
            .putLong(totalKey, currentTotal + safeValue)
            .putLong(maxKey, maxOf(currentMax, safeValue))
            .putLong(lastKey, safeValue)
            .putString(windowKey, encodeDurationWindow(updatedWindow))
            .apply()
    }

    fun getSourceScanTimings(context: Context): List<SourceScanTiming> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return SOURCE_TYPES.mapNotNull { type ->
            val count = prefs.getLong(sourceTimingKey(type, KEY_SOURCE_TIMING_COUNT_SUFFIX), 0L)
            if (count <= 0L) return@mapNotNull null

            val total = prefs.getLong(sourceTimingKey(type, KEY_SOURCE_TIMING_TOTAL_MS_SUFFIX), 0L)
            val max = prefs.getLong(sourceTimingKey(type, KEY_SOURCE_TIMING_MAX_MS_SUFFIX), 0L)
            val last = prefs.getLong(sourceTimingKey(type, KEY_SOURCE_TIMING_LAST_MS_SUFFIX), 0L)
            val windowValues = parseDurationWindow(
                prefs.getString(sourceTimingKey(type, KEY_SOURCE_TIMING_WINDOW_SUFFIX), null)
            )
            SourceScanTiming(
                sourceType = type,
                sampleCount = count,
                averageDurationMs = (total / count).coerceAtLeast(0L),
                maxDurationMs = max.coerceAtLeast(0L),
                lastDurationMs = last.coerceAtLeast(0L),
                p50DurationMs = percentile(windowValues, 0.50),
                p95DurationMs = percentile(windowValues, 0.95)
            )
        }
    }

    private fun parseDurationWindow(raw: String?): List<Long> =
        raw
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.map { it.coerceAtLeast(0L) }
            ?: emptyList()

    private fun encodeDurationWindow(values: List<Long>): String =
        values.joinToString(separator = ",") { it.toString() }

    private fun percentile(values: List<Long>, p: Double): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val idx = ((sorted.lastIndex) * p).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }

    private fun encodeIntervalChangeEvents(events: List<IntervalChangeEvent>): String =
        events.joinToString(separator = "\n") { event ->
            listOf(
                event.timestampEpochMs.toString(),
                event.fromSeconds.toString(),
                event.toSeconds.toString(),
                event.reason.replace("|", "/")
            ).joinToString("|")
        }

    private fun decodeIntervalChangeEvents(raw: String?): List<IntervalChangeEvent> =
        raw
            ?.lineSequence()
            ?.mapNotNull { line ->
                val parts = line.split("|", limit = 4)
                if (parts.size < 4) return@mapNotNull null
                val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
                val from = parts[1].toLongOrNull() ?: return@mapNotNull null
                val to = parts[2].toLongOrNull() ?: return@mapNotNull null
                val reason = parts[3].trim().ifBlank { "unknown" }
                IntervalChangeEvent(timestamp, from, to, reason)
            }
            ?.toList()
            ?: emptyList()

    private fun encodeEvasionActionLog(entries: List<EvasionActionLogEntry>): String =
        entries.joinToString(separator = "\n") { entry ->
            listOf(
                entry.timestampEpochMs.toString(),
                entry.action.replace("|", "/"),
                entry.detail.replace("|", "/")
            ).joinToString("|")
        }

    private fun decodeEvasionActionLog(raw: String?): List<EvasionActionLogEntry> =
        raw
            ?.lineSequence()
            ?.mapNotNull { line ->
                val parts = line.split("|", limit = 3)
                if (parts.size < 3) return@mapNotNull null
                val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
                EvasionActionLogEntry(
                    timestampEpochMs = timestamp,
                    action = parts[1].trim().ifBlank { "unknown" },
                    detail = parts[2].trim().ifBlank { "none" }
                )
            }
            ?.toList()
            ?: emptyList()

    private fun sourceTimingKey(sourceType: String, suffix: String): String =
        "${sourceType}${suffix}"

    private fun setSensorEnabled(context: Context, key: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, enabled)
            .apply()
    }

    fun formatInterval(seconds: Long): String = when {
        seconds < 60L -> "$seconds sec"
        seconds % 60L == 0L -> "${seconds / 60L} min"
        else -> "${seconds}s"
    }
}
