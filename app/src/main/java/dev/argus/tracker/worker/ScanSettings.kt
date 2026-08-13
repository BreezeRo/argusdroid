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
    const val DEFAULT_SCAN_INTERVAL_SECONDS = 15L * 60L
    const val MIN_PERIODIC_INTERVAL_SECONDS = 15L * 60L
    const val DEFAULT_CHAIN_SYNC_WINDOW_MINUTES = 120L
    const val DEFAULT_CHAIN_AUTO_SYNC_INTERVAL_SECONDS = 60L
    const val DEFAULT_CHAIN_HEARTBEAT_INTERVAL_SECONDS = 20L
    val ALLOWED_INTERVALS_SECONDS = listOf(5L, 15L, 30L, 60L, 5L * 60L, 15L * 60L, 30L * 60L, 60L * 60L)
    val ALLOWED_CHAIN_AUTO_SYNC_INTERVAL_SECONDS = listOf(15L, 30L, 60L, 120L, 300L, 600L)
    val ALLOWED_CHAIN_HEARTBEAT_INTERVAL_SECONDS = listOf(10L, 15L, 20L, 30L, 60L)

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
