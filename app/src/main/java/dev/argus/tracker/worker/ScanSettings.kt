package dev.argus.tracker.worker

import android.content.Context

object ScanSettings {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_SCAN_INTERVAL_MINUTES = "scan_interval_minutes"
    const val DEFAULT_SCAN_INTERVAL_MINUTES = 15L
    val ALLOWED_INTERVALS_MINUTES = listOf(15L, 30L, 60L)

    fun getScanIntervalMinutes(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val configured = prefs.getLong(KEY_SCAN_INTERVAL_MINUTES, DEFAULT_SCAN_INTERVAL_MINUTES)
        return if (configured in ALLOWED_INTERVALS_MINUTES) {
            configured
        } else {
            DEFAULT_SCAN_INTERVAL_MINUTES
        }
    }

    fun setScanIntervalMinutes(context: Context, minutes: Long) {
        val safeValue = if (minutes in ALLOWED_INTERVALS_MINUTES) {
            minutes
        } else {
            DEFAULT_SCAN_INTERVAL_MINUTES
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SCAN_INTERVAL_MINUTES, safeValue)
            .apply()
    }
}
