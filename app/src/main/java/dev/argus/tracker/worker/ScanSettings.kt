package dev.argus.tracker.worker

import android.content.Context

object ScanSettings {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_SCAN_INTERVAL_SECONDS = "scan_interval_seconds"
    private const val LEGACY_KEY_SCAN_INTERVAL_MINUTES = "scan_interval_minutes"
    private const val KEY_TRACKING_ENABLED = "tracking_enabled"
    private const val KEY_SENSOR_WIFI_ENABLED = "sensor_wifi_enabled"
    private const val KEY_SENSOR_BLE_ENABLED = "sensor_ble_enabled"
    private const val KEY_SENSOR_CELLULAR_ENABLED = "sensor_cellular_enabled"
    private const val KEY_SENSOR_REMOTE_ID_ENABLED = "sensor_remote_id_enabled"
    const val DEFAULT_SCAN_INTERVAL_SECONDS = 15L * 60L
    const val MIN_PERIODIC_INTERVAL_SECONDS = 15L * 60L
    val ALLOWED_INTERVALS_SECONDS = listOf(5L, 15L, 30L, 60L, 5L * 60L, 15L * 60L, 30L * 60L, 60L * 60L)

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
