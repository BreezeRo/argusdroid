package dev.argus.tracker.ui

import android.Manifest
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.AssistChip
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import dev.argus.tracker.MainActivity
import dev.argus.tracker.ArgusApplication
import dev.argus.tracker.data.AppBackupManager
import dev.argus.tracker.data.chain.ChainMeshSnapshot
import dev.argus.tracker.data.chain.ChainPeerState
import dev.argus.tracker.data.chain.MeshForegroundServiceController
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterProvenance
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.sensing.CellTowerLookupService
import dev.argus.tracker.sensing.DetectionLocation
import dev.argus.tracker.sensing.LocationSnapshotProvider
import dev.argus.tracker.sensing.RemoteIdForegroundServiceController
import dev.argus.tracker.sensing.SensorStatus
import dev.argus.tracker.sensing.SensorStatusProvider
import dev.argus.tracker.sensing.TowerLookupResult
import dev.argus.tracker.sensing.remoteid.RemoteIdPayloadParser
import dev.argus.tracker.wear.WearDevicePoint
import dev.argus.tracker.wear.WearStatusBridgePublisher
import dev.argus.tracker.worker.ScanSettings
import dev.argus.tracker.worker.WorkScheduler
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.random.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private enum class DataScope {
    RECENT_100,
    ALL
}

private enum class DeviceSortMode {
    LAST_SEEN,
    MOST_SEEN
}

private enum class EvasionProfile {
    QUIET,
    BALANCED,
    WATCH
}

private enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

private const val HOME_ROUTE = "home"
private const val SETTINGS_ROUTE = "settings"
private const val DETECTION_ROUTE = "detection"
private const val EVASION_ROUTE = "evasion"
private const val DEVICES_ENCOUNTERS_ROUTE = "devicesEncounters"
private const val APPROACH_ALERT_MAP_ROUTE = "approachAlertMap/{source}/{primaryId}"
private const val MOVING_DEVICE_PATH_ROUTE = "movingDevicePath/{source}/{primaryId}"
private const val DEVICE_DETAIL_ROUTE = "deviceDetail/{source}/{primaryId}?lat={lat}&lon={lon}&ts={ts}"
private const val ENCOUNTER_DETAIL_ROUTE = "encounterDetail/{source}/{primaryId}/{timestamp}"
private val DETAIL_TWO_COLUMN_MIN_WIDTH: Dp = 720.dp

private val topLevelRoutes = setOf(HOME_ROUTE, DETECTION_ROUTE, EVASION_ROUTE, DEVICES_ENCOUNTERS_ROUTE, SETTINGS_ROUTE)
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private const val APPROACH_ALERT_CHANNEL_ID = "argus_approach_alerts"
private const val APPROACH_ALERT_COOLDOWN_MS = 2 * 60 * 1000L
private const val TRACKER_ALERT_CHANNEL_ID = "argus_tracker_alerts"
private const val TRACKER_ALERT_COOLDOWN_MS = 5 * 60 * 1000L
private const val FOREIGN_SIGNAL_ALERT_CHANNEL_ID = "argus_foreign_signal_alerts"
private const val FOREIGN_SIGNAL_ALERT_COOLDOWN_MS = 5 * 60 * 1000L
private const val MAGNETIC_INCREASE_ALERT_CHANNEL_ID = "argus_magnetic_increase_alerts"
private const val MAGNETIC_INCREASE_ALERT_COOLDOWN_MS = 90 * 1000L
private const val MAGNETIC_INCREASE_DELTA_THRESHOLD_UT = 12.0
private const val MAGNETIC_INCREASE_MIN_CURRENT_UT = 55.0
private const val MAGNETIC_DISTURBANCE_UPPER_BOUND_UT = 65.0
private const val ALERT_LOG_MAX_ENTRIES = 400
private const val MAP_AUTO_FOCUS_MAX_DISTANCE_METERS = 160_000.0
private const val SIGNAL_INTEL_WINDOW_MS = 30L * 60L * 1000L
private const val SIGNAL_INTEL_MAX_ENCOUNTERS = 4000
private const val SIGNAL_INTEL_WINDOW_MINUTES = SIGNAL_INTEL_WINDOW_MS / 60_000L
private const val FOREIGN_RISK_WINDOW_MS = 30L * 60L * 1000L
private const val FOREIGN_RISK_MAX_ENCOUNTERS = 4000
private const val ACTION_OPEN_APPROACH_MAP = "dev.argus.tracker.action.OPEN_APPROACH_MAP"
private const val EXTRA_APPROACH_SOURCE = "extra_approach_source"
private const val EXTRA_APPROACH_PRIMARY_ID = "extra_approach_primary_id"

private enum class AlertLogType {
    APPROACH,
    TRACKER,
    FOREIGN_SIGNAL
}

private data class AlertLogEntry(
    val timestampEpochMs: Long,
    val type: AlertLogType,
    val source: String,
    val primaryId: String,
    val message: String,
    val confidence: Double?
)

private enum class TrackerRiskLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH
}

private data class TrackerRiskSignal(
    val level: TrackerRiskLevel,
    val confidence: Double,
    val uniqueLocationCells: Int,
    val spreadMeters: Double,
    val activeWindowMinutes: Double,
    val summary: String
)

private enum class ForeignSignalRiskLevel {
    QUIET,
    ELEVATED,
    HIGH,
    CRITICAL
}

private data class ForeignSignalRiskSignal(
    val level: ForeignSignalRiskLevel,
    val score: Int,
    val confidence: Double,
    val summary: String,
    val windowMinutes: Double,
    val sampleCount: Int,
    val cellularAnomalyScore: Double,
    val wifiAnomalyScore: Double,
    val bleAnomalyScore: Double,
    val gnssInterferenceScore: Double,
    val uwbActivityScore: Double,
    val rfTextureScore: Double,
    val acousticProxyScore: Double,
    val magneticProxyScore: Double,
    val directAcousticObserved: Boolean,
    val directMagneticObserved: Boolean,
    val unavailableSignals: List<String>
)

private data class DeviceItem(
    val source: String,
    val primaryId: String,
    val secondaryId: String?,
    val seenCount: Int,
    val lastSeenEpochMs: Long,
    val lastRssiDbm: Int?,
    val lastFrequencyMhz: Int?,
    val lastLat: Double?,
    val lastLon: Double?,
    val lastRawPayloadJson: String?,
    val lastProvenance: EncounterProvenance,
    val lastProvenanceNodeId: String?,
    val lastProvenanceOriginNodeId: String?,
    val lastProvenancePathNodeIds: String?,
    val lastProvenanceReceivedAtEpochMs: Long?,
    val lastProvenanceHopCount: Int,
    val hasChainLinkedData: Boolean,
    val chainLinkedPeerCount: Int,
    val isApproaching: Boolean,
    val approachConfidence: Double?,
    val approachDeltaMeters: Double?,
    val isInMotion: Boolean,
    val motionSpeedMps: Double?,
    val motionHeadingDeg: Double?,
    val isOwned: Boolean,
    val trackerRisk: TrackerRiskSignal?
)

private data class ApproachSignal(
    val isApproaching: Boolean,
    val confidence: Double,
    val deltaMeters: Double
)

private data class MotionSignal(
    val isInMotion: Boolean,
    val speedMps: Double,
    val headingDeg: Double,
    val sampleCount: Int
)

private data class SensorGateSettings(
    val wifiEnabled: Boolean,
    val bluetoothEnabled: Boolean,
    val cellularEnabled: Boolean,
    val remoteIdEnabled: Boolean,
    val uwbEnabled: Boolean,
    val sdrEnabled: Boolean,
    val directAcousticEnabled: Boolean,
    val directMagneticEnabled: Boolean
)

private data class InferredDeviceLocation(
    val lat: Double,
    val lon: Double,
    val estimatedRangeMeters: Double?
)

private data class ResolvedDeviceLocation(
    val lat: Double,
    val lon: Double,
    val method: String,
    val approximateRangeMeters: Double?,
    val resolvedFromTimestampEpochMs: Long
)

private data class RemoteIdBroadcastPoint(
    val lat: Double,
    val lon: Double,
    val timestampEpochMs: Long
)

private data class DeviceLocationCandidate(
    val source: String,
    val primaryId: String,
    val secondaryId: String?,
    val latestTimestampEpochMs: Long,
    val seenCount: Int,
    val encounters: List<Encounter>,
    val approximateLocation: DetectionLocation? = null,
    val approximateMethod: String? = null,
    val approximateRangeMeters: Double? = null,
    val hasChainLinkedData: Boolean = false,
    val chainLinkedPeerCount: Int = 0,
    val approachSignal: ApproachSignal? = null,
    val motionSignal: MotionSignal? = null,
    val trackerRisk: TrackerRiskSignal? = null,
    val isOwned: Boolean = false
)

private data class MeshCoverageNodeInsight(
    val nodeId: String,
    val displayName: String,
    val seenDevices: Int,
    val uniqueContributions: Int,
    val blindSpotsFilledByOthers: Int,
    val coverageSharePercent: Int,
    val blindSpotFillPercent: Int
)

private object OwnedDeviceRegistry {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_OWNED_DEVICE_KEYS = "owned_device_keys"

    fun keyFor(source: String, primaryId: String): String = "$source|$primaryId"

    fun read(context: android.content.Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_OWNED_DEVICE_KEYS, emptySet())?.toSet() ?: emptySet()
    }

    fun setOwned(context: android.content.Context, source: String, primaryId: String, owned: Boolean) {
        val current = read(context).toMutableSet()
        val key = keyFor(source, primaryId)
        if (owned) {
            current += key
        } else {
            current -= key
        }
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_OWNED_DEVICE_KEYS, current)
            .apply()
    }
}

private object AlertLogStore {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_ALERT_LOG_ENTRIES = "alert_log_entries"

    fun read(context: android.content.Context): List<AlertLogEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ALERT_LOG_ENTRIES, "[]") ?: "[]"
        val array = runCatching { org.json.JSONArray(raw) }.getOrElse { org.json.JSONArray() }
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val type = runCatching { AlertLogType.valueOf(item.optString("type")) }
                    .getOrDefault(AlertLogType.APPROACH)
                add(
                    AlertLogEntry(
                        timestampEpochMs = item.optLong("timestampEpochMs", 0L),
                        type = type,
                        source = item.optString("source", "UNKNOWN_RF"),
                        primaryId = item.optString("primaryId", "unknown"),
                        message = item.optString("message", ""),
                        confidence = if (item.has("confidence")) item.optDouble("confidence") else null
                    )
                )
            }
        }
    }

    fun append(context: android.content.Context, entry: AlertLogEntry) {
        val combined = (listOf(entry) + read(context))
            .sortedByDescending { it.timestampEpochMs }
            .take(ALERT_LOG_MAX_ENTRIES)
        write(context, combined)
    }

    fun appendAll(context: android.content.Context, entries: List<AlertLogEntry>) {
        if (entries.isEmpty()) return
        val combined = (entries + read(context))
            .sortedByDescending { it.timestampEpochMs }
            .take(ALERT_LOG_MAX_ENTRIES)
        write(context, combined)
    }

    fun clear(context: android.content.Context) {
        write(context, emptyList())
    }

    private fun write(context: android.content.Context, entries: List<AlertLogEntry>) {
        val array = org.json.JSONArray()
        entries.forEach { entry ->
            val obj = org.json.JSONObject().apply {
                put("timestampEpochMs", entry.timestampEpochMs)
                put("type", entry.type.name)
                put("source", entry.source)
                put("primaryId", entry.primaryId)
                put("message", entry.message)
                if (entry.confidence != null) put("confidence", entry.confidence)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ALERT_LOG_ENTRIES, array.toString())
            .apply()
    }
}

private object ApproachTrackStore {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_APPROACH_TRACK_STARTS = "approach_track_starts"

    fun getTrackStartEpochMs(context: android.content.Context, source: String, primaryId: String): Long? {
        val key = "${source}|${primaryId}"
        val raw = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getString(KEY_APPROACH_TRACK_STARTS, "{}")
            .orEmpty()
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        return obj.optLong(key, -1L).takeIf { it > 0L }
    }

    fun setTrackStartEpochMs(context: android.content.Context, source: String, primaryId: String, epochMs: Long) {
        val key = "${source}|${primaryId}"
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_APPROACH_TRACK_STARTS, "{}").orEmpty()
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        obj.put(key, epochMs.coerceAtLeast(0L))
        prefs.edit().putString(KEY_APPROACH_TRACK_STARTS, obj.toString()).apply()
    }
}

private object DeviceSpeedRecordStore {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_DEVICE_TOP_SPEEDS = "device_top_speeds_mps"

    fun getRecordSpeedMps(context: android.content.Context, source: String, primaryId: String): Double? {
        val key = "${source}|${primaryId}"
        val raw = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_TOP_SPEEDS, "{}")
            .orEmpty()
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        if (!obj.has(key)) return null
        return obj.optDouble(key).takeIf { it.isFinite() && it >= 0.0 }
    }

    fun updateIfHigher(
        context: android.content.Context,
        source: String,
        primaryId: String,
        currentSpeedMps: Double
    ): Boolean {
        if (!currentSpeedMps.isFinite() || currentSpeedMps <= 0.0) return false
        val key = "${source}|${primaryId}"
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DEVICE_TOP_SPEEDS, "{}").orEmpty()
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        val existing = obj.optDouble(key, Double.NaN)
        if (existing.isFinite() && existing >= currentSpeedMps) return false
        obj.put(key, currentSpeedMps)
        prefs.edit().putString(KEY_DEVICE_TOP_SPEEDS, obj.toString()).apply()
        return true
    }
}

private fun readSensorGateSettings(context: android.content.Context): SensorGateSettings =
    SensorGateSettings(
        wifiEnabled = ScanSettings.isWifiSensorEnabled(context),
        bluetoothEnabled = ScanSettings.isBleSensorEnabled(context),
        cellularEnabled = ScanSettings.isCellularSensorEnabled(context),
        remoteIdEnabled = ScanSettings.isRemoteIdSensorEnabled(context),
        uwbEnabled = ScanSettings.isUwbSensorEnabled(context),
        sdrEnabled = ScanSettings.isSdrSensorEnabled(context),
        directAcousticEnabled = ScanSettings.isForeignDirectAcousticEnabled(context),
        directMagneticEnabled = ScanSettings.isForeignDirectMagneticEnabled(context)
    )

@Composable
fun ArgusApp(notificationIntent: Intent? = null) {
    val context = LocalContext.current
    val app = context.applicationContext as ArgusApplication
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val viewModel = viewModel<ArgusViewModel>(
        factory = ArgusViewModel.Factory(app.container.repository)
    )
    var trackingActive by remember { mutableStateOf(false) }
    var sensorStatuses by remember { mutableStateOf(emptyList<SensorStatus>()) }
    var readinessItems by remember { mutableStateOf(emptyList<DetectionReadinessItem>()) }
    var scanIntervalSeconds by remember { mutableStateOf(ScanSettings.getScanIntervalSeconds(context)) }
    var liveMapUpdateIntervalSeconds by remember { mutableStateOf(ScanSettings.getLiveMapUpdateIntervalSeconds(context)) }
    var sourceScanIntervals by remember { mutableStateOf(ScanSettings.getAllSourceScanIntervalSeconds(context)) }
    var sourceLastScanEpochs by remember { mutableStateOf(ScanSettings.getAllSourceLastScanEpochMs(context)) }
    var sensorGateSettings by remember { mutableStateOf(readSensorGateSettings(context)) }
    var approachDetectionEnabled by remember { mutableStateOf(ScanSettings.isApproachDetectionEnabled(context)) }
    var approachNotificationsEnabled by remember { mutableStateOf(ScanSettings.isApproachNotificationsEnabled(context)) }
    var trackerNotificationsEnabled by remember { mutableStateOf(ScanSettings.isTrackerNotificationsEnabled(context)) }
    var magneticIncreaseNotificationsEnabled by remember { mutableStateOf(ScanSettings.isMagneticIncreaseNotificationsEnabled(context)) }
    var meshConnectivityNotificationsEnabled by remember { mutableStateOf(ScanSettings.isMeshConnectivityNotificationsEnabled(context)) }
    var meshWipeNotificationsEnabled by remember { mutableStateOf(ScanSettings.isMeshWipeNotificationsEnabled(context)) }
    var foreignSignalRiskEnabled by remember { mutableStateOf(ScanSettings.isForeignSignalRiskEnabled(context)) }
    var foreignSignalAlertsEnabled by remember { mutableStateOf(ScanSettings.isForeignSignalAlertsEnabled(context)) }
    var foreignSignalAlertThreshold by remember { mutableStateOf(ScanSettings.getForeignSignalAlertThreshold(context)) }
    var foreignDirectAcousticEnabled by remember { mutableStateOf(ScanSettings.isForeignDirectAcousticEnabled(context)) }
    var foreignDirectMagneticEnabled by remember { mutableStateOf(ScanSettings.isForeignDirectMagneticEnabled(context)) }
    var chainLinkEnabled by remember { mutableStateOf(ScanSettings.isChainLinkEnabled(context)) }
    var chainNodeId by remember { mutableStateOf(ScanSettings.getChainNodeId(context)) }
    var chainDeviceName by remember { mutableStateOf(ScanSettings.getChainDeviceName(context)) }
    var chainSharedSecret by remember { mutableStateOf(ScanSettings.getChainSharedSecret(context)) }
    var chainAutoSyncEnabled by remember { mutableStateOf(ScanSettings.isChainAutoSyncEnabled(context)) }
    var chainAutoSyncIntervalSeconds by remember { mutableStateOf(ScanSettings.getChainAutoSyncIntervalSeconds(context)) }
    var chainPersistentChannelEnabled by remember { mutableStateOf(ScanSettings.isChainPersistentChannelEnabled(context)) }
    var chainHeartbeatIntervalSeconds by remember { mutableStateOf(ScanSettings.getChainHeartbeatIntervalSeconds(context)) }
    var chainSharePreciseLocationEnabled by remember { mutableStateOf(ScanSettings.isChainSharePreciseLocationEnabled(context)) }
    var evasionProfile by remember {
        mutableStateOf(
            runCatching { EvasionProfile.valueOf(ScanSettings.getEvasionProfile(context)) }
                .getOrDefault(EvasionProfile.BALANCED)
        )
    }
    var evasionAutoEscalateEnabled by remember { mutableStateOf(ScanSettings.isEvasionAutoEscalateEnabled(context)) }
    var evasionEscalateDurationSeconds by remember { mutableStateOf(ScanSettings.getEvasionAutoEscalateDurationSeconds(context)) }
    var evasionJitterEnabled by remember { mutableStateOf(ScanSettings.isEvasionJitterEnabled(context)) }
    var evasionJitterPercent by remember { mutableStateOf(ScanSettings.getEvasionJitterPercent(context)) }
    var evasionBurstEnabled by remember { mutableStateOf(ScanSettings.isEvasionBurstEnabled(context)) }
    var evasionBurstWatchSeconds by remember { mutableStateOf(ScanSettings.getEvasionBurstWatchSeconds(context)) }
    var evasionBurstCooldownSeconds by remember { mutableStateOf(ScanSettings.getEvasionBurstCooldownSeconds(context)) }
    var evasionActionLog by remember { mutableStateOf(ScanSettings.getEvasionActionLog(context, 25)) }
    var evasionEscalationActiveUntilEpochMs by remember { mutableStateOf<Long?>(null) }
    var evasionEscalationBaselineProfile by remember { mutableStateOf<EvasionProfile?>(null) }
    var evasionBurstActiveUntilEpochMs by remember { mutableStateOf<Long?>(null) }
    var evasionBurstBaselineProfile by remember { mutableStateOf<EvasionProfile?>(null) }
    var ownedDeviceKeys by remember { mutableStateOf(OwnedDeviceRegistry.read(context)) }
    var alertLogs by remember { mutableStateOf(AlertLogStore.read(context)) }
    var lastScanDurationMs by remember { mutableStateOf(ScanSettings.getLastScanDurationMs(context)) }
    var sourceScanTimings by remember { mutableStateOf(ScanSettings.getSourceScanTimings(context)) }
    var autoAdjustScanIntervalEnabled by remember { mutableStateOf(ScanSettings.isAutoAdjustScanIntervalEnabled(context)) }
    var scanIntervalChangeEvents by remember { mutableStateOf(ScanSettings.getScanIntervalChangeEvents(context, 10)) }
    var autoAdjustConsecutiveOverruns by remember { mutableStateOf(mapOf<String, Int>()) }
    var autoAdjustStableCycles by remember { mutableStateOf(mapOf<String, Int>()) }
    var appThemeMode by remember {
        mutableStateOf(
            runCatching { AppThemeMode.valueOf(ScanSettings.getAppThemeMode(context)) }
                .getOrDefault(AppThemeMode.SYSTEM)
        )
    }
    var trackingStartMessage by remember { mutableStateOf<String?>(null) }
    var trackingStartMessageIsError by remember { mutableStateOf(false) }
    val approachStateByDevice = remember { mutableMapOf<String, Boolean>() }
    val lastApproachNotificationEpochByDevice = remember { mutableMapOf<String, Long>() }
    val trackerStateByDevice = remember { mutableMapOf<String, TrackerRiskLevel>() }
    val lastTrackerNotificationEpochByDevice = remember { mutableMapOf<String, Long>() }
    var lastForeignSignalAlertEpochMs by remember { mutableStateOf(0L) }
    var lastMagneticIncreaseAlertEpochMs by remember { mutableStateOf(0L) }
    var lastMagneticObservedSampleEpochMs by remember { mutableStateOf(0L) }
    var previousForeignSignalRiskLevel by remember { mutableStateOf(ForeignSignalRiskLevel.QUIET) }
    var lastWearStatusSignature by remember { mutableStateOf<String?>(null) }

    val recent by viewModel.recentEncounters.collectAsState()
    val recent100 by viewModel.recent100Encounters.collectAsState()
    val allEncounters by viewModel.allEncounters.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val chainMesh by app.container.chainLinkCoordinator.observeMesh().collectAsState()
    val lastScanEpochMs = remember(recent) { recent.maxOfOrNull { it.timestampEpochMs } }

    LaunchedEffect(chainMesh, alertLogs, allEncounters) {
        val peersTotal = chainMesh.peers.size
        val peersConnected = chainMesh.peers.count { it.state == ChainPeerState.CONNECTED }
        val meshDashboardUrl = chainMesh.peers
            .asSequence()
            .filter { it.state == ChainPeerState.CONNECTED }
            .mapNotNull { peer ->
                peer.host
                    .takeIf { host -> host.isNotBlank() }
                    ?.let { host -> "http://$host:8091/" }
            }
            .firstOrNull()
        val recentDevicePoints = buildWearDevicePoints(allEncounters)
        val latestAlert = alertLogs.maxByOrNull { it.timestampEpochMs }
        val alertMessage = latestAlert?.message?.takeIf { it.isNotBlank() } ?: "No recent alerts"
        val alertEpochMs = latestAlert?.timestampEpochMs
        val pointsSignature = recentDevicePoints.joinToString(separator = "|") {
            "${it.label}:${it.lat}:${it.lon}:${it.timestampEpochMs}"
        }
        val signature = "$peersTotal|$peersConnected|$alertMessage|${alertEpochMs ?: 0L}|${meshDashboardUrl.orEmpty()}|$pointsSignature"
        if (signature == lastWearStatusSignature) return@LaunchedEffect

        lastWearStatusSignature = signature
        WearStatusBridgePublisher.publishStatus(
            context = context,
            peersTotal = peersTotal,
            peersConnected = peersConnected,
            lastAlertMessage = alertMessage,
            lastAlertEpochMs = alertEpochMs,
            devicePoints = recentDevicePoints,
            dashboardMapUrlOverride = meshDashboardUrl
        )
    }

    LaunchedEffect(allEncounters, ownedDeviceKeys) {
        val updatedKeys = autoMarkConnectedWifiAsOwned(
            context = context,
            encounters = allEncounters,
            ownedDeviceKeys = ownedDeviceKeys
        )
        if (updatedKeys != ownedDeviceKeys) {
            ownedDeviceKeys = updatedKeys
        }
    }

    LaunchedEffect(notificationIntent) {
        val intent = notificationIntent ?: return@LaunchedEffect
        if (intent.action != ACTION_OPEN_APPROACH_MAP) return@LaunchedEffect
        val source = intent.getStringExtra(EXTRA_APPROACH_SOURCE)?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val primaryId = intent.getStringExtra(EXTRA_APPROACH_PRIMARY_ID)?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        navController.navigate("approachAlertMap/${Uri.encode(source)}/${Uri.encode(primaryId)}") {
            launchSingleTop = true
        }
    }
    val autoAdjustSuggestedIntervalSeconds = remember(sourceScanTimings, sensorGateSettings) {
        computeRecommendedIntervalSeconds(sourceScanTimings, sensorGateSettings)
    }
    val autoAdjustSuggestedBySource = remember(sourceScanTimings) {
        sourceScanTimings.associate { timing ->
            timing.sourceType to suggestSafeIntervalSeconds(timing.p95DurationMs)
        }
    }

    suspend fun applyScanInterval(seconds: Long, sourceLabel: String, reasonCode: String) {
        val previous = scanIntervalSeconds
        if (seconds == previous) return
        scanIntervalSeconds = seconds
        ScanSettings.setScanIntervalSeconds(context, seconds)
        ScanSettings.appendScanIntervalChangeEvent(
            context = context,
            fromSeconds = previous,
            toSeconds = seconds,
            reason = reasonCode
        )
        scanIntervalChangeEvents = ScanSettings.getScanIntervalChangeEvents(context, 10)
        if (trackingActive) {
            WorkScheduler.stop(context)
            val restartResult = WorkScheduler.startAndVerify(context)
            trackingStartMessage = if (restartResult.success) {
                "$sourceLabel set interval to ${ScanSettings.formatInterval(seconds)} and restarted tracking."
            } else {
                "$sourceLabel set interval to ${ScanSettings.formatInterval(seconds)}, but restart failed: ${restartResult.message}"
            }
            trackingStartMessageIsError = !restartResult.success
            trackingActive = WorkScheduler.isTrackingActive(context)
        } else {
            trackingStartMessage = "$sourceLabel set interval to ${ScanSettings.formatInterval(seconds)}."
            trackingStartMessageIsError = false
        }
    }

    fun minimumEnabledSourceIntervalSeconds(): Long {
        val enabled = enabledSourceTypes(sensorGateSettings)
        if (enabled.isEmpty()) return scanIntervalSeconds
        return enabled
            .map { source -> sourceScanIntervals[source] ?: ScanSettings.DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS }
            .minOrNull()
            ?.coerceAtLeast(1L)
            ?: scanIntervalSeconds
    }

    fun appendEvasionAction(action: String, detail: String) {
        ScanSettings.appendEvasionActionLog(context, action = action, detail = detail)
        evasionActionLog = ScanSettings.getEvasionActionLog(context, 25)
    }

    fun evasionBaseCadence(profile: EvasionProfile): Pair<Map<String, Long>, Long> = when (profile) {
        EvasionProfile.QUIET -> {
            ScanSettings.SOURCE_TYPES.associateWith { 300L } to (15L * 60L)
        }

        EvasionProfile.BALANCED -> {
            mapOf(
                "wifi" to 15L,
                "wifi_direct" to 30L,
                "ble" to 15L,
                "bt_classic" to 30L,
                "cellular" to 30L,
                "remote_id" to 60L,
                "uwb" to 30L,
                "sdr" to 30L,
                "acoustic" to 30L,
                "magnetic" to 30L
            ) to 15L
        }

        EvasionProfile.WATCH -> {
            ScanSettings.SOURCE_TYPES.associateWith { 3L } to 3L
        }
    }

    fun jitterSeconds(baseSeconds: Long, percent: Int): Long {
        val pct = percent.coerceIn(1, 50) / 100.0
        val factor = Random.nextDouble(1.0 - pct, 1.0 + pct)
        val raw = (baseSeconds * factor).toLong().coerceAtLeast(1L)
        return raw.coerceIn(ScanSettings.MIN_SOURCE_SCAN_INTERVAL_SECONDS, ScanSettings.MAX_SOURCE_SCAN_INTERVAL_SECONDS)
    }

    fun nearestAllowedGlobalInterval(seconds: Long): Long {
        return ScanSettings.ALLOWED_INTERVALS_SECONDS.minByOrNull { abs(it - seconds) }
            ?: ScanSettings.DEFAULT_SCAN_INTERVAL_SECONDS
    }

    suspend fun applyEvasionCadence(
        profile: EvasionProfile,
        reasonCode: String,
        jitterEnabled: Boolean,
        jitterPercent: Int,
        appendLog: Boolean
    ) {
        val (baseSourceIntervals, baseGlobalTick) = evasionBaseCadence(profile)
        val resolvedSourceIntervals = if (jitterEnabled) {
            baseSourceIntervals.mapValues { (_, value) -> jitterSeconds(value, jitterPercent) }
        } else {
            baseSourceIntervals
        }

        resolvedSourceIntervals.forEach { (source, seconds) ->
            ScanSettings.setSourceScanIntervalSeconds(context, source, seconds)
        }
        sourceScanIntervals = ScanSettings.getAllSourceScanIntervalSeconds(context)

        val targetGlobalTick = if (jitterEnabled) {
            nearestAllowedGlobalInterval(jitterSeconds(baseGlobalTick, jitterPercent))
        } else {
            nearestAllowedGlobalInterval(baseGlobalTick)
        }

        if (scanIntervalSeconds != targetGlobalTick) {
            ScanSettings.appendScanIntervalChangeEvent(
                context = context,
                fromSeconds = scanIntervalSeconds,
                toSeconds = targetGlobalTick,
                reason = reasonCode
            )
            scanIntervalSeconds = targetGlobalTick
            ScanSettings.setScanIntervalSeconds(context, targetGlobalTick)
            scanIntervalChangeEvents = ScanSettings.getScanIntervalChangeEvents(context, 10)
        }

        if (appendLog) {
            appendEvasionAction(
                action = "JITTER_APPLIED",
                detail = "${profile.name} cadence jittered by +/-${jitterPercent}%"
            )
        }
    }

    suspend fun applyEvasionProfile(
        profile: EvasionProfile,
        reason: String,
        allowClearEscalation: Boolean = false
    ) {
        val previous = evasionProfile

        val wifiEnabled: Boolean
        val bluetoothEnabled: Boolean
        val cellularEnabled: Boolean
        val remoteIdEnabled: Boolean

        when (profile) {
            EvasionProfile.QUIET -> {
                wifiEnabled = false
                bluetoothEnabled = false
                cellularEnabled = false
                remoteIdEnabled = false
            }

            EvasionProfile.BALANCED -> {
                wifiEnabled = true
                bluetoothEnabled = true
                cellularEnabled = true
                remoteIdEnabled = false
            }

            EvasionProfile.WATCH -> {
                wifiEnabled = true
                bluetoothEnabled = true
                cellularEnabled = true
                remoteIdEnabled = true
            }
        }

        ScanSettings.setWifiSensorEnabled(context, wifiEnabled)
        ScanSettings.setBleSensorEnabled(context, bluetoothEnabled)
        ScanSettings.setCellularSensorEnabled(context, cellularEnabled)
        ScanSettings.setRemoteIdSensorEnabled(context, remoteIdEnabled)
        RemoteIdForegroundServiceController.ensureState(context)
        sensorGateSettings = readSensorGateSettings(context)

        applyEvasionCadence(
            profile = profile,
            reasonCode = "evasion-${profile.name.lowercase()}",
            jitterEnabled = evasionJitterEnabled,
            jitterPercent = evasionJitterPercent,
            appendLog = false
        )

        if (trackingActive) {
            WorkScheduler.stop(context)
            val restartResult = WorkScheduler.startAndVerify(context)
            trackingActive = WorkScheduler.isTrackingActive(context)
            appendEvasionAction(
                action = "TRACKING_RESTART",
                detail = if (restartResult.success) {
                    "Restarted for ${profile.name} policy"
                } else {
                    "Restart failed: ${restartResult.message}"
                }
            )
        }

        evasionProfile = profile
        ScanSettings.setEvasionProfile(context, profile.name)
        appendEvasionAction(
            action = "PROFILE_APPLIED",
            detail = "${previous.name} -> ${profile.name} ($reason)"
        )

        if (allowClearEscalation && evasionEscalationActiveUntilEpochMs != null && profile != EvasionProfile.WATCH) {
            evasionEscalationActiveUntilEpochMs = null
            evasionEscalationBaselineProfile = null
            appendEvasionAction(
                action = "AUTO_ESCALATION_CANCELLED",
                detail = "Manual profile change to ${profile.name}"
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshSummary()
        trackingActive = WorkScheduler.isTrackingActive(context)
        sensorGateSettings = readSensorGateSettings(context)
        chainLinkEnabled = ScanSettings.isChainLinkEnabled(context)
        chainNodeId = ScanSettings.getChainNodeId(context)
        chainDeviceName = ScanSettings.getChainDeviceName(context)
        chainSharedSecret = ScanSettings.getChainSharedSecret(context)
        chainAutoSyncEnabled = ScanSettings.isChainAutoSyncEnabled(context)
        chainAutoSyncIntervalSeconds = ScanSettings.getChainAutoSyncIntervalSeconds(context)
        chainPersistentChannelEnabled = ScanSettings.isChainPersistentChannelEnabled(context)
        chainHeartbeatIntervalSeconds = ScanSettings.getChainHeartbeatIntervalSeconds(context)
        chainSharePreciseLocationEnabled = ScanSettings.isChainSharePreciseLocationEnabled(context)
        evasionProfile = runCatching { EvasionProfile.valueOf(ScanSettings.getEvasionProfile(context)) }
            .getOrDefault(EvasionProfile.BALANCED)
        evasionAutoEscalateEnabled = ScanSettings.isEvasionAutoEscalateEnabled(context)
        evasionEscalateDurationSeconds = ScanSettings.getEvasionAutoEscalateDurationSeconds(context)
        evasionJitterEnabled = ScanSettings.isEvasionJitterEnabled(context)
        evasionJitterPercent = ScanSettings.getEvasionJitterPercent(context)
        evasionBurstEnabled = ScanSettings.isEvasionBurstEnabled(context)
        evasionBurstWatchSeconds = ScanSettings.getEvasionBurstWatchSeconds(context)
        evasionBurstCooldownSeconds = ScanSettings.getEvasionBurstCooldownSeconds(context)
        evasionActionLog = ScanSettings.getEvasionActionLog(context, 25)
        liveMapUpdateIntervalSeconds = ScanSettings.getLiveMapUpdateIntervalSeconds(context)
        sourceScanIntervals = ScanSettings.getAllSourceScanIntervalSeconds(context)
        sourceLastScanEpochs = ScanSettings.getAllSourceLastScanEpochMs(context)
        foreignSignalRiskEnabled = ScanSettings.isForeignSignalRiskEnabled(context)
        foreignSignalAlertsEnabled = ScanSettings.isForeignSignalAlertsEnabled(context)
        foreignSignalAlertThreshold = ScanSettings.getForeignSignalAlertThreshold(context)
        foreignDirectAcousticEnabled = ScanSettings.isForeignDirectAcousticEnabled(context)
        foreignDirectMagneticEnabled = ScanSettings.isForeignDirectMagneticEnabled(context)
        lastScanDurationMs = ScanSettings.getLastScanDurationMs(context)
        sourceScanTimings = ScanSettings.getSourceScanTimings(context)
        autoAdjustScanIntervalEnabled = ScanSettings.isAutoAdjustScanIntervalEnabled(context)
        scanIntervalChangeEvents = ScanSettings.getScanIntervalChangeEvents(context, 10)
        sensorStatuses = SensorStatusProvider.read(context)
        readinessItems = DetectionReadinessAdvisor.evaluate(context)
        MeshForegroundServiceController.ensureState(context)
    }

    LaunchedEffect(autoAdjustScanIntervalEnabled, trackingActive, sourceScanTimings, sensorGateSettings, sourceScanIntervals, scanIntervalSeconds) {
        if (!autoAdjustScanIntervalEnabled || !trackingActive) return@LaunchedEffect

        while (true) {
            val timingBySource = sourceScanTimings.associateBy { it.sourceType }
            enabledSourceTypes(sensorGateSettings).forEach { sourceType ->
                val timing = timingBySource[sourceType] ?: return@forEach
                val currentSourceInterval = sourceScanIntervals[sourceType]
                    ?: ScanSettings.DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS
                val currentSourceIntervalMs = currentSourceInterval * 1000L
                val overrun = timing.lastDurationMs > currentSourceIntervalMs
                val suggestedSource = autoAdjustSuggestedBySource[sourceType] ?: currentSourceInterval

                val overrunCount = autoAdjustConsecutiveOverruns[sourceType] ?: 0
                val stableCount = autoAdjustStableCycles[sourceType] ?: 0

                if (overrun) {
                    autoAdjustConsecutiveOverruns = autoAdjustConsecutiveOverruns + (sourceType to (overrunCount + 1))
                    autoAdjustStableCycles = autoAdjustStableCycles + (sourceType to 0)
                    if (overrunCount + 1 >= 2) {
                        val updated = (currentSourceInterval + 1L)
                            .coerceAtMost(ScanSettings.MAX_SOURCE_SCAN_INTERVAL_SECONDS)
                        if (updated != currentSourceInterval) {
                            ScanSettings.setSourceScanIntervalSeconds(context, sourceType, updated)
                            sourceScanIntervals = sourceScanIntervals + (sourceType to updated)
                            ScanSettings.appendScanIntervalChangeEvent(
                                context = context,
                                fromSeconds = currentSourceInterval,
                                toSeconds = updated,
                                reason = "auto-overrun-$sourceType"
                            )
                            scanIntervalChangeEvents = ScanSettings.getScanIntervalChangeEvents(context, 10)
                        }
                        autoAdjustConsecutiveOverruns = autoAdjustConsecutiveOverruns + (sourceType to 0)
                    }
                } else {
                    autoAdjustConsecutiveOverruns = autoAdjustConsecutiveOverruns + (sourceType to 0)
                    autoAdjustStableCycles = autoAdjustStableCycles + (sourceType to (stableCount + 1))
                    if (stableCount + 1 >= 10 && currentSourceInterval > suggestedSource) {
                        val updated = (currentSourceInterval - 1L)
                            .coerceAtLeast(suggestedSource)
                            .coerceAtLeast(ScanSettings.MIN_SOURCE_SCAN_INTERVAL_SECONDS)
                        if (updated != currentSourceInterval) {
                            ScanSettings.setSourceScanIntervalSeconds(context, sourceType, updated)
                            sourceScanIntervals = sourceScanIntervals + (sourceType to updated)
                            ScanSettings.appendScanIntervalChangeEvent(
                                context = context,
                                fromSeconds = currentSourceInterval,
                                toSeconds = updated,
                                reason = "auto-stable-$sourceType"
                            )
                            scanIntervalChangeEvents = ScanSettings.getScanIntervalChangeEvents(context, 10)
                        }
                        autoAdjustStableCycles = autoAdjustStableCycles + (sourceType to 0)
                    }
                }
            }

            delay(2000)
        }
    }

    LaunchedEffect(chainLinkEnabled, chainAutoSyncEnabled, chainAutoSyncIntervalSeconds, chainSharedSecret) {
        if (!chainLinkEnabled || !chainAutoSyncEnabled) return@LaunchedEffect
        if (chainSharedSecret.isBlank()) return@LaunchedEffect

        while (true) {
            runCatching { app.container.chainLinkCoordinator.syncNow() }
            delay(chainAutoSyncIntervalSeconds * 1000L)
        }
    }

    LaunchedEffect(chainLinkEnabled) {
        if (!chainLinkEnabled) return@LaunchedEffect
        while (true) {
            runCatching { app.container.chainLinkCoordinator.refreshPeers() }
            delay(5000)
        }
    }

    LaunchedEffect(context) {
        while (true) {
            trackingActive = WorkScheduler.isTrackingActive(context)
            lastScanDurationMs = ScanSettings.getLastScanDurationMs(context)
            sourceScanTimings = ScanSettings.getSourceScanTimings(context)
            sourceScanIntervals = ScanSettings.getAllSourceScanIntervalSeconds(context)
            sourceLastScanEpochs = ScanSettings.getAllSourceLastScanEpochMs(context)
            scanIntervalChangeEvents = ScanSettings.getScanIntervalChangeEvents(context, 10)
            delay(1000)
        }
    }

    LaunchedEffect(allEncounters, ownedDeviceKeys, approachDetectionEnabled, approachNotificationsEnabled) {
        if (!approachDetectionEnabled) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val devices = withContext(Dispatchers.Default) {
            buildDeviceItems(
                encounters = allEncounters,
                approachDetectionEnabled = true,
                ownedDeviceKeys = ownedDeviceKeys
            )
        }
        val seenKeys = mutableSetOf<String>()
        val approachLogsToAppend = mutableListOf<AlertLogEntry>()

        devices.forEach { device ->
            val key = "${device.source}|${device.primaryId}"
            seenKeys += key
            val wasApproaching = approachStateByDevice[key] ?: false
            val isApproaching = device.isApproaching

            if (isApproaching && !wasApproaching) {
                val confidencePct = ((device.approachConfidence ?: 0.0) * 100.0).toInt().coerceIn(0, 100)
                val trend = device.approachDeltaMeters
                    ?.takeIf { it > 0.0 }
                    ?.let { formatDistanceFeetMiles(it) }
                    ?: "unknown"
                approachLogsToAppend += AlertLogEntry(
                    timestampEpochMs = now,
                    type = AlertLogType.APPROACH,
                    source = device.source,
                    primaryId = device.primaryId,
                    message = "Approaching ${listSourceLabel(device.source, device.secondaryId)} ${device.primaryId}${if (device.isOwned) " [OWNED DEVICE]" else ""} (${confidencePct}% confidence, trend ${trend})",
                    confidence = device.approachConfidence
                )

                val lastNotified = lastApproachNotificationEpochByDevice[key] ?: 0L
                if (approachNotificationsEnabled && hasPostNotificationsPermission(context) && now - lastNotified >= APPROACH_ALERT_COOLDOWN_MS) {
                    ensureApproachNotificationChannel(context)
                    sendApproachNotification(context, device)
                    lastApproachNotificationEpochByDevice[key] = now
                }
            }

            approachStateByDevice[key] = isApproaching
        }

        if (approachLogsToAppend.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                AlertLogStore.appendAll(context, approachLogsToAppend)
            }
            alertLogs = AlertLogStore.read(context)
        }

        val staleKeys = approachStateByDevice.keys.filter { it !in seenKeys }
        staleKeys.forEach { staleKey ->
            approachStateByDevice.remove(staleKey)
            lastApproachNotificationEpochByDevice.remove(staleKey)
        }
    }

    LaunchedEffect(
        allEncounters,
        ownedDeviceKeys,
        approachDetectionEnabled,
        trackerNotificationsEnabled,
        evasionAutoEscalateEnabled,
        evasionProfile,
        evasionEscalateDurationSeconds
    ) {
        if (!approachDetectionEnabled) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val devices = withContext(Dispatchers.Default) {
            buildDeviceItems(
                encounters = allEncounters,
                approachDetectionEnabled = true,
                ownedDeviceKeys = ownedDeviceKeys
            )
        }
        val seenKeys = mutableSetOf<String>()
        val trackerLogsToAppend = mutableListOf<AlertLogEntry>()

        devices.forEach { device ->
            val key = "${device.source}|${device.primaryId}"
            seenKeys += key
            val currentRisk = device.trackerRisk?.level ?: TrackerRiskLevel.NONE
            val previousRisk = trackerStateByDevice[key] ?: TrackerRiskLevel.NONE

            if (currentRisk == TrackerRiskLevel.HIGH && previousRisk != TrackerRiskLevel.HIGH && !device.isOwned) {
                trackerLogsToAppend += AlertLogEntry(
                    timestampEpochMs = now,
                    type = AlertLogType.TRACKER,
                    source = device.source,
                    primaryId = device.primaryId,
                    message = "Tracker risk HIGH for ${listSourceLabel(device.source, device.secondaryId)} ${device.primaryId}",
                    confidence = device.trackerRisk?.confidence
                )

                val lastNotified = lastTrackerNotificationEpochByDevice[key] ?: 0L
                if (trackerNotificationsEnabled && hasPostNotificationsPermission(context) && now - lastNotified >= TRACKER_ALERT_COOLDOWN_MS) {
                    ensureTrackerNotificationChannel(context)
                    sendTrackerRiskNotification(context, device)
                    lastTrackerNotificationEpochByDevice[key] = now
                }

                if (evasionAutoEscalateEnabled && evasionProfile != EvasionProfile.WATCH) {
                    evasionEscalationBaselineProfile = evasionProfile
                    evasionEscalationActiveUntilEpochMs = now + (evasionEscalateDurationSeconds * 1000L)
                    applyEvasionProfile(
                        profile = EvasionProfile.WATCH,
                        reason = "Auto escalation: tracker risk HIGH",
                        allowClearEscalation = false
                    )
                    appendEvasionAction(
                        action = "AUTO_ESCALATION_STARTED",
                        detail = "Tracker HIGH on ${device.source}:${device.primaryId} for ${evasionEscalateDurationSeconds}s"
                    )
                }
            }

            trackerStateByDevice[key] = currentRisk
        }

        if (trackerLogsToAppend.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                AlertLogStore.appendAll(context, trackerLogsToAppend)
            }
            alertLogs = AlertLogStore.read(context)
        }

        val staleKeys = trackerStateByDevice.keys.filter { it !in seenKeys }
        staleKeys.forEach { staleKey ->
            trackerStateByDevice.remove(staleKey)
            lastTrackerNotificationEpochByDevice.remove(staleKey)
        }
    }

    LaunchedEffect(
        allEncounters,
        foreignSignalRiskEnabled,
        foreignSignalAlertsEnabled,
        foreignSignalAlertThreshold
    ) {
        val risk = withContext(Dispatchers.Default) { analyzeForeignSignalRisk(allEncounters) }
        val currentLevel = risk?.level ?: ForeignSignalRiskLevel.QUIET
        val previousLevel = previousForeignSignalRiskLevel

        if (!foreignSignalRiskEnabled) {
            previousForeignSignalRiskLevel = currentLevel
            return@LaunchedEffect
        }

        val thresholdLevel = when (foreignSignalAlertThreshold.uppercase(Locale.US)) {
            "CRITICAL" -> ForeignSignalRiskLevel.CRITICAL
            else -> ForeignSignalRiskLevel.HIGH
        }

        val meetsThreshold = currentLevel.ordinal >= thresholdLevel.ordinal
        val crossedUp = previousLevel.ordinal < thresholdLevel.ordinal && meetsThreshold
        val now = System.currentTimeMillis()

        if (risk != null && crossedUp) {
            withContext(Dispatchers.IO) {
                AlertLogStore.append(
                    context,
                    AlertLogEntry(
                        timestampEpochMs = now,
                        type = AlertLogType.FOREIGN_SIGNAL,
                        source = "UNKNOWN_RF",
                        primaryId = "environment",
                        message = "Foreign signal risk ${risk.level.name} (${risk.score}/100): ${risk.summary}",
                        confidence = risk.confidence
                    )
                )
            }
            alertLogs = AlertLogStore.read(context)

            if (foreignSignalAlertsEnabled && hasPostNotificationsPermission(context) && now - lastForeignSignalAlertEpochMs >= FOREIGN_SIGNAL_ALERT_COOLDOWN_MS) {
                ensureForeignSignalNotificationChannel(context)
                sendForeignSignalRiskNotification(context, risk)
                lastForeignSignalAlertEpochMs = now
            }
        }

        previousForeignSignalRiskLevel = currentLevel
    }

    LaunchedEffect(allEncounters, foreignDirectMagneticEnabled) {
        if (!foreignDirectMagneticEnabled) return@LaunchedEffect

        val recentMagneticSamples = allEncounters
            .asSequence()
            .filter { isDirectSignalChannel(it, "magnetic") }
            .mapNotNull { encounter ->
                val payload = parseEncounterPayload(encounter) ?: return@mapNotNull null
                val magnitude = payload
                    .optDouble("magnitudeMicroTesla", Double.NaN)
                    .takeIf { it.isFinite() }
                    ?: return@mapNotNull null
                encounter.timestampEpochMs to magnitude
            }
            .sortedBy { it.first }
            .toList()
            .takeLast(2)

        if (recentMagneticSamples.size < 2) return@LaunchedEffect

        val previous = recentMagneticSamples[0]
        val current = recentMagneticSamples[1]
        if (current.first <= lastMagneticObservedSampleEpochMs) return@LaunchedEffect

        val deltaMicroTesla = current.second - previous.second
        val crossedDisturbanceBand =
            previous.second < MAGNETIC_DISTURBANCE_UPPER_BOUND_UT &&
                current.second >= MAGNETIC_DISTURBANCE_UPPER_BOUND_UT
        val sharpIncrease =
            deltaMicroTesla >= MAGNETIC_INCREASE_DELTA_THRESHOLD_UT &&
                current.second >= MAGNETIC_INCREASE_MIN_CURRENT_UT

        val now = System.currentTimeMillis()
        if ((crossedDisturbanceBand || sharpIncrease) &&
            magneticIncreaseNotificationsEnabled &&
            hasPostNotificationsPermission(context) &&
            now - lastMagneticIncreaseAlertEpochMs >= MAGNETIC_INCREASE_ALERT_COOLDOWN_MS
        ) {
            ensureMagneticIncreaseNotificationChannel(context)
            sendMagneticIncreaseNotification(
                context = context,
                previousMagnitudeMicroTesla = previous.second,
                currentMagnitudeMicroTesla = current.second,
                deltaMicroTesla = deltaMicroTesla
            )
            lastMagneticIncreaseAlertEpochMs = now
        }

        lastMagneticObservedSampleEpochMs = current.first
    }

    LaunchedEffect(evasionEscalationActiveUntilEpochMs) {
        val until = evasionEscalationActiveUntilEpochMs ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        val remaining = (until - now).coerceAtLeast(0L)
        if (remaining > 0L) {
            delay(remaining)
        }
        if (evasionEscalationActiveUntilEpochMs == null) return@LaunchedEffect
        val baseline = evasionEscalationBaselineProfile ?: EvasionProfile.BALANCED
        applyEvasionProfile(
            profile = baseline,
            reason = "Auto escalation timeout",
            allowClearEscalation = false
        )
        appendEvasionAction(
            action = "AUTO_ESCALATION_ENDED",
            detail = "Reverted to ${baseline.name}"
        )
        evasionEscalationActiveUntilEpochMs = null
        evasionEscalationBaselineProfile = null
    }

    LaunchedEffect(
        evasionJitterEnabled,
        evasionJitterPercent,
        evasionProfile,
        evasionEscalationActiveUntilEpochMs,
        evasionBurstActiveUntilEpochMs
    ) {
        if (!evasionJitterEnabled) return@LaunchedEffect
        while (evasionJitterEnabled) {
            delay(45_000L)
            if (!evasionJitterEnabled) break
            if (evasionEscalationActiveUntilEpochMs != null) continue
            if (evasionBurstActiveUntilEpochMs != null) continue
            applyEvasionCadence(
                profile = evasionProfile,
                reasonCode = "evasion-jitter-${evasionProfile.name.lowercase()}",
                jitterEnabled = true,
                jitterPercent = evasionJitterPercent,
                appendLog = true
            )
        }
    }

    LaunchedEffect(
        evasionBurstEnabled,
        evasionBurstWatchSeconds,
        evasionBurstCooldownSeconds,
        evasionEscalationActiveUntilEpochMs,
        evasionProfile
    ) {
        if (!evasionBurstEnabled) return@LaunchedEffect
        while (evasionBurstEnabled) {
            if (evasionEscalationActiveUntilEpochMs != null || evasionProfile == EvasionProfile.WATCH) {
                delay(5_000L)
                continue
            }

            delay(evasionBurstCooldownSeconds * 1000L)
            if (!evasionBurstEnabled) break
            if (evasionEscalationActiveUntilEpochMs != null || evasionProfile == EvasionProfile.WATCH) continue

            val baseline = evasionProfile
            evasionBurstBaselineProfile = baseline
            val burstStart = System.currentTimeMillis()
            evasionBurstActiveUntilEpochMs = burstStart + (evasionBurstWatchSeconds * 1000L)

            applyEvasionProfile(
                profile = EvasionProfile.WATCH,
                reason = "Burst cycle start",
                allowClearEscalation = false
            )
            appendEvasionAction(
                action = "BURST_STARTED",
                detail = "WATCH for ${ScanSettings.formatInterval(evasionBurstWatchSeconds)}"
            )

            delay(evasionBurstWatchSeconds * 1000L)

            if (evasionEscalationActiveUntilEpochMs == null && evasionBurstEnabled && evasionProfile == EvasionProfile.WATCH) {
                val revertProfile = evasionBurstBaselineProfile ?: EvasionProfile.BALANCED
                applyEvasionProfile(
                    profile = revertProfile,
                    reason = "Burst cooldown return",
                    allowClearEscalation = false
                )
                appendEvasionAction(
                    action = "BURST_ENDED",
                    detail = "Reverted to ${revertProfile.name}"
                )
            }
            evasionBurstActiveUntilEpochMs = null
            evasionBurstBaselineProfile = null
        }
    }

    LaunchedEffect(allEncounters, ownedDeviceKeys) {
        val devices = withContext(Dispatchers.Default) {
            buildDeviceItems(
                encounters = allEncounters,
                approachDetectionEnabled = approachDetectionEnabled,
                ownedDeviceKeys = ownedDeviceKeys
            )
        }
        devices.forEach { device ->
            val speed = device.motionSpeedMps ?: return@forEach
            DeviceSpeedRecordStore.updateIfHigher(
                context = context,
                source = device.source,
                primaryId = device.primaryId,
                currentSpeedMps = speed
            )
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: HOME_ROUTE
    val darkThemeEnabled = when (appThemeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    MaterialTheme(colorScheme = if (darkThemeEnabled) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (currentRoute in topLevelRoutes) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentRoute == HOME_ROUTE,
                            onClick = { navController.navigate(HOME_ROUTE) },
                            icon = { Text("H") },
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == DETECTION_ROUTE,
                            onClick = { navController.navigate(DETECTION_ROUTE) },
                            icon = { Text("R") },
                            label = { Text("Detection") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == EVASION_ROUTE,
                            onClick = { navController.navigate(EVASION_ROUTE) },
                            icon = { Text("E") },
                            label = { Text("Evasion") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == DEVICES_ENCOUNTERS_ROUTE,
                            onClick = { navController.navigate(DEVICES_ENCOUNTERS_ROUTE) },
                            icon = { Text("D") },
                            label = { Text("Devices & Encounters") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == SETTINGS_ROUTE,
                            onClick = { navController.navigate(SETTINGS_ROUTE) },
                            icon = { Text("S") },
                            label = { Text("Settings") }
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = HOME_ROUTE,
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
            ) {
            composable(HOME_ROUTE) {
                HomePage(
                    trackingActive = trackingActive,
                    lastScanEpochMs = lastScanEpochMs,
                    lastScanDurationMs = lastScanDurationMs,
                    scanIntervalSeconds = scanIntervalSeconds,
                    sourceScanTimings = sourceScanTimings,
                    sourceScanIntervals = sourceScanIntervals,
                    sourceLastScanEpochs = sourceLastScanEpochs,
                    sensorStatuses = sensorStatuses,
                    sensorGateSettings = sensorGateSettings,
                    summary = summary,
                    onStart = {
                        scope.launch {
                            val startResult = WorkScheduler.startAndVerify(context)
                            trackingStartMessage = startResult.message
                            trackingStartMessageIsError = !startResult.success
                            trackingActive = WorkScheduler.isTrackingActive(context)
                            sensorGateSettings = readSensorGateSettings(context)
                            sensorStatuses = SensorStatusProvider.read(context)
                            readinessItems = DetectionReadinessAdvisor.evaluate(context)
                        }
                    },
                    onStop = {
                        WorkScheduler.stop(context)
                        viewModel.refreshSummary()
                        scope.launch {
                            trackingActive = WorkScheduler.isTrackingActive(context)
                            sensorGateSettings = readSensorGateSettings(context)
                            sensorStatuses = SensorStatusProvider.read(context)
                            readinessItems = DetectionReadinessAdvisor.evaluate(context)
                            trackingStartMessage = "Tracking stopped."
                            trackingStartMessageIsError = false
                        }
                    },
                    onRefresh = {
                        viewModel.refreshSummary()
                        scope.launch {
                            trackingActive = WorkScheduler.isTrackingActive(context)
                            sensorGateSettings = readSensorGateSettings(context)
                            sensorStatuses = SensorStatusProvider.read(context)
                            readinessItems = DetectionReadinessAdvisor.evaluate(context)
                            trackingStartMessage = "Status refreshed."
                            trackingStartMessageIsError = false
                        }
                    },
                    onSensorGateChanged = { sensor, enabled ->
                        scope.launch {
                            when (sensor) {
                                "wifi" -> ScanSettings.setWifiSensorEnabled(context, enabled)
                                "bluetooth" -> ScanSettings.setBleSensorEnabled(context, enabled)
                                "cellular" -> ScanSettings.setCellularSensorEnabled(context, enabled)
                                "remote_id" -> ScanSettings.setRemoteIdSensorEnabled(context, enabled)
                                "uwb" -> ScanSettings.setUwbSensorEnabled(context, enabled)
                                "sdr" -> ScanSettings.setSdrSensorEnabled(context, enabled)
                                "direct_acoustic" -> ScanSettings.setForeignDirectAcousticEnabled(context, enabled)
                                "direct_magnetic" -> ScanSettings.setForeignDirectMagneticEnabled(context, enabled)
                            }
                            RemoteIdForegroundServiceController.ensureState(context)
                            sensorGateSettings = readSensorGateSettings(context)
                            foreignDirectAcousticEnabled = ScanSettings.isForeignDirectAcousticEnabled(context)
                            foreignDirectMagneticEnabled = ScanSettings.isForeignDirectMagneticEnabled(context)
                            sensorStatuses = SensorStatusProvider.read(context)
                            trackingStartMessage = "Sensor gating updated."
                            trackingStartMessageIsError = false
                        }
                    },
                    onClearEncounters = {
                        scope.launch {
                            app.container.repository.clearEncounters()
                            viewModel.refreshSummary()
                        }
                    },
                    onClearDevices = {
                        scope.launch {
                            app.container.repository.clearDevices()
                            viewModel.refreshSummary()
                        }
                    },
                    startMessage = trackingStartMessage,
                    startMessageIsError = trackingStartMessageIsError
                )
            }

            composable(SETTINGS_ROUTE) {
                AppSettingsPage(
                    scanIntervalSeconds = scanIntervalSeconds,
                    liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds,
                    sourceScanIntervals = sourceScanIntervals,
                    lastScanDurationMs = lastScanDurationMs,
                    sourceScanTimings = sourceScanTimings,
                    scanIntervalChangeEvents = scanIntervalChangeEvents,
                    autoAdjustScanIntervalEnabled = autoAdjustScanIntervalEnabled,
                    autoAdjustSuggestedIntervalSeconds = autoAdjustSuggestedIntervalSeconds,
                    appThemeMode = appThemeMode,
                    approachDetectionEnabled = approachDetectionEnabled,
                    approachNotificationsEnabled = approachNotificationsEnabled,
                    trackerNotificationsEnabled = trackerNotificationsEnabled,
                    magneticIncreaseNotificationsEnabled = magneticIncreaseNotificationsEnabled,
                    meshConnectivityNotificationsEnabled = meshConnectivityNotificationsEnabled,
                    meshWipeNotificationsEnabled = meshWipeNotificationsEnabled,
                    foreignSignalRiskEnabled = foreignSignalRiskEnabled,
                    foreignSignalAlertsEnabled = foreignSignalAlertsEnabled,
                    foreignSignalAlertThreshold = foreignSignalAlertThreshold,
                    foreignDirectAcousticEnabled = foreignDirectAcousticEnabled,
                    foreignDirectMagneticEnabled = foreignDirectMagneticEnabled,
                    onScanIntervalSelected = { seconds ->
                        scope.launch {
                            applyScanInterval(seconds, "Manual update", "manual")
                        }
                    },
                    onAutoAdjustScanIntervalChanged = { enabled ->
                        autoAdjustScanIntervalEnabled = enabled
                        ScanSettings.setAutoAdjustScanIntervalEnabled(context, enabled)
                        autoAdjustConsecutiveOverruns = emptyMap()
                        autoAdjustStableCycles = emptyMap()
                        if (enabled) {
                            scope.launch {
                                val alignedSourceIntervals = ScanSettings.SOURCE_TYPES.associateWith { source ->
                                    val suggested = autoAdjustSuggestedBySource[source]
                                        ?: (sourceScanIntervals[source] ?: ScanSettings.DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS)
                                    ScanSettings.setSourceScanIntervalSeconds(context, source, suggested)
                                    suggested
                                }
                                sourceScanIntervals = alignedSourceIntervals
                            }
                        }
                    },
                    onSourceScanIntervalSelected = { sourceType, seconds ->
                        val previous = sourceScanIntervals[sourceType]
                            ?: ScanSettings.DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS
                        val updated = seconds.coerceIn(
                            ScanSettings.MIN_SOURCE_SCAN_INTERVAL_SECONDS,
                            ScanSettings.MAX_SOURCE_SCAN_INTERVAL_SECONDS
                        )
                        ScanSettings.setSourceScanIntervalSeconds(context, sourceType, updated)
                        sourceScanIntervals = sourceScanIntervals + (sourceType to updated)
                        ScanSettings.appendScanIntervalChangeEvent(
                            context = context,
                            fromSeconds = previous,
                            toSeconds = updated,
                            reason = "manual-$sourceType"
                        )
                        scanIntervalChangeEvents = ScanSettings.getScanIntervalChangeEvents(context, 10)
                    },
                    onThemeModeSelected = { mode ->
                        appThemeMode = mode
                        ScanSettings.setAppThemeMode(context, mode.name)
                    },
                    onApproachDetectionChanged = { enabled ->
                        approachDetectionEnabled = enabled
                        ScanSettings.setApproachDetectionEnabled(context, enabled)
                    },
                    onApproachNotificationsChanged = { enabled ->
                        approachNotificationsEnabled = enabled
                        ScanSettings.setApproachNotificationsEnabled(context, enabled)
                    },
                    onTrackerNotificationsChanged = { enabled ->
                        trackerNotificationsEnabled = enabled
                        ScanSettings.setTrackerNotificationsEnabled(context, enabled)
                    },
                    onMagneticIncreaseNotificationsChanged = { enabled ->
                        magneticIncreaseNotificationsEnabled = enabled
                        ScanSettings.setMagneticIncreaseNotificationsEnabled(context, enabled)
                    },
                    onMeshConnectivityNotificationsChanged = { enabled ->
                        meshConnectivityNotificationsEnabled = enabled
                        ScanSettings.setMeshConnectivityNotificationsEnabled(context, enabled)
                    },
                    onMeshWipeNotificationsChanged = { enabled ->
                        meshWipeNotificationsEnabled = enabled
                        ScanSettings.setMeshWipeNotificationsEnabled(context, enabled)
                    },
                    onForeignSignalRiskEnabledChanged = { enabled ->
                        foreignSignalRiskEnabled = enabled
                        ScanSettings.setForeignSignalRiskEnabled(context, enabled)
                    },
                    onForeignSignalAlertsEnabledChanged = { enabled ->
                        foreignSignalAlertsEnabled = enabled
                        ScanSettings.setForeignSignalAlertsEnabled(context, enabled)
                    },
                    onForeignSignalAlertThresholdChanged = { threshold ->
                        foreignSignalAlertThreshold = threshold
                        ScanSettings.setForeignSignalAlertThreshold(context, threshold)
                    },
                    onForeignDirectAcousticEnabledChanged = { enabled ->
                        foreignDirectAcousticEnabled = enabled
                        ScanSettings.setForeignDirectAcousticEnabled(context, enabled)
                        sensorGateSettings = readSensorGateSettings(context)
                    },
                    onForeignDirectMagneticEnabledChanged = { enabled ->
                        foreignDirectMagneticEnabled = enabled
                        ScanSettings.setForeignDirectMagneticEnabled(context, enabled)
                        sensorGateSettings = readSensorGateSettings(context)
                    },
                    onLiveMapUpdateIntervalSelected = { seconds ->
                        liveMapUpdateIntervalSeconds = seconds
                        ScanSettings.setLiveMapUpdateIntervalSeconds(context, seconds)
                    },
                    onExportBackup = {
                        val file = AppBackupManager.exportSnapshot(
                            context = context,
                            repository = app.container.repository,
                            reason = "manual settings export"
                        )
                        "Backup exported: ${file.name}"
                    },
                    onExportEncryptedBackup = { passphrase ->
                        val file = AppBackupManager.exportEncryptedSnapshot(
                            context = context,
                            repository = app.container.repository,
                            reason = "manual encrypted settings export",
                            passphrase = passphrase
                        )
                        "Encrypted backup exported: ${file.name}"
                    },
                    onImportLatestBackup = {
                        val fileName = AppBackupManager.importLatestSnapshot(
                            context = context,
                            repository = app.container.repository
                        )
                        sensorGateSettings = readSensorGateSettings(context)
                        chainLinkEnabled = ScanSettings.isChainLinkEnabled(context)
                        chainNodeId = ScanSettings.getChainNodeId(context)
                        chainDeviceName = ScanSettings.getChainDeviceName(context)
                        chainSharedSecret = ScanSettings.getChainSharedSecret(context)
                        chainAutoSyncEnabled = ScanSettings.isChainAutoSyncEnabled(context)
                        chainAutoSyncIntervalSeconds = ScanSettings.getChainAutoSyncIntervalSeconds(context)
                        chainPersistentChannelEnabled = ScanSettings.isChainPersistentChannelEnabled(context)
                        chainHeartbeatIntervalSeconds = ScanSettings.getChainHeartbeatIntervalSeconds(context)
                        chainSharePreciseLocationEnabled = ScanSettings.isChainSharePreciseLocationEnabled(context)
                        evasionProfile = runCatching { EvasionProfile.valueOf(ScanSettings.getEvasionProfile(context)) }
                            .getOrDefault(EvasionProfile.BALANCED)
                        evasionAutoEscalateEnabled = ScanSettings.isEvasionAutoEscalateEnabled(context)
                        evasionEscalateDurationSeconds = ScanSettings.getEvasionAutoEscalateDurationSeconds(context)
                        evasionJitterEnabled = ScanSettings.isEvasionJitterEnabled(context)
                        evasionJitterPercent = ScanSettings.getEvasionJitterPercent(context)
                        evasionBurstEnabled = ScanSettings.isEvasionBurstEnabled(context)
                        evasionBurstWatchSeconds = ScanSettings.getEvasionBurstWatchSeconds(context)
                        evasionBurstCooldownSeconds = ScanSettings.getEvasionBurstCooldownSeconds(context)
                        evasionActionLog = ScanSettings.getEvasionActionLog(context, 25)
                        liveMapUpdateIntervalSeconds = ScanSettings.getLiveMapUpdateIntervalSeconds(context)
                        sourceScanIntervals = ScanSettings.getAllSourceScanIntervalSeconds(context)
                        sourceLastScanEpochs = ScanSettings.getAllSourceLastScanEpochMs(context)
                        appThemeMode = runCatching { AppThemeMode.valueOf(ScanSettings.getAppThemeMode(context)) }
                            .getOrDefault(AppThemeMode.SYSTEM)
                        approachNotificationsEnabled = ScanSettings.isApproachNotificationsEnabled(context)
                        trackerNotificationsEnabled = ScanSettings.isTrackerNotificationsEnabled(context)
                        magneticIncreaseNotificationsEnabled = ScanSettings.isMagneticIncreaseNotificationsEnabled(context)
                        meshConnectivityNotificationsEnabled = ScanSettings.isMeshConnectivityNotificationsEnabled(context)
                        meshWipeNotificationsEnabled = ScanSettings.isMeshWipeNotificationsEnabled(context)
                        foreignSignalRiskEnabled = ScanSettings.isForeignSignalRiskEnabled(context)
                        foreignSignalAlertsEnabled = ScanSettings.isForeignSignalAlertsEnabled(context)
                        foreignSignalAlertThreshold = ScanSettings.getForeignSignalAlertThreshold(context)
                        foreignDirectAcousticEnabled = ScanSettings.isForeignDirectAcousticEnabled(context)
                        foreignDirectMagneticEnabled = ScanSettings.isForeignDirectMagneticEnabled(context)
                        lastScanDurationMs = ScanSettings.getLastScanDurationMs(context)
                        sourceScanTimings = ScanSettings.getSourceScanTimings(context)
                        autoAdjustScanIntervalEnabled = ScanSettings.isAutoAdjustScanIntervalEnabled(context)
                        scanIntervalChangeEvents = ScanSettings.getScanIntervalChangeEvents(context, 10)
                        alertLogs = AlertLogStore.read(context)
                        ownedDeviceKeys = OwnedDeviceRegistry.read(context)
                        MeshForegroundServiceController.ensureState(context)
                        viewModel.refreshSummary()
                        "Backup imported from $fileName"
                    },
                    onImportLatestEncryptedBackup = { passphrase ->
                        val fileName = AppBackupManager.importLatestEncryptedSnapshot(
                            context = context,
                            repository = app.container.repository,
                            passphrase = passphrase
                        )
                        sensorGateSettings = readSensorGateSettings(context)
                        chainLinkEnabled = ScanSettings.isChainLinkEnabled(context)
                        chainNodeId = ScanSettings.getChainNodeId(context)
                        chainDeviceName = ScanSettings.getChainDeviceName(context)
                        chainSharedSecret = ScanSettings.getChainSharedSecret(context)
                        chainAutoSyncEnabled = ScanSettings.isChainAutoSyncEnabled(context)
                        chainAutoSyncIntervalSeconds = ScanSettings.getChainAutoSyncIntervalSeconds(context)
                        chainPersistentChannelEnabled = ScanSettings.isChainPersistentChannelEnabled(context)
                        chainHeartbeatIntervalSeconds = ScanSettings.getChainHeartbeatIntervalSeconds(context)
                        chainSharePreciseLocationEnabled = ScanSettings.isChainSharePreciseLocationEnabled(context)
                        evasionProfile = runCatching { EvasionProfile.valueOf(ScanSettings.getEvasionProfile(context)) }
                            .getOrDefault(EvasionProfile.BALANCED)
                        evasionAutoEscalateEnabled = ScanSettings.isEvasionAutoEscalateEnabled(context)
                        evasionEscalateDurationSeconds = ScanSettings.getEvasionAutoEscalateDurationSeconds(context)
                        evasionJitterEnabled = ScanSettings.isEvasionJitterEnabled(context)
                        evasionJitterPercent = ScanSettings.getEvasionJitterPercent(context)
                        evasionBurstEnabled = ScanSettings.isEvasionBurstEnabled(context)
                        evasionBurstWatchSeconds = ScanSettings.getEvasionBurstWatchSeconds(context)
                        evasionBurstCooldownSeconds = ScanSettings.getEvasionBurstCooldownSeconds(context)
                        evasionActionLog = ScanSettings.getEvasionActionLog(context, 25)
                        liveMapUpdateIntervalSeconds = ScanSettings.getLiveMapUpdateIntervalSeconds(context)
                        sourceScanIntervals = ScanSettings.getAllSourceScanIntervalSeconds(context)
                        sourceLastScanEpochs = ScanSettings.getAllSourceLastScanEpochMs(context)
                        appThemeMode = runCatching { AppThemeMode.valueOf(ScanSettings.getAppThemeMode(context)) }
                            .getOrDefault(AppThemeMode.SYSTEM)
                        approachNotificationsEnabled = ScanSettings.isApproachNotificationsEnabled(context)
                        trackerNotificationsEnabled = ScanSettings.isTrackerNotificationsEnabled(context)
                        magneticIncreaseNotificationsEnabled = ScanSettings.isMagneticIncreaseNotificationsEnabled(context)
                        meshConnectivityNotificationsEnabled = ScanSettings.isMeshConnectivityNotificationsEnabled(context)
                        meshWipeNotificationsEnabled = ScanSettings.isMeshWipeNotificationsEnabled(context)
                        foreignSignalRiskEnabled = ScanSettings.isForeignSignalRiskEnabled(context)
                        foreignSignalAlertsEnabled = ScanSettings.isForeignSignalAlertsEnabled(context)
                        foreignSignalAlertThreshold = ScanSettings.getForeignSignalAlertThreshold(context)
                        foreignDirectAcousticEnabled = ScanSettings.isForeignDirectAcousticEnabled(context)
                        foreignDirectMagneticEnabled = ScanSettings.isForeignDirectMagneticEnabled(context)
                        lastScanDurationMs = ScanSettings.getLastScanDurationMs(context)
                        sourceScanTimings = ScanSettings.getSourceScanTimings(context)
                        autoAdjustScanIntervalEnabled = ScanSettings.isAutoAdjustScanIntervalEnabled(context)
                        scanIntervalChangeEvents = ScanSettings.getScanIntervalChangeEvents(context, 10)
                        alertLogs = AlertLogStore.read(context)
                        ownedDeviceKeys = OwnedDeviceRegistry.read(context)
                        MeshForegroundServiceController.ensureState(context)
                        viewModel.refreshSummary()
                        "Encrypted backup imported from $fileName"
                    },
                    onSoftReset = {
                        scope.launch {
                            app.container.repository.clearEncounters()
                            app.container.repository.clearDevices()
                            ScanSettings.clearOperationalLogs(context)
                            alertLogs = emptyList()
                            evasionActionLog = emptyList()
                            scanIntervalChangeEvents = emptyList()
                            viewModel.refreshSummary()
                        }
                    },
                    onHardReset = {
                        scope.launch {
                            app.container.repository.clearEncounters()
                            app.container.repository.clearDevices()
                            ScanSettings.clearOperationalLogs(context)
                            ScanSettings.resetMeshNetworkSettings(context)
                            app.container.chainLinkCoordinator.stopServer()
                            MeshForegroundServiceController.ensureState(context)
                            alertLogs = emptyList()
                            evasionActionLog = emptyList()
                            scanIntervalChangeEvents = emptyList()
                            chainLinkEnabled = ScanSettings.isChainLinkEnabled(context)
                            chainNodeId = ScanSettings.getChainNodeId(context)
                            chainDeviceName = ScanSettings.getChainDeviceName(context)
                            chainSharedSecret = ScanSettings.getChainSharedSecret(context)
                            chainAutoSyncEnabled = ScanSettings.isChainAutoSyncEnabled(context)
                            chainAutoSyncIntervalSeconds = ScanSettings.getChainAutoSyncIntervalSeconds(context)
                            chainPersistentChannelEnabled = ScanSettings.isChainPersistentChannelEnabled(context)
                            chainHeartbeatIntervalSeconds = ScanSettings.getChainHeartbeatIntervalSeconds(context)
                            chainSharePreciseLocationEnabled = ScanSettings.isChainSharePreciseLocationEnabled(context)
                            appThemeMode = runCatching { AppThemeMode.valueOf(ScanSettings.getAppThemeMode(context)) }
                                .getOrDefault(AppThemeMode.SYSTEM)
                            approachNotificationsEnabled = ScanSettings.isApproachNotificationsEnabled(context)
                            trackerNotificationsEnabled = ScanSettings.isTrackerNotificationsEnabled(context)
                            magneticIncreaseNotificationsEnabled = ScanSettings.isMagneticIncreaseNotificationsEnabled(context)
                            meshConnectivityNotificationsEnabled = ScanSettings.isMeshConnectivityNotificationsEnabled(context)
                            meshWipeNotificationsEnabled = ScanSettings.isMeshWipeNotificationsEnabled(context)
                            foreignSignalRiskEnabled = ScanSettings.isForeignSignalRiskEnabled(context)
                            foreignSignalAlertsEnabled = ScanSettings.isForeignSignalAlertsEnabled(context)
                            foreignSignalAlertThreshold = ScanSettings.getForeignSignalAlertThreshold(context)
                            foreignDirectAcousticEnabled = ScanSettings.isForeignDirectAcousticEnabled(context)
                            foreignDirectMagneticEnabled = ScanSettings.isForeignDirectMagneticEnabled(context)
                            viewModel.refreshSummary()
                        }
                    }
                )
            }

            composable(DETECTION_ROUTE) {
                DetectionPage(
                    readinessItems = readinessItems,
                    encounters = recent,
                    meshInsightEncounters = allEncounters,
                    foreignSignalRiskEnabled = foreignSignalRiskEnabled,
                    approachDetectionEnabled = approachDetectionEnabled,
                    ownedDeviceKeys = ownedDeviceKeys,
                    alertLogs = alertLogs,
                    chainLinkEnabled = chainLinkEnabled,
                    chainNodeId = chainNodeId,
                    chainDeviceName = chainDeviceName,
                    chainSharedSecret = chainSharedSecret,
                    chainAutoSyncEnabled = chainAutoSyncEnabled,
                    chainAutoSyncIntervalSeconds = chainAutoSyncIntervalSeconds,
                    chainPersistentChannelEnabled = chainPersistentChannelEnabled,
                    chainHeartbeatIntervalSeconds = chainHeartbeatIntervalSeconds,
                    chainSharePreciseLocationEnabled = chainSharePreciseLocationEnabled,
                    liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds,
                    chainMeshSnapshot = chainMesh,
                    onEncounterMapPinClick = { source, primaryId, timestampEpochMs ->
                        navController.navigate(
                            "encounterDetail/${Uri.encode(source)}/${Uri.encode(primaryId)}/${timestampEpochMs}"
                        )
                    },
                    onDeviceMapPinClick = { source, primaryId, lat, lon, timestampEpochMs ->
                        val queryParts = buildList {
                            lat?.let { add("lat=${Uri.encode(it.toString())}") }
                            lon?.let { add("lon=${Uri.encode(it.toString())}") }
                            timestampEpochMs?.let { add("ts=$it") }
                        }
                        val query = if (queryParts.isEmpty()) "" else "?${queryParts.joinToString("&")}" 
                        navController.navigate(
                            "deviceDetail/${Uri.encode(source)}/${Uri.encode(primaryId)}$query"
                        )
                    },
                    onMovingDeviceMapPinClick = { source, primaryId ->
                        navController.navigate(
                            "movingDevicePath/${Uri.encode(source)}/${Uri.encode(primaryId)}"
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onRefresh = {
                        readinessItems = DetectionReadinessAdvisor.evaluate(context)
                    },
                    onLiveCollect = {
                        val meshGate = ScanSettings.getMeshWipeGateState(context)
                        if (meshGate.enabled) {
                            val initiator = meshGate.initiatorDeviceName
                                ?: meshGate.initiatorNodeId
                                ?: "mesh coordinator"
                            return@DetectionPage "Live scan paused: mesh wipe gate active (initiated by $initiator)."
                        }

                        runCatching {
                            val scanResult = app.container.sensingService.collectBatchWithMetrics()
                            app.container.repository.insertBatch(scanResult.encounters)
                            ScanSettings.setLastScanDurationMs(context, scanResult.totalDurationMs)
                            scanResult.sourceDurationsMs.forEach { (sourceType, durationMs) ->
                                ScanSettings.recordSourceScanDurationMs(context, sourceType, durationMs)
                            }
                            lastScanDurationMs = scanResult.totalDurationMs
                            sourceScanTimings = ScanSettings.getSourceScanTimings(context)
                            val chainStats = app.container.chainLinkCoordinator.syncNow()
                            viewModel.refreshSummary()
                            readinessItems = DetectionReadinessAdvisor.evaluate(context)
                            if (scanResult.encounters.isEmpty()) {
                                if (chainStats.enabled) {
                                    if (!chainStats.authConfigured) {
                                        "Live scan completed: set a shared chain passphrase to enable secure peer sync."
                                    } else {
                                        "Live scan completed: no local detections. Chain imported ${chainStats.importedRecords} from ${chainStats.peersSynced} peers."
                                    }
                                } else {
                                    "Live scan completed: no detections this cycle."
                                }
                            } else {
                                if (chainStats.enabled) {
                                    if (!chainStats.authConfigured) {
                                        "Live scan added ${scanResult.encounters.size} local detections. Configure a shared chain passphrase to sync peers."
                                    } else {
                                        "Live scan added ${scanResult.encounters.size} local detections and ${chainStats.importedRecords} chain detections."
                                    }
                                } else {
                                    "Live scan added ${scanResult.encounters.size} detections."
                                }
                            }
                        }.getOrElse { error ->
                            "Live scan failed: ${error.message ?: "unknown error"}"
                        }
                    },
                    onOpenReadinessSetting = { item ->
                        runCatching {
                            context.startActivity(item.settingsIntent)
                        }.recoverCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                            )
                        }
                    },
                    onClearAlertLogs = {
                        AlertLogStore.clear(context)
                        alertLogs = emptyList()
                    },
                    onOpenApproachLogMap = { source, primaryId ->
                        navController.navigate(
                            "approachAlertMap/${Uri.encode(source)}/${Uri.encode(primaryId)}"
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onChainLinkChanged = { enabled ->
                        chainLinkEnabled = enabled
                        ScanSettings.setChainLinkEnabled(context, enabled)
                        if (enabled) {
                            app.container.chainLinkCoordinator.ensureServerRunning()
                        } else {
                            app.container.chainLinkCoordinator.stopServer()
                        }
                        MeshForegroundServiceController.ensureState(context)
                    },
                    onChainDeviceNameChanged = { newName ->
                        chainDeviceName = newName
                        ScanSettings.setChainDeviceName(context, newName)
                    },
                    onChainSharedSecretChanged = { newSecret ->
                        chainSharedSecret = newSecret.trim()
                        ScanSettings.setChainSharedSecret(context, newSecret)
                    },
                    onChainAutoSyncChanged = { enabled ->
                        chainAutoSyncEnabled = enabled
                        ScanSettings.setChainAutoSyncEnabled(context, enabled)
                    },
                    onChainAutoSyncIntervalChanged = { seconds ->
                        chainAutoSyncIntervalSeconds = seconds
                        ScanSettings.setChainAutoSyncIntervalSeconds(context, seconds)
                    },
                    onChainPersistentChannelChanged = { enabled ->
                        chainPersistentChannelEnabled = enabled
                        ScanSettings.setChainPersistentChannelEnabled(context, enabled)
                        if (enabled) {
                            app.container.chainLinkCoordinator.ensureServerRunning()
                        }
                        MeshForegroundServiceController.ensureState(context)
                    },
                    onChainHeartbeatIntervalChanged = { seconds ->
                        chainHeartbeatIntervalSeconds = seconds
                        ScanSettings.setChainHeartbeatIntervalSeconds(context, seconds)
                    },
                    onChainSharePreciseLocationChanged = { enabled ->
                        chainSharePreciseLocationEnabled = enabled
                        ScanSettings.setChainSharePreciseLocationEnabled(context, enabled)
                    },
                    onRefreshPeers = {
                        app.container.chainLinkCoordinator.refreshPeers()
                    },
                    onSendLinkRequest = { host, message ->
                        app.container.chainLinkCoordinator.sendLinkRequest(host, message)
                    },
                    onSyncNow = {
                        val stats = app.container.chainLinkCoordinator.syncNow()
                        if (!stats.enabled) {
                            "Chain link is disabled."
                        } else if (!stats.authConfigured) {
                            "Set a shared chain passphrase on all devices before syncing."
                        } else {
                            "Peers ${stats.peersSynced}/${stats.peersDiscovered}, imported ${stats.importedRecords}, exported ${stats.exportedRecords}, failures ${stats.failures}."
                        }
                    },
                    onWipeMeshData = {
                        val wipe = app.container.chainLinkCoordinator.wipeMeshDataAcrossPeers()
                        viewModel.refreshSummary()
                        when {
                            !wipe.enabled -> "Local soft reset applied (encounters/devices/logs). Chain link is disabled, so no remote peers were targeted."
                            !wipe.authConfigured -> "Local soft reset applied (encounters/devices/logs). Configure a shared chain passphrase to wipe linked peers."
                            wipe.failures > 0 -> "Soft reset incomplete across mesh: local cleared, peers reset ${wipe.peersWiped}/${wipe.peersTargeted}, failures ${wipe.failures}. Scan gate remains active until all targeted peers complete reset."
                            else -> "Soft reset completed across mesh: local + peers reset ${wipe.peersWiped}/${wipe.peersTargeted}. Scan gate released across mesh."
                        }
                    }
                )
            }

            composable(EVASION_ROUTE) {
                EvasionPage(
                    profile = evasionProfile,
                    autoEscalateEnabled = evasionAutoEscalateEnabled,
                    autoEscalateDurationSeconds = evasionEscalateDurationSeconds,
                    escalationActiveUntilEpochMs = evasionEscalationActiveUntilEpochMs,
                    jitterEnabled = evasionJitterEnabled,
                    jitterPercent = evasionJitterPercent,
                    burstEnabled = evasionBurstEnabled,
                    burstWatchSeconds = evasionBurstWatchSeconds,
                    burstCooldownSeconds = evasionBurstCooldownSeconds,
                    burstActiveUntilEpochMs = evasionBurstActiveUntilEpochMs,
                    actionLog = evasionActionLog,
                    onProfileSelected = { selected ->
                        scope.launch {
                            applyEvasionProfile(
                                profile = selected,
                                reason = "Manual selection",
                                allowClearEscalation = true
                            )
                        }
                    },
                    onAutoEscalateEnabledChanged = { enabled ->
                        evasionAutoEscalateEnabled = enabled
                        ScanSettings.setEvasionAutoEscalateEnabled(context, enabled)
                        appendEvasionAction(
                            action = "AUTO_ESCALATE_TOGGLE",
                            detail = if (enabled) "Enabled" else "Disabled"
                        )
                    },
                    onAutoEscalateDurationSelected = { seconds ->
                        evasionEscalateDurationSeconds = seconds
                        ScanSettings.setEvasionAutoEscalateDurationSeconds(context, seconds)
                        appendEvasionAction(
                            action = "AUTO_ESCALATE_DURATION",
                            detail = "Set to ${ScanSettings.formatInterval(seconds)}"
                        )
                    },
                    onJitterEnabledChanged = { enabled ->
                        evasionJitterEnabled = enabled
                        ScanSettings.setEvasionJitterEnabled(context, enabled)
                        appendEvasionAction(
                            action = "JITTER_TOGGLE",
                            detail = if (enabled) "Enabled" else "Disabled"
                        )
                        if (enabled) {
                            scope.launch {
                                applyEvasionCadence(
                                    profile = evasionProfile,
                                    reasonCode = "evasion-jitter-enable",
                                    jitterEnabled = true,
                                    jitterPercent = evasionJitterPercent,
                                    appendLog = false
                                )
                            }
                        }
                    },
                    onJitterPercentSelected = { percent ->
                        evasionJitterPercent = percent
                        ScanSettings.setEvasionJitterPercent(context, percent)
                        appendEvasionAction(
                            action = "JITTER_PERCENT",
                            detail = "Set to +/-${percent}%"
                        )
                        if (evasionJitterEnabled) {
                            scope.launch {
                                applyEvasionCadence(
                                    profile = evasionProfile,
                                    reasonCode = "evasion-jitter-percent",
                                    jitterEnabled = true,
                                    jitterPercent = percent,
                                    appendLog = false
                                )
                            }
                        }
                    },
                    onBurstEnabledChanged = { enabled ->
                        evasionBurstEnabled = enabled
                        ScanSettings.setEvasionBurstEnabled(context, enabled)
                        appendEvasionAction(
                            action = "BURST_TOGGLE",
                            detail = if (enabled) "Enabled" else "Disabled"
                        )
                    },
                    onBurstWatchSecondsSelected = { seconds ->
                        evasionBurstWatchSeconds = seconds
                        ScanSettings.setEvasionBurstWatchSeconds(context, seconds)
                        appendEvasionAction(
                            action = "BURST_WATCH_DURATION",
                            detail = "Set to ${ScanSettings.formatInterval(seconds)}"
                        )
                    },
                    onBurstCooldownSecondsSelected = { seconds ->
                        evasionBurstCooldownSeconds = seconds
                        ScanSettings.setEvasionBurstCooldownSeconds(context, seconds)
                        appendEvasionAction(
                            action = "BURST_COOLDOWN_DURATION",
                            detail = "Set to ${ScanSettings.formatInterval(seconds)}"
                        )
                    },
                    onClearActionLog = {
                        ScanSettings.clearEvasionActionLog(context)
                        evasionActionLog = emptyList()
                    }
                )
            }

            composable(DEVICES_ENCOUNTERS_ROUTE) {
                DevicesEncountersPage(
                    recentEncounters = recent100,
                    allEncounters = allEncounters,
                    approachDetectionEnabled = approachDetectionEnabled,
                    ownedDeviceKeys = ownedDeviceKeys,
                    onDeviceClick = { device ->
                        navController.navigate(
                            "deviceDetail/${Uri.encode(device.source)}/${Uri.encode(device.primaryId)}"
                        )
                    },
                    onEncounterClick = { encounter ->
                        navController.navigate(
                            "encounterDetail/${Uri.encode(encounter.source.name)}/${Uri.encode(encounter.primaryId)}/${encounter.timestampEpochMs}"
                        )
                    }
                )
            }

            composable(DEVICE_DETAIL_ROUTE) { entry ->
                val source = Uri.decode(entry.arguments?.getString("source") ?: "")
                val primaryId = Uri.decode(entry.arguments?.getString("primaryId") ?: "")
                val selectedPinLat = entry.arguments?.getString("lat")?.toDoubleOrNull()
                val selectedPinLon = entry.arguments?.getString("lon")?.toDoubleOrNull()
                val selectedPinTs = entry.arguments?.getString("ts")?.toLongOrNull()
                val deviceEncounters = remember(allEncounters, source, primaryId) {
                    allEncounters.filter { it.source.name == source && it.primaryId == primaryId }
                }
                val item = remember(deviceEncounters, source, primaryId, approachDetectionEnabled, ownedDeviceKeys) {
                    buildSingleDeviceItem(
                        source = source,
                        primaryId = primaryId,
                        groupedEncounters = deviceEncounters,
                        approachDetectionEnabled = approachDetectionEnabled,
                        ownedDeviceKeys = ownedDeviceKeys
                    )
                }
                DeviceDetailPage(
                    item = item,
                    deviceEncounters = deviceEncounters,
                    initialPinnedLat = selectedPinLat,
                    initialPinnedLon = selectedPinLon,
                    initialPinnedTimestampEpochMs = selectedPinTs,
                    onBack = { navController.popBackStack() },
                    onOwnedChanged = { changedSource, changedPrimaryId, owned ->
                        OwnedDeviceRegistry.setOwned(context, changedSource, changedPrimaryId, owned)
                        ownedDeviceKeys = OwnedDeviceRegistry.read(context)
                    }
                )
            }

            composable(ENCOUNTER_DETAIL_ROUTE) { entry ->
                val source = Uri.decode(entry.arguments?.getString("source") ?: "")
                val primaryId = Uri.decode(entry.arguments?.getString("primaryId") ?: "")
                val timestamp = entry.arguments?.getString("timestamp")?.toLongOrNull() ?: -1L
                val encounter = recent.firstOrNull {
                    it.source.name == source &&
                        it.primaryId == primaryId &&
                        it.timestampEpochMs == timestamp
                }
                EncounterDetailPage(encounter = encounter, onBack = { navController.popBackStack() })
            }

            composable(APPROACH_ALERT_MAP_ROUTE) { entry ->
                val source = Uri.decode(entry.arguments?.getString("source") ?: "")
                val primaryId = Uri.decode(entry.arguments?.getString("primaryId") ?: "")
                val isOwnedTarget = OwnedDeviceRegistry.keyFor(source, primaryId) in ownedDeviceKeys
                ApproachAlertMapPage(
                    source = source,
                    primaryId = primaryId,
                    isOwnedTarget = isOwnedTarget,
                    encounters = allEncounters,
                    liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds,
                    onOpenDeviceDetails = { detailSource, detailPrimaryId ->
                        navController.navigate(
                            "deviceDetail/${Uri.encode(detailSource)}/${Uri.encode(detailPrimaryId)}"
                        )
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(MOVING_DEVICE_PATH_ROUTE) { entry ->
                val source = Uri.decode(entry.arguments?.getString("source") ?: "")
                val primaryId = Uri.decode(entry.arguments?.getString("primaryId") ?: "")
                MovingDevicePathMapPage(
                    source = source,
                    primaryId = primaryId,
                    encounters = allEncounters,
                    onOpenDeviceDetails = { detailSource, detailPrimaryId ->
                        navController.navigate(
                            "deviceDetail/${Uri.encode(detailSource)}/${Uri.encode(detailPrimaryId)}"
                        )
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            }
        }
    }
}

@Composable
private fun ApproachAlertMapPage(
    source: String,
    primaryId: String,
    isOwnedTarget: Boolean,
    encounters: List<Encounter>,
    liveMapUpdateIntervalSeconds: Long,
    onOpenDeviceDetails: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.4219999, -122.0840575), 15f)
    }

    var observerLocation by remember { mutableStateOf(LocationSnapshotProvider.read(context)) }
    var observerLastUpdatedEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var nowEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val targetEncounters = remember(encounters, source, primaryId) {
        encounters
            .filter { it.source.name == source && it.primaryId == primaryId }
            .sortedByDescending { it.timestampEpochMs }
    }
    val trackStartEpochMs = remember(source, primaryId, targetEncounters.size) {
        ApproachTrackStore.getTrackStartEpochMs(context, source, primaryId)
    }
    val observerTrackPoints = remember(targetEncounters, trackStartEpochMs) {
        targetEncounters
            .asReversed()
            .filter { encounter ->
                val start = trackStartEpochMs
                start == null || encounter.timestampEpochMs >= start
            }
            .mapNotNull { encounter ->
                if (!isValidLatLon(encounter.lat, encounter.lon)) return@mapNotNull null
                LatLng(encounter.lat!!, encounter.lon!!)
            }
            .fold(mutableListOf<LatLng>()) { acc, point ->
                val previous = acc.lastOrNull()
                if (previous == null || previous.latitude != point.latitude || previous.longitude != point.longitude) {
                    acc += point
                }
                acc
            }
    }
    val estimatedTargetPathPoints = remember(targetEncounters, trackStartEpochMs) {
        val chron = targetEncounters
            .asReversed()
            .filter { encounter ->
                val start = trackStartEpochMs
                start == null || encounter.timestampEpochMs >= start
            }

        if (chron.size < 2) {
            emptyList()
        } else {
            chron.indices.mapNotNull { idx ->
                val windowStart = (idx - 5).coerceAtLeast(0)
                val window = chron.subList(windowStart, idx + 1)
                estimateApproachingDeviceLocation(window)?.first
            }.fold(mutableListOf<LatLng>()) { acc, point ->
                val previous = acc.lastOrNull()
                if (previous == null || previous.latitude != point.latitude || previous.longitude != point.longitude) {
                    acc += point
                }
                acc
            }
        }
    }
    val targetEstimate = remember(targetEncounters) {
        estimateApproachingDeviceLocation(targetEncounters)
    }

    LaunchedEffect(liveMapUpdateIntervalSeconds, source, primaryId) {
        val loopMs = (liveMapUpdateIntervalSeconds.coerceAtLeast(1L) * 1000L)
        while (true) {
            observerLocation = LocationSnapshotProvider.read(context)
            observerLastUpdatedEpochMs = System.currentTimeMillis()
            delay(loopMs)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowEpochMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    LaunchedEffect(observerLocation, targetEstimate) {
        val observerLatLng = observerLocation
            ?.takeIf { isValidLatLon(it.lat, it.lon) }
            ?.let { LatLng(it.lat, it.lon) }
        val targetLatLng = targetEstimate?.first

        when {
            observerLatLng != null && targetLatLng != null -> {
                val bounds = LatLngBounds.builder()
                    .include(observerLatLng)
                    .include(targetLatLng)
                    .build()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 140))
            }
            observerLatLng != null -> {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(observerLatLng, 16f))
            }
            targetLatLng != null -> {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(targetLatLng, 16f))
            }
        }
    }

    val latestTargetSeen = targetEncounters.firstOrNull()?.timestampEpochMs
    val observerAgeMs = (nowEpochMs - observerLastUpdatedEpochMs).coerceAtLeast(0L)
    val targetAgeMs = latestTargetSeen?.let { (nowEpochMs - it).coerceAtLeast(0L) }
    val observerFreshnessMs = maxOf(15_000L, liveMapUpdateIntervalSeconds * 2_000L)
    val targetFreshnessMs = maxOf(30_000L, liveMapUpdateIntervalSeconds * 3_000L)
    val observerIsLive = observerLocation != null && observerAgeMs <= observerFreshnessMs
    val targetIsLive = targetAgeMs != null && targetAgeMs <= targetFreshnessMs
    val mapIsLive = observerIsLive && targetIsLive

    fun formatAge(ageMs: Long?): String {
        if (ageMs == null) return "n/a"
        return when {
            ageMs < 1_000L -> "just now"
            ageMs < 60_000L -> "${ageMs / 1000L}s ago"
            else -> "${ageMs / 60_000L}m ago"
        }
    }

    val separationMeters = run {
        val observer = observerLocation
        val target = targetEstimate?.first
        if (observer == null || target == null) {
            null
        } else {
            distanceFromLocationMeters(
                fromLat = observer.lat,
                fromLon = observer.lon,
                toLat = target.latitude,
                toLon = target.longitude
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Approach Alert Map", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { },
                label = { Text(if (mapIsLive) "LIVE" else "STALE") },
                leadingIcon = {
                    Text(
                        "●",
                        color = if (mapIsLive) Color(0xFF2E7D32) else Color(0xFFB3261E)
                    )
                }
            )
            Text(
                text = "Observer ${formatAge(observerAgeMs)} | Target ${formatAge(targetAgeMs)}",
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Text("Target: ${listSourceLabel(source, null)} $primaryId${if (isOwnedTarget) " [OWNED DEVICE]" else ""}")
        if (isOwnedTarget) {
            Text("Notice: this approaching target is marked as your own device.")
        }
        Text(
            "Tracking path since: ${trackStartEpochMs?.let(::formatEpoch) ?: "Not started"}"
        )
        Text("Observer path points: ${observerTrackPoints.size} | Target path points: ${estimatedTargetPathPoints.size}")
        Text("Legend: Blue=you, Red=current target estimate, Yellow=observer path start, Orange=RED path start")
        Text("Red path direction: starts at ORANGE marker and ends at RED marker.")
        Text("Target last seen: ${latestTargetSeen?.let(::formatEpoch) ?: "Unknown"}")
        Text("Observer location: ${observerLocation?.let { "${it.lat}, ${it.lon}" } ?: "Unavailable"}")
        Text("Target estimate: ${targetEstimate?.second ?: "Unavailable"}")
        if (separationMeters != null) {
            Text("Estimated separation: ${formatDistanceFeetMiles(separationMeters)}")
        }

        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = true)
        ) {
            observerLocation
                ?.takeIf { isValidLatLon(it.lat, it.lon) }
                ?.let { observer ->
                    Marker(
                        state = MarkerState(position = LatLng(observer.lat, observer.lon)),
                        title = "You",
                        snippet = "Current observer location",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    )
                }

            targetEstimate?.first?.let { targetLatLng ->
                Marker(
                    state = MarkerState(position = targetLatLng),
                    title = if (isOwnedTarget) "Approaching owned device" else "Approaching device",
                    snippet = "$source $primaryId${if (isOwnedTarget) " • owned" else ""}",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                )
            }

            if (observerTrackPoints.size >= 2) {
                Polyline(
                    points = observerTrackPoints,
                    color = Color(0xFF1976D2),
                    width = 6f
                )
            }

            if (estimatedTargetPathPoints.size >= 2) {
                Polyline(
                    points = estimatedTargetPathPoints,
                    color = Color(0xFFD32F2F),
                    width = 8f
                )
            }

            observerTrackPoints.firstOrNull()?.let { startPoint ->
                Marker(
                    state = MarkerState(position = startPoint),
                    title = "Observer path start",
                    snippet = "Where you were when approach tracking started",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
                )
            }

            estimatedTargetPathPoints.firstOrNull()?.let { startPoint ->
                Marker(
                    state = MarkerState(position = startPoint),
                    title = "RED path start (target)",
                    snippet = "This is where the red target path begins",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onOpenDeviceDetails(source, primaryId) }) {
                Text("Open Device Details")
            }
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun HomePage(
    trackingActive: Boolean,
    lastScanEpochMs: Long?,
    lastScanDurationMs: Long?,
    scanIntervalSeconds: Long,
    sourceScanTimings: List<ScanSettings.SourceScanTiming>,
    sourceScanIntervals: Map<String, Long>,
    sourceLastScanEpochs: Map<String, Long>,
    sensorStatuses: List<SensorStatus>,
    sensorGateSettings: SensorGateSettings,
    summary: List<SourceSummary>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onSensorGateChanged: (String, Boolean) -> Unit,
    onClearEncounters: () -> Unit,
    onClearDevices: () -> Unit,
    startMessage: String?,
    startMessageIsError: Boolean
) {
    var nowEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowEpochMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    val enabledSources = remember(sensorGateSettings) {
        enabledSourceTypes(sensorGateSettings)
    }
    val hasFreshSourceScans = remember(enabledSources, sourceLastScanEpochs, sourceScanIntervals, nowEpochMs) {
        enabledSources.any { sourceType ->
            val lastScan = sourceLastScanEpochs[sourceType] ?: 0L
            if (lastScan <= 0L) return@any false
            val intervalSeconds = sourceScanIntervals[sourceType] ?: ScanSettings.DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS
            val freshnessWindowMs = maxOf(intervalSeconds * 3000L, 15_000L)
            (nowEpochMs - lastScan) <= freshnessWindowMs
        }
    }
    val currentSourceOverruns = remember(sourceScanTimings, sourceScanIntervals, sourceLastScanEpochs, nowEpochMs) {
        sourceScanTimings.mapNotNull { timing ->
            val intervalSeconds = sourceScanIntervals[timing.sourceType] ?: return@mapNotNull null
            val lastSourceScanEpoch = sourceLastScanEpochs[timing.sourceType] ?: 0L
            val isOverrun = timing.lastDurationMs > intervalSeconds * 1000L
            val freshnessWindowMs = maxOf(intervalSeconds * 3000L, 15_000L)
            val isCurrent = lastSourceScanEpoch > 0L && (nowEpochMs - lastSourceScanEpoch) <= freshnessWindowMs
            if (isOverrun && isCurrent) {
                Triple(timing.sourceType, timing.lastDurationMs, intervalSeconds)
            } else {
                null
            }
        }
    }
    val staleSourceOverruns = remember(sourceScanTimings, sourceScanIntervals, sourceLastScanEpochs, nowEpochMs) {
        sourceScanTimings.mapNotNull { timing ->
            val intervalSeconds = sourceScanIntervals[timing.sourceType] ?: return@mapNotNull null
            val lastSourceScanEpoch = sourceLastScanEpochs[timing.sourceType] ?: 0L
            val isOverrun = timing.lastDurationMs > intervalSeconds * 1000L
            val freshnessWindowMs = maxOf(intervalSeconds * 3000L, 15_000L)
            val isCurrent = lastSourceScanEpoch > 0L && (nowEpochMs - lastSourceScanEpoch) <= freshnessWindowMs
            if (isOverrun && !isCurrent) {
                Triple(timing.sourceType, timing.lastDurationMs, intervalSeconds)
            } else {
                null
            }
        }
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text("Argus Home", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Text("Tracking controls and source health overview.")
        }
        item {
            Text(
                text = buildString {
                    append(if (trackingActive) "Tracking Status: Running" else "Tracking Status: Stopped")
                    append(" | Last scan: ")
                    append(lastScanEpochMs?.let(::formatEpoch) ?: "Never")
                    append(" | Last scan cycle total: ")
                    append(lastScanDurationMs?.let(::formatScanDuration) ?: "n/a")
                },
                fontWeight = FontWeight.Medium
            )
        }
        item {
            val perSourceSummary = sourceScanTimings
                .joinToString(separator = " | ") { timing ->
                    "${formatSourceTypeLabel(timing.sourceType)} ${formatScanDuration(timing.lastDurationMs)}"
                }
                .ifBlank { "n/a" }
            Text(
                text = "Per-source last durations: $perSourceSummary",
                fontWeight = FontWeight.Medium
            )
        }
        if (currentSourceOverruns.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Current warning: some source scans are exceeding their intervals.",
                            color = Color(0xFFB3261E),
                            fontWeight = FontWeight.SemiBold
                        )
                        currentSourceOverruns.forEach { (sourceType, durationMs, intervalSeconds) ->
                            Text(
                                text = "${formatSourceTypeLabel(sourceType)}: ${formatScanDuration(durationMs)} > ${ScanSettings.formatInterval(intervalSeconds)}",
                                color = Color(0xFFB3261E)
                            )
                        }
                    }
                }
            }
        } else if (staleSourceOverruns.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Info: no current overrun, but previous scans exceeded intervals.",
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.SemiBold
                        )
                        staleSourceOverruns.forEach { (sourceType, durationMs, intervalSeconds) ->
                            Text(
                                text = "${formatSourceTypeLabel(sourceType)} (previous): ${formatScanDuration(durationMs)} > ${ScanSettings.formatInterval(intervalSeconds)}",
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
            }
        } else if (hasFreshSourceScans) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No current scan overrun warnings.",
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Overrun status is stale: no recent source scans yet.",
                        color = Color(0xFFE65100),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        if (startMessage != null) {
            item {
                val statusColor = if (startMessageIsError) Color(0xFFB3261E) else Color(0xFF2E7D32)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = startMessage,
                        color = statusColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!trackingActive) {
                    Button(onClick = onStart) {
                        Text("Start Tracking")
                    }
                }
                Button(onClick = onStop, enabled = trackingActive) {
                    Text("Stop")
                }
                Button(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onClearEncounters) {
                    Text("Clear Encounters")
                }
                Button(onClick = onClearDevices) {
                    Text("Clear Devices")
                }
            }
        }

        item {
            Text("Sensors", fontWeight = FontWeight.Bold)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Sensor Collection Toggles", fontWeight = FontWeight.Medium)
                    Text("Only enabled sensors are collected during tracking and live scans.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Wi-Fi")
                        Switch(
                            checked = sensorGateSettings.wifiEnabled,
                            onCheckedChange = { onSensorGateChanged("wifi", it) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bluetooth LE")
                        Switch(
                            checked = sensorGateSettings.bluetoothEnabled,
                            onCheckedChange = { onSensorGateChanged("bluetooth", it) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cellular")
                        Switch(
                            checked = sensorGateSettings.cellularEnabled,
                            onCheckedChange = { onSensorGateChanged("cellular", it) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Remote ID")
                        Switch(
                            checked = sensorGateSettings.remoteIdEnabled,
                            onCheckedChange = { onSensorGateChanged("remote_id", it) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("UWB")
                        Switch(
                            checked = sensorGateSettings.uwbEnabled,
                            onCheckedChange = { onSensorGateChanged("uwb", it) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("SDR")
                        Switch(
                            checked = sensorGateSettings.sdrEnabled,
                            onCheckedChange = { onSensorGateChanged("sdr", it) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Acoustic (Direct)")
                        Switch(
                            checked = sensorGateSettings.directAcousticEnabled,
                            onCheckedChange = { onSensorGateChanged("direct_acoustic", it) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Magnetometer (Direct)")
                        Switch(
                            checked = sensorGateSettings.directMagneticEnabled,
                            onCheckedChange = { onSensorGateChanged("direct_magnetic", it) }
                        )
                    }
                }
            }
        }
        items(sensorStatuses) { sensor ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(sensor.name)
                    Text(
                        "${if (sensor.isOn) "On" else "Off"} | ${if (sensor.factoredByArgus) "Factored" else "Not factored"}"
                    )
                }
            }
        }

        item {
            Text("Last 24h Summary", fontWeight = FontWeight.Bold)
        }
        items(summary) { item ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(item.source)
                    Text(item.count.toString())
                }
            }
        }
    }
}

@Composable
private fun AppSettingsPage(
    scanIntervalSeconds: Long,
    liveMapUpdateIntervalSeconds: Long,
    sourceScanIntervals: Map<String, Long>,
    lastScanDurationMs: Long?,
    sourceScanTimings: List<ScanSettings.SourceScanTiming>,
    scanIntervalChangeEvents: List<ScanSettings.IntervalChangeEvent>,
    autoAdjustScanIntervalEnabled: Boolean,
    autoAdjustSuggestedIntervalSeconds: Long,
    appThemeMode: AppThemeMode,
    approachDetectionEnabled: Boolean,
    approachNotificationsEnabled: Boolean,
    trackerNotificationsEnabled: Boolean,
    magneticIncreaseNotificationsEnabled: Boolean,
    meshConnectivityNotificationsEnabled: Boolean,
    meshWipeNotificationsEnabled: Boolean,
    foreignSignalRiskEnabled: Boolean,
    foreignSignalAlertsEnabled: Boolean,
    foreignSignalAlertThreshold: String,
    foreignDirectAcousticEnabled: Boolean,
    foreignDirectMagneticEnabled: Boolean,
    onScanIntervalSelected: (Long) -> Unit,
    onAutoAdjustScanIntervalChanged: (Boolean) -> Unit,
    onSourceScanIntervalSelected: (String, Long) -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onApproachDetectionChanged: (Boolean) -> Unit,
    onApproachNotificationsChanged: (Boolean) -> Unit,
    onTrackerNotificationsChanged: (Boolean) -> Unit,
    onMagneticIncreaseNotificationsChanged: (Boolean) -> Unit,
    onMeshConnectivityNotificationsChanged: (Boolean) -> Unit,
    onMeshWipeNotificationsChanged: (Boolean) -> Unit,
    onForeignSignalRiskEnabledChanged: (Boolean) -> Unit,
    onForeignSignalAlertsEnabledChanged: (Boolean) -> Unit,
    onForeignSignalAlertThresholdChanged: (String) -> Unit,
    onForeignDirectAcousticEnabledChanged: (Boolean) -> Unit,
    onForeignDirectMagneticEnabledChanged: (Boolean) -> Unit,
    onLiveMapUpdateIntervalSelected: (Long) -> Unit,
    onExportBackup: suspend () -> String,
    onExportEncryptedBackup: suspend (String) -> String,
    onImportLatestBackup: suspend () -> String,
    onImportLatestEncryptedBackup: suspend (String) -> String,
    onSoftReset: () -> Unit,
    onHardReset: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var liveMapIntervalExpanded by remember { mutableStateOf(false) }
    var sourceIntervalExpandedFor by remember { mutableStateOf<String?>(null) }
    var foreignSignalThresholdExpanded by remember { mutableStateOf(false) }
    var themeModeExpanded by remember { mutableStateOf(false) }
    var backupActionInProgress by remember { mutableStateOf(false) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var backupPassphrase by rememberSaveable { mutableStateOf("") }
    var selectedSettingsTab by rememberSaveable { mutableStateOf(0) }
    val hasStrongPassphrase = backupPassphrase.trim().length >= 8
    val settingsTabs = listOf("Appearance", "Scheduling", "Detection", "Notifications", "Data")
    val intervalOverrun = (lastScanDurationMs ?: 0L) > (scanIntervalSeconds * 1000L)
    val recommendedBySource = remember(sourceScanTimings) {
        sourceScanTimings.associate { timing ->
            timing.sourceType to suggestSafeIntervalSeconds(timing.p95DurationMs)
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            TabRow(selectedTabIndex = selectedSettingsTab) {
                settingsTabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedSettingsTab == index,
                        onClick = { selectedSettingsTab = index },
                        text = { Text(label) }
                    )
                }
            }
        }
        if (selectedSettingsTab == 0) {
            item {
                Text("Appearance", fontWeight = FontWeight.Bold)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Theme mode")
                    Button(onClick = { themeModeExpanded = true }) {
                        Text(appThemeMode.name)
                    }
                    DropdownMenu(
                        expanded = themeModeExpanded,
                        onDismissRequest = { themeModeExpanded = false }
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.name) },
                                onClick = {
                                    onThemeModeSelected(mode)
                                    themeModeExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            item {
                Text("System follows your device theme. Light and Dark force a fixed app appearance.")
            }
        }
        if (selectedSettingsTab == 1) {
            item {
                Text("Worker Cadence (Global Scheduler)", fontWeight = FontWeight.Bold)
            }
            item {
                Text(
                    "Current worker cadence: every ${ScanSettings.formatInterval(scanIntervalSeconds)}",
                    fontWeight = FontWeight.Medium
                )
            }
            item {
                Button(onClick = { expanded = true }) {
                    Text("Change worker cadence")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ScanSettings.ALLOWED_INTERVALS_SECONDS.forEach { seconds ->
                        DropdownMenuItem(
                            text = { Text("Every ${ScanSettings.formatInterval(seconds)}") },
                            onClick = {
                                onScanIntervalSelected(seconds)
                                expanded = false
                            }
                        )
                    }
                }
            }
            item {
                Text("Worker cadence controls how often scan batches wake up.")
            }
            item {
                Text("Per-source intervals are minimum per-source spacing; effective source cadence cannot exceed worker cadence.")
            }
            item {
                Text("Note: Under 15 min uses chained one-time work; 15+ min uses periodic work.")
            }
            item {
                Text("Per-Source Scan Intervals", fontWeight = FontWeight.Bold)
            }
            items(ScanSettings.SOURCE_TYPES) { sourceType ->
                val currentInterval = sourceScanIntervals[sourceType]
                    ?: ScanSettings.DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatSourceTypeLabel(sourceType), fontWeight = FontWeight.Medium)
                        Button(onClick = { sourceIntervalExpandedFor = sourceType }) {
                            Text(ScanSettings.formatInterval(currentInterval))
                        }
                        DropdownMenu(
                            expanded = sourceIntervalExpandedFor == sourceType,
                            onDismissRequest = { sourceIntervalExpandedFor = null }
                        ) {
                            ScanSettings.ALLOWED_SOURCE_SCAN_INTERVAL_SECONDS.forEach { seconds ->
                                DropdownMenuItem(
                                    text = { Text(ScanSettings.formatInterval(seconds)) },
                                    onClick = {
                                        onSourceScanIntervalSelected(sourceType, seconds)
                                        sourceIntervalExpandedFor = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Auto-adjust scan interval")
                            Switch(
                                checked = autoAdjustScanIntervalEnabled,
                                onCheckedChange = onAutoAdjustScanIntervalChanged
                            )
                        }
                        Text("Recommended now (overall): every ${ScanSettings.formatInterval(autoAdjustSuggestedIntervalSeconds)}")
                        if (recommendedBySource.isEmpty()) {
                            Text("Recommended per source: waiting for timing samples")
                        } else {
                            ScanSettings.SOURCE_TYPES.forEach { sourceType ->
                                val perSource = recommendedBySource[sourceType] ?: return@forEach
                                Text(
                                    "${formatSourceTypeLabel(sourceType)}: every ${ScanSettings.formatInterval(perSource)}"
                                )
                            }
                        }
                        Text("Auto mode raises interval quickly when overloaded and lowers gradually when stable.")
                    }
                }
            }
            item {
                Text(
                    "Last scan duration: ${lastScanDurationMs?.let(::formatScanDuration) ?: "n/a"}",
                    fontWeight = FontWeight.Medium
                )
            }
            if (intervalOverrun) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Warning: scan duration exceeded interval. Consider raising scan interval.",
                            color = Color(0xFFB3261E),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            item {
                Text("Per-Source Scan Timing", fontWeight = FontWeight.Bold)
            }
            if (sourceScanTimings.isEmpty()) {
                item {
                    Text("No timing samples yet. Start tracking or run live scans.")
                }
            } else {
                items(sourceScanTimings) { timing ->
                    val suggestedInterval = suggestSafeIntervalSeconds(timing.p95DurationMs)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(formatSourceTypeLabel(timing.sourceType), fontWeight = FontWeight.SemiBold)
                            Text("Samples: ${timing.sampleCount}")
                            Text("Last: ${formatScanDuration(timing.lastDurationMs)} | Avg: ${formatScanDuration(timing.averageDurationMs)}")
                            Text("p50: ${formatScanDuration(timing.p50DurationMs)} | p95: ${formatScanDuration(timing.p95DurationMs)} | Max: ${formatScanDuration(timing.maxDurationMs)}")
                            Text("Suggested safe interval: ${ScanSettings.formatInterval(suggestedInterval)}")
                        }
                    }
                }
            }
            item {
                Text("Auto-Adjust Activity Log", fontWeight = FontWeight.Bold)
            }
            if (scanIntervalChangeEvents.isEmpty()) {
                item {
                    Text("No interval changes logged yet.")
                }
            } else {
                items(scanIntervalChangeEvents) { event ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "${ScanSettings.formatInterval(event.fromSeconds)} -> ${ScanSettings.formatInterval(event.toSeconds)}",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("Reason: ${formatIntervalChangeReason(event.reason)}")
                            Text(formatEpoch(event.timestampEpochMs))
                        }
                    }
                }
            }
            item {
                Text("Live Map Updates", fontWeight = FontWeight.Bold)
            }
            item {
                Text(
                    "Current: every ${formatLiveMapIntervalLabel(liveMapUpdateIntervalSeconds)}",
                    fontWeight = FontWeight.Medium
                )
            }
            item {
                Button(onClick = { liveMapIntervalExpanded = true }) {
                    Text("Change live map interval")
                }
                DropdownMenu(expanded = liveMapIntervalExpanded, onDismissRequest = { liveMapIntervalExpanded = false }) {
                    ScanSettings.ALLOWED_LIVE_MAP_UPDATE_INTERVAL_SECONDS.forEach { seconds ->
                        DropdownMenuItem(
                            text = { Text(formatLiveMapIntervalLabel(seconds)) },
                            onClick = {
                                onLiveMapUpdateIntervalSelected(seconds)
                                liveMapIntervalExpanded = false
                            }
                        )
                    }
                }
            }
        }
        if (selectedSettingsTab == 2) {
            item {
                Text("Approach Detection", fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Enable approach detection")
                            Switch(
                                checked = approachDetectionEnabled,
                                onCheckedChange = onApproachDetectionChanged
                            )
                        }
                        Text("Approach detection drives approaching-state analysis and tracker-risk modeling.")
                    }
                }
            }
            item {
                Text("Foreign Signal Risk", fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Enable foreign signal scoring")
                            Switch(
                                checked = foreignSignalRiskEnabled,
                                onCheckedChange = onForeignSignalRiskEnabledChanged
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Direct acoustic channel")
                            Switch(
                                checked = foreignDirectAcousticEnabled,
                                onCheckedChange = onForeignDirectAcousticEnabledChanged
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Direct magnetometer channel")
                            Switch(
                                checked = foreignDirectMagneticEnabled,
                                onCheckedChange = onForeignDirectMagneticEnabledChanged
                            )
                        }
                        Text("Direct acoustic and magnetometer channels ingest live samples when enabled.")
                    }
                }
            }
        }
        if (selectedSettingsTab == 3) {
            item {
                Text("Notifications", fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Approach notifications")
                            Switch(
                                checked = approachNotificationsEnabled,
                                onCheckedChange = onApproachNotificationsChanged,
                                enabled = approachDetectionEnabled
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tracker suspicion alerts")
                            Switch(
                                checked = trackerNotificationsEnabled,
                                onCheckedChange = onTrackerNotificationsChanged,
                                enabled = approachDetectionEnabled
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Foreign signal alerts")
                            Switch(
                                checked = foreignSignalAlertsEnabled,
                                onCheckedChange = onForeignSignalAlertsEnabledChanged,
                                enabled = foreignSignalRiskEnabled
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Magnetic disturbance alerts")
                            Switch(
                                checked = magneticIncreaseNotificationsEnabled,
                                onCheckedChange = onMagneticIncreaseNotificationsChanged
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mesh peer connectivity alerts")
                            Switch(
                                checked = meshConnectivityNotificationsEnabled,
                                onCheckedChange = onMeshConnectivityNotificationsChanged
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mesh wipe lifecycle alerts")
                            Switch(
                                checked = meshWipeNotificationsEnabled,
                                onCheckedChange = onMeshWipeNotificationsChanged
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Alert threshold")
                            Button(
                                onClick = { foreignSignalThresholdExpanded = true },
                                enabled = foreignSignalRiskEnabled && foreignSignalAlertsEnabled
                            ) {
                                Text(foreignSignalAlertThreshold)
                            }
                            DropdownMenu(
                                expanded = foreignSignalThresholdExpanded,
                                onDismissRequest = { foreignSignalThresholdExpanded = false }
                            ) {
                                ScanSettings.ALLOWED_FOREIGN_SIGNAL_ALERT_THRESHOLDS.forEach { threshold ->
                                    DropdownMenuItem(
                                        text = { Text(threshold) },
                                        onClick = {
                                            onForeignSignalAlertThresholdChanged(threshold)
                                            foreignSignalThresholdExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Text("HIGH triggers earlier foreign-signal alerts; CRITICAL reduces noise.")
                    }
                }
            }
        }
        if (selectedSettingsTab == 4) {
            item {
                Text("Backup and Restore", fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Export creates an app-wide snapshot (encounters + settings/logs).")
                        Text("Import restores the latest snapshot from internal app backup storage.")
                        Text("Encrypted backup uses AES-GCM and a passphrase-derived key.")
                        OutlinedTextField(
                            value = backupPassphrase,
                            onValueChange = { backupPassphrase = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text("Encryption passphrase (min 8 chars)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                enabled = !backupActionInProgress,
                                onClick = {
                                    scope.launch {
                                        backupActionInProgress = true
                                        backupStatusMessage = runCatching { onExportBackup() }
                                            .getOrElse { error -> "Export failed: ${error.message ?: "unknown error"}" }
                                        backupActionInProgress = false
                                    }
                                }
                            ) {
                                Text(if (backupActionInProgress) "Working..." else "Export Backup")
                            }
                            Button(
                                enabled = !backupActionInProgress,
                                onClick = {
                                    scope.launch {
                                        backupActionInProgress = true
                                        backupStatusMessage = runCatching { onImportLatestBackup() }
                                            .getOrElse { error -> "Import failed: ${error.message ?: "unknown error"}" }
                                        backupActionInProgress = false
                                    }
                                }
                            ) {
                                Text("Import Latest Backup")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                enabled = !backupActionInProgress && hasStrongPassphrase,
                                onClick = {
                                    scope.launch {
                                        backupActionInProgress = true
                                        backupStatusMessage = runCatching {
                                            onExportEncryptedBackup(backupPassphrase.trim())
                                        }.getOrElse { error ->
                                            "Encrypted export failed: ${error.message ?: "unknown error"}"
                                        }
                                        backupActionInProgress = false
                                    }
                                }
                            ) {
                                Text(if (backupActionInProgress) "Working..." else "Export Encrypted Backup")
                            }
                            Button(
                                enabled = !backupActionInProgress && hasStrongPassphrase,
                                onClick = {
                                    scope.launch {
                                        backupActionInProgress = true
                                        backupStatusMessage = runCatching {
                                            onImportLatestEncryptedBackup(backupPassphrase.trim())
                                        }.getOrElse { error ->
                                            "Encrypted import failed: ${error.message ?: "unknown error"}"
                                        }
                                        backupActionInProgress = false
                                    }
                                }
                            ) {
                                Text("Import Latest Encrypted")
                            }
                        }
                        if (!hasStrongPassphrase) {
                            Text("Set a passphrase of at least 8 characters to enable encrypted backup/export.")
                        }
                        if (backupStatusMessage != null) {
                            Text(backupStatusMessage!!)
                        }
                        Text("Backups are stored in internal app files under backups/.")
                    }
                }
            }
            item {
                Text("Reset", fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Soft reset clears local encounters/devices and all logs.")
                        Text("Hard reset does soft reset plus wipes mesh network settings (chain link config, passphrase, and mesh toggles).")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = onSoftReset) {
                                Text("Soft Reset")
                            }
                            Button(onClick = onHardReset) {
                                Text("Hard Reset")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EvasionPage(
    profile: EvasionProfile,
    autoEscalateEnabled: Boolean,
    autoEscalateDurationSeconds: Long,
    escalationActiveUntilEpochMs: Long?,
    jitterEnabled: Boolean,
    jitterPercent: Int,
    burstEnabled: Boolean,
    burstWatchSeconds: Long,
    burstCooldownSeconds: Long,
    burstActiveUntilEpochMs: Long?,
    actionLog: List<ScanSettings.EvasionActionLogEntry>,
    onProfileSelected: (EvasionProfile) -> Unit,
    onAutoEscalateEnabledChanged: (Boolean) -> Unit,
    onAutoEscalateDurationSelected: (Long) -> Unit,
    onJitterEnabledChanged: (Boolean) -> Unit,
    onJitterPercentSelected: (Int) -> Unit,
    onBurstEnabledChanged: (Boolean) -> Unit,
    onBurstWatchSecondsSelected: (Long) -> Unit,
    onBurstCooldownSecondsSelected: (Long) -> Unit,
    onClearActionLog: () -> Unit
) {
    val context = LocalContext.current
    var durationExpanded by remember { mutableStateOf(false) }
    var jitterPercentExpanded by remember { mutableStateOf(false) }
    var burstWatchExpanded by remember { mutableStateOf(false) }
    var burstCooldownExpanded by remember { mutableStateOf(false) }
    var postureReadinessRefreshToken by remember { mutableStateOf(0) }
    val postureReadinessRows = remember(profile, postureReadinessRefreshToken) {
        val readinessItemsById = DetectionReadinessAdvisor.evaluate(context).associateBy { it.id }
        evasionPostureReadinessRows(profile, readinessItemsById)
    }
    val remainingEscalationSeconds = escalationActiveUntilEpochMs
        ?.let { ((it - System.currentTimeMillis()).coerceAtLeast(0L)) / 1000L }
    val remainingBurstSeconds = burstActiveUntilEpochMs
        ?.let { ((it - System.currentTimeMillis()).coerceAtLeast(0L)) / 1000L }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text("Evasion", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Text("Minimize exposure while preserving situational awareness with adaptive posture presets.")
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current Posture", fontWeight = FontWeight.Bold)
                    Text(evasionProfileSummary(profile))
                    Text("Posture controls in-app scan gates/cadence. Android radios/permissions still require OS settings.")
                    evasionProfileDetailLines(profile).forEach { detailLine ->
                        Text(detailLine)
                    }
                    EvasionPostureReadinessTable(
                        rows = postureReadinessRows,
                        onRefresh = { postureReadinessRefreshToken++ },
                        onOpenReadinessSetting = { item ->
                            runCatching {
                                context.startActivity(item.settingsIntent)
                            }.recoverCatching {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                                )
                            }
                        }
                    )
                    if (remainingEscalationSeconds != null && remainingEscalationSeconds > 0L) {
                        Text(
                            "Auto-escalation active for ${ScanSettings.formatInterval(remainingEscalationSeconds)}",
                            color = Color(0xFFB3261E),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EvasionProfile.entries.forEach { option ->
                            FilterChip(
                                selected = option == profile,
                                onClick = {
                                    if (option != profile) {
                                        onProfileSelected(option)
                                    }
                                },
                                label = { Text(evasionProfileLabel(option)) }
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Auto Escalation", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Escalate to WATCH on tracker risk HIGH")
                        Switch(
                            checked = autoEscalateEnabled,
                            onCheckedChange = onAutoEscalateEnabledChanged
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Escalation hold")
                        Button(onClick = { durationExpanded = true }) {
                            Text(ScanSettings.formatInterval(autoEscalateDurationSeconds))
                        }
                        DropdownMenu(
                            expanded = durationExpanded,
                            onDismissRequest = { durationExpanded = false }
                        ) {
                            ScanSettings.ALLOWED_EVASION_ESCALATE_DURATION_SECONDS.forEach { seconds ->
                                DropdownMenuItem(
                                    text = { Text(ScanSettings.formatInterval(seconds)) },
                                    onClick = {
                                        onAutoEscalateDurationSelected(seconds)
                                        durationExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Anti-Correlation Jitter", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable cadence jitter")
                        Switch(
                            checked = jitterEnabled,
                            onCheckedChange = onJitterEnabledChanged
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Jitter range")
                        Button(onClick = { jitterPercentExpanded = true }) {
                            Text("+/-${jitterPercent}%")
                        }
                        DropdownMenu(
                            expanded = jitterPercentExpanded,
                            onDismissRequest = { jitterPercentExpanded = false }
                        ) {
                            ScanSettings.ALLOWED_EVASION_JITTER_PERCENT.forEach { percent ->
                                DropdownMenuItem(
                                    text = { Text("+/-${percent}%") },
                                    onClick = {
                                        onJitterPercentSelected(percent)
                                        jitterPercentExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Text("Randomizes scan cadence around posture defaults to reduce stable timing fingerprints.")
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Burst Scheduler", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable burst cycle")
                        Switch(
                            checked = burstEnabled,
                            onCheckedChange = onBurstEnabledChanged
                        )
                    }
                    if (remainingBurstSeconds != null && remainingBurstSeconds > 0L) {
                        Text(
                            "Burst active for ${ScanSettings.formatInterval(remainingBurstSeconds)}",
                            color = Color(0xFF1565C0),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Watch burst")
                        Button(onClick = { burstWatchExpanded = true }) {
                            Text(ScanSettings.formatInterval(burstWatchSeconds))
                        }
                        DropdownMenu(
                            expanded = burstWatchExpanded,
                            onDismissRequest = { burstWatchExpanded = false }
                        ) {
                            ScanSettings.ALLOWED_EVASION_BURST_WATCH_SECONDS.forEach { seconds ->
                                DropdownMenuItem(
                                    text = { Text(ScanSettings.formatInterval(seconds)) },
                                    onClick = {
                                        onBurstWatchSecondsSelected(seconds)
                                        burstWatchExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cooldown")
                        Button(onClick = { burstCooldownExpanded = true }) {
                            Text(ScanSettings.formatInterval(burstCooldownSeconds))
                        }
                        DropdownMenu(
                            expanded = burstCooldownExpanded,
                            onDismissRequest = { burstCooldownExpanded = false }
                        ) {
                            ScanSettings.ALLOWED_EVASION_BURST_COOLDOWN_SECONDS.forEach { seconds ->
                                DropdownMenuItem(
                                    text = { Text(ScanSettings.formatInterval(seconds)) },
                                    onClick = {
                                        onBurstCooldownSecondsSelected(seconds)
                                        burstCooldownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Text("Runs periodic WATCH bursts followed by cooldown to balance awareness with reduced persistent exposure.")
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Action Log", fontWeight = FontWeight.Bold)
                        if (actionLog.isNotEmpty()) {
                            Button(onClick = onClearActionLog) {
                                Text("Clear Log")
                            }
                        }
                    }
                    if (actionLog.isEmpty()) {
                        Text("No evasion actions yet.")
                    } else {
                        actionLog.take(20).forEach { entry ->
                            Text("${formatEpoch(entry.timestampEpochMs)} • ${entry.action} • ${entry.detail}")
                        }
                    }
                }
            }
        }
    }
}

private fun evasionProfileLabel(profile: EvasionProfile): String = when (profile) {
    EvasionProfile.QUIET -> "Quiet"
    EvasionProfile.BALANCED -> "Balanced"
    EvasionProfile.WATCH -> "Watch"
}

private fun evasionProfileSummary(profile: EvasionProfile): String = when (profile) {
    EvasionProfile.QUIET -> "Quiet: sensors gated down and slower scan cadence to reduce emitted/observable surface."
    EvasionProfile.BALANCED -> "Balanced: core awareness enabled with moderate cadence and reduced nonessential sources."
    EvasionProfile.WATCH -> "Watch: full awareness with faster scan cadence for short elevated-risk windows."
}

private fun evasionProfileDetailLines(profile: EvasionProfile): List<String> = when (profile) {
    EvasionProfile.QUIET -> listOf(
        "Sensors: Wi-Fi OFF • Bluetooth OFF • Cellular OFF • Remote ID OFF",
        "Global cadence: every 15 min",
        "Per-source cadence: every 5 min"
    )

    EvasionProfile.BALANCED -> listOf(
        "Sensors: Wi-Fi ON • Bluetooth ON • Cellular ON • Remote ID OFF",
        "Global cadence: every 15 sec",
        "Per-source cadence: Wi-Fi/BLE 15s • Wi-Fi Direct/BT Classic/Cell/UWB/SDR 30s • Remote ID 60s"
    )

    EvasionProfile.WATCH -> listOf(
        "Sensors: Wi-Fi ON • Bluetooth ON • Cellular ON • Remote ID ON",
        "Global cadence: every 3 sec",
        "Per-source cadence: every 3 sec"
    )
}

private data class EvasionPostureReadinessRow(
    val label: String,
    val neededState: String,
    val currentState: String,
    val isCompliant: Boolean,
    val readinessItem: DetectionReadinessItem?
)

private fun evasionPostureReadinessRows(
    profile: EvasionProfile,
    readinessItemsById: Map<String, DetectionReadinessItem>
): List<EvasionPostureReadinessRow> {
    val targetIds = when (profile) {
        EvasionProfile.QUIET -> listOf(
            "setting_wifi",
            "setting_bluetooth",
            "setting_location_services",
            "setting_battery_optimization"
        )

        EvasionProfile.BALANCED -> listOf(
            "perm_fine_location",
            "perm_background_location",
            "perm_ble_scan",
            "setting_location_services",
            "setting_wifi",
            "setting_bluetooth",
            "setting_battery_optimization"
        )

        EvasionProfile.WATCH -> listOf(
            "perm_fine_location",
            "perm_background_location",
            "perm_ble_scan",
            "perm_nearby_wifi",
            "setting_location_services",
            "setting_wifi_scanning",
            "setting_bluetooth_scanning",
            "setting_wifi",
            "setting_bluetooth",
            "setting_battery_optimization",
            "perm_notifications"
        )
    }

    return targetIds.mapNotNull { id ->
        val item = readinessItemsById[id] ?: return@mapNotNull null
        val neededState = when {
            profile == EvasionProfile.QUIET && id == "setting_wifi" -> "Disabled"
            profile == EvasionProfile.QUIET && id == "setting_bluetooth" -> "Disabled"
            profile == EvasionProfile.QUIET && id == "setting_location_services" -> "Enabled"
            else -> item.recommendedValue
        }
        val currentState = item.currentValue
        EvasionPostureReadinessRow(
            label = item.title,
            neededState = neededState,
            currentState = currentState,
            isCompliant = currentState.equals(neededState, ignoreCase = true),
            readinessItem = item
        )
    }
}

@Composable
private fun EvasionPostureReadinessTable(
    rows: List<EvasionPostureReadinessRow>,
    onRefresh: () -> Unit,
    onOpenReadinessSetting: (DetectionReadinessItem) -> Unit
) {
    if (rows.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Posture readiness", fontWeight = FontWeight.Bold)
            Button(onClick = onRefresh) {
                Text("Refresh")
            }
        }
        rows.forEach { row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(row.label, modifier = Modifier.weight(1.2f))
                    Text(
                        "Need ${row.neededState}",
                        modifier = Modifier.weight(0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        row.currentState,
                        modifier = Modifier.weight(0.8f),
                        color = if (row.isCompliant) Color(0xFF2E7D32) else Color(0xFFB3261E),
                        fontWeight = FontWeight.Medium
                    )
                    if (!row.isCompliant && row.readinessItem != null) {
                        AssistChip(
                            onClick = { onOpenReadinessSetting(row.readinessItem) },
                            label = { Text("Open") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChainMeshVisualizer(
    snapshot: ChainMeshSnapshot,
    sharePreciseLocationEnabled: Boolean
) {
    val context = LocalContext.current
    val hasMapsApiKey = remember(context) { hasGoogleMapsApiKey(context) }
    var localLocation by remember { mutableStateOf(LocationSnapshotProvider.read(context)) }
    val hasLocalLocation = remember(localLocation) {
        val currentLocation = localLocation
        currentLocation != null && isValidLatLon(currentLocation.lat, currentLocation.lon)
    }
    val localLatLng = remember(localLocation, hasLocalLocation) {
        val currentLocation = localLocation
        if (hasLocalLocation && currentLocation != null) {
            LatLng(currentLocation.lat, currentLocation.lon)
        } else {
            LatLng(37.4219999, -122.0840575)
        }
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(localLatLng, 16f)
    }
    val peers = remember(snapshot.peers) { snapshot.peers.take(24) }
    val peersWithSharedLocation = remember(peers) {
        peers.mapNotNull { peer ->
            val lat = peer.sharedLocationLat
            val lon = peer.sharedLocationLon
            if (isValidLatLon(lat, lon)) {
                peer to LatLng(lat!!, lon!!)
            } else {
                null
            }
        }
    }
    val peersWithoutSharedLocation = remember(peers, peersWithSharedLocation) {
        val locatedIds = peersWithSharedLocation.map { it.first.nodeId }.toSet()
        peers.filterNot { it.nodeId in locatedIds }
    }
    val relativeFallbackPositions = remember(peersWithoutSharedLocation, localLatLng) {
        if (peersWithoutSharedLocation.isEmpty()) {
            emptyList()
        } else {
            val step = 360.0 / peersWithoutSharedLocation.size.toDouble()
            peersWithoutSharedLocation.mapIndexed { index, peer ->
                val ringMeters = 65.0 + ((index / 8) * 25.0)
                val bearing = step * index.toDouble()
                peer to offsetLatLng(localLatLng, ringMeters, bearing)
            }
        }
    }
    val peerPositions = remember(peersWithSharedLocation, relativeFallbackPositions) {
        val precise = peersWithSharedLocation.map { (peer, point) -> Triple(peer, point, true) }
        val fallback = relativeFallbackPositions.map { (peer, point) -> Triple(peer, point, false) }
        precise + fallback
    }
    val hasAnySharedPeerLocations = peersWithSharedLocation.isNotEmpty()

    LaunchedEffect(context) {
        while (true) {
            localLocation = LocationSnapshotProvider.read(context)
            delay(5_000)
        }
    }

    LaunchedEffect(localLatLng, peerPositions.size) {
        if (peerPositions.isNotEmpty()) {
            val bounds = LatLngBounds.Builder().apply {
                if (hasLocalLocation || !hasAnySharedPeerLocations) {
                    include(localLatLng)
                }
                peerPositions.forEach { (_, point, _) -> include(point) }
            }.build()
            runCatching {
                cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            }
        } else {
            runCatching {
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(localLatLng, 16f))
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mesh Visualizer", fontWeight = FontWeight.SemiBold)
            if (!hasMapsApiKey) {
                Text("Map overlay unavailable: Google Maps API key is missing.")
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = true,
                            zoomGesturesEnabled = true,
                            scrollGesturesEnabled = true,
                            tiltGesturesEnabled = false,
                            rotationGesturesEnabled = false,
                            myLocationButtonEnabled = false
                        )
                    ) {
                        if (hasLocalLocation || !hasAnySharedPeerLocations) {
                            Marker(
                                state = MarkerState(position = localLatLng),
                                title = "This device",
                                snippet = "${snapshot.localDeviceName} • ${snapshot.localNodeId.take(20)}",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                            )
                        }

                        peerPositions.forEach { (peer, peerLatLng, isPrecise) ->
                            if (hasLocalLocation || !isPrecise) {
                                Polyline(
                                    points = listOf(localLatLng, peerLatLng),
                                    color = meshPeerColor(peer.state),
                                    width = 4f
                                )
                            }
                            Marker(
                                state = MarkerState(position = peerLatLng),
                                title = (peer.deviceName?.takeIf { it.isNotBlank() } ?: peer.nodeId).take(26),
                                snippet = "${peer.state.name} • ${peer.host.take(32)} • ${if (isPrecise) "shared precise" else "relative"}",
                                icon = BitmapDescriptorFactory.defaultMarker(meshPeerMarkerHue(peer.state))
                            )
                        }
                    }
                }
            }
            if (sharePreciseLocationEnabled && hasAnySharedPeerLocations) {
                Text("Mesh overlay includes peer-shared precise locations where available; remaining peers are shown relatively.")
            } else if (sharePreciseLocationEnabled) {
                Text("Precise sharing is enabled on this device. Peer markers remain relative until peers enable sharing too.")
            } else {
                Text("Relative mesh overlay: peers are shown topologically around your location.")
            }
            if (!hasLocalLocation) {
                Text("Local location unavailable on this device right now; some link lines may be hidden until location is available.")
            }
            Text("Blue center is this device (${snapshot.localDeviceName}). Green nodes are connected peers.")
        }
    }
}

@Composable
private fun DetectionPage(
    readinessItems: List<DetectionReadinessItem>,
    encounters: List<Encounter>,
    meshInsightEncounters: List<Encounter>,
    foreignSignalRiskEnabled: Boolean,
    approachDetectionEnabled: Boolean,
    ownedDeviceKeys: Set<String>,
    alertLogs: List<AlertLogEntry>,
    chainLinkEnabled: Boolean,
    chainNodeId: String,
    chainDeviceName: String,
    chainSharedSecret: String,
    chainAutoSyncEnabled: Boolean,
    chainAutoSyncIntervalSeconds: Long,
    chainPersistentChannelEnabled: Boolean,
    chainHeartbeatIntervalSeconds: Long,
    chainSharePreciseLocationEnabled: Boolean,
    liveMapUpdateIntervalSeconds: Long,
    chainMeshSnapshot: ChainMeshSnapshot,
    onEncounterMapPinClick: (source: String, primaryId: String, timestampEpochMs: Long) -> Unit,
    onDeviceMapPinClick: (source: String, primaryId: String, lat: Double?, lon: Double?, timestampEpochMs: Long?) -> Unit,
    onMovingDeviceMapPinClick: (source: String, primaryId: String) -> Unit,
    onRefresh: () -> Unit,
    onLiveCollect: suspend () -> String,
    onOpenReadinessSetting: (DetectionReadinessItem) -> Unit,
    onClearAlertLogs: () -> Unit,
    onOpenApproachLogMap: (source: String, primaryId: String) -> Unit,
    onChainLinkChanged: (Boolean) -> Unit,
    onChainDeviceNameChanged: (String) -> Unit,
    onChainSharedSecretChanged: (String) -> Unit,
    onChainAutoSyncChanged: (Boolean) -> Unit,
    onChainAutoSyncIntervalChanged: (Long) -> Unit,
    onChainPersistentChannelChanged: (Boolean) -> Unit,
    onChainHeartbeatIntervalChanged: (Long) -> Unit,
    onChainSharePreciseLocationChanged: (Boolean) -> Unit,
    onRefreshPeers: suspend () -> Unit,
    onSendLinkRequest: suspend (host: String, message: String?) -> Boolean,
    onSyncNow: suspend () -> String,
    onWipeMeshData: suspend () -> String
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var encounterPinLimit by rememberSaveable { mutableStateOf(1000) }
    var cellDevicePinLimit by rememberSaveable { mutableStateOf(1000) }
    var movingOnlyOnDeviceMap by rememberSaveable { mutableStateOf(false) }
    var sinceSnapshotOnlyOnDeviceMap by rememberSaveable { mutableStateOf(false) }
    var deviceMapSnapshotEpochMs by rememberSaveable { mutableStateOf<Long?>(null) }
    val tabs = listOf("Readiness", "Signal Intel", "Device Encounters Map", "Device Location Map", "Alert Logs", "Mesh Network")
    val isDeviceLocationTabActive = selectedTab == 3
    val foreignSignalRisk = remember(meshInsightEncounters, foreignSignalRiskEnabled) {
        if (foreignSignalRiskEnabled) analyzeForeignSignalRisk(meshInsightEncounters) else null
    }
    val signalIntel = remember(meshInsightEncounters, foreignSignalRiskEnabled) {
        buildSignalIntelSnapshot(meshInsightEncounters, foreignSignalRiskEnabled)
    }

    val encounterPins = remember(encounters) {
        encounters
            .asSequence()
            .mapNotNull { encounter ->
                val lat = encounter.lat
                val lon = encounter.lon
                if (!isValidLatLon(lat, lon)) {
                    null
                } else {
                    val provenanceBadge = provenanceBadge(encounter.provenance, encounter.provenanceNodeId)
                    MapPin(
                        position = LatLng(lat!!, lon!!),
                        title = "${encounter.source} • ${encounter.primaryId}$provenanceBadge",
                        snippet = "${formatEpoch(encounter.timestampEpochMs)}$provenanceBadge",
                        timestampEpochMs = encounter.timestampEpochMs,
                        source = encounter.source.name,
                        primaryId = encounter.primaryId,
                        encounterTimestampEpochMs = encounter.timestampEpochMs,
                        motionBadge = null,
                        motionSpeedMps = null
                    )
                }
            }
            .sortedByDescending { it.timestampEpochMs }
            .toList()
    }

    val allDeviceCandidates = remember(isDeviceLocationTabActive, meshInsightEncounters, approachDetectionEnabled, ownedDeviceKeys) {
        if (!isDeviceLocationTabActive) {
            emptyList()
        } else {
            meshInsightEncounters
                .asSequence()
                .groupBy { "${it.source.name}|${it.primaryId}" }
                .mapNotNull { (_, deviceEncounters) ->
                    val latest = deviceEncounters.maxByOrNull { it.timestampEpochMs } ?: return@mapNotNull null
                    val owned = OwnedDeviceRegistry.keyFor(latest.source.name, latest.primaryId) in ownedDeviceKeys
                    val approachSignal = if (approachDetectionEnabled) {
                        analyzeApproachSignal(deviceEncounters)
                    } else {
                        null
                    }
                    val motionSignal = analyzeMotionSignal(deviceEncounters)
                    DeviceLocationCandidate(
                        source = latest.source.name,
                        primaryId = latest.primaryId,
                        secondaryId = latest.secondaryId,
                        latestTimestampEpochMs = latest.timestampEpochMs,
                        seenCount = deviceEncounters.size,
                        encounters = deviceEncounters,
                        hasChainLinkedData = deviceEncounters.any { it.provenance == EncounterProvenance.CHAIN_LINKED },
                        chainLinkedPeerCount = deviceEncounters.mapNotNull { it.provenanceNodeId }.toSet().size,
                        isOwned = owned,
                        approachSignal = approachSignal,
                        motionSignal = motionSignal,
                        trackerRisk = analyzeTrackerRisk(
                            encounters = deviceEncounters,
                            isOwned = owned,
                            approachSignal = approachSignal
                        )
                    )
                }
                .sortedByDescending { it.latestTimestampEpochMs }
        }
    }

    val deviceLocationLookupKey = remember(allDeviceCandidates) {
        allDeviceCandidates.joinToString(separator = "|") { candidate ->
            "${candidate.source}:${candidate.primaryId}:${candidate.latestTimestampEpochMs}:${candidate.seenCount}"
        }
    }
    var estimatedDeviceLocationPins by remember { mutableStateOf(emptyList<MapPin>()) }

    LaunchedEffect(deviceLocationLookupKey, isDeviceLocationTabActive) {
        if (!isDeviceLocationTabActive || allDeviceCandidates.isEmpty()) {
            estimatedDeviceLocationPins = emptyList()
            return@LaunchedEffect
        }

        val resolvedCandidates = buildList {
            allDeviceCandidates.forEach { candidate ->
                val latest = candidate.encounters.maxByOrNull { it.timestampEpochMs } ?: return@forEach
                val sourceEnum = runCatching { EncounterSource.valueOf(candidate.source) }.getOrDefault(EncounterSource.UNKNOWN_RF)
                val resolvedLocation = resolveDeviceLocation(
                    source = sourceEnum,
                    encounters = candidate.encounters
                )
                val resolvedCandidate = resolvedLocation?.let { resolved ->
                    candidate.copy(
                        approximateLocation = DetectionLocation(resolved.lat, resolved.lon),
                        approximateMethod = resolved.method,
                        approximateRangeMeters = resolved.approximateRangeMeters
                    )
                }

                if (resolvedCandidate != null) {
                    add(resolvedCandidate)
                }
            }
        }

        val resolvedPins = resolvedCandidates.mapNotNull { candidate ->
            val location = candidate.approximateLocation ?: return@mapNotNull null
            if (!isValidLatLon(location.lat, location.lon)) return@mapNotNull null

            val rangeSnippet = candidate.approximateRangeMeters
                ?.let { " • Approx range ${formatDistanceFeetMiles(it)}" }
                .orEmpty()
            val approachSnippet = candidate.approachSignal
                ?.takeIf { it.isApproaching }
                ?.let { signal ->
                    " • Approaching (${(signal.confidence * 100.0).toInt()}%)"
                }
                .orEmpty()
            val motionSnippet = candidate.motionSignal?.let { signal ->
                if (signal.isInMotion) {
                    " • Moving ${formatSpeedLabel(signal.speedMps)} ${formatHeadingCardinal(signal.headingDeg)}"
                } else {
                    " • Not moving"
                }
            }.orEmpty()
            val ownershipSnippet = if (candidate.isOwned) {
                " • Marked as Mine"
            } else {
                ""
            }
            val chainSnippet = if (candidate.hasChainLinkedData) {
                " • CHAIN x${candidate.chainLinkedPeerCount.coerceAtLeast(1)}"
            } else {
                ""
            }
            val trackerSnippet = when (candidate.trackerRisk?.level) {
                TrackerRiskLevel.HIGH -> " • Tracker Risk HIGH"
                TrackerRiskLevel.MEDIUM -> " • Tracker Risk MEDIUM"
                TrackerRiskLevel.LOW -> " • Tracker Risk LOW"
                else -> ""
            }
            val motionBadge = candidate.motionSignal?.let { if (it.isInMotion) "MOVING" else "STATIC" }
            val motionLine = candidate.motionSignal?.let { signal ->
                if (signal.isInMotion) {
                    "Motion: MOVING ${formatSpeedLabel(signal.speedMps)} ${formatHeadingCardinal(signal.headingDeg)}"
                } else {
                    "Motion: STATIC ${formatSpeedLabel(signal.speedMps)}"
                }
            } ?: "Motion: n/a"
            val topSpeedLine = DeviceSpeedRecordStore
                .getRecordSpeedMps(context, candidate.source, candidate.primaryId)
                ?.let { " • Top ${formatSpeedLabel(it)}" }
                .orEmpty()

            MapPin(
                position = LatLng(location.lat, location.lon),
                title = buildPinTitle(
                    sourceLabel = listSourceLabel(candidate.source, candidate.secondaryId),
                    primaryId = candidate.primaryId,
                    motionBadge = motionBadge
                ),
                snippet = buildThreeLineSnippet(
                    line1 = "Seen ${candidate.seenCount}x",
                    line2 = "Last ${formatEpoch(candidate.latestTimestampEpochMs)}$rangeSnippet$approachSnippet",
                    line3 = "$motionLine$topSpeedLine$ownershipSnippet$chainSnippet$trackerSnippet"
                ),
                timestampEpochMs = candidate.latestTimestampEpochMs,
                source = candidate.source,
                primaryId = candidate.primaryId,
                encounterTimestampEpochMs = null,
                motionBadge = motionBadge,
                motionSpeedMps = candidate.motionSignal?.speedMps
            )
        }

        estimatedDeviceLocationPins = spreadOverlappingMapPins(resolvedPins)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Detection", style = MaterialTheme.typography.headlineMedium)
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        if (selectedTab == 0) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    Text("Recommended settings and permissions for stronger detections.")
                }
                item {
                    Button(onClick = onRefresh) {
                        Text("Refresh Readiness")
                    }
                }
                item {
                    if (!foreignSignalRiskEnabled) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Foreign Signal Risk: disabled in settings.",
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else if (foreignSignalRisk == null) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Foreign Signal Risk: waiting for enough recent samples.",
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        val riskColor = when (foreignSignalRisk.level) {
                            ForeignSignalRiskLevel.CRITICAL -> Color(0xFFB3261E)
                            ForeignSignalRiskLevel.HIGH -> Color(0xFFD84315)
                            ForeignSignalRiskLevel.ELEVATED -> Color(0xFFE65100)
                            ForeignSignalRiskLevel.QUIET -> Color(0xFF2E7D32)
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Foreign Signal Risk: ${foreignSignalRisk.level.name} (${foreignSignalRisk.score}/100)",
                                    color = riskColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(foreignSignalRisk.summary)
                                Text(
                                    "Confidence ${String.format(Locale.US, "%.0f%%", foreignSignalRisk.confidence * 100.0)} " +
                                        "| Window ${String.format(Locale.US, "%.1f min", foreignSignalRisk.windowMinutes)} " +
                                        "| Samples ${foreignSignalRisk.sampleCount}"
                                )
                                Text(
                                    "Cell ${formatRiskScorePct(foreignSignalRisk.cellularAnomalyScore)} | " +
                                        "Wi-Fi ${formatRiskScorePct(foreignSignalRisk.wifiAnomalyScore)} | " +
                                        "BLE ${formatRiskScorePct(foreignSignalRisk.bleAnomalyScore)}"
                                )
                                Text(
                                    "GNSS ${formatRiskScorePct(foreignSignalRisk.gnssInterferenceScore)} | " +
                                        "UWB ${formatRiskScorePct(foreignSignalRisk.uwbActivityScore)} | " +
                                        "RF Texture ${formatRiskScorePct(foreignSignalRisk.rfTextureScore)}"
                                )
                                Text(
                                    "Acoustic ${if (foreignSignalRisk.directAcousticObserved) "direct" else "proxy"} ${formatRiskScorePct(foreignSignalRisk.acousticProxyScore)} | " +
                                        "Magnetic ${if (foreignSignalRisk.directMagneticObserved) "direct" else "proxy"} ${formatRiskScorePct(foreignSignalRisk.magneticProxyScore)}"
                                )
                                if (foreignSignalRisk.unavailableSignals.isNotEmpty()) {
                                    Text(
                                        "Unavailable on current pipeline: ${foreignSignalRisk.unavailableSignals.joinToString(", ")}",
                                        color = Color(0xFF5F6368)
                                    )
                                }
                            }
                        }
                    }
                }
                items(readinessItems) { item ->
                    val statusText = if (item.isMissing) "MISSING" else "READY"
                    val statusColor = if (item.isMissing) Color(0xFFB3261E) else Color(0xFF2E7D32)
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(item.title, fontWeight = FontWeight.Bold)
                                Text(statusText, color = statusColor, fontWeight = FontWeight.Bold)
                            }
                            Text("Current: ${item.currentValue}")
                            Text("Recommended: ${item.recommendedValue}")
                            Button(onClick = { onOpenReadinessSetting(item) }) {
                                Text(item.openSettingsLabel)
                            }
                        }
                    }
                }
            }
        } else if (selectedTab == 1) {
            DetectionSignalIntelPage(
                intel = signalIntel,
                riskEnabled = foreignSignalRiskEnabled,
                onRefresh = onRefresh
            )
        } else if (selectedTab == 2) {
            DetectionMapPage(
                mapTitle = "Device Encounters Map",
                mapDescription = "Pins show individual encounter points.",
                pins = encounterPins,
                pinLimit = encounterPinLimit,
                onPinLimitChange = { encounterPinLimit = it },
                onPinDetailsClick = { pin ->
                    val timestamp = pin.encounterTimestampEpochMs ?: return@DetectionMapPage
                    onEncounterMapPinClick(pin.source, pin.primaryId, timestamp)
                },
                liveUpdatesAllowed = false,
                onLiveCollect = onLiveCollect,
                liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds
            )
        } else if (selectedTab == 3) {
            val deviceMapPins = estimatedDeviceLocationPins
                .asSequence()
                .filter { pin ->
                    if (!movingOnlyOnDeviceMap) true else pin.motionBadge == "MOVING"
                }
                .filter { pin ->
                    if (!sinceSnapshotOnlyOnDeviceMap) {
                        true
                    } else {
                        val snapshotEpoch = deviceMapSnapshotEpochMs
                        snapshotEpoch != null && pin.timestampEpochMs >= snapshotEpoch
                    }
                }
                .toList()
            DetectionMapPage(
                mapTitle = "Device Location Map",
                mapDescription = "Pins show estimated cell tower locations and inferred Wi-Fi/BLE device locations.",
                pins = deviceMapPins,
                pinLimit = cellDevicePinLimit,
                onPinLimitChange = { cellDevicePinLimit = it },
                onPinDetailsClick = { pin ->
                    if (pin.motionBadge == "MOVING") {
                        onMovingDeviceMapPinClick(pin.source, pin.primaryId)
                    } else {
                        onDeviceMapPinClick(
                            pin.source,
                            pin.primaryId,
                            pin.position.latitude,
                            pin.position.longitude,
                            pin.timestampEpochMs
                        )
                    }
                },
                liveUpdatesAllowed = true,
                useSourceOnlyPinColors = true,
                showMovingOnlyControl = true,
                movingOnlyEnabled = movingOnlyOnDeviceMap,
                onMovingOnlyEnabledChange = { movingOnlyOnDeviceMap = it },
                showSinceSnapshotControl = true,
                sinceSnapshotEnabled = sinceSnapshotOnlyOnDeviceMap,
                snapshotEpochMs = deviceMapSnapshotEpochMs,
                onSinceSnapshotEnabledChange = { enabled ->
                    sinceSnapshotOnlyOnDeviceMap = enabled
                    if (enabled && deviceMapSnapshotEpochMs == null) {
                        deviceMapSnapshotEpochMs = System.currentTimeMillis()
                    }
                },
                onCaptureSnapshot = {
                    deviceMapSnapshotEpochMs = System.currentTimeMillis()
                },
                onLiveCollect = onLiveCollect,
                liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds
            )
        } else if (selectedTab == 4) {
            DetectionLogsPage(
                logs = alertLogs,
                onClearLogs = onClearAlertLogs,
                onOpenApproachMap = onOpenApproachLogMap
            )
        } else {
            DetectionMeshNetworkPage(
                encounters = meshInsightEncounters,
                chainLinkEnabled = chainLinkEnabled,
                chainNodeId = chainNodeId,
                chainDeviceName = chainDeviceName,
                chainSharedSecret = chainSharedSecret,
                chainAutoSyncEnabled = chainAutoSyncEnabled,
                chainAutoSyncIntervalSeconds = chainAutoSyncIntervalSeconds,
                chainPersistentChannelEnabled = chainPersistentChannelEnabled,
                chainHeartbeatIntervalSeconds = chainHeartbeatIntervalSeconds,
                chainSharePreciseLocationEnabled = chainSharePreciseLocationEnabled,
                chainMeshSnapshot = chainMeshSnapshot,
                onChainLinkChanged = onChainLinkChanged,
                onChainDeviceNameChanged = onChainDeviceNameChanged,
                onChainSharedSecretChanged = onChainSharedSecretChanged,
                onChainAutoSyncChanged = onChainAutoSyncChanged,
                onChainAutoSyncIntervalChanged = onChainAutoSyncIntervalChanged,
                onChainPersistentChannelChanged = onChainPersistentChannelChanged,
                onChainHeartbeatIntervalChanged = onChainHeartbeatIntervalChanged,
                onChainSharePreciseLocationChanged = onChainSharePreciseLocationChanged,
                onRefreshPeers = onRefreshPeers,
                onSendLinkRequest = onSendLinkRequest,
                onSyncNow = onSyncNow,
                onWipeMeshData = onWipeMeshData
            )
        }
    }
}

private data class SignalIntelSnapshot(
    val encounterWindowCount: Int,
    val uwbEncounterCount: Int,
    val uwbUniqueDeviceCount: Int,
    val lastUwbEpochMs: Long?,
    val gnssLocationSampleCount: Int,
    val gnssInterferenceScore: Double,
    val rfTextureScore: Double,
    val rfRssiSampleCount: Int,
    val acousticDirectSampleCount: Int,
    val acousticDirectTotalCount: Int,
    val lastAcousticRmsDbFs: Double?,
    val magneticDirectSampleCount: Int,
    val magneticDirectTotalCount: Int,
    val lastMagneticMagnitudeMicroTesla: Double?,
    val foreignRiskScore: Int?,
    val foreignRiskLevel: ForeignSignalRiskLevel?,
    val knowledgeGaps: List<String>
)

private fun buildSignalIntelSnapshot(
    encounters: List<Encounter>,
    foreignSignalRiskEnabled: Boolean
): SignalIntelSnapshot {
    val window = selectRecentEncounterWindow(
        encounters = encounters,
        windowMs = SIGNAL_INTEL_WINDOW_MS,
        maxEncounters = SIGNAL_INTEL_MAX_ENCOUNTERS
    )

    val uwbEncounters = window.filter { it.source == EncounterSource.UWB }
    val lastUwbEpochMs = uwbEncounters.maxOfOrNull { it.timestampEpochMs }

    val gnssLocations = window.count { isValidLatLon(it.lat, it.lon) }
    val gnssScore = computeGnssInterferenceScore(window)

    val rfSamples = window.count { it.rssiDbm != null }
    val rfTextureScore = computeRfTextureScore(window)

    val acousticDirect = window
        .asSequence()
        .filter { isDirectSignalChannel(it, "acoustic") }
        .toList()
    val acousticDirectTotalCount = encounters.count { isDirectSignalChannel(it, "acoustic") }
    val lastAcousticPayload = acousticDirect.maxByOrNull { it.timestampEpochMs }
        ?.let(::parseEncounterPayload)
    val lastAcousticRmsDbFs = lastAcousticPayload
        ?.optDouble("rmsDbFs", Double.NaN)
        ?.takeIf { it.isFinite() }

    val magneticDirect = window
        .asSequence()
        .filter { isDirectSignalChannel(it, "magnetic") }
        .toList()
    val magneticDirectTotalCount = encounters.count { isDirectSignalChannel(it, "magnetic") }
    val lastMagneticPayload = magneticDirect.maxByOrNull { it.timestampEpochMs }
        ?.let(::parseEncounterPayload)
    val lastMagneticMagnitudeMicroTesla = lastMagneticPayload
        ?.optDouble("magnitudeMicroTesla", Double.NaN)
        ?.takeIf { it.isFinite() }

    val foreignRisk = if (foreignSignalRiskEnabled) analyzeForeignSignalRisk(window) else null

    val gaps = buildList {
        if (uwbEncounters.isEmpty()) {
            add("No UWB encounters in recent window. Verify UWB source toggle or ingest feed.")
        }
        if (gnssLocations < 4) {
            add("Insufficient location samples for reliable GNSS interference inference.")
        }
        if (rfSamples < 6) {
            add("Insufficient RSSI samples for stable RF texture scoring.")
        }
        if (acousticDirect.isEmpty()) {
            add("No direct acoustic samples observed. Check microphone permission and direct acoustic toggle.")
        }
        if (magneticDirect.isEmpty()) {
            add("No direct magnetometer samples observed. Check magnetometer availability and direct magnetic toggle.")
        }
        val sdrSeen = window.any { it.source == EncounterSource.SDR }
        if (!sdrSeen) {
            add("No SDR feed observations in recent window. Configure SDR ingest if expected.")
        }
    }

    return SignalIntelSnapshot(
        encounterWindowCount = window.size,
        uwbEncounterCount = uwbEncounters.size,
        uwbUniqueDeviceCount = uwbEncounters.map { it.primaryId }.toSet().size,
        lastUwbEpochMs = lastUwbEpochMs,
        gnssLocationSampleCount = gnssLocations,
        gnssInterferenceScore = gnssScore,
        rfTextureScore = rfTextureScore,
        rfRssiSampleCount = rfSamples,
        acousticDirectSampleCount = acousticDirect.size,
        acousticDirectTotalCount = acousticDirectTotalCount,
        lastAcousticRmsDbFs = lastAcousticRmsDbFs,
        magneticDirectSampleCount = magneticDirect.size,
        magneticDirectTotalCount = magneticDirectTotalCount,
        lastMagneticMagnitudeMicroTesla = lastMagneticMagnitudeMicroTesla,
        foreignRiskScore = foreignRisk?.score,
        foreignRiskLevel = foreignRisk?.level,
        knowledgeGaps = gaps
    )
}

@Composable
private fun DetectionSignalIntelPage(
    intel: SignalIntelSnapshot,
    riskEnabled: Boolean,
    onRefresh: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Signal Intel", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Window", fontWeight = FontWeight.Bold)
                    Text("Recent encounters sampled (last ${SIGNAL_INTEL_WINDOW_MINUTES}m): ${intel.encounterWindowCount}")
                    if (riskEnabled && intel.foreignRiskScore != null && intel.foreignRiskLevel != null) {
                        Text("Foreign signal score: ${intel.foreignRiskScore}/100 (${intel.foreignRiskLevel.name})")
                    } else {
                        Text("Foreign signal scoring disabled in settings.")
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("UWB", fontWeight = FontWeight.Bold)
                    Text("Encounters: ${intel.uwbEncounterCount}")
                    Text("Unique devices: ${intel.uwbUniqueDeviceCount}")
                    Text("Last seen: ${intel.lastUwbEpochMs?.let(::formatEpoch) ?: "n/a"}")
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("GNSS and RF Texture", fontWeight = FontWeight.Bold)
                    Text("GNSS location samples: ${intel.gnssLocationSampleCount}")
                    Text("GNSS interference score: ${formatRiskScorePct(intel.gnssInterferenceScore)}")
                    Text("RF texture score: ${formatRiskScorePct(intel.rfTextureScore)}")
                    Text("RF RSSI samples: ${intel.rfRssiSampleCount}")
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Direct Acoustic", fontWeight = FontWeight.Bold)
                    Text("Samples (last ${SIGNAL_INTEL_WINDOW_MINUTES}m): ${intel.acousticDirectSampleCount}")
                    Text("Total stored: ${intel.acousticDirectTotalCount}")
                    Text(
                        "Latest RMS: ${intel.lastAcousticRmsDbFs?.let { String.format(Locale.US, "%.1f dBFS", it) } ?: "n/a"}"
                    )
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Direct Magnetometer", fontWeight = FontWeight.Bold)
                    Text("Samples (last ${SIGNAL_INTEL_WINDOW_MINUTES}m): ${intel.magneticDirectSampleCount}")
                    Text("Total stored: ${intel.magneticDirectTotalCount}")
                    Text(
                        "Latest magnitude: ${intel.lastMagneticMagnitudeMicroTesla?.let { String.format(Locale.US, "%.2f uT", it) } ?: "n/a"}"
                    )
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Knowledge Gaps", fontWeight = FontWeight.Bold)
                    if (intel.knowledgeGaps.isEmpty()) {
                        Text("No major coverage gaps detected in the current sampling window.")
                    } else {
                        intel.knowledgeGaps.forEach { gap ->
                            Text("- $gap")
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DetectionLogsPage(
    logs: List<AlertLogEntry>,
    onClearLogs: () -> Unit,
    onOpenApproachMap: (source: String, primaryId: String) -> Unit
) {
    var showApproachLogs by rememberSaveable { mutableStateOf(true) }
    var showTrackerLogs by rememberSaveable { mutableStateOf(true) }
    var showForeignSignalLogs by rememberSaveable { mutableStateOf(true) }

    val filteredLogs = remember(logs, showApproachLogs, showTrackerLogs, showForeignSignalLogs) {
        logs.filter { entry ->
            (showApproachLogs && entry.type == AlertLogType.APPROACH) ||
                (showTrackerLogs && entry.type == AlertLogType.TRACKER) ||
                (showForeignSignalLogs && entry.type == AlertLogType.FOREIGN_SIGNAL)
        }.sortedByDescending { it.timestampEpochMs }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Alert Logs", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onClearLogs, enabled = logs.isNotEmpty()) {
                    Text("Clear")
                }
            }
        }
        item {
            Text("Review historical approach and tracker-risk events.")
        }
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Approach")
                    Switch(
                        checked = showApproachLogs,
                        onCheckedChange = { showApproachLogs = it }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tracker")
                    Switch(
                        checked = showTrackerLogs,
                        onCheckedChange = { showTrackerLogs = it }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Foreign Signal")
                    Switch(
                        checked = showForeignSignalLogs,
                        onCheckedChange = { showForeignSignalLogs = it }
                    )
                }
            }
        }
        item {
            Text("Showing ${filteredLogs.size} of ${logs.size} logs")
        }

        if (filteredLogs.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No alert logs for current filters.",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        items(filteredLogs) { entry ->
            val typeColor = when (entry.type) {
                AlertLogType.APPROACH -> Color(0xFF1565C0)
                AlertLogType.TRACKER -> Color(0xFFB3261E)
                AlertLogType.FOREIGN_SIGNAL -> Color(0xFF6A1B9A)
            }
            val isApproachEntry = entry.type == AlertLogType.APPROACH
            val confidenceLabel = entry.confidence
                ?.let { " • ${String.format(Locale.US, "%.0f%%", it * 100.0)} confidence" }
                .orEmpty()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isApproachEntry) {
                            Modifier.clickable {
                                onOpenApproachMap(entry.source, entry.primaryId)
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${entry.type.name} • ${listSourceLabel(entry.source, null)} • ${entry.primaryId}",
                        color = typeColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(entry.message + confidenceLabel)
                    Text(formatEpoch(entry.timestampEpochMs))
                    if (isApproachEntry) {
                        Text(
                            text = "Tap to open Approach Alert Map",
                            color = Color(0xFF1565C0),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectionMeshNetworkPage(
    encounters: List<Encounter>,
    chainLinkEnabled: Boolean,
    chainNodeId: String,
    chainDeviceName: String,
    chainSharedSecret: String,
    chainAutoSyncEnabled: Boolean,
    chainAutoSyncIntervalSeconds: Long,
    chainPersistentChannelEnabled: Boolean,
    chainHeartbeatIntervalSeconds: Long,
    chainSharePreciseLocationEnabled: Boolean,
    chainMeshSnapshot: ChainMeshSnapshot,
    onChainLinkChanged: (Boolean) -> Unit,
    onChainDeviceNameChanged: (String) -> Unit,
    onChainSharedSecretChanged: (String) -> Unit,
    onChainAutoSyncChanged: (Boolean) -> Unit,
    onChainAutoSyncIntervalChanged: (Long) -> Unit,
    onChainPersistentChannelChanged: (Boolean) -> Unit,
    onChainHeartbeatIntervalChanged: (Long) -> Unit,
    onChainSharePreciseLocationChanged: (Boolean) -> Unit,
    onRefreshPeers: suspend () -> Unit,
    onSendLinkRequest: suspend (host: String, message: String?) -> Boolean,
    onSyncNow: suspend () -> String,
    onWipeMeshData: suspend () -> String
) {
    val context = LocalContext.current
    var chainIntervalExpanded by remember { mutableStateOf(false) }
    var heartbeatExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var syncInProgress by remember { mutableStateOf(false) }
    var wipeInProgress by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var refreshInProgress by remember { mutableStateOf(false) }
    var manualLinkRequestInProgress by remember { mutableStateOf(false) }
    var peerLinkRequestInProgress by remember { mutableStateOf(false) }
    var linkHostInput by remember { mutableStateOf("") }
    var linkMessageInput by remember { mutableStateOf("") }
    var meshServiceActive by remember { mutableStateOf(MeshForegroundServiceController.isActive(context)) }
    var meshWipeGateState by remember { mutableStateOf(ScanSettings.getMeshWipeGateState(context)) }

    LaunchedEffect(chainLinkEnabled, chainPersistentChannelEnabled) {
        while (true) {
            meshServiceActive = MeshForegroundServiceController.isActive(context)
            meshWipeGateState = ScanSettings.getMeshWipeGateState(context)
            delay(1500)
        }
    }

    val connectedCount = chainMeshSnapshot.peers.count { it.state == ChainPeerState.CONNECTED }
    val unconnectedCount = chainMeshSnapshot.peers.count { it.state != ChainPeerState.CONNECTED }
    val meshCoverageInsights = remember(encounters, chainMeshSnapshot, chainNodeId) {
        buildMeshCoverageInsights(
            encounters = encounters,
            snapshot = chainMeshSnapshot,
            localNodeId = chainNodeId
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Mesh Network", style = MaterialTheme.typography.headlineSmall)
                AssistChip(
                    onClick = {
                        MeshForegroundServiceController.ensureState(context)
                        meshServiceActive = MeshForegroundServiceController.isActive(context)
                    },
                    label = {
                        Text(if (meshServiceActive) "FG Mesh Active" else "FG Mesh Inactive")
                    },
                    leadingIcon = {
                        Text("●", color = if (meshServiceActive) Color(0xFF2E7D32) else Color(0xFFB3261E))
                    }
                )
            }
        }
        item {
            ChainMeshVisualizer(
                snapshot = chainMeshSnapshot,
                sharePreciseLocationEnabled = chainSharePreciseLocationEnabled
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Mesh Wipe Coordination", fontWeight = FontWeight.Bold)
                    if (meshWipeGateState.enabled) {
                        val initiator = meshWipeGateState.initiatorDeviceName
                            ?: meshWipeGateState.initiatorNodeId
                            ?: "unknown"
                        Text("Scan gate: ACTIVE")
                        Text("Initiated by: $initiator")
                        if (meshWipeGateState.sessionId != null) {
                            Text("Session: ${meshWipeGateState.sessionId}")
                        }
                    } else {
                        Text("Scan gate: INACTIVE")
                    }

                    if (chainMeshSnapshot.wipeNotices.isEmpty()) {
                        Text("No mesh wipe notices yet.")
                    } else {
                        Text("Recent wipe notices", fontWeight = FontWeight.SemiBold)
                        chainMeshSnapshot.wipeNotices.take(8).forEach { notice ->
                            val who = notice.initiatorDeviceName ?: notice.initiatorNodeId
                            Text("${formatEpoch(notice.timestampEpochMs)} • $who • ${notice.detail}")
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Blind-Spot Fill Insights", fontWeight = FontWeight.Bold)
                    Text("Last 2h across mesh-visible devices. Credits observations to origin node (not relay sender) to reduce sync-forwarding bias.")
                    if (meshCoverageInsights.isEmpty()) {
                        Text("No enough mesh-attributed observations yet. Run sync and collect more detections.")
                    } else {
                        val topSpotter = meshCoverageInsights.maxByOrNull { it.seenDevices }
                        val leastSpotter = meshCoverageInsights.minByOrNull { it.seenDevices }
                        if (topSpotter != null) {
                            Text("Most spotting: ${topSpotter.displayName} (${topSpotter.seenDevices} devices)")
                        }
                        if (leastSpotter != null) {
                            Text("Least spotting: ${leastSpotter.displayName} (${leastSpotter.seenDevices} devices)")
                        }

                        meshCoverageInsights.forEach { insight ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(insight.displayName, fontWeight = FontWeight.SemiBold)
                                    Text("Seen ${insight.seenDevices} (${insight.coverageSharePercent}% mesh coverage) • Unique ${insight.uniqueContributions}")
                                    Text(
                                        "Blind spots filled by others: ${insight.blindSpotsFilledByOthers} (${insight.blindSpotFillPercent}%)",
                                        color = if (insight.blindSpotsFilledByOthers == 0) Color(0xFF2E7D32) else Color(0xFFB3261E)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable chain linking")
                        Switch(
                            checked = chainLinkEnabled,
                            onCheckedChange = onChainLinkChanged
                        )
                    }
                    Text("Node ID: $chainNodeId")
                    OutlinedTextField(
                        value = chainDeviceName,
                        onValueChange = onChainDeviceNameChanged,
                        label = { Text("This Device Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = chainLinkEnabled
                    )
                    Text("Shares and syncs detections with other Argus devices on the same LAN.")
                    OutlinedTextField(
                        value = chainSharedSecret,
                        onValueChange = onChainSharedSecretChanged,
                        label = { Text("Chain Shared Passphrase") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = chainLinkEnabled
                    )
                    Text("Use the exact same passphrase on every linked device.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            enabled = chainLinkEnabled && !refreshInProgress,
                            onClick = {
                                scope.launch {
                                    refreshInProgress = true
                                    runCatching { onRefreshPeers() }
                                    refreshInProgress = false
                                }
                            }
                        ) {
                            Text(if (refreshInProgress) "Refreshing..." else "Refresh Peers")
                        }
                        Text(
                            "Connected $connectedCount | Unconnected $unconnectedCount",
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Auto sync")
                        Switch(
                            checked = chainAutoSyncEnabled,
                            onCheckedChange = onChainAutoSyncChanged,
                            enabled = chainLinkEnabled
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Persistent channel")
                        Switch(
                            checked = chainPersistentChannelEnabled,
                            onCheckedChange = onChainPersistentChannelChanged,
                            enabled = chainLinkEnabled && chainSharedSecret.isNotBlank()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Share Precise Location")
                        Switch(
                            checked = chainSharePreciseLocationEnabled,
                            onCheckedChange = onChainSharePreciseLocationChanged,
                            enabled = chainLinkEnabled
                        )
                    }
                    Text("When enabled, this device shares its current location with linked peers to improve mesh map accuracy.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Heartbeat interval")
                        Button(
                            enabled = chainLinkEnabled && chainPersistentChannelEnabled,
                            onClick = { heartbeatExpanded = true }
                        ) {
                            Text(ScanSettings.formatInterval(chainHeartbeatIntervalSeconds))
                        }
                        DropdownMenu(
                            expanded = heartbeatExpanded,
                            onDismissRequest = { heartbeatExpanded = false }
                        ) {
                            ScanSettings.ALLOWED_CHAIN_HEARTBEAT_INTERVAL_SECONDS.forEach { seconds ->
                                DropdownMenuItem(
                                    text = { Text("Every ${ScanSettings.formatInterval(seconds)}") },
                                    onClick = {
                                        onChainHeartbeatIntervalChanged(seconds)
                                        heartbeatExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Auto sync interval")
                        Button(
                            enabled = chainLinkEnabled && chainAutoSyncEnabled,
                            onClick = { chainIntervalExpanded = true }
                        ) {
                            Text(ScanSettings.formatInterval(chainAutoSyncIntervalSeconds))
                        }
                        DropdownMenu(
                            expanded = chainIntervalExpanded,
                            onDismissRequest = { chainIntervalExpanded = false }
                        ) {
                            ScanSettings.ALLOWED_CHAIN_AUTO_SYNC_INTERVAL_SECONDS.forEach { seconds ->
                                DropdownMenuItem(
                                    text = { Text("Every ${ScanSettings.formatInterval(seconds)}") },
                                    onClick = {
                                        onChainAutoSyncIntervalChanged(seconds)
                                        chainIntervalExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Button(
                        enabled = chainLinkEnabled && chainSharedSecret.isNotBlank() && !syncInProgress,
                        onClick = {
                            scope.launch {
                                syncInProgress = true
                                syncMessage = onSyncNow()
                                syncInProgress = false
                            }
                        }
                    ) {
                        Text(if (syncInProgress) "Syncing..." else "Sync Now")
                    }
                    Button(
                        enabled = !wipeInProgress,
                        onClick = {
                            scope.launch {
                                wipeInProgress = true
                                syncMessage = onWipeMeshData()
                                wipeInProgress = false
                            }
                        }
                    ) {
                        Text(if (wipeInProgress) "Resetting Mesh..." else "Mesh Soft Reset (All Devices)")
                    }
                    Text("Backs up then clears encounters/devices/logs locally and on discovered peers with authenticated coordination to keep two (or more) mesh devices better synchronized.")

                    Text("Send Linking Request", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = linkHostInput,
                        onValueChange = { linkHostInput = it },
                        label = { Text("Peer Host (e.g. 192.168.1.24)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = chainLinkEnabled
                    )
                    OutlinedTextField(
                        value = linkMessageInput,
                        onValueChange = { linkMessageInput = it },
                        label = { Text("Optional message") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = chainLinkEnabled
                    )
                    Button(
                        enabled = chainLinkEnabled && linkHostInput.isNotBlank() && !manualLinkRequestInProgress,
                        onClick = {
                            scope.launch {
                                manualLinkRequestInProgress = true
                                val sent = onSendLinkRequest(linkHostInput.trim(), linkMessageInput.trim().ifBlank { null })
                                syncMessage = if (sent) {
                                    "Link request sent to ${linkHostInput.trim()}"
                                } else {
                                    "Link request failed for ${linkHostInput.trim()}"
                                }
                                manualLinkRequestInProgress = false
                            }
                        }
                    ) {
                        Text(if (manualLinkRequestInProgress) "Sending..." else "Send Link Request")
                    }

                    if (chainMeshSnapshot.peers.isNotEmpty()) {
                        Text("Peers", fontWeight = FontWeight.SemiBold)
                        chainMeshSnapshot.peers.take(24).forEach { peer ->
                            val canRequestPeerLink = peer.state != ChainPeerState.CONNECTED &&
                                peer.state != ChainPeerState.REQUESTED
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val peerDisplay = peer.deviceName?.takeIf { it.isNotBlank() } ?: peer.nodeId
                                    Text("$peerDisplay @ ${peer.host}")
                                    if (!peer.deviceName.isNullOrBlank()) {
                                        Text("Node: ${peer.nodeId}")
                                    }
                                    Text("State: ${peer.state.name}")
                                    Text("Last seen: ${formatEpoch(peer.lastSeenEpochMs)}")
                                    if (isValidLatLon(peer.sharedLocationLat, peer.sharedLocationLon)) {
                                        val lat = peer.sharedLocationLat ?: 0.0
                                        val lon = peer.sharedLocationLon ?: 0.0
                                        Text(
                                            "Shared precise location: ${"%.5f".format(lat)}, ${"%.5f".format(lon)}"
                                        )
                                    } else {
                                        Text("Shared precise location: not provided")
                                    }
                                    if (peer.lastSuccessfulSyncEpochMs != null) {
                                        Text("Last sync: ${formatEpoch(peer.lastSuccessfulSyncEpochMs)}")
                                    }
                                    if (!peer.lastFailure.isNullOrBlank()) {
                                        Text("Failure: ${peer.lastFailure}", color = Color(0xFFB3261E))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            enabled = chainLinkEnabled && canRequestPeerLink && !peerLinkRequestInProgress,
                                            onClick = {
                                                scope.launch {
                                                    peerLinkRequestInProgress = true
                                                    val sent = onSendLinkRequest(peer.host, "Link with $chainNodeId")
                                                    syncMessage = if (sent) {
                                                        "Link request sent to ${peer.host}"
                                                    } else {
                                                        "Link request failed for ${peer.host}"
                                                    }
                                                    peerLinkRequestInProgress = false
                                                }
                                            }
                                        ) {
                                            Text("Link Request")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (chainMeshSnapshot.incomingRequests.isNotEmpty()) {
                        Text("Incoming Link Requests", fontWeight = FontWeight.SemiBold)
                        chainMeshSnapshot.incomingRequests.take(12).forEach { request ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val requesterDisplay = request.requesterDeviceName?.takeIf { it.isNotBlank() } ?: request.requesterNodeId
                                    Text("$requesterDisplay @ ${request.requesterHost}")
                                    if (!request.requesterDeviceName.isNullOrBlank()) {
                                        Text("Node: ${request.requesterNodeId}")
                                    }
                                    if (!request.message.isNullOrBlank()) {
                                        Text("Msg: ${request.message}")
                                    }
                                    Text(formatEpoch(request.timestampEpochMs))
                                }
                            }
                        }
                    }

                    if (syncMessage != null) {
                        Text(syncMessage!!)
                    }
                }
            }
        }
    }
}

private fun buildMeshCoverageInsights(
    encounters: List<Encounter>,
    snapshot: ChainMeshSnapshot,
    localNodeId: String,
    windowMs: Long = 2L * 60L * 60L * 1000L
): List<MeshCoverageNodeInsight> {
    val cutoff = System.currentTimeMillis() - windowMs
    val peersById = snapshot.peers.associateBy { it.nodeId }

    val observationsByNode = linkedMapOf<String, MutableSet<String>>()
    fun nodeSet(nodeId: String): MutableSet<String> = observationsByNode.getOrPut(nodeId) { linkedSetOf() }

    encounters.asSequence()
        .filter { it.timestampEpochMs >= cutoff }
        .forEach { encounter ->
            val originNode = when (encounter.provenance) {
                EncounterProvenance.CHAIN_LINKED -> {
                    encounter.provenanceOriginNodeId
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: parseProvenancePathNodeIds(encounter.provenancePathNodeIds).firstOrNull()
                        ?: encounter.provenanceNodeId
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                        ?: "peer-unknown"
                }

                else -> localNodeId
            }
            val deviceKey = "${encounter.source.name}|${encounter.primaryId}"
            nodeSet(originNode).add(deviceKey)
        }

    if (observationsByNode.isEmpty()) {
        return emptyList()
    }

    if (!observationsByNode.containsKey(localNodeId)) {
        observationsByNode[localNodeId] = linkedSetOf()
    }
    snapshot.peers.forEach { peer ->
        observationsByNode.putIfAbsent(peer.nodeId, linkedSetOf())
    }

    val allObserved = observationsByNode.values.flatten().toSet()
    val allObservedCount = allObserved.size.coerceAtLeast(1)

    return observationsByNode.map { (nodeId, seenSet) ->
        val othersUnion = observationsByNode
            .asSequence()
            .filter { it.key != nodeId }
            .flatMap { it.value.asSequence() }
            .toSet()

        val uniqueContributions = seenSet.count { it !in othersUnion }
        val blindSpotsFilledByOthers = othersUnion.count { it !in seenSet }
        val coverageSharePercent = ((seenSet.size * 100.0) / allObservedCount.toDouble()).toInt()
        val blindSpotFillPercent = ((blindSpotsFilledByOthers * 100.0) / allObservedCount.toDouble()).toInt()
        val displayName = when {
            nodeId == localNodeId -> "${snapshot.localDeviceName} (This Device)"
            nodeId == "peer-unknown" -> "Unknown Peer"
            else -> peersById[nodeId]?.deviceName?.takeIf { it.isNotBlank() } ?: nodeId
        }

        MeshCoverageNodeInsight(
            nodeId = nodeId,
            displayName = displayName,
            seenDevices = seenSet.size,
            uniqueContributions = uniqueContributions,
            blindSpotsFilledByOthers = blindSpotsFilledByOthers,
            coverageSharePercent = coverageSharePercent,
            blindSpotFillPercent = blindSpotFillPercent
        )
    }.sortedWith(
        compareByDescending<MeshCoverageNodeInsight> { it.seenDevices }
            .thenByDescending { it.uniqueContributions }
            .thenBy { it.displayName.lowercase(Locale.US) }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DetectionMapPage(
    mapTitle: String,
    mapDescription: String,
    pins: List<MapPin>,
    pinLimit: Int,
    onPinLimitChange: (Int) -> Unit,
    onPinDetailsClick: (MapPin) -> Unit,
    liveUpdatesAllowed: Boolean = true,
    useSourceOnlyPinColors: Boolean = false,
    showMovingOnlyControl: Boolean = false,
    movingOnlyEnabled: Boolean = false,
    onMovingOnlyEnabledChange: (Boolean) -> Unit = {},
    showSinceSnapshotControl: Boolean = false,
    sinceSnapshotEnabled: Boolean = false,
    snapshotEpochMs: Long? = null,
    onSinceSnapshotEnabledChange: (Boolean) -> Unit = {},
    onCaptureSnapshot: () -> Unit = {},
    onLiveCollect: suspend () -> String,
    liveMapUpdateIntervalSeconds: Long
) {
    val pinLimitOptions = listOf(100, 250, 500, 1000)
    val context = LocalContext.current
    val hasMapsApiKey = remember(context) { hasGoogleMapsApiKey(context) }
    val mapsApiKeyDiagnostic = remember(context) { getMapsApiKeyDiagnostic(context) }
    val playServicesDiagnostic = remember(context) { getPlayServicesDiagnostic(context) }
    val hasNetwork = remember(context) { hasNetworkConnectivity(context) }
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    var pinLimitExpanded by remember { mutableStateOf(false) }
    var diagnosticsVisible by rememberSaveable { mutableStateOf(false) }
    var liveModeEnabled by rememberSaveable { mutableStateOf(true) }
    var liveCollectInProgress by remember { mutableStateOf(false) }
    var liveStatusMessage by remember { mutableStateOf("Live mode is off.") }
    var mapLoaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    val visiblePins = remember(pins, pinLimit) { selectVisiblePinsWithSourceCoverage(pins, pinLimit) }
    val legendItems = remember(visiblePins) { legendItemsForPins(visiblePins) }

    val currentLocation = remember { LocationSnapshotProvider.read(context) }
    val nearbyVisiblePins = remember(visiblePins, currentLocation) {
        filterPinsNearCurrentLocation(
            pins = visiblePins,
            currentLocation = currentLocation,
            maxDistanceMeters = MAP_AUTO_FOCUS_MAX_DISTANCE_METERS
        )
    }
    var autoPositioned by rememberSaveable { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
    }

    LaunchedEffect(currentLocation, visiblePins, nearbyVisiblePins, hasMapsApiKey, autoPositioned) {
        if (!hasMapsApiKey || autoPositioned) return@LaunchedEffect

        val focusPins = if (nearbyVisiblePins.isNotEmpty()) nearbyVisiblePins else visiblePins

        when {
            focusPins.size > 1 && mapLoaded -> {
                val boundsBuilder = LatLngBounds.Builder()
                focusPins.forEach { pin -> boundsBuilder.include(pin.position) }
                val bounds = boundsBuilder.build()
                runCatching {
                    cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                }.onFailure {
                    mapError = "Failed to fit pins in view: ${it.message ?: "unknown error"}"
                }
            }

            focusPins.size == 1 -> {
                runCatching {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(focusPins.first().position, 13f)
                    )
                }.onFailure {
                    mapError = "Failed to center on pin: ${it.message ?: "unknown error"}"
                }
            }

            currentLocation != null -> {
                runCatching {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(currentLocation.lat, currentLocation.lon),
                            13f
                        )
                    )
                }.onFailure {
                    mapError = "Failed to center on current location: ${it.message ?: "unknown error"}"
                }
            }

            else -> {
                runCatching {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(LatLng(0.0, 0.0), 2f)
                    )
                }.onFailure {
                    mapError = "Failed to move camera to fallback location: ${it.message ?: "unknown error"}"
                }
            }
        }

        autoPositioned = true
    }

    LaunchedEffect(hasMapsApiKey, mapLoaded, hasNetwork, playServicesDiagnostic) {
        if (!hasMapsApiKey || mapLoaded) return@LaunchedEffect
        delay(7000)
        if (mapLoaded) return@LaunchedEffect
        if (mapError == null) {
            mapError = buildString {
                append("Map did not finish loading. ")
                append("Check API key restrictions, Maps SDK enablement, billing, Play Services, and network. ")
                append("Play Services: ")
                append(playServicesDiagnostic)
                append(". Network: ")
                append(if (hasNetwork) "available" else "unavailable")
            }
        }
    }

    LaunchedEffect(liveModeEnabled, liveUpdatesAllowed) {
        if (!liveUpdatesAllowed) {
            liveCollectInProgress = false
            liveStatusMessage = "Live updates are available on Device Location Map only."
            return@LaunchedEffect
        }

        if (!liveModeEnabled) {
            liveCollectInProgress = false
            liveStatusMessage = "Live mode is off."
            return@LaunchedEffect
        }

        while (liveModeEnabled) {
            liveCollectInProgress = true
            liveStatusMessage = onLiveCollect()
            liveCollectInProgress = false
            delay(liveMapUpdateIntervalSeconds * 1000L)
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(mapTitle, fontWeight = FontWeight.Bold)
                Text(mapDescription)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                if (liveModeEnabled) {
                    Text(
                        text = "● LIVE",
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text("Panels")
                    Switch(
                        checked = controlsVisible,
                        onCheckedChange = { controlsVisible = it }
                    )
                }
            }
        }
        if (legendItems.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pin Color Legend", fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        legendItems.forEach { item ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("●", color = item.color)
                                Text(item.label)
                            }
                        }
                    }
                }
            }
        }
        if (!hasMapsApiKey) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Map unavailable: Google Maps API key is missing.", fontWeight = FontWeight.Bold)
                    Text("Add com.google.android.geo.API_KEY in AndroidManifest metadata to enable map rendering.")
                }
            }
        } else {
            if (controlsVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Pin Limit", fontWeight = FontWeight.Bold)
                                Button(onClick = { pinLimitExpanded = true }) {
                                    Text("$pinLimit")
                                }
                            }
                            Text("Showing ${visiblePins.size}/${pins.size}")
                            DropdownMenu(expanded = pinLimitExpanded, onDismissRequest = { pinLimitExpanded = false }) {
                                pinLimitOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.toString()) },
                                        onClick = {
                                            onPinLimitChange(option)
                                            pinLimitExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Card(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Live Map Updates", fontWeight = FontWeight.Bold)
                                Switch(
                                    checked = liveModeEnabled && liveUpdatesAllowed,
                                    onCheckedChange = { enabled ->
                                        if (liveUpdatesAllowed) {
                                            liveModeEnabled = enabled
                                        }
                                    },
                                    enabled = liveUpdatesAllowed
                                )
                            }
                            Text("Foreground scan every ${formatLiveMapIntervalLabel(liveMapUpdateIntervalSeconds)} while open.")
                            Text(
                                text = if (liveCollectInProgress) "Live scan running..." else liveStatusMessage,
                                color = if (liveStatusMessage.startsWith("Live scan failed")) Color(0xFFB3261E) else Color.Unspecified
                            )
                            if (showMovingOnlyControl) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("Moving Only")
                                    Switch(
                                        checked = movingOnlyEnabled,
                                        onCheckedChange = onMovingOnlyEnabledChange
                                    )
                                }
                            }
                            if (showSinceSnapshotControl) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("Since Snapshot")
                                    Switch(
                                        checked = sinceSnapshotEnabled,
                                        onCheckedChange = onSinceSnapshotEnabledChange
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = snapshotEpochMs?.let { "Snapshot: ${formatEpoch(it)}" } ?: "Snapshot: not captured"
                                    )
                                    Button(onClick = onCaptureSnapshot) {
                                        Text("Capture")
                                    }
                                }
                            }
                        }
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Map Diagnostics", fontWeight = FontWeight.Bold)
                            Switch(
                                checked = diagnosticsVisible,
                                onCheckedChange = { diagnosticsVisible = it }
                            )
                        }
                        Text(
                            if (diagnosticsVisible) {
                                "Diagnostics are enabled."
                            } else {
                                "Diagnostics are hidden."
                            }
                        )
                        if (diagnosticsVisible) {
                            Text("Loaded: ${if (mapLoaded) "yes" else "no"}")
                            Text("API key: $mapsApiKeyDiagnostic")
                            Text("Play Services: $playServicesDiagnostic")
                            Text("Network: ${if (hasNetwork) "available" else "unavailable"}")
                            Text("Pins rendered: ${visiblePins.size}/${pins.size}")
                            Text(
                                text = if (currentLocation != null) {
                                    "Current location: ${"%.5f".format(currentLocation.lat)}, ${"%.5f".format(currentLocation.lon)}"
                                } else {
                                    "Current location: unavailable"
                                }
                            )
                            if (mapError != null) {
                                Text(
                                    text = "Error: ${mapError}",
                                    color = Color(0xFFB3261E),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    onMapLoaded = {
                        mapLoaded = true
                        autoPositioned = false
                    },
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        zoomGesturesEnabled = true,
                        scrollGesturesEnabled = true,
                        tiltGesturesEnabled = false,
                        rotationGesturesEnabled = false,
                        myLocationButtonEnabled = false
                    )
                ) {
                    visiblePins.forEach { pin ->
                        Marker(
                            state = MarkerState(position = pin.position),
                            title = pin.title,
                            snippet = pin.snippet,
                            icon = markerIconForPin(pin, useSourceOnlyPinColors),
                            onInfoWindowClick = {
                                onPinDetailsClick(pin)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MovingDevicePathMapPage(
    source: String,
    primaryId: String,
    encounters: List<Encounter>,
    onOpenDeviceDetails: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.4219999, -122.0840575), 15f)
    }
    val deviceEncounters = remember(encounters, source, primaryId) {
        encounters
            .filter { it.source.name == source && it.primaryId == primaryId }
            .sortedBy { it.timestampEpochMs }
    }
    val pathPoints = remember(deviceEncounters) {
        deviceEncounters
            .mapNotNull { encounter ->
                if (!isValidLatLon(encounter.lat, encounter.lon)) return@mapNotNull null
                LatLng(encounter.lat!!, encounter.lon!!)
            }
            .fold(mutableListOf<LatLng>()) { acc, point ->
                val previous = acc.lastOrNull()
                if (previous == null || previous.latitude != point.latitude || previous.longitude != point.longitude) {
                    acc += point
                }
                acc
            }
    }
    val latestMotion = remember(deviceEncounters) { analyzeMotionSignal(deviceEncounters) }

    LaunchedEffect(pathPoints) {
        when {
            pathPoints.size > 1 -> {
                val boundsBuilder = LatLngBounds.Builder()
                pathPoints.forEach { point -> boundsBuilder.include(point) }
                runCatching {
                    cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
                }
            }

            pathPoints.size == 1 -> {
                runCatching {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(pathPoints.first(), 15f)
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Moving Device Path", style = MaterialTheme.typography.headlineSmall)
        Text("$source • $primaryId")
        val statusText = latestMotion?.let {
            if (it.isInMotion) {
                "Status: MOVING ${formatSpeedLabel(it.speedMps)} ${formatHeadingCardinal(it.headingDeg)}"
            } else {
                "Status: STATIC ${formatSpeedLabel(it.speedMps)}"
            }
        } ?: "Status: n/a"
        Text(statusText)
        Text("Path points: ${pathPoints.size}")
        Text("Blue path direction: starts at GREEN marker and ends at RED marker.")

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    zoomGesturesEnabled = true,
                    scrollGesturesEnabled = true,
                    tiltGesturesEnabled = false,
                    rotationGesturesEnabled = false,
                    myLocationButtonEnabled = false
                )
            ) {
                if (pathPoints.size >= 2) {
                    Polyline(
                        points = pathPoints,
                        color = Color(0xFF1565C0),
                        width = 8f
                    )
                }

                pathPoints.firstOrNull()?.let { start ->
                    Marker(
                        state = MarkerState(position = start),
                        title = "Path start",
                        snippet = "${formatEpoch(deviceEncounters.firstOrNull()?.timestampEpochMs ?: 0L)}",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }

                pathPoints.lastOrNull()?.let { end ->
                    Marker(
                        state = MarkerState(position = end),
                        title = "Latest device position",
                        snippet = formatEpoch(deviceEncounters.lastOrNull()?.timestampEpochMs ?: 0L),
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onOpenDeviceDetails(source, primaryId) }) {
                Text("Open Device Details")
            }
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

private data class MapPin(
    val position: LatLng,
    val title: String,
    val snippet: String,
    val timestampEpochMs: Long,
    val source: String,
    val primaryId: String,
    val encounterTimestampEpochMs: Long?,
    val motionBadge: String? = null,
    val motionSpeedMps: Double? = null
)

private data class PinLegendItem(
    val label: String,
    val color: Color
)

private fun offsetLatLng(base: LatLng, distanceMeters: Double, bearingDegrees: Double): LatLng {
    val earthRadiusMeters = 6_378_137.0
    val angularDistance = distanceMeters / earthRadiusMeters
    val bearingRad = Math.toRadians(bearingDegrees)
    val lat1 = Math.toRadians(base.latitude)
    val lon1 = Math.toRadians(base.longitude)

    val sinLat1 = sin(lat1)
    val cosLat1 = cos(lat1)
    val sinAngular = sin(angularDistance)
    val cosAngular = cos(angularDistance)

    val lat2 = kotlin.math.asin(
        sinLat1 * cosAngular + cosLat1 * sinAngular * cos(bearingRad)
    )
    val lon2 = lon1 + kotlin.math.atan2(
        sin(bearingRad) * sinAngular * cosLat1,
        cosAngular - sinLat1 * sin(lat2)
    )

    return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

private fun meshPeerColor(state: ChainPeerState): Color = when (state) {
    ChainPeerState.CONNECTED -> Color(0xFF2E7D32)
    ChainPeerState.DISCOVERED -> Color(0xFFF9A825)
    ChainPeerState.REQUESTED -> Color(0xFF1565C0)
    ChainPeerState.FAILED -> Color(0xFFB3261E)
}

private fun meshPeerMarkerHue(state: ChainPeerState): Float = when (state) {
    ChainPeerState.CONNECTED -> BitmapDescriptorFactory.HUE_GREEN
    ChainPeerState.DISCOVERED -> BitmapDescriptorFactory.HUE_YELLOW
    ChainPeerState.REQUESTED -> BitmapDescriptorFactory.HUE_AZURE
    ChainPeerState.FAILED -> BitmapDescriptorFactory.HUE_RED
}

private fun selectVisiblePinsWithSourceCoverage(pins: List<MapPin>, pinLimit: Int): List<MapPin> {
    if (pins.isEmpty()) return emptyList()
    val safeLimit = pinLimit.coerceAtLeast(1)
    if (pins.size <= safeLimit) return pins

    // Preserve one newest pin per source so map/legend do not silently drop source types.
    val newestPerSource = pins
        .groupBy { it.source }
        .values
        .mapNotNull { grouped -> grouped.maxByOrNull { it.timestampEpochMs } }
        .sortedByDescending { it.timestampEpochMs }

    if (newestPerSource.size >= safeLimit) {
        return newestPerSource.take(safeLimit)
    }

    val selected = LinkedHashMap<String, MapPin>()
    newestPerSource.forEach { pin ->
        selected["${pin.source}|${pin.primaryId}|${pin.timestampEpochMs}"] = pin
    }

    pins.forEach { pin ->
        if (selected.size >= safeLimit) return@forEach
        val key = "${pin.source}|${pin.primaryId}|${pin.timestampEpochMs}"
        if (!selected.containsKey(key)) {
            selected[key] = pin
        }
    }

    return selected.values
        .sortedByDescending { it.timestampEpochMs }
        .take(safeLimit)
}

private fun spreadOverlappingMapPins(pins: List<MapPin>): List<MapPin> {
    if (pins.size < 2) return pins

    val grouped = pins.groupBy { pin ->
        val latKey = (pin.position.latitude * 100_000.0).toLong()
        val lonKey = (pin.position.longitude * 100_000.0).toLong()
        "$latKey:$lonKey"
    }

    val adjusted = mutableListOf<MapPin>()
    grouped.values.forEach { group ->
        if (group.size <= 1) {
            adjusted += group
            return@forEach
        }

        group.forEachIndexed { index, pin ->
            val jitterMeters = (0.6 + ((index / 10) * 0.25)).coerceAtMost(1.8)
            val angle = (2.0 * Math.PI * index.toDouble()) / group.size.toDouble()
            val baseLat = pin.position.latitude
            val baseLon = pin.position.longitude
            val latRad = Math.toRadians(baseLat)
            val metersPerDegLat = 111_132.0
            val metersPerDegLon = (111_320.0 * cos(latRad)).coerceAtLeast(1.0)
            val dLat = (jitterMeters * cos(angle)) / metersPerDegLat
            val dLon = (jitterMeters * sin(angle)) / metersPerDegLon
            val adjustedLat = baseLat + dLat
            val adjustedLon = baseLon + dLon

            adjusted += pin.copy(
                position = LatLng(adjustedLat, adjustedLon)
            )
        }
    }

    return adjusted.sortedByDescending { it.timestampEpochMs }
}

private fun markerHueForSource(source: String): Float = when (source) {
    "CELL" -> BitmapDescriptorFactory.HUE_AZURE
    "WIFI" -> BitmapDescriptorFactory.HUE_ORANGE
    "WIFI_DIRECT" -> BitmapDescriptorFactory.HUE_YELLOW
    "BLUETOOTH_LE" -> BitmapDescriptorFactory.HUE_GREEN
    "BLUETOOTH_CLASSIC" -> BitmapDescriptorFactory.HUE_CYAN
    "REMOTE_ID" -> BitmapDescriptorFactory.HUE_VIOLET
    "UWB" -> BitmapDescriptorFactory.HUE_MAGENTA
    "SDR" -> BitmapDescriptorFactory.HUE_RED
    "UNKNOWN_RF" -> BitmapDescriptorFactory.HUE_ROSE
    else -> BitmapDescriptorFactory.HUE_RED
}

private fun markerLegendColorForSource(source: String): Color = when (source) {
    "CELL" -> Color(0xFF1E88E5)
    "WIFI" -> Color(0xFFFB8C00)
    "WIFI_DIRECT" -> Color(0xFFFBC02D)
    "BLUETOOTH_LE" -> Color(0xFF43A047)
    "BLUETOOTH_CLASSIC" -> Color(0xFF00ACC1)
    "REMOTE_ID" -> Color(0xFF8E24AA)
    "UWB" -> Color(0xFFAB47BC)
    "SDR" -> Color(0xFFE53935)
    "UNKNOWN_RF" -> Color(0xFFE91E63)
    else -> Color(0xFFD32F2F)
}

private fun markerLegendLabelForSource(source: String): String = when (source) {
    "CELL" -> "CELL TOWER"
    "WIFI" -> "WIFI"
    "WIFI_DIRECT" -> "WIFI DIRECT"
    "BLUETOOTH_LE" -> "BLUETOOTH LE"
    "BLUETOOTH_CLASSIC" -> "BLUETOOTH CLASSIC"
    "REMOTE_ID" -> "REMOTE ID"
    "UWB" -> "UWB"
    "SDR" -> "SDR"
    "UNKNOWN_RF" -> "UNKNOWN RF"
    else -> source
}

private fun legendItemsForPins(pins: List<MapPin>): List<PinLegendItem> {
    val preferredOrder = listOf(
        "CELL",
        "WIFI",
        "WIFI_DIRECT",
        "BLUETOOTH_LE",
        "BLUETOOTH_CLASSIC",
        "REMOTE_ID",
        "UWB",
        "SDR",
        "UNKNOWN_RF"
    )
    val sourcesInPins = pins.map { it.source }.toSet()
    val orderedSources = preferredOrder.filter { it in sourcesInPins } +
        sourcesInPins.filterNot { it in preferredOrder }.sorted()

    return orderedSources.map { source ->
        PinLegendItem(
            label = markerLegendLabelForSource(source),
            color = markerLegendColorForSource(source)
        )
    }
}

private fun buildPinTitle(sourceLabel: String, primaryId: String, motionBadge: String?): String {
    val shortId = if (primaryId.length <= 18) primaryId else primaryId.take(15) + "..."
    val badge = when (motionBadge) {
        "MOVING" -> "[M] "
        "STATIC" -> "[S] "
        else -> ""
    }
    return "$badge$sourceLabel • $shortId"
}

private fun buildThreeLineSnippet(line1: String, line2: String, line3: String): String {
    fun cap(text: String, max: Int): String {
        val compact = text.trim().replace("\n", " ").replace(Regex("\\s+"), " ")
        return if (compact.length <= max) compact else compact.take(max - 3).trimEnd() + "..."
    }
    val segments = listOf(line1, line2, line3)
        .map { cap(it, 54) }
        .filter { it.isNotBlank() }
    return cap(segments.joinToString(" | "), 180)
}

private fun markerHueForPin(pin: MapPin, useSourceOnlyPinColors: Boolean = false): Float {
    if (useSourceOnlyPinColors) {
        return markerHueForSource(pin.source)
    }
    return when (pin.motionBadge) {
        "MOVING" -> BitmapDescriptorFactory.HUE_GREEN
        else -> markerHueForSource(pin.source)
    }
}

private val deviceMarkerIconCache = mutableMapOf<String, BitmapDescriptor>()

private fun markerIconForPin(pin: MapPin, useSourceOnlyPinColors: Boolean = false): BitmapDescriptor {
    val glyph = deviceGlyphForSource(pin.source)
    val bgColor = markerBackgroundColorForPin(pin, useSourceOnlyPinColors)
    val key = "${pin.source}|${glyph}|${bgColor.toArgb()}"
    deviceMarkerIconCache[key]?.let { return it }

    val width = when {
        glyph.length > 9 -> 148
        glyph.length > 6 -> 116
        else -> 92
    }
    val height = 42
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = bgColor.toArgb()
    }
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
        color = android.graphics.Color.argb(255, 16, 33, 34)
    }
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = android.graphics.Color.argb(255, 15, 21, 22)
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = if (glyph.length > 9) 14f else if (glyph.length > 6) 16f else 18f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    }

    val rect = android.graphics.RectF(2f, 2f, width - 2f, height - 10f)
    canvas.drawRoundRect(rect, 18f, 18f, fillPaint)
    canvas.drawRoundRect(rect, 18f, 18f, strokePaint)

    val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(glyph, rect.centerX(), textY, textPaint)

    val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
    deviceMarkerIconCache[key] = descriptor
    return descriptor
}

private fun markerBackgroundColorForPin(pin: MapPin, useSourceOnlyPinColors: Boolean): Color {
    if (!useSourceOnlyPinColors && pin.motionBadge == "MOVING") {
        return Color(0xFF43A047)
    }

    return markerLegendColorForSource(pin.source)
}

private fun deviceGlyphForSource(source: String): String = when (source) {
    "CELL" -> "CELL"
    "WIFI" -> "WIFI"
    "WIFI_DIRECT" -> "WFD"
    "BLUETOOTH_LE" -> "BLE"
    "BLUETOOTH_CLASSIC" -> "BT"
    "REMOTE_ID" -> "RID / Drone"
    "UWB" -> "UWB"
    "SDR" -> "SDR"
    "UNKNOWN_RF" -> "RF"
    else -> "RF"
}

private fun formatLiveMapIntervalLabel(seconds: Long): String = when (seconds) {
    1L -> "1s"
    3L -> "3s"
    5L -> "5s"
    15L -> "15s"
    30L -> "30s"
    60L -> "1 minute"
    300L -> "5 minutes"
    1800L -> "30 minutes"
    3600L -> "hourly"
    else -> ScanSettings.formatInterval(seconds)
}

private fun formatScanDuration(durationMs: Long): String {
    val safe = durationMs.coerceAtLeast(0L)
    return if (safe < 1000L) {
        "${safe}ms"
    } else {
        String.format(Locale.US, "%.3fs", safe / 1000.0)
    }
}

private fun formatSourceTypeLabel(sourceType: String): String = when (sourceType) {
    "wifi" -> "Wi-Fi"
    "wifi_direct" -> "Wi-Fi Direct"
    "ble" -> "Bluetooth LE"
    "bt_classic" -> "Bluetooth Classic"
    "cellular" -> "Cellular"
    "remote_id" -> "Remote ID"
    "uwb" -> "UWB"
    "sdr" -> "SDR"
    "acoustic" -> "Acoustic"
    "magnetic" -> "Magnetometer"
    else -> sourceType.replace('_', ' ').uppercase()
}

private fun suggestSafeIntervalSeconds(referenceDurationMs: Long): Long {
    val targetMs = (referenceDurationMs.coerceAtLeast(0L) * 1.25).toLong().coerceAtLeast(1000L)
    val targetSeconds = (targetMs + 999L) / 1000L
    return targetSeconds.coerceIn(
        ScanSettings.MIN_SOURCE_SCAN_INTERVAL_SECONDS,
        ScanSettings.MAX_SOURCE_SCAN_INTERVAL_SECONDS
    )
}

private fun computeRecommendedIntervalSeconds(
    timings: List<ScanSettings.SourceScanTiming>,
    sensorGateSettings: SensorGateSettings
): Long {
    val enabledTypes = buildSet {
        if (sensorGateSettings.wifiEnabled) {
            add("wifi")
            add("wifi_direct")
        }
        if (sensorGateSettings.bluetoothEnabled) {
            add("ble")
            add("bt_classic")
        }
        if (sensorGateSettings.cellularEnabled) add("cellular")
        if (sensorGateSettings.remoteIdEnabled) add("remote_id")
        if (sensorGateSettings.uwbEnabled) add("uwb")
        if (sensorGateSettings.sdrEnabled) add("sdr")
        if (sensorGateSettings.directAcousticEnabled) add("acoustic")
        if (sensorGateSettings.directMagneticEnabled) add("magnetic")
    }
    val suggested = timings
        .filter { it.sourceType in enabledTypes }
        .map { suggestSafeIntervalSeconds(it.p95DurationMs) }
        .maxOrNull()
    return suggested ?: ScanSettings.DEFAULT_SCAN_INTERVAL_SECONDS
}

private fun nextLowerInterval(currentIntervalSeconds: Long): Long {
    val options = ScanSettings.ALLOWED_INTERVALS_SECONDS
    val idx = options.indexOf(currentIntervalSeconds)
    if (idx <= 0) return options.first()
    return options[idx - 1]
}

private fun enabledSourceTypes(sensorGateSettings: SensorGateSettings): List<String> = buildList {
    if (sensorGateSettings.wifiEnabled) {
        add("wifi")
        add("wifi_direct")
    }
    if (sensorGateSettings.bluetoothEnabled) {
        add("ble")
        add("bt_classic")
    }
    if (sensorGateSettings.cellularEnabled) add("cellular")
    if (sensorGateSettings.remoteIdEnabled) add("remote_id")
    if (sensorGateSettings.uwbEnabled) add("uwb")
    if (sensorGateSettings.sdrEnabled) add("sdr")
    if (sensorGateSettings.directAcousticEnabled) add("acoustic")
    if (sensorGateSettings.directMagneticEnabled) add("magnetic")
}

private fun autoMarkConnectedWifiAsOwned(
    context: android.content.Context,
    encounters: List<Encounter>,
    ownedDeviceKeys: Set<String>
): Set<String> {
    val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return ownedDeviceKeys
    val activeNetwork = connectivityManager.activeNetwork ?: return ownedDeviceKeys
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return ownedDeviceKeys
    if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return ownedDeviceKeys

    val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? WifiManager
        ?: return ownedDeviceKeys
    val wifiInfo = runCatching { wifiManager.connectionInfo }.getOrNull() ?: return ownedDeviceKeys

    val connectedBssid = wifiInfo.bssid
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("02:00:00:00:00:00", ignoreCase = true) }
    val connectedSsid = normalizeWifiSsid(wifiInfo.ssid)

    val matchedKeys = encounters
        .asSequence()
        .filter { it.source == EncounterSource.WIFI }
        .mapNotNull { encounter ->
            val primaryId = encounter.primaryId.trim()
            if (primaryId.isBlank() || primaryId == "unknown-bssid") return@mapNotNull null

            val bssidMatch = connectedBssid != null && primaryId.equals(connectedBssid, ignoreCase = true)
            val encounterSsid = normalizeWifiSsid(encounter.secondaryId)
            val ssidMatch = connectedSsid.isNotBlank() && encounterSsid.isNotBlank() &&
                encounterSsid.equals(connectedSsid, ignoreCase = true)
            if (!bssidMatch && !ssidMatch) return@mapNotNull null

            OwnedDeviceRegistry.keyFor("WIFI", encounter.primaryId)
        }
        .toSet()

    if (matchedKeys.isEmpty()) return ownedDeviceKeys

    var changed = false
    val updated = ownedDeviceKeys.toMutableSet()
    matchedKeys.forEach { key ->
        if (updated.add(key)) {
            val primaryId = key.substringAfter("WIFI|", "")
            if (primaryId.isNotBlank()) {
                OwnedDeviceRegistry.setOwned(context, "WIFI", primaryId, true)
                changed = true
            }
        }
    }
    return if (changed) updated else ownedDeviceKeys
}

private fun normalizeWifiSsid(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isBlank()) return ""
    if (trimmed.equals("<unknown ssid>", ignoreCase = true)) return ""
    return trimmed.removePrefix("\"").removeSuffix("\"").trim()
}

private fun formatIntervalChangeReason(reason: String): String = when (reason) {
    "manual" -> "Manual change"
    "auto-bootstrap" -> "Auto-adjust startup alignment"
    "auto-overrun" -> "Auto-adjust overrun protection"
    "auto-stable" -> "Auto-adjust stable downshift"
    "scheduler-align" -> "Scheduler alignment to source intervals"
    "manual-wifi" -> "Manual Wi-Fi interval change"
    "manual-ble" -> "Manual Bluetooth LE interval change"
    "manual-bt_classic" -> "Manual Bluetooth Classic interval change"
    "manual-cellular" -> "Manual Cellular interval change"
    "manual-remote_id" -> "Manual Remote ID interval change"
    "manual-wifi_direct" -> "Manual Wi-Fi Direct interval change"
    "manual-uwb" -> "Manual UWB interval change"
    "manual-sdr" -> "Manual SDR interval change"
    "manual-acoustic" -> "Manual acoustic interval change"
    "manual-magnetic" -> "Manual magnetometer interval change"
    "auto-overrun-wifi" -> "Auto-adjust Wi-Fi overrun protection"
    "auto-overrun-wifi_direct" -> "Auto-adjust Wi-Fi Direct overrun protection"
    "auto-overrun-ble" -> "Auto-adjust Bluetooth LE overrun protection"
    "auto-overrun-bt_classic" -> "Auto-adjust Bluetooth Classic overrun protection"
    "auto-overrun-cellular" -> "Auto-adjust Cellular overrun protection"
    "auto-overrun-remote_id" -> "Auto-adjust Remote ID overrun protection"
    "auto-overrun-uwb" -> "Auto-adjust UWB overrun protection"
    "auto-overrun-sdr" -> "Auto-adjust SDR overrun protection"
    "auto-overrun-acoustic" -> "Auto-adjust acoustic overrun protection"
    "auto-overrun-magnetic" -> "Auto-adjust magnetometer overrun protection"
    "auto-stable-wifi" -> "Auto-adjust Wi-Fi stable downshift"
    "auto-stable-wifi_direct" -> "Auto-adjust Wi-Fi Direct stable downshift"
    "auto-stable-ble" -> "Auto-adjust Bluetooth LE stable downshift"
    "auto-stable-bt_classic" -> "Auto-adjust Bluetooth Classic stable downshift"
    "auto-stable-cellular" -> "Auto-adjust Cellular stable downshift"
    "auto-stable-remote_id" -> "Auto-adjust Remote ID stable downshift"
    "auto-stable-uwb" -> "Auto-adjust UWB stable downshift"
    "auto-stable-sdr" -> "Auto-adjust SDR stable downshift"
    "auto-stable-acoustic" -> "Auto-adjust acoustic stable downshift"
    "auto-stable-magnetic" -> "Auto-adjust magnetometer stable downshift"
    else -> reason.replace('-', ' ').replace('_', ' ')
}

private fun isValidLatLon(lat: Double?, lon: Double?): Boolean {
    if (lat == null || lon == null) return false
    if (!lat.isFinite() || !lon.isFinite()) return false
    return lat in -90.0..90.0 && lon in -180.0..180.0
}

private fun hasGoogleMapsApiKey(context: android.content.Context): Boolean {
    val appInfo = runCatching {
        context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
    }.getOrNull() ?: return false

    val value = appInfo.metaData?.getString("com.google.android.geo.API_KEY")?.trim().orEmpty()
    return value.isNotEmpty()
}

private fun getMapsApiKeyDiagnostic(context: android.content.Context): String {
    val appInfo = runCatching {
        context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
    }.getOrNull() ?: return "metadata unavailable"

    val value = appInfo.metaData?.getString("com.google.android.geo.API_KEY")?.trim().orEmpty()
    if (value.isEmpty()) return "missing"
    if (value.contains("MAPS_API_KEY")) return "placeholder unresolved"
    return "present (${value.length} chars)"
}

private fun getPlayServicesDiagnostic(context: android.content.Context): String {
    val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    return when (code) {
        ConnectionResult.SUCCESS -> "available"
        ConnectionResult.SERVICE_MISSING -> "missing"
        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "update required"
        ConnectionResult.SERVICE_DISABLED -> "disabled"
        ConnectionResult.SERVICE_INVALID -> "invalid"
        else -> "error code $code"
    }
}

private fun supportsSecondaryIdInList(source: String): Boolean =
    source == "WIFI" ||
        source == "WIFI_DIRECT" ||
        source == "BLUETOOTH_LE" ||
        source == "BLUETOOTH_CLASSIC"

private fun secondaryIdLabel(source: String): String = when (source) {
    "WIFI" -> "SSID"
    "WIFI_DIRECT" -> "Peer Name"
    "BLUETOOTH_LE" -> "Device Name"
    "BLUETOOTH_CLASSIC" -> "Device Name"
    else -> "Secondary ID"
}

private fun listSourceLabel(source: String, secondaryId: String?): String {
    if (source == "CELL") {
        if (!secondaryId.isNullOrBlank()) {
            return "CELL TOWER (${secondaryId})"
        }
        return "CELL TOWER"
    }
    if (source == "WIFI_DIRECT") return "WIFI DIRECT"
    if (source == "BLUETOOTH_CLASSIC") return "BLUETOOTH CLASSIC"
    return source
}

private fun provenanceLabel(provenance: EncounterProvenance, nodeId: String?): String = when (provenance) {
    EncounterProvenance.LOCAL -> "Local device"
    EncounterProvenance.CHAIN_LINKED -> {
        if (nodeId.isNullOrBlank()) "Chain-linked peer" else "Chain-linked peer ($nodeId)"
    }
}

private fun provenanceBadge(provenance: EncounterProvenance, nodeId: String?): String = when (provenance) {
    EncounterProvenance.LOCAL -> ""
    EncounterProvenance.CHAIN_LINKED -> {
        if (nodeId.isNullOrBlank()) " • CHAIN" else " • CHAIN:$nodeId"
    }
}

private fun readJsonFields(
    payload: JSONObject,
    definitions: List<Pair<String, String>>
): List<Pair<String, String>> {
    val fields = mutableListOf<Pair<String, String>>()
    definitions.forEach { (label, key) ->
        val value = payload.opt(key)
        if (value != null && value != JSONObject.NULL) {
            fields += label to value.toString()
        }
    }
    return fields
}

private fun readWifiAccessPointFields(rawPayloadJson: String): List<Pair<String, String>> {
    val payload = runCatching { JSONObject(rawPayloadJson) }.getOrNull() ?: return emptyList()
    return readJsonFields(
        payload,
        listOf(
            "SSID" to "ssid",
            "BSSID" to "bssid",
            "Capabilities" to "capabilities",
            "Wi-Fi Standard" to "wifiStandard",
            "Channel Width" to "channelWidth",
            "Center Freq 0" to "centerFreq0",
            "Center Freq 1" to "centerFreq1",
            "Passpoint" to "isPasspoint",
            "802.11mc Responder" to "is80211mcResponder",
            "Operator Friendly Name" to "operatorFriendlyName",
            "Venue Name" to "venueName",
            "Scan Request Accepted" to "scanRequestAccepted",
            "Scan Timestamp (us)" to "timestampMicros"
        )
    )
}

private fun readBleDeviceFields(rawPayloadJson: String): List<Pair<String, String>> {
    val payload = runCatching { JSONObject(rawPayloadJson) }.getOrNull() ?: return emptyList()
    return readJsonFields(
        payload,
        listOf(
            "Address" to "address",
            "Device Name" to "name",
            "RSSI" to "rssi",
            "Tx Power" to "txPower",
            "Primary PHY" to "primaryPhy",
            "Secondary PHY" to "secondaryPhy",
            "Legacy" to "isLegacy",
            "Connectable" to "isConnectable",
            "Advertising Interval" to "periodicAdvertisingInterval",
            "Advertise Flags" to "advertiseFlags",
            "Record Tx Power" to "txPowerLevel",
            "Service UUIDs" to "serviceUuids",
            "Manufacturer Data Count" to "manufacturerSpecificDataSize",
            "Service Data Count" to "serviceDataSize"
        )
    )
}

private fun readGenericPayloadFields(rawPayloadJson: String): List<Pair<String, String>> {
    val payload = runCatching { JSONObject(rawPayloadJson) }.getOrNull() ?: return emptyList()
    return payload.keys()
        .asSequence()
        .toList()
        .sorted()
        .take(20)
        .mapNotNull { key ->
            val value = payload.opt(key)
            if (value == null || value == JSONObject.NULL) null else key to value.toString()
        }
}

private fun readDirectAcousticFields(rawPayloadJson: String): List<Pair<String, String>> {
    val payload = runCatching { JSONObject(rawPayloadJson) }.getOrNull() ?: return emptyList()
    val fields = mutableListOf<Pair<String, String>>()

    val sampleRateHz = payload.optInt("sampleRateHz", -1).takeIf { it > 0 }
    val sampleCount = payload.optInt("sampleCount", -1).takeIf { it > 0 }
    val rmsDbFs = payload.optDouble("rmsDbFs", Double.NaN).takeIf { it.isFinite() }
    val peakDbFs = payload.optDouble("peakDbFs", Double.NaN).takeIf { it.isFinite() }

    sampleRateHz?.let { fields += "Sample Rate" to "$it Hz" }
    sampleCount?.let { fields += "Sample Count" to it.toString() }
    rmsDbFs?.let { fields += "RMS Level" to String.format(Locale.US, "%.1f dBFS", it) }
    peakDbFs?.let { fields += "Peak Level" to String.format(Locale.US, "%.1f dBFS", it) }

    return if (fields.isNotEmpty()) fields else readGenericPayloadFields(rawPayloadJson)
}

private fun readDirectMagneticFields(rawPayloadJson: String): List<Pair<String, String>> {
    val payload = runCatching { JSONObject(rawPayloadJson) }.getOrNull() ?: return emptyList()
    val fields = mutableListOf<Pair<String, String>>()

    fun appendMicroTesla(label: String, key: String) {
        val value = payload.optDouble(key, Double.NaN)
        if (value.isFinite()) {
            fields += label to String.format(Locale.US, "%.2f uT", value)
        }
    }

    appendMicroTesla("X", "xMicroTesla")
    appendMicroTesla("Y", "yMicroTesla")
    appendMicroTesla("Z", "zMicroTesla")
    appendMicroTesla("Magnitude", "magnitudeMicroTesla")
    appendMicroTesla("Delta from Earth Baseline", "deltaFromEarthBaselineMicroTesla")

    val accuracy = payload.optInt("accuracy", Int.MIN_VALUE)
    if (accuracy != Int.MIN_VALUE) {
        fields += "Sensor Accuracy" to accuracy.toString()
    }

    return if (fields.isNotEmpty()) fields else readGenericPayloadFields(rawPayloadJson)
}

private fun readUnknownRfFields(rawPayloadJson: String): Pair<String, List<Pair<String, String>>> {
    val payload = runCatching { JSONObject(rawPayloadJson) }.getOrNull()
    val signalChannel = payload?.optString("signalChannel", "")?.trim()?.lowercase(Locale.US).orEmpty()
    val isDirect = payload?.optBoolean("directChannel", false) == true

    return when {
        isDirect && signalChannel == "acoustic" -> {
            "Direct Acoustic Signal Details" to readDirectAcousticFields(rawPayloadJson)
        }

        isDirect && signalChannel == "magnetic" -> {
            "Direct Magnetometer Signal Details" to readDirectMagneticFields(rawPayloadJson)
        }

        else -> {
            "Unknown RF Details" to readGenericPayloadFields(rawPayloadJson)
        }
    }
}

private fun readRemoteIdFields(rawPayloadJson: String): List<Pair<String, String>> {
    val payload = runCatching { JSONObject(rawPayloadJson) }.getOrNull() ?: return emptyList()
    val normalized = RemoteIdPayloadParser.normalizeIncomingPayload(payload)
    val decoded = normalized.decoded

    val semantic = mutableListOf<Pair<String, String>>()
    semantic += "UAS ID" to normalized.primaryId
    normalized.secondaryId?.let { semantic += "Operator ID" to it }
    decoded?.let {
        semantic += "Message Type" to it.messageType
        semantic += "Parse Confidence" to it.parseConfidence.name
        it.droneLat?.let { value -> semantic += "Drone Lat" to formatCoordinate(value) }
        it.droneLon?.let { value -> semantic += "Drone Lon" to formatCoordinate(value) }
        it.operatorLat?.let { value -> semantic += "Operator Lat" to formatCoordinate(value) }
        it.operatorLon?.let { value -> semantic += "Operator Lon" to formatCoordinate(value) }
        it.altitudeMeters?.let { value -> semantic += "Aircraft Altitude" to String.format(Locale.US, "%.1f m", value) }
        it.speedMetersPerSecond?.let { value -> semantic += "Speed" to String.format(Locale.US, "%.1f m/s", value) }
        it.headingDegrees?.let { value -> semantic += "Heading" to String.format(Locale.US, "%.0f deg", value) }
        it.emergencyStatus?.takeIf { value -> value.isNotBlank() }?.let { value ->
            semantic += "Emergency" to value
        }
    }

    if (semantic.isNotEmpty()) {
        return semantic
    }

    return readGenericPayloadFields(rawPayloadJson)
}

private fun formatCoordinate(value: Double): String =
    String.format(Locale.US, "%.6f", value)

private fun sourceSpecificDetails(encounter: Encounter): Pair<String, List<Pair<String, String>>> =
    when (encounter.source) {
        EncounterSource.WIFI -> "Wi-Fi Access Point Details" to readWifiAccessPointFields(encounter.rawPayloadJson)
        EncounterSource.WIFI_DIRECT -> "Wi-Fi Direct Peer Details" to readGenericPayloadFields(encounter.rawPayloadJson)
        EncounterSource.BLUETOOTH_LE -> "Bluetooth LE Device Details" to readBleDeviceFields(encounter.rawPayloadJson)
        EncounterSource.BLUETOOTH_CLASSIC -> "Bluetooth Classic Device Details" to readGenericPayloadFields(encounter.rawPayloadJson)
        EncounterSource.CELL -> "Cell Tower Details" to readCellTowerFields(encounter.rawPayloadJson)
        EncounterSource.REMOTE_ID -> "Remote ID Details" to readRemoteIdFields(encounter.rawPayloadJson)
        EncounterSource.UWB -> "UWB Device Details" to readGenericPayloadFields(encounter.rawPayloadJson)
        EncounterSource.SDR -> "SDR Device Details" to readGenericPayloadFields(encounter.rawPayloadJson)
        EncounterSource.UNKNOWN_RF -> readUnknownRfFields(encounter.rawPayloadJson)
    }

@Composable
private fun ProvenanceGraphSection(
    provenance: EncounterProvenance,
    provenanceNodeId: String?,
    provenanceOriginNodeId: String?,
    provenancePathNodeIds: String?,
    provenanceReceivedAtEpochMs: Long?,
    provenanceHopCount: Int,
    localNodeId: String
) {
    val chainNodes = remember(
        provenance,
        provenanceNodeId,
        provenanceOriginNodeId,
        provenancePathNodeIds,
        localNodeId
    ) {
        buildProvenanceNodeSequence(
            provenance = provenance,
            provenanceNodeId = provenanceNodeId,
            provenanceOriginNodeId = provenanceOriginNodeId,
            provenancePathNodeIds = provenancePathNodeIds,
            localNodeId = localNodeId
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Provenance Graph", fontWeight = FontWeight.SemiBold)
            if (provenance == EncounterProvenance.LOCAL) {
                Text("Local capture on this device.")
                Text("Node: $localNodeId")
                return@Column
            }

            Text("Hop Count: ${provenanceHopCount.coerceAtLeast(chainNodes.size - 1)}")
            if (provenanceReceivedAtEpochMs != null) {
                Text("Received via mesh: ${formatEpoch(provenanceReceivedAtEpochMs)}")
            }
            chainNodes.forEachIndexed { index, nodeId ->
                val label = when (index) {
                    0 -> "Origin"
                    chainNodes.lastIndex -> "This Device"
                    else -> "Relay ${index}"
                }
                Text("$label: $nodeId")
                if (index < chainNodes.lastIndex) {
                    Text("  |"); Text("  v")
                }
            }
        }
    }
}

private fun buildProvenanceNodeSequence(
    provenance: EncounterProvenance,
    provenanceNodeId: String?,
    provenanceOriginNodeId: String?,
    provenancePathNodeIds: String?,
    localNodeId: String
): List<String> {
    if (provenance == EncounterProvenance.LOCAL) {
        return listOf(localNodeId)
    }

    val nodes = parseProvenancePathNodeIds(provenancePathNodeIds).toMutableList()
    val origin = provenanceOriginNodeId?.trim().takeUnless { it.isNullOrBlank() }
        ?: provenanceNodeId?.trim().takeUnless { it.isNullOrBlank() }
        ?: "unknown-origin"

    if (nodes.isEmpty()) {
        nodes += origin
    }
    if (nodes.lastOrNull() != provenanceNodeId && !provenanceNodeId.isNullOrBlank()) {
        nodes += provenanceNodeId
    }
    if (nodes.lastOrNull() != localNodeId) {
        nodes += localNodeId
    }

    return nodes
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .fold(mutableListOf<String>()) { acc, node ->
            if (acc.lastOrNull() != node) acc += node
            acc
        }
}

private fun parseProvenancePathNodeIds(raw: String?): List<String> =
    raw
        ?.split("|")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

private fun readCellTowerFields(rawPayloadJson: String): List<Pair<String, String>> {
    val payload = runCatching { JSONObject(rawPayloadJson) }.getOrNull() ?: return emptyList()
    val fields = mutableListOf<Pair<String, String>>()

    val radio = payload.optString("radio", "")
    if (radio.isNotBlank()) fields += "Radio" to radio
    val operatorName = payload.optString("networkOperatorName", "")
    if (operatorName.isNotBlank()) fields += "Operator" to operatorName
    val operatorCode = payload.optString("networkOperator", "")
    if (operatorCode.isNotBlank()) fields += "Operator Code" to operatorCode

    fun addIfPresent(label: String, key: String) {
        val value = payload.opt(key)
        if (value != null && value != JSONObject.NULL) {
            fields += label to value.toString()
        }
    }

    addIfPresent("MCC", "mcc")
    addIfPresent("MNC", "mnc")

    when (radio.uppercase()) {
        "LTE" -> {
            addIfPresent("Cell ID (CI)", "ci")
            addIfPresent("Tracking Area Code (TAC)", "tac")
            addIfPresent("Physical Cell ID (PCI)", "pci")
            addIfPresent("EARFCN", "earfcn")
            addIfPresent("Bandwidth", "bandwidth")
        }

        "NR" -> {
            addIfPresent("NR Cell ID (NCI)", "nci")
            addIfPresent("Tracking Area Code (TAC)", "tac")
            addIfPresent("Physical Cell ID (PCI)", "pci")
            addIfPresent("NRARFCN", "nrarfcn")
            addIfPresent("SS RSRP", "ssRsrp")
            addIfPresent("CSI RSRP", "csiRsrp")
        }

        "WCDMA" -> {
            addIfPresent("Cell ID (CID)", "cid")
            addIfPresent("Location Area Code (LAC)", "lac")
            addIfPresent("Primary Scrambling Code (PSC)", "psc")
            addIfPresent("UARFCN", "uarfcn")
        }

        "GSM" -> {
            addIfPresent("Cell ID (CID)", "cid")
            addIfPresent("Location Area Code (LAC)", "lac")
            addIfPresent("ARFCN", "arfcn")
            addIfPresent("BSIC", "bsic")
        }

        "CDMA" -> {
            addIfPresent("Base Station ID", "basestationId")
            addIfPresent("Network ID", "networkId")
            addIfPresent("System ID", "systemId")
        }
    }

    addIfPresent("ASU", "asu")
    addIfPresent("Signal Level", "level")
    addIfPresent("Registered", "registered")
    return fields
}

private fun formatTowerRangeFeetMiles(
    fromLat: Double,
    fromLon: Double,
    toLat: Double,
    toLon: Double
): String {
    val result = FloatArray(1)
    android.location.Location.distanceBetween(fromLat, fromLon, toLat, toLon, result)
    val meters = result[0].toDouble()
    val miles = meters / 1609.344
    val feet = meters / 0.3048
    return String.format(Locale.US, "%.2f mi (%.0f ft)", miles, feet)
}

private fun formatDistanceFeetMiles(meters: Double): String {
    val safeMeters = meters.coerceAtLeast(0.0)
    val miles = safeMeters / 1609.344
    val feet = safeMeters / 0.3048
    return String.format(Locale.US, "%.2f mi (%.0f ft)", miles, feet)
}

private fun distanceFromLocationMeters(
    fromLat: Double,
    fromLon: Double,
    toLat: Double?,
    toLon: Double?
): Double? {
    if (!isValidLatLon(toLat, toLon)) return null
    val result = FloatArray(1)
    android.location.Location.distanceBetween(fromLat, fromLon, toLat!!, toLon!!, result)
    return result[0].toDouble()
}

private fun filterPinsNearCurrentLocation(
    pins: List<MapPin>,
    currentLocation: DetectionLocation?,
    maxDistanceMeters: Double
): List<MapPin> {
    if (pins.isEmpty() || currentLocation == null || maxDistanceMeters <= 0.0) {
        return pins
    }

    return pins.filter { pin ->
        val distance = distanceFromLocationMeters(
            fromLat = currentLocation.lat,
            fromLon = currentLocation.lon,
            toLat = pin.position.latitude,
            toLon = pin.position.longitude
        )
        distance != null && distance <= maxDistanceMeters
    }
}

private fun distanceForEncounterMeters(
    encounter: Encounter,
    currentLocation: DetectionLocation?
): Double? {
    // Prefer radio-derived proximity estimates for local radios.
    val estimatedRange = estimateRangeMeters(encounter)
    if (estimatedRange != null) return estimatedRange

    return currentLocation?.let {
        distanceFromLocationMeters(
            fromLat = it.lat,
            fromLon = it.lon,
            toLat = encounter.lat,
            toLon = encounter.lon
        )
    }
}

private fun distanceForDeviceMeters(
    device: DeviceItem,
    currentLocation: DetectionLocation?
): Double? {
    val pseudoEncounter = Encounter(
        timestampEpochMs = device.lastSeenEpochMs,
        source = runCatching { EncounterSource.valueOf(device.source) }.getOrDefault(EncounterSource.UNKNOWN_RF),
        primaryId = device.primaryId,
        secondaryId = device.secondaryId,
        rssiDbm = device.lastRssiDbm,
        frequencyMhz = device.lastFrequencyMhz,
        lat = device.lastLat,
        lon = device.lastLon,
        rawPayloadJson = device.lastRawPayloadJson ?: "{}"
    )
    return distanceForEncounterMeters(pseudoEncounter, currentLocation)
}

private suspend fun resolveDeviceLocation(
    source: EncounterSource,
    encounters: List<Encounter>
): ResolvedDeviceLocation? {
    val latest = encounters.maxByOrNull { it.timestampEpochMs } ?: return null
    return when (source) {
        EncounterSource.CELL -> {
            when (val lookup = CellTowerLookupService.lookup(latest)) {
                is TowerLookupResult.Success -> {
                    val estimate = lookup.estimate
                    if (!isValidLatLon(estimate.latitude, estimate.longitude)) {
                        null
                    } else {
                        ResolvedDeviceLocation(
                            lat = estimate.latitude,
                            lon = estimate.longitude,
                            method = estimate.provider,
                            approximateRangeMeters = null,
                            resolvedFromTimestampEpochMs = latest.timestampEpochMs
                        )
                    }
                }

                is TowerLookupResult.Failure -> {
                    if (!isValidLatLon(latest.lat, latest.lon)) {
                        null
                    } else {
                        ResolvedDeviceLocation(
                            lat = latest.lat!!,
                            lon = latest.lon!!,
                            method = "Observed encounter location fallback",
                            approximateRangeMeters = null,
                            resolvedFromTimestampEpochMs = latest.timestampEpochMs
                        )
                    }
                }
            }
        }

        EncounterSource.WIFI,
        EncounterSource.BLUETOOTH_LE -> {
            val inferred = withContext(Dispatchers.Default) {
                inferLikelyDeviceLocation(encounters)
            }
            if (inferred != null && isValidLatLon(inferred.lat, inferred.lon)) {
                ResolvedDeviceLocation(
                    lat = inferred.lat,
                    lon = inferred.lon,
                    method = "Inferred location",
                    approximateRangeMeters = inferred.estimatedRangeMeters,
                    resolvedFromTimestampEpochMs = latest.timestampEpochMs
                )
            } else if (isValidLatLon(latest.lat, latest.lon)) {
                ResolvedDeviceLocation(
                    lat = latest.lat!!,
                    lon = latest.lon!!,
                    method = "Observed encounter location fallback",
                    approximateRangeMeters = estimateRangeMeters(latest),
                    resolvedFromTimestampEpochMs = latest.timestampEpochMs
                )
            } else {
                null
            }
        }

        EncounterSource.REMOTE_ID -> {
            val broadcastPoint = latestRemoteIdBroadcastPoint(encounters)
            if (broadcastPoint != null) {
                ResolvedDeviceLocation(
                    lat = broadcastPoint.lat,
                    lon = broadcastPoint.lon,
                    method = "Remote ID broadcast location",
                    approximateRangeMeters = null,
                    resolvedFromTimestampEpochMs = broadcastPoint.timestampEpochMs
                )
            } else {
                null
            }
        }

        else -> {
            if (!isValidLatLon(latest.lat, latest.lon)) {
                null
            } else {
                ResolvedDeviceLocation(
                    lat = latest.lat!!,
                    lon = latest.lon!!,
                    method = "Observed encounter location",
                    approximateRangeMeters = null,
                    resolvedFromTimestampEpochMs = latest.timestampEpochMs
                )
            }
        }
    }
}

private fun analyzeApproachSignal(encounters: List<Encounter>): ApproachSignal? {
    val samples = encounters
        .sortedBy { it.timestampEpochMs }
        .mapNotNull { encounter ->
            val distance = distanceForEncounterMeters(encounter, null) ?: return@mapNotNull null
            encounter.timestampEpochMs to distance
        }

    if (samples.size < 4) return null

    val recent = samples.takeLast(8)
    val spanMs = recent.last().first - recent.first().first
    if (spanMs < 15_000L) return null

    val chunkSize = (recent.size / 3).coerceAtLeast(1)
    val firstChunk = recent.take(chunkSize).map { it.second }
    val lastChunk = recent.takeLast(chunkSize).map { it.second }
    val startAvg = firstChunk.average()
    val endAvg = lastChunk.average()
    val deltaMeters = startAvg - endAvg

    var approachSteps = 0
    var consideredSteps = 0
    for (i in 1 until recent.size) {
        val prev = recent[i - 1].second
        val curr = recent[i].second
        val step = prev - curr
        consideredSteps += 1
        if (step >= 3.0 || (prev > 0.0 && (curr / prev) <= 0.97)) {
            approachSteps += 1
        }
    }

    if (consideredSteps == 0) return null
    val consistency = approachSteps.toDouble() / consideredSteps.toDouble()
    val confidence = (0.65 * (deltaMeters / 60.0).coerceIn(0.0, 1.0) +
        0.35 * consistency).coerceIn(0.0, 1.0)

    val approaching = deltaMeters >= 15.0 && consistency >= 0.60 && confidence >= 0.55
    return ApproachSignal(
        isApproaching = approaching,
        confidence = confidence,
        deltaMeters = deltaMeters
    )
}

private fun analyzeMotionSignal(encounters: List<Encounter>): MotionSignal? {
    data class TimedPoint(val timestampEpochMs: Long, val lat: Double, val lon: Double)

    val points = encounters
        .filter { isValidLatLon(it.lat, it.lon) }
        .sortedBy { it.timestampEpochMs }
        .map { TimedPoint(it.timestampEpochMs, it.lat!!, it.lon!!) }
        .distinctBy { "${it.timestampEpochMs}:${it.lat}:${it.lon}" }

    if (points.size < 3) return null
    val recent = points.takeLast(12)
    if (recent.size < 3) return null

    var totalDistanceMeters = 0.0
    var totalTimeSeconds = 0.0
    var validSegments = 0
    var weightedSin = 0.0
    var weightedCos = 0.0

    for (i in 1 until recent.size) {
        val prev = recent[i - 1]
        val curr = recent[i]
        val dtSeconds = ((curr.timestampEpochMs - prev.timestampEpochMs).coerceAtLeast(0L) / 1000.0)
        if (dtSeconds < 1.0 || dtSeconds > 180.0) continue

        val distance = distanceFromLocationMeters(prev.lat, prev.lon, curr.lat, curr.lon) ?: continue
        if (!distance.isFinite() || distance < 0.5) continue

        val heading = bearingDegrees(prev.lat, prev.lon, curr.lat, curr.lon)
        totalDistanceMeters += distance
        totalTimeSeconds += dtSeconds
        validSegments += 1
        val headingRad = Math.toRadians(heading)
        weightedSin += sin(headingRad) * distance
        weightedCos += cos(headingRad) * distance
    }

    if (validSegments < 2 || totalTimeSeconds <= 0.0) return null

    val avgSpeedMps = (totalDistanceMeters / totalTimeSeconds).coerceAtLeast(0.0)
    val netDisplacement = distanceFromLocationMeters(
        recent.first().lat,
        recent.first().lon,
        recent.last().lat,
        recent.last().lon
    ) ?: 0.0

    val isInMotion = avgSpeedMps >= 0.8 && netDisplacement >= 10.0

    val heading = if (weightedSin == 0.0 && weightedCos == 0.0) {
        0.0
    } else {
        ((Math.toDegrees(kotlin.math.atan2(weightedSin, weightedCos)) + 360.0) % 360.0)
    }

    return MotionSignal(
        isInMotion = isInMotion,
        speedMps = avgSpeedMps,
        headingDeg = heading,
        sampleCount = validSegments
    )
}

private fun bearingDegrees(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
    val fromLatRad = Math.toRadians(fromLat)
    val toLatRad = Math.toRadians(toLat)
    val deltaLonRad = Math.toRadians(toLon - fromLon)

    val y = sin(deltaLonRad) * cos(toLatRad)
    val x = cos(fromLatRad) * sin(toLatRad) -
        sin(fromLatRad) * cos(toLatRad) * cos(deltaLonRad)
    return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
}

private fun formatSpeedLabel(speedMps: Double): String {
    val safe = speedMps.coerceAtLeast(0.0)
    val mph = safe * 2.2369362921
    return String.format(Locale.US, "%.1f mph", mph)
}

private fun formatHeadingCardinal(headingDeg: Double): String {
    val normalized = ((headingDeg % 360.0) + 360.0) % 360.0
    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = (((normalized + 22.5) / 45.0).toInt()) % directions.size
    return "${directions[index]} (${String.format(Locale.US, "%.0f", normalized)} deg)"
}

private fun locationCellKey(lat: Double, lon: Double, cellDegrees: Double = 0.0025): String {
    val latBucket = (lat / cellDegrees).toInt()
    val lonBucket = (lon / cellDegrees).toInt()
    return "$latBucket:$lonBucket"
}

private fun analyzeTrackerRisk(
    encounters: List<Encounter>,
    isOwned: Boolean,
    approachSignal: ApproachSignal?
): TrackerRiskSignal? {
    if (isOwned) {
        return TrackerRiskSignal(
            level = TrackerRiskLevel.NONE,
            confidence = 1.0,
            uniqueLocationCells = 0,
            spreadMeters = 0.0,
            activeWindowMinutes = 0.0,
            summary = "Marked as owned by user"
        )
    }

    val ordered = encounters
        .sortedBy { it.timestampEpochMs }
        .takeLast(24)
    if (ordered.size < 3) return null

    val validLocations = ordered
        .mapNotNull { e ->
            if (!isValidLatLon(e.lat, e.lon)) return@mapNotNull null
            e.lat!! to e.lon!!
        }
    if (validLocations.size < 3) return null

    val uniqueCells = validLocations
        .map { (lat, lon) -> locationCellKey(lat, lon) }
        .toSet()
        .size

    var maxSpreadMeters = 0.0
    validLocations.forEachIndexed { i, a ->
        for (j in i + 1 until validLocations.size) {
            val b = validLocations[j]
            val spread = distanceFromLocationMeters(a.first, a.second, b.first, b.second) ?: 0.0
            if (spread > maxSpreadMeters) maxSpreadMeters = spread
        }
    }

    val activeWindowMinutes = ((ordered.last().timestampEpochMs - ordered.first().timestampEpochMs)
        .coerceAtLeast(0L) / 60_000.0)

    val locationScore = ((uniqueCells - 1).toDouble() / 5.0).coerceIn(0.0, 1.0)
    val spreadScore = (maxSpreadMeters / 1500.0).coerceIn(0.0, 1.0)
    val durationScore = (activeWindowMinutes / 90.0).coerceIn(0.0, 1.0)
    val approachScore = when {
        approachSignal?.isApproaching == true -> approachSignal.confidence.coerceIn(0.0, 1.0)
        else -> 0.0
    }
    val confidence = (
        0.35 * locationScore +
            0.30 * spreadScore +
            0.20 * durationScore +
            0.15 * approachScore
        ).coerceIn(0.0, 1.0)

    val level = when {
        uniqueCells >= 5 && maxSpreadMeters >= 1200.0 && activeWindowMinutes >= 40.0 && confidence >= 0.75 -> {
            TrackerRiskLevel.HIGH
        }

        uniqueCells >= 3 && maxSpreadMeters >= 450.0 && activeWindowMinutes >= 20.0 && confidence >= 0.55 -> {
            TrackerRiskLevel.MEDIUM
        }

        uniqueCells >= 2 && maxSpreadMeters >= 200.0 && activeWindowMinutes >= 10.0 -> {
            TrackerRiskLevel.LOW
        }

        else -> TrackerRiskLevel.NONE
    }

    val summary = when (level) {
        TrackerRiskLevel.HIGH -> "Strong repeated co-movement across multiple locations"
        TrackerRiskLevel.MEDIUM -> "Moderate cross-location repeat pattern"
        TrackerRiskLevel.LOW -> "Early co-movement signal; monitor"
        TrackerRiskLevel.NONE -> "No meaningful co-movement pattern"
    }

    return TrackerRiskSignal(
        level = level,
        confidence = confidence,
        uniqueLocationCells = uniqueCells,
        spreadMeters = maxSpreadMeters,
        activeWindowMinutes = activeWindowMinutes,
        summary = summary
    )
}

private fun analyzeForeignSignalRisk(encounters: List<Encounter>): ForeignSignalRiskSignal? {
    val ordered = selectRecentEncounterWindow(
        encounters = encounters,
        windowMs = FOREIGN_RISK_WINDOW_MS,
        maxEncounters = FOREIGN_RISK_MAX_ENCOUNTERS
    ).sortedBy { it.timestampEpochMs }

    if (ordered.size < 8) return null

    val firstTs = ordered.first().timestampEpochMs
    val lastTs = ordered.last().timestampEpochMs
    val windowMinutes = ((lastTs - firstTs).coerceAtLeast(0L) / 60_000.0)

    val cellularScore = computeCellularAnomalyScore(ordered)
    val wifiScore = computeWifiAnomalyScore(ordered)
    val bleScore = computeBleAnomalyScore(ordered)
    val gnssScore = computeGnssInterferenceScore(ordered)
    val uwbScore = computeUwbActivityScore(ordered, windowMinutes)
    val rfTextureScore = computeRfTextureScore(ordered)

    val directAcousticObserved = ordered.any { isDirectSignalChannel(it, "acoustic") }
    val directMagneticObserved = ordered.any { isDirectSignalChannel(it, "magnetic") }
    val directAcousticScore = computeDirectAcousticScore(ordered)
    val directMagneticScore = computeDirectMagneticScore(ordered)

    val unknownEmissionScore = computeUnknownEmissionScore(ordered, windowMinutes)
    val acousticProxyScore = if (directAcousticObserved) {
        directAcousticScore
    } else {
        (unknownEmissionScore * 0.55).coerceIn(0.0, 1.0)
    }
    val magneticProxyScore = if (directMagneticObserved) {
        directMagneticScore
    } else {
        (unknownEmissionScore * 0.45).coerceIn(0.0, 1.0)
    }

    val distinctSources = ordered.map { it.source }.toSet().size
    val sourceDiversityScore = ((distinctSources - 1).toDouble() / 6.0).coerceIn(0.0, 1.0)

    val weightedScore = (
        0.18 * cellularScore +
            0.14 * wifiScore +
            0.14 * bleScore +
            0.14 * gnssScore +
            0.08 * uwbScore +
            0.18 * rfTextureScore +
            0.07 * acousticProxyScore +
            0.07 * magneticProxyScore +
            0.10 * sourceDiversityScore
        ).coerceIn(0.0, 1.0)

    val score = (weightedScore * 100.0).roundToInt().coerceIn(0, 100)

    val level = when {
        score >= 80 -> ForeignSignalRiskLevel.CRITICAL
        score >= 60 -> ForeignSignalRiskLevel.HIGH
        score >= 35 -> ForeignSignalRiskLevel.ELEVATED
        else -> ForeignSignalRiskLevel.QUIET
    }

    val activeSignalFamilies = listOf(
        cellularScore,
        wifiScore,
        bleScore,
        gnssScore,
        uwbScore,
        rfTextureScore
    ).count { it > 0.0 }

    val confidence = (
        0.50 * (ordered.size.toDouble() / 180.0).coerceIn(0.0, 1.0) +
            0.25 * (windowMinutes / 30.0).coerceIn(0.0, 1.0) +
            0.25 * (activeSignalFamilies.toDouble() / 6.0).coerceIn(0.0, 1.0)
        ).coerceIn(0.0, 1.0)

    val unavailable = buildList {
        if (ordered.none { it.source == EncounterSource.UWB }) {
            add("UWB (no recent encounters)")
        }
        if (!directAcousticObserved) add("Direct acoustic signature channel")
        if (!directMagneticObserved) add("Direct magnetometer disturbance channel")
    }

    val summary = when (level) {
        ForeignSignalRiskLevel.CRITICAL -> "Multiple channels show simultaneous foreign-signal anomalies."
        ForeignSignalRiskLevel.HIGH -> "Cross-channel anomalies detected; likely elevated foreign-signal activity nearby."
        ForeignSignalRiskLevel.ELEVATED -> "Early anomaly pattern detected across one or more radio channels."
        ForeignSignalRiskLevel.QUIET -> "No strong multi-channel foreign-signal anomalies detected."
    }

    return ForeignSignalRiskSignal(
        level = level,
        score = score,
        confidence = confidence,
        summary = summary,
        windowMinutes = windowMinutes,
        sampleCount = ordered.size,
        cellularAnomalyScore = cellularScore,
        wifiAnomalyScore = wifiScore,
        bleAnomalyScore = bleScore,
        gnssInterferenceScore = gnssScore,
        uwbActivityScore = uwbScore,
        rfTextureScore = rfTextureScore,
        acousticProxyScore = acousticProxyScore,
        magneticProxyScore = magneticProxyScore,
        directAcousticObserved = directAcousticObserved,
        directMagneticObserved = directMagneticObserved,
        unavailableSignals = unavailable
    )
}

private fun isDirectSignalChannel(encounter: Encounter, channel: String): Boolean {
    if (encounter.source != EncounterSource.UNKNOWN_RF) return false
    val normalizedChannel = channel.trim().lowercase(Locale.US)

    val secondary = encounter.secondaryId?.trim()?.lowercase(Locale.US)
    val primary = encounter.primaryId.trim().lowercase(Locale.US)
    if (normalizedChannel == "acoustic" &&
        (secondary == "direct-acoustic" || primary.startsWith("acoustic:"))
    ) {
        return true
    }
    if (normalizedChannel == "magnetic" &&
        (secondary == "direct-magnetic" || primary.startsWith("magnetic:"))
    ) {
        return true
    }

    val payload = parseEncounterPayload(encounter) ?: return false
    val signalChannel = payload.optString("signalChannel", "").trim().lowercase(Locale.US)
    val direct = payload.optBoolean("directChannel", false)
    return direct && signalChannel == normalizedChannel
}

private fun selectRecentEncounterWindow(
    encounters: List<Encounter>,
    windowMs: Long,
    maxEncounters: Int
): List<Encounter> {
    val latestTs = encounters.maxOfOrNull { it.timestampEpochMs }
    val cutoffTs = latestTs?.minus(windowMs) ?: Long.MIN_VALUE
    return encounters
        .asSequence()
        .filter { it.timestampEpochMs >= cutoffTs }
        .sortedByDescending { it.timestampEpochMs }
        .take(maxEncounters)
        .toList()
}

private fun computeDirectAcousticScore(encounters: List<Encounter>): Double {
    val acousticPayloads = encounters
        .filter { isDirectSignalChannel(it, "acoustic") }
        .mapNotNull { parseEncounterPayload(it) }

    if (acousticPayloads.size < 2) return 0.0

    val rmsDbFs = acousticPayloads
        .mapNotNull { payload ->
            if (!payload.has("rmsDbFs") || payload.isNull("rmsDbFs")) return@mapNotNull null
            payload.optDouble("rmsDbFs", Double.NaN).takeIf { it.isFinite() }
        }
    if (rmsDbFs.isEmpty()) return 0.0

    val loudRate = rmsDbFs.count { it >= -42.0 }.toDouble() / rmsDbFs.size.toDouble()
    val volatility = (standardDeviation(rmsDbFs) / 20.0).coerceIn(0.0, 1.0)
    return (0.65 * loudRate.coerceIn(0.0, 1.0) + 0.35 * volatility).coerceIn(0.0, 1.0)
}

private fun computeDirectMagneticScore(encounters: List<Encounter>): Double {
    val magneticPayloads = encounters
        .filter { isDirectSignalChannel(it, "magnetic") }
        .mapNotNull { parseEncounterPayload(it) }

    if (magneticPayloads.size < 2) return 0.0

    val magnitudes = magneticPayloads
        .mapNotNull { payload ->
            if (!payload.has("magnitudeMicroTesla") || payload.isNull("magnitudeMicroTesla")) return@mapNotNull null
            payload.optDouble("magnitudeMicroTesla", Double.NaN).takeIf { it.isFinite() }
        }
    if (magnitudes.isEmpty()) return 0.0

    val anomalyRate = magnitudes.count { it < 25.0 || it > 65.0 }.toDouble() / magnitudes.size.toDouble()
    val volatility = (standardDeviation(magnitudes) / 35.0).coerceIn(0.0, 1.0)
    return (0.70 * anomalyRate.coerceIn(0.0, 1.0) + 0.30 * volatility).coerceIn(0.0, 1.0)
}

private fun computeCellularAnomalyScore(encounters: List<Encounter>): Double {
    val cellEncounters = encounters
        .filter { it.source == EncounterSource.CELL }
        .sortedBy { it.timestampEpochMs }
    if (cellEncounters.size < 4) return 0.0

    val payloads = cellEncounters.mapNotNull { parseEncounterPayload(it) }
    val operatorCodes = payloads
        .map { it.optString("networkOperator", "").trim() }
        .filter { it.isNotBlank() }
    val radios = payloads
        .map { it.optString("radio", "").uppercase(Locale.US) }
        .filter { it.isNotBlank() }

    val operatorDiversity = ((operatorCodes.toSet().size - 1).toDouble() / 3.0).coerceIn(0.0, 1.0)
    val radioTransitions = transitionsFraction(radios)
    val rssiVolatility = (standardDeviation(cellEncounters.mapNotNull { it.rssiDbm?.toDouble() }) / 24.0)
        .coerceIn(0.0, 1.0)

    return (
        0.35 * operatorDiversity +
            0.35 * radioTransitions +
            0.30 * rssiVolatility
        ).coerceIn(0.0, 1.0)
}

private fun computeWifiAnomalyScore(encounters: List<Encounter>): Double {
    val wifiEncounters = encounters.filter {
        it.source == EncounterSource.WIFI || it.source == EncounterSource.WIFI_DIRECT
    }
    if (wifiEncounters.size < 6) return 0.0

    val payloads = wifiEncounters.mapNotNull { parseEncounterPayload(it) }
    val ssidToBssid = mutableMapOf<String, MutableSet<String>>()
    payloads.forEach { payload ->
        val ssid = payload.optString("ssid", "").trim()
        val bssid = payload.optString("bssid", "").trim()
        if (ssid.isNotBlank() && bssid.isNotBlank()) {
            ssidToBssid.getOrPut(ssid) { mutableSetOf() }.add(bssid)
        }
    }

    val spoofLikeClusters = ssidToBssid.values.count { it.size >= 3 }
    val spoofClusterScore = if (ssidToBssid.isEmpty()) {
        0.0
    } else {
        (spoofLikeClusters.toDouble() / ssidToBssid.size.toDouble()).coerceIn(0.0, 1.0)
    }

    val idChurn = (wifiEncounters.map { it.primaryId }.toSet().size.toDouble() / wifiEncounters.size.toDouble())
        .coerceIn(0.0, 1.0)
    val rssiVolatility = (standardDeviation(wifiEncounters.mapNotNull { it.rssiDbm?.toDouble() }) / 20.0)
        .coerceIn(0.0, 1.0)

    return (
        0.35 * spoofClusterScore +
            0.30 * idChurn +
            0.35 * rssiVolatility
        ).coerceIn(0.0, 1.0)
}

private fun computeBleAnomalyScore(encounters: List<Encounter>): Double {
    val bleEncounters = encounters.filter {
        it.source == EncounterSource.BLUETOOTH_LE || it.source == EncounterSource.REMOTE_ID
    }
    if (bleEncounters.size < 6) return 0.0

    val payloads = bleEncounters.mapNotNull { parseEncounterPayload(it) }
    val unknownClassRate = payloads
        .map { it.optString("deviceClassHint", "") }
        .let { classes ->
            if (classes.isEmpty()) 0.0 else classes.count { it.equals("unknown", ignoreCase = true) }.toDouble() / classes.size.toDouble()
        }
    val idChurn = (bleEncounters.map { it.primaryId }.toSet().size.toDouble() / bleEncounters.size.toDouble())
        .coerceIn(0.0, 1.0)
    val rssiVolatility = (standardDeviation(bleEncounters.mapNotNull { it.rssiDbm?.toDouble() }) / 18.0)
        .coerceIn(0.0, 1.0)

    return (
        0.40 * idChurn +
            0.30 * unknownClassRate.coerceIn(0.0, 1.0) +
            0.30 * rssiVolatility
        ).coerceIn(0.0, 1.0)
}

private fun computeGnssInterferenceScore(encounters: List<Encounter>): Double {
    data class TimedLatLon(val ts: Long, val lat: Double, val lon: Double)

    val points = encounters
        .filter { isValidLatLon(it.lat, it.lon) }
        .sortedBy { it.timestampEpochMs }
        .map { TimedLatLon(it.timestampEpochMs, it.lat!!, it.lon!!) }
        .distinctBy { "${it.ts}:${it.lat}:${it.lon}" }

    if (points.size < 4) return 0.0

    val speeds = mutableListOf<Double>()
    val stepDistances = mutableListOf<Double>()

    for (i in 1 until points.size) {
        val previous = points[i - 1]
        val current = points[i]
        val dtSeconds = ((current.ts - previous.ts).coerceAtLeast(0L) / 1000.0)
        if (dtSeconds <= 0.0 || dtSeconds > 300.0) continue
        val distance = distanceFromLocationMeters(previous.lat, previous.lon, current.lat, current.lon) ?: continue
        if (!distance.isFinite() || distance < 0.0) continue
        stepDistances += distance
        speeds += (distance / dtSeconds)
    }

    if (speeds.size < 3) return 0.0

    val impossibleSpeedRatio = speeds.count { it > 70.0 }.toDouble() / speeds.size.toDouble()
    val jitterScore = (standardDeviation(stepDistances) / 150.0).coerceIn(0.0, 1.0)

    return (
        0.65 * impossibleSpeedRatio.coerceIn(0.0, 1.0) +
            0.35 * jitterScore
        ).coerceIn(0.0, 1.0)
}

private fun computeUwbActivityScore(encounters: List<Encounter>, windowMinutes: Double): Double {
    val uwbEncounters = encounters.filter { it.source == EncounterSource.UWB }
    if (uwbEncounters.isEmpty()) return 0.0
    val safeWindow = windowMinutes.coerceAtLeast(1.0)
    val burstDensityPerMinute = uwbEncounters.size.toDouble() / safeWindow
    val burstScore = (burstDensityPerMinute / 6.0).coerceIn(0.0, 1.0)
    val idDiversity = (uwbEncounters.map { it.primaryId }.toSet().size.toDouble() / uwbEncounters.size.toDouble())
        .coerceIn(0.0, 1.0)
    return (0.70 * burstScore + 0.30 * idDiversity).coerceIn(0.0, 1.0)
}

private fun computeRfTextureScore(encounters: List<Encounter>): Double {
    val samples = encounters.mapNotNull { it.rssiDbm?.toDouble() }
    if (samples.size < 6) return 0.0

    val weakSignalRate = samples.count { it <= -92.0 }.toDouble() / samples.size.toDouble()
    val volatility = (standardDeviation(samples) / 22.0).coerceIn(0.0, 1.0)

    val sourceAverages = encounters
        .filter { it.rssiDbm != null }
        .groupBy { it.source }
        .mapValues { (_, values) -> values.mapNotNull { it.rssiDbm?.toDouble() }.average() }

    val crossSourceWeakness = if (sourceAverages.isEmpty()) {
        0.0
    } else {
        sourceAverages.values.count { it <= -88.0 }.toDouble() / sourceAverages.size.toDouble()
    }

    return (
        0.40 * weakSignalRate.coerceIn(0.0, 1.0) +
            0.35 * volatility +
            0.25 * crossSourceWeakness.coerceIn(0.0, 1.0)
        ).coerceIn(0.0, 1.0)
}

private fun computeUnknownEmissionScore(encounters: List<Encounter>, windowMinutes: Double): Double {
    val unknown = encounters.count {
        it.source == EncounterSource.SDR ||
            (it.source == EncounterSource.UNKNOWN_RF &&
                !isDirectSignalChannel(it, "acoustic") &&
                !isDirectSignalChannel(it, "magnetic"))
    }
    if (unknown == 0) return 0.0
    val safeWindow = windowMinutes.coerceAtLeast(1.0)
    return ((unknown.toDouble() / safeWindow) / 4.0).coerceIn(0.0, 1.0)
}

private fun parseEncounterPayload(encounter: Encounter): JSONObject? =
    runCatching { JSONObject(encounter.rawPayloadJson) }.getOrNull()

private fun transitionsFraction(values: List<String>): Double {
    if (values.size < 2) return 0.0
    var transitions = 0
    for (i in 1 until values.size) {
        if (values[i] != values[i - 1]) transitions += 1
    }
    return (transitions.toDouble() / (values.size - 1).toDouble()).coerceIn(0.0, 1.0)
}

private fun standardDeviation(values: List<Double>): Double {
    if (values.size < 2) return 0.0
    val mean = values.average()
    val variance = values
        .map { sample ->
            val delta = sample - mean
            delta * delta
        }
        .average()
    if (!variance.isFinite()) return 0.0
    return sqrt(variance)
}

private fun formatRiskScorePct(score: Double): String =
    "${(score.coerceIn(0.0, 1.0) * 100.0).roundToInt()}%"

private fun estimateWifiRangeMeters(encounter: Encounter): Double? {
    val rssi = encounter.rssiDbm ?: return null
    if (rssi >= 0) return null
    val frequencyMhz = encounter.frequencyMhz?.toDouble() ?: 2412.0
    if (frequencyMhz <= 0.0) return null

    // RSSI-distance approximation with potentially high variance in dense environments.
    val estimate = 10.0.pow((27.55 - (20.0 * log10(frequencyMhz)) + abs(rssi.toDouble())) / 20.0)
    if (!estimate.isFinite()) return null
    return estimate.coerceIn(1.0, 3000.0)
}

private fun estimateBleRangeMeters(encounter: Encounter): Double? {
    val rssi = encounter.rssiDbm ?: return null
    if (rssi >= 0) return null

    val payload = runCatching { JSONObject(encounter.rawPayloadJson) }.getOrNull()
    val txPower = payload?.opt("txPower")
        ?.let { value ->
            when (value) {
                is Number -> value.toInt()
                else -> value.toString().toIntOrNull()
            }
        }
        ?.takeIf { it in -127..20 }
        ?: -59

    val pathLossExponent = 2.2
    val estimate = 10.0.pow((txPower - rssi).toDouble() / (10.0 * pathLossExponent))
    if (!estimate.isFinite()) return null
    return estimate.coerceIn(1.0, 3000.0)
}

private fun estimateRangeMeters(encounter: Encounter): Double? = when (encounter.source) {
    EncounterSource.WIFI -> estimateWifiRangeMeters(encounter)
    EncounterSource.BLUETOOTH_LE -> estimateBleRangeMeters(encounter)
    else -> null
}

private fun estimateApproachingDeviceLocation(encounters: List<Encounter>): Pair<LatLng, String>? {
    if (encounters.isEmpty()) return null
    val latest = encounters.maxByOrNull { it.timestampEpochMs } ?: return null

    if (latest.source == EncounterSource.REMOTE_ID) {
        val broadcastPoint = latestRemoteIdBroadcastPoint(encounters)
        if (broadcastPoint != null) {
            return LatLng(broadcastPoint.lat, broadcastPoint.lon) to "Remote ID broadcast location"
        }
        return null
    }

    val inferred = when (latest.source) {
        EncounterSource.WIFI,
        EncounterSource.BLUETOOTH_LE -> inferLikelyDeviceLocation(encounters)
        else -> null
    }

    if (inferred != null && isValidLatLon(inferred.lat, inferred.lon)) {
        return LatLng(inferred.lat, inferred.lon) to "Inferred from movement/range"
    }

    if (isValidLatLon(latest.lat, latest.lon)) {
        return LatLng(latest.lat!!, latest.lon!!) to "Observed encounter location"
    }

    return null
}

private fun inferLikelyDeviceLocation(encounters: List<Encounter>): InferredDeviceLocation? {
    data class RangeObservation(
        val lat: Double,
        val lon: Double,
        val rangeMeters: Double
    )

    val observations = encounters.mapNotNull { encounter ->
        if (!isValidLatLon(encounter.lat, encounter.lon)) return@mapNotNull null
        val rangeMeters = estimateRangeMeters(encounter) ?: return@mapNotNull null
        RangeObservation(encounter.lat!!, encounter.lon!!, rangeMeters)
    }

    if (observations.isEmpty()) return null
    if (observations.size == 1) {
        val single = observations.first()
        return InferredDeviceLocation(single.lat, single.lon, single.rangeMeters)
    }

    val refLat = observations.map { it.lat }.average()
    val refLon = observations.map { it.lon }.average()
    val refLatRad = Math.toRadians(refLat)
    val metersPerDegLat = 111_132.0
    val metersPerDegLon = 111_320.0 * cos(refLatRad)

    data class LocalObs(
        val x: Double,
        val y: Double,
        val range: Double,
        val weight: Double
    )

    val localObs = observations.map { obs ->
        LocalObs(
            x = (obs.lon - refLon) * metersPerDegLon,
            y = (obs.lat - refLat) * metersPerDegLat,
            range = obs.rangeMeters,
            weight = 1.0 / obs.rangeMeters.coerceAtLeast(1.0)
        )
    }

    fun objective(x: Double, y: Double): Double {
        var total = 0.0
        localObs.forEach { obs ->
            val dx = x - obs.x
            val dy = y - obs.y
            val dist = sqrt(dx * dx + dy * dy)
            val err = dist - obs.range
            total += obs.weight * err * err
        }
        return total
    }

    val initialWeightTotal = localObs.sumOf { it.weight }
    var x = if (initialWeightTotal > 0.0) {
        localObs.sumOf { it.x * it.weight } / initialWeightTotal
    } else {
        0.0
    }
    var y = if (initialWeightTotal > 0.0) {
        localObs.sumOf { it.y * it.weight } / initialWeightTotal
    } else {
        0.0
    }

    var step = localObs.map { it.range }.average().coerceAtLeast(20.0)
    var bestScore = objective(x, y)
    repeat(80) {
        val candidates = listOf(
            x to y,
            x + step to y,
            x - step to y,
            x to y + step,
            x to y - step,
            x + step to y + step,
            x + step to y - step,
            x - step to y + step,
            x - step to y - step
        )

        var moved = false
        candidates.forEach { (cx, cy) ->
            val score = objective(cx, cy)
            if (score < bestScore) {
                bestScore = score
                x = cx
                y = cy
                moved = true
            }
        }

        step = if (moved) {
            step * 0.92
        } else {
            step * 0.70
        }
        if (step < 1.0) return@repeat
    }

    val inferredLat = refLat + (y / metersPerDegLat)
    val inferredLon = refLon + (x / metersPerDegLon)
    if (!isValidLatLon(inferredLat, inferredLon)) return null

    val approxRange = localObs.map {
        val dx = x - it.x
        val dy = y - it.y
        sqrt(dx * dx + dy * dy)
    }.average()

    return InferredDeviceLocation(
        lat = inferredLat,
        lon = inferredLon,
        estimatedRangeMeters = if (approxRange.isFinite()) approxRange else null
    )
}

private fun hasNetworkConnectivity(context: android.content.Context): Boolean {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun hasPostNotificationsPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

private fun ensureApproachNotificationChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val existing = manager.getNotificationChannel(APPROACH_ALERT_CHANNEL_ID)
    if (existing != null) return

    val channel = NotificationChannel(
        APPROACH_ALERT_CHANNEL_ID,
        "Approach Alerts",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Alerts when detected devices are approaching"
    }
    manager.createNotificationChannel(channel)
}

private fun ensureTrackerNotificationChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val existing = manager.getNotificationChannel(TRACKER_ALERT_CHANNEL_ID)
    if (existing != null) return

    val channel = NotificationChannel(
        TRACKER_ALERT_CHANNEL_ID,
        "Tracker Suspicion Alerts",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Alerts when unknown devices repeatedly co-move across locations"
    }
    manager.createNotificationChannel(channel)
}

private fun ensureForeignSignalNotificationChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val existing = manager.getNotificationChannel(FOREIGN_SIGNAL_ALERT_CHANNEL_ID)
    if (existing != null) return

    val channel = NotificationChannel(
        FOREIGN_SIGNAL_ALERT_CHANNEL_ID,
        "Foreign Signal Alerts",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Alerts when multi-channel foreign-signal risk crosses threshold"
    }
    manager.createNotificationChannel(channel)
}

private fun ensureMagneticIncreaseNotificationChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val existing = manager.getNotificationChannel(MAGNETIC_INCREASE_ALERT_CHANNEL_ID)
    if (existing != null) return

    val channel = NotificationChannel(
        MAGNETIC_INCREASE_ALERT_CHANNEL_ID,
        "Magnetometer Disturbance Alerts",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Alerts when direct magnetometer magnitude rises sharply"
    }
    manager.createNotificationChannel(channel)
}

private fun sendApproachNotification(context: android.content.Context, device: DeviceItem) {
    val confidencePct = ((device.approachConfidence ?: 0.0) * 100.0).toInt().coerceIn(0, 100)
    val trend = device.approachDeltaMeters
        ?.takeIf { it > 0.0 }
        ?.let { formatDistanceFeetMiles(it) }
        ?: "unknown"

    val ownershipTag = if (device.isOwned) " [OWNED DEVICE]" else ""
    val title = if (device.isOwned) "Approaching owned device" else "Approaching device detected"
    val content = "${listSourceLabel(device.source, device.secondaryId)} ${device.primaryId}${ownershipTag} • Confidence ${confidencePct}% • Trend ${trend}"
    val notificationId = ("${device.source}|${device.primaryId}").hashCode()
    val tapIntent = Intent(context, MainActivity::class.java).apply {
        action = ACTION_OPEN_APPROACH_MAP
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_APPROACH_SOURCE, device.source)
        putExtra(EXTRA_APPROACH_PRIMARY_ID, device.primaryId)
    }
    val tapPendingIntent = PendingIntent.getActivity(
        context,
        notificationId,
        tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, APPROACH_ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_more)
        .setContentTitle(title)
        .setContentText(content)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(tapPendingIntent)
        .build()

    NotificationManagerCompat.from(context).notify(notificationId, notification)
}

private fun sendTrackerRiskNotification(context: android.content.Context, device: DeviceItem) {
    val risk = device.trackerRisk ?: return
    val title = "Potential tracker pattern detected"
    val content = buildString {
        append(listSourceLabel(device.source, device.secondaryId))
        append(" ")
        append(device.primaryId)
        append(" • Risk ")
        append(risk.level.name)
        append(" • ")
        append(String.format(Locale.US, "%.0f%% confidence", risk.confidence * 100.0))
    }

    val notification = NotificationCompat.Builder(context, TRACKER_ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle(title)
        .setContentText(content)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    val notificationId = ("tracker:${device.source}|${device.primaryId}").hashCode()
    NotificationManagerCompat.from(context).notify(notificationId, notification)
}

private fun sendForeignSignalRiskNotification(
    context: android.content.Context,
    risk: ForeignSignalRiskSignal
) {
    val title = "Foreign signal risk ${risk.level.name}"
    val content = buildString {
        append("Score ")
        append(risk.score)
        append("/100")
        append(" • ")
        append(String.format(Locale.US, "%.0f%% confidence", risk.confidence * 100.0))
        append(" • ")
        append(risk.summary)
    }

    val notification = NotificationCompat.Builder(context, FOREIGN_SIGNAL_ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setContentTitle(title)
        .setContentText(content)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    val notificationId = ("foreign-signal:${risk.level.name}:${risk.score}").hashCode()
    NotificationManagerCompat.from(context).notify(notificationId, notification)
}

private fun sendMagneticIncreaseNotification(
    context: android.content.Context,
    previousMagnitudeMicroTesla: Double,
    currentMagnitudeMicroTesla: Double,
    deltaMicroTesla: Double
) {
    val title = "Magnetometer disturbance increase"
    val content = buildString {
        append("Magnitude ")
        append(String.format(Locale.US, "%.1f", previousMagnitudeMicroTesla))
        append(" -> ")
        append(String.format(Locale.US, "%.1f", currentMagnitudeMicroTesla))
        append(" uT")
        append(" (delta ")
        append(String.format(Locale.US, "%+.1f", deltaMicroTesla))
        append(" uT)")
    }

    val notification = NotificationCompat.Builder(context, MAGNETIC_INCREASE_ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_more)
        .setContentTitle(title)
        .setContentText(content)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    val notificationId = ("magnetic-increase:${System.currentTimeMillis() / 60_000L}").hashCode()
    NotificationManagerCompat.from(context).notify(notificationId, notification)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DevicesEncountersPage(
    recentEncounters: List<Encounter>,
    allEncounters: List<Encounter>,
    approachDetectionEnabled: Boolean,
    ownedDeviceKeys: Set<String>,
    onDeviceClick: (DeviceItem) -> Unit,
    onEncounterClick: (Encounter) -> Unit
) {
    val tabs = listOf("Devices", "Encounters")
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Devices & Encounters", style = MaterialTheme.typography.headlineMedium)
        Text("Switch between grouped device history and individual encounter telemetry.")
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedTab == 0) {
                DevicesPage(
                    recentEncounters = recentEncounters,
                    allEncounters = allEncounters,
                    approachDetectionEnabled = approachDetectionEnabled,
                    ownedDeviceKeys = ownedDeviceKeys,
                    onDeviceClick = onDeviceClick
                )
            } else {
                EncountersPage(
                    recentEncounters = recentEncounters,
                    allEncounters = allEncounters,
                    ownedDeviceKeys = ownedDeviceKeys,
                    onEncounterClick = onEncounterClick
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DevicesPage(
    recentEncounters: List<Encounter>,
    allEncounters: List<Encounter>,
    approachDetectionEnabled: Boolean,
    ownedDeviceKeys: Set<String>,
    onDeviceClick: (DeviceItem) -> Unit
) {
    val context = LocalContext.current
    val currentLocation = remember { LocationSnapshotProvider.read(context) }
    var dataScope by remember { mutableStateOf(DataScope.RECENT_100) }
    var sortMode by remember { mutableStateOf(DeviceSortMode.LAST_SEEN) }
    var sourceFilter by remember { mutableStateOf<String?>(null) }
    var queryFilter by remember { mutableStateOf("") }
    var showSecondaryIds by rememberSaveable { mutableStateOf(false) }
    var showDistance by rememberSaveable { mutableStateOf(false) }
    var sortByDistance by rememberSaveable { mutableStateOf(false) }
    var showOwnedOnly by rememberSaveable { mutableStateOf(false) }
    var showTrackerRiskOnly by rememberSaveable { mutableStateOf(false) }
    val selectedEncounters = if (dataScope == DataScope.RECENT_100) recentEncounters else allEncounters
    val devices = remember(selectedEncounters, sortMode, approachDetectionEnabled, ownedDeviceKeys) {
        buildDeviceItems(
            encounters = selectedEncounters,
            sortMode = sortMode,
            approachDetectionEnabled = approachDetectionEnabled,
            ownedDeviceKeys = ownedDeviceKeys
        )
    }
    val sourceOptions = remember(devices) { devices.map { it.source }.distinct().sorted() }
    val filteredDevices = remember(devices, sourceFilter, queryFilter, showOwnedOnly, showTrackerRiskOnly) {
        devices.filter { device ->
            val sourceMatches = sourceFilter == null || device.source == sourceFilter
            val queryMatches = queryFilter.isBlank() ||
                device.primaryId.contains(queryFilter, ignoreCase = true) ||
                (device.secondaryId?.contains(queryFilter, ignoreCase = true) == true)
            val ownedMatches = !showOwnedOnly || device.isOwned
            val riskMatches = !showTrackerRiskOnly ||
                (device.trackerRisk?.level == TrackerRiskLevel.HIGH || device.trackerRisk?.level == TrackerRiskLevel.MEDIUM)
            sourceMatches && queryMatches && ownedMatches && riskMatches
        }
    }
    val displayedDevices = remember(filteredDevices, showDistance, sortByDistance, currentLocation) {
        if (!showDistance || !sortByDistance) {
            filteredDevices
        } else {
            filteredDevices.sortedWith(
                compareByDescending<DeviceItem> { distanceForDeviceMeters(it, currentLocation) ?: Double.MIN_VALUE }
                    .thenByDescending { it.lastSeenEpochMs }
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Detected Devices", style = MaterialTheme.typography.headlineMedium)
        Text("Tap any device for detailed history.")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScopeFilterDropdown(
                selectedScope = dataScope,
                onScopeSelected = {
                    dataScope = it
                    sourceFilter = null
                    queryFilter = ""
                }
            )
            DeviceSortDropdown(
                selectedSort = sortMode,
                onSortSelected = { sortMode = it }
            )
            SourceFilterDropdown(
                selectedSource = sourceFilter,
                sourceOptions = sourceOptions,
                onSourceSelected = { sourceFilter = it }
            )
            OutlinedTextField(
                value = queryFilter,
                onValueChange = { queryFilter = it },
                modifier = Modifier.widthIn(min = 220.dp),
                label = { Text("Search device ID or label") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Show Secondary IDs")
                Switch(
                    checked = showSecondaryIds,
                    onCheckedChange = { showSecondaryIds = it }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Show Distance")
                Switch(
                    checked = showDistance,
                    onCheckedChange = {
                        showDistance = it
                        if (!it) sortByDistance = false
                    }
                )
            }
            if (showDistance) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sort by Distance")
                    Switch(
                        checked = sortByDistance,
                        onCheckedChange = { sortByDistance = it }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Show Owned Only")
                Switch(
                    checked = showOwnedOnly,
                    onCheckedChange = { showOwnedOnly = it }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tracker Risk Only")
                Switch(
                    checked = showTrackerRiskOnly,
                    onCheckedChange = { showTrackerRiskOnly = it }
                )
            }
        }
        Text("Showing ${displayedDevices.size} of ${devices.size}")
        LazyColumn {
            items(displayedDevices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable { onDeviceClick(device) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${listSourceLabel(device.source, device.secondaryId)} • ${device.primaryId}")
                        if (device.hasChainLinkedData) {
                            val label = if (device.chainLinkedPeerCount > 1) {
                                "CHAIN-LINKED (${device.chainLinkedPeerCount} peers)"
                            } else {
                                "CHAIN-LINKED"
                            }
                            Text(
                                text = label,
                                color = Color(0xFF1565C0),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (device.isOwned) {
                            Text(
                                text = "Marked as My Device",
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (device.isApproaching) {
                            val confidencePct = ((device.approachConfidence ?: 0.0) * 100.0).toInt()
                            val deltaLabel = device.approachDeltaMeters
                                ?.let { formatDistanceFeetMiles(it.coerceAtLeast(0.0)) }
                                ?: "n/a"
                            Text(
                                text = "Approaching • Confidence ${confidencePct}% • Trend ${deltaLabel}",
                                color = Color(0xFFB3261E),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (device.motionSpeedMps != null && device.motionHeadingDeg != null) {
                            val motionText = if (device.isInMotion) {
                                "In Motion • ${formatSpeedLabel(device.motionSpeedMps)} • Heading ${formatHeadingCardinal(device.motionHeadingDeg)}"
                            } else {
                                "Not moving • Last motion ${formatSpeedLabel(device.motionSpeedMps)}"
                            }
                            Text(
                                text = motionText,
                                color = if (device.isInMotion) Color(0xFF1565C0) else Color(0xFF5F6368),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        val recordSpeed = DeviceSpeedRecordStore.getRecordSpeedMps(context, device.source, device.primaryId)
                        if (recordSpeed != null) {
                            Text(
                                text = "Top Speed Record • ${formatSpeedLabel(recordSpeed)}",
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        when (device.trackerRisk?.level) {
                            TrackerRiskLevel.HIGH -> Text(
                                text = "Tracker Risk: HIGH",
                                color = Color(0xFFB3261E),
                                fontWeight = FontWeight.Bold
                            )

                            TrackerRiskLevel.MEDIUM -> Text(
                                text = "Tracker Risk: MEDIUM",
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.SemiBold
                            )

                            TrackerRiskLevel.LOW -> Text(
                                text = "Tracker Risk: LOW",
                                color = Color(0xFF6A4F00)
                            )

                            else -> Unit
                        }
                        if (showSecondaryIds && supportsSecondaryIdInList(device.source) && !device.secondaryId.isNullOrBlank()) {
                            Text("${secondaryIdLabel(device.source)}: ${device.secondaryId}")
                        }
                        if (showDistance) {
                            val distanceMeters = distanceForDeviceMeters(device, currentLocation)
                            Text(
                                "Distance: ${distanceMeters?.let(::formatDistanceFeetMiles) ?: "n/a"}"
                            )
                        }
                        Text("Seen ${device.seenCount} times")
                        Text("Last seen ${formatEpoch(device.lastSeenEpochMs)}")
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EncountersPage(
    recentEncounters: List<Encounter>,
    allEncounters: List<Encounter>,
    ownedDeviceKeys: Set<String>,
    onEncounterClick: (Encounter) -> Unit
) {
    val context = LocalContext.current
    val currentLocation = remember { LocationSnapshotProvider.read(context) }
    var dataScope by remember { mutableStateOf(DataScope.RECENT_100) }
    var sourceFilter by remember { mutableStateOf<String?>(null) }
    var queryFilter by remember { mutableStateOf("") }
    var showSecondaryIds by rememberSaveable { mutableStateOf(false) }
    var showDistance by rememberSaveable { mutableStateOf(false) }
    var sortByDistance by rememberSaveable { mutableStateOf(false) }
    var showOwnedOnly by rememberSaveable { mutableStateOf(false) }
    val encounters = if (dataScope == DataScope.RECENT_100) recentEncounters else allEncounters
    val sourceOptions = remember(encounters) { encounters.map { it.source.name }.distinct().sorted() }
    val filteredEncounters = remember(encounters, sourceFilter, queryFilter, showOwnedOnly, ownedDeviceKeys) {
        encounters.filter { encounter ->
            val sourceMatches = sourceFilter == null || encounter.source.name == sourceFilter
            val queryMatches = queryFilter.isBlank() ||
                encounter.primaryId.contains(queryFilter, ignoreCase = true) ||
                (encounter.secondaryId?.contains(queryFilter, ignoreCase = true) == true) ||
                encounter.rawPayloadJson.contains(queryFilter, ignoreCase = true)
            val ownedMatches = !showOwnedOnly ||
                (OwnedDeviceRegistry.keyFor(encounter.source.name, encounter.primaryId) in ownedDeviceKeys)
            sourceMatches && queryMatches && ownedMatches
        }
    }
    val displayedEncounters = remember(filteredEncounters, showDistance, sortByDistance, currentLocation) {
        if (!showDistance || !sortByDistance) {
            filteredEncounters
        } else {
            filteredEncounters.sortedWith(
                compareByDescending<Encounter> { distanceForEncounterMeters(it, currentLocation) ?: Double.MIN_VALUE }
                    .thenByDescending { it.timestampEpochMs }
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Encounters", style = MaterialTheme.typography.headlineMedium)
        Text("Tap any encounter for full telemetry.")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScopeFilterDropdown(
                selectedScope = dataScope,
                onScopeSelected = {
                    dataScope = it
                    sourceFilter = null
                    queryFilter = ""
                }
            )
            SourceFilterDropdown(
                selectedSource = sourceFilter,
                sourceOptions = sourceOptions,
                onSourceSelected = { sourceFilter = it }
            )
            OutlinedTextField(
                value = queryFilter,
                onValueChange = { queryFilter = it },
                modifier = Modifier.widthIn(min = 220.dp),
                label = { Text("Search ID, label, or payload") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Show Secondary IDs")
                Switch(
                    checked = showSecondaryIds,
                    onCheckedChange = { showSecondaryIds = it }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Show Distance")
                Switch(
                    checked = showDistance,
                    onCheckedChange = {
                        showDistance = it
                        if (!it) sortByDistance = false
                    }
                )
            }
            if (showDistance) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sort by Distance")
                    Switch(
                        checked = sortByDistance,
                        onCheckedChange = { sortByDistance = it }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Show Owned Only")
                Switch(
                    checked = showOwnedOnly,
                    onCheckedChange = { showOwnedOnly = it }
                )
            }
        }
        Text("Showing ${displayedEncounters.size} of ${encounters.size}")
        LazyColumn {
            items(displayedEncounters) { encounter ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable { onEncounterClick(encounter) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${listSourceLabel(encounter.source.name, encounter.secondaryId)} • ${encounter.primaryId}")
                        val provenanceLabel = provenanceBadge(encounter.provenance, encounter.provenanceNodeId)
                        if (provenanceLabel.isNotBlank()) {
                            Text(
                                provenanceLabel.trimStart(' ', '-', '•'),
                                color = Color(0xFF1565C0),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (OwnedDeviceRegistry.keyFor(encounter.source.name, encounter.primaryId) in ownedDeviceKeys) {
                            Text(
                                text = "Marked as My Device",
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (
                            showSecondaryIds &&
                            supportsSecondaryIdInList(encounter.source.name) &&
                            !encounter.secondaryId.isNullOrBlank()
                        ) {
                            Text("${secondaryIdLabel(encounter.source.name)}: ${encounter.secondaryId}")
                        }
                        if (showDistance) {
                            val distanceMeters = distanceForEncounterMeters(encounter, currentLocation)
                            Text(
                                "Distance: ${distanceMeters?.let(::formatDistanceFeetMiles) ?: "n/a"}"
                            )
                        }
                        Text("RSSI=${encounter.rssiDbm ?: "n/a"} dBm, Freq=${encounter.frequencyMhz ?: "n/a"} MHz")
                        Text(formatEpoch(encounter.timestampEpochMs))
                    }
                }
            }
        }
    }
}

private fun buildDeviceItems(
    encounters: List<Encounter>,
    sortMode: DeviceSortMode = DeviceSortMode.LAST_SEEN,
    approachDetectionEnabled: Boolean = true,
    ownedDeviceKeys: Set<String> = emptySet()
): List<DeviceItem> =
    encounters
        .groupBy { it.source.name to it.primaryId }
        .mapNotNull { (key, groupedEncounters) ->
            buildDeviceItemForGroup(
                source = key.first,
                primaryId = key.second,
                groupedEncounters = groupedEncounters,
                approachDetectionEnabled = approachDetectionEnabled,
                ownedDeviceKeys = ownedDeviceKeys
            )
        }
        .let { deviceItems ->
            when (sortMode) {
                DeviceSortMode.LAST_SEEN -> deviceItems.sortedByDescending { it.lastSeenEpochMs }
                DeviceSortMode.MOST_SEEN -> deviceItems.sortedWith(
                    compareByDescending<DeviceItem> { it.seenCount }
                        .thenByDescending { it.lastSeenEpochMs }
                )
            }
        }

private fun buildSingleDeviceItem(
    source: String,
    primaryId: String,
    groupedEncounters: List<Encounter>,
    approachDetectionEnabled: Boolean,
    ownedDeviceKeys: Set<String>
): DeviceItem? = buildDeviceItemForGroup(
    source = source,
    primaryId = primaryId,
    groupedEncounters = groupedEncounters,
    approachDetectionEnabled = approachDetectionEnabled,
    ownedDeviceKeys = ownedDeviceKeys
)

private fun buildDeviceItemForGroup(
    source: String,
    primaryId: String,
    groupedEncounters: List<Encounter>,
    approachDetectionEnabled: Boolean,
    ownedDeviceKeys: Set<String>
): DeviceItem? {
    if (groupedEncounters.isEmpty()) return null

    val latest = groupedEncounters.maxByOrNull { it.timestampEpochMs } ?: groupedEncounters.first()
    val remoteIdBroadcastPoint = if (source == EncounterSource.REMOTE_ID.name) {
        latestRemoteIdBroadcastPoint(groupedEncounters)
    } else {
        null
    }
    val owned = OwnedDeviceRegistry.keyFor(source, primaryId) in ownedDeviceKeys
    val approachSignal = if (approachDetectionEnabled) {
        analyzeApproachSignal(groupedEncounters)
    } else {
        null
    }
    val trackerRisk = analyzeTrackerRisk(
        encounters = groupedEncounters,
        isOwned = owned,
        approachSignal = approachSignal
    )
    val motionSignal = analyzeMotionSignal(groupedEncounters)

    return DeviceItem(
        source = source,
        primaryId = primaryId,
        secondaryId = latest.secondaryId,
        seenCount = groupedEncounters.size,
        lastSeenEpochMs = latest.timestampEpochMs,
        lastRssiDbm = latest.rssiDbm,
        lastFrequencyMhz = latest.frequencyMhz,
        lastLat = if (source == EncounterSource.REMOTE_ID.name) {
            remoteIdBroadcastPoint?.lat
        } else {
            latest.lat
        },
        lastLon = if (source == EncounterSource.REMOTE_ID.name) {
            remoteIdBroadcastPoint?.lon
        } else {
            latest.lon
        },
        lastRawPayloadJson = latest.rawPayloadJson,
        lastProvenance = latest.provenance,
        lastProvenanceNodeId = latest.provenanceNodeId,
        lastProvenanceOriginNodeId = latest.provenanceOriginNodeId,
        lastProvenancePathNodeIds = latest.provenancePathNodeIds,
        lastProvenanceReceivedAtEpochMs = latest.provenanceReceivedAtEpochMs,
        lastProvenanceHopCount = latest.provenanceHopCount,
        hasChainLinkedData = groupedEncounters.any { it.provenance == EncounterProvenance.CHAIN_LINKED },
        chainLinkedPeerCount = groupedEncounters.mapNotNull { it.provenanceNodeId }.toSet().size,
        isApproaching = approachSignal?.isApproaching == true,
        approachConfidence = approachSignal?.confidence,
        approachDeltaMeters = approachSignal?.deltaMeters,
        isInMotion = motionSignal?.isInMotion == true,
        motionSpeedMps = motionSignal?.speedMps,
        motionHeadingDeg = motionSignal?.headingDeg,
        isOwned = owned,
        trackerRisk = trackerRisk
    )
}

private fun latestRemoteIdBroadcastPoint(encounters: List<Encounter>): RemoteIdBroadcastPoint? {
    return encounters
        .asSequence()
        .filter { it.source == EncounterSource.REMOTE_ID }
        .sortedByDescending { it.timestampEpochMs }
        .mapNotNull { encounter ->
            remoteIdBroadcastLatLon(encounter)?.let { (lat, lon) ->
                RemoteIdBroadcastPoint(
                    lat = lat,
                    lon = lon,
                    timestampEpochMs = encounter.timestampEpochMs
                )
            }
        }
        .firstOrNull()
}

private fun remoteIdBroadcastLatLon(encounter: Encounter): Pair<Double, Double>? {
    if (encounter.source != EncounterSource.REMOTE_ID) return null
    val payload = runCatching { JSONObject(encounter.rawPayloadJson) }.getOrNull() ?: return null

    val decoded = payload.optJSONObject("remoteIdDecoded")
    val lat = decoded.optDoubleOrNull("droneLat")
        ?: payload.optDoubleOrNull("droneLat")
        ?: decoded.optDoubleOrNull("lat")
        ?: payload.optDoubleOrNull("lat")
    val lon = decoded.optDoubleOrNull("droneLon")
        ?: payload.optDoubleOrNull("droneLon")
        ?: decoded.optDoubleOrNull("lon")
        ?: payload.optDoubleOrNull("lon")

    if (!isValidLatLon(lat, lon)) return null
    return lat!! to lon!!
}

private fun extractWearPointLatLon(encounter: Encounter): Pair<Double, Double>? {
    if (isValidLatLon(encounter.lat, encounter.lon)) {
        return encounter.lat!! to encounter.lon!!
    }

    if (encounter.source == EncounterSource.REMOTE_ID) {
        remoteIdBroadcastLatLon(encounter)?.let { return it }
    }

    val payload = runCatching { JSONObject(encounter.rawPayloadJson) }.getOrNull()
    val decoded = payload?.optJSONObject("remoteIdDecoded")

    val candidateLat = listOfNotNull(
        payload.optDoubleOrNull("lat"),
        payload.optDoubleOrNull("latitude"),
        payload.optDoubleOrNull("droneLat"),
        payload.optDoubleOrNull("towerLat"),
        decoded.optDoubleOrNull("lat"),
        decoded.optDoubleOrNull("latitude"),
        decoded.optDoubleOrNull("droneLat")
    ).firstOrNull { it.isFinite() }

    val candidateLon = listOfNotNull(
        payload.optDoubleOrNull("lon"),
        payload.optDoubleOrNull("lng"),
        payload.optDoubleOrNull("longitude"),
        payload.optDoubleOrNull("droneLon"),
        payload.optDoubleOrNull("towerLon"),
        decoded.optDoubleOrNull("lon"),
        decoded.optDoubleOrNull("lng"),
        decoded.optDoubleOrNull("longitude"),
        decoded.optDoubleOrNull("droneLon")
    ).firstOrNull { it.isFinite() }

    return if (isValidLatLon(candidateLat, candidateLon)) {
        candidateLat!! to candidateLon!!
    } else {
        null
    }
}

private fun buildWearDevicePoints(encounters: List<Encounter>): List<WearDevicePoint> {
    if (encounters.isEmpty()) return emptyList()

    return encounters
        .groupBy { "${it.source.name}|${it.primaryId}" }
        .values
        .mapNotNull { deviceEncounters ->
            val latest = deviceEncounters.maxByOrNull { it.timestampEpochMs } ?: return@mapNotNull null
            val resolvedLatLon = when (latest.source) {
                EncounterSource.REMOTE_ID -> {
                    latestRemoteIdBroadcastPoint(deviceEncounters)?.let { it.lat to it.lon }
                        ?: extractWearPointLatLon(latest)
                }

                EncounterSource.WIFI,
                EncounterSource.BLUETOOTH_LE -> {
                    inferLikelyDeviceLocation(deviceEncounters)
                        ?.let { it.lat to it.lon }
                        ?: extractWearPointLatLon(latest)
                }

                else -> extractWearPointLatLon(latest)
            } ?: return@mapNotNull null

            val shortId = latest.primaryId.takeLast(6)
            WearDevicePoint(
                label = "${latest.source.name} $shortId",
                lat = resolvedLatLon.first,
                lon = resolvedLatLon.second,
                timestampEpochMs = latest.timestampEpochMs
            )
        }
        .sortedByDescending { it.timestampEpochMs }
        .take(40)
}

private fun JSONObject?.optDoubleOrNull(key: String): Double? {
    val obj = this ?: return null
    if (!obj.has(key) || obj.isNull(key)) return null
    val value = obj.optDouble(key, Double.NaN)
    if (!value.isFinite()) return null
    return value
}

@Composable
private fun DeviceSortDropdown(
    selectedSort: DeviceSortMode,
    onSortSelected: (DeviceSortMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Sort", fontWeight = FontWeight.Medium)
        Button(onClick = { expanded = true }) {
            val label = when (selectedSort) {
                DeviceSortMode.LAST_SEEN -> "Last Seen"
                DeviceSortMode.MOST_SEEN -> "Most Seen"
            }
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Last Seen") },
                onClick = {
                    onSortSelected(DeviceSortMode.LAST_SEEN)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Most Seen") },
                onClick = {
                    onSortSelected(DeviceSortMode.MOST_SEEN)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun ScopeFilterDropdown(
    selectedScope: DataScope,
    onScopeSelected: (DataScope) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Data Scope", fontWeight = FontWeight.Medium)
        Button(onClick = { expanded = true }) {
            Text(if (selectedScope == DataScope.RECENT_100) "Recent (100)" else "All")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Recent (100)") },
                onClick = {
                    onScopeSelected(DataScope.RECENT_100)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("All") },
                onClick = {
                    onScopeSelected(DataScope.ALL)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun DeviceDetailPage(
    item: DeviceItem?,
    deviceEncounters: List<Encounter>,
    initialPinnedLat: Double?,
    initialPinnedLon: Double?,
    initialPinnedTimestampEpochMs: Long?,
    onBack: () -> Unit,
    onOwnedChanged: (source: String, primaryId: String, owned: Boolean) -> Unit
) {
    val context = LocalContext.current
    val currentLocation = remember { LocationSnapshotProvider.read(context) }
    var isOwnedState by remember(item?.source, item?.primaryId) { mutableStateOf(item?.isOwned == true) }
    var realtimeMapEnabled by rememberSaveable(item?.source, item?.primaryId) { mutableStateOf(false) }
    var pinnedMapLat by rememberSaveable(item?.source, item?.primaryId, initialPinnedLat) {
        mutableStateOf(initialPinnedLat ?: item?.lastLat)
    }
    var pinnedMapLon by rememberSaveable(item?.source, item?.primaryId, initialPinnedLon) {
        mutableStateOf(initialPinnedLon ?: item?.lastLon)
    }
    var pinnedMapTimestampEpochMs by rememberSaveable(item?.source, item?.primaryId, initialPinnedTimestampEpochMs) {
        mutableStateOf(initialPinnedTimestampEpochMs ?: item?.lastSeenEpochMs)
    }
    var mapLocationMethod by rememberSaveable(item?.source, item?.primaryId) { mutableStateOf<String?>(null) }
    var mapApproximateRangeMeters by rememberSaveable(item?.source, item?.primaryId) { mutableStateOf<Double?>(null) }

    LaunchedEffect(realtimeMapEnabled, item?.lastLat, item?.lastLon) {
        if (realtimeMapEnabled) {
            pinnedMapLat = item?.lastLat
            pinnedMapLon = item?.lastLon
            pinnedMapTimestampEpochMs = item?.lastSeenEpochMs
            mapLocationMethod = "Live/latest device sample"
            mapApproximateRangeMeters = null
        }
    }

    LaunchedEffect(item?.source, item?.primaryId, deviceEncounters, realtimeMapEnabled) {
        if (item == null || deviceEncounters.isEmpty()) return@LaunchedEffect
        if (realtimeMapEnabled) return@LaunchedEffect

        val sourceEnum = runCatching { EncounterSource.valueOf(item.source) }
            .getOrDefault(EncounterSource.UNKNOWN_RF)
        val resolved = resolveDeviceLocation(
            source = sourceEnum,
            encounters = deviceEncounters
        )
        if (resolved != null) {
            pinnedMapLat = resolved.lat
            pinnedMapLon = resolved.lon
            pinnedMapTimestampEpochMs = resolved.resolvedFromTimestampEpochMs
            mapLocationMethod = resolved.method
            mapApproximateRangeMeters = resolved.approximateRangeMeters
        }
    }

    val source = remember(item?.source) {
        runCatching { EncounterSource.valueOf(item?.source ?: "") }
            .getOrDefault(EncounterSource.UNKNOWN_RF)
    }
    val deviceEncounter = remember(item, source) {
        item?.let {
            Encounter(
                timestampEpochMs = it.lastSeenEpochMs,
                source = source,
                primaryId = it.primaryId,
                secondaryId = it.secondaryId,
                rssiDbm = it.lastRssiDbm,
                frequencyMhz = it.lastFrequencyMhz,
                lat = it.lastLat,
                lon = it.lastLon,
                rawPayloadJson = it.lastRawPayloadJson ?: "{}"
            )
        }
    }
    val topSpeedRecordMps = remember(item?.source, item?.primaryId) {
        if (item == null) {
            null
        } else {
            DeviceSpeedRecordStore.getRecordSpeedMps(context, item.source, item.primaryId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack) {
            Text("Back")
        }
        Text("Device Details", style = MaterialTheme.typography.headlineMedium)
        if (item == null) {
            Text("Device not found in current encounter window.")
            return
        }
        ResponsiveDetailColumns(
            left = {
                DetailRow("Source", listSourceLabel(item.source, item.secondaryId))
                DetailRow("Data Origin", provenanceLabel(item.lastProvenance, item.lastProvenanceNodeId))
                ProvenanceGraphSection(
                    provenance = item.lastProvenance,
                    provenanceNodeId = item.lastProvenanceNodeId,
                    provenanceOriginNodeId = item.lastProvenanceOriginNodeId,
                    provenancePathNodeIds = item.lastProvenancePathNodeIds,
                    provenanceReceivedAtEpochMs = item.lastProvenanceReceivedAtEpochMs,
                    provenanceHopCount = item.lastProvenanceHopCount,
                    localNodeId = ScanSettings.getChainNodeId(context)
                )
                DetailRow("Primary ID", item.primaryId)
                DetailRow("Secondary ID", item.secondaryId ?: "n/a")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Mark as My Device", fontWeight = FontWeight.Medium)
                        Switch(
                            checked = isOwnedState,
                            onCheckedChange = { enabled ->
                                isOwnedState = enabled
                                onOwnedChanged(item.source, item.primaryId, enabled)
                            }
                        )
                    }
                }
                DetailRow("Seen Count", item.seenCount.toString())
                DetailRow("Last Seen", formatEpoch(item.lastSeenEpochMs))
                DetailRow(
                    "Approach Status",
                    if (item.isApproaching) {
                        val confidencePct = ((item.approachConfidence ?: 0.0) * 100.0).toInt()
                        val deltaLabel = item.approachDeltaMeters
                            ?.let { formatDistanceFeetMiles(it.coerceAtLeast(0.0)) }
                            ?: "n/a"
                        "Approaching (${confidencePct}% confidence, trend ${deltaLabel})"
                    } else {
                        "Not approaching"
                    }
                )
                DetailRow("Last RSSI", item.lastRssiDbm?.toString() ?: "n/a")
                DetailRow("Last Frequency", item.lastFrequencyMhz?.toString() ?: "n/a")
                DetailRow(
                    "Motion Status",
                    when {
                        item.motionSpeedMps == null || item.motionHeadingDeg == null -> "Insufficient motion samples"
                        item.isInMotion -> "In motion"
                        else -> "Not moving"
                    }
                )
                if (item.motionSpeedMps != null) {
                    DetailRow("Estimated Speed", formatSpeedLabel(item.motionSpeedMps))
                }
                if (item.motionHeadingDeg != null) {
                    DetailRow("Estimated Direction", formatHeadingCardinal(item.motionHeadingDeg))
                }
            },

            right = {
                DetailRow(
                    "Top Speed Record",
                    topSpeedRecordMps
                        ?.let(::formatSpeedLabel)
                        ?: "n/a"
                )
                DetailRow(
                    "Tracker Risk",
                    when (item.trackerRisk?.level) {
                        TrackerRiskLevel.HIGH -> "HIGH"
                        TrackerRiskLevel.MEDIUM -> "MEDIUM"
                        TrackerRiskLevel.LOW -> "LOW"
                        else -> "NONE"
                    }
                )
                if (item.trackerRisk != null) {
                    DetailRow("Tracker Confidence", String.format(Locale.US, "%.0f%%", item.trackerRisk.confidence * 100.0))
                    DetailRow("Cross-Location Cells", item.trackerRisk.uniqueLocationCells.toString())
                    DetailRow("Observed Spread", formatDistanceFeetMiles(item.trackerRisk.spreadMeters))
                    DetailRow("Observed Window", String.format(Locale.US, "%.1f min", item.trackerRisk.activeWindowMinutes))
                    DetailRow("Assessment", item.trackerRisk.summary)
                }
                DetailRow(
                    "Last Device Location",
                    if (isValidLatLon(item.lastLat, item.lastLon)) {
                        String.format(Locale.US, "%.6f, %.6f", item.lastLat!!, item.lastLon!!)
                    } else {
                        "n/a"
                    }
                )
                if (!mapLocationMethod.isNullOrBlank()) {
                    DetailRow("Map Location Method", mapLocationMethod!!)
                }
                if (mapApproximateRangeMeters != null) {
                    DetailRow("Map Approx Range", formatDistanceFeetMiles(mapApproximateRangeMeters!!))
                }
                DeviceDetailMapSection(
                    source = item.source,
                    primaryId = item.primaryId,
                    lat = pinnedMapLat,
                    lon = pinnedMapLon,
                    currentLocation = currentLocation,
                    lastSeenEpochMs = pinnedMapTimestampEpochMs ?: item.lastSeenEpochMs,
                    realtimeEnabled = realtimeMapEnabled,
                    onRealtimeEnabledChanged = { enabled ->
                        realtimeMapEnabled = enabled
                        if (enabled) {
                            pinnedMapLat = item.lastLat
                            pinnedMapLon = item.lastLon
                            pinnedMapTimestampEpochMs = item.lastSeenEpochMs
                        }
                    }
                )

                if (!item.lastRawPayloadJson.isNullOrBlank() && deviceEncounter != null) {
                    SourceSpecificDetailsSection(encounter = deviceEncounter, currentLocation = currentLocation)
                }
            }
        )
    }
}

@Composable
private fun EncounterDetailPage(
    encounter: Encounter?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentLocation = remember { LocationSnapshotProvider.read(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack) {
            Text("Back")
        }
        Text("Encounter Details", style = MaterialTheme.typography.headlineMedium)
        if (encounter == null) {
            Text("Encounter not found in current encounter window.")
            return
        }
        ResponsiveDetailColumns(
            left = {
                DetailRow("Source", encounter.source.name)
                DetailRow("Data Origin", provenanceLabel(encounter.provenance, encounter.provenanceNodeId))
                ProvenanceGraphSection(
                    provenance = encounter.provenance,
                    provenanceNodeId = encounter.provenanceNodeId,
                    provenanceOriginNodeId = encounter.provenanceOriginNodeId,
                    provenancePathNodeIds = encounter.provenancePathNodeIds,
                    provenanceReceivedAtEpochMs = encounter.provenanceReceivedAtEpochMs,
                    provenanceHopCount = encounter.provenanceHopCount,
                    localNodeId = ScanSettings.getChainNodeId(context)
                )
                DetailRow("Primary ID", encounter.primaryId)
                DetailRow("Secondary ID", encounter.secondaryId ?: "n/a")
                DetailRow("Timestamp", formatEpoch(encounter.timestampEpochMs))
            },

            right = {
                DetailRow("RSSI", encounter.rssiDbm?.toString() ?: "n/a")
                DetailRow("Frequency", encounter.frequencyMhz?.toString() ?: "n/a")
                DetailRow(
                    "Encounter Location",
                    if (isValidLatLon(encounter.lat, encounter.lon)) {
                        String.format(Locale.US, "%.6f, %.6f", encounter.lat!!, encounter.lon!!)
                    } else {
                        "n/a"
                    }
                )
                DetailRow(
                    "My Location Snapshot",
                    if (currentLocation != null && isValidLatLon(currentLocation.lat, currentLocation.lon)) {
                        String.format(Locale.US, "%.6f, %.6f", currentLocation.lat, currentLocation.lon)
                    } else {
                        "n/a"
                    }
                )
                EncounterDetailMapSection(
                    encounter = encounter,
                    currentLocation = currentLocation
                )
                if (encounter.rawPayloadJson.isNotBlank()) {
                    SourceSpecificDetailsSection(encounter = encounter, currentLocation = currentLocation)
                }
                DetailRow("Payload", encounter.rawPayloadJson)
            }
        )
    }
}

@Composable
private fun EncounterDetailMapSection(
    encounter: Encounter,
    currentLocation: DetectionLocation?
) {
    var zoomControlsEnabled by rememberSaveable(encounter.source.name, encounter.primaryId) { mutableStateOf(true) }
    var compassEnabled by rememberSaveable(encounter.source.name, encounter.primaryId) { mutableStateOf(true) }
    var mapToolbarEnabled by rememberSaveable(encounter.source.name, encounter.primaryId) { mutableStateOf(false) }

    val foreignBasePoint = remember(encounter.lat, encounter.lon) {
        if (isValidLatLon(encounter.lat, encounter.lon)) LatLng(encounter.lat!!, encounter.lon!!) else null
    }
    val localBasePoint = remember(currentLocation) {
        if (currentLocation != null && isValidLatLon(currentLocation.lat, currentLocation.lon)) {
            LatLng(currentLocation.lat, currentLocation.lon)
        } else {
            null
        }
    }
    val separatedPoints = remember(foreignBasePoint, localBasePoint) {
        separateOverlappingPins(
            primary = foreignBasePoint,
            secondary = localBasePoint,
            overlapThresholdFeet = 6.0,
            separationFeet = 2.5
        )
    }
    val foreignPoint = separatedPoints.first
    val localPoint = separatedPoints.second
    val fallbackLatLng = remember { LatLng(37.4219999, -122.0840575) }
    val mapPoints = remember(foreignPoint, localPoint) {
        listOfNotNull(foreignPoint, localPoint)
    }
    val centerPoint = remember(mapPoints) {
        when {
            mapPoints.isEmpty() -> fallbackLatLng
            mapPoints.size == 1 -> mapPoints.first()
            else -> {
                val avgLat = mapPoints.map { it.latitude }.average()
                val avgLon = mapPoints.map { it.longitude }.average()
                LatLng(avgLat, avgLon)
            }
        }
    }
    val zoomLevel = if (mapPoints.size <= 1) 16f else 14f
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(centerPoint, zoomLevel)
    }

    LaunchedEffect(centerPoint, zoomLevel) {
        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(centerPoint, zoomLevel))
    }

    fun focusMap(points: List<LatLng>) {
        when (points.size) {
            0 -> cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(fallbackLatLng, 12f))
            1 -> cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(points.first(), 16f))
            else -> {
                val avgLat = points.map { it.latitude }.average()
                val avgLon = points.map { it.longitude }.average()
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(avgLat, avgLon), 14f))
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Encounter Map", fontWeight = FontWeight.Bold)
            Text("Shows foreign device encounter pin and your local snapshot pin when available.")
            Text("When pins overlap, each is shifted by ~2.5 ft for visibility.")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Zoom controls")
                Switch(checked = zoomControlsEnabled, onCheckedChange = { zoomControlsEnabled = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Compass")
                Switch(checked = compassEnabled, onCheckedChange = { compassEnabled = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Map toolbar")
                Switch(checked = mapToolbarEnabled, onCheckedChange = { mapToolbarEnabled = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { foreignPoint?.let { focusMap(listOf(it)) } },
                    enabled = foreignPoint != null
                ) {
                    Text("Focus Device")
                }
                Button(
                    onClick = { localPoint?.let { focusMap(listOf(it)) } },
                    enabled = localPoint != null
                ) {
                    Text("Focus Me")
                }
                Button(
                    onClick = { focusMap(mapPoints) },
                    enabled = mapPoints.isNotEmpty()
                ) {
                    Text("Focus Both")
                }
            }

            if (foreignPoint == null && localPoint == null) {
                Text("No valid coordinates available for either pin.")
            } else {
                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = zoomControlsEnabled,
                        compassEnabled = compassEnabled,
                        mapToolbarEnabled = mapToolbarEnabled
                    )
                ) {
                    foreignPoint?.let { point ->
                        Marker(
                            state = MarkerState(position = point),
                            title = "Foreign device encounter",
                            snippet = "${encounter.source.name} • ${encounter.primaryId}",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                        )
                    }
                    localPoint?.let { point ->
                        Marker(
                            state = MarkerState(position = point),
                            title = "My location snapshot",
                            snippet = "Local observer position",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponsiveDetailColumns(
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val twoColumn = maxWidth >= DETAIL_TWO_COLUMN_MIN_WIDTH
        if (twoColumn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = left
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = right
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                left()
                right()
            }
        }
    }
}

@Composable
private fun DeviceDetailMapSection(
    source: String,
    primaryId: String,
    lat: Double?,
    lon: Double?,
    currentLocation: DetectionLocation?,
    lastSeenEpochMs: Long,
    realtimeEnabled: Boolean,
    onRealtimeEnabledChanged: (Boolean) -> Unit
) {
    var zoomControlsEnabled by rememberSaveable(source, primaryId) { mutableStateOf(true) }
    var compassEnabled by rememberSaveable(source, primaryId) { mutableStateOf(true) }
    var mapToolbarEnabled by rememberSaveable(source, primaryId) { mutableStateOf(false) }

    val hasFix = isValidLatLon(lat, lon)
    val markerBaseLatLng = remember(lat, lon, hasFix) {
        if (hasFix) {
            LatLng(lat!!, lon!!)
        } else {
            null
        }
    }
    val localBasePoint = remember(currentLocation) {
        if (currentLocation != null && isValidLatLon(currentLocation.lat, currentLocation.lon)) {
            LatLng(currentLocation.lat, currentLocation.lon)
        } else {
            null
        }
    }
    val separatedPoints = remember(markerBaseLatLng, localBasePoint) {
        separateOverlappingPins(
            primary = markerBaseLatLng,
            secondary = localBasePoint,
            overlapThresholdFeet = 6.0,
            separationFeet = 2.5
        )
    }
    val markerLatLng = separatedPoints.first
    val localPoint = separatedPoints.second

    val fallbackLatLng = remember { LatLng(37.4219999, -122.0840575) }
    val mapPoints = remember(markerLatLng, localPoint) { listOfNotNull(markerLatLng, localPoint) }
    val hasDeviceFix = markerLatLng != null
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(markerLatLng ?: localPoint ?: fallbackLatLng, if (hasFix) 16f else 12f)
    }

    LaunchedEffect(markerLatLng, localPoint, realtimeEnabled) {
        if (realtimeEnabled) {
            val target = markerLatLng ?: localPoint
            target?.let {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 16f))
            }
        }
    }

    fun focusMap(points: List<LatLng>) {
        when (points.size) {
            0 -> cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(fallbackLatLng, 12f))
            1 -> cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(points.first(), 16f))
            else -> {
                val avgLat = points.map { it.latitude }.average()
                val avgLon = points.map { it.longitude }.average()
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(avgLat, avgLon), 14f))
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Device Location Map", fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Real-time updates")
                Switch(
                    checked = realtimeEnabled,
                    onCheckedChange = onRealtimeEnabledChanged
                )
            }
            Text(
                if (realtimeEnabled) {
                    "Map follows latest fixes while this page is open."
                } else {
                    "Map is pinned. Enable real-time to follow incoming updates."
                }
            )
            Text("When local/device pins overlap, each is shifted by ~2.5 ft for visibility.")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Zoom controls")
                Switch(checked = zoomControlsEnabled, onCheckedChange = { zoomControlsEnabled = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Compass")
                Switch(checked = compassEnabled, onCheckedChange = { compassEnabled = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Map toolbar")
                Switch(checked = mapToolbarEnabled, onCheckedChange = { mapToolbarEnabled = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { markerLatLng?.let { focusMap(listOf(it)) } },
                    enabled = markerLatLng != null
                ) {
                    Text("Focus Device")
                }
                Button(
                    onClick = { localPoint?.let { focusMap(listOf(it)) } },
                    enabled = localPoint != null
                ) {
                    Text("Focus Me")
                }
                Button(
                    onClick = { focusMap(mapPoints) },
                    enabled = mapPoints.isNotEmpty()
                ) {
                    Text("Focus Both")
                }
            }

            if (!hasDeviceFix && localPoint == null) {
                Text("No valid device location available yet.")
            } else {
                markerLatLng?.let {
                    Text(
                        "${String.format(Locale.US, "%.6f, %.6f", it.latitude, it.longitude)} • ${formatEpoch(lastSeenEpochMs)}"
                    )
                }
                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = zoomControlsEnabled,
                        compassEnabled = compassEnabled,
                        mapToolbarEnabled = mapToolbarEnabled
                    )
                ) {
                    markerLatLng?.let { point ->
                        Marker(
                            state = MarkerState(position = point),
                            title = "$source • $primaryId",
                            snippet = formatEpoch(lastSeenEpochMs),
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                        )
                    }
                    localPoint?.let { point ->
                        Marker(
                            state = MarkerState(position = point),
                            title = "My location snapshot",
                            snippet = "Local observer position",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                        )
                    }
                }
            }
        }
    }
}

private fun separateOverlappingPins(
    primary: LatLng?,
    secondary: LatLng?,
    overlapThresholdFeet: Double,
    separationFeet: Double
): Pair<LatLng?, LatLng?> {
    if (primary == null || secondary == null) return primary to secondary

    val distanceMeters = FloatArray(1)
    android.location.Location.distanceBetween(
        primary.latitude,
        primary.longitude,
        secondary.latitude,
        secondary.longitude,
        distanceMeters
    )

    val thresholdMeters = overlapThresholdFeet * 0.3048
    if (distanceMeters[0].toDouble() > thresholdMeters) return primary to secondary

    val offsetMeters = separationFeet * 0.3048
    val shiftedPrimary = offsetLatLng(primary, offsetMeters, 90.0)
    val shiftedSecondary = offsetLatLng(secondary, offsetMeters, 270.0)
    return shiftedPrimary to shiftedSecondary
}

@Composable
private fun SourceSpecificDetailsSection(
    encounter: Encounter,
    currentLocation: DetectionLocation?
) {
    val scope = rememberCoroutineScope()
    // Keep lookup result stable while viewing a device/encounter identity, even if fresh scans update timestamps.
    var lookupInProgress by remember(encounter.source, encounter.primaryId) { mutableStateOf(false) }
    var lookupResult by remember(encounter.source, encounter.primaryId) { mutableStateOf<TowerLookupResult?>(null) }
    var autoLookupAttempted by remember(encounter.source, encounter.primaryId) { mutableStateOf(false) }
    val details = remember(encounter) { sourceSpecificDetails(encounter) }
    val sectionTitle = details.first
    val sectionFields = details.second

    LaunchedEffect(encounter.source, encounter.primaryId) {
        if (encounter.source != EncounterSource.CELL) return@LaunchedEffect
        if (autoLookupAttempted || lookupInProgress || lookupResult != null) return@LaunchedEffect

        autoLookupAttempted = true
        lookupInProgress = true
        lookupResult = CellTowerLookupService.lookup(encounter)
        lookupInProgress = false
    }

    Text(sectionTitle, fontWeight = FontWeight.Bold)
    if (sectionFields.isEmpty()) {
        Text("No parsed source-specific fields available for this encounter.")
    } else {
        sectionFields.forEach { (label, value) ->
            DetailRow(label, value)
        }
    }

    if (encounter.source == EncounterSource.WIFI || encounter.source == EncounterSource.BLUETOOTH_LE) {
        val estimatedRange = estimateRangeMeters(encounter)
        DetailRow(
            "Approx Range",
            estimatedRange?.let(::formatDistanceFeetMiles) ?: "n/a"
        )
    }

    if (encounter.source == EncounterSource.CELL) {
        Button(
            enabled = !lookupInProgress,
            onClick = {
                scope.launch {
                    lookupInProgress = true
                    lookupResult = CellTowerLookupService.lookup(encounter)
                    lookupInProgress = false
                }
            }
        ) {
            val label = when {
                lookupInProgress -> "Looking up tower..."
                lookupResult != null -> "Refresh Tower Estimate"
                else -> "Estimate Tower Location"
            }
            Text(label)
        }

        when (val result = lookupResult) {
            is TowerLookupResult.Success -> {
                val estimate = result.estimate
                DetailRow(
                    "Estimated Tower Location",
                    String.format(Locale.US, "%.6f, %.6f", estimate.latitude, estimate.longitude)
                )
                DetailRow(
                    "Lookup Accuracy",
                    estimate.accuracyMeters?.let { String.format(Locale.US, "~%.0f m", it) } ?: "n/a"
                )
                DetailRow("Lookup Provider", estimate.provider)
                if (currentLocation != null) {
                    DetailRow(
                        "Range From Device",
                        formatTowerRangeFeetMiles(
                            fromLat = currentLocation.lat,
                            fromLon = currentLocation.lon,
                            toLat = estimate.latitude,
                            toLon = estimate.longitude
                        )
                    )
                }
            }

            is TowerLookupResult.Failure -> {
                DetailRow("Tower Lookup", "Failed: ${result.reason}")
            }

            null -> Unit
        }
    }
}

@Composable
private fun SourceFilterDropdown(
    selectedSource: String?,
    sourceOptions: List<String>,
    onSourceSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Source Filter", fontWeight = FontWeight.Medium)
        Button(onClick = { expanded = true }) {
            Text(selectedSource ?: "All Sources")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All Sources") },
                onClick = {
                    onSourceSelected(null)
                    expanded = false
                }
            )
            sourceOptions.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source) },
                    onClick = {
                        onSourceSelected(source)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(value)
        }
    }
}

private fun formatEpoch(epochMs: Long): String {
    val instant = Instant.ofEpochMilli(epochMs)
    return timeFormatter.format(instant.atZone(ZoneId.systemDefault()))
}
