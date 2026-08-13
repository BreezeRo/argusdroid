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
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
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
import dev.argus.tracker.data.chain.ChainMeshSnapshot
import dev.argus.tracker.data.chain.ChainPeerState
import dev.argus.tracker.data.chain.MeshForegroundServiceController
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterProvenance
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.sensing.CellTowerLookupService
import dev.argus.tracker.sensing.DetectionLocation
import dev.argus.tracker.sensing.LocationSnapshotProvider
import dev.argus.tracker.sensing.SensorStatus
import dev.argus.tracker.sensing.SensorStatusProvider
import dev.argus.tracker.sensing.TowerLookupResult
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
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

private const val HOME_ROUTE = "home"
private const val SETTINGS_ROUTE = "settings"
private const val DETECTION_ROUTE = "detection"
private const val DEVICES_ENCOUNTERS_ROUTE = "devicesEncounters"
private const val APPROACH_ALERT_MAP_ROUTE = "approachAlertMap/{source}/{primaryId}"
private const val MOVING_DEVICE_PATH_ROUTE = "movingDevicePath/{source}/{primaryId}"
private const val DEVICE_DETAIL_ROUTE = "deviceDetail/{source}/{primaryId}"
private const val ENCOUNTER_DETAIL_ROUTE = "encounterDetail/{source}/{primaryId}/{timestamp}"

private val topLevelRoutes = setOf(HOME_ROUTE, DETECTION_ROUTE, DEVICES_ENCOUNTERS_ROUTE, SETTINGS_ROUTE)
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private const val APPROACH_ALERT_CHANNEL_ID = "argus_approach_alerts"
private const val APPROACH_ALERT_COOLDOWN_MS = 2 * 60 * 1000L
private const val TRACKER_ALERT_CHANNEL_ID = "argus_tracker_alerts"
private const val TRACKER_ALERT_COOLDOWN_MS = 5 * 60 * 1000L
private const val ALERT_LOG_MAX_ENTRIES = 400
private const val ACTION_OPEN_APPROACH_MAP = "dev.argus.tracker.action.OPEN_APPROACH_MAP"
private const val EXTRA_APPROACH_SOURCE = "extra_approach_source"
private const val EXTRA_APPROACH_PRIMARY_ID = "extra_approach_primary_id"

private enum class AlertLogType {
    APPROACH,
    TRACKER
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
    val remoteIdEnabled: Boolean
)

private data class InferredDeviceLocation(
    val lat: Double,
    val lon: Double,
    val estimatedRangeMeters: Double?
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
        remoteIdEnabled = ScanSettings.isRemoteIdSensorEnabled(context)
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
    var chainLinkEnabled by remember { mutableStateOf(ScanSettings.isChainLinkEnabled(context)) }
    var chainNodeId by remember { mutableStateOf(ScanSettings.getChainNodeId(context)) }
    var chainDeviceName by remember { mutableStateOf(ScanSettings.getChainDeviceName(context)) }
    var chainSharedSecret by remember { mutableStateOf(ScanSettings.getChainSharedSecret(context)) }
    var chainAutoSyncEnabled by remember { mutableStateOf(ScanSettings.isChainAutoSyncEnabled(context)) }
    var chainAutoSyncIntervalSeconds by remember { mutableStateOf(ScanSettings.getChainAutoSyncIntervalSeconds(context)) }
    var chainPersistentChannelEnabled by remember { mutableStateOf(ScanSettings.isChainPersistentChannelEnabled(context)) }
    var chainHeartbeatIntervalSeconds by remember { mutableStateOf(ScanSettings.getChainHeartbeatIntervalSeconds(context)) }
    var chainSharePreciseLocationEnabled by remember { mutableStateOf(ScanSettings.isChainSharePreciseLocationEnabled(context)) }
    var ownedDeviceKeys by remember { mutableStateOf(OwnedDeviceRegistry.read(context)) }
    var alertLogs by remember { mutableStateOf(AlertLogStore.read(context)) }
    var lastScanDurationMs by remember { mutableStateOf(ScanSettings.getLastScanDurationMs(context)) }
    var sourceScanTimings by remember { mutableStateOf(ScanSettings.getSourceScanTimings(context)) }
    var autoAdjustScanIntervalEnabled by remember { mutableStateOf(ScanSettings.isAutoAdjustScanIntervalEnabled(context)) }
    var scanIntervalChangeEvents by remember { mutableStateOf(ScanSettings.getScanIntervalChangeEvents(context, 10)) }
    var autoAdjustConsecutiveOverruns by remember { mutableStateOf(mapOf<String, Int>()) }
    var autoAdjustStableCycles by remember { mutableStateOf(mapOf<String, Int>()) }
    var trackingStartMessage by remember { mutableStateOf<String?>(null) }
    var trackingStartMessageIsError by remember { mutableStateOf(false) }
    val approachStateByDevice = remember { mutableMapOf<String, Boolean>() }
    val lastApproachNotificationEpochByDevice = remember { mutableMapOf<String, Long>() }
    val trackerStateByDevice = remember { mutableMapOf<String, TrackerRiskLevel>() }
    val lastTrackerNotificationEpochByDevice = remember { mutableMapOf<String, Long>() }

    val recent by viewModel.recentEncounters.collectAsState()
    val recent100 by viewModel.recent100Encounters.collectAsState()
    val allEncounters by viewModel.allEncounters.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val chainMesh by app.container.chainLinkCoordinator.observeMesh().collectAsState()
    val lastScanEpochMs = remember(recent) { recent.maxOfOrNull { it.timestampEpochMs } }

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
        liveMapUpdateIntervalSeconds = ScanSettings.getLiveMapUpdateIntervalSeconds(context)
        sourceScanIntervals = ScanSettings.getAllSourceScanIntervalSeconds(context)
        sourceLastScanEpochs = ScanSettings.getAllSourceLastScanEpochMs(context)
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

            val schedulerTick = minimumEnabledSourceIntervalSeconds()
            if (schedulerTick != scanIntervalSeconds) {
                applyScanInterval(schedulerTick, "Auto-adjust", "scheduler-align")
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
                    message = "Approaching ${listSourceLabel(device.source, device.secondaryId)} ${device.primaryId} (${confidencePct}% confidence, trend ${trend})",
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

    LaunchedEffect(allEncounters, ownedDeviceKeys, approachDetectionEnabled, trackerNotificationsEnabled) {
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
                            }
                            sensorGateSettings = readSensorGateSettings(context)
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
                    approachDetectionEnabled = approachDetectionEnabled,
                    approachNotificationsEnabled = approachNotificationsEnabled,
                    trackerNotificationsEnabled = trackerNotificationsEnabled,
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
                                val schedulerTick = minimumEnabledSourceIntervalSeconds()
                                if (schedulerTick != scanIntervalSeconds) {
                                    applyScanInterval(schedulerTick, "Auto-adjust", "auto-bootstrap")
                                }
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
                        scope.launch {
                            val schedulerTick = minimumEnabledSourceIntervalSeconds()
                            if (schedulerTick != scanIntervalSeconds) {
                                applyScanInterval(schedulerTick, "Scheduler alignment", "scheduler-align")
                            }
                        }
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
                    onLiveMapUpdateIntervalSelected = { seconds ->
                        liveMapUpdateIntervalSeconds = seconds
                        ScanSettings.setLiveMapUpdateIntervalSeconds(context, seconds)
                    }
                )
            }

            composable(DETECTION_ROUTE) {
                DetectionPage(
                    readinessItems = readinessItems,
                    encounters = recent,
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
                    onDeviceMapPinClick = { source, primaryId ->
                        navController.navigate(
                            "deviceDetail/${Uri.encode(source)}/${Uri.encode(primaryId)}"
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
                val item = buildDeviceItems(
                    encounters = allEncounters,
                    approachDetectionEnabled = approachDetectionEnabled,
                    ownedDeviceKeys = ownedDeviceKeys
                )
                    .firstOrNull { it.source == source && it.primaryId == primaryId }
                DeviceDetailPage(
                    item = item,
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
                ApproachAlertMapPage(
                    source = source,
                    primaryId = primaryId,
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

@Composable
private fun ApproachAlertMapPage(
    source: String,
    primaryId: String,
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
        Text("Target: ${listSourceLabel(source, null)} $primaryId")
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
                    title = "Approaching device",
                    snippet = "$source $primaryId",
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
    approachDetectionEnabled: Boolean,
    approachNotificationsEnabled: Boolean,
    trackerNotificationsEnabled: Boolean,
    onScanIntervalSelected: (Long) -> Unit,
    onAutoAdjustScanIntervalChanged: (Boolean) -> Unit,
    onSourceScanIntervalSelected: (String, Long) -> Unit,
    onApproachDetectionChanged: (Boolean) -> Unit,
    onApproachNotificationsChanged: (Boolean) -> Unit,
    onTrackerNotificationsChanged: (Boolean) -> Unit,
    onLiveMapUpdateIntervalSelected: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var liveMapIntervalExpanded by remember { mutableStateOf(false) }
    var sourceIntervalExpandedFor by remember { mutableStateOf<String?>(null) }
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
            Text("Scan interval")
        }
        item {
            Text(
                "Current: every ${ScanSettings.formatInterval(scanIntervalSeconds)}",
                fontWeight = FontWeight.Medium
            )
        }
        item {
            Button(onClick = { expanded = true }) {
                Text("Change interval")
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
                    Text("Notifications trigger when a tracked device changes into approaching state.")
                    Text("Tracker alerts trigger when unknown devices show strong cross-location co-movement patterns.")
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
    val localLocation = remember { LocationSnapshotProvider.read(context) }
    val hasLocalLocation = remember(localLocation) {
        localLocation != null && isValidLatLon(localLocation.lat, localLocation.lon)
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
    onDeviceMapPinClick: (source: String, primaryId: String) -> Unit,
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
    onSyncNow: suspend () -> String
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var encounterPinLimit by rememberSaveable { mutableStateOf(1000) }
    var cellDevicePinLimit by rememberSaveable { mutableStateOf(1000) }
    var movingOnlyOnDeviceMap by rememberSaveable { mutableStateOf(false) }
    val tabs = listOf("Readiness", "Device Encounters Map", "Device Location Map", "Alert Logs", "Mesh Network")

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

    val allDeviceCandidates = remember(encounters, approachDetectionEnabled, ownedDeviceKeys) {
        encounters
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

    val deviceLocationLookupKey = remember(allDeviceCandidates) {
        allDeviceCandidates.joinToString(separator = "|") { candidate ->
            "${candidate.source}:${candidate.primaryId}:${candidate.latestTimestampEpochMs}:${candidate.seenCount}"
        }
    }
    var estimatedDeviceLocationPins by remember { mutableStateOf(emptyList<MapPin>()) }

    LaunchedEffect(deviceLocationLookupKey) {
        if (allDeviceCandidates.isEmpty()) {
            estimatedDeviceLocationPins = emptyList()
            return@LaunchedEffect
        }

        val resolvedCandidates = buildList {
            allDeviceCandidates.forEach { candidate ->
                val latest = candidate.encounters.maxByOrNull { it.timestampEpochMs } ?: return@forEach
                val sourceEnum = runCatching { EncounterSource.valueOf(candidate.source) }
                    .getOrDefault(EncounterSource.UNKNOWN_RF)

                val resolvedCandidate = when (sourceEnum) {
                    EncounterSource.CELL -> {
                        val lookup = CellTowerLookupService.lookup(latest)
                        when (lookup) {
                            is TowerLookupResult.Success -> {
                                val estimate = lookup.estimate
                                if (!isValidLatLon(estimate.latitude, estimate.longitude)) {
                                    null
                                } else {
                                    candidate.copy(
                                        approximateLocation = DetectionLocation(estimate.latitude, estimate.longitude),
                                        approximateMethod = estimate.provider,
                                        approximateRangeMeters = null
                                    )
                                }
                            }

                            is TowerLookupResult.Failure -> {
                                if (isValidLatLon(latest.lat, latest.lon)) {
                                    candidate.copy(
                                        approximateLocation = DetectionLocation(latest.lat!!, latest.lon!!),
                                        approximateMethod = "Observed encounter location fallback",
                                        approximateRangeMeters = null
                                    )
                                } else {
                                    null
                                }
                            }
                        }
                    }

                    EncounterSource.WIFI,
                    EncounterSource.BLUETOOTH_LE -> {
                        val inferred = inferLikelyDeviceLocation(candidate.encounters)
                        if (inferred != null && isValidLatLon(inferred.lat, inferred.lon)) {
                            candidate.copy(
                                approximateLocation = DetectionLocation(inferred.lat, inferred.lon),
                                approximateMethod = "Inferred location",
                                approximateRangeMeters = inferred.estimatedRangeMeters
                            )
                        } else if (isValidLatLon(latest.lat, latest.lon)) {
                            candidate.copy(
                                approximateLocation = DetectionLocation(latest.lat!!, latest.lon!!),
                                approximateMethod = "Observed encounter location fallback",
                                approximateRangeMeters = estimateRangeMeters(latest)
                            )
                        } else {
                            null
                        }
                    }

                    else -> {
                        if (isValidLatLon(latest.lat, latest.lon)) {
                            candidate.copy(
                                approximateLocation = DetectionLocation(latest.lat!!, latest.lon!!),
                                approximateMethod = "Observed encounter location",
                                approximateRangeMeters = null
                            )
                        } else {
                            null
                        }
                    }
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
        } else if (selectedTab == 2) {
            val deviceMapPins = if (movingOnlyOnDeviceMap) {
                estimatedDeviceLocationPins.filter { it.motionBadge == "MOVING" }
            } else {
                estimatedDeviceLocationPins
            }
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
                        onDeviceMapPinClick(pin.source, pin.primaryId)
                    }
                },
                liveUpdatesAllowed = true,
                useSourceOnlyPinColors = true,
                showMovingOnlyControl = true,
                movingOnlyEnabled = movingOnlyOnDeviceMap,
                onMovingOnlyEnabledChange = { movingOnlyOnDeviceMap = it },
                onLiveCollect = onLiveCollect,
                liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds
            )
        } else if (selectedTab == 3) {
            DetectionLogsPage(
                logs = alertLogs,
                onClearLogs = onClearAlertLogs,
                onOpenApproachMap = onOpenApproachLogMap
            )
        } else {
            DetectionMeshNetworkPage(
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
                onSyncNow = onSyncNow
            )
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

    val filteredLogs = remember(logs, showApproachLogs, showTrackerLogs) {
        logs.filter { entry ->
            (showApproachLogs && entry.type == AlertLogType.APPROACH) ||
                (showTrackerLogs && entry.type == AlertLogType.TRACKER)
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
    onSyncNow: suspend () -> String
) {
    val context = LocalContext.current
    var chainIntervalExpanded by remember { mutableStateOf(false) }
    var heartbeatExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var syncInProgress by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var refreshInProgress by remember { mutableStateOf(false) }
    var manualLinkRequestInProgress by remember { mutableStateOf(false) }
    var peerLinkRequestInProgress by remember { mutableStateOf(false) }
    var linkHostInput by remember { mutableStateOf("") }
    var linkMessageInput by remember { mutableStateOf("") }
    var meshServiceActive by remember { mutableStateOf(MeshForegroundServiceController.isActive(context)) }

    LaunchedEffect(chainLinkEnabled, chainPersistentChannelEnabled) {
        while (true) {
            meshServiceActive = MeshForegroundServiceController.isActive(context)
            delay(1500)
        }
    }

    val connectedCount = chainMeshSnapshot.peers.count { it.state == ChainPeerState.CONNECTED }
    val unconnectedCount = chainMeshSnapshot.peers.count { it.state != ChainPeerState.CONNECTED }

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
    var autoPositioned by rememberSaveable { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
    }

    LaunchedEffect(currentLocation, visiblePins, hasMapsApiKey, autoPositioned) {
        if (!hasMapsApiKey || autoPositioned) return@LaunchedEffect

        when {
            visiblePins.size > 1 && mapLoaded -> {
                val boundsBuilder = LatLngBounds.Builder()
                visiblePins.forEach { pin -> boundsBuilder.include(pin.position) }
                val bounds = boundsBuilder.build()
                runCatching {
                    cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                }.onFailure {
                    mapError = "Failed to fit pins in view: ${it.message ?: "unknown error"}"
                }
            }

            visiblePins.size == 1 -> {
                runCatching {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(visiblePins.first().position, 13f)
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
                            icon = BitmapDescriptorFactory.defaultMarker(
                                markerHueForPin(pin, useSourceOnlyPinColors)
                            ),
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
        if (sensorGateSettings.remoteIdEnabled) {
            add("remote_id")
            add("uwb")
            add("sdr")
        }
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
    if (sensorGateSettings.remoteIdEnabled) {
        add("remote_id")
        add("uwb")
        add("sdr")
    }
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
    "auto-overrun-wifi" -> "Auto-adjust Wi-Fi overrun protection"
    "auto-overrun-wifi_direct" -> "Auto-adjust Wi-Fi Direct overrun protection"
    "auto-overrun-ble" -> "Auto-adjust Bluetooth LE overrun protection"
    "auto-overrun-bt_classic" -> "Auto-adjust Bluetooth Classic overrun protection"
    "auto-overrun-cellular" -> "Auto-adjust Cellular overrun protection"
    "auto-overrun-remote_id" -> "Auto-adjust Remote ID overrun protection"
    "auto-overrun-uwb" -> "Auto-adjust UWB overrun protection"
    "auto-overrun-sdr" -> "Auto-adjust SDR overrun protection"
    "auto-stable-wifi" -> "Auto-adjust Wi-Fi stable downshift"
    "auto-stable-wifi_direct" -> "Auto-adjust Wi-Fi Direct stable downshift"
    "auto-stable-ble" -> "Auto-adjust Bluetooth LE stable downshift"
    "auto-stable-bt_classic" -> "Auto-adjust Bluetooth Classic stable downshift"
    "auto-stable-cellular" -> "Auto-adjust Cellular stable downshift"
    "auto-stable-remote_id" -> "Auto-adjust Remote ID stable downshift"
    "auto-stable-uwb" -> "Auto-adjust UWB stable downshift"
    "auto-stable-sdr" -> "Auto-adjust SDR stable downshift"
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

private fun sourceSpecificDetails(encounter: Encounter): Pair<String, List<Pair<String, String>>> =
    when (encounter.source) {
        EncounterSource.WIFI -> "Wi-Fi Access Point Details" to readWifiAccessPointFields(encounter.rawPayloadJson)
        EncounterSource.WIFI_DIRECT -> "Wi-Fi Direct Peer Details" to readGenericPayloadFields(encounter.rawPayloadJson)
        EncounterSource.BLUETOOTH_LE -> "Bluetooth LE Device Details" to readBleDeviceFields(encounter.rawPayloadJson)
        EncounterSource.BLUETOOTH_CLASSIC -> "Bluetooth Classic Device Details" to readGenericPayloadFields(encounter.rawPayloadJson)
        EncounterSource.CELL -> "Cell Tower Details" to readCellTowerFields(encounter.rawPayloadJson)
        EncounterSource.REMOTE_ID -> "Remote ID Details" to readGenericPayloadFields(encounter.rawPayloadJson)
        EncounterSource.UWB -> "UWB Device Details" to readGenericPayloadFields(encounter.rawPayloadJson)
        EncounterSource.SDR -> "SDR Device Details" to readGenericPayloadFields(encounter.rawPayloadJson)
        EncounterSource.UNKNOWN_RF -> "Unknown RF Details" to readGenericPayloadFields(encounter.rawPayloadJson)
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

private fun sendApproachNotification(context: android.content.Context, device: DeviceItem) {
    val confidencePct = ((device.approachConfidence ?: 0.0) * 100.0).toInt().coerceIn(0, 100)
    val trend = device.approachDeltaMeters
        ?.takeIf { it > 0.0 }
        ?.let { formatDistanceFeetMiles(it) }
        ?: "unknown"

    val title = "Approaching device detected"
    val content = "${listSourceLabel(device.source, device.secondaryId)} ${device.primaryId} • Confidence ${confidencePct}% • Trend ${trend}"
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
                Text("Owned Only")
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
    val encounters = if (dataScope == DataScope.RECENT_100) recentEncounters else allEncounters
    val sourceOptions = remember(encounters) { encounters.map { it.source.name }.distinct().sorted() }
    val filteredEncounters = remember(encounters, sourceFilter, queryFilter) {
        encounters.filter { encounter ->
            val sourceMatches = sourceFilter == null || encounter.source.name == sourceFilter
            val queryMatches = queryFilter.isBlank() ||
                encounter.primaryId.contains(queryFilter, ignoreCase = true) ||
                (encounter.secondaryId?.contains(queryFilter, ignoreCase = true) == true) ||
                encounter.rawPayloadJson.contains(queryFilter, ignoreCase = true)
            sourceMatches && queryMatches
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
        .map { (key, groupedEncounters) ->
            val latest = groupedEncounters.maxByOrNull { it.timestampEpochMs } ?: groupedEncounters.first()
            val owned = OwnedDeviceRegistry.keyFor(key.first, key.second) in ownedDeviceKeys
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
            DeviceItem(
                source = key.first,
                primaryId = key.second,
                secondaryId = latest.secondaryId,
                seenCount = groupedEncounters.size,
                lastSeenEpochMs = latest.timestampEpochMs,
                lastRssiDbm = latest.rssiDbm,
                lastFrequencyMhz = latest.frequencyMhz,
                lastLat = latest.lat,
                lastLon = latest.lon,
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
        .let { deviceItems ->
            when (sortMode) {
                DeviceSortMode.LAST_SEEN -> deviceItems.sortedByDescending { it.lastSeenEpochMs }
                DeviceSortMode.MOST_SEEN -> deviceItems.sortedWith(
                    compareByDescending<DeviceItem> { it.seenCount }
                        .thenByDescending { it.lastSeenEpochMs }
                )
            }
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
    onBack: () -> Unit,
    onOwnedChanged: (source: String, primaryId: String, owned: Boolean) -> Unit
) {
    val context = LocalContext.current
    val currentLocation = remember { LocationSnapshotProvider.read(context) }
    var isOwnedState by remember(item?.source, item?.primaryId) { mutableStateOf(item?.isOwned == true) }

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
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        DetailRow(
            "Top Speed Record",
            DeviceSpeedRecordStore
                .getRecordSpeedMps(context, item.source, item.primaryId)
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

        if (!item.lastRawPayloadJson.isNullOrBlank()) {
            val source = remember(item.source) {
                runCatching { EncounterSource.valueOf(item.source) }
                    .getOrDefault(EncounterSource.UNKNOWN_RF)
            }
            val deviceEncounter = remember(item, source) {
                Encounter(
                    timestampEpochMs = item.lastSeenEpochMs,
                    source = source,
                    primaryId = item.primaryId,
                    secondaryId = item.secondaryId,
                    rssiDbm = item.lastRssiDbm,
                    frequencyMhz = item.lastFrequencyMhz,
                    lat = item.lastLat,
                    lon = item.lastLon,
                    rawPayloadJson = item.lastRawPayloadJson ?: "{}"
                )
            }
            SourceSpecificDetailsSection(encounter = deviceEncounter, currentLocation = currentLocation)
        }
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
        DetailRow("RSSI", encounter.rssiDbm?.toString() ?: "n/a")
        DetailRow("Frequency", encounter.frequencyMhz?.toString() ?: "n/a")
        if (encounter.rawPayloadJson.isNotBlank()) {
            SourceSpecificDetailsSection(encounter = encounter, currentLocation = currentLocation)
        }
        DetailRow("Payload", encounter.rawPayloadJson)
    }
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
