package dev.argus.tracker.worker

import android.content.Context
import android.os.Build
import dev.argus.tracker.data.SecureSettingsStore
import dev.argus.tracker.domain.SourceCatalog
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

object ScanSettings {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_SCAN_INTERVAL_SECONDS = "scan_interval_seconds"
    private const val LEGACY_KEY_SCAN_INTERVAL_MINUTES = "scan_interval_minutes"
    private const val KEY_TRACKING_ENABLED = "tracking_enabled"
    private const val KEY_SENSOR_WIFI_ENABLED = "sensor_wifi_enabled"
    private const val KEY_SENSOR_BLE_ENABLED = "sensor_ble_enabled"
    private const val KEY_SENSOR_BT_CLASSIC_ENABLED = "sensor_bt_classic_enabled"
    private const val KEY_SENSOR_NFC_ENABLED = "sensor_nfc_enabled"
    private const val KEY_SENSOR_CELLULAR_ENABLED = "sensor_cellular_enabled"
    private const val KEY_SENSOR_REMOTE_ID_ENABLED = "sensor_remote_id_enabled"
    private const val KEY_SENSOR_SDR_ENABLED = "sensor_sdr_enabled"
    private const val KEY_SENSOR_AVIATION_ADSB_ENABLED = "sensor_aviation_adsb_enabled"
    private const val KEY_SENSOR_AVIATION_PUBLIC_ENABLED = "sensor_aviation_public_enabled"
    private const val KEY_AVIATION_PUBLIC_FEED_URL = "aviation_public_feed_url"
    private const val KEY_AVIATION_PUBLIC_RADIUS_MILES = "aviation_public_radius_miles"
    private const val KEY_REMOTE_ID_INGEST_TOKEN = "remote_id_ingest_token"
    private const val KEY_APPROACH_DETECTION_ENABLED = "approach_detection_enabled"
    private const val KEY_LIVE_MODE_ONLY_ENABLED = "live_mode_only_enabled"
    private const val KEY_APPROACH_NOTIFICATIONS_ENABLED = "approach_notifications_enabled"
    private const val KEY_TRACKER_NOTIFICATIONS_ENABLED = "tracker_notifications_enabled"
    private const val KEY_FLOCK_NOTIFICATIONS_ENABLED = "flock_notifications_enabled"
    private const val KEY_CAMERA_IN_VIEW_NOTIFICATIONS_ENABLED = "camera_in_view_notifications_enabled"
    private const val KEY_FLOCK_MONITOR_LAST_RUN_EPOCH_MS = "flock_monitor_last_run_epoch_ms"
    private const val KEY_FLOCK_ALERT_LAST_NOTIFICATION_EPOCH_MS = "flock_alert_last_notification_epoch_ms"
    private const val KEY_FLOCK_ALERT_LAST_SIGNATURE = "flock_alert_last_signature"
    private const val KEY_NO_FLY_PASS_THROUGH_NOTIFICATIONS_ENABLED = "no_fly_pass_through_notifications_enabled"
    private const val KEY_NFC_NOTIFICATIONS_ENABLED = "nfc_notifications_enabled"
    private const val KEY_STINGRAY_NOTIFICATIONS_ENABLED = "stingray_notifications_enabled"
    private const val KEY_MAGNETIC_INCREASE_NOTIFICATIONS_ENABLED = "magnetic_increase_notifications_enabled"
    private const val KEY_MAGNETIC_RHYTHM_BEEP_ENABLED = "magnetic_rhythm_beep_enabled"
    private const val KEY_MAGNETIC_EVENT_TRIGGER_THRESHOLD_UT = "magnetic_event_trigger_threshold_ut"
    private const val KEY_MAGNETIC_GPS_ACCURACY_REQUIREMENT_METERS = "magnetic_gps_accuracy_requirement_meters"
    private const val KEY_MESH_CONNECTIVITY_NOTIFICATIONS_ENABLED = "mesh_connectivity_notifications_enabled"
    private const val KEY_MESH_WIPE_NOTIFICATIONS_ENABLED = "mesh_wipe_notifications_enabled"
    private const val KEY_FOREIGN_DIRECT_ACOUSTIC_ENABLED = "foreign_direct_acoustic_enabled"
    private const val KEY_FOREIGN_DIRECT_MAGNETIC_ENABLED = "foreign_direct_magnetic_enabled"
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
    private const val KEY_ALERT_LOG_ENTRIES = "alert_log_entries"
    private const val KEY_MESH_WIPE_GATE_ENABLED = "mesh_wipe_gate_enabled"
    private const val KEY_MESH_WIPE_GATE_SESSION_ID = "mesh_wipe_gate_session_id"
    private const val KEY_MESH_WIPE_GATE_INITIATOR_NODE_ID = "mesh_wipe_gate_initiator_node_id"
    private const val KEY_MESH_WIPE_GATE_INITIATOR_DEVICE_NAME = "mesh_wipe_gate_initiator_device_name"
    private const val KEY_MESH_WIPE_GATE_UPDATED_EPOCH_MS = "mesh_wipe_gate_updated_epoch_ms"
    private const val KEY_MAP_CLUSTERING_ENABLED = "map_clustering_enabled"
    private const val KEY_MAP_CLUSTER_RANGE_LEVEL = "map_cluster_range_level"
    private const val KEY_MAP_TRAFFIC_ENABLED = "map_traffic_enabled"
    private const val KEY_MAP_NO_FLY_ZONES_ENABLED = "map_no_fly_zones_enabled"
    private const val KEY_MAP_NO_FLY_RENDER_QUALITY_LEVEL = "map_no_fly_render_quality_level"
    private const val KEY_MAP_SCANNER_SWEEP_ANIMATION_ENABLED = "map_scanner_sweep_animation_enabled"
    private const val KEY_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESET = "map_scanner_sweep_animation_speed_preset"
    private const val KEY_MAP_IDENTITY_FULL_NAMES_ENABLED = "map_identity_full_names_enabled"
    private const val KEY_MAP_SIGNAL_LINK_LINES_ENABLED = "map_signal_link_lines_enabled"
    private const val KEY_MAP_SIGNAL_LINK_CAMERA_IN_VIEW_ONLY = "map_signal_link_camera_in_view_only"
    private const val KEY_MAP_SIGNAL_LINK_ALL_DEVICES_ENABLED = "map_signal_link_all_devices_enabled"
    private const val KEY_MAP_SIGNAL_LINK_ALL_DEVICES_MAX_RANGE_METERS = "map_signal_link_all_devices_max_range_meters"
    private const val KEY_MAP_SIGNAL_LINK_SELECTED_SOURCES = "map_signal_link_selected_sources"
    private const val KEY_STICKY_COMPASS_MAP_ENABLED = "sticky_compass_map_enabled"
    private const val KEY_STICKY_COMPASS_MAP_LIVE_MODE = "sticky_compass_map_live_mode"
    private const val KEY_STICKY_COMPASS_MAP_PIP_ELIGIBLE = "sticky_compass_map_pip_eligible"
    private const val KEY_WIFI_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED = "wifi_randomized_one_off_suppression_enabled"
    private const val KEY_WIFI_AGGREGATE_ONLY_ENABLED = "wifi_aggregate_only_enabled"
    private const val KEY_BLE_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED = "ble_randomized_one_off_suppression_enabled"
    private const val KEY_BLE_AGGREGATE_ONLY_ENABLED = "ble_aggregate_only_enabled"
    private const val KEY_BLE_SWEEP_IDENTIFIABLE_ONLY_ENABLED = "ble_sweep_identifiable_only_enabled"
    private const val KEY_FULL_ENCRYPTION_ENABLED = "full_encryption_enabled"
    private const val KEY_FULL_ENCRYPTION_UNLOCK_METHOD = "full_encryption_unlock_method"
    private const val KEY_FULL_ENCRYPTION_AUTO_LOCK_TIMEOUT_SECONDS = "full_encryption_auto_lock_timeout_seconds"
    private const val KEY_FULL_ENCRYPTION_PIN_WIPE_ENABLED = "full_encryption_pin_wipe_enabled"
    private const val KEY_LAST_APP_LAUNCH_EPOCH_MS = "last_app_launch_epoch_ms"
    private const val KEY_STARTUP_BOOTSTRAP_WAIT_REQUIRED = "startup_bootstrap_wait_required"
    private const val KEY_HOME_POINT_LAT = "home_point_lat"
    private const val KEY_HOME_POINT_LON = "home_point_lon"
    private const val KEY_HOME_POINT_RADIUS_METERS = "home_point_radius_meters"
    private const val KEY_APP_THEME_MODE = "app_theme_mode"
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
    private const val KEY_SOURCE_LAST_RAW_OBSERVATION_EPOCH_MS_SUFFIX = "_last_raw_observation_epoch_ms"
    private const val SOURCE_TIMING_WINDOW_SIZE = 120
    private const val MAX_INTERVAL_CHANGE_EVENTS = 50
    const val DEFAULT_SCAN_INTERVAL_SECONDS = 15L
    const val MIN_PERIODIC_INTERVAL_SECONDS = 15L * 60L
    const val DEFAULT_CHAIN_SYNC_WINDOW_MINUTES = 120L
    const val DEFAULT_CHAIN_AUTO_SYNC_INTERVAL_SECONDS = 60L
    const val DEFAULT_CHAIN_HEARTBEAT_INTERVAL_SECONDS = 20L
    const val DEFAULT_MAP_CLUSTERING_ENABLED = false
    const val DEFAULT_MAP_CLUSTER_RANGE_LEVEL = 3
    const val DEFAULT_MAP_TRAFFIC_ENABLED = true
    const val DEFAULT_MAP_NO_FLY_ZONES_ENABLED = true
    const val DEFAULT_MAP_NO_FLY_RENDER_QUALITY_LEVEL = 2
    const val DEFAULT_MAP_SCANNER_SWEEP_ANIMATION_ENABLED = true
    const val MAP_SCANNER_SWEEP_SPEED_PRESET_CONSERVATIVE = "CONSERVATIVE"
    const val MAP_SCANNER_SWEEP_SPEED_PRESET_BALANCED = "BALANCED"
    const val MAP_SCANNER_SWEEP_SPEED_PRESET_SMOOTH = "SMOOTH"
    const val DEFAULT_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESET = MAP_SCANNER_SWEEP_SPEED_PRESET_BALANCED
    const val DEFAULT_MAP_IDENTITY_FULL_NAMES_ENABLED = false
    const val DEFAULT_MAP_SIGNAL_LINK_LINES_ENABLED = true
    const val DEFAULT_MAP_SIGNAL_LINK_CAMERA_IN_VIEW_ONLY = true
    const val DEFAULT_MAP_SIGNAL_LINK_ALL_DEVICES_ENABLED = false
    const val DEFAULT_MAP_SIGNAL_LINK_ALL_DEVICES_MAX_RANGE_METERS = 5000.0
    val DEFAULT_MAP_SIGNAL_LINK_SELECTED_SOURCES = listOf(
        SourceCatalog.SOURCE_AIRCRAFT,
        SourceCatalog.SOURCE_CAMERA
    )
    const val DEFAULT_STICKY_COMPASS_MAP_ENABLED = false
    const val STICKY_COMPASS_MAP_LIVE_MODE_FOLLOW_DEVICE_MAP = "FOLLOW_DEVICE_MAP"
    const val STICKY_COMPASS_MAP_LIVE_MODE_FORCE_LIVE_ONLY = "FORCE_LIVE_ONLY"
    const val DEFAULT_STICKY_COMPASS_MAP_LIVE_MODE = STICKY_COMPASS_MAP_LIVE_MODE_FOLLOW_DEVICE_MAP
    const val DEFAULT_WIFI_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED = true
    const val DEFAULT_WIFI_AGGREGATE_ONLY_ENABLED = true
    const val DEFAULT_BLE_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED = true
    const val DEFAULT_BLE_AGGREGATE_ONLY_ENABLED = true
    const val DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS = 60L
    const val DEFAULT_AIRCRAFT_SOURCE_SCAN_INTERVAL_SECONDS = 900L
    const val DEFAULT_CAMERA_SOURCE_SCAN_INTERVAL_SECONDS = 900L
    const val DEFAULT_MAGNETIC_SOURCE_SCAN_INTERVAL_SECONDS = 5L
    const val DEFAULT_MAGNETIC_EVENT_TRIGGER_THRESHOLD_UT = 120.0
    const val MIN_MAGNETIC_EVENT_TRIGGER_THRESHOLD_UT = 40.0
    const val MAX_MAGNETIC_EVENT_TRIGGER_THRESHOLD_UT = 120.0
    const val DEFAULT_MAGNETIC_GPS_ACCURACY_REQUIREMENT_METERS = 1.0
    const val MIN_MAGNETIC_GPS_ACCURACY_REQUIREMENT_METERS = 0.5
    const val MAX_MAGNETIC_GPS_ACCURACY_REQUIREMENT_METERS = 25.0
    val ALLOWED_MAGNETIC_GPS_ACCURACY_REQUIREMENT_METERS =
        listOf(0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 10.0, 12.0, 15.0, 20.0, 25.0)
    const val DEFAULT_AVIATION_PUBLIC_RADIUS_MILES = 25
    const val DEFAULT_AVIATION_PUBLIC_FEED_URL = "https://opensky-network.org/api/states/all"
    const val DEFAULT_APP_THEME_MODE = "DARK"
    const val DEFAULT_HOME_POINT_RADIUS_METERS = 160.0
    const val MIN_HOME_POINT_RADIUS_METERS = 30.0
    const val MAX_HOME_POINT_RADIUS_METERS = 1500.0
    val ALLOWED_HOME_POINT_RADIUS_METERS = listOf(50.0, 100.0, 160.0, 250.0, 500.0, 800.0, 1200.0)
    const val MIN_SOURCE_SCAN_INTERVAL_SECONDS = 1L
    const val MAX_SOURCE_SCAN_INTERVAL_SECONDS = 3600L
    private val SHARED_ALLOWED_CADENCE_SECONDS: List<Long> =
        (1L..60L).toList() + listOf(120L, 300L, 600L, 900L, 1800L, 3600L)
    val ALLOWED_INTERVALS_SECONDS = SHARED_ALLOWED_CADENCE_SECONDS
    val ALLOWED_CHAIN_AUTO_SYNC_INTERVAL_SECONDS = listOf(15L, 30L, 60L, 120L, 300L, 600L)
    val ALLOWED_CHAIN_HEARTBEAT_INTERVAL_SECONDS = listOf(10L, 15L, 20L, 30L, 60L)
    val ALLOWED_MAP_CLUSTER_RANGE_LEVELS = (1..5).toList()
    val ALLOWED_MAP_NO_FLY_RENDER_QUALITY_LEVELS = (1..3).toList()
    val ALLOWED_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESETS = listOf(
        MAP_SCANNER_SWEEP_SPEED_PRESET_CONSERVATIVE,
        MAP_SCANNER_SWEEP_SPEED_PRESET_BALANCED,
        MAP_SCANNER_SWEEP_SPEED_PRESET_SMOOTH
    )
    val ALLOWED_MAP_SIGNAL_LINK_ALL_DEVICES_MAX_RANGE_METERS = listOf(
        100.0,
        250.0,
        500.0,
        1000.0,
        2500.0,
        5000.0,
        10000.0,
        25000.0,
        50000.0
    )
    val ALLOWED_STICKY_COMPASS_MAP_LIVE_MODES = listOf(
        STICKY_COMPASS_MAP_LIVE_MODE_FOLLOW_DEVICE_MAP,
        STICKY_COMPASS_MAP_LIVE_MODE_FORCE_LIVE_ONLY
    )
    val ALLOWED_APP_THEME_MODES = listOf("SYSTEM", "LIGHT", "DARK")
    const val FULL_ENCRYPTION_UNLOCK_METHOD_BIOMETRIC = "BIOMETRIC_OR_DEVICE_CREDENTIAL"
    const val FULL_ENCRYPTION_UNLOCK_METHOD_PIN = "PIN"
    const val FULL_ENCRYPTION_UNLOCK_METHOD_PASSWORD = "PASSWORD"
    const val DEFAULT_FULL_ENCRYPTION_AUTO_LOCK_TIMEOUT_SECONDS = 60L
    val ALLOWED_FULL_ENCRYPTION_AUTO_LOCK_TIMEOUT_SECONDS = listOf(0L, 15L, 30L, 60L, 120L, 300L, 600L, 1800L)
    val ALLOWED_FULL_ENCRYPTION_UNLOCK_METHODS = listOf(
        FULL_ENCRYPTION_UNLOCK_METHOD_BIOMETRIC,
        FULL_ENCRYPTION_UNLOCK_METHOD_PIN,
        FULL_ENCRYPTION_UNLOCK_METHOD_PASSWORD
    )
    val SOURCE_TYPES = SourceCatalog.SCAN_SOURCE_KEYS
    val ALLOWED_SOURCE_SCAN_INTERVAL_SECONDS: List<Long> = SHARED_ALLOWED_CADENCE_SECONDS

    @Volatile
    private var stickyCompassMapPipEligibleCache: Boolean? = null

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

    data class MeshWipeGateState(
        val enabled: Boolean,
        val sessionId: String?,
        val initiatorNodeId: String?,
        val initiatorDeviceName: String?,
        val updatedEpochMs: Long?
    )

    data class HomePoint(
        val lat: Double,
        val lon: Double,
        val radiusMeters: Double
    )

    data class OperationalState(
        val lastScanDurationMs: Long?,
        val sourceScanTimings: List<SourceScanTiming>,
        val sourceScanIntervals: Map<String, Long>,
        val sourceLastScanEpochs: Map<String, Long>,
        val sourceLastRawObservationEpochs: Map<String, Long>,
        val scanIntervalChangeEvents: List<IntervalChangeEvent>
    )

    fun getOperationalState(context: Context): OperationalState = OperationalState(
        lastScanDurationMs = getLastScanDurationMs(context),
        sourceScanTimings = getSourceScanTimings(context),
        sourceScanIntervals = getAllSourceScanIntervalSeconds(context),
        sourceLastScanEpochs = getAllSourceLastScanEpochMs(context),
        sourceLastRawObservationEpochs = getAllSourceLastRawObservationEpochMs(context),
        scanIntervalChangeEvents = getScanIntervalChangeEvents(context, 10)
    )

    fun observeOperationalState(context: Context): Flow<OperationalState> = callbackFlow {
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)

        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(getOperationalState(context))
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getOperationalState(context))

        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.conflate()

    fun getScanIntervalSeconds(context: Context): Long {
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val configuredSeconds = if (prefs.contains(KEY_SCAN_INTERVAL_SECONDS)) {
            prefs.getLong(KEY_SCAN_INTERVAL_SECONDS, DEFAULT_SCAN_INTERVAL_SECONDS)
        } else if (prefs.contains(LEGACY_KEY_SCAN_INTERVAL_MINUTES)) {
            // Migrate previous minute-based setting only when the legacy key exists.
            val legacyMinutes = prefs.getLong(LEGACY_KEY_SCAN_INTERVAL_MINUTES, 15L)
            legacyMinutes * 60L
        } else {
            DEFAULT_SCAN_INTERVAL_SECONDS
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
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putLong(KEY_SCAN_INTERVAL_SECONDS, safeValue)
            .apply()
    }

    fun isTrackingEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_TRACKING_ENABLED, false)

    fun setTrackingEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_TRACKING_ENABLED, enabled)
            .apply()
    }

    fun isWifiSensorEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_SENSOR_WIFI_ENABLED, true)

    fun setWifiSensorEnabled(context: Context, enabled: Boolean) {
        setSensorEnabled(context, KEY_SENSOR_WIFI_ENABLED, enabled)
    }

    fun isBleSensorEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_SENSOR_BLE_ENABLED, true)

    fun setBleSensorEnabled(context: Context, enabled: Boolean) {
        setSensorEnabled(context, KEY_SENSOR_BLE_ENABLED, enabled)
        // Keep legacy Classic key in sync now that Bluetooth is a unified sensor gate.
        setSensorEnabled(context, KEY_SENSOR_BT_CLASSIC_ENABLED, enabled)
        // Keep Remote ID gate synchronized with Bluetooth sensor collection toggle.
        setSensorEnabled(context, KEY_SENSOR_REMOTE_ID_ENABLED, enabled)
    }

    fun isBluetoothClassicSensorEnabled(context: Context): Boolean {
        return isBleSensorEnabled(context)
    }

    fun setBluetoothClassicSensorEnabled(context: Context, enabled: Boolean) {
        setBleSensorEnabled(context, enabled)
    }

    fun isNfcSensorEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_SENSOR_NFC_ENABLED, true)

    fun setNfcSensorEnabled(context: Context, enabled: Boolean) {
        setSensorEnabled(context, KEY_SENSOR_NFC_ENABLED, enabled)
    }

    fun isCellularSensorEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_SENSOR_CELLULAR_ENABLED, true)

    fun setCellularSensorEnabled(context: Context, enabled: Boolean) {
        setSensorEnabled(context, KEY_SENSOR_CELLULAR_ENABLED, enabled)
    }

    fun isRemoteIdSensorEnabled(context: Context): Boolean =
        isBleSensorEnabled(context)

    fun setRemoteIdSensorEnabled(context: Context, enabled: Boolean) {
        setBleSensorEnabled(context, enabled)
    }

    fun isSdrSensorEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_SENSOR_SDR_ENABLED, true)

    fun setSdrSensorEnabled(context: Context, enabled: Boolean) {
        setSensorEnabled(context, KEY_SENSOR_SDR_ENABLED, enabled)
    }

    fun isAviationAdsbSensorEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_SENSOR_AVIATION_ADSB_ENABLED, true)

    fun setAviationAdsbSensorEnabled(context: Context, enabled: Boolean) {
        setSensorEnabled(context, KEY_SENSOR_AVIATION_ADSB_ENABLED, enabled)
    }

    fun isAviationPublicSensorEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_SENSOR_AVIATION_PUBLIC_ENABLED, true)

    fun setAviationPublicSensorEnabled(context: Context, enabled: Boolean) {
        setSensorEnabled(context, KEY_SENSOR_AVIATION_PUBLIC_ENABLED, enabled)
    }

    fun getAviationPublicFeedUrl(context: Context): String =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getString(KEY_AVIATION_PUBLIC_FEED_URL, DEFAULT_AVIATION_PUBLIC_FEED_URL)
            .orEmpty()
            .trim()

    fun setAviationPublicFeedUrl(context: Context, url: String) {
        val safe = url.trim().ifBlank { DEFAULT_AVIATION_PUBLIC_FEED_URL }
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_AVIATION_PUBLIC_FEED_URL, safe)
            .apply()
    }

    fun getAviationPublicRadiusMiles(context: Context): Int {
        val value = SecureSettingsStore.prefs(context, PREFS_NAME)
            .getInt(KEY_AVIATION_PUBLIC_RADIUS_MILES, DEFAULT_AVIATION_PUBLIC_RADIUS_MILES)
        return value.coerceIn(10, 300)
    }

    fun setAviationPublicRadiusMiles(context: Context, miles: Int) {
        val safe = miles.coerceIn(10, 300)
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putInt(KEY_AVIATION_PUBLIC_RADIUS_MILES, safe)
            .apply()
    }

    fun getRemoteIdIngestToken(context: Context): String =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getString(KEY_REMOTE_ID_INGEST_TOKEN, "")
            .orEmpty()
            .trim()

    fun setRemoteIdIngestToken(context: Context, token: String) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_REMOTE_ID_INGEST_TOKEN, token.trim())
            .apply()
    }

    fun isApproachDetectionEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_APPROACH_DETECTION_ENABLED, true)

    fun setApproachDetectionEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_APPROACH_DETECTION_ENABLED, enabled)
            .apply()
    }

    fun isLiveModeOnlyEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_LIVE_MODE_ONLY_ENABLED, false)

    fun setLiveModeOnlyEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_LIVE_MODE_ONLY_ENABLED, enabled)
            .apply()
    }

    fun isApproachNotificationsEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_APPROACH_NOTIFICATIONS_ENABLED, false)

    fun setApproachNotificationsEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_APPROACH_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun isTrackerNotificationsEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_TRACKER_NOTIFICATIONS_ENABLED, true)

    fun setTrackerNotificationsEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_TRACKER_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun isFlockNotificationsEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_FLOCK_NOTIFICATIONS_ENABLED, true)

    fun setFlockNotificationsEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_FLOCK_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun isCameraInViewNotificationsEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_CAMERA_IN_VIEW_NOTIFICATIONS_ENABLED, true)

    fun setCameraInViewNotificationsEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_CAMERA_IN_VIEW_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun getFlockMonitorLastRunEpochMs(context: Context): Long =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getLong(KEY_FLOCK_MONITOR_LAST_RUN_EPOCH_MS, 0L)

    fun setFlockMonitorLastRunEpochMs(context: Context, epochMs: Long) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putLong(KEY_FLOCK_MONITOR_LAST_RUN_EPOCH_MS, epochMs)
            .apply()
    }

    fun getFlockAlertLastNotificationEpochMs(context: Context): Long =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getLong(KEY_FLOCK_ALERT_LAST_NOTIFICATION_EPOCH_MS, 0L)

    fun setFlockAlertLastNotificationEpochMs(context: Context, epochMs: Long) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putLong(KEY_FLOCK_ALERT_LAST_NOTIFICATION_EPOCH_MS, epochMs)
            .apply()
    }

    fun getFlockAlertLastSignature(context: Context): String =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getString(KEY_FLOCK_ALERT_LAST_SIGNATURE, "")
            .orEmpty()

    fun setFlockAlertLastSignature(context: Context, signature: String) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_FLOCK_ALERT_LAST_SIGNATURE, signature.trim())
            .apply()
    }

    fun isNfcNotificationsEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_NFC_NOTIFICATIONS_ENABLED, true)

    fun setNfcNotificationsEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_NFC_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun isStingrayNotificationsEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_STINGRAY_NOTIFICATIONS_ENABLED, true)

    fun setStingrayNotificationsEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_STINGRAY_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun isNoFlyPassThroughNotificationsEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_NO_FLY_PASS_THROUGH_NOTIFICATIONS_ENABLED, true)

    fun setNoFlyPassThroughNotificationsEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_NO_FLY_PASS_THROUGH_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun isMagneticIncreaseNotificationsEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_MAGNETIC_INCREASE_NOTIFICATIONS_ENABLED, true)

    fun setMagneticIncreaseNotificationsEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_MAGNETIC_INCREASE_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun isMagneticRhythmBeepEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_MAGNETIC_RHYTHM_BEEP_ENABLED, true)

    fun setMagneticRhythmBeepEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_MAGNETIC_RHYTHM_BEEP_ENABLED, enabled)
            .apply()
    }

    fun getMagneticEventTriggerThresholdMicroTesla(context: Context): Double =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getFloat(
                KEY_MAGNETIC_EVENT_TRIGGER_THRESHOLD_UT,
                DEFAULT_MAGNETIC_EVENT_TRIGGER_THRESHOLD_UT.toFloat()
            )
            .toDouble()
            .coerceIn(
                MIN_MAGNETIC_EVENT_TRIGGER_THRESHOLD_UT,
                MAX_MAGNETIC_EVENT_TRIGGER_THRESHOLD_UT
            )

    fun setMagneticEventTriggerThresholdMicroTesla(context: Context, thresholdMicroTesla: Double) {
        val safeThreshold = thresholdMicroTesla.coerceIn(
            MIN_MAGNETIC_EVENT_TRIGGER_THRESHOLD_UT,
            MAX_MAGNETIC_EVENT_TRIGGER_THRESHOLD_UT
        )
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putFloat(KEY_MAGNETIC_EVENT_TRIGGER_THRESHOLD_UT, safeThreshold.toFloat())
            .apply()
    }

    fun getMagneticGpsAccuracyRequirementMeters(context: Context): Double =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getFloat(
                KEY_MAGNETIC_GPS_ACCURACY_REQUIREMENT_METERS,
                DEFAULT_MAGNETIC_GPS_ACCURACY_REQUIREMENT_METERS.toFloat()
            )
            .toDouble()
            .coerceIn(
                MIN_MAGNETIC_GPS_ACCURACY_REQUIREMENT_METERS,
                MAX_MAGNETIC_GPS_ACCURACY_REQUIREMENT_METERS
            )

    fun setMagneticGpsAccuracyRequirementMeters(context: Context, meters: Double) {
        val safeMeters = meters.coerceIn(
            MIN_MAGNETIC_GPS_ACCURACY_REQUIREMENT_METERS,
            MAX_MAGNETIC_GPS_ACCURACY_REQUIREMENT_METERS
        )
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putFloat(KEY_MAGNETIC_GPS_ACCURACY_REQUIREMENT_METERS, safeMeters.toFloat())
            .apply()
    }

    fun isMeshConnectivityNotificationsEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_MESH_CONNECTIVITY_NOTIFICATIONS_ENABLED, false)

    fun setMeshConnectivityNotificationsEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_MESH_CONNECTIVITY_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun isMeshWipeNotificationsEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_MESH_WIPE_NOTIFICATIONS_ENABLED, true)

    fun setMeshWipeNotificationsEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_MESH_WIPE_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun isForeignDirectAcousticEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_FOREIGN_DIRECT_ACOUSTIC_ENABLED, false)

    fun setForeignDirectAcousticEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_FOREIGN_DIRECT_ACOUSTIC_ENABLED, enabled)
            .apply()
    }

    fun isForeignDirectMagneticEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_FOREIGN_DIRECT_MAGNETIC_ENABLED, false)

    fun setForeignDirectMagneticEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_FOREIGN_DIRECT_MAGNETIC_ENABLED, enabled)
            .apply()
    }

    fun getHomePoint(context: Context): HomePoint? {
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val lat = prefs.getString(KEY_HOME_POINT_LAT, null)?.toDoubleOrNull()
        val lon = prefs.getString(KEY_HOME_POINT_LON, null)?.toDoubleOrNull()
        if (lat == null || lon == null) return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null

        val radiusMeters = prefs.getString(KEY_HOME_POINT_RADIUS_METERS, null)
            ?.toDoubleOrNull()
            ?.coerceIn(MIN_HOME_POINT_RADIUS_METERS, MAX_HOME_POINT_RADIUS_METERS)
            ?: DEFAULT_HOME_POINT_RADIUS_METERS

        return HomePoint(
            lat = lat,
            lon = lon,
            radiusMeters = radiusMeters
        )
    }

    fun setHomePoint(context: Context, lat: Double, lon: Double, radiusMeters: Double = DEFAULT_HOME_POINT_RADIUS_METERS) {
        val safeLat = lat.coerceIn(-90.0, 90.0)
        val safeLon = lon.coerceIn(-180.0, 180.0)
        val safeRadius = radiusMeters.coerceIn(MIN_HOME_POINT_RADIUS_METERS, MAX_HOME_POINT_RADIUS_METERS)
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_HOME_POINT_LAT, String.format(Locale.US, "%.6f", safeLat))
            .putString(KEY_HOME_POINT_LON, String.format(Locale.US, "%.6f", safeLon))
            .putString(KEY_HOME_POINT_RADIUS_METERS, String.format(Locale.US, "%.1f", safeRadius))
            .apply()
    }

    fun clearHomePoint(context: Context) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .remove(KEY_HOME_POINT_LAT)
            .remove(KEY_HOME_POINT_LON)
            .remove(KEY_HOME_POINT_RADIUS_METERS)
            .apply()
    }

    fun setHomePointRadiusMeters(context: Context, radiusMeters: Double): Boolean {
        val existing = getHomePoint(context) ?: return false
        setHomePoint(
            context = context,
            lat = existing.lat,
            lon = existing.lon,
            radiusMeters = radiusMeters
        )
        return true
    }

    fun isChainLinkEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_CHAIN_LINK_ENABLED, false)

    fun setChainLinkEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_CHAIN_LINK_ENABLED, enabled)
            .apply()
    }

    fun getChainNodeId(context: Context): String {
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val existing = prefs.getString(KEY_CHAIN_NODE_ID, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing
        val generated = "argus-${UUID.randomUUID().toString().take(12)}"
        prefs.edit().putString(KEY_CHAIN_NODE_ID, generated).apply()
        return generated
    }

    fun getChainSyncWindowMinutes(context: Context): Long {
        val value = SecureSettingsStore.prefs(context, PREFS_NAME)
            .getLong(KEY_CHAIN_SYNC_WINDOW_MINUTES, DEFAULT_CHAIN_SYNC_WINDOW_MINUTES)
        return value.coerceIn(15L, 24L * 60L)
    }

    fun setChainSyncWindowMinutes(context: Context, minutes: Long) {
        val safe = minutes.coerceIn(15L, 24L * 60L)
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putLong(KEY_CHAIN_SYNC_WINDOW_MINUTES, safe)
            .apply()
    }

    fun getChainSharedSecret(context: Context): String =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getString(KEY_CHAIN_SHARED_SECRET, "")
            .orEmpty()
            .trim()

    fun setChainSharedSecret(context: Context, secret: String) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_CHAIN_SHARED_SECRET, secret.trim())
            .apply()
    }

    fun isChainAutoSyncEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_CHAIN_AUTO_SYNC_ENABLED, true)

    fun setChainAutoSyncEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_CHAIN_AUTO_SYNC_ENABLED, enabled)
            .apply()
    }

    fun getChainAutoSyncIntervalSeconds(context: Context): Long {
        val value = SecureSettingsStore.prefs(context, PREFS_NAME)
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
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putLong(KEY_CHAIN_AUTO_SYNC_INTERVAL_SECONDS, safeValue)
            .apply()
    }

    fun isChainPersistentChannelEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_CHAIN_PERSISTENT_CHANNEL_ENABLED, true)

    fun setChainPersistentChannelEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_CHAIN_PERSISTENT_CHANNEL_ENABLED, enabled)
            .apply()
    }

    fun getChainHeartbeatIntervalSeconds(context: Context): Long {
        val value = SecureSettingsStore.prefs(context, PREFS_NAME)
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
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putLong(KEY_CHAIN_HEARTBEAT_INTERVAL_SECONDS, safeValue)
            .apply()
    }

    fun getChainDeviceName(context: Context): String {
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val existing = prefs.getString(KEY_CHAIN_DEVICE_NAME, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing

        val defaultName = Build.MODEL.take(40).ifBlank { "Argus Device" }
        prefs.edit().putString(KEY_CHAIN_DEVICE_NAME, defaultName).apply()
        return defaultName
    }

    fun setChainDeviceName(context: Context, name: String) {
        val normalized = name.trim().ifBlank { "Argus Device" }.take(40)
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_CHAIN_DEVICE_NAME, normalized)
            .apply()
    }

    fun isChainSharePreciseLocationEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_CHAIN_SHARE_PRECISE_LOCATION_ENABLED, false)

    fun setChainSharePreciseLocationEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_CHAIN_SHARE_PRECISE_LOCATION_ENABLED, enabled)
            .apply()
    }

    fun clearAlertLogs(context: Context) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .remove(KEY_ALERT_LOG_ENTRIES)
            .apply()
    }

    fun clearScanIntervalChangeEvents(context: Context) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .remove(KEY_SCAN_INTERVAL_CHANGE_EVENTS)
            .apply()
    }

    fun clearOperationalLogs(context: Context) {
        clearAlertLogs(context)
        clearScanIntervalChangeEvents(context)
    }

    fun resetMeshNetworkSettings(context: Context) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_CHAIN_LINK_ENABLED, false)
            .putString(KEY_CHAIN_SHARED_SECRET, "")
            .putBoolean(KEY_CHAIN_AUTO_SYNC_ENABLED, false)
            .putLong(KEY_CHAIN_AUTO_SYNC_INTERVAL_SECONDS, DEFAULT_CHAIN_AUTO_SYNC_INTERVAL_SECONDS)
            .putBoolean(KEY_CHAIN_PERSISTENT_CHANNEL_ENABLED, false)
            .putLong(KEY_CHAIN_HEARTBEAT_INTERVAL_SECONDS, DEFAULT_CHAIN_HEARTBEAT_INTERVAL_SECONDS)
            .putBoolean(KEY_CHAIN_SHARE_PRECISE_LOCATION_ENABLED, false)
            .putLong(KEY_CHAIN_SYNC_WINDOW_MINUTES, DEFAULT_CHAIN_SYNC_WINDOW_MINUTES)
            .putString(KEY_CHAIN_DEVICE_NAME, Build.MODEL.take(40))
            .putBoolean(KEY_MESH_WIPE_GATE_ENABLED, false)
            .remove(KEY_MESH_WIPE_GATE_SESSION_ID)
            .remove(KEY_MESH_WIPE_GATE_INITIATOR_NODE_ID)
            .remove(KEY_MESH_WIPE_GATE_INITIATOR_DEVICE_NAME)
            .remove(KEY_MESH_WIPE_GATE_UPDATED_EPOCH_MS)
            .apply()
    }

    fun getMeshWipeGateState(context: Context): MeshWipeGateState {
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val enabled = prefs.getBoolean(KEY_MESH_WIPE_GATE_ENABLED, false)
        val sessionId = prefs.getString(KEY_MESH_WIPE_GATE_SESSION_ID, null)?.trim()?.ifBlank { null }
        val initiatorNodeId = prefs.getString(KEY_MESH_WIPE_GATE_INITIATOR_NODE_ID, null)?.trim()?.ifBlank { null }
        val initiatorDeviceName = prefs.getString(KEY_MESH_WIPE_GATE_INITIATOR_DEVICE_NAME, null)?.trim()?.ifBlank { null }
        val updated = prefs.getLong(KEY_MESH_WIPE_GATE_UPDATED_EPOCH_MS, -1L).takeIf { it >= 0L }
        return MeshWipeGateState(
            enabled = enabled,
            sessionId = sessionId,
            initiatorNodeId = initiatorNodeId,
            initiatorDeviceName = initiatorDeviceName,
            updatedEpochMs = updated
        )
    }

    fun observeMeshWipeGateState(context: Context): Flow<MeshWipeGateState> = callbackFlow {
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)

        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (
                key == KEY_MESH_WIPE_GATE_ENABLED ||
                key == KEY_MESH_WIPE_GATE_SESSION_ID ||
                key == KEY_MESH_WIPE_GATE_INITIATOR_NODE_ID ||
                key == KEY_MESH_WIPE_GATE_INITIATOR_DEVICE_NAME ||
                key == KEY_MESH_WIPE_GATE_UPDATED_EPOCH_MS
            ) {
                trySend(getMeshWipeGateState(context))
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getMeshWipeGateState(context))

        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.conflate()

    fun isMeshWipeGateEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_MESH_WIPE_GATE_ENABLED, false)

    fun beginMeshWipeGate(
        context: Context,
        sessionId: String,
        initiatorNodeId: String,
        initiatorDeviceName: String?
    ) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_MESH_WIPE_GATE_ENABLED, true)
            .putString(KEY_MESH_WIPE_GATE_SESSION_ID, sessionId.trim())
            .putString(KEY_MESH_WIPE_GATE_INITIATOR_NODE_ID, initiatorNodeId.trim())
            .putString(KEY_MESH_WIPE_GATE_INITIATOR_DEVICE_NAME, initiatorDeviceName?.trim()?.ifBlank { null })
            .putLong(KEY_MESH_WIPE_GATE_UPDATED_EPOCH_MS, System.currentTimeMillis())
            .apply()
    }

    fun completeMeshWipeGate(context: Context) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_MESH_WIPE_GATE_ENABLED, false)
            .putLong(KEY_MESH_WIPE_GATE_UPDATED_EPOCH_MS, System.currentTimeMillis())
            .apply()
    }

    fun isMapClusteringEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_MAP_CLUSTERING_ENABLED, DEFAULT_MAP_CLUSTERING_ENABLED)

    fun setMapClusteringEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_MAP_CLUSTERING_ENABLED, enabled)
            .apply()
    }

    fun getMapClusterRangeLevel(context: Context): Int {
        val raw = SecureSettingsStore.prefs(context, PREFS_NAME)
            .getInt(KEY_MAP_CLUSTER_RANGE_LEVEL, DEFAULT_MAP_CLUSTER_RANGE_LEVEL)
        return raw.takeIf { it in ALLOWED_MAP_CLUSTER_RANGE_LEVELS }
            ?: DEFAULT_MAP_CLUSTER_RANGE_LEVEL
    }

    fun setMapClusterRangeLevel(context: Context, level: Int) {
        val safe = level.takeIf { it in ALLOWED_MAP_CLUSTER_RANGE_LEVELS }
            ?: DEFAULT_MAP_CLUSTER_RANGE_LEVEL
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putInt(KEY_MAP_CLUSTER_RANGE_LEVEL, safe)
            .apply()
    }

    fun isMapTrafficEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_MAP_TRAFFIC_ENABLED, DEFAULT_MAP_TRAFFIC_ENABLED)

    fun setMapTrafficEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_MAP_TRAFFIC_ENABLED, enabled)
            .apply()
    }

    fun isMapNoFlyZonesEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_MAP_NO_FLY_ZONES_ENABLED, DEFAULT_MAP_NO_FLY_ZONES_ENABLED)

    fun setMapNoFlyZonesEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_MAP_NO_FLY_ZONES_ENABLED, enabled)
            .apply()
    }

    fun getMapNoFlyRenderQualityLevel(context: Context): Int {
        val raw = SecureSettingsStore.prefs(context, PREFS_NAME)
            .getInt(KEY_MAP_NO_FLY_RENDER_QUALITY_LEVEL, DEFAULT_MAP_NO_FLY_RENDER_QUALITY_LEVEL)
        return raw.takeIf { it in ALLOWED_MAP_NO_FLY_RENDER_QUALITY_LEVELS }
            ?: DEFAULT_MAP_NO_FLY_RENDER_QUALITY_LEVEL
    }

    fun setMapNoFlyRenderQualityLevel(context: Context, level: Int) {
        val safe = level.takeIf { it in ALLOWED_MAP_NO_FLY_RENDER_QUALITY_LEVELS }
            ?: DEFAULT_MAP_NO_FLY_RENDER_QUALITY_LEVEL
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putInt(KEY_MAP_NO_FLY_RENDER_QUALITY_LEVEL, safe)
            .apply()
    }

    fun isMapScannerSweepAnimationEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_MAP_SCANNER_SWEEP_ANIMATION_ENABLED, DEFAULT_MAP_SCANNER_SWEEP_ANIMATION_ENABLED)

    fun setMapScannerSweepAnimationEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_MAP_SCANNER_SWEEP_ANIMATION_ENABLED, enabled)
            .apply()
    }

    fun getMapScannerSweepAnimationSpeedPreset(context: Context): String {
        val raw = SecureSettingsStore.prefs(context, PREFS_NAME)
            .getString(
                KEY_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESET,
                DEFAULT_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESET
            )
            .orEmpty()
            .trim()
            .uppercase(Locale.US)
        return raw.takeIf { it in ALLOWED_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESETS }
            ?: DEFAULT_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESET
    }

    fun setMapScannerSweepAnimationSpeedPreset(context: Context, preset: String) {
        val safe = preset.trim().uppercase(Locale.US)
            .takeIf { it in ALLOWED_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESETS }
            ?: DEFAULT_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESET
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESET, safe)
            .apply()
    }

    fun isMapIdentityFullNamesEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_MAP_IDENTITY_FULL_NAMES_ENABLED, DEFAULT_MAP_IDENTITY_FULL_NAMES_ENABLED)

    fun setMapIdentityFullNamesEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_MAP_IDENTITY_FULL_NAMES_ENABLED, enabled)
            .apply()
    }

    fun isMapSignalLinkLinesEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_MAP_SIGNAL_LINK_LINES_ENABLED, DEFAULT_MAP_SIGNAL_LINK_LINES_ENABLED)

    fun setMapSignalLinkLinesEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_MAP_SIGNAL_LINK_LINES_ENABLED, enabled)
            .apply()
    }

    fun isMapSignalLinkCameraInViewOnlyEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(
                KEY_MAP_SIGNAL_LINK_CAMERA_IN_VIEW_ONLY,
                DEFAULT_MAP_SIGNAL_LINK_CAMERA_IN_VIEW_ONLY
            )

    fun setMapSignalLinkCameraInViewOnlyEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_MAP_SIGNAL_LINK_CAMERA_IN_VIEW_ONLY, enabled)
            .apply()
    }

    fun isMapSignalLinkAllDevicesEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(
                KEY_MAP_SIGNAL_LINK_ALL_DEVICES_ENABLED,
                DEFAULT_MAP_SIGNAL_LINK_ALL_DEVICES_ENABLED
            )

    fun setMapSignalLinkAllDevicesEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_MAP_SIGNAL_LINK_ALL_DEVICES_ENABLED, enabled)
            .apply()
    }

    fun getMapSignalLinkAllDevicesMaxRangeMeters(context: Context): Double {
        val raw = SecureSettingsStore.prefs(context, PREFS_NAME)
            .getFloat(
                KEY_MAP_SIGNAL_LINK_ALL_DEVICES_MAX_RANGE_METERS,
                DEFAULT_MAP_SIGNAL_LINK_ALL_DEVICES_MAX_RANGE_METERS.toFloat()
            )
            .toDouble()
        return raw.takeIf { it in ALLOWED_MAP_SIGNAL_LINK_ALL_DEVICES_MAX_RANGE_METERS }
            ?: DEFAULT_MAP_SIGNAL_LINK_ALL_DEVICES_MAX_RANGE_METERS
    }

    fun setMapSignalLinkAllDevicesMaxRangeMeters(context: Context, meters: Double) {
        val safe = meters.takeIf { it in ALLOWED_MAP_SIGNAL_LINK_ALL_DEVICES_MAX_RANGE_METERS }
            ?: DEFAULT_MAP_SIGNAL_LINK_ALL_DEVICES_MAX_RANGE_METERS
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putFloat(KEY_MAP_SIGNAL_LINK_ALL_DEVICES_MAX_RANGE_METERS, safe.toFloat())
            .apply()
    }

    fun getMapSignalLinkSelectedSources(context: Context): List<String> {
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val raw = if (prefs.contains(KEY_MAP_SIGNAL_LINK_SELECTED_SOURCES)) {
            prefs.getStringSet(KEY_MAP_SIGNAL_LINK_SELECTED_SOURCES, emptySet())
        } else {
            DEFAULT_MAP_SIGNAL_LINK_SELECTED_SOURCES.toSet()
        }

        return raw
            ?.asSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.sorted()
            ?.toList()
            .orEmpty()
    }

    fun setMapSignalLinkSelectedSources(context: Context, sources: List<String>) {
        val safe = sources
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toSet()
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putStringSet(KEY_MAP_SIGNAL_LINK_SELECTED_SOURCES, safe)
            .apply()
    }

    fun isStickyCompassMapEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_STICKY_COMPASS_MAP_ENABLED, DEFAULT_STICKY_COMPASS_MAP_ENABLED)

    fun setStickyCompassMapEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_STICKY_COMPASS_MAP_ENABLED, enabled)
            .apply()
    }

    fun getStickyCompassMapLiveMode(context: Context): String {
        val raw = SecureSettingsStore.prefs(context, PREFS_NAME)
            .getString(KEY_STICKY_COMPASS_MAP_LIVE_MODE, DEFAULT_STICKY_COMPASS_MAP_LIVE_MODE)
            .orEmpty()
            .trim()
            .uppercase(Locale.US)
        return raw.takeIf { it in ALLOWED_STICKY_COMPASS_MAP_LIVE_MODES }
            ?: DEFAULT_STICKY_COMPASS_MAP_LIVE_MODE
    }

    fun setStickyCompassMapLiveMode(context: Context, mode: String) {
        val safe = mode.trim().uppercase(Locale.US)
            .takeIf { it in ALLOWED_STICKY_COMPASS_MAP_LIVE_MODES }
            ?: DEFAULT_STICKY_COMPASS_MAP_LIVE_MODE
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_STICKY_COMPASS_MAP_LIVE_MODE, safe)
            .apply()
    }

    fun isStickyCompassMapPipEligible(context: Context): Boolean =
        stickyCompassMapPipEligibleCache ?: SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_STICKY_COMPASS_MAP_PIP_ELIGIBLE, false)
            .also { stickyCompassMapPipEligibleCache = it }

    fun setStickyCompassMapPipEligible(context: Context, eligible: Boolean) {
        stickyCompassMapPipEligibleCache = eligible
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_STICKY_COMPASS_MAP_PIP_ELIGIBLE, eligible)
            .apply()
    }

    fun isWifiRandomizedOneOffSuppressionEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(
                KEY_WIFI_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED,
                DEFAULT_WIFI_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED
            )

    fun setWifiRandomizedOneOffSuppressionEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_WIFI_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED, enabled)
            .apply()
    }

    fun isWifiAggregateOnlyEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(
                KEY_WIFI_AGGREGATE_ONLY_ENABLED,
                DEFAULT_WIFI_AGGREGATE_ONLY_ENABLED
            )

    fun setWifiAggregateOnlyEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_WIFI_AGGREGATE_ONLY_ENABLED, enabled)
            .apply()
    }

    fun isBleRandomizedOneOffSuppressionEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(
                KEY_BLE_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED,
                DEFAULT_BLE_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED
            )

    fun setBleRandomizedOneOffSuppressionEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_BLE_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED, enabled)
            .apply()
    }

    fun isBleAggregateOnlyEnabled(context: Context): Boolean {
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        return if (prefs.contains(KEY_BLE_AGGREGATE_ONLY_ENABLED)) {
            prefs.getBoolean(
                KEY_BLE_AGGREGATE_ONLY_ENABLED,
                DEFAULT_BLE_AGGREGATE_ONLY_ENABLED
            )
        } else {
            // Migration fallback from previous BLE identifiable-only setting.
            prefs.getBoolean(
                KEY_BLE_SWEEP_IDENTIFIABLE_ONLY_ENABLED,
                DEFAULT_BLE_AGGREGATE_ONLY_ENABLED
            )
        }
    }

    fun isFullEncryptionEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_FULL_ENCRYPTION_ENABLED, false)

    fun setFullEncryptionEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_FULL_ENCRYPTION_ENABLED, enabled)
            .apply()
    }

    fun getFullEncryptionUnlockMethod(context: Context): String {
        val raw = SecureSettingsStore.prefs(context, PREFS_NAME)
            .getString(KEY_FULL_ENCRYPTION_UNLOCK_METHOD, FULL_ENCRYPTION_UNLOCK_METHOD_BIOMETRIC)
            .orEmpty()
            .trim()
            .uppercase()
        return raw.takeIf { it in ALLOWED_FULL_ENCRYPTION_UNLOCK_METHODS }
            ?: FULL_ENCRYPTION_UNLOCK_METHOD_BIOMETRIC
    }

    fun setFullEncryptionUnlockMethod(context: Context, method: String) {
        val safe = method.trim().uppercase().takeIf { it in ALLOWED_FULL_ENCRYPTION_UNLOCK_METHODS }
            ?: FULL_ENCRYPTION_UNLOCK_METHOD_BIOMETRIC
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_FULL_ENCRYPTION_UNLOCK_METHOD, safe)
            .apply()
    }

    fun getFullEncryptionAutoLockTimeoutSeconds(context: Context): Long {
        val value = SecureSettingsStore.prefs(context, PREFS_NAME)
            .getLong(
                KEY_FULL_ENCRYPTION_AUTO_LOCK_TIMEOUT_SECONDS,
                DEFAULT_FULL_ENCRYPTION_AUTO_LOCK_TIMEOUT_SECONDS
            )
        return value.takeIf { it in ALLOWED_FULL_ENCRYPTION_AUTO_LOCK_TIMEOUT_SECONDS }
            ?: DEFAULT_FULL_ENCRYPTION_AUTO_LOCK_TIMEOUT_SECONDS
    }

    fun setFullEncryptionAutoLockTimeoutSeconds(context: Context, seconds: Long) {
        val safe = seconds.takeIf { it in ALLOWED_FULL_ENCRYPTION_AUTO_LOCK_TIMEOUT_SECONDS }
            ?: DEFAULT_FULL_ENCRYPTION_AUTO_LOCK_TIMEOUT_SECONDS
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putLong(KEY_FULL_ENCRYPTION_AUTO_LOCK_TIMEOUT_SECONDS, safe)
            .apply()
    }

    fun isFullEncryptionPinWipeEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_FULL_ENCRYPTION_PIN_WIPE_ENABLED, false)

    fun setFullEncryptionPinWipeEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_FULL_ENCRYPTION_PIN_WIPE_ENABLED, enabled)
            .apply()
    }

    fun setBleAggregateOnlyEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_BLE_AGGREGATE_ONLY_ENABLED, enabled)
            .apply()
    }

    fun getLastAppLaunchEpochMs(context: Context): Long? =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getLong(KEY_LAST_APP_LAUNCH_EPOCH_MS, -1L)
            .takeIf { it > 0L }

    fun setLastAppLaunchEpochMs(context: Context, epochMs: Long) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putLong(KEY_LAST_APP_LAUNCH_EPOCH_MS, epochMs.coerceAtLeast(0L))
            .apply()
    }

    fun isStartupBootstrapWaitRequired(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_STARTUP_BOOTSTRAP_WAIT_REQUIRED, true)

    fun setStartupBootstrapWaitRequired(context: Context, required: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_STARTUP_BOOTSTRAP_WAIT_REQUIRED, required)
            .apply()
    }

    fun getAppThemeMode(context: Context): String {
        val raw = SecureSettingsStore.prefs(context, PREFS_NAME)
            .getString(KEY_APP_THEME_MODE, DEFAULT_APP_THEME_MODE)
            .orEmpty()
            .trim()
            .uppercase()
        return raw.takeIf { it in ALLOWED_APP_THEME_MODES } ?: DEFAULT_APP_THEME_MODE
    }

    fun setAppThemeMode(context: Context, mode: String) {
        val safe = mode.trim().uppercase().takeIf { it in ALLOWED_APP_THEME_MODES }
            ?: DEFAULT_APP_THEME_MODE
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_APP_THEME_MODE, safe)
            .apply()
    }

    fun getLastScanDurationMs(context: Context): Long? {
        val value = SecureSettingsStore.prefs(context, PREFS_NAME)
            .getLong(KEY_LAST_SCAN_DURATION_MS, -1L)
        return value.takeIf { it >= 0L }
    }

    fun setLastScanDurationMs(context: Context, durationMs: Long) {
        val safeValue = durationMs.coerceAtLeast(0L)
        SecureSettingsStore.prefs(context, PREFS_NAME)
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
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
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
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val all = decodeIntervalChangeEvents(prefs.getString(KEY_SCAN_INTERVAL_CHANGE_EVENTS, null))
        return all.takeLast(limit.coerceAtLeast(1)).asReversed()
    }

    fun isAutoAdjustScanIntervalEnabled(context: Context): Boolean =
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .getBoolean(KEY_AUTO_ADJUST_SCAN_INTERVAL_ENABLED, false)

    fun setAutoAdjustScanIntervalEnabled(context: Context, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putBoolean(KEY_AUTO_ADJUST_SCAN_INTERVAL_ENABLED, enabled)
            .apply()
    }

    fun getSourceScanIntervalSeconds(context: Context, sourceType: String): Long {
        val normalizedType = sourceType.trim().lowercase()
        val canonicalType = canonicalSourceIntervalType(normalizedType)
        if (canonicalType !in SOURCE_TYPES) return DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val key = sourceTimingKey(canonicalType, KEY_SOURCE_SCAN_INTERVAL_SECONDS_SUFFIX)
        val value = prefs.getLong(key, defaultSourceScanIntervalSeconds(canonicalType))
        return value.coerceIn(MIN_SOURCE_SCAN_INTERVAL_SECONDS, MAX_SOURCE_SCAN_INTERVAL_SECONDS)
    }

    fun setSourceScanIntervalSeconds(context: Context, sourceType: String, seconds: Long) {
        val normalizedType = sourceType.trim().lowercase()
        val canonicalType = canonicalSourceIntervalType(normalizedType)
        if (canonicalType !in SOURCE_TYPES) return
        val safeValue = seconds.coerceIn(MIN_SOURCE_SCAN_INTERVAL_SECONDS, MAX_SOURCE_SCAN_INTERVAL_SECONDS)
        val key = sourceTimingKey(canonicalType, KEY_SOURCE_SCAN_INTERVAL_SECONDS_SUFFIX)
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putLong(key, safeValue)
            .apply()
    }

    fun getAllSourceScanIntervalSeconds(context: Context): Map<String, Long> =
        SOURCE_TYPES.associateWith { type -> getSourceScanIntervalSeconds(context, type) }

    private fun defaultSourceScanIntervalSeconds(sourceType: String): Long = when (sourceType) {
        SourceCatalog.KEY_AIRCRAFT -> DEFAULT_AIRCRAFT_SOURCE_SCAN_INTERVAL_SECONDS
        SourceCatalog.KEY_CAMERA -> DEFAULT_CAMERA_SOURCE_SCAN_INTERVAL_SECONDS
        SourceCatalog.KEY_MAGNETIC -> DEFAULT_MAGNETIC_SOURCE_SCAN_INTERVAL_SECONDS
        else -> DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS
    }

    private fun canonicalSourceIntervalType(sourceType: String): String = when (sourceType) {
        SourceCatalog.KEY_WIFI_DIRECT -> SourceCatalog.KEY_WIFI
        SourceCatalog.KEY_BT_CLASSIC,
        SourceCatalog.KEY_REMOTE_ID -> SourceCatalog.KEY_BLE
        else -> sourceType
    }

    fun getSourceLastScanEpochMs(context: Context, sourceType: String): Long {
        val normalizedType = sourceType.trim().lowercase()
        if (normalizedType !in SOURCE_TYPES) return 0L
        val key = sourceTimingKey(normalizedType, KEY_SOURCE_LAST_SCAN_EPOCH_MS_SUFFIX)
        return SecureSettingsStore.prefs(context, PREFS_NAME)
            .getLong(key, 0L)
            .coerceAtLeast(0L)
    }

    fun setSourceLastScanEpochMs(context: Context, sourceType: String, epochMs: Long) {
        val normalizedType = sourceType.trim().lowercase()
        if (normalizedType !in SOURCE_TYPES) return
        val safeValue = epochMs.coerceAtLeast(0L)
        val key = sourceTimingKey(normalizedType, KEY_SOURCE_LAST_SCAN_EPOCH_MS_SUFFIX)
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putLong(key, safeValue)
            .apply()
    }

    fun getAllSourceLastScanEpochMs(context: Context): Map<String, Long> =
        SOURCE_TYPES.associateWith { type -> getSourceLastScanEpochMs(context, type) }

    fun getSourceLastRawObservationEpochMs(context: Context, sourceType: String): Long {
        val normalizedType = sourceType.trim().lowercase()
        if (normalizedType !in SOURCE_TYPES) return 0L
        val key = sourceTimingKey(normalizedType, KEY_SOURCE_LAST_RAW_OBSERVATION_EPOCH_MS_SUFFIX)
        return SecureSettingsStore.prefs(context, PREFS_NAME)
            .getLong(key, 0L)
            .coerceAtLeast(0L)
    }

    fun setSourceLastRawObservationEpochMs(context: Context, sourceType: String, epochMs: Long) {
        val normalizedType = sourceType.trim().lowercase()
        if (normalizedType !in SOURCE_TYPES) return
        val safeValue = epochMs.coerceAtLeast(0L)
        val key = sourceTimingKey(normalizedType, KEY_SOURCE_LAST_RAW_OBSERVATION_EPOCH_MS_SUFFIX)
        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putLong(key, safeValue)
            .apply()
    }

    fun getAllSourceLastRawObservationEpochMs(context: Context): Map<String, Long> =
        SOURCE_TYPES.associateWith { type -> getSourceLastRawObservationEpochMs(context, type) }

    fun recordSourceScanDurationMs(context: Context, sourceType: String, durationMs: Long) {
        val normalizedType = sourceType.trim().lowercase()
        if (normalizedType.isBlank()) return
        val safeValue = durationMs.coerceAtLeast(0L)
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
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
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
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

    private fun sourceTimingKey(sourceType: String, suffix: String): String =
        "${sourceType}${suffix}"

    private fun setSensorEnabled(context: Context, key: String, enabled: Boolean) {
        SecureSettingsStore.prefs(context, PREFS_NAME)
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


