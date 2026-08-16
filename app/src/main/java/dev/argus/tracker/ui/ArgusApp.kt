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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.AssistChip
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import dev.argus.tracker.MainActivity
import dev.argus.tracker.ArgusApplication
import dev.argus.tracker.data.AppBackupManager
import dev.argus.tracker.data.OwnedSignalRegistry
import dev.argus.tracker.data.OperationalErrorLogEntry
import dev.argus.tracker.data.OperationalErrorLogStore
import dev.argus.tracker.data.chain.ChainMeshSnapshot
import dev.argus.tracker.data.chain.ChainPeerState
import dev.argus.tracker.data.chain.MeshForegroundServiceController
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterProvenance
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SourceCatalog
import dev.argus.tracker.sensing.CellTowerLookupService
import dev.argus.tracker.sensing.DetectionLocation
import dev.argus.tracker.sensing.LocationSnapshotProvider
import dev.argus.tracker.sensing.NoFlyZoneOverlayProvider
import dev.argus.tracker.sensing.RemoteIdForegroundServiceController
import dev.argus.tracker.sensing.SensorStatus
import dev.argus.tracker.sensing.SensorStatusProvider
import dev.argus.tracker.sensing.TowerLookupResult
import dev.argus.tracker.sensing.AviationPerfStatsStore
import dev.argus.tracker.sensing.remoteid.RemoteIdPayloadParser
import dev.argus.tracker.sensing.remoteid.RemoteIdParseConfidence
import dev.argus.tracker.wear.WearDevicePoint
import dev.argus.tracker.wear.WearStatusBridgePublisher
import dev.argus.tracker.worker.ScanSettings
import dev.argus.tracker.worker.WorkScheduler
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private enum class DeviceSortMode {
    LAST_SEEN,
    MOST_SEEN
}

private const val DETECTION_LIST_PAGE_SIZE = 250

private enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

private const val HOME_ROUTE = "home"
private const val SETTINGS_ROUTE = "settings"
private const val DETECTION_ROUTE = "detection"
private const val LOGS_ROUTE = "logs"
private const val LOGS_ENCOUNTERS_ROUTE = "logsEncounters"
private const val DEVICES_ENCOUNTERS_ROUTE = "devicesEncounters"
private const val APPROACH_ALERT_MAP_ROUTE = "approachAlertMap/{source}/{primaryId}"
private const val MOVING_DEVICE_PATH_ROUTE = "movingDevicePath/{source}/{primaryId}"
private const val NO_FLY_INCIDENT_PATH_ROUTE = "noFlyIncidentPath/{source}/{primaryId}/{zoneSummary}/{eventEpochMs}?lat={lat}&lon={lon}&zoneIds={zoneIds}"
private const val DEVICE_DETAIL_ROUTE = "deviceDetail/{source}/{primaryId}?lat={lat}&lon={lon}&ts={ts}"
private const val ENCOUNTER_DETAIL_ROUTE = "encounterDetail/{source}/{primaryId}/{timestamp}"
private val DETAIL_TWO_COLUMN_MIN_WIDTH: Dp = 720.dp
private val HOME_TWO_COLUMN_MIN_WIDTH: Dp = 560.dp
private val HOME_THREE_COLUMN_MIN_WIDTH: Dp = 920.dp

private val topLevelRoutes = setOf(HOME_ROUTE, DETECTION_ROUTE, LOGS_ROUTE, LOGS_ENCOUNTERS_ROUTE, SETTINGS_ROUTE)
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private const val APPROACH_ALERT_CHANNEL_ID = "argus_approach_alerts"
private const val APPROACH_ALERT_COOLDOWN_MS = 2 * 60 * 1000L
private const val TRACKER_ALERT_CHANNEL_ID = "argus_tracker_alerts"
private const val TRACKER_ALERT_COOLDOWN_MS = 5 * 60 * 1000L
private const val FLOCK_ALERT_CHANNEL_ID = "argus_flock_alerts"
private const val FLOCK_ALERT_COOLDOWN_MS = 10 * 60 * 1000L
private const val NO_FLY_PASS_THROUGH_ALERT_CHANNEL_ID = "argus_no_fly_pass_through_alerts"
private const val NO_FLY_PASS_THROUGH_ALERT_COOLDOWN_MS = 2 * 60 * 1000L
private const val NFC_ALERT_CHANNEL_ID = "argus_nfc_alerts"
private const val NFC_ALERT_COOLDOWN_MS = 30 * 1000L
private const val MAGNETIC_INCREASE_ALERT_CHANNEL_ID = "argus_magnetic_increase_alerts"
private const val MAGNETIC_INCREASE_ALERT_COOLDOWN_MS = 90 * 1000L
private const val MAGNETIC_INCREASE_DELTA_THRESHOLD_UT = 12.0
private const val MAGNETIC_INCREASE_MIN_CURRENT_UT = 55.0
private const val MAGNETIC_DISTURBANCE_UPPER_BOUND_UT = 65.0
private const val ALERT_LOG_MAX_ENTRIES = 400
private const val MAP_AUTO_FOCUS_MAX_DISTANCE_METERS = 160_000.0
private const val REMOTE_ID_BROADCAST_MAX_OFFSET_METERS = 80_000.0
private const val MAP_COVERAGE_CENTER_JITTER_METERS = 20.0
private const val MAP_COVERAGE_RADIUS_JITTER_METERS = 30.0
private const val MAP_COVERAGE_RADIUS_MAX_STEP_METERS = 150.0
private const val MAP_COVERAGE_RADIUS_UPDATE_MIN_INTERVAL_MS = 4_000L
private const val MAP_COVERAGE_EMPTY_HOLD_MS = 12_000L
private const val MAP_COVERAGE_IMMEDIATE_RESIZE_DELTA_METERS = 300.0
private const val MAP_COVERAGE_RECENT_WINDOW_MS = 120_000L
private const val MAP_AIRCRAFT_COVERAGE_RECENT_WINDOW_MS = 600_000L
private const val MAP_COVERAGE_RADIUS_PERCENTILE = 0.85
private const val WIFI_RANDOMIZED_MIN_SIGHTINGS = 2
private const val MAP_CAMERA_BOUNDS_SAMPLE_LIMIT = 220
private const val MOVING_PATH_RENDER_POINT_LIMIT = 900
private const val MAP_RENDER_PIN_LIMIT_FAR = 260
private const val MAP_RENDER_PIN_LIMIT_MID = 420
private const val MAP_SWEEP_ANIMATION_STEP_DEGREES = 8f
private const val MAP_SWEEP_ANIMATION_FRAME_MS = 140L
private const val MAP_SWEEP_DISABLE_PIN_THRESHOLD = 280
private const val INCIDENT_NO_FLY_BOUNDARY_POINT_LIMIT = 120
private const val NO_FLY_ZONE_RENDER_COUNT_LOW = 60
private const val NO_FLY_ZONE_RENDER_COUNT_BALANCED = 120
private const val NO_FLY_ZONE_RENDER_COUNT_HIGH = 220
private const val NO_FLY_ZONE_MARKER_COUNT_LOW = 24
private const val NO_FLY_ZONE_MARKER_COUNT_BALANCED = 48
private const val NO_FLY_ZONE_MARKER_COUNT_HIGH = 96
private const val DETECTION_TAB_MESH_INDEX = 4
private const val SIGNAL_INTEL_WINDOW_MS = 30L * 60L * 1000L
private const val SIGNAL_INTEL_MAX_ENCOUNTERS = 4000
private const val SIGNAL_INTEL_WINDOW_MINUTES = SIGNAL_INTEL_WINDOW_MS / 60_000L
private const val FIXED_LIVE_MAP_UPDATE_INTERVAL_SECONDS = 5L
private const val OPERATIONAL_ANALYSIS_WINDOW_MS = 60L * 60L * 1000L
private const val OPERATIONAL_ANALYSIS_MAX_ENCOUNTERS = 6000
private const val MAGNETIC_ALERT_WINDOW_MS = 30L * 60L * 1000L
private const val MAGNETIC_ALERT_MAX_ENCOUNTERS = 2000
private const val ACTION_OPEN_APPROACH_MAP = "dev.argus.tracker.action.OPEN_APPROACH_MAP"
private const val ACTION_OPEN_NO_FLY_INCIDENT_PATH = "dev.argus.tracker.action.OPEN_NO_FLY_INCIDENT_PATH"
private const val EXTRA_APPROACH_SOURCE = "extra_approach_source"
private const val EXTRA_APPROACH_PRIMARY_ID = "extra_approach_primary_id"
private const val EXTRA_NO_FLY_SOURCE = "extra_no_fly_source"
private const val EXTRA_NO_FLY_PRIMARY_ID = "extra_no_fly_primary_id"
private const val EXTRA_NO_FLY_ZONE_SUMMARY = "extra_no_fly_zone_summary"
private const val EXTRA_NO_FLY_EVENT_EPOCH_MS = "extra_no_fly_event_epoch_ms"
private const val EXTRA_NO_FLY_LAT = "extra_no_fly_lat"
private const val EXTRA_NO_FLY_LON = "extra_no_fly_lon"
private const val EXTRA_NO_FLY_ZONE_IDS = "extra_no_fly_zone_ids"
private const val GOOGLE_MAP_DARK_STYLE_JSON = """
[
    {"elementType":"geometry","stylers":[{"color":"#212121"}]},
    {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
    {"elementType":"labels.text.fill","stylers":[{"color":"#757575"}]},
    {"elementType":"labels.text.stroke","stylers":[{"color":"#212121"}]},
    {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#757575"}]},
    {"featureType":"administrative.country","elementType":"labels.text.fill","stylers":[{"color":"#9e9e9e"}]},
    {"featureType":"administrative.land_parcel","stylers":[{"visibility":"off"}]},
    {"featureType":"administrative.locality","elementType":"labels.text.fill","stylers":[{"color":"#bdbdbd"}]},
    {"featureType":"poi","elementType":"labels.text.fill","stylers":[{"color":"#757575"}]},
    {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#181818"}]},
    {"featureType":"poi.park","elementType":"labels.text.fill","stylers":[{"color":"#616161"}]},
    {"featureType":"poi.park","elementType":"labels.text.stroke","stylers":[{"color":"#1b1b1b"}]},
    {"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#2c2c2c"}]},
    {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#8a8a8a"}]},
    {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#373737"}]},
    {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#3c3c3c"}]},
    {"featureType":"road.highway.controlled_access","elementType":"geometry","stylers":[{"color":"#4e4e4e"}]},
    {"featureType":"road.local","elementType":"labels.text.fill","stylers":[{"color":"#616161"}]},
    {"featureType":"transit","elementType":"labels.text.fill","stylers":[{"color":"#757575"}]},
    {"featureType":"water","elementType":"geometry","stylers":[{"color":"#000000"}]},
    {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#3d3d3d"}]}
]
"""

private fun routeNeedsNowTicker(route: String): Boolean {
    return route == HOME_ROUTE || route.startsWith("approachAlertMap/")
}

private fun parseNoFlyZoneSummaryFromLogMessage(message: String): String? {
    val marker = "entered no-fly zone:"
    val markerIndex = message.indexOf(marker, ignoreCase = true)
    if (markerIndex < 0) return null
    return message
        .substring(markerIndex + marker.length)
        .trim()
        .takeIf { it.isNotBlank() }
}

private fun topLevelRouteForSelection(route: String): String = when (route) {
    LOGS_ENCOUNTERS_ROUTE, DEVICES_ENCOUNTERS_ROUTE -> LOGS_ROUTE
    else -> route
}

@Composable
private fun rememberMapStyleOptionsForTheme(): MapStyleOptions? {
        val useDarkMapStyle = MaterialTheme.colorScheme.background.luminance() < 0.5f
        return remember(useDarkMapStyle) {
                if (useDarkMapStyle) MapStyleOptions(GOOGLE_MAP_DARK_STYLE_JSON) else null
        }
}

private enum class AlertLogType {
    APPROACH,
    TRACKER,
    NO_FLY_PASS_THROUGH,
    NFC
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
    val summary: String,
    val seenAtHome: Boolean,
    val seenAwayFromHome: Boolean,
    val trackerFamilyHint: String? = null,
    val trackerFamilyConfidence: Double? = null
)

private data class HomeSensorToggle(
    val key: String,
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val status: SensorStatus?
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
    val gpsSpoofSuspected: Boolean,
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
    val bluetoothLeEnabled: Boolean,
    val cellularEnabled: Boolean,
    val nfcEnabled: Boolean,
    val aviationAdsbEnabled: Boolean,
    val aviationPublicEnabled: Boolean,
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

private data class RemoteIdResolvedLocation(
    val lat: Double,
    val lon: Double,
    val timestampEpochMs: Long,
    val method: String
)

private data class DeviceLocationCandidate(
    val source: String,
    val primaryId: String,
    val secondaryId: String?,
    val latestTimestampEpochMs: Long,
    val seenCount: Int,
    val encounters: List<Encounter>,
    val latestEncounter: Encounter,
    val previousEncounter: Encounter? = null,
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

private data class NoFlyTrackSnapshot(
    val source: EncounterSource,
    val primaryId: String,
    val secondaryId: String?,
    val latestTimestampEpochMs: Long,
    val latestLat: Double,
    val latestLon: Double,
    val latestAltitudeFeet: Double?,
    val previousLat: Double?,
    val previousLon: Double?,
    val previousAltitudeFeet: Double?
)

private object OwnedDeviceRegistry {
    fun keyFor(source: String, primaryId: String): String = OwnedSignalRegistry.keyFor(source, primaryId)

    fun read(context: android.content.Context): Set<String> = OwnedSignalRegistry.read(context)

    fun setOwned(context: android.content.Context, source: String, primaryId: String, owned: Boolean) {
        OwnedSignalRegistry.setOwned(context, source, primaryId, owned)
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

    fun getAllRecordSpeedsMps(context: android.content.Context): Map<String, Double> {
        val raw = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_TOP_SPEEDS, "{}")
            .orEmpty()
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        val keys = obj.keys().asSequence().toList()
        if (keys.isEmpty()) return emptyMap()

        val records = LinkedHashMap<String, Double>(keys.size)
        keys.forEach { key ->
            val value = obj.optDouble(key, Double.NaN)
            if (value.isFinite() && value >= 0.0) {
                records[key] = value
            }
        }
        return records
    }

    fun getRecordSpeedMps(context: android.content.Context, source: String, primaryId: String): Double? {
        val key = "${source}|${primaryId}"
        return getAllRecordSpeedsMps(context)[key]
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
        bluetoothLeEnabled = ScanSettings.isBleSensorEnabled(context),
        cellularEnabled = ScanSettings.isCellularSensorEnabled(context),
        nfcEnabled = ScanSettings.isNfcSensorEnabled(context),
        aviationAdsbEnabled = ScanSettings.isAviationAdsbSensorEnabled(context),
        aviationPublicEnabled = ScanSettings.isAviationPublicSensorEnabled(context),
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
    val liveMapUpdateIntervalSeconds = FIXED_LIVE_MAP_UPDATE_INTERVAL_SECONDS
    var mapClusteringEnabled by remember { mutableStateOf(ScanSettings.isMapClusteringEnabled(context)) }
    var mapClusterRangeLevel by remember { mutableStateOf(ScanSettings.getMapClusterRangeLevel(context)) }
    var mapTrafficEnabled by remember { mutableStateOf(ScanSettings.isMapTrafficEnabled(context)) }
    var mapNoFlyZonesEnabled by remember { mutableStateOf(ScanSettings.isMapNoFlyZonesEnabled(context)) }
    var mapNoFlyRenderQualityLevel by remember {
        mutableStateOf(ScanSettings.getMapNoFlyRenderQualityLevel(context))
    }
    var mapScannerSweepAnimationEnabled by remember {
        mutableStateOf(ScanSettings.isMapScannerSweepAnimationEnabled(context))
    }
    var wifiRandomizedOneOffSuppressionEnabled by remember {
        mutableStateOf(ScanSettings.isWifiRandomizedOneOffSuppressionEnabled(context))
    }
    var wifiAggregateOnlyEnabled by remember {
        mutableStateOf(ScanSettings.isWifiAggregateOnlyEnabled(context))
    }
    var bleRandomizedOneOffSuppressionEnabled by remember {
        mutableStateOf(ScanSettings.isBleRandomizedOneOffSuppressionEnabled(context))
    }
    var bleAggregateOnlyEnabled by remember {
        mutableStateOf(ScanSettings.isBleAggregateOnlyEnabled(context))
    }
    var sourceScanIntervals by remember { mutableStateOf(ScanSettings.getAllSourceScanIntervalSeconds(context)) }
    var sourceLastScanEpochs by remember { mutableStateOf(ScanSettings.getAllSourceLastScanEpochMs(context)) }
    var sourceLastRawObservationEpochs by remember {
        mutableStateOf(ScanSettings.getAllSourceLastRawObservationEpochMs(context))
    }
    var sensorGateSettings by remember { mutableStateOf(readSensorGateSettings(context)) }
    var approachDetectionEnabled by remember { mutableStateOf(ScanSettings.isApproachDetectionEnabled(context)) }
    var approachNotificationsEnabled by remember { mutableStateOf(ScanSettings.isApproachNotificationsEnabled(context)) }
    var trackerNotificationsEnabled by remember { mutableStateOf(ScanSettings.isTrackerNotificationsEnabled(context)) }
    var flockNotificationsEnabled by remember { mutableStateOf(ScanSettings.isFlockNotificationsEnabled(context)) }
    var noFlyPassThroughNotificationsEnabled by remember {
        mutableStateOf(ScanSettings.isNoFlyPassThroughNotificationsEnabled(context))
    }
    var nfcNotificationsEnabled by remember { mutableStateOf(ScanSettings.isNfcNotificationsEnabled(context)) }
    var magneticIncreaseNotificationsEnabled by remember { mutableStateOf(ScanSettings.isMagneticIncreaseNotificationsEnabled(context)) }
    var meshConnectivityNotificationsEnabled by remember { mutableStateOf(ScanSettings.isMeshConnectivityNotificationsEnabled(context)) }
    var meshWipeNotificationsEnabled by remember { mutableStateOf(ScanSettings.isMeshWipeNotificationsEnabled(context)) }
    var foreignDirectAcousticEnabled by remember { mutableStateOf(ScanSettings.isForeignDirectAcousticEnabled(context)) }
    var foreignDirectMagneticEnabled by remember { mutableStateOf(ScanSettings.isForeignDirectMagneticEnabled(context)) }
    var homePoint by remember { mutableStateOf(ScanSettings.getHomePoint(context)) }
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
    var errorLogs by remember { mutableStateOf(OperationalErrorLogStore.read(context)) }
    var lastScanDurationMs by remember { mutableStateOf(ScanSettings.getLastScanDurationMs(context)) }
    var sourceScanTimings by remember { mutableStateOf(ScanSettings.getSourceScanTimings(context)) }
    var scanIntervalChangeEvents by remember { mutableStateOf(ScanSettings.getScanIntervalChangeEvents(context, 10)) }
    var appThemeMode by remember {
        mutableStateOf(
            runCatching { AppThemeMode.valueOf(ScanSettings.getAppThemeMode(context)) }
                .getOrDefault(AppThemeMode.DARK)
        )
    }
    var trackingStartMessage by remember { mutableStateOf<String?>(null) }
    var trackingStartMessageIsError by remember { mutableStateOf(false) }
    var appNowEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val approachStateByDevice = remember { mutableMapOf<String, Boolean>() }
    val lastApproachNotificationEpochByDevice = remember { mutableMapOf<String, Long>() }
    val trackerStateByDevice = remember { mutableMapOf<String, TrackerRiskLevel>() }
    val lastTrackerNotificationEpochByDevice = remember { mutableMapOf<String, Long>() }
    val lastFlockNotificationEpochBySignature = remember { mutableMapOf<String, Long>() }
    val noFlyZoneStateByTrack = remember { mutableMapOf<String, Set<String>>() }
    val lastNoFlyZoneNotificationEpochByTrackZone = remember { mutableMapOf<String, Long>() }
    val noFlyZoneDetectionCache = remember { mutableMapOf<String, List<NoFlyZoneOverlayProvider.NoFlyZonePolygon>>() }
    var lastMagneticIncreaseAlertEpochMs by remember { mutableStateOf(0L) }
    var lastNfcAlertEpochMs by remember { mutableStateOf(0L) }
    var lastNfcObservedEncounterEpochMs by remember { mutableStateOf(0L) }
    var lastMagneticObservedSampleEpochMs by remember { mutableStateOf(0L) }
    var lastWearStatusSignature by remember { mutableStateOf<String?>(null) }
    var lastWearStatusPublishEpochMs by remember { mutableStateOf(0L) }
    var lastOperationalStateSignature by remember { mutableStateOf("") }
    var startupConfigLoaded by remember { mutableStateOf(false) }
    var trackingObserverInitialized by remember { mutableStateOf(false) }
    var operationalObserverInitialized by remember { mutableStateOf(false) }
    var mapDataPrewarmReady by remember { mutableStateOf(false) }
    var startupBootstrapScanCompleted by remember { mutableStateOf(false) }
    var startupBootstrapWaitRequired by remember { mutableStateOf(true) }
    var startupRuntimeGateReleased by remember { mutableStateOf(false) }
    var startupPrewarmedDevicePins by remember { mutableStateOf<List<MapPin>>(emptyList()) }
    var startupPrewarmedNoFlyZones by remember { mutableStateOf<List<NoFlyZoneOverlayProvider.NoFlyZonePolygon>>(emptyList()) }
    val intervalManagedSourceTypes = remember {
        ScanSettings.SOURCE_TYPES.filterNot {
            it == SourceCatalog.KEY_WIFI_DIRECT ||
                it == SourceCatalog.KEY_BT_CLASSIC ||
                it == SourceCatalog.KEY_REMOTE_ID ||
                it == SourceCatalog.KEY_NFC
        }
    }
    var mapResetGeneration by remember { mutableStateOf(0L) }
    var analyzedDevices by remember { mutableStateOf<List<DeviceItem>>(emptyList()) }
    var detectionInitialTabRequest by rememberSaveable { mutableStateOf<Int?>(null) }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: HOME_ROUTE
    fun navigateTopLevel(route: String) {
        if (topLevelRouteForSelection(currentRoute) == route) return
        if (currentRoute == DEVICE_DETAIL_ROUTE && route != DETECTION_ROUTE) {
            detectionInitialTabRequest = 0
        }
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
        }
    }
    val requireAllEncounterStream = remember(currentRoute) {
        requiresAllEncountersRoute(currentRoute)
    }

    val recent by viewModel.recentEncounters.collectAsState()
    val recent100 by viewModel.recent100Encounters.collectAsState()
    val allEncounters by if (requireAllEncounterStream) {
        viewModel.allEncounters.collectAsState()
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    val summary by viewModel.summary.collectAsState()
    val chainMesh by app.container.chainLinkCoordinator.observeMesh().collectAsState()
    val meshPeerEncounters = remember(chainMesh) {
        buildMeshPeerEncounters(chainMesh)
    }
    val recentPipelineEncounters = remember(recent, meshPeerEncounters) {
        mergePipelineEncounters(recent, meshPeerEncounters)
    }
    val recent100PipelineEncounters = remember(recent100, meshPeerEncounters) {
        mergePipelineEncounters(recent100, meshPeerEncounters)
    }
    val allPipelineEncounters = remember(allEncounters, meshPeerEncounters) {
        mergePipelineEncounters(allEncounters, meshPeerEncounters)
    }
    val hasMapsApiKey by remember(context) { mutableStateOf(hasGoogleMapsApiKey(context)) }
    var mapPrewarmReady by remember { mutableStateOf(!hasMapsApiKey) }
    val appStartupReady = startupConfigLoaded && trackingObserverInitialized && operationalObserverInitialized
    val appRuntimeReady = appStartupReady && mapPrewarmReady && mapDataPrewarmReady
    val startupBootstrapGateSatisfied = !startupBootstrapWaitRequired || startupBootstrapScanCompleted
    val lastScanEpochMs = remember(recent) { recent.maxOfOrNull { it.timestampEpochMs } }
    val operationalAnalysisWindow = remember(recent) {
        selectRecentEncounterWindow(
            encounters = recent,
            windowMs = OPERATIONAL_ANALYSIS_WINDOW_MS,
            maxEncounters = OPERATIONAL_ANALYSIS_MAX_ENCOUNTERS
        )
    }
    val magneticAlertWindow = remember(recent) {
        selectRecentEncounterWindow(
            encounters = recent,
            windowMs = MAGNETIC_ALERT_WINDOW_MS,
            maxEncounters = MAGNETIC_ALERT_MAX_ENCOUNTERS
        )
    }
    val suppressedWifiRandomizedOneOffCount = remember(
        operationalAnalysisWindow,
        wifiRandomizedOneOffSuppressionEnabled
    ) {
        countSuppressedLikelyRandomizedWifiOneOffDevices(
            encounters = operationalAnalysisWindow,
            suppressionEnabled = wifiRandomizedOneOffSuppressionEnabled
        )
    }
    val suppressedBleRandomizedOneOffCount = remember(
        operationalAnalysisWindow,
        bleRandomizedOneOffSuppressionEnabled
    ) {
        countSuppressedLikelyRandomizedBleOneOffDevices(
            encounters = operationalAnalysisWindow,
            suppressionEnabled = bleRandomizedOneOffSuppressionEnabled
        )
    }

    LaunchedEffect(lastScanEpochMs) {
        if (lastScanEpochMs == null) return@LaunchedEffect
        delay(200L)
        viewModel.refreshSummary()
    }

    val nowTickerEnabled = remember(currentRoute) { routeNeedsNowTicker(currentRoute) }
    LaunchedEffect(nowTickerEnabled) {
        if (!nowTickerEnabled) return@LaunchedEffect
        tickerFlow(periodMs = 1000L).collect { now ->
            appNowEpochMs = now
        }
    }

    LaunchedEffect(chainMesh, alertLogs, recent100PipelineEncounters) {
        delay(750)
        val peersTotal = chainMesh.peers.size
        val peersConnected = chainMesh.peers.count { it.state == ChainPeerState.CONNECTED }
        val recentDevicePoints = withContext(Dispatchers.Default) {
            buildWearDevicePoints(recent100PipelineEncounters)
        }
        val latestAlert = alertLogs.maxByOrNull { it.timestampEpochMs }
        val alertMessage = latestAlert?.message?.takeIf { it.isNotBlank() } ?: "No recent alerts"
        val alertEpochMs = latestAlert?.timestampEpochMs
        val pointsSignature = recentDevicePoints.hashCode()
        val signature = "$peersTotal|$peersConnected|$alertMessage|${alertEpochMs ?: 0L}|$pointsSignature"
        if (signature == lastWearStatusSignature) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - lastWearStatusPublishEpochMs < 3_000L) return@LaunchedEffect

        lastWearStatusSignature = signature
        lastWearStatusPublishEpochMs = now
        WearStatusBridgePublisher.publishStatus(
            context = context,
            peersTotal = peersTotal,
            peersConnected = peersConnected,
            lastAlertMessage = alertMessage,
            lastAlertEpochMs = alertEpochMs,
            devicePoints = recentDevicePoints
        )
    }

    LaunchedEffect(operationalAnalysisWindow, ownedDeviceKeys) {
        val updatedKeys = autoMarkConnectedWifiAsOwned(
            context = context,
            encounters = operationalAnalysisWindow,
            ownedDeviceKeys = ownedDeviceKeys
        )
        if (updatedKeys != ownedDeviceKeys) {
            ownedDeviceKeys = updatedKeys
        }
    }

    LaunchedEffect(
        operationalAnalysisWindow,
        ownedDeviceKeys,
        approachDetectionEnabled,
        homePoint,
        wifiRandomizedOneOffSuppressionEnabled,
        bleRandomizedOneOffSuppressionEnabled
    ) {
        if (!approachDetectionEnabled) {
            analyzedDevices = emptyList()
            return@LaunchedEffect
        }
        analyzedDevices = withContext(Dispatchers.Default) {
            buildDeviceItems(
                encounters = operationalAnalysisWindow,
                approachDetectionEnabled = true,
                ownedDeviceKeys = ownedDeviceKeys,
                homePoint = homePoint,
                suppressLikelyRandomizedWifiOneOffs = wifiRandomizedOneOffSuppressionEnabled,
                suppressLikelyRandomizedBleOneOffs = bleRandomizedOneOffSuppressionEnabled
            )
        }
    }

    LaunchedEffect(notificationIntent) {
        val intent = notificationIntent ?: return@LaunchedEffect
        when (intent.action) {
            ACTION_OPEN_APPROACH_MAP -> {
                val source = intent.getStringExtra(EXTRA_APPROACH_SOURCE)
                    ?.takeIf { it.isNotBlank() }
                    ?: return@LaunchedEffect
                val primaryId = intent.getStringExtra(EXTRA_APPROACH_PRIMARY_ID)
                    ?.takeIf { it.isNotBlank() }
                    ?: return@LaunchedEffect
                navController.navigate("approachAlertMap/${Uri.encode(source)}/${Uri.encode(primaryId)}") {
                    launchSingleTop = true
                }
            }

            ACTION_OPEN_NO_FLY_INCIDENT_PATH -> {
                val source = intent.getStringExtra(EXTRA_NO_FLY_SOURCE)
                    ?.takeIf { it.isNotBlank() }
                    ?: return@LaunchedEffect
                val primaryId = intent.getStringExtra(EXTRA_NO_FLY_PRIMARY_ID)
                    ?.takeIf { it.isNotBlank() }
                    ?: return@LaunchedEffect
                val zoneSummary = intent.getStringExtra(EXTRA_NO_FLY_ZONE_SUMMARY)
                    ?.takeIf { it.isNotBlank() }
                    ?: "No-fly zone"
                val eventEpochMs = intent.getLongExtra(EXTRA_NO_FLY_EVENT_EPOCH_MS, 0L)
                    .takeIf { it > 0L }
                    ?: System.currentTimeMillis()
                val incidentLat = intent.getDoubleExtra(EXTRA_NO_FLY_LAT, Double.NaN)
                    .takeIf { !it.isNaN() && it in -90.0..90.0 }
                val incidentLon = intent.getDoubleExtra(EXTRA_NO_FLY_LON, Double.NaN)
                    .takeIf { !it.isNaN() && it in -180.0..180.0 }
                val enteredZoneIds = intent.getStringExtra(EXTRA_NO_FLY_ZONE_IDS)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                val queryParts = buildList {
                    incidentLat?.let { add("lat=${Uri.encode(it.toString())}") }
                    incidentLon?.let { add("lon=${Uri.encode(it.toString())}") }
                    enteredZoneIds?.let { add("zoneIds=${Uri.encode(it)}") }
                }
                val query = if (queryParts.isEmpty()) "" else "?${queryParts.joinToString("&")}" 

                navController.navigate(
                    "noFlyIncidentPath/${Uri.encode(source)}/${Uri.encode(primaryId)}/${Uri.encode(zoneSummary)}/$eventEpochMs$query"
                ) {
                    launchSingleTop = true
                }
            }
        }
    }
    suspend fun applyScanInterval(
        seconds: Long,
        sourceLabel: String,
        reasonCode: String,
        announceResult: Boolean = true
    ) {
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
            if (announceResult) {
                trackingStartMessage = if (restartResult.success) {
                    "$sourceLabel set interval to ${ScanSettings.formatInterval(seconds)} and restarted tracking."
                } else {
                    "$sourceLabel set interval to ${ScanSettings.formatInterval(seconds)}, but restart failed: ${restartResult.message}"
                }
                trackingStartMessageIsError = !restartResult.success
            }
            trackingActive = WorkScheduler.isTrackingActive(context)
        } else if (announceResult) {
            trackingStartMessage = "$sourceLabel set interval to ${ScanSettings.formatInterval(seconds)}."
            trackingStartMessageIsError = false
        }
    }

    fun minimumEnabledSourceIntervalSeconds(): Long {
        val enabled = enabledIntervalSourceTypes(sensorGateSettings)
        if (enabled.isEmpty()) return scanIntervalSeconds
        return enabled
            .map { source -> sourceScanIntervals[source] ?: ScanSettings.DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS }
            .minOrNull()
            ?.coerceAtLeast(1L)
            ?: scanIntervalSeconds
    }

    fun nearestAllowedGlobalInterval(seconds: Long): Long {
        return ScanSettings.ALLOWED_INTERVALS_SECONDS.minByOrNull { abs(it - seconds) }
            ?: ScanSettings.DEFAULT_SCAN_INTERVAL_SECONDS
    }

    suspend fun alignWorkerCadenceWithSources(reasonCode: String) {
        val targetInterval = nearestAllowedGlobalInterval(minimumEnabledSourceIntervalSeconds())
        applyScanInterval(
            seconds = targetInterval,
            sourceLabel = "Cadence sync",
            reasonCode = reasonCode,
            announceResult = false
        )
    }

    LaunchedEffect(Unit) {
        startupBootstrapWaitRequired = ScanSettings.isStartupBootstrapWaitRequired(context)

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
        mapClusteringEnabled = ScanSettings.isMapClusteringEnabled(context)
        mapClusterRangeLevel = ScanSettings.getMapClusterRangeLevel(context)
        mapTrafficEnabled = ScanSettings.isMapTrafficEnabled(context)
        mapNoFlyZonesEnabled = ScanSettings.isMapNoFlyZonesEnabled(context)
        mapNoFlyRenderQualityLevel = ScanSettings.getMapNoFlyRenderQualityLevel(context)
        mapScannerSweepAnimationEnabled = ScanSettings.isMapScannerSweepAnimationEnabled(context)
        wifiRandomizedOneOffSuppressionEnabled = ScanSettings.isWifiRandomizedOneOffSuppressionEnabled(context)
        wifiAggregateOnlyEnabled = ScanSettings.isWifiAggregateOnlyEnabled(context)
        bleRandomizedOneOffSuppressionEnabled = ScanSettings.isBleRandomizedOneOffSuppressionEnabled(context)
        bleAggregateOnlyEnabled = ScanSettings.isBleAggregateOnlyEnabled(context)
        sourceScanIntervals = ScanSettings.getAllSourceScanIntervalSeconds(context)
        sourceLastScanEpochs = ScanSettings.getAllSourceLastScanEpochMs(context)
        sourceLastRawObservationEpochs = ScanSettings.getAllSourceLastRawObservationEpochMs(context)
        foreignDirectAcousticEnabled = ScanSettings.isForeignDirectAcousticEnabled(context)
        foreignDirectMagneticEnabled = ScanSettings.isForeignDirectMagneticEnabled(context)
        homePoint = ScanSettings.getHomePoint(context)
        lastScanDurationMs = ScanSettings.getLastScanDurationMs(context)
        sourceScanTimings = ScanSettings.getSourceScanTimings(context)
        scanIntervalChangeEvents = ScanSettings.getScanIntervalChangeEvents(context, 10)
        errorLogs = OperationalErrorLogStore.read(context)
        sensorStatuses = SensorStatusProvider.read(context)
        readinessItems = DetectionReadinessAdvisor.evaluate(context)
        if (chainLinkEnabled && chainSharedSecret.isNotBlank() && !chainPersistentChannelEnabled) {
            chainPersistentChannelEnabled = true
            ScanSettings.setChainPersistentChannelEnabled(context, true)
        }
        MeshForegroundServiceController.ensureState(context)
        startupConfigLoaded = true
    }

    LaunchedEffect(appRuntimeReady, startupBootstrapGateSatisfied, startupRuntimeGateReleased) {
        if (startupRuntimeGateReleased || !appRuntimeReady || !startupBootstrapGateSatisfied) return@LaunchedEffect
        startupRuntimeGateReleased = true
    }

    LaunchedEffect(Unit) {
        val prewarmedPins = runCatching {
            withTimeoutOrNull(3500L) {
                val startupEncounters = withContext(Dispatchers.IO) {
                    app.container.repository.observeRecent(limit = 5000).first()
                }
                withContext(Dispatchers.Default) {
                    buildStartupPrewarmedDeviceMapPins(
                        encounters = startupEncounters,
                        ownedDeviceKeys = ownedDeviceKeys,
                        approachDetectionEnabled = approachDetectionEnabled,
                        sourceScanIntervals = sourceScanIntervals,
                        liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds,
                        maxDeviceCandidates = 1200,
                        suppressLikelyRandomizedWifiOneOffs = wifiRandomizedOneOffSuppressionEnabled,
                        suppressLikelyRandomizedBleOneOffs = bleRandomizedOneOffSuppressionEnabled
                    )
                }
            }
        }.getOrNull().orEmpty()

        startupPrewarmedDevicePins = prewarmedPins

        val prewarmedNoFlyZones = if (mapNoFlyZonesEnabled) {
            withContext(Dispatchers.IO) {
                NoFlyZoneOverlayProvider.read(
                    context = context,
                    near = LocationSnapshotProvider.read(context),
                    allowNetworkFetch = false
                )
            }
        } else {
            emptyList()
        }
        startupPrewarmedNoFlyZones = prewarmedNoFlyZones

        withContext(Dispatchers.Default) {
            prewarmedPins.take(180).forEach { pin ->
                markerDotIconForPin(pin, useSourceOnlyPinColors = true)
                markerIconForPin(pin, useSourceOnlyPinColors = true)
            }
        }

        mapDataPrewarmReady = true
    }

    LaunchedEffect(chainLinkEnabled, chainAutoSyncEnabled, chainAutoSyncIntervalSeconds, chainSharedSecret) {
        if (!chainLinkEnabled || !chainAutoSyncEnabled) return@LaunchedEffect
        if (chainSharedSecret.isBlank()) return@LaunchedEffect

        while (true) {
            runCatching { app.container.chainLinkCoordinator.syncNow() }
            delay(chainAutoSyncIntervalSeconds * 1000L)
        }
    }

    LaunchedEffect(sourceScanIntervals, sensorGateSettings) {
        alignWorkerCadenceWithSources(reasonCode = "scheduler-align")
    }

    LaunchedEffect(
        chainLinkEnabled,
        chainPersistentChannelEnabled,
        chainSharedSecret,
        chainAutoSyncEnabled,
        chainAutoSyncIntervalSeconds
    ) {
        if (!chainLinkEnabled) return@LaunchedEffect
        runCatching { app.container.chainLinkCoordinator.refreshPeers() }
    }

    LaunchedEffect(context) {
        WorkScheduler.observeTrackingActive(context).collect { isActive ->
            trackingActive = isActive
            trackingObserverInitialized = true
        }
    }

    LaunchedEffect(context) {
        WorkScheduler.observeStartupBootstrapScanCompleted(context).collect { completed ->
            startupBootstrapScanCompleted = completed
        }
    }

    LaunchedEffect(context) {
        ScanSettings.observeOperationalState(context)
            .debounce(250L)
            .collect { state ->
            operationalObserverInitialized = true
            val signature = buildString {
                append(state.lastScanDurationMs)
                append('|')
                append(state.sourceScanTimings.hashCode())
                append('|')
                append(state.sourceScanIntervals.hashCode())
                append('|')
                append(state.sourceLastScanEpochs.hashCode())
                append('|')
                append(state.sourceLastRawObservationEpochs.hashCode())
                append('|')
                append(state.scanIntervalChangeEvents.hashCode())
            }
            if (signature == lastOperationalStateSignature) return@collect
            lastOperationalStateSignature = signature

            lastScanDurationMs = state.lastScanDurationMs
            sourceScanTimings = state.sourceScanTimings
            sourceScanIntervals = state.sourceScanIntervals
            sourceLastScanEpochs = state.sourceLastScanEpochs
            sourceLastRawObservationEpochs = state.sourceLastRawObservationEpochs
            scanIntervalChangeEvents = state.scanIntervalChangeEvents
            errorLogs = OperationalErrorLogStore.read(context)
        }
    }

    LaunchedEffect(analyzedDevices, approachDetectionEnabled, approachNotificationsEnabled) {
        if (!approachDetectionEnabled) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val devices = analyzedDevices
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
        analyzedDevices,
        approachDetectionEnabled,
        trackerNotificationsEnabled
    ) {
        if (!approachDetectionEnabled) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val devices = analyzedDevices
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

    LaunchedEffect(operationalAnalysisWindow, flockNotificationsEnabled) {
        if (!flockNotificationsEnabled) {
            lastFlockNotificationEpochBySignature.clear()
            return@LaunchedEffect
        }

        val now = System.currentTimeMillis()
        val flocks = withContext(Dispatchers.Default) {
            detectDeviceFlocks(
                encounters = operationalAnalysisWindow,
                minTravelSpanMeters = 10.0
            )
        }

        val activeSignatures = mutableSetOf<String>()
        flocks.forEach { flock ->
            val signature = flock.members
                .asSequence()
                .map { member -> "${member.source}|${member.primaryId}" }
                .sorted()
                .joinToString(separator = ",")

            if (signature.isBlank()) return@forEach
            activeSignatures += signature

            val lastNotified = lastFlockNotificationEpochBySignature[signature] ?: 0L
            val persistedLastSignature = ScanSettings.getFlockAlertLastSignature(context)
            val persistedLastNotified = ScanSettings.getFlockAlertLastNotificationEpochMs(context)
            val sharedLastNotified = maxOf(lastNotified, persistedLastNotified)
            if ((signature == persistedLastSignature) || (sharedLastNotified != 0L && now - sharedLastNotified < FLOCK_ALERT_COOLDOWN_MS)) {
                return@forEach
            }

            val primaryMember = flock.members.firstOrNull()
            val primarySource = primaryMember?.source ?: SourceCatalog.SOURCE_CELL
            val primaryId = primaryMember?.primaryId ?: "flock-${flock.id}"
            val memberPreview = flock.members
                .take(4)
                .joinToString(separator = ", ") { member ->
                    "${member.source}:${member.primaryId}"
                }
            val overflow = (flock.members.size - 4).coerceAtLeast(0)
            val overflowLabel = if (overflow > 0) " +$overflow more" else ""
            val message = "Flock detected (${flock.members.size} devices, ${flock.coTravelEventCount} co-travel events, span ${formatDistanceFeetMiles(flock.travelSpanMeters)}): $memberPreview$overflowLabel"

            withContext(Dispatchers.IO) {
                AlertLogStore.append(
                    context = context,
                    entry = AlertLogEntry(
                        timestampEpochMs = now,
                        type = AlertLogType.TRACKER,
                        source = primarySource,
                        primaryId = primaryId,
                        message = message,
                        confidence = null
                    )
                )
            }

            if (hasPostNotificationsPermission(context)) {
                ensureFlockNotificationChannel(context)
                sendFlockNotification(context, flock)
            }

            lastFlockNotificationEpochBySignature[signature] = now
            ScanSettings.setFlockAlertLastSignature(context, signature)
            ScanSettings.setFlockAlertLastNotificationEpochMs(context, now)
        }

        val stale = lastFlockNotificationEpochBySignature.keys.filter { it !in activeSignatures }
        stale.forEach { signature ->
            lastFlockNotificationEpochBySignature.remove(signature)
        }

        alertLogs = AlertLogStore.read(context)
    }

    LaunchedEffect(operationalAnalysisWindow, noFlyPassThroughNotificationsEnabled) {
        val now = System.currentTimeMillis()
        val observerLocation = LocationSnapshotProvider.read(context)
        val noFlyAlertRadiusMeters = ScanSettings.getAviationPublicRadiusMiles(context).coerceIn(10, 300) * 1609.344
        val noFlyTracks = withContext(Dispatchers.Default) {
            val anchor = observerLocation ?: return@withContext emptyList()
            operationalAnalysisWindow
                .asSequence()
                .filter { encounter ->
                    encounter.source == EncounterSource.AIRCRAFT ||
                        encounter.source == EncounterSource.REMOTE_ID
                }
                .groupBy { encounter -> "${encounter.source.name}|${encounter.primaryId}" }
                .values
                .mapNotNull { encountersForTrack ->
                    val sorted = encountersForTrack.sortedByDescending { encounter -> encounter.timestampEpochMs }
                    val latest = sorted.firstOrNull() ?: return@mapNotNull null

                    fun resolvedLatLon(encounter: Encounter): Pair<Double, Double>? {
                        return when (encounter.source) {
                            EncounterSource.REMOTE_ID -> {
                                remoteIdBroadcastLatLon(encounter)
                                    ?: if (isValidLatLon(encounter.lat, encounter.lon)) {
                                        encounter.lat!! to encounter.lon!!
                                    } else {
                                        null
                                    }
                            }

                            else -> if (isValidLatLon(encounter.lat, encounter.lon)) {
                                encounter.lat!! to encounter.lon!!
                            } else {
                                null
                            }
                        }
                    }

                    val latestResolved = resolvedLatLon(latest) ?: return@mapNotNull null
                    val distanceFromObserverMeters = distanceFromLocationMeters(
                        fromLat = anchor.lat,
                        fromLon = anchor.lon,
                        toLat = latestResolved.first,
                        toLon = latestResolved.second
                    ) ?: return@mapNotNull null
                    if (distanceFromObserverMeters > noFlyAlertRadiusMeters) {
                        return@mapNotNull null
                    }
                    val previousResolvedWithEncounter = sorted
                        .drop(1)
                        .asSequence()
                        .mapNotNull { previousEncounter ->
                            resolvedLatLon(previousEncounter)?.let { previousEncounter to it }
                        }
                        .firstOrNull()
                    val previousResolved = previousResolvedWithEncounter?.second
                    val previousAltitudeFeet = previousResolvedWithEncounter
                        ?.first
                        ?.let(::extractEncounterAltitudeFeet)

                    NoFlyTrackSnapshot(
                        source = latest.source,
                        primaryId = latest.primaryId,
                        secondaryId = latest.secondaryId,
                        latestTimestampEpochMs = latest.timestampEpochMs,
                        latestLat = latestResolved.first,
                        latestLon = latestResolved.second,
                        latestAltitudeFeet = extractEncounterAltitudeFeet(latest),
                        previousLat = previousResolved?.first,
                        previousLon = previousResolved?.second,
                        previousAltitudeFeet = previousAltitudeFeet
                    )
                }
        }

        val seenTrackKeys = mutableSetOf<String>()
        val noFlyLogsToAppend = mutableListOf<AlertLogEntry>()

        for (track in noFlyTracks) {
            val trackKey = "${track.source.name}|${track.primaryId}"
            seenTrackKeys += trackKey

            val location = DetectionLocation(lat = track.latestLat, lon = track.latestLon)
            val zoneCacheKey = noFlyDetectionCacheKey(location)
            val zones = noFlyZoneDetectionCache[zoneCacheKey] ?: withContext(Dispatchers.IO) {
                NoFlyZoneOverlayProvider.read(context, near = location)
            }.also { loaded ->
                noFlyZoneDetectionCache[zoneCacheKey] = loaded
            }

            if (zones.isEmpty()) {
                noFlyZoneStateByTrack[trackKey] = emptySet()
                continue
            }

            val currentZoneIds = zones
                .asSequence()
                .filter { zone ->
                    noFlyZoneContainsPoint(zone, track.latestLat, track.latestLon) &&
                        noFlyZoneAltitudeAllowsEntry(zone, track.latestAltitudeFeet)
                }
                .map { zone -> zone.id }
                .toSet()

            val previousZoneIds = run {
                if (isValidLatLon(track.previousLat, track.previousLon)) {
                    zones
                        .asSequence()
                        .filter { zone ->
                            noFlyZoneContainsPoint(zone, track.previousLat!!, track.previousLon!!) &&
                                noFlyZoneAltitudeAllowsEntry(zone, track.previousAltitudeFeet)
                        }
                        .map { zone -> zone.id }
                        .toSet()
                } else {
                    noFlyZoneStateByTrack[trackKey] ?: emptySet()
                }
            }

            val enteredZoneIds = currentZoneIds - previousZoneIds
            if (enteredZoneIds.isNotEmpty()) {
                val enteredZones = zones.filter { zone -> zone.id in enteredZoneIds }
                val zoneHeadline = enteredZones
                    .take(2)
                    .joinToString(" / ") { zone ->
                        zone.label.takeIf { it.isNotBlank() } ?: "Unnamed zone"
                    }
                val overflowSuffix = if (enteredZones.size > 2) {
                    " +${enteredZones.size - 2} more"
                } else {
                    ""
                }
                val sourceLabel = listSourceLabel(track.source.name, track.secondaryId)
                val platformLabel = if (track.source == EncounterSource.REMOTE_ID) "Drone" else "Aircraft"
                val message = "$platformLabel $sourceLabel ${track.primaryId} entered no-fly zone: $zoneHeadline$overflowSuffix"

                noFlyLogsToAppend += AlertLogEntry(
                    timestampEpochMs = now,
                    type = AlertLogType.NO_FLY_PASS_THROUGH,
                    source = track.source.name,
                    primaryId = track.primaryId,
                    message = message,
                    confidence = null
                )

                val zoneNotificationKey = "$trackKey|${enteredZoneIds.sorted().joinToString(",")}"
                val lastNotifiedEpochMs = lastNoFlyZoneNotificationEpochByTrackZone[zoneNotificationKey] ?: 0L
                if (noFlyPassThroughNotificationsEnabled &&
                    hasPostNotificationsPermission(context) &&
                    now - lastNotifiedEpochMs >= NO_FLY_PASS_THROUGH_ALERT_COOLDOWN_MS
                ) {
                    ensureNoFlyPassThroughNotificationChannel(context)
                    sendNoFlyPassThroughNotification(
                        context = context,
                        source = track.source,
                        primaryId = track.primaryId,
                        sourceLabel = sourceLabel,
                        zones = enteredZones,
                        eventEpochMs = track.latestTimestampEpochMs,
                        eventLat = track.latestLat,
                        eventLon = track.latestLon
                    )
                    lastNoFlyZoneNotificationEpochByTrackZone[zoneNotificationKey] = now
                }
            }

            noFlyZoneStateByTrack[trackKey] = currentZoneIds
        }

        if (noFlyLogsToAppend.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                AlertLogStore.appendAll(context, noFlyLogsToAppend)
            }
            alertLogs = AlertLogStore.read(context)
        }

        val staleTrackKeys = noFlyZoneStateByTrack.keys.filter { key -> key !in seenTrackKeys }
        staleTrackKeys.forEach { staleKey ->
            noFlyZoneStateByTrack.remove(staleKey)
            lastNoFlyZoneNotificationEpochByTrackZone.keys
                .filter { key -> key.startsWith("$staleKey|") }
                .forEach { key -> lastNoFlyZoneNotificationEpochByTrackZone.remove(key) }
        }
    }

    LaunchedEffect(magneticAlertWindow, foreignDirectMagneticEnabled) {
        if (!foreignDirectMagneticEnabled) return@LaunchedEffect

        val pair = readLatestTwoMagneticSamples(magneticAlertWindow) ?: return@LaunchedEffect
        val previous = pair.first
        val current = pair.second
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

    LaunchedEffect(operationalAnalysisWindow, nfcNotificationsEnabled) {
        val latestNfc = operationalAnalysisWindow
            .asSequence()
            .filter { encounter -> encounter.source == EncounterSource.NFC }
            .maxByOrNull { encounter -> encounter.timestampEpochMs }
            ?: return@LaunchedEffect

        if (latestNfc.timestampEpochMs <= lastNfcObservedEncounterEpochMs) {
            return@LaunchedEffect
        }

        val now = System.currentTimeMillis()
        if (nfcNotificationsEnabled &&
            hasPostNotificationsPermission(context) &&
            now - lastNfcAlertEpochMs >= NFC_ALERT_COOLDOWN_MS
        ) {
            ensureNfcNotificationChannel(context)
            sendNfcNotification(context, latestNfc)
            lastNfcAlertEpochMs = now
        }

        lastNfcObservedEncounterEpochMs = latestNfc.timestampEpochMs
    }

    LaunchedEffect(
        operationalAnalysisWindow,
        ownedDeviceKeys,
        homePoint,
        wifiRandomizedOneOffSuppressionEnabled,
        bleRandomizedOneOffSuppressionEnabled
    ) {
        val devices = withContext(Dispatchers.Default) {
            buildDeviceItems(
                encounters = operationalAnalysisWindow,
                approachDetectionEnabled = approachDetectionEnabled,
                ownedDeviceKeys = ownedDeviceKeys,
                homePoint = homePoint,
                suppressLikelyRandomizedWifiOneOffs = wifiRandomizedOneOffSuppressionEnabled,
                suppressLikelyRandomizedBleOneOffs = bleRandomizedOneOffSuppressionEnabled
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
                            onClick = { navigateTopLevel(HOME_ROUTE) },
                            icon = { Text("H") },
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == DETECTION_ROUTE,
                            onClick = { navigateTopLevel(DETECTION_ROUTE) },
                            icon = { Text("R") },
                            label = { Text("Detection") }
                        )
                        NavigationBarItem(
                            selected =
                                currentRoute == LOGS_ROUTE ||
                                    currentRoute == LOGS_ENCOUNTERS_ROUTE ||
                                    currentRoute == DEVICES_ENCOUNTERS_ROUTE,
                            onClick = { navigateTopLevel(LOGS_ROUTE) },
                            icon = { Text("L") },
                            label = { Text("Logs") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == SETTINGS_ROUTE,
                            onClick = { navigateTopLevel(SETTINGS_ROUTE) },
                            icon = { Text("S") },
                            label = { Text("Settings") }
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
            if (!mapPrewarmReady) {
                MapEnginePrewarmHost(
                    onReady = {
                        mapPrewarmReady = true
                    }
                )
            }
            NavHost(
                navController = navController,
                startDestination = HOME_ROUTE,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
            composable(HOME_ROUTE) {
                HomePage(
                    trackingActive = trackingActive,
                    nowEpochMs = appNowEpochMs,
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
                                "bluetooth_le" -> ScanSettings.setBleSensorEnabled(context, enabled)
                                "cellular" -> ScanSettings.setCellularSensorEnabled(context, enabled)
                                "nfc" -> ScanSettings.setNfcSensorEnabled(context, enabled)
                                "aviation_adsb" -> ScanSettings.setAviationAdsbSensorEnabled(context, enabled)
                                "aviation_public" -> ScanSettings.setAviationPublicSensorEnabled(context, enabled)
                                "sdr" -> ScanSettings.setSdrSensorEnabled(context, enabled)
                                "direct_acoustic" -> ScanSettings.setForeignDirectAcousticEnabled(context, enabled)
                                "direct_magnetic" -> ScanSettings.setForeignDirectMagneticEnabled(context, enabled)
                            }
                            RemoteIdForegroundServiceController.ensureState(context)
                            sensorGateSettings = readSensorGateSettings(context)
                            foreignDirectAcousticEnabled = ScanSettings.isForeignDirectAcousticEnabled(context)
                            foreignDirectMagneticEnabled = ScanSettings.isForeignDirectMagneticEnabled(context)
                            sensorStatuses = SensorStatusProvider.read(context)
                            alignWorkerCadenceWithSources(reasonCode = "scheduler-align-sensor-gate")
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
                    onOpenDevicesEncounters = {
                        navController.navigate(LOGS_ENCOUNTERS_ROUTE) {
                            launchSingleTop = true
                        }
                    },
                    startMessage = trackingStartMessage,
                    startMessageIsError = trackingStartMessageIsError
                )
            }

            composable(SETTINGS_ROUTE) {
                AppSettingsPage(
                    scanIntervalSeconds = scanIntervalSeconds,
                    mapClusteringEnabled = mapClusteringEnabled,
                    mapClusterRangeLevel = mapClusterRangeLevel,
                    mapTrafficEnabled = mapTrafficEnabled,
                    mapNoFlyZonesEnabled = mapNoFlyZonesEnabled,
                    mapNoFlyRenderQualityLevel = mapNoFlyRenderQualityLevel,
                    mapScannerSweepAnimationEnabled = mapScannerSweepAnimationEnabled,
                    wifiRandomizedOneOffSuppressionEnabled = wifiRandomizedOneOffSuppressionEnabled,
                    wifiAggregateOnlyEnabled = wifiAggregateOnlyEnabled,
                    bleRandomizedOneOffSuppressionEnabled = bleRandomizedOneOffSuppressionEnabled,
                    bleAggregateOnlyEnabled = bleAggregateOnlyEnabled,
                    suppressedWifiRandomizedOneOffCount = suppressedWifiRandomizedOneOffCount,
                    suppressedBleRandomizedOneOffCount = suppressedBleRandomizedOneOffCount,
                    sourceScanIntervals = sourceScanIntervals,
                    sourceLastScanEpochs = sourceLastScanEpochs,
                    lastScanDurationMs = lastScanDurationMs,
                    sourceScanTimings = sourceScanTimings,
                    sourceLastRawObservationEpochs = sourceLastRawObservationEpochs,
                    scanIntervalChangeEvents = scanIntervalChangeEvents,
                    appThemeMode = appThemeMode,
                    approachDetectionEnabled = approachDetectionEnabled,
                    approachNotificationsEnabled = approachNotificationsEnabled,
                    trackerNotificationsEnabled = trackerNotificationsEnabled,
                    flockNotificationsEnabled = flockNotificationsEnabled,
                    noFlyPassThroughNotificationsEnabled = noFlyPassThroughNotificationsEnabled,
                    nfcNotificationsEnabled = nfcNotificationsEnabled,
                    magneticIncreaseNotificationsEnabled = magneticIncreaseNotificationsEnabled,
                    meshConnectivityNotificationsEnabled = meshConnectivityNotificationsEnabled,
                    meshWipeNotificationsEnabled = meshWipeNotificationsEnabled,
                    foreignDirectAcousticEnabled = foreignDirectAcousticEnabled,
                    foreignDirectMagneticEnabled = foreignDirectMagneticEnabled,
                    homePoint = homePoint,
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
                            alignWorkerCadenceWithSources(reasonCode = "scheduler-align-manual-$sourceType")
                        }
                    },
                    onAllSourceScanIntervalsSelected = { seconds ->
                        var hasChanges = false
                        intervalManagedSourceTypes.forEach { sourceType ->
                            val previous = sourceScanIntervals[sourceType]
                                ?: ScanSettings.DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS
                            val updated = seconds.coerceIn(
                                ScanSettings.MIN_SOURCE_SCAN_INTERVAL_SECONDS,
                                ScanSettings.MAX_SOURCE_SCAN_INTERVAL_SECONDS
                            )
                            if (previous != updated) {
                                hasChanges = true
                                ScanSettings.setSourceScanIntervalSeconds(context, sourceType, updated)
                                ScanSettings.appendScanIntervalChangeEvent(
                                    context = context,
                                    fromSeconds = previous,
                                    toSeconds = updated,
                                    reason = "manual-all-$sourceType"
                                )
                            }
                        }
                        if (hasChanges) {
                            sourceScanIntervals = ScanSettings.getAllSourceScanIntervalSeconds(context)
                            scanIntervalChangeEvents = ScanSettings.getScanIntervalChangeEvents(context, 10)
                            scope.launch {
                                alignWorkerCadenceWithSources(reasonCode = "scheduler-align-manual-all")
                            }
                        }
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
                    onFlockNotificationsChanged = { enabled ->
                        flockNotificationsEnabled = enabled
                        ScanSettings.setFlockNotificationsEnabled(context, enabled)
                    },
                    onNoFlyPassThroughNotificationsChanged = { enabled ->
                        noFlyPassThroughNotificationsEnabled = enabled
                        ScanSettings.setNoFlyPassThroughNotificationsEnabled(context, enabled)
                    },
                    onNfcNotificationsChanged = { enabled ->
                        nfcNotificationsEnabled = enabled
                        ScanSettings.setNfcNotificationsEnabled(context, enabled)
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
                    onForeignDirectAcousticEnabledChanged = { enabled ->
                        foreignDirectAcousticEnabled = enabled
                        ScanSettings.setForeignDirectAcousticEnabled(context, enabled)
                        sensorGateSettings = readSensorGateSettings(context)
                        scope.launch {
                            alignWorkerCadenceWithSources(reasonCode = "scheduler-align-direct-acoustic")
                        }
                    },
                    onForeignDirectMagneticEnabledChanged = { enabled ->
                        foreignDirectMagneticEnabled = enabled
                        ScanSettings.setForeignDirectMagneticEnabled(context, enabled)
                        sensorGateSettings = readSensorGateSettings(context)
                        scope.launch {
                            alignWorkerCadenceWithSources(reasonCode = "scheduler-align-direct-magnetic")
                        }
                    },
                    onSetHomePointFromCurrentLocation = {
                        val currentLocation = LocationSnapshotProvider.read(context)
                        if (currentLocation == null || !isValidLatLon(currentLocation.lat, currentLocation.lon)) {
                            "Current location unavailable. Wait for a GNSS fix and try again."
                        } else {
                            val existingRadiusMeters = homePoint?.radiusMeters
                                ?: ScanSettings.DEFAULT_HOME_POINT_RADIUS_METERS
                            ScanSettings.setHomePoint(
                                context = context,
                                lat = currentLocation.lat,
                                lon = currentLocation.lon,
                                radiusMeters = existingRadiusMeters
                            )
                            homePoint = ScanSettings.getHomePoint(context)
                            val safeHomePoint = homePoint
                            if (safeHomePoint == null) {
                                "Home point could not be saved."
                            } else {
                                "Home point set to ${String.format(Locale.US, "%.5f", safeHomePoint.lat)}, ${String.format(Locale.US, "%.5f", safeHomePoint.lon)}"
                            }
                        }
                    },
                    onSetHomePointRadiusMeters = { radiusMeters ->
                        val updated = ScanSettings.setHomePointRadiusMeters(context, radiusMeters)
                        homePoint = ScanSettings.getHomePoint(context)
                        val updatedHomePoint = homePoint
                        if (!updated || updatedHomePoint == null) {
                            "Home point is not set. Set a Home Point first."
                        } else {
                            "Home radius set to ${String.format(Locale.US, "%.0f", updatedHomePoint.radiusMeters)} m"
                        }
                    },
                    onClearHomePoint = {
                        ScanSettings.clearHomePoint(context)
                        homePoint = null
                        "Home point cleared."
                    },
                    onMapClusteringEnabledChanged = { enabled ->
                        mapClusteringEnabled = enabled
                        ScanSettings.setMapClusteringEnabled(context, enabled)
                    },
                    onMapClusterRangeLevelSelected = { level ->
                        mapClusterRangeLevel = level
                        ScanSettings.setMapClusterRangeLevel(context, level)
                    },
                    onMapTrafficEnabledChanged = { enabled ->
                        mapTrafficEnabled = enabled
                        ScanSettings.setMapTrafficEnabled(context, enabled)
                    },
                    onMapNoFlyZonesEnabledChanged = { enabled ->
                        mapNoFlyZonesEnabled = enabled
                        ScanSettings.setMapNoFlyZonesEnabled(context, enabled)
                    },
                    onMapNoFlyRenderQualityLevelSelected = { level ->
                        mapNoFlyRenderQualityLevel = level
                        ScanSettings.setMapNoFlyRenderQualityLevel(context, level)
                    },
                    onMapScannerSweepAnimationEnabledChanged = { enabled ->
                        mapScannerSweepAnimationEnabled = enabled
                        ScanSettings.setMapScannerSweepAnimationEnabled(context, enabled)
                    },
                    onWifiRandomizedOneOffSuppressionEnabledChanged = { enabled ->
                        wifiRandomizedOneOffSuppressionEnabled = enabled
                        ScanSettings.setWifiRandomizedOneOffSuppressionEnabled(context, enabled)
                    },
                    onWifiAggregateOnlyEnabledChanged = { enabled ->
                        wifiAggregateOnlyEnabled = enabled
                        ScanSettings.setWifiAggregateOnlyEnabled(context, enabled)
                    },
                    onBleRandomizedOneOffSuppressionEnabledChanged = { enabled ->
                        bleRandomizedOneOffSuppressionEnabled = enabled
                        ScanSettings.setBleRandomizedOneOffSuppressionEnabled(context, enabled)
                    },
                    onBleAggregateOnlyEnabledChanged = { enabled ->
                        bleAggregateOnlyEnabled = enabled
                        ScanSettings.setBleAggregateOnlyEnabled(context, enabled)
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
                        mapClusteringEnabled = ScanSettings.isMapClusteringEnabled(context)
                        mapClusterRangeLevel = ScanSettings.getMapClusterRangeLevel(context)
                        mapTrafficEnabled = ScanSettings.isMapTrafficEnabled(context)
                        mapNoFlyZonesEnabled = ScanSettings.isMapNoFlyZonesEnabled(context)
                        mapNoFlyRenderQualityLevel = ScanSettings.getMapNoFlyRenderQualityLevel(context)
                        mapScannerSweepAnimationEnabled = ScanSettings.isMapScannerSweepAnimationEnabled(context)
                        wifiRandomizedOneOffSuppressionEnabled = ScanSettings.isWifiRandomizedOneOffSuppressionEnabled(context)
                        wifiAggregateOnlyEnabled = ScanSettings.isWifiAggregateOnlyEnabled(context)
                        bleRandomizedOneOffSuppressionEnabled = ScanSettings.isBleRandomizedOneOffSuppressionEnabled(context)
                        bleAggregateOnlyEnabled = ScanSettings.isBleAggregateOnlyEnabled(context)
                        sourceScanIntervals = ScanSettings.getAllSourceScanIntervalSeconds(context)
                        sourceLastScanEpochs = ScanSettings.getAllSourceLastScanEpochMs(context)
                        sourceLastRawObservationEpochs = ScanSettings.getAllSourceLastRawObservationEpochMs(context)
                        appThemeMode = runCatching { AppThemeMode.valueOf(ScanSettings.getAppThemeMode(context)) }
                            .getOrDefault(AppThemeMode.DARK)
                        approachNotificationsEnabled = ScanSettings.isApproachNotificationsEnabled(context)
                        trackerNotificationsEnabled = ScanSettings.isTrackerNotificationsEnabled(context)
                        flockNotificationsEnabled = ScanSettings.isFlockNotificationsEnabled(context)
                        noFlyPassThroughNotificationsEnabled = ScanSettings.isNoFlyPassThroughNotificationsEnabled(context)
                        nfcNotificationsEnabled = ScanSettings.isNfcNotificationsEnabled(context)
                        magneticIncreaseNotificationsEnabled = ScanSettings.isMagneticIncreaseNotificationsEnabled(context)
                        meshConnectivityNotificationsEnabled = ScanSettings.isMeshConnectivityNotificationsEnabled(context)
                        meshWipeNotificationsEnabled = ScanSettings.isMeshWipeNotificationsEnabled(context)
                        foreignDirectAcousticEnabled = ScanSettings.isForeignDirectAcousticEnabled(context)
                        foreignDirectMagneticEnabled = ScanSettings.isForeignDirectMagneticEnabled(context)
                        homePoint = ScanSettings.getHomePoint(context)
                        lastScanDurationMs = ScanSettings.getLastScanDurationMs(context)
                        sourceScanTimings = ScanSettings.getSourceScanTimings(context)
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
                        mapClusteringEnabled = ScanSettings.isMapClusteringEnabled(context)
                        mapClusterRangeLevel = ScanSettings.getMapClusterRangeLevel(context)
                        mapTrafficEnabled = ScanSettings.isMapTrafficEnabled(context)
                        mapNoFlyZonesEnabled = ScanSettings.isMapNoFlyZonesEnabled(context)
                        mapNoFlyRenderQualityLevel = ScanSettings.getMapNoFlyRenderQualityLevel(context)
                        mapScannerSweepAnimationEnabled = ScanSettings.isMapScannerSweepAnimationEnabled(context)
                        wifiRandomizedOneOffSuppressionEnabled = ScanSettings.isWifiRandomizedOneOffSuppressionEnabled(context)
                        wifiAggregateOnlyEnabled = ScanSettings.isWifiAggregateOnlyEnabled(context)
                        bleRandomizedOneOffSuppressionEnabled = ScanSettings.isBleRandomizedOneOffSuppressionEnabled(context)
                        bleAggregateOnlyEnabled = ScanSettings.isBleAggregateOnlyEnabled(context)
                        sourceScanIntervals = ScanSettings.getAllSourceScanIntervalSeconds(context)
                        sourceLastScanEpochs = ScanSettings.getAllSourceLastScanEpochMs(context)
                        sourceLastRawObservationEpochs = ScanSettings.getAllSourceLastRawObservationEpochMs(context)
                        appThemeMode = runCatching { AppThemeMode.valueOf(ScanSettings.getAppThemeMode(context)) }
                            .getOrDefault(AppThemeMode.DARK)
                        approachNotificationsEnabled = ScanSettings.isApproachNotificationsEnabled(context)
                        trackerNotificationsEnabled = ScanSettings.isTrackerNotificationsEnabled(context)
                        flockNotificationsEnabled = ScanSettings.isFlockNotificationsEnabled(context)
                        noFlyPassThroughNotificationsEnabled = ScanSettings.isNoFlyPassThroughNotificationsEnabled(context)
                        nfcNotificationsEnabled = ScanSettings.isNfcNotificationsEnabled(context)
                        magneticIncreaseNotificationsEnabled = ScanSettings.isMagneticIncreaseNotificationsEnabled(context)
                        meshConnectivityNotificationsEnabled = ScanSettings.isMeshConnectivityNotificationsEnabled(context)
                        meshWipeNotificationsEnabled = ScanSettings.isMeshWipeNotificationsEnabled(context)
                        foreignDirectAcousticEnabled = ScanSettings.isForeignDirectAcousticEnabled(context)
                        foreignDirectMagneticEnabled = ScanSettings.isForeignDirectMagneticEnabled(context)
                        homePoint = ScanSettings.getHomePoint(context)
                        lastScanDurationMs = ScanSettings.getLastScanDurationMs(context)
                        sourceScanTimings = ScanSettings.getSourceScanTimings(context)
                        scanIntervalChangeEvents = ScanSettings.getScanIntervalChangeEvents(context, 10)
                        alertLogs = AlertLogStore.read(context)
                        ownedDeviceKeys = OwnedDeviceRegistry.read(context)
                        MeshForegroundServiceController.ensureState(context)
                        viewModel.refreshSummary()
                        "Encrypted backup imported from $fileName"
                    },
                    onResetDefaults = {
                        ScanSettings.setWifiSensorEnabled(context, true)
                        ScanSettings.setBleSensorEnabled(context, true)
                        ScanSettings.setCellularSensorEnabled(context, true)
                        ScanSettings.setAviationAdsbSensorEnabled(context, true)
                        ScanSettings.setAviationPublicSensorEnabled(context, true)
                        ScanSettings.setSdrSensorEnabled(context, true)

                        ScanSettings.setMapClusteringEnabled(context, ScanSettings.DEFAULT_MAP_CLUSTERING_ENABLED)
                        ScanSettings.setMapClusterRangeLevel(context, ScanSettings.DEFAULT_MAP_CLUSTER_RANGE_LEVEL)
                        ScanSettings.setMapTrafficEnabled(context, ScanSettings.DEFAULT_MAP_TRAFFIC_ENABLED)
                        ScanSettings.setMapNoFlyZonesEnabled(context, ScanSettings.DEFAULT_MAP_NO_FLY_ZONES_ENABLED)
                        ScanSettings.setMapNoFlyRenderQualityLevel(context, ScanSettings.DEFAULT_MAP_NO_FLY_RENDER_QUALITY_LEVEL)
                        ScanSettings.setMapScannerSweepAnimationEnabled(
                            context,
                            ScanSettings.DEFAULT_MAP_SCANNER_SWEEP_ANIMATION_ENABLED
                        )
                        ScanSettings.setWifiRandomizedOneOffSuppressionEnabled(
                            context,
                            ScanSettings.DEFAULT_WIFI_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED
                        )
                        ScanSettings.setWifiAggregateOnlyEnabled(context, ScanSettings.DEFAULT_WIFI_AGGREGATE_ONLY_ENABLED)
                        ScanSettings.setBleRandomizedOneOffSuppressionEnabled(
                            context,
                            ScanSettings.DEFAULT_BLE_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED
                        )
                        ScanSettings.setBleAggregateOnlyEnabled(context, ScanSettings.DEFAULT_BLE_AGGREGATE_ONLY_ENABLED)
                        ScanSettings.setAppThemeMode(context, ScanSettings.DEFAULT_APP_THEME_MODE)
                        ScanSettings.setApproachDetectionEnabled(context, true)
                        ScanSettings.setApproachNotificationsEnabled(context, false)
                        ScanSettings.setTrackerNotificationsEnabled(context, true)
                        ScanSettings.setFlockNotificationsEnabled(context, true)
                        ScanSettings.setNoFlyPassThroughNotificationsEnabled(context, true)
                        ScanSettings.setNfcNotificationsEnabled(context, true)
                        ScanSettings.setMagneticIncreaseNotificationsEnabled(context, true)
                        ScanSettings.setMeshConnectivityNotificationsEnabled(context, false)
                        ScanSettings.setMeshWipeNotificationsEnabled(context, true)
                        ScanSettings.setForeignDirectAcousticEnabled(context, false)
                        ScanSettings.setForeignDirectMagneticEnabled(context, false)
                        ScanSettings.clearHomePoint(context)

                        intervalManagedSourceTypes.forEach { sourceType ->
                            val defaultSeconds = when (sourceType) {
                                SourceCatalog.KEY_AIRCRAFT -> ScanSettings.DEFAULT_AIRCRAFT_SOURCE_SCAN_INTERVAL_SECONDS
                                SourceCatalog.KEY_CAMERA -> ScanSettings.DEFAULT_CAMERA_SOURCE_SCAN_INTERVAL_SECONDS
                                else -> ScanSettings.DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS
                            }
                            ScanSettings.setSourceScanIntervalSeconds(context, sourceType, defaultSeconds)
                        }

                        mapClusteringEnabled = ScanSettings.DEFAULT_MAP_CLUSTERING_ENABLED
                        mapClusterRangeLevel = ScanSettings.DEFAULT_MAP_CLUSTER_RANGE_LEVEL
                        mapTrafficEnabled = ScanSettings.DEFAULT_MAP_TRAFFIC_ENABLED
                        mapNoFlyZonesEnabled = ScanSettings.DEFAULT_MAP_NO_FLY_ZONES_ENABLED
                        mapNoFlyRenderQualityLevel = ScanSettings.DEFAULT_MAP_NO_FLY_RENDER_QUALITY_LEVEL
                        mapScannerSweepAnimationEnabled = ScanSettings.DEFAULT_MAP_SCANNER_SWEEP_ANIMATION_ENABLED
                        wifiRandomizedOneOffSuppressionEnabled = ScanSettings.DEFAULT_WIFI_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED
                        wifiAggregateOnlyEnabled = ScanSettings.DEFAULT_WIFI_AGGREGATE_ONLY_ENABLED
                        bleRandomizedOneOffSuppressionEnabled = ScanSettings.DEFAULT_BLE_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED
                        bleAggregateOnlyEnabled = ScanSettings.DEFAULT_BLE_AGGREGATE_ONLY_ENABLED
                        sourceScanIntervals = ScanSettings.getAllSourceScanIntervalSeconds(context)
                        appThemeMode = runCatching { AppThemeMode.valueOf(ScanSettings.DEFAULT_APP_THEME_MODE) }
                            .getOrDefault(AppThemeMode.DARK)
                        approachDetectionEnabled = true
                        approachNotificationsEnabled = false
                        trackerNotificationsEnabled = true
                        flockNotificationsEnabled = true
                        noFlyPassThroughNotificationsEnabled = true
                        nfcNotificationsEnabled = true
                        magneticIncreaseNotificationsEnabled = true
                        meshConnectivityNotificationsEnabled = false
                        meshWipeNotificationsEnabled = true
                        foreignDirectAcousticEnabled = false
                        foreignDirectMagneticEnabled = false
                        homePoint = null
                        sensorGateSettings = readSensorGateSettings(context)
                        sensorStatuses = SensorStatusProvider.read(context)

                        scope.launch {
                            alignWorkerCadenceWithSources(reasonCode = "scheduler-align-defaults-reset")
                        }

                        "Settings reset to defaults."
                    },
                    onSoftReset = {
                        app.container.repository.clearEncounters()
                        app.container.repository.clearDevices()
                        ScanSettings.clearOperationalLogs(context)
                        startupPrewarmedDevicePins = emptyList()
                        startupPrewarmedNoFlyZones = emptyList()
                        mapResetGeneration += 1L
                        alertLogs = emptyList()
                        errorLogs = emptyList()
                        scanIntervalChangeEvents = emptyList()
                        viewModel.refreshSummary()
                        "Soft reset completed: local encounters/devices/logs cleared."
                    },
                    onHardReset = {
                        app.container.repository.clearEncounters()
                        app.container.repository.clearDevices()
                        ScanSettings.clearOperationalLogs(context)
                        ScanSettings.resetMeshNetworkSettings(context)
                        startupPrewarmedDevicePins = emptyList()
                        startupPrewarmedNoFlyZones = emptyList()
                        mapResetGeneration += 1L
                        app.container.chainLinkCoordinator.stopServer()
                        MeshForegroundServiceController.ensureState(context)
                        alertLogs = emptyList()
                        errorLogs = emptyList()
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
                            .getOrDefault(AppThemeMode.DARK)
                        approachNotificationsEnabled = ScanSettings.isApproachNotificationsEnabled(context)
                        trackerNotificationsEnabled = ScanSettings.isTrackerNotificationsEnabled(context)
                        flockNotificationsEnabled = ScanSettings.isFlockNotificationsEnabled(context)
                        noFlyPassThroughNotificationsEnabled = ScanSettings.isNoFlyPassThroughNotificationsEnabled(context)
                        nfcNotificationsEnabled = ScanSettings.isNfcNotificationsEnabled(context)
                        magneticIncreaseNotificationsEnabled = ScanSettings.isMagneticIncreaseNotificationsEnabled(context)
                        meshConnectivityNotificationsEnabled = ScanSettings.isMeshConnectivityNotificationsEnabled(context)
                        meshWipeNotificationsEnabled = ScanSettings.isMeshWipeNotificationsEnabled(context)
                        foreignDirectAcousticEnabled = ScanSettings.isForeignDirectAcousticEnabled(context)
                        foreignDirectMagneticEnabled = ScanSettings.isForeignDirectMagneticEnabled(context)
                        homePoint = ScanSettings.getHomePoint(context)
                        mapClusteringEnabled = ScanSettings.isMapClusteringEnabled(context)
                        mapClusterRangeLevel = ScanSettings.getMapClusterRangeLevel(context)
                        mapTrafficEnabled = ScanSettings.isMapTrafficEnabled(context)
                        mapNoFlyZonesEnabled = ScanSettings.isMapNoFlyZonesEnabled(context)
                        mapNoFlyRenderQualityLevel = ScanSettings.getMapNoFlyRenderQualityLevel(context)
                        mapScannerSweepAnimationEnabled = ScanSettings.isMapScannerSweepAnimationEnabled(context)
                        wifiRandomizedOneOffSuppressionEnabled = ScanSettings.isWifiRandomizedOneOffSuppressionEnabled(context)
                        wifiAggregateOnlyEnabled = ScanSettings.isWifiAggregateOnlyEnabled(context)
                        bleRandomizedOneOffSuppressionEnabled = ScanSettings.isBleRandomizedOneOffSuppressionEnabled(context)
                        bleAggregateOnlyEnabled = ScanSettings.isBleAggregateOnlyEnabled(context)
                        viewModel.refreshSummary()
                        "Hard reset completed: local data/logs cleared and mesh settings reset."
                    }
                )
            }

            composable(DETECTION_ROUTE) {
                DetectionPage(
                    initialTabRequest = detectionInitialTabRequest,
                    onInitialTabRequestHandled = { detectionInitialTabRequest = null },
                    readinessItems = readinessItems,
                    encounters = recentPipelineEncounters,
                    meshInsightEncounters = allPipelineEncounters,
                    approachDetectionEnabled = approachDetectionEnabled,
                    ownedDeviceKeys = ownedDeviceKeys,
                    chainLinkEnabled = chainLinkEnabled,
                    chainNodeId = chainNodeId,
                    chainDeviceName = chainDeviceName,
                    chainSharedSecret = chainSharedSecret,
                    chainAutoSyncEnabled = chainAutoSyncEnabled,
                    chainSharePreciseLocationEnabled = chainSharePreciseLocationEnabled,
                    liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds,
                    sourceScanIntervals = sourceScanIntervals,
                    startupPrewarmedDevicePins = startupPrewarmedDevicePins,
                    startupPrewarmedNoFlyZones = startupPrewarmedNoFlyZones,
                    mapResetGeneration = mapResetGeneration,
                    wifiRandomizedOneOffSuppressionEnabled = wifiRandomizedOneOffSuppressionEnabled,
                    bleRandomizedOneOffSuppressionEnabled = bleRandomizedOneOffSuppressionEnabled,
                    mapNoFlyZonesEnabled = mapNoFlyZonesEnabled,
                    mapNoFlyRenderQualityLevel = mapNoFlyRenderQualityLevel,
                    mapTrafficEnabled = mapTrafficEnabled,
                    mapClusteringEnabled = mapClusteringEnabled,
                    onMapClusteringEnabledChanged = { enabled ->
                        mapClusteringEnabled = enabled
                        ScanSettings.setMapClusteringEnabled(context, enabled)
                    },
                    mapClusterRangeLevel = mapClusterRangeLevel,
                    onMapClusterRangeLevelChanged = { level ->
                        mapClusterRangeLevel = level
                        ScanSettings.setMapClusterRangeLevel(context, level)
                    },
                    mapScannerSweepAnimationEnabled = mapScannerSweepAnimationEnabled,
                    chainMeshSnapshot = chainMesh,
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
                    onDeviceClick = { device ->
                        navController.navigate(
                            "deviceDetail/${Uri.encode(device.source)}/${Uri.encode(device.primaryId)}"
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
                            val (scanResult, chainStats) = withContext(Dispatchers.IO) {
                                val scan = app.container.sensingService.collectBatchWithMetrics()
                                app.container.repository.insertBatch(scan.encounters)
                                val chain = app.container.chainLinkCoordinator.syncNow()
                                scan to chain
                            }
                            ScanSettings.setLastScanDurationMs(context, scanResult.totalDurationMs)
                            scanResult.sourceDurationsMs.forEach { (sourceType, durationMs) ->
                                ScanSettings.recordSourceScanDurationMs(context, sourceType, durationMs)
                            }
                            lastScanDurationMs = scanResult.totalDurationMs
                            sourceScanTimings = ScanSettings.getSourceScanTimings(context)
                            viewModel.refreshSummary()
                            readinessItems = DetectionReadinessAdvisor.evaluate(context)
                            errorLogs = OperationalErrorLogStore.read(context)
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
                            if (error is CancellationException) {
                                throw error
                            }
                            OperationalErrorLogStore.append(
                                context = context,
                                category = "LIVE_COLLECT",
                                source = "system",
                                message = error.message ?: "unknown error"
                            )
                            errorLogs = OperationalErrorLogStore.read(context)
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
                    onChainLinkChanged = { enabled ->
                        chainLinkEnabled = enabled
                        ScanSettings.setChainLinkEnabled(context, enabled)
                        if (enabled && chainSharedSecret.isNotBlank() && !chainPersistentChannelEnabled) {
                            chainPersistentChannelEnabled = true
                            ScanSettings.setChainPersistentChannelEnabled(context, true)
                        }
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
                        if (chainLinkEnabled && chainSharedSecret.isNotBlank() && !chainPersistentChannelEnabled) {
                            chainPersistentChannelEnabled = true
                            ScanSettings.setChainPersistentChannelEnabled(context, true)
                            app.container.chainLinkCoordinator.ensureServerRunning()
                            MeshForegroundServiceController.ensureState(context)
                        }
                    },
                    onChainAutoSyncChanged = { enabled ->
                        chainAutoSyncEnabled = enabled
                        ScanSettings.setChainAutoSyncEnabled(context, enabled)
                    },
                    onChainSharePreciseLocationChanged = { enabled ->
                        chainSharePreciseLocationEnabled = enabled
                        ScanSettings.setChainSharePreciseLocationEnabled(context, enabled)
                    },
                    onRefreshPeers = {
                        app.container.chainLinkCoordinator.refreshPeers()
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
                        coroutineScope {
                            val wipeDeferred = async {
                                app.container.chainLinkCoordinator.wipeMeshDataAcrossPeers()
                            }

                            repeat(4) {
                                delay(250L)
                                viewModel.refreshSummary()
                                if (wipeDeferred.isCompleted) return@repeat
                            }

                            val wipe = wipeDeferred.await()
                            viewModel.refreshSummary()
                            when {
                                !wipe.enabled -> "Local soft reset applied (encounters/devices/logs). Chain link is disabled, so no remote peers were targeted."
                                !wipe.authConfigured -> "Local soft reset applied (encounters/devices/logs). Configure a shared chain passphrase to wipe linked peers."
                                wipe.failures > 0 -> "Soft reset incomplete across mesh: local cleared, peers reset ${wipe.peersWiped}/${wipe.peersTargeted}, failures ${wipe.failures}. Scan gate remains active until all targeted peers complete reset."
                                else -> "Soft reset completed across mesh: local + peers reset ${wipe.peersWiped}/${wipe.peersTargeted}. Scan gate released across mesh."
                            }
                        }
                    }
                )
            }

            composable(LOGS_ROUTE) {
                LogsHubPage(
                    logs = alertLogs,
                    errorLogs = errorLogs,
                    recentEncounters = recent100PipelineEncounters,
                    allEncounters = allPipelineEncounters,
                    ownedDeviceKeys = ownedDeviceKeys,
                    initialTab = 0,
                    onClearLogs = {
                        AlertLogStore.clear(context)
                        alertLogs = emptyList()
                    },
                    onClearErrorLogs = {
                        OperationalErrorLogStore.clear(context)
                        errorLogs = emptyList()
                    },
                    onOpenApproachMap = { source, primaryId ->
                        navController.navigate(
                            "approachAlertMap/${Uri.encode(source)}/${Uri.encode(primaryId)}"
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onOpenNoFlyIncidentPath = { source, primaryId, zoneSummary, eventEpochMs ->
                        navController.navigate(
                            "noFlyIncidentPath/${Uri.encode(source)}/${Uri.encode(primaryId)}/${Uri.encode(zoneSummary)}/$eventEpochMs"
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onOpenDeviceDetails = { source, primaryId ->
                        navController.navigate(
                            "deviceDetail/${Uri.encode(source)}/${Uri.encode(primaryId)}"
                        )
                    },
                    onEncounterClick = { encounter ->
                        navController.navigate(
                            "encounterDetail/${Uri.encode(encounter.source.name)}/${Uri.encode(encounter.primaryId)}/${encounter.timestampEpochMs}"
                        )
                    }
                )
            }

            composable(LOGS_ENCOUNTERS_ROUTE) {
                LogsHubPage(
                    logs = alertLogs,
                    errorLogs = errorLogs,
                    recentEncounters = recent100PipelineEncounters,
                    allEncounters = allPipelineEncounters,
                    ownedDeviceKeys = ownedDeviceKeys,
                    initialTab = 2,
                    onClearLogs = {
                        AlertLogStore.clear(context)
                        alertLogs = emptyList()
                    },
                    onClearErrorLogs = {
                        OperationalErrorLogStore.clear(context)
                        errorLogs = emptyList()
                    },
                    onOpenApproachMap = { source, primaryId ->
                        navController.navigate(
                            "approachAlertMap/${Uri.encode(source)}/${Uri.encode(primaryId)}"
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onOpenNoFlyIncidentPath = { source, primaryId, zoneSummary, eventEpochMs ->
                        navController.navigate(
                            "noFlyIncidentPath/${Uri.encode(source)}/${Uri.encode(primaryId)}/${Uri.encode(zoneSummary)}/$eventEpochMs"
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onOpenDeviceDetails = { source, primaryId ->
                        navController.navigate(
                            "deviceDetail/${Uri.encode(source)}/${Uri.encode(primaryId)}"
                        )
                    },
                    onEncounterClick = { encounter ->
                        navController.navigate(
                            "encounterDetail/${Uri.encode(encounter.source.name)}/${Uri.encode(encounter.primaryId)}/${encounter.timestampEpochMs}"
                        )
                    }
                )
            }

            composable(DEVICES_ENCOUNTERS_ROUTE) {
                LogsHubPage(
                    logs = alertLogs,
                    errorLogs = errorLogs,
                    recentEncounters = recent100PipelineEncounters,
                    allEncounters = allPipelineEncounters,
                    ownedDeviceKeys = ownedDeviceKeys,
                    initialTab = 2,
                    onClearLogs = {
                        AlertLogStore.clear(context)
                        alertLogs = emptyList()
                    },
                    onClearErrorLogs = {
                        OperationalErrorLogStore.clear(context)
                        errorLogs = emptyList()
                    },
                    onOpenApproachMap = { source, primaryId ->
                        navController.navigate(
                            "approachAlertMap/${Uri.encode(source)}/${Uri.encode(primaryId)}"
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onOpenNoFlyIncidentPath = { source, primaryId, zoneSummary, eventEpochMs ->
                        navController.navigate(
                            "noFlyIncidentPath/${Uri.encode(source)}/${Uri.encode(primaryId)}/${Uri.encode(zoneSummary)}/$eventEpochMs"
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onOpenDeviceDetails = { source, primaryId ->
                        navController.navigate(
                            "deviceDetail/${Uri.encode(source)}/${Uri.encode(primaryId)}"
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
                val deviceEncounters = remember(allPipelineEncounters, source, primaryId) {
                    allPipelineEncounters.filter { it.source.name == source && it.primaryId == primaryId }
                }
                val item = remember(
                    deviceEncounters,
                    source,
                    primaryId,
                    approachDetectionEnabled,
                    ownedDeviceKeys,
                    wifiRandomizedOneOffSuppressionEnabled,
                    bleRandomizedOneOffSuppressionEnabled
                ) {
                    buildSingleDeviceItem(
                        source = source,
                        primaryId = primaryId,
                        groupedEncounters = deviceEncounters,
                        approachDetectionEnabled = approachDetectionEnabled,
                        ownedDeviceKeys = ownedDeviceKeys,
                        suppressLikelyRandomizedWifiOneOffs = wifiRandomizedOneOffSuppressionEnabled,
                        suppressLikelyRandomizedBleOneOffs = bleRandomizedOneOffSuppressionEnabled
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
                val encounter = allPipelineEncounters.firstOrNull {
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
                    encounters = allPipelineEncounters,
                    nowEpochMs = appNowEpochMs,
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
                    encounters = allPipelineEncounters,
                    incidentZoneSummary = null,
                    incidentEventEpochMs = null,
                    onOpenDeviceDetails = { detailSource, detailPrimaryId ->
                        navController.navigate(
                            "deviceDetail/${Uri.encode(detailSource)}/${Uri.encode(detailPrimaryId)}"
                        )
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(NO_FLY_INCIDENT_PATH_ROUTE) { entry ->
                val source = Uri.decode(entry.arguments?.getString("source") ?: "")
                val primaryId = Uri.decode(entry.arguments?.getString("primaryId") ?: "")
                val zoneSummary = Uri.decode(entry.arguments?.getString("zoneSummary") ?: "")
                val eventEpochMs = entry.arguments?.getString("eventEpochMs")?.toLongOrNull()
                val incidentLat = entry.arguments?.getString("lat")?.toDoubleOrNull()
                val incidentLon = entry.arguments?.getString("lon")?.toDoubleOrNull()
                val enteredZoneIds = entry.arguments
                    ?.getString("zoneIds")
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    .orEmpty()
                val incidentLocation = if (isValidLatLon(incidentLat, incidentLon)) {
                    DetectionLocation(incidentLat!!, incidentLon!!)
                } else {
                    null
                }
                MovingDevicePathMapPage(
                    source = source,
                    primaryId = primaryId,
                    encounters = allPipelineEncounters,
                    incidentZoneSummary = zoneSummary.takeIf { it.isNotBlank() },
                    incidentEventEpochMs = eventEpochMs,
                    fallbackIncidentLocation = incidentLocation,
                    enteredZoneIds = enteredZoneIds,
                    onOpenDeviceDetails = { detailSource, detailPrimaryId ->
                        navController.navigate(
                            "deviceDetail/${Uri.encode(detailSource)}/${Uri.encode(detailPrimaryId)}"
                        )
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            }

            if (chainLinkEnabled) {
                val hasConnectedPeer = chainMesh.peers.any { it.state == ChainPeerState.CONNECTED }
                val isConnecting = !hasConnectedPeer && chainMesh.peers.any {
                    it.state == ChainPeerState.DISCOVERED || it.state == ChainPeerState.REQUESTED
                }
                val statusColor = when {
                    hasConnectedPeer -> Color(0xFF2E7D32)
                    isConnecting -> Color(0xFFE65100)
                    else -> Color(0xFFB3261E)
                }
                val statusLabel = when {
                    hasConnectedPeer -> "Connected"
                    isConnecting -> "Connecting"
                    else -> "Disconnected"
                }

                Card(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomStart)
                        .padding(12.dp)
                        .clickable {
                            detectionInitialTabRequest = DETECTION_TAB_MESH_INDEX
                            navController.navigate(DETECTION_ROUTE) {
                                launchSingleTop = true
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("●", color = statusColor, fontWeight = FontWeight.Bold)
                        Text("Mesh", fontWeight = FontWeight.Medium)
                        Text(statusLabel, color = statusColor, fontWeight = FontWeight.Medium)
                    }
                }
            }
            }

            if (!appRuntimeReady || !startupRuntimeGateReleased) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                ) {
                    Column(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.Center)
                            .padding(20.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val startupChecks = listOf(
                            startupConfigLoaded,
                            trackingObserverInitialized,
                            operationalObserverInitialized,
                            mapPrewarmReady,
                            mapDataPrewarmReady,
                            startupBootstrapGateSatisfied
                        )
                        val startupProgress = startupChecks.count { it }.toFloat() / startupChecks.size.toFloat()
                        val startupProgressPercent = (startupProgress * 100f).toInt().coerceIn(0, 100)

                        CircularProgressIndicator()
                        LinearProgressIndicator(
                            progress = { startupProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 360.dp)
                        )
                        Text(
                            "Startup progress: $startupProgressPercent%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (appRuntimeReady && !startupRuntimeGateReleased) {
                                if (startupBootstrapWaitRequired) {
                                    "Waiting for startup scan to complete..."
                                } else {
                                    "Preparing Argus runtime..."
                                }
                            } else {
                                "Preparing Argus runtime..."
                            }
                        )
                        Text(
                            text = buildString {
                                append("Config: ")
                                append(if (startupConfigLoaded) "ok" else "loading")
                                append(" • Tracking observer: ")
                                append(if (trackingObserverInitialized) "ok" else "waiting")
                                append(" • Scan observer: ")
                                append(if (operationalObserverInitialized) "ok" else "waiting")
                                append(" • Maps prewarm: ")
                                append(if (mapPrewarmReady) "ok" else "warming")
                                append(" • Map data prewarm: ")
                                append(if (mapDataPrewarmReady) "ok" else "warming")
                                append(" • Startup scan: ")
                                append(
                                    if (!startupBootstrapWaitRequired) {
                                        "skipped (<1h since last start)"
                                    } else if (startupBootstrapScanCompleted) {
                                        "complete"
                                    } else {
                                        "running"
                                    }
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapEnginePrewarmHost(
    onReady: () -> Unit
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.4219999, -122.0840575), 2f)
    }
    var completed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .widthIn(max = 1.dp)
            .height(1.dp)
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = false,
                mapStyleOptions = null
            ),
            onMapLoaded = {
                if (!completed) {
                    completed = true
                    onReady()
                }
            },
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                zoomGesturesEnabled = false,
                scrollGesturesEnabled = false,
                tiltGesturesEnabled = false,
                rotationGesturesEnabled = false,
                myLocationButtonEnabled = false
            )
        ) {
            // Intentional no-op: this host exists only to warm map rendering internals.
        }
    }
}

@Composable
private fun ApproachAlertMapPage(
    source: String,
    primaryId: String,
    isOwnedTarget: Boolean,
    encounters: List<Encounter>,
    nowEpochMs: Long,
    liveMapUpdateIntervalSeconds: Long,
    onOpenDeviceDetails: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mapStyleOptions = rememberMapStyleOptionsForTheme()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.4219999, -122.0840575), 15f)
    }

    var observerLocation by remember { mutableStateOf(LocationSnapshotProvider.read(context)) }
    var observerLastUpdatedEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }
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
        val updateIntervalMs = liveMapUpdateIntervalSeconds.coerceAtLeast(1L) * 1000L
        LocationSnapshotProvider.observe(context, minUpdateIntervalMs = updateIntervalMs).collect { latestLocation ->
            observerLocation = latestLocation
            observerLastUpdatedEpochMs = System.currentTimeMillis()
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
            properties = MapProperties(mapStyleOptions = mapStyleOptions),
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
    nowEpochMs: Long,
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
    onOpenDevicesEncounters: () -> Unit,
    startMessage: String?,
    startMessageIsError: Boolean
) {
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
    val sourceHealthSummary = when {
        currentSourceOverruns.isNotEmpty() -> "Warning: one or more active source scans are exceeding their configured interval."
        staleSourceOverruns.isNotEmpty() -> "Caution: prior scans exceeded interval, but no current active overrun is detected."
        hasFreshSourceScans -> "Healthy: no current scan overrun warnings."
        else -> "Status pending: no recent source scans yet."
    }
    val sourceHealthColor = when {
        currentSourceOverruns.isNotEmpty() -> Color(0xFFB3261E)
        staleSourceOverruns.isNotEmpty() -> Color(0xFFE65100)
        hasFreshSourceScans -> Color(0xFF2E7D32)
        else -> Color(0xFFE65100)
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text("Argus Home", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (trackingActive) "Tracking Status: Running" else "Tracking Status: Stopped",
                        fontWeight = FontWeight.Bold
                    )
                    Text("Last scan: ${lastScanEpochMs?.let(::formatEpoch) ?: "Never"}")
                    Text("Worker cadence: every ${ScanSettings.formatInterval(scanIntervalSeconds)}")
                    Text(
                        text = sourceHealthSummary,
                        color = sourceHealthColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (startMessage != null) {
                        val statusColor = if (startMessageIsError) Color(0xFFB3261E) else Color(0xFF2E7D32)
                        Text(
                            text = startMessage,
                            color = statusColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= HOME_TWO_COLUMN_MIN_WIDTH) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!trackingActive) {
                            Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                                Text("Start Tracking")
                            }
                        }
                        Button(onClick = onStop, enabled = trackingActive, modifier = Modifier.weight(1f)) {
                            Text("Stop")
                        }
                        Button(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                            Text("Refresh")
                        }
                        Button(onClick = onOpenDevicesEncounters, modifier = Modifier.weight(1f)) {
                            Text("Devices & Encounters")
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!trackingActive) {
                            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                                Text("Start Tracking")
                            }
                        }
                        Button(onClick = onStop, enabled = trackingActive, modifier = Modifier.fillMaxWidth()) {
                            Text("Stop")
                        }
                        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                            Text("Refresh")
                        }
                        Button(onClick = onOpenDevicesEncounters, modifier = Modifier.fillMaxWidth()) {
                            Text("Devices & Encounters")
                        }
                    }
                }
            }
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
        }
        if (staleSourceOverruns.isNotEmpty() && currentSourceOverruns.isEmpty()) {
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
        }

        item {
            Text("Last 24h Summary", fontWeight = FontWeight.Bold)
        }
        item {
            if (summary.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No source activity recorded in the last 24 hours.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columnSpacing = 8.dp
                    val targetCardMinWidth = 170.dp
                    val computedColumns = ((maxWidth + columnSpacing) / (targetCardMinWidth + columnSpacing))
                        .toInt()
                        .coerceIn(1, 3)

                    Column(verticalArrangement = Arrangement.spacedBy(columnSpacing)) {
                        summary.chunked(computedColumns).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(columnSpacing)
                            ) {
                                rowItems.forEach { sourceSummary ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = listSourceLabel(sourceSummary.source, null),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = sourceSummary.count.toString(),
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (sourceSummary.count == 1) "event" else "events",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                repeat(computedColumns - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
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
                    val sensorStatusByName = sensorStatuses.associateBy { it.name }
                    val toggles = listOf(
                        HomeSensorToggle(
                            "wifi",
                            "Wi-Fi",
                            "Nearby network environment",
                            sensorGateSettings.wifiEnabled,
                            sensorStatusByName["Wi-Fi"]
                        ),
                        HomeSensorToggle(
                            "bluetooth_le",
                            "Bluetooth (LE + Classic + Remote ID)",
                            "Combined Bluetooth sensor collection",
                            sensorGateSettings.bluetoothLeEnabled,
                            sensorStatusByName["Bluetooth (LE + Classic + Remote ID)"]
                        ),
                        HomeSensorToggle(
                            "cellular",
                            "Cellular",
                            "Cell and network telemetry",
                            sensorGateSettings.cellularEnabled,
                            sensorStatusByName["Cellular"]
                        ),
                        HomeSensorToggle(
                            "nfc",
                            "NFC",
                            "Nearby field tag and transponder interactions",
                            sensorGateSettings.nfcEnabled,
                            sensorStatusByName["NFC"]
                        ),
                        HomeSensorToggle(
                            "aviation_adsb",
                            "ADS-B (Aviation)",
                            "Aircraft transponder ingest feed",
                            sensorGateSettings.aviationAdsbEnabled,
                            sensorStatusByName["ADS-B (Aviation)"]
                        ),
                        HomeSensorToggle(
                            "aviation_public",
                            "Public Flight Radar",
                            "Internet-based aircraft feed",
                            sensorGateSettings.aviationPublicEnabled,
                            sensorStatusByName["Public Flight Radar"]
                        ),
                        HomeSensorToggle(
                            "sdr",
                            "SDR",
                            "Software-defined radio ingest",
                            sensorGateSettings.sdrEnabled,
                            sensorStatusByName["SDR"]
                        ),
                        HomeSensorToggle(
                            "direct_acoustic",
                            "Acoustic (Direct)",
                            "On-device acoustic proxy",
                            sensorGateSettings.directAcousticEnabled,
                            sensorStatusByName["Acoustic (Direct)"]
                        ),
                        HomeSensorToggle(
                            "direct_magnetic",
                            "Magnetometer (Direct)",
                            "On-device magnetic proxy",
                            sensorGateSettings.directMagneticEnabled,
                            sensorStatusByName["Magnetometer (Direct)"]
                        )
                    )
                    HomeResponsiveGrid(
                        items = toggles,
                        twoColumnMinWidth = HOME_TWO_COLUMN_MIN_WIDTH,
                        threeColumnMinWidth = HOME_THREE_COLUMN_MIN_WIDTH
                    ) { toggle ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(toggle.title, fontWeight = FontWeight.Medium)
                                    Text(
                                        toggle.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    toggle.status?.let { sensorStatus ->
                                        val statusText = "${if (sensorStatus.isOn) "On" else "Off"} | ${if (sensorStatus.factoredByArgus) "Factored" else "Not factored"}"
                                        val statusColor = if (sensorStatus.isOn && sensorStatus.factoredByArgus) {
                                            Color(0xFF2E7D32)
                                        } else {
                                            Color(0xFFE65100)
                                        }
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = statusColor,
                                            modifier = Modifier.padding(top = 6.dp)
                                        )
                                    }
                                }
                                Switch(
                                    checked = toggle.enabled,
                                    onCheckedChange = { onSensorGateChanged(toggle.key, it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> HomeResponsiveGrid(
    items: List<T>,
    twoColumnMinWidth: Dp,
    threeColumnMinWidth: Dp,
    itemContent: @Composable (T) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= threeColumnMinWidth -> 3
            maxWidth >= twoColumnMinWidth -> 2
            else -> 1
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { gridItem ->
                        Box(modifier = Modifier.weight(1f)) {
                            itemContent(gridItem)
                        }
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSettingsPage(
    scanIntervalSeconds: Long,
    mapClusteringEnabled: Boolean,
    mapClusterRangeLevel: Int,
    mapTrafficEnabled: Boolean,
    mapNoFlyZonesEnabled: Boolean,
    mapNoFlyRenderQualityLevel: Int,
    mapScannerSweepAnimationEnabled: Boolean,
    wifiRandomizedOneOffSuppressionEnabled: Boolean,
    wifiAggregateOnlyEnabled: Boolean,
    bleRandomizedOneOffSuppressionEnabled: Boolean,
    bleAggregateOnlyEnabled: Boolean,
    suppressedWifiRandomizedOneOffCount: Int,
    suppressedBleRandomizedOneOffCount: Int,
    sourceScanIntervals: Map<String, Long>,
    sourceLastScanEpochs: Map<String, Long>,
    lastScanDurationMs: Long?,
    sourceScanTimings: List<ScanSettings.SourceScanTiming>,
    sourceLastRawObservationEpochs: Map<String, Long>,
    scanIntervalChangeEvents: List<ScanSettings.IntervalChangeEvent>,
    appThemeMode: AppThemeMode,
    approachDetectionEnabled: Boolean,
    approachNotificationsEnabled: Boolean,
    trackerNotificationsEnabled: Boolean,
    flockNotificationsEnabled: Boolean,
    noFlyPassThroughNotificationsEnabled: Boolean,
    nfcNotificationsEnabled: Boolean,
    magneticIncreaseNotificationsEnabled: Boolean,
    meshConnectivityNotificationsEnabled: Boolean,
    meshWipeNotificationsEnabled: Boolean,
    foreignDirectAcousticEnabled: Boolean,
    foreignDirectMagneticEnabled: Boolean,
    homePoint: ScanSettings.HomePoint?,
    onSourceScanIntervalSelected: (String, Long) -> Unit,
    onAllSourceScanIntervalsSelected: (Long) -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onApproachDetectionChanged: (Boolean) -> Unit,
    onApproachNotificationsChanged: (Boolean) -> Unit,
    onTrackerNotificationsChanged: (Boolean) -> Unit,
    onFlockNotificationsChanged: (Boolean) -> Unit,
    onNoFlyPassThroughNotificationsChanged: (Boolean) -> Unit,
    onNfcNotificationsChanged: (Boolean) -> Unit,
    onMagneticIncreaseNotificationsChanged: (Boolean) -> Unit,
    onMeshConnectivityNotificationsChanged: (Boolean) -> Unit,
    onMeshWipeNotificationsChanged: (Boolean) -> Unit,
    onForeignDirectAcousticEnabledChanged: (Boolean) -> Unit,
    onForeignDirectMagneticEnabledChanged: (Boolean) -> Unit,
    onSetHomePointFromCurrentLocation: () -> String,
    onSetHomePointRadiusMeters: (Double) -> String,
    onClearHomePoint: () -> String,
    onMapClusteringEnabledChanged: (Boolean) -> Unit,
    onMapClusterRangeLevelSelected: (Int) -> Unit,
    onMapTrafficEnabledChanged: (Boolean) -> Unit,
    onMapNoFlyZonesEnabledChanged: (Boolean) -> Unit,
    onMapNoFlyRenderQualityLevelSelected: (Int) -> Unit,
    onMapScannerSweepAnimationEnabledChanged: (Boolean) -> Unit,
    onWifiRandomizedOneOffSuppressionEnabledChanged: (Boolean) -> Unit,
    onWifiAggregateOnlyEnabledChanged: (Boolean) -> Unit,
    onBleRandomizedOneOffSuppressionEnabledChanged: (Boolean) -> Unit,
    onBleAggregateOnlyEnabledChanged: (Boolean) -> Unit,
    onExportBackup: suspend () -> String,
    onExportEncryptedBackup: suspend (String) -> String,
    onImportLatestBackup: suspend () -> String,
    onImportLatestEncryptedBackup: suspend (String) -> String,
    onResetDefaults: suspend () -> String,
    onSoftReset: suspend () -> String,
    onHardReset: suspend () -> String
) {
    val scope = rememberCoroutineScope()
    var sourceIntervalExpandedFor by remember { mutableStateOf<String?>(null) }
    var allSourceIntervalsExpanded by remember { mutableStateOf(false) }
    var homePointRadiusExpanded by remember { mutableStateOf(false) }
    var themeModeExpanded by remember { mutableStateOf(false) }
    var mapClusterRangeExpanded by remember { mutableStateOf(false) }
    var noFlyRenderQualityExpanded by remember { mutableStateOf(false) }
    var backupActionInProgress by remember { mutableStateOf(false) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var backupPassphrase by rememberSaveable { mutableStateOf("") }
    var resetDialogTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultsResetDialogVisible by rememberSaveable { mutableStateOf(false) }
    var resetActionInProgress by remember { mutableStateOf(false) }
    var resetStatusMessage by remember { mutableStateOf<String?>(null) }
    var resetStatusIsError by remember { mutableStateOf(false) }
    var homePointStatusMessage by remember { mutableStateOf<String?>(null) }
    var defaultsResetInProgress by remember { mutableStateOf(false) }
    var selectedSettingsTab by rememberSaveable { mutableStateOf(0) }
    val hasStrongPassphrase = backupPassphrase.trim().length >= 8
    val settingsTabs = listOf("Look", "Schedule", "Detection", "Alerts", "Data")
    val intervalSourceTypes = ScanSettings.SOURCE_TYPES.filterNot {
        it == SourceCatalog.KEY_WIFI_DIRECT ||
            it == SourceCatalog.KEY_BT_CLASSIC ||
            it == SourceCatalog.KEY_REMOTE_ID ||
            it == SourceCatalog.KEY_NFC
    }
    val workerCadenceMs = scanIntervalSeconds * 1000L
    val workerCadenceOverrun = (lastScanDurationMs ?: 0L) > workerCadenceMs

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Button(
                    enabled = !defaultsResetInProgress,
                    onClick = {
                        defaultsResetDialogVisible = true
                        resetStatusMessage = null
                        resetStatusIsError = false
                    }
                ) {
                    Text(if (defaultsResetInProgress) "Resetting..." else "Defaults")
                }
            }
        }
        if (resetStatusMessage != null) {
            item {
                Text(
                    text = resetStatusMessage!!,
                    color = if (resetStatusIsError) Color(0xFFB3261E) else Color(0xFF2E7D32),
                    fontWeight = FontWeight.Medium
                )
            }
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
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Map Clustering", fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Enable clustering")
                            Switch(
                                checked = mapClusteringEnabled,
                                onCheckedChange = onMapClusteringEnabledChanged
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Cluster range")
                            Button(
                                onClick = { mapClusterRangeExpanded = true },
                                enabled = mapClusteringEnabled
                            ) {
                                Text(mapClusterRangeLabel(mapClusterRangeLevel))
                            }
                        }
                        DropdownMenu(
                            expanded = mapClusterRangeExpanded,
                            onDismissRequest = { mapClusterRangeExpanded = false }
                        ) {
                            ScanSettings.ALLOWED_MAP_CLUSTER_RANGE_LEVELS.forEach { level ->
                                DropdownMenuItem(
                                    text = { Text(mapClusterRangeLabel(level)) },
                                    onClick = {
                                        onMapClusterRangeLevelSelected(level)
                                        mapClusterRangeExpanded = false
                                    }
                                )
                            }
                        }
                        Text("Tight keeps clusters smaller; wide merges nearby markers earlier when zoomed out.")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Traffic layer")
                            Switch(
                                checked = mapTrafficEnabled,
                                onCheckedChange = onMapTrafficEnabledChanged
                            )
                        }
                        Text("Shows live road traffic overlays on Detection Device Map.")
                        Text("Note: Google traffic layer cannot hide only non-congested (green) segments.")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("No-fly zone overlays")
                            Switch(
                                checked = mapNoFlyZonesEnabled,
                                onCheckedChange = onMapNoFlyZonesEnabledChanged
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("No-fly render quality")
                            Button(
                                onClick = { noFlyRenderQualityExpanded = true },
                                enabled = mapNoFlyZonesEnabled
                            ) {
                                Text(mapNoFlyRenderQualityLabel(mapNoFlyRenderQualityLevel))
                            }
                        }
                        DropdownMenu(
                            expanded = noFlyRenderQualityExpanded,
                            onDismissRequest = { noFlyRenderQualityExpanded = false }
                        ) {
                            ScanSettings.ALLOWED_MAP_NO_FLY_RENDER_QUALITY_LEVELS.forEach { level ->
                                DropdownMenuItem(
                                    text = { Text(mapNoFlyRenderQualityLabel(level)) },
                                    onClick = {
                                        onMapNoFlyRenderQualityLevelSelected(level)
                                        noFlyRenderQualityExpanded = false
                                    }
                                )
                            }
                        }
                        Text("Warning: no-fly overlays can be slow to load/render and may impact map performance. Enable only when needed.")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Scanner sweep animation")
                            Switch(
                                checked = mapScannerSweepAnimationEnabled,
                                onCheckedChange = onMapScannerSweepAnimationEnabledChanged
                            )
                        }
                        Text("Radial sweep overlay animation on maps. Off by default for better performance.")
                    }
                }
            }
        }
        if (selectedSettingsTab == 1) {
            item {
                Text("Scan Cadence", fontWeight = FontWeight.Bold)
            }
            item {
                Text(
                    "Worker cadence (auto): every ${ScanSettings.formatInterval(scanIntervalSeconds)}",
                    fontWeight = FontWeight.Medium
                )
            }
            item {
                Text("Worker cadence is aligned automatically to the fastest enabled per-source interval.")
            }
            item {
                Text("Per-source intervals are the single source of truth for scan spacing.")
            }
            item {
                Text("Note: Under 15 min uses chained one-time work; 15+ min uses periodic work.")
            }
            item {
                Text("Per-Source Scan Intervals", fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Set all sources", fontWeight = FontWeight.Medium)
                        Button(onClick = { allSourceIntervalsExpanded = true }) {
                            Text("Choose interval")
                        }
                        DropdownMenu(
                            expanded = allSourceIntervalsExpanded,
                            onDismissRequest = { allSourceIntervalsExpanded = false }
                        ) {
                            ScanSettings.ALLOWED_SOURCE_SCAN_INTERVAL_SECONDS.forEach { seconds ->
                                DropdownMenuItem(
                                    text = { Text(ScanSettings.formatInterval(seconds)) },
                                    onClick = {
                                        onAllSourceScanIntervalsSelected(seconds)
                                        allSourceIntervalsExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            item {
                HomeResponsiveGrid(
                    items = intervalSourceTypes,
                    twoColumnMinWidth = 760.dp,
                    threeColumnMinWidth = 10_000.dp
                ) { sourceType ->
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
            }
            item {
                Text(
                    "Last scan duration: ${lastScanDurationMs?.let(::formatScanDuration) ?: "n/a"}",
                    fontWeight = FontWeight.Medium
                )
            }
            if (workerCadenceOverrun) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Warning: last full worker cycle exceeded worker cadence.",
                                color = Color(0xFFB3261E),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Cycle ${formatScanDuration(lastScanDurationMs ?: 0L)} > Cadence ${ScanSettings.formatInterval(scanIntervalSeconds)}",
                                color = Color(0xFFB3261E)
                            )
                            Text(
                                text = "Per-source overruns are listed below in Per-Source Scan Timing.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Text("Per-Source Scan Timing", fontWeight = FontWeight.Bold)
            }
            val timingBySource = sourceScanTimings.associateBy { it.sourceType }
            items(ScanSettings.SOURCE_TYPES) { sourceType ->
                val timing = timingBySource[sourceType]
                val lastScanEpochMs = sourceLastScanEpochs[sourceType] ?: 0L
                val directRawObservationEpochMs = sourceLastRawObservationEpochs[sourceType] ?: 0L
                val lastRawObservationEpochMs = effectiveRawObservationEpochMsForSource(
                    sourceType = sourceType,
                    sourceLastRawObservationEpochs = sourceLastRawObservationEpochs
                )
                val canonicalSourceType = canonicalScanSourceType(sourceType)
                val usesCanonicalRawObservation =
                    canonicalSourceType != sourceType &&
                        directRawObservationEpochMs <= 0L &&
                        lastRawObservationEpochMs > 0L
                val sourceLabel = formatSourceTypeLabel(sourceType)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(sourceLabel, fontWeight = FontWeight.SemiBold)
                        if (timing == null || timing.sampleCount <= 0L) {
                            Text("Samples: 0")
                            Text("No timing samples yet. Start tracking or run live scans.")
                        } else {
                            val suggestedInterval = suggestSafeIntervalSeconds(timing.p95DurationMs)
                            Text("Samples: ${timing.sampleCount}")
                            Text("Last: ${formatScanDuration(timing.lastDurationMs)} | Avg: ${formatScanDuration(timing.averageDurationMs)}")
                            Text("p50: ${formatScanDuration(timing.p50DurationMs)} | p95: ${formatScanDuration(timing.p95DurationMs)} | Max: ${formatScanDuration(timing.maxDurationMs)}")
                            Text("Suggested safe interval: ${ScanSettings.formatInterval(suggestedInterval)}")
                        }
                        val lastScanLabel = if (lastScanEpochMs > 0L) {
                            "${formatEpoch(lastScanEpochMs)} (${formatMapPinAge(lastScanEpochMs)} ago)"
                        } else {
                            "Never"
                        }
                        val rawSightingLabel = if (lastRawObservationEpochMs > 0L) {
                            val suffix = if (usesCanonicalRawObservation) {
                                " (shared via ${formatSourceTypeLabel(canonicalSourceType)} loop)"
                            } else {
                                ""
                            }
                            "${formatEpoch(lastRawObservationEpochMs)} (${formatMapPinAge(lastRawObservationEpochMs)} ago)$suffix"
                        } else {
                            "Never"
                        }
                        Text("Last scan attempt: $lastScanLabel")
                        Text("Last raw observation (pre-pipeline): $rawSightingLabel")
                        if (lastScanEpochMs > 0L && lastRawObservationEpochMs <= 0L) {
                            val warningText = if (sourceType == SourceCatalog.KEY_BT_CLASSIC) {
                                "No ACTION_FOUND frames observed yet. Check Bluetooth scanning/location settings and test near discoverable Classic hardware."
                            } else {
                                "No raw observations observed yet for $sourceLabel. Verify this source is enabled and producing scan data."
                            }
                            Text(
                                warningText,
                                color = Color(0xFFE65100)
                            )
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
        }
        if (selectedSettingsTab == 2) {
            item {
                Text("Signal Noise Filtering", fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Wi-Fi and Bluetooth LE sweeps use matching noise controls.")

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Wi-Fi Sweep", fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("Wi-Fi aggregate-only mode", fontWeight = FontWeight.Medium)
                                    Switch(
                                        checked = wifiAggregateOnlyEnabled,
                                        onCheckedChange = onWifiAggregateOnlyEnabledChanged
                                    )
                                }
                                Text("Stores each Wi-Fi sweep as one aggregate detection and keeps named Wi-Fi devices as individual entries.")
                                Text(
                                    if (wifiAggregateOnlyEnabled) {
                                        "Current: aggregate + named Wi-Fi devices only"
                                    } else {
                                        "Current: aggregate + all raw per-AP detections"
                                    }
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("Hide one-off randomized Wi-Fi IDs", fontWeight = FontWeight.Medium)
                                    Switch(
                                        checked = wifiRandomizedOneOffSuppressionEnabled,
                                        onCheckedChange = onWifiRandomizedOneOffSuppressionEnabledChanged
                                    )
                                }
                                Text("Suppresses likely-randomized Wi-Fi IDs seen only once to reduce one-off noise.")
                                Text("Currently suppressed: $suppressedWifiRandomizedOneOffCount")
                                Text(
                                    if (wifiRandomizedOneOffSuppressionEnabled) {
                                        "Current: one-off randomized Wi-Fi hidden"
                                    } else {
                                        "Current: one-off randomized Wi-Fi shown"
                                    }
                                )
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Bluetooth LE Sweep", fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("Bluetooth LE aggregate-only mode", fontWeight = FontWeight.Medium)
                                    Switch(
                                        checked = bleAggregateOnlyEnabled,
                                        onCheckedChange = onBleAggregateOnlyEnabledChanged
                                    )
                                }
                                Text("Stores each Bluetooth LE sweep as one aggregate detection and keeps named Bluetooth LE devices as individual entries.")
                                Text(
                                    if (bleAggregateOnlyEnabled) {
                                        "Current: aggregate + named Bluetooth LE devices only"
                                    } else {
                                        "Current: aggregate + all raw BLE detections"
                                    }
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("Hide one-off randomized Bluetooth LE IDs", fontWeight = FontWeight.Medium)
                                    Switch(
                                        checked = bleRandomizedOneOffSuppressionEnabled,
                                        onCheckedChange = onBleRandomizedOneOffSuppressionEnabledChanged
                                    )
                                }
                                Text("Suppresses likely-randomized Bluetooth LE IDs seen only once to reduce one-off noise.")
                                Text("Currently suppressed: $suppressedBleRandomizedOneOffCount")
                                Text(
                                    if (bleRandomizedOneOffSuppressionEnabled) {
                                        "Current: one-off randomized Bluetooth LE hidden"
                                    } else {
                                        "Current: one-off randomized Bluetooth LE shown"
                                    }
                                )
                            }
                        }
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
                        Text("Approach detection drives approaching-state analysis and tracker-risk modeling.")
                    }
                }
            }
            item {
                Text("Home Point", fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val homePointSummary = homePoint?.let {
                            "${String.format(Locale.US, "%.5f", it.lat)}, ${String.format(Locale.US, "%.5f", it.lon)} • ${String.format(Locale.US, "%.0f m", it.radiusMeters)}"
                        } ?: "Not set"
                        Text("Saved point: $homePointSummary")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    homePointStatusMessage = onSetHomePointFromCurrentLocation()
                                }
                            ) {
                                Text("Set From Current Location")
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    homePointStatusMessage = onClearHomePoint()
                                },
                                enabled = homePoint != null
                            ) {
                                Text("Clear")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Radius")
                            Button(
                                enabled = homePoint != null,
                                onClick = { homePointRadiusExpanded = true }
                            ) {
                                Text(
                                    homePoint?.let { String.format(Locale.US, "%.0f m", it.radiusMeters) }
                                        ?: "Set Home First"
                                )
                            }
                            DropdownMenu(
                                expanded = homePointRadiusExpanded,
                                onDismissRequest = { homePointRadiusExpanded = false }
                            ) {
                                ScanSettings.ALLOWED_HOME_POINT_RADIUS_METERS.forEach { radiusMeters ->
                                    DropdownMenuItem(
                                        text = { Text(String.format(Locale.US, "%.0f m", radiusMeters)) },
                                        onClick = {
                                            homePointStatusMessage = onSetHomePointRadiusMeters(radiusMeters)
                                            homePointRadiusExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Text(
                            "Tracker scoring gives extra weight when non-owned devices appear both near Home Point and away from it.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!homePointStatusMessage.isNullOrBlank()) {
                            Text(homePointStatusMessage!!)
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
                        Text("Direct Signal Channels", fontWeight = FontWeight.Bold)
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
                        val notificationToggleKeys = listOf(
                            "approach",
                            "tracker",
                            "flock",
                            "no_fly",
                            "nfc",
                            "magnetic",
                            "mesh_connectivity",
                            "mesh_wipe"
                        )

                        HomeResponsiveGrid(
                            items = notificationToggleKeys,
                            twoColumnMinWidth = 760.dp,
                            threeColumnMinWidth = HOME_THREE_COLUMN_MIN_WIDTH
                        ) { toggleKey ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                when (toggleKey) {
                                    "approach" -> {
                                        Text("Approach notifications")
                                        Switch(
                                            checked = approachNotificationsEnabled,
                                            onCheckedChange = onApproachNotificationsChanged,
                                            enabled = approachDetectionEnabled
                                        )
                                    }

                                    "tracker" -> {
                                        Text("Tracker suspicion alerts")
                                        Switch(
                                            checked = trackerNotificationsEnabled,
                                            onCheckedChange = onTrackerNotificationsChanged,
                                            enabled = approachDetectionEnabled
                                        )
                                    }

                                    "flock" -> {
                                        Text("Flock alerts")
                                        Switch(
                                            checked = flockNotificationsEnabled,
                                            onCheckedChange = onFlockNotificationsChanged
                                        )
                                    }

                                    "no_fly" -> {
                                        Text("No-fly pass-through alerts")
                                        Switch(
                                            checked = noFlyPassThroughNotificationsEnabled,
                                            onCheckedChange = onNoFlyPassThroughNotificationsChanged
                                        )
                                    }

                                    "nfc" -> {
                                        Text("NFC tap alerts")
                                        Switch(
                                            checked = nfcNotificationsEnabled,
                                            onCheckedChange = onNfcNotificationsChanged
                                        )
                                    }

                                    "magnetic" -> {
                                        Text("Magnetic disturbance alerts")
                                        Switch(
                                            checked = magneticIncreaseNotificationsEnabled,
                                            onCheckedChange = onMagneticIncreaseNotificationsChanged
                                        )
                                    }

                                    "mesh_connectivity" -> {
                                        Text("Mesh peer connectivity alerts")
                                        Switch(
                                            checked = meshConnectivityNotificationsEnabled,
                                            onCheckedChange = onMeshConnectivityNotificationsChanged
                                        )
                                    }

                                    else -> {
                                        Text("Mesh wipe lifecycle alerts")
                                        Switch(
                                            checked = meshWipeNotificationsEnabled,
                                            onCheckedChange = onMeshWipeNotificationsChanged
                                        )
                                    }
                                }
                            }
                        }
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
                            Button(
                                onClick = {
                                    resetDialogTarget = "soft"
                                    resetStatusMessage = null
                                    resetStatusIsError = false
                                }
                            ) {
                                Text("Soft Reset")
                            }
                            Button(
                                onClick = {
                                    resetDialogTarget = "hard"
                                    resetStatusMessage = null
                                    resetStatusIsError = false
                                }
                            ) {
                                Text("Hard Reset")
                            }
                        }
                    }
                }
            }
        }
    }

    val dialogTarget = resetDialogTarget
    if (defaultsResetDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!defaultsResetInProgress) {
                    defaultsResetDialogVisible = false
                }
            },
            title = {
                Text("Reset Settings to Defaults")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will overwrite your current settings values with app defaults. Logs and collected encounters are not deleted. Continue?")
                    if (defaultsResetInProgress) {
                        Text("Applying defaults...")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !defaultsResetInProgress,
                    onClick = {
                        scope.launch {
                            defaultsResetInProgress = true
                            resetStatusIsError = false
                            resetStatusMessage = runCatching {
                                onResetDefaults()
                            }.getOrElse { error ->
                                resetStatusIsError = true
                                "Failed to reset defaults: ${error.message ?: "unknown error"}"
                            }
                            defaultsResetInProgress = false
                            defaultsResetDialogVisible = false
                        }
                    }
                ) {
                    Text(if (defaultsResetInProgress) "Working..." else "Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !defaultsResetInProgress,
                    onClick = {
                        defaultsResetDialogVisible = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (dialogTarget != null) {
        val isHardReset = dialogTarget == "hard"
        AlertDialog(
            onDismissRequest = {
                if (!resetActionInProgress) {
                    resetDialogTarget = null
                    resetStatusMessage = null
                    resetStatusIsError = false
                }
            },
            title = {
                Text(if (isHardReset) "Confirm Hard Reset" else "Confirm Soft Reset")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isHardReset) {
                            "Hard reset clears local encounters/devices/logs and wipes mesh network settings. Continue?"
                        } else {
                            "Soft reset clears local encounters/devices/logs. Continue?"
                        }
                    )
                    if (resetActionInProgress) {
                        Text("Running reset...")
                    }
                    resetStatusMessage?.let { message ->
                        Text(
                            text = message,
                            color = if (resetStatusIsError) Color(0xFFB3261E) else Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !resetActionInProgress,
                    onClick = {
                        scope.launch {
                            resetActionInProgress = true
                            val result = runCatching {
                                if (isHardReset) onHardReset() else onSoftReset()
                            }
                            resetStatusMessage = result.getOrElse { error ->
                                "Reset failed: ${error.message ?: "unknown error"}"
                            }
                            resetStatusIsError = result.isFailure
                            resetActionInProgress = false
                        }
                    }
                ) {
                    Text(if (resetActionInProgress) "Working..." else "Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !resetActionInProgress,
                    onClick = {
                        resetDialogTarget = null
                        resetStatusMessage = null
                        resetStatusIsError = false
                    }
                ) {
                    Text(if (resetStatusMessage != null) "Close" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetectionPage(
    initialTabRequest: Int?,
    onInitialTabRequestHandled: () -> Unit,
    readinessItems: List<DetectionReadinessItem>,
    encounters: List<Encounter>,
    meshInsightEncounters: List<Encounter>,
    approachDetectionEnabled: Boolean,
    ownedDeviceKeys: Set<String>,
    chainLinkEnabled: Boolean,
    chainNodeId: String,
    chainDeviceName: String,
    chainSharedSecret: String,
    chainAutoSyncEnabled: Boolean,
    chainSharePreciseLocationEnabled: Boolean,
    liveMapUpdateIntervalSeconds: Long,
    sourceScanIntervals: Map<String, Long>,
    startupPrewarmedDevicePins: List<MapPin>,
    startupPrewarmedNoFlyZones: List<NoFlyZoneOverlayProvider.NoFlyZonePolygon>,
    mapResetGeneration: Long,
    wifiRandomizedOneOffSuppressionEnabled: Boolean,
    bleRandomizedOneOffSuppressionEnabled: Boolean,
    mapNoFlyZonesEnabled: Boolean,
    mapNoFlyRenderQualityLevel: Int,
    mapTrafficEnabled: Boolean,
    mapClusteringEnabled: Boolean,
    onMapClusteringEnabledChanged: (Boolean) -> Unit,
    mapClusterRangeLevel: Int,
    onMapClusterRangeLevelChanged: (Int) -> Unit,
    mapScannerSweepAnimationEnabled: Boolean,
    chainMeshSnapshot: ChainMeshSnapshot,
    onDeviceMapPinClick: (source: String, primaryId: String, lat: Double?, lon: Double?, timestampEpochMs: Long?) -> Unit,
    onDeviceClick: (DeviceItem) -> Unit,
    onMovingDeviceMapPinClick: (source: String, primaryId: String) -> Unit,
    onRefresh: () -> Unit,
    onLiveCollect: suspend () -> String,
    onOpenReadinessSetting: (DetectionReadinessItem) -> Unit,
    onChainLinkChanged: (Boolean) -> Unit,
    onChainDeviceNameChanged: (String) -> Unit,
    onChainSharedSecretChanged: (String) -> Unit,
    onChainAutoSyncChanged: (Boolean) -> Unit,
    onChainSharePreciseLocationChanged: (Boolean) -> Unit,
    onRefreshPeers: suspend () -> Unit,
    onSyncNow: suspend () -> String,
    onWipeMeshData: suspend () -> String
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var cellDevicePinLimit by rememberSaveable { mutableStateOf(1000) }
    var liveOnlyOnDeviceMap by rememberSaveable { mutableStateOf(true) }
    var identityModeOnDeviceMap by rememberSaveable { mutableStateOf(false) }
    var movingOnlyOnDeviceMap by rememberSaveable { mutableStateOf(false) }
    var sinceSnapshotOnlyOnDeviceMap by rememberSaveable { mutableStateOf(false) }
    var deviceMapSnapshotEpochMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedMapSubTab by rememberSaveable { mutableStateOf(0) }
    var flightMapPinLimit by rememberSaveable { mutableStateOf(1000) }
    var liveOnlyOnFlightMap by rememberSaveable { mutableStateOf(false) }
    var identityModeOnFlightMap by rememberSaveable { mutableStateOf(false) }
    var sinceSnapshotOnlyOnFlightMap by rememberSaveable { mutableStateOf(false) }
    var flightMapSnapshotEpochMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var bluetoothMapPinLimit by rememberSaveable { mutableStateOf(1000) }
    var liveOnlyOnBluetoothMap by rememberSaveable { mutableStateOf(true) }
    var identityModeOnBluetoothMap by rememberSaveable { mutableStateOf(true) }
    var movingOnlyOnBluetoothMap by rememberSaveable { mutableStateOf(false) }
    var sinceSnapshotOnlyOnBluetoothMap by rememberSaveable { mutableStateOf(false) }
    var bluetoothMapSnapshotEpochMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var deviceMapAircraftRadiusMiles by rememberSaveable {
        mutableStateOf(ScanSettings.getAviationPublicRadiusMiles(context).coerceIn(25, 75))
    }
    var flightMapRadiusMiles by rememberSaveable {
        mutableStateOf(ScanSettings.getAviationPublicRadiusMiles(context).coerceIn(10, 1000))
    }
    val tabs = listOf("Status", "Device", "Flock", "Map", "Mesh")
    var mapLiveNowEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }

    val mapLiveTickerEnabled = selectedTab == 3 && (
        (selectedMapSubTab == 0 && liveOnlyOnDeviceMap) ||
            (selectedMapSubTab == 1 && liveOnlyOnBluetoothMap)
        )

    LaunchedEffect(mapLiveTickerEnabled, liveMapUpdateIntervalSeconds) {
        if (!mapLiveTickerEnabled) return@LaunchedEffect
        val tickMs = (liveMapUpdateIntervalSeconds.coerceAtLeast(1L) * 1000L).coerceAtMost(5_000L)
        tickerFlow(periodMs = tickMs).collect { now ->
            mapLiveNowEpochMs = now
        }
    }

    LaunchedEffect(initialTabRequest) {
        val requestedTab = initialTabRequest ?: return@LaunchedEffect
        selectedTab = requestedTab.coerceIn(0, tabs.lastIndex)
        onInitialTabRequestHandled()
    }

    val isDeviceLocationTabActive = selectedTab == 3 && (selectedMapSubTab == 0 || selectedMapSubTab == 1)
    val isDeviceMapActive = selectedTab == 3 && selectedMapSubTab == 0
    val isBluetoothMapActive = selectedTab == 3 && selectedMapSubTab == 1
    val useFullHistoryForActiveDeviceLocationMap =
        (isDeviceMapActive && !liveOnlyOnDeviceMap && !movingOnlyOnDeviceMap && !sinceSnapshotOnlyOnDeviceMap) ||
            (isBluetoothMapActive && !liveOnlyOnBluetoothMap && !movingOnlyOnBluetoothMap && !sinceSnapshotOnlyOnBluetoothMap)
    val scopedMapSources: Set<EncounterSource>? = if (isBluetoothMapActive) {
        setOf(
            EncounterSource.BLUETOOTH_LE,
            EncounterSource.BLUETOOTH_CLASSIC,
            EncounterSource.BLUETOOTH_LE_SWEEP
        )
    } else {
        null
    }
    val signalIntel = remember(selectedTab, meshInsightEncounters) {
        if (selectedTab == 0) buildSignalIntelSnapshot(meshInsightEncounters) else null
    }
    val missingReadinessItems = remember(readinessItems) {
        readinessItems.filter { it.isMissing }
    }
    val readyReadinessCount = remember(readinessItems) {
        readinessItems.count { !it.isMissing }
    }
    val flightDisplayRadiusMeters = flightMapRadiusMiles.coerceIn(10, 1000) * 1609.344
    val topSpeedRecords = remember(selectedTab, selectedMapSubTab, meshInsightEncounters.size) {
        if (selectedTab == 3 && (selectedMapSubTab == 0 || selectedMapSubTab == 1)) {
            DeviceSpeedRecordStore.getAllRecordSpeedsMps(context)
        } else {
            emptyMap()
        }
    }

    val maxDeviceCandidatesToResolve = remember(cellDevicePinLimit) {
        (cellDevicePinLimit * 2).coerceIn(200, 2000)
    }
    var allDeviceCandidates by remember { mutableStateOf<List<DeviceLocationCandidate>>(emptyList()) }
    var deviceCandidatesPrepared by remember { mutableStateOf(false) }
    val resolvedLocationCache = remember { mutableMapOf<String, Pair<Long, ResolvedDeviceLocation?>>() }

    LaunchedEffect(
        isDeviceLocationTabActive,
        useFullHistoryForActiveDeviceLocationMap,
        scopedMapSources,
        meshInsightEncounters,
        approachDetectionEnabled,
        ownedDeviceKeys,
        wifiRandomizedOneOffSuppressionEnabled,
        bleRandomizedOneOffSuppressionEnabled,
        sourceScanIntervals,
        liveMapUpdateIntervalSeconds,
        maxDeviceCandidatesToResolve
    ) {
        if (!isDeviceLocationTabActive) {
            allDeviceCandidates = emptyList()
            deviceCandidatesPrepared = false
            resolvedLocationCache.clear()
            return@LaunchedEffect
        }

        deviceCandidatesPrepared = false
        val trackerHomePoint = ScanSettings.getHomePoint(context)

        allDeviceCandidates = withContext(Dispatchers.Default) {
            val sourceScopedEncounters = scopedMapSources?.let { allowed ->
                meshInsightEncounters.filter { encounter -> encounter.source in allowed }
            } ?: meshInsightEncounters

            val latestSnapshotEpochMs = sourceScopedEncounters.maxOfOrNull { encounter ->
                encounterFreshnessEpochMs(encounter)
            }
            val latestSnapshot = latestSnapshotEpochMs ?: Long.MIN_VALUE
            val candidateEncounters = if (useFullHistoryForActiveDeviceLocationMap) {
                sourceScopedEncounters
            } else {
                sourceScopedEncounters.filter { encounter ->
                    val sourceType = scanTypeKeyForSourceName(encounter.source.name)
                    val recentWindowMs = mapRecentWindowMsForSource(
                        sourceType = sourceType,
                        sourceScanIntervals = sourceScanIntervals,
                        liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds
                    )
                    encounterFreshnessEpochMs(encounter) >= latestSnapshot - recentWindowMs
                }
            }

            val groupedByDevice = candidateEncounters
                .asSequence()
                .groupBy { "${it.source.name}|${it.primaryId}" }

            val candidateTakeLimit = if (useFullHistoryForActiveDeviceLocationMap) {
                maxDeviceCandidatesToResolve.coerceAtLeast(2_000).coerceAtMost(10_000)
            } else {
                maxDeviceCandidatesToResolve
            }

            groupedByDevice
                .values
                .asSequence()
                .mapNotNull { deviceEncounters ->
                    val latest = deviceEncounters.maxByOrNull { encounter ->
                        encounterFreshnessEpochMs(encounter)
                    } ?: return@mapNotNull null
                    if (
                        shouldSuppressLikelyRandomizedWifiNoise(
                            source = latest.source,
                            primaryId = latest.primaryId,
                            seenCount = deviceEncounters.size,
                            wifiSuppressionEnabled = wifiRandomizedOneOffSuppressionEnabled,
                            bleSuppressionEnabled = bleRandomizedOneOffSuppressionEnabled
                        )
                    ) {
                        return@mapNotNull null
                    }
                    latest to deviceEncounters
                }
                .sortedByDescending { (latest, _) -> encounterFreshnessEpochMs(latest) }
                .take(candidateTakeLimit)
                .map { (latest, deviceEncounters) ->
                    val preferredSecondaryId = bestSecondaryId(deviceEncounters)
                    val isCameraSource = latest.source == EncounterSource.CAMERA
                    val owned = OwnedDeviceRegistry.keyFor(latest.source.name, latest.primaryId) in ownedDeviceKeys
                    val approachSignal = if (approachDetectionEnabled && isApproachEligibleSource(latest.source)) {
                        analyzeApproachSignal(deviceEncounters)
                    } else {
                        null
                    }
                    val motionSignal = if (isCameraSource) null else analyzeMotionSignal(deviceEncounters)
                    DeviceLocationCandidate(
                        source = latest.source.name,
                        primaryId = latest.primaryId,
                        secondaryId = preferredSecondaryId,
                        latestTimestampEpochMs = encounterFreshnessEpochMs(latest),
                        seenCount = deviceEncounters.size,
                        encounters = deviceEncounters,
                        latestEncounter = latest,
                        previousEncounter = deviceEncounters
                            .asSequence()
                            .filter { it.timestampEpochMs < latest.timestampEpochMs }
                            .maxByOrNull { it.timestampEpochMs },
                        hasChainLinkedData = deviceEncounters.any { it.provenance == EncounterProvenance.CHAIN_LINKED },
                        chainLinkedPeerCount = deviceEncounters.mapNotNull { it.provenanceNodeId }.toSet().size,
                        isOwned = owned,
                        approachSignal = approachSignal,
                        motionSignal = motionSignal,
                        trackerRisk = if (isCameraSource) {
                            null
                        } else {
                            analyzeTrackerRisk(
                                encounters = deviceEncounters,
                                isOwned = owned,
                                approachSignal = approachSignal,
                                homePoint = trackerHomePoint
                            )
                        }
                    )
                }
                .toList()
        }

        deviceCandidatesPrepared = true

        val activeCandidateKeys = allDeviceCandidates
            .asSequence()
            .map { "${it.source}|${it.primaryId}" }
            .toSet()
        resolvedLocationCache.keys.removeAll { it !in activeCandidateKeys }
    }

    var estimatedDeviceLocationPins by remember(startupPrewarmedDevicePins) {
        mutableStateOf(startupPrewarmedDevicePins)
    }

    val flightMapCurrentLocation by if (selectedTab == 3 && selectedMapSubTab == 2) {
        LocationSnapshotProvider.observe(
            context,
            minUpdateIntervalMs = (liveMapUpdateIntervalSeconds.coerceAtLeast(1L) * 1000L).coerceAtMost(5_000L)
        ).collectAsState(initial = LocationSnapshotProvider.read(context))
    } else {
        remember { mutableStateOf<DetectionLocation?>(null) }
    }
    val deviceMapCurrentLocation by if (selectedTab == 3 && selectedMapSubTab == 0) {
        LocationSnapshotProvider.observe(
            context,
            minUpdateIntervalMs = (liveMapUpdateIntervalSeconds.coerceAtLeast(1L) * 1000L).coerceAtMost(5_000L)
        ).collectAsState(initial = LocationSnapshotProvider.read(context))
    } else {
        remember { mutableStateOf<DetectionLocation?>(null) }
    }
    val bluetoothMapCurrentLocation by if (selectedTab == 3 && selectedMapSubTab == 1) {
        LocationSnapshotProvider.observe(
            context,
            minUpdateIntervalMs = (liveMapUpdateIntervalSeconds.coerceAtLeast(1L) * 1000L).coerceAtMost(5_000L)
        ).collectAsState(initial = LocationSnapshotProvider.read(context))
    } else {
        remember { mutableStateOf<DetectionLocation?>(null) }
    }

    val isFlightMapActive = selectedTab == 3 && selectedMapSubTab == 2
    val activeMapLocation = when (selectedMapSubTab) {
        1 -> bluetoothMapCurrentLocation
        2 -> flightMapCurrentLocation
        else -> deviceMapCurrentLocation
    }
    val noFlyOverlayLocationKey = remember(activeMapLocation) {
        activeMapLocation?.let { location ->
            val latBucket = (location.lat * 2.0).roundToInt()
            val lonBucket = (location.lon * 2.0).roundToInt()
            "$latBucket:$lonBucket"
        } ?: "none"
    }
    val noFlyZoneOverlayCache = remember {
        mutableMapOf<String, List<NoFlyZoneOverlayProvider.NoFlyZonePolygon>>()
    }
    var noFlyZoneOverlays by remember(startupPrewarmedNoFlyZones, mapNoFlyZonesEnabled) {
        mutableStateOf(if (mapNoFlyZonesEnabled) startupPrewarmedNoFlyZones else emptyList())
    }
    var allTrackedAircraftPins by remember { mutableStateOf<List<MapPin>>(emptyList()) }

    LaunchedEffect(mapResetGeneration) {
        deviceMapSnapshotEpochMs = null
        flightMapSnapshotEpochMs = null
        bluetoothMapSnapshotEpochMs = null
        sinceSnapshotOnlyOnDeviceMap = false
        sinceSnapshotOnlyOnFlightMap = false
        sinceSnapshotOnlyOnBluetoothMap = false
        allDeviceCandidates = emptyList()
        deviceCandidatesPrepared = false
        resolvedLocationCache.clear()
        estimatedDeviceLocationPins = emptyList()
        noFlyZoneOverlayCache.clear()
        noFlyZoneOverlays = emptyList()
        allTrackedAircraftPins = emptyList()
    }

    LaunchedEffect(selectedTab, selectedMapSubTab, noFlyOverlayLocationKey, mapNoFlyZonesEnabled) {
        if (selectedTab != 3) {
            return@LaunchedEffect
        }

        if (!mapNoFlyZonesEnabled) {
            noFlyZoneOverlays = emptyList()
            return@LaunchedEffect
        }

        noFlyZoneOverlayCache[noFlyOverlayLocationKey]?.let { cached ->
            noFlyZoneOverlays = cached
            return@LaunchedEffect
        }

        val overlays = withContext(Dispatchers.IO) {
            NoFlyZoneOverlayProvider.read(context, near = activeMapLocation)
        }
        noFlyZoneOverlayCache[noFlyOverlayLocationKey] = overlays
        noFlyZoneOverlays = overlays
    }

    LaunchedEffect(meshInsightEncounters, sourceScanIntervals, liveMapUpdateIntervalSeconds, isFlightMapActive) {
        if (!isFlightMapActive) {
            allTrackedAircraftPins = emptyList()
            return@LaunchedEffect
        }

        allTrackedAircraftPins = withContext(Dispatchers.Default) {
            data class AircraftAccumulator(
                var latest: Encounter,
                var previous: Encounter?,
                var seenCount: Int
            )

            val latestByAircraft = LinkedHashMap<String, AircraftAccumulator>()
            var latestAircraftEpochMs: Long? = null

            meshInsightEncounters.forEach { encounter ->
                if (encounter.source != EncounterSource.AIRCRAFT) return@forEach
                if (!isValidLatLon(encounter.lat, encounter.lon)) return@forEach

                val existing = latestByAircraft[encounter.primaryId]
                if (existing == null) {
                    latestByAircraft[encounter.primaryId] = AircraftAccumulator(
                        latest = encounter,
                        previous = null,
                        seenCount = 1
                    )
                } else {
                    existing.seenCount += 1
                    if (encounter.timestampEpochMs >= existing.latest.timestampEpochMs) {
                        if (encounter.timestampEpochMs > existing.latest.timestampEpochMs) {
                            existing.previous = existing.latest
                        }
                        existing.latest = encounter
                    } else {
                        val previous = existing.previous
                        if (previous == null || encounter.timestampEpochMs > previous.timestampEpochMs) {
                            existing.previous = encounter
                        }
                    }
                }

                val freshnessEpochMs = encounterFreshnessEpochMs(encounter)
                if (latestAircraftEpochMs == null || freshnessEpochMs > latestAircraftEpochMs!!) {
                    latestAircraftEpochMs = freshnessEpochMs
                }
            }

            if (latestByAircraft.isEmpty()) {
                emptyList()
            } else {
                val aircraftLiveWindowMs = mapLiveWindowMsForSource(
                    sourceType = SourceCatalog.KEY_AIRCRAFT,
                    sourceScanIntervals = sourceScanIntervals,
                    liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds
                )
                val aircraftLiveCutoffEpochMs = (latestAircraftEpochMs ?: Long.MIN_VALUE) - aircraftLiveWindowMs
                latestByAircraft.values
                    .map { acc ->
                        val latest = acc.latest
                        val preferredSecondaryId = bestSecondaryId(listOfNotNull(latest, acc.previous))
                        val latestFreshnessEpochMs = encounterFreshnessEpochMs(latest)
                        val isLive = latestFreshnessEpochMs >= aircraftLiveCutoffEpochMs
                        val sourceLabel = listSourceLabel(latest.source.name, preferredSecondaryId)
                        val pinTitle = buildPinTitle(
                            sourceLabel = sourceLabel,
                            primaryId = latest.primaryId,
                            secondaryId = preferredSecondaryId,
                            motionBadge = null
                        )
                        val freshnessSnippet = if (isLive) {
                            "Live • Seen ${formatMapPinAge(latestFreshnessEpochMs)} ago"
                        } else {
                            "Recent • Seen ${formatMapPinAge(latestFreshnessEpochMs)} ago"
                        }
                        val line1 = "Seen ${acc.seenCount}x"
                        val line2 = "$freshnessSnippet • Last ${formatEpoch(latestFreshnessEpochMs)}"
                        val line3 = preferredSecondaryId?.let { "Callsign: $it" } ?: "Callsign: n/a"
                        val pinSnippet = buildThreeLineSnippet(
                            line1 = line1,
                            line2 = line2,
                            line3 = line3
                        )
                        val derivedHeading = estimateHeadingFromEncounters(
                            previous = acc.previous,
                            latest = latest
                        )
                        val visualHints = readAircraftVisualHints(latest.rawPayloadJson)
                        MapPin(
                            position = LatLng(latest.lat!!, latest.lon!!),
                            title = pinTitle,
                            snippetBuilder = {
                                pinSnippet
                            },
                            searchableMetadata = buildPinSearchableMetadata(
                                source = latest.source.name,
                                primaryId = latest.primaryId,
                                secondaryId = preferredSecondaryId,
                                title = pinTitle,
                                snippet = pinSnippet,
                                rawPayloads = listOfNotNull(
                                    latest.rawPayloadJson,
                                    acc.previous?.rawPayloadJson
                                )
                            ),
                            timestampEpochMs = latestFreshnessEpochMs,
                            source = latest.source.name,
                            primaryId = latest.primaryId,
                            secondaryId = preferredSecondaryId,
                            encounterTimestampEpochMs = latest.timestampEpochMs,
                            aircraftIconType = visualHints.iconType,
                            headingDegrees = visualHints.headingDegrees ?: derivedHeading,
                            motionBadge = null,
                            motionSpeedMps = null,
                            isLive = isLive
                        )
                    }
                    .sortedByDescending { it.timestampEpochMs }
            }
        }
    }

    LaunchedEffect(allDeviceCandidates, isDeviceLocationTabActive) {
        if (!isDeviceLocationTabActive) {
            estimatedDeviceLocationPins = emptyList()
            return@LaunchedEffect
        }

        if (!deviceCandidatesPrepared) {
            return@LaunchedEffect
        }

        if (allDeviceCandidates.isEmpty()) {
            estimatedDeviceLocationPins = emptyList()
            return@LaunchedEffect
        }

        val resolvedCandidates = withContext(Dispatchers.IO) {
            buildList {
                allDeviceCandidates.forEach { candidate ->
                    val sourceEnum = runCatching { EncounterSource.valueOf(candidate.source) }.getOrDefault(EncounterSource.UNKNOWN_RF)
                    val cacheKey = "${candidate.source}|${candidate.primaryId}"
                    val cacheVersion = candidate.latestTimestampEpochMs
                    val cached = resolvedLocationCache[cacheKey]
                    val resolvedLocation = if (cached != null && cached.first == cacheVersion) {
                        cached.second
                    } else {
                        resolveDeviceLocation(
                            source = sourceEnum,
                            encounters = candidate.encounters
                        ).also { resolved ->
                            resolvedLocationCache[cacheKey] = cacheVersion to resolved
                        }
                    }
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
        }

        val nowEpochMs = System.currentTimeMillis()

        val resolvedPins = resolvedCandidates.mapNotNull { candidate ->
            val location = candidate.approximateLocation ?: return@mapNotNull null
            if (!isValidLatLon(location.lat, location.lon)) return@mapNotNull null
            val sourceIsAircraft = candidate.source == EncounterSource.AIRCRAFT.name
            val latestEncounter = if (sourceIsAircraft) candidate.latestEncounter else null
            val previousEncounter = if (sourceIsAircraft) candidate.previousEncounter else null
            val aircraftVisualHints = if (sourceIsAircraft && latestEncounter != null) {
                readAircraftVisualHints(latestEncounter.rawPayloadJson)
            } else {
                null
            }
            val resolvedAircraftHeading = if (sourceIsAircraft && latestEncounter != null) {
                aircraftVisualHints?.headingDegrees ?: estimateHeadingFromEncounters(previousEncounter, latestEncounter)
            } else {
                null
            }
            val sourceType = scanTypeKeyForSourceName(candidate.source)
            val liveWindowMs = mapLiveWindowMsForSource(
                sourceType = sourceType,
                sourceScanIntervals = sourceScanIntervals,
                liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds
            )
            val liveCutoffForSource = nowEpochMs - liveWindowMs
            val isLive = candidate.latestTimestampEpochMs >= liveCutoffForSource

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
            val isCameraSource = candidate.source == EncounterSource.CAMERA.name
            val motionBadge = if (isCameraSource) {
                null
            } else {
                candidate.motionSignal?.let { if (it.isInMotion) "MOVING" else "STATIC" }
            }
            val motionLine = if (isCameraSource) {
                "Motion: n/a"
            } else {
                candidate.motionSignal?.let { signal ->
                    if (signal.isInMotion) {
                        "Motion: MOVING ${formatSpeedLabel(signal.speedMps)} ${formatHeadingCardinal(signal.headingDeg)}"
                    } else {
                        "Motion: STATIC ${formatSpeedLabel(signal.speedMps)}"
                    }
                } ?: "Motion: n/a"
            }
            val topSpeedLine = if (isCameraSource) {
                ""
            } else {
                topSpeedRecords
                    .get("${candidate.source}|${candidate.primaryId}")
                    ?.let { " • Top ${formatSpeedLabel(it)}" }
                    .orEmpty()
            }
            val freshnessSnippet = if (isLive) {
                "Live • Seen ${formatMapPinAge(candidate.latestTimestampEpochMs)} ago"
            } else {
                "Recent • Seen ${formatMapPinAge(candidate.latestTimestampEpochMs)} ago"
            }
            val sourceLabel = listSourceLabel(candidate.source, candidate.secondaryId)
            val pinTitle = buildPinTitle(
                sourceLabel = sourceLabel,
                primaryId = candidate.primaryId,
                secondaryId = candidate.secondaryId,
                motionBadge = motionBadge
            )
            val bluetoothFamilyBadge = if (
                candidate.source == EncounterSource.BLUETOOTH_LE.name ||
                    candidate.source == EncounterSource.BLUETOOTH_CLASSIC.name
            ) {
                trackerFamilyBadgeLabel(candidate.trackerRisk?.trackerFamilyHint)
            } else {
                null
            }
            val line1 = "Seen ${candidate.seenCount}x"
            val line2 = "$freshnessSnippet • Last ${formatEpoch(candidate.latestTimestampEpochMs)}$rangeSnippet$approachSnippet"
            val line3 = "$motionLine$topSpeedLine$ownershipSnippet$chainSnippet$trackerSnippet"
            val pinSnippet = buildThreeLineSnippet(
                line1 = line1,
                line2 = line2,
                line3 = line3
            )

            MapPin(
                position = LatLng(location.lat, location.lon),
                title = pinTitle,
                snippetBuilder = {
                    pinSnippet
                },
                searchableMetadata = buildPinSearchableMetadata(
                    source = candidate.source,
                    primaryId = candidate.primaryId,
                    secondaryId = candidate.secondaryId,
                    title = pinTitle,
                    snippet = pinSnippet,
                    rawPayloads = candidate.encounters
                        .sortedByDescending { it.timestampEpochMs }
                        .map { it.rawPayloadJson }
                ),
                timestampEpochMs = candidate.latestTimestampEpochMs,
                source = candidate.source,
                primaryId = candidate.primaryId,
                secondaryId = candidate.secondaryId,
                trackerFamilyBadge = bluetoothFamilyBadge,
                encounterTimestampEpochMs = candidate.latestEncounter.timestampEpochMs,
                aircraftIconType = aircraftVisualHints?.iconType,
                headingDegrees = resolvedAircraftHeading,
                motionBadge = motionBadge,
                motionSpeedMps = candidate.motionSignal?.speedMps,
                isLive = isLive
            )
        }

        estimatedDeviceLocationPins = resolvedPins
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Detection", style = MaterialTheme.typography.headlineMedium)
            if (selectedTab == 3) {
                Text(
                    text = "● LIVE",
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            }
        }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("Permissions and Readiness", fontWeight = FontWeight.Bold)
                        TextButton(onClick = onRefresh) {
                            Text("Refresh")
                        }
                    }
                }
                item {
                    signalIntel?.let {
                        DetectionSignalIntelSection(intel = it)
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${missingReadinessItems.size} need attention • $readyReadinessCount ready",
                                fontWeight = FontWeight.Medium
                            )
                            if (missingReadinessItems.isEmpty()) {
                                Text("All good", color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                            } else {
                                Text("Action needed", color = Color(0xFFB3261E), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                if (missingReadinessItems.isNotEmpty()) {
                    item {
                        HomeResponsiveGrid(
                            items = missingReadinessItems,
                            twoColumnMinWidth = 760.dp,
                            threeColumnMinWidth = HOME_THREE_COLUMN_MIN_WIDTH
                        ) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(item.title, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "Now: ${item.currentValue}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Set: ${item.recommendedValue}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = { onOpenReadinessSetting(item) }) {
                                        Text(item.openSettingsLabel)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (selectedTab == 1) {
            DevicesPage(
                allEncounters = meshInsightEncounters,
                approachDetectionEnabled = approachDetectionEnabled,
                ownedDeviceKeys = ownedDeviceKeys,
                wifiRandomizedOneOffSuppressionEnabled = wifiRandomizedOneOffSuppressionEnabled,
                bleRandomizedOneOffSuppressionEnabled = bleRandomizedOneOffSuppressionEnabled,
                onDeviceClick = onDeviceClick
            )
        } else if (selectedTab == 2) {
            FlocksPage(
                allEncounters = meshInsightEncounters
            )
        } else if (selectedTab == 3) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TabRow(selectedTabIndex = selectedMapSubTab) {
                    Tab(
                        selected = selectedMapSubTab == 0,
                        onClick = { selectedMapSubTab = 0 },
                        text = { Text("Device Map") }
                    )
                    Tab(
                        selected = selectedMapSubTab == 1,
                        onClick = { selectedMapSubTab = 1 },
                        text = { Text("Bluetooth Map") }
                    )
                    Tab(
                        selected = selectedMapSubTab == 2,
                        onClick = { selectedMapSubTab = 2 },
                        text = { Text("Aircraft Map") }
                    )
                }

                if (selectedMapSubTab == 0) {
                    val deviceMapAircraftRadiusMeters = deviceMapAircraftRadiusMiles.coerceIn(25, 75) * 1609.344
                    val deviceMapPins by produceState(
                        estimatedDeviceLocationPins,
                        estimatedDeviceLocationPins,
                        deviceMapCurrentLocation,
                        deviceMapAircraftRadiusMeters,
                        liveOnlyOnDeviceMap,
                        mapLiveNowEpochMs,
                        movingOnlyOnDeviceMap,
                        sinceSnapshotOnlyOnDeviceMap,
                        deviceMapSnapshotEpochMs
                    ) {
                        value = withContext(Dispatchers.Default) {
                            val nowEpochMs = mapLiveNowEpochMs
                            estimatedDeviceLocationPins
                                .asSequence()
                                .filter { pin ->
                                    if (pin.source != EncounterSource.AIRCRAFT.name) {
                                        true
                                    } else {
                                        deviceMapCurrentLocation?.let { loc ->
                                            distanceFromLocationMeters(
                                                fromLat = loc.lat,
                                                fromLon = loc.lon,
                                                toLat = pin.position.latitude,
                                                toLon = pin.position.longitude
                                            )?.let { distance -> distance <= deviceMapAircraftRadiusMeters } ?: false
                                        } ?: true
                                    }
                                }
                                .filter { pin ->
                                    if (!liveOnlyOnDeviceMap) {
                                        true
                                    } else {
                                        isMapPinLiveNow(
                                            pin = pin,
                                            nowEpochMs = nowEpochMs,
                                            sourceScanIntervals = sourceScanIntervals,
                                            liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds
                                        )
                                    }
                                }
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
                                .let(::spreadOverlappingMapPins)
                        }
                    }
                    DetectionMapPage(
                        mapTitle = "Device Map",
                        mapDescription = "Live pins show what is currently nearby; recent pins are faded for short-lived context. Click the items in the Pin Color Legend box to filter.",
                        showTrafficLayer = mapTrafficEnabled,
                        currentLocationOverride = deviceMapCurrentLocation,
                        noFlyZones = noFlyZoneOverlays,
                        showNoFlyZoneControl = mapNoFlyZonesEnabled,
                        noFlyRenderQualityLevel = mapNoFlyRenderQualityLevel,
                        pins = deviceMapPins,
                        pinLimit = cellDevicePinLimit,
                        onPinLimitChange = { cellDevicePinLimit = it },
                        mapClusteringEnabled = mapClusteringEnabled,
                        onMapClusteringEnabledChange = onMapClusteringEnabledChanged,
                        mapClusterRangeLevel = mapClusterRangeLevel,
                        onMapClusterRangeLevelChange = onMapClusterRangeLevelChanged,
                        mapScannerSweepAnimationEnabled = mapScannerSweepAnimationEnabled,
                        onPinDetailsClick = { pin ->
                            if (pin.motionBadge == "MOVING") {
                                onMovingDeviceMapPinClick(pin.source, pin.primaryId)
                            } else {
                                onDeviceMapPinClick(
                                    pin.source,
                                    pin.primaryId,
                                    pin.position.latitude,
                                    pin.position.longitude,
                                    pin.encounterTimestampEpochMs ?: pin.timestampEpochMs
                                )
                            }
                        },
                        liveUpdatesAllowed = true,
                        useSourceOnlyPinColors = true,
                        enableVerticalScroll = true,
                        showLiveOnlyControl = true,
                        liveOnlyEnabled = liveOnlyOnDeviceMap,
                        onLiveOnlyEnabledChange = { liveOnlyOnDeviceMap = it },
                        identityModeEnabled = identityModeOnDeviceMap,
                        onIdentityModeEnabledChange = { identityModeOnDeviceMap = it },
                        showRadiusControl = true,
                        radiusMiles = deviceMapAircraftRadiusMiles,
                        onRadiusMilesChange = { miles ->
                            val safeMiles = miles.coerceIn(25, 75)
                            deviceMapAircraftRadiusMiles = safeMiles
                            flightMapRadiusMiles = safeMiles
                            ScanSettings.setAviationPublicRadiusMiles(context, safeMiles)
                        },
                        radiusControlLabel = "Aircraft Radius (mi)",
                        radiusOptions = listOf(25, 50, 75),
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
                } else if (selectedMapSubTab == 1) {
                    val bluetoothMapPins by produceState(
                        estimatedDeviceLocationPins,
                        bluetoothMapPinLimit,
                        liveOnlyOnBluetoothMap,
                        mapLiveNowEpochMs,
                        movingOnlyOnBluetoothMap,
                        sinceSnapshotOnlyOnBluetoothMap,
                        bluetoothMapSnapshotEpochMs
                    ) {
                        value = withContext(Dispatchers.Default) {
                            val nowEpochMs = mapLiveNowEpochMs
                            estimatedDeviceLocationPins
                                .asSequence()
                                .filter { pin ->
                                    pin.source == EncounterSource.BLUETOOTH_LE.name ||
                                        pin.source == EncounterSource.BLUETOOTH_CLASSIC.name ||
                                        pin.source == EncounterSource.BLUETOOTH_LE_SWEEP.name
                                }
                                .filter { pin ->
                                    if (!liveOnlyOnBluetoothMap) {
                                        true
                                    } else {
                                        isMapPinLiveNow(
                                            pin = pin,
                                            nowEpochMs = nowEpochMs,
                                            sourceScanIntervals = sourceScanIntervals,
                                            liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds
                                        )
                                    }
                                }
                                .filter { pin ->
                                    if (!movingOnlyOnBluetoothMap) true else pin.motionBadge == "MOVING"
                                }
                                .filter { pin ->
                                    if (!sinceSnapshotOnlyOnBluetoothMap) {
                                        true
                                    } else {
                                        val snapshotEpoch = bluetoothMapSnapshotEpochMs
                                        snapshotEpoch == null || pin.timestampEpochMs >= snapshotEpoch
                                    }
                                }
                                .toList()
                                .let { pins ->
                                    val preLimit = (bluetoothMapPinLimit.coerceAtLeast(1) * 2)
                                        .coerceAtMost(MAP_RENDER_PIN_LIMIT_MID)
                                    val bounded = selectVisiblePinsWithSourceCoverage(
                                        pins = pins,
                                        pinLimit = preLimit
                                    )
                                    runCatching {
                                        spreadOverlappingMapPinsMinimal(
                                            pins = bounded,
                                            minSeparationMeters = 1.2
                                        )
                                    }.getOrElse { bounded }
                                }
                        }
                    }

                    DetectionMapPage(
                        mapTitle = "Bluetooth Map",
                        mapDescription = "Bluetooth-only pins (LE + Classic). Identity mode defaults on. Classification badges show detected tracker family.",
                        currentLocationOverride = bluetoothMapCurrentLocation,
                        noFlyZones = noFlyZoneOverlays,
                        showNoFlyZoneControl = mapNoFlyZonesEnabled,
                        noFlyRenderQualityLevel = mapNoFlyRenderQualityLevel,
                        pins = bluetoothMapPins,
                        pinLimit = bluetoothMapPinLimit,
                        onPinLimitChange = { bluetoothMapPinLimit = it },
                        mapClusteringEnabled = mapClusteringEnabled,
                        onMapClusteringEnabledChange = onMapClusteringEnabledChanged,
                        mapClusterRangeLevel = mapClusterRangeLevel,
                        onMapClusterRangeLevelChange = onMapClusterRangeLevelChanged,
                        mapScannerSweepAnimationEnabled = mapScannerSweepAnimationEnabled,
                        onPinDetailsClick = { pin ->
                            if (pin.motionBadge == "MOVING") {
                                onMovingDeviceMapPinClick(pin.source, pin.primaryId)
                            } else {
                                onDeviceMapPinClick(
                                    pin.source,
                                    pin.primaryId,
                                    pin.position.latitude,
                                    pin.position.longitude,
                                    pin.encounterTimestampEpochMs ?: pin.timestampEpochMs
                                )
                            }
                        },
                        liveUpdatesAllowed = true,
                        useSourceOnlyPinColors = true,
                        enableVerticalScroll = true,
                        showLiveOnlyControl = true,
                        liveOnlyEnabled = liveOnlyOnBluetoothMap,
                        onLiveOnlyEnabledChange = { liveOnlyOnBluetoothMap = it },
                        identityModeEnabled = identityModeOnBluetoothMap,
                        onIdentityModeEnabledChange = { identityModeOnBluetoothMap = it },
                        showMovingOnlyControl = true,
                        movingOnlyEnabled = movingOnlyOnBluetoothMap,
                        onMovingOnlyEnabledChange = { movingOnlyOnBluetoothMap = it },
                        showSinceSnapshotControl = true,
                        sinceSnapshotEnabled = sinceSnapshotOnlyOnBluetoothMap,
                        snapshotEpochMs = bluetoothMapSnapshotEpochMs,
                        onSinceSnapshotEnabledChange = { enabled ->
                            sinceSnapshotOnlyOnBluetoothMap = enabled
                            if (enabled && bluetoothMapSnapshotEpochMs == null) {
                                bluetoothMapSnapshotEpochMs = System.currentTimeMillis()
                            }
                        },
                        onCaptureSnapshot = {
                            bluetoothMapSnapshotEpochMs = System.currentTimeMillis()
                        },
                        showTrackerFamilyBadge = true,
                        onLiveCollect = onLiveCollect,
                        liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds
                    )
                } else {
                    val flightSensorsEnabled = ScanSettings.isAviationAdsbSensorEnabled(context) ||
                        ScanSettings.isAviationPublicSensorEnabled(context)
                    if (!flightSensorsEnabled) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Flight sensors are disabled.",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Enable ADS-B (Aviation) or Public Flight Radar under Status > Sensors to view Aircraft Map."
                                )
                                Button(onClick = { selectedTab = 0 }) {
                                    Text("Open Status > Sensors")
                                }
                            }
                        }
                    } else {
                        val flightMapPins by produceState(
                            allTrackedAircraftPins,
                            allTrackedAircraftPins,
                            liveOnlyOnFlightMap,
                            flightMapCurrentLocation,
                            flightDisplayRadiusMeters,
                            sinceSnapshotOnlyOnFlightMap,
                            flightMapSnapshotEpochMs
                        ) {
                            value = withContext(Dispatchers.Default) {
                                allTrackedAircraftPins
                                    .asSequence()
                                    .filter { pin ->
                                        if (!liveOnlyOnFlightMap) {
                                            true
                                        } else {
                                            val withinRadius = flightMapCurrentLocation?.let { loc ->
                                                distanceFromLocationMeters(
                                                    fromLat = loc.lat,
                                                    fromLon = loc.lon,
                                                    toLat = pin.position.latitude,
                                                    toLon = pin.position.longitude
                                                )?.let { distance -> distance <= flightDisplayRadiusMeters } ?: false
                                            } ?: true
                                            pin.isLive && withinRadius
                                        }
                                    }
                                    .filter { pin ->
                                        if (!sinceSnapshotOnlyOnFlightMap) {
                                            true
                                        } else {
                                            val snapshotEpoch = flightMapSnapshotEpochMs
                                            snapshotEpoch != null && pin.timestampEpochMs >= snapshotEpoch
                                        }
                                    }
                                    .toList()
                            }
                        }
                        DetectionMapPage(
                            mapTitle = "Aircraft Map",
                            mapDescription = "Aircraft-only view from public radar and ADS-B ingest. Use Live Only for the freshest tracks.",
                            currentLocationOverride = flightMapCurrentLocation,
                            centerOnAircraftCoverageOnOpen = true,
                            noFlyZones = noFlyZoneOverlays,
                            showNoFlyZoneControl = mapNoFlyZonesEnabled,
                            noFlyRenderQualityLevel = mapNoFlyRenderQualityLevel,
                            pins = flightMapPins,
                            pinLimit = flightMapPinLimit,
                            onPinLimitChange = { flightMapPinLimit = it },
                            mapClusteringEnabled = mapClusteringEnabled,
                            onMapClusteringEnabledChange = onMapClusteringEnabledChanged,
                            mapClusterRangeLevel = mapClusterRangeLevel,
                            onMapClusterRangeLevelChange = onMapClusterRangeLevelChanged,
                            mapScannerSweepAnimationEnabled = mapScannerSweepAnimationEnabled,
                            onPinDetailsClick = { pin ->
                                onDeviceMapPinClick(
                                    pin.source,
                                    pin.primaryId,
                                    pin.position.latitude,
                                    pin.position.longitude,
                                    pin.encounterTimestampEpochMs ?: pin.timestampEpochMs
                                )
                            },
                            liveUpdatesAllowed = true,
                            useSourceOnlyPinColors = true,
                            enableVerticalScroll = true,
                            showLiveOnlyControl = true,
                            liveOnlyEnabled = liveOnlyOnFlightMap,
                            onLiveOnlyEnabledChange = { liveOnlyOnFlightMap = it },
                            identityModeEnabled = identityModeOnFlightMap,
                            onIdentityModeEnabledChange = { identityModeOnFlightMap = it },
                            showRadiusControl = true,
                            radiusMiles = flightMapRadiusMiles,
                            onRadiusMilesChange = { miles ->
                                val safeMiles = miles.coerceIn(10, 1000)
                                flightMapRadiusMiles = safeMiles
                                deviceMapAircraftRadiusMiles = safeMiles.coerceIn(25, 75)
                                ScanSettings.setAviationPublicRadiusMiles(context, safeMiles)
                            },
                            radiusControlLabel = "Radius (mi)",
                            radiusOptions = listOf(10, 25, 50, 100, 200, 300, 500, 750, 1000),
                            showMovingOnlyControl = false,
                            showSinceSnapshotControl = true,
                            sinceSnapshotEnabled = sinceSnapshotOnlyOnFlightMap,
                            snapshotEpochMs = flightMapSnapshotEpochMs,
                            onSinceSnapshotEnabledChange = { enabled ->
                                sinceSnapshotOnlyOnFlightMap = enabled
                                if (enabled && flightMapSnapshotEpochMs == null) {
                                    flightMapSnapshotEpochMs = System.currentTimeMillis()
                                }
                            },
                            onCaptureSnapshot = {
                                flightMapSnapshotEpochMs = System.currentTimeMillis()
                            },
                            onLiveCollect = onLiveCollect,
                            liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds
                        )
                    }
                }
            }
        } else {
            DetectionMeshNetworkPage(
                chainLinkEnabled = chainLinkEnabled,
                chainNodeId = chainNodeId,
                chainDeviceName = chainDeviceName,
                chainSharedSecret = chainSharedSecret,
                chainAutoSyncEnabled = chainAutoSyncEnabled,
                chainSharePreciseLocationEnabled = chainSharePreciseLocationEnabled,
                chainMeshSnapshot = chainMeshSnapshot,
                onChainLinkChanged = onChainLinkChanged,
                onChainDeviceNameChanged = onChainDeviceNameChanged,
                onChainSharedSecretChanged = onChainSharedSecretChanged,
                onChainAutoSyncChanged = onChainAutoSyncChanged,
                onChainSharePreciseLocationChanged = onChainSharePreciseLocationChanged,
                onRefreshPeers = onRefreshPeers,
                onSyncNow = onSyncNow,
                onWipeMeshData = onWipeMeshData
            )
        }
    }
}

private data class SignalIntelSnapshot(
    val encounterWindowCount: Int,
    val nfcEncounterCount: Int,
    val nfcUniqueTagCount: Int,
    val lastNfcEpochMs: Long?,
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
    val knowledgeGaps: List<String>
)

private fun buildSignalIntelSnapshot(
    encounters: List<Encounter>
): SignalIntelSnapshot {
    val window = selectRecentEncounterWindow(
        encounters = encounters,
        windowMs = SIGNAL_INTEL_WINDOW_MS,
        maxEncounters = SIGNAL_INTEL_MAX_ENCOUNTERS
    )

    val nfcEncounters = window.filter { it.source == EncounterSource.NFC }
    val lastNfcEpochMs = nfcEncounters.maxOfOrNull { it.timestampEpochMs }

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

    val gaps = buildList {
        if (nfcEncounters.isEmpty()) {
            add("No NFC encounters in recent window. NFC is event-driven and appears after Android tag intents are received.")
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
        nfcEncounterCount = nfcEncounters.size,
        nfcUniqueTagCount = nfcEncounters.map { it.primaryId }.toSet().size,
        lastNfcEpochMs = lastNfcEpochMs,
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
        knowledgeGaps = gaps
    )
}

@Composable
private fun DetectionSignalIntelSection(
    intel: SignalIntelSnapshot
) {
    val signalPanelKeys = listOf(
        "nfc",
        "window",
        "gnss_rf",
        "direct_acoustic",
        "direct_magnetic"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Signal", style = MaterialTheme.typography.titleLarge)
        HomeResponsiveGrid(
            items = signalPanelKeys,
            twoColumnMinWidth = 760.dp,
            threeColumnMinWidth = HOME_THREE_COLUMN_MIN_WIDTH
        ) { panelKey ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (panelKey) {
                        "nfc" -> {
                            Text("NFC", fontWeight = FontWeight.Bold)
                            Text("Encounters: ${intel.nfcEncounterCount}")
                            Text("Unique tags/devices: ${intel.nfcUniqueTagCount}")
                            Text("Last seen: ${intel.lastNfcEpochMs?.let(::formatEpoch) ?: "n/a"}")
                        }

                        "window" -> {
                            Text("Window", fontWeight = FontWeight.Bold)
                            Text("Recent encounters sampled (last ${SIGNAL_INTEL_WINDOW_MINUTES}m): ${intel.encounterWindowCount}")
                        }

                        "gnss_rf" -> {
                            Text("GNSS and RF Texture", fontWeight = FontWeight.Bold)
                            Text("GNSS location samples: ${intel.gnssLocationSampleCount}")
                            Text("GNSS interference score: ${formatRiskScorePct(intel.gnssInterferenceScore)}")
                            Text("RF texture score: ${formatRiskScorePct(intel.rfTextureScore)}")
                            Text("RF RSSI samples: ${intel.rfRssiSampleCount}")
                        }

                        "direct_acoustic" -> {
                            Text("Direct Acoustic", fontWeight = FontWeight.Bold)
                            Text("Samples (last ${SIGNAL_INTEL_WINDOW_MINUTES}m): ${intel.acousticDirectSampleCount}")
                            Text("Total stored: ${intel.acousticDirectTotalCount}")
                            Text(
                                "Latest RMS: ${intel.lastAcousticRmsDbFs?.let { String.format(Locale.US, "%.1f dBFS", it) } ?: "n/a"}"
                            )
                        }

                        else -> {
                            Text("Direct Magnetometer", fontWeight = FontWeight.Bold)
                            Text("Samples (last ${SIGNAL_INTEL_WINDOW_MINUTES}m): ${intel.magneticDirectSampleCount}")
                            Text("Total stored: ${intel.magneticDirectTotalCount}")
                            Text(
                                "Latest magnitude: ${intel.lastMagneticMagnitudeMicroTesla?.let { String.format(Locale.US, "%.2f uT", it) } ?: "n/a"}"
                            )
                        }
                    }
                }
            }
        }
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

@Composable
private fun LogsHubPage(
    logs: List<AlertLogEntry>,
    errorLogs: List<OperationalErrorLogEntry>,
    recentEncounters: List<Encounter>,
    allEncounters: List<Encounter>,
    ownedDeviceKeys: Set<String>,
    initialTab: Int,
    onClearLogs: () -> Unit,
    onClearErrorLogs: () -> Unit,
    onOpenApproachMap: (source: String, primaryId: String) -> Unit,
    onOpenNoFlyIncidentPath: (source: String, primaryId: String, zoneSummary: String, eventEpochMs: Long) -> Unit,
    onOpenDeviceDetails: (source: String, primaryId: String) -> Unit,
    onEncounterClick: (Encounter) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab.coerceIn(0, 2)) }
    val tabs = listOf("Alerts", "Errors", "Encounters")

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                DetectionLogsPage(
                    logs = logs,
                    onClearLogs = onClearLogs,
                    onOpenApproachMap = onOpenApproachMap,
                    onOpenNoFlyIncidentPath = onOpenNoFlyIncidentPath,
                    onOpenDeviceDetails = onOpenDeviceDetails
                )
            } else if (selectedTab == 1) {
                ErrorLogsPage(
                    logs = errorLogs,
                    onClearLogs = onClearErrorLogs
                )
            } else {
                EncountersPage(
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
private fun DetectionLogsPage(
    logs: List<AlertLogEntry>,
    onClearLogs: () -> Unit,
    onOpenApproachMap: (source: String, primaryId: String) -> Unit,
    onOpenNoFlyIncidentPath: (source: String, primaryId: String, zoneSummary: String, eventEpochMs: Long) -> Unit,
    onOpenDeviceDetails: (source: String, primaryId: String) -> Unit
) {
    var selectedLogTab by rememberSaveable { mutableStateOf(0) }

    val approachCount = remember(logs) { logs.count { it.type == AlertLogType.APPROACH } }
    val trackerCount = remember(logs) { logs.count { it.type == AlertLogType.TRACKER } }
    val noFlyCount = remember(logs) { logs.count { it.type == AlertLogType.NO_FLY_PASS_THROUGH } }
    val tabLabels = listOf(
        "All (${logs.size})",
        "Approach ($approachCount)",
        "Tracker ($trackerCount)",
        "No-Fly ($noFlyCount)"
    )
    val safeSelectedLogTab = selectedLogTab.coerceIn(0, tabLabels.lastIndex)

    val filteredLogs = remember(logs, safeSelectedLogTab) {
        logs.asSequence()
            .filter { entry ->
                when (safeSelectedLogTab) {
                    1 -> entry.type == AlertLogType.APPROACH
                    2 -> entry.type == AlertLogType.TRACKER
                    3 -> entry.type == AlertLogType.NO_FLY_PASS_THROUGH
                    else -> true
                }
            }
            .sortedByDescending { it.timestampEpochMs }
            .toList()
    }
    val latestLogEpoch = remember(filteredLogs) { filteredLogs.firstOrNull()?.timestampEpochMs }

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
            Text("Review historical alerts by category.")
        }
        item {
            TabRow(selectedTabIndex = safeSelectedLogTab) {
                tabLabels.forEachIndexed { index, label ->
                    Tab(
                        selected = safeSelectedLogTab == index,
                        onClick = { selectedLogTab = index },
                        text = { Text(label) }
                    )
                }
            }
        }
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = { }, label = { Text("Total ${logs.size}") })
                AssistChip(onClick = { }, label = { Text("Approach $approachCount") })
                AssistChip(onClick = { }, label = { Text("Tracker $trackerCount") })
                AssistChip(onClick = { }, label = { Text("No-Fly $noFlyCount") })
            }
        }
        item {
            val latest = latestLogEpoch?.let(::formatEpoch) ?: "n/a"
            Text(
                "Showing ${filteredLogs.size} log${if (filteredLogs.size == 1) "" else "s"} • Latest: $latest",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (filteredLogs.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No logs in this tab.",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        itemsIndexed(
            items = filteredLogs,
            key = { index, entry ->
                "${entry.timestampEpochMs}|${entry.type.name}|${entry.source}|${entry.primaryId}|${entry.message.hashCode()}|$index"
            },
            contentType = { _, _ -> "alertLog" }
        ) { _, entry ->
            val typeColor = when (entry.type) {
                AlertLogType.APPROACH -> Color(0xFF1565C0)
                AlertLogType.TRACKER -> Color(0xFFB3261E)
                AlertLogType.NO_FLY_PASS_THROUGH -> Color(0xFFEF6C00)
                AlertLogType.NFC -> Color(0xFF2E7D32)
            }
            val typeLabel = when (entry.type) {
                AlertLogType.APPROACH -> "Approach"
                AlertLogType.TRACKER -> "Tracker"
                AlertLogType.NO_FLY_PASS_THROUGH -> "No-Fly"
                AlertLogType.NFC -> "NFC"
            }
            val isApproachEntry = entry.type == AlertLogType.APPROACH
            val isNoFlyEntry = entry.type == AlertLogType.NO_FLY_PASS_THROUGH
            val opensDeviceDetails = entry.type == AlertLogType.TRACKER ||
                entry.type == AlertLogType.NFC
            val noFlyZoneSummary = if (isNoFlyEntry) {
                parseNoFlyZoneSummaryFromLogMessage(entry.message) ?: "No-fly zone"
            } else {
                null
            }
            val confidenceLabel = entry.confidence
                ?.let { " • ${String.format(Locale.US, "%.0f%%", it * 100.0)} confidence" }
                .orEmpty()
            val isClickable = isApproachEntry || isNoFlyEntry || opensDeviceDetails
            val tapHint = when {
                isApproachEntry -> "Tap to open approach map"
                isNoFlyEntry -> "Tap to open no-fly incident path"
                opensDeviceDetails -> "Tap to open device details"
                else -> null
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isClickable) {
                            Modifier.clickable {
                                when {
                                    isApproachEntry -> onOpenApproachMap(entry.source, entry.primaryId)
                                    isNoFlyEntry -> onOpenNoFlyIncidentPath(
                                        entry.source,
                                        entry.primaryId,
                                        noFlyZoneSummary ?: "No-fly zone",
                                        entry.timestampEpochMs
                                    )
                                    opensDeviceDetails -> onOpenDeviceDetails(entry.source, entry.primaryId)
                                }
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
                        text = "$typeLabel • ${listSourceLabel(entry.source, null)}",
                        color = typeColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ID: ${entry.primaryId} • ${formatEpoch(entry.timestampEpochMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(entry.message + confidenceLabel)
                    if (tapHint != null) {
                        Text(
                            text = tapHint,
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
@OptIn(ExperimentalLayoutApi::class)
private fun ErrorLogsPage(
    logs: List<OperationalErrorLogEntry>,
    onClearLogs: () -> Unit
) {
    var showWarnings by rememberSaveable { mutableStateOf(false) }
    val warningCount = remember(logs) { logs.count { it.severity == "WARNING" } }
    val errorCount = remember(logs) { logs.count { it.severity != "WARNING" } }
    val filteredLogs = remember(logs, showWarnings) {
        if (showWarnings) logs else logs.filter { it.severity != "WARNING" }
    }
    val visibleWarningCount = remember(filteredLogs) { filteredLogs.count { it.severity == "WARNING" } }
    val hiddenWarningCount = if (showWarnings) 0 else warningCount
    val categoryCounts = remember(filteredLogs) {
        filteredLogs.groupingBy { it.category }.eachCount().toList().sortedByDescending { it.second }
    }
    val latestLogEpoch = remember(filteredLogs) { filteredLogs.firstOrNull()?.timestampEpochMs }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Error Logs", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onClearLogs, enabled = logs.isNotEmpty()) {
                    Text("Clear")
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Show warnings ($warningCount)")
                Switch(
                    checked = showWarnings,
                    onCheckedChange = { showWarnings = it }
                )
            }
        }
        item {
            val latest = latestLogEpoch?.let(::formatEpoch) ?: "n/a"
            Text(
                "Showing ${filteredLogs.size} log${if (filteredLogs.size == 1) "" else "s"} • Errors $errorCount • Warnings $visibleWarningCount/${warningCount}${if (hiddenWarningCount > 0) " ($hiddenWarningCount hidden)" else ""} • Latest: $latest",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (categoryCounts.isNotEmpty()) {
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categoryCounts.take(8).forEach { (category, count) ->
                        AssistChip(onClick = { }, label = { Text("$category $count") })
                    }
                }
            }
        }
        if (filteredLogs.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (!showWarnings && warningCount > 0) {
                            "No error-level logs. Enable Show warnings to view warning entries."
                        } else {
                            "No operational errors logged."
                        },
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        items(
            items = filteredLogs,
            key = { entry ->
                "${entry.timestampEpochMs}|${entry.category}|${entry.source}|${entry.severity}|${entry.message.hashCode()}"
            },
            contentType = { "errorLog" }
        ) { entry ->
            val isWarning = entry.severity == "WARNING"
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${entry.severity} • ${entry.category} • ${entry.source}",
                        color = if (isWarning) Color(0xFFB26A00) else Color(0xFFB3261E),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatEpoch(entry.timestampEpochMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(entry.message)
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
    chainSharePreciseLocationEnabled: Boolean,
    chainMeshSnapshot: ChainMeshSnapshot,
    onChainLinkChanged: (Boolean) -> Unit,
    onChainDeviceNameChanged: (String) -> Unit,
    onChainSharedSecretChanged: (String) -> Unit,
    onChainAutoSyncChanged: (Boolean) -> Unit,
    onChainSharePreciseLocationChanged: (Boolean) -> Unit,
    onRefreshPeers: suspend () -> Unit,
    onSyncNow: suspend () -> String,
    onWipeMeshData: suspend () -> String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var syncInProgress by remember { mutableStateOf(false) }
    var wipeInProgress by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var refreshInProgress by remember { mutableStateOf(false) }
    var meshServiceActive by remember { mutableStateOf(MeshForegroundServiceController.isActive(context)) }
    var meshWipeGateState by remember { mutableStateOf(ScanSettings.getMeshWipeGateState(context)) }

    LaunchedEffect(context) {
        MeshForegroundServiceController.observeActive(context).collect { isActive ->
            meshServiceActive = isActive
        }
    }

    LaunchedEffect(context) {
        ScanSettings.observeMeshWipeGateState(context).collect { state ->
            meshWipeGateState = state
        }
    }

    val connectedCount = chainMeshSnapshot.peers.count { it.state == ChainPeerState.CONNECTED }
    val unconnectedCount = chainMeshSnapshot.peers.count { it.state != ChainPeerState.CONNECTED }
    val meshReady = chainLinkEnabled && chainSharedSecret.isNotBlank()
    val wipeGateLabel = if (meshWipeGateState.enabled) "Active" else "Inactive"
    val connectedPeers = remember(chainMeshSnapshot.peers) {
        chainMeshSnapshot.peers
            .filter { it.state == ChainPeerState.CONNECTED }
            .sortedByDescending { it.lastSeenEpochMs }
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Status", fontWeight = FontWeight.Bold)
                    Text("Chain Link: ${if (chainLinkEnabled) "On" else "Off"}")
                    Text("Passphrase: ${if (chainSharedSecret.isNotBlank()) "Set" else "Missing"}")
                    Text("Peers: $connectedCount connected • $unconnectedCount not connected")
                    Text("Wipe Gate: $wipeGateLabel")
                    if (!meshReady) {
                        Text(
                            "Enable Chain Link and set a shared passphrase to sync.",
                            color = Color(0xFFE65100)
                        )
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
                    Text("Setup", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Chain Link")
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
                        label = { Text("Shared Passphrase") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = chainLinkEnabled
                    )
                    Text("Use the same passphrase on every linked device.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("Auto Sync")
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
                        Text("Share Precise Location")
                        Switch(
                            checked = chainSharePreciseLocationEnabled,
                            onCheckedChange = onChainSharePreciseLocationChanged,
                            enabled = chainLinkEnabled
                        )
                    }
                    Text("When enabled, linked peers can place your node accurately on mesh views.")
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Actions", fontWeight = FontWeight.Bold)
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
                                        .onFailure { actionMessage = "Peer refresh failed: ${it.message ?: "unknown error"}" }
                                    refreshInProgress = false
                                }
                            }
                        ) {
                            Text(if (refreshInProgress) "Refreshing..." else "Refresh Peers")
                        }
                        if (!chainAutoSyncEnabled) {
                            Button(
                                enabled = meshReady && !syncInProgress,
                                onClick = {
                                    scope.launch {
                                        syncInProgress = true
                                        actionMessage = onSyncNow()
                                        syncInProgress = false
                                    }
                                }
                            ) {
                                Text(if (syncInProgress) "Syncing..." else "Sync Now")
                            }
                        }
                    }
                    Button(
                        enabled = !wipeInProgress,
                        onClick = {
                            scope.launch {
                                wipeInProgress = true
                                actionMessage = onWipeMeshData()
                                wipeInProgress = false
                            }
                        }
                    ) {
                        Text(if (wipeInProgress) "Wiping Mesh Data..." else "Wipe Mesh Data (All Devices)")
                    }
                    Text(
                        "Wipe clears encounters, devices, and logs locally and on authenticated peers.",
                        color = Color(0xFFB3261E)
                    )
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Connected Peers", fontWeight = FontWeight.Bold)
                    if (connectedPeers.isEmpty()) {
                        Text("No connected peers.")
                    } else {
                        connectedPeers.take(8).forEach { peer ->
                            val peerDisplay = peer.deviceName?.takeIf { it.isNotBlank() } ?: peer.nodeId
                            Text("$peerDisplay @ ${peer.host}")
                            Text(
                                "Last seen ${formatEpoch(peer.lastSeenEpochMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        if (!actionMessage.isNullOrBlank()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = actionMessage!!,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
private fun DetectionMapPage(
    mapTitle: String,
    mapDescription: String,
    showTrafficLayer: Boolean = false,
    currentLocationOverride: DetectionLocation? = null,
    centerOnAircraftCoverageOnOpen: Boolean = false,
    showCoverageRadiusCircle: Boolean = true,
    noFlyZones: List<NoFlyZoneOverlayProvider.NoFlyZonePolygon> = emptyList(),
    showNoFlyZoneControl: Boolean = false,
    noFlyRenderQualityLevel: Int = ScanSettings.DEFAULT_MAP_NO_FLY_RENDER_QUALITY_LEVEL,
    pins: List<MapPin>,
    pinLimit: Int,
    onPinLimitChange: (Int) -> Unit,
    mapClusteringEnabled: Boolean = true,
    onMapClusteringEnabledChange: (Boolean) -> Unit = {},
    mapClusterRangeLevel: Int = ScanSettings.DEFAULT_MAP_CLUSTER_RANGE_LEVEL,
    onMapClusterRangeLevelChange: (Int) -> Unit = {},
    mapScannerSweepAnimationEnabled: Boolean = ScanSettings.DEFAULT_MAP_SCANNER_SWEEP_ANIMATION_ENABLED,
    onPinDetailsClick: (MapPin) -> Unit,
    liveUpdatesAllowed: Boolean = true,
    useSourceOnlyPinColors: Boolean = false,
    enableVerticalScroll: Boolean = false,
    showLiveOnlyControl: Boolean = false,
    liveOnlyEnabled: Boolean = false,
    onLiveOnlyEnabledChange: (Boolean) -> Unit = {},
    identityModeEnabled: Boolean = false,
    onIdentityModeEnabledChange: (Boolean) -> Unit = {},
    showRadiusControl: Boolean = false,
    radiusMiles: Int = 100,
    onRadiusMilesChange: (Int) -> Unit = {},
    radiusControlLabel: String = "Radius (mi)",
    radiusOptions: List<Int> = listOf(10, 25, 50, 100, 200, 300, 500, 750, 1000),
    showMovingOnlyControl: Boolean = false,
    movingOnlyEnabled: Boolean = false,
    onMovingOnlyEnabledChange: (Boolean) -> Unit = {},
    showSinceSnapshotControl: Boolean = false,
    sinceSnapshotEnabled: Boolean = false,
    snapshotEpochMs: Long? = null,
    onSinceSnapshotEnabledChange: (Boolean) -> Unit = {},
    onCaptureSnapshot: () -> Unit = {},
    showTrackerFamilyBadge: Boolean = false,
    onLiveCollect: suspend () -> String,
    liveMapUpdateIntervalSeconds: Long
) {
    val pinLimitOptions = listOf(100, 250, 500, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000)
    val compactMapLayout = LocalConfiguration.current.screenWidthDp < 420
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasMapsApiKey = remember(context) { hasGoogleMapsApiKey(context) }
    val mapsApiKeyDiagnostic = remember(context) { getMapsApiKeyDiagnostic(context) }
    val playServicesDiagnostic = remember(context) { getPlayServicesDiagnostic(context) }
    val hasNetwork = remember(context) { hasNetworkConnectivity(context) }
    val hasLocationPermission = remember(context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    var pinLimitExpanded by remember { mutableStateOf(false) }
    var clusterRangeExpanded by remember { mutableStateOf(false) }
    var radiusExpanded by remember { mutableStateOf(false) }
    var diagnosticsVisible by rememberSaveable { mutableStateOf(false) }
    var noFlyZonesVisible by rememberSaveable { mutableStateOf(true) }
    var selectedNoFlyZoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMapPin by remember { mutableStateOf<MapPin?>(null) }
    var legendPanelVisible by rememberSaveable { mutableStateOf(false) }
    var deviceTypeFiltersCollapsed by rememberSaveable { mutableStateOf(false) }
    var liveModeEnabled by rememberSaveable { mutableStateOf(true) }
    var preciseDotsEnabled by rememberSaveable { mutableStateOf(false) }
    var liveCollectInProgress by remember { mutableStateOf(false) }
    var liveStatusMessage by remember { mutableStateOf("Live mode is off.") }
    var mapLoaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var mapTouchInProgress by remember { mutableStateOf(false) }
    var hiddenLegendSources by remember { mutableStateOf(setOf<String>()) }
    var metadataFilterQuery by rememberSaveable { mutableStateOf("") }
    val useScrollableLayout = enableVerticalScroll && controlsVisible
    var visiblePins by remember { mutableStateOf<List<MapPin>>(emptyList()) }
    LaunchedEffect(pins, pinLimit) {
        visiblePins = withContext(Dispatchers.Default) {
            selectVisiblePinsWithSourceCoverage(pins, pinLimit)
        }
    }
    val legendItems by produceState(
        initialValue = emptyList<PinLegendItem>(),
        key1 = visiblePins
    ) {
        value = withContext(Dispatchers.Default) {
            legendItemsForPins(visiblePins)
        }
    }
    val legendSources = remember(legendItems) { legendItems.map { it.source }.toSet() }
    var filteredVisiblePins by remember { mutableStateOf<List<MapPin>>(emptyList()) }
    LaunchedEffect(visiblePins, hiddenLegendSources, metadataFilterQuery) {
        val query = metadataFilterQuery.trim()
        filteredVisiblePins = withContext(Dispatchers.Default) {
            visiblePins.filter { pin ->
                val sourceVisible = pin.source !in hiddenLegendSources
                sourceVisible && pinMatchesMetadataQuery(pin, query)
            }
        }

        @Composable
        fun NoFlyZoneLayer(
            renderedNoFlyZones: List<NoFlyZoneRenderShape>,
            renderedNoFlyZoneMarkers: List<NoFlyZoneRenderShape>,
            selectedNoFlyZoneId: String?,
            onSelectZone: (String) -> Unit
        ) {
            if (renderedNoFlyZones.isEmpty()) return

            renderedNoFlyZones.forEach { zoneRender ->
                val zone = zoneRender.zone
                Polygon(
                    points = zoneRender.polygonPoints,
                    fillColor = Color(0x30E53935),
                    strokeColor = Color(0xFFE53935),
                    strokeWidth = 2f,
                    clickable = true,
                    onClick = { onSelectZone(zone.id) }
                )
            }

            renderedNoFlyZoneMarkers.forEach { zoneRender ->
                val zone = zoneRender.zone
                val point = zoneRender.centroid ?: return@forEach
                val markerState = remember(point) { MarkerState(position = point) }
                Marker(
                    state = markerState,
                    title = zone.label,
                    snippet = noFlyZoneSnippet(zone),
                    icon = noFlyZoneMarkerIcon(
                        selected = selectedNoFlyZoneId == zone.id,
                        compact = true
                    ),
                    anchor = Offset(0.5f, 0.5f),
                    onClick = {
                        onSelectZone(zone.id)
                        false
                    }
                )
            }
        }

        @Composable
        fun CoverageSweepLayer(
            showCoverageRadiusCircle: Boolean,
            proximityRenderCenter: DetectionLocation?,
            stableCoverageRadiusMeters: Double,
            aircraftRenderCenter: DetectionLocation?,
            stableAircraftCoverageRadiusMeters: Double,
            mapScannerSweepAnimationEnabled: Boolean,
            radarSweepHeadingDeg: Float
        ) {
            if (!showCoverageRadiusCircle) return

            val proximityCenter = proximityRenderCenter
            if (proximityCenter != null && stableCoverageRadiusMeters > 10.0) {
                val coverageCenter = LatLng(proximityCenter.lat, proximityCenter.lon)
                Circle(
                    center = coverageCenter,
                    radius = stableCoverageRadiusMeters,
                    strokeWidth = 2f,
                    strokeColor = Color(0xFFE65100),
                    fillColor = Color(0x1AE65100)
                )
                if (mapScannerSweepAnimationEnabled) {
                    val sweepRadiusMeters = containedSweepRadiusMeters(stableCoverageRadiusMeters)
                    Polygon(
                        points = buildRadarSweepSectorPoints(
                            center = coverageCenter,
                            radiusMeters = sweepRadiusMeters,
                            headingDegrees = radarSweepHeadingDeg,
                            halfWidthDegrees = 22f,
                            arcStepDegrees = 6f
                        ),
                        fillColor = Color(0x33FFB74D),
                        strokeColor = Color.Transparent,
                        strokeWidth = 0f
                    )
                }
            }

            val aircraftCenter = aircraftRenderCenter
            if (aircraftCenter != null && stableAircraftCoverageRadiusMeters > 10.0) {
                val coverageCenter = LatLng(aircraftCenter.lat, aircraftCenter.lon)
                Circle(
                    center = coverageCenter,
                    radius = stableAircraftCoverageRadiusMeters,
                    strokeWidth = 2f,
                    strokeColor = Color(0xFF1565C0),
                    fillColor = Color(0x1A1565C0)
                )
                if (mapScannerSweepAnimationEnabled) {
                    val sweepRadiusMeters = containedSweepRadiusMeters(stableAircraftCoverageRadiusMeters)
                    Polygon(
                        points = buildRadarSweepSectorPoints(
                            center = coverageCenter,
                            radiusMeters = sweepRadiusMeters,
                            headingDegrees = (radarSweepHeadingDeg + 120f) % 360f,
                            halfWidthDegrees = 18f,
                            arcStepDegrees = 6f
                        ),
                        fillColor = Color(0x3D4FC3F7),
                        strokeColor = Color.Transparent,
                        strokeWidth = 0f
                    )
                }
            }
        }

        @Composable
        fun MapRenderLayer(
            mapRenderItems: List<MapRenderItem>,
            preciseDotsEnabled: Boolean,
            useDenseDotMarkers: Boolean,
            useSourceOnlyPinColors: Boolean,
            showTrackerFamilyBadge: Boolean,
            renderedPinSnippetCache: Map<String, String?>,
            selectedMapPin: MapPin?,
            onSelectMapPin: (MapPin) -> Unit,
            onPinDetailsClick: (MapPin) -> Unit
        ) {
            mapRenderItems.forEach { item ->
                when (item) {
                    is MapRenderItem.SinglePin -> {
                        val pin = item.pin
                        val markerState = remember(pin.position) { MarkerState(position = pin.position) }
                        val aircraftHeading = if (pin.source == SourceCatalog.SOURCE_AIRCRAFT) {
                            pin.headingDegrees?.toFloat()?.let { value ->
                                ((value % 360f) + 360f) % 360f
                            } ?: 0f
                        } else {
                            0f
                        }
                        val showMarkerDetails = !preciseDotsEnabled
                        val markerKey = "${pin.source}|${pin.primaryId}|${pin.timestampEpochMs}"
                        Marker(
                            state = markerState,
                            title = if (showMarkerDetails) pin.title else null,
                            snippet = if (showMarkerDetails) renderedPinSnippetCache[markerKey] else null,
                            icon = if (preciseDotsEnabled) {
                                markerDotIconForPin(pin, useSourceOnlyPinColors)
                            } else {
                                markerIconForPin(pin, useSourceOnlyPinColors, showTrackerFamilyBadge)
                            },
                            anchor = if (pin.source == SourceCatalog.SOURCE_AIRCRAFT) Offset(0.5f, 0.5f) else Offset(0.5f, 1f),
                            rotation = aircraftHeading,
                            flat = pin.source == SourceCatalog.SOURCE_AIRCRAFT,
                            onClick = {
                                if (!showMarkerDetails) {
                                    val selected = selectedMapPin
                                    val isSameSelection =
                                        selected != null &&
                                            selected.source == pin.source &&
                                            selected.primaryId == pin.primaryId &&
                                            selected.timestampEpochMs == pin.timestampEpochMs

                                    if (isSameSelection) {
                                        onPinDetailsClick(pin)
                                    } else {
                                        onSelectMapPin(pin)
                                    }
                                    true
                                } else {
                                    false
                                }
                            },
                            onInfoWindowClick = {
                                onPinDetailsClick(pin)
                            }
                        )
                    }

                    is MapRenderItem.Cluster -> {
                        val markerState = remember(item.position) { MarkerState(position = item.position) }
                        Marker(
                            state = markerState,
                            title = "Cluster (${item.count})",
                            snippet = item.summary,
                            icon = clusterMarkerIcon(item),
                            anchor = Offset(0.5f, 0.5f),
                            flat = false
                        )
                    }
                }
            }
        }
    }
    val showAllHistoryVisualMode =
        showLiveOnlyControl &&
            !liveOnlyEnabled &&
            (!showMovingOnlyControl || !movingOnlyEnabled) &&
            (!showSinceSnapshotControl || !sinceSnapshotEnabled)

    var displayFilteredPins by remember { mutableStateOf<List<MapPin>>(filteredVisiblePins) }
    LaunchedEffect(filteredVisiblePins, identityModeEnabled, showAllHistoryVisualMode) {
        displayFilteredPins = withContext(Dispatchers.Default) {
            val basePins = if (showAllHistoryVisualMode) {
                filteredVisiblePins.map { pin -> pin.copy(isLive = true) }
            } else {
                filteredVisiblePins
            }

            if (!identityModeEnabled) {
                basePins
            } else {
                basePins.map { pin ->
                    val identityName = pin.secondaryId
                        ?.trim()
                        ?.takeIf { value -> value.isNotBlank() }
                    if (identityName == null) {
                        pin
                    } else {
                        pin.copy(
                            markerGlyphOverride = identityName,
                            title = buildPinTitle(
                                sourceLabel = identityName,
                                primaryId = pin.primaryId,
                                secondaryId = null,
                                motionBadge = pin.motionBadge
                            )
                        )
                    }
                }
            }
        }
    }
    val scrollState = if (useScrollableLayout) rememberScrollState() else null
    val mapStyleOptions = rememberMapStyleOptionsForTheme()
    val mapProperties = remember(hasLocationPermission, mapStyleOptions, showTrafficLayer) {
        MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            isTrafficEnabled = showTrafficLayer,
            mapStyleOptions = mapStyleOptions
        )
    }
    val aviationPerfSnapshot = remember(diagnosticsVisible, pins.size) {
        if (diagnosticsVisible) AviationPerfStatsStore.snapshot(context) else null
    }

    val observedCurrentLocation by if (currentLocationOverride == null) {
        LocationSnapshotProvider.observe(
            context,
            minUpdateIntervalMs = (liveMapUpdateIntervalSeconds.coerceAtLeast(1L) * 1000L).coerceAtMost(5_000L)
        ).collectAsState(initial = LocationSnapshotProvider.read(context))
    } else {
        remember { mutableStateOf<DetectionLocation?>(null) }
    }
    val currentLocation = currentLocationOverride ?: observedCurrentLocation
    var coverageAnchorLocation by remember { mutableStateOf(currentLocation) }
    var coverageAnchorUpdatedEpochMs by remember { mutableStateOf(0L) }
    LaunchedEffect(currentLocation, liveMapUpdateIntervalSeconds) {
        val latest = currentLocation ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        val minIntervalMs = (liveMapUpdateIntervalSeconds.coerceAtLeast(1L) * 1000L).coerceAtMost(5_000L)
        val previous = coverageAnchorLocation
        val movedMeters = if (previous == null) {
            Double.MAX_VALUE
        } else {
            distanceFromLocationMeters(
                fromLat = previous.lat,
                fromLon = previous.lon,
                toLat = latest.lat,
                toLon = latest.lon
            ) ?: 0.0
        }
        val movedMeaningfully = movedMeters >= MAP_COVERAGE_CENTER_JITTER_METERS * 2.5
        val intervalElapsed = now - coverageAnchorUpdatedEpochMs >= minIntervalMs
        if (previous == null || movedMeaningfully || intervalElapsed) {
            coverageAnchorLocation = latest
            coverageAnchorUpdatedEpochMs = now
        }
    }
    val coveragePinBuckets by produceState(
        initialValue = Pair(emptyList<MapPin>(), emptyList<MapPin>()),
        key1 = displayFilteredPins
    ) {
        value = withContext(Dispatchers.Default) {
            val nonAircraft = displayFilteredPins.filter { pin ->
                pin.source != SourceCatalog.SOURCE_AIRCRAFT &&
                    pin.source != SourceCatalog.SOURCE_REMOTE_ID &&
                    pin.source != SourceCatalog.SOURCE_CAMERA
            }
            val aircraft = displayFilteredPins.filter { pin -> pin.source == SourceCatalog.SOURCE_AIRCRAFT }
            nonAircraft to aircraft
        }
    }
    val nonAircraftCoveragePins = coveragePinBuckets.first
    val aircraftCoveragePins = coveragePinBuckets.second
    val nearbyVisiblePins by produceState(
        initialValue = displayFilteredPins,
        key1 = displayFilteredPins,
        key2 = currentLocation
    ) {
        value = withContext(Dispatchers.Default) {
            filterPinsNearCurrentLocation(
                pins = displayFilteredPins,
                currentLocation = currentLocation,
                maxDistanceMeters = MAP_AUTO_FOCUS_MAX_DISTANCE_METERS
            )
        }
    }
    val nonAircraftCoverageSnapshot by produceState<Pair<DetectionLocation, Double>?>(
        initialValue = null,
        key1 = nonAircraftCoveragePins,
        key2 = coverageAnchorLocation,
        key3 = showCoverageRadiusCircle
    ) {
        value = withContext(Dispatchers.Default) {
            val center = coverageAnchorLocation
            if (!showCoverageRadiusCircle || center == null || nonAircraftCoveragePins.isEmpty()) {
                null
            } else {
                val radiusMeters = calculateRealtimeCoverageRadiusMeters(
                    center = center,
                    pins = nonAircraftCoveragePins,
                    nowEpochMs = System.currentTimeMillis(),
                    recentWindowMs = MAP_COVERAGE_RECENT_WINDOW_MS,
                    percentile = MAP_COVERAGE_RADIUS_PERCENTILE
                )
                radiusMeters?.let { center to it }
            }
        }
    }
    val aircraftCoverageSnapshot by produceState<Pair<DetectionLocation, Double>?>(
        initialValue = null,
        key1 = aircraftCoveragePins,
        key2 = coverageAnchorLocation,
        key3 = showCoverageRadiusCircle
    ) {
        value = withContext(Dispatchers.Default) {
            val center = coverageAnchorLocation
            if (!showCoverageRadiusCircle || center == null || aircraftCoveragePins.isEmpty()) {
                null
            } else {
                val radiusMeters = calculateRealtimeCoverageRadiusMeters(
                    center = center,
                    pins = aircraftCoveragePins,
                    nowEpochMs = System.currentTimeMillis(),
                    recentWindowMs = MAP_AIRCRAFT_COVERAGE_RECENT_WINDOW_MS,
                    percentile = MAP_COVERAGE_RADIUS_PERCENTILE
                )
                radiusMeters?.let { center to it }
            }
        }
    }
    var stableCoverageCenter by remember { mutableStateOf<DetectionLocation?>(null) }
    var stableCoverageRadiusMeters by remember { mutableStateOf(0.0) }
    var stableAircraftCoverageCenter by remember { mutableStateOf<DetectionLocation?>(null) }
    var stableAircraftCoverageRadiusMeters by remember { mutableStateOf(0.0) }
    var lastCoverageRadiusUpdateEpochMs by remember { mutableStateOf(0L) }
    var lastAircraftCoverageRadiusUpdateEpochMs by remember { mutableStateOf(0L) }
    var lastCoverageEvidenceEpochMs by remember { mutableStateOf(0L) }
    var lastAircraftCoverageEvidenceEpochMs by remember { mutableStateOf(0L) }
    LaunchedEffect(showCoverageRadiusCircle, nonAircraftCoverageSnapshot, nonAircraftCoveragePins.size) {
        val latest = nonAircraftCoverageSnapshot
        val now = System.currentTimeMillis()
        if (!showCoverageRadiusCircle) {
            stableCoverageCenter = null
            stableCoverageRadiusMeters = 0.0
            lastCoverageRadiusUpdateEpochMs = 0L
            lastCoverageEvidenceEpochMs = 0L
            return@LaunchedEffect
        }
        if (nonAircraftCoveragePins.isEmpty()) {
            if (now - lastCoverageEvidenceEpochMs >= MAP_COVERAGE_EMPTY_HOLD_MS) {
                stableCoverageRadiusMeters = 0.0
            }
            return@LaunchedEffect
        }
        if (latest == null) {
            // Keep last stable values if current location momentarily drops out.
            return@LaunchedEffect
        }

        lastCoverageEvidenceEpochMs = now

        val targetCenter = latest.first
        val targetRadius = latest.second
        stableCoverageCenter = targetCenter

        val previousRadius = stableCoverageRadiusMeters

        if (previousRadius <= 0.0) {
            stableCoverageRadiusMeters = targetRadius
            return@LaunchedEffect
        }

        val stabilizedRadius = stabilizeCoverageRadius(previousRadius, targetRadius)
        if (stabilizedRadius != previousRadius) {
            val deltaMeters = abs(stabilizedRadius - previousRadius)
            val intervalElapsed = now - lastCoverageRadiusUpdateEpochMs >= MAP_COVERAGE_RADIUS_UPDATE_MIN_INTERVAL_MS
            val allowImmediate = deltaMeters >= MAP_COVERAGE_IMMEDIATE_RESIZE_DELTA_METERS
            if (intervalElapsed || allowImmediate) {
                stableCoverageRadiusMeters = stabilizedRadius
                lastCoverageRadiusUpdateEpochMs = now
            }
        }
    }
    LaunchedEffect(showCoverageRadiusCircle, aircraftCoverageSnapshot, aircraftCoveragePins.size) {
        val latest = aircraftCoverageSnapshot
        val now = System.currentTimeMillis()
        if (!showCoverageRadiusCircle) {
            stableAircraftCoverageCenter = null
            stableAircraftCoverageRadiusMeters = 0.0
            lastAircraftCoverageRadiusUpdateEpochMs = 0L
            lastAircraftCoverageEvidenceEpochMs = 0L
            return@LaunchedEffect
        }
        if (aircraftCoveragePins.isEmpty()) {
            if (now - lastAircraftCoverageEvidenceEpochMs >= MAP_COVERAGE_EMPTY_HOLD_MS) {
                stableAircraftCoverageRadiusMeters = 0.0
            }
            return@LaunchedEffect
        }
        if (latest == null) {
            // Keep last stable values if current location momentarily drops out.
            return@LaunchedEffect
        }

        lastAircraftCoverageEvidenceEpochMs = now

        val targetCenter = latest.first
        val targetRadius = latest.second
        stableAircraftCoverageCenter = targetCenter

        val previousRadius = stableAircraftCoverageRadiusMeters

        if (previousRadius <= 0.0) {
            stableAircraftCoverageRadiusMeters = targetRadius
            return@LaunchedEffect
        }

        val stabilizedRadius = stabilizeCoverageRadius(previousRadius, targetRadius)
        if (stabilizedRadius != previousRadius) {
            val deltaMeters = abs(stabilizedRadius - previousRadius)
            val intervalElapsed = now - lastAircraftCoverageRadiusUpdateEpochMs >= MAP_COVERAGE_RADIUS_UPDATE_MIN_INTERVAL_MS
            val allowImmediate = deltaMeters >= MAP_COVERAGE_IMMEDIATE_RESIZE_DELTA_METERS
            if (intervalElapsed || allowImmediate) {
                stableAircraftCoverageRadiusMeters = stabilizedRadius
                lastAircraftCoverageRadiusUpdateEpochMs = now
            }
        }
    }
    val cameraFocusPins by produceState(
        initialValue = displayFilteredPins,
        key1 = nearbyVisiblePins,
        key2 = displayFilteredPins
    ) {
        value = withContext(Dispatchers.Default) {
            val focusPins = if (nearbyVisiblePins.isNotEmpty()) nearbyVisiblePins else displayFilteredPins
            samplePinsForCamera(focusPins, MAP_CAMERA_BOUNDS_SAMPLE_LIMIT)
        }
    }
    val proximityRenderCenter = coverageAnchorLocation ?: currentLocation ?: stableCoverageCenter
    val aircraftRenderCenter = coverageAnchorLocation ?: currentLocation ?: stableAircraftCoverageCenter
    var initialFallbackPositioned by rememberSaveable { mutableStateOf(false) }
    var initialMyLocationPositioned by rememberSaveable { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
    }
    val zoomBucket = remember(cameraPositionState.position.zoom) {
        (cameraPositionState.position.zoom * 2f).roundToInt()
    }
    val renderPinLimit = remember(zoomBucket, pinLimit) {
        val bucketZoom = zoomBucket / 2f
        when {
            bucketZoom < 8.0f -> MAP_RENDER_PIN_LIMIT_FAR
            bucketZoom < 11.0f -> MAP_RENDER_PIN_LIMIT_MID
            else -> pinLimit.coerceAtMost(MOVING_PATH_RENDER_POINT_LIMIT)
        }
    }
    val renderedPins by produceState(
        initialValue = displayFilteredPins,
        key1 = displayFilteredPins,
        key2 = renderPinLimit
    ) {
        value = withContext(Dispatchers.Default) {
            samplePinsForRender(displayFilteredPins, renderPinLimit)
        }
    }
    val useDenseDotMarkers = remember(zoomBucket, displayFilteredPins.size) {
        val bucketZoom = zoomBucket / 2f
        displayFilteredPins.size > MAP_RENDER_PIN_LIMIT_MID || bucketZoom < 9.5f
    }
    val noFlyRenderAnchor = coverageAnchorLocation ?: currentLocation
    val noFlyQualityProfile = remember(noFlyRenderQualityLevel) {
        noFlyRenderQualityProfile(noFlyRenderQualityLevel)
    }
    val noFlyRenderRadiusMeters = remember(showRadiusControl, radiusMiles) {
        if (showRadiusControl) {
            radiusMiles.coerceAtLeast(1) * 1609.344
        } else {
            null
        }
    }
    val renderedNoFlyZones by produceState(
        emptyList<NoFlyZoneRenderShape>(),
        noFlyZones,
        showNoFlyZoneControl,
        noFlyZonesVisible,
        zoomBucket,
        noFlyRenderAnchor,
        selectedNoFlyZoneId,
        noFlyRenderQualityLevel,
        noFlyRenderRadiusMeters
    ) {
        value = withContext(Dispatchers.Default) {
            buildNoFlyZoneRenderShapes(
                noFlyZones = noFlyZones,
                showNoFlyZoneControl = showNoFlyZoneControl,
                noFlyZonesVisible = noFlyZonesVisible,
                zoomBucket = zoomBucket,
                anchor = noFlyRenderAnchor,
                renderRadiusMeters = noFlyRenderRadiusMeters,
                selectedZoneId = selectedNoFlyZoneId,
                qualityLevel = noFlyRenderQualityLevel,
                maxRenderCount = noFlyQualityProfile.maxRenderCount
            )
        }
    }
    val renderedNoFlyZoneMarkers = remember(renderedNoFlyZones, selectedNoFlyZoneId) {
        val selected = selectedNoFlyZoneId
        val selectedMarker = renderedNoFlyZones.firstOrNull {
            it.zone.id == selected && it.centroid != null
        }
        val base = renderedNoFlyZones
            .asSequence()
            .filter { it.centroid != null && it.zone.id != selected }
            .take(noFlyQualityProfile.maxMarkerCount)
            .toList()
        if (selectedMarker != null) listOf(selectedMarker) + base else base
    }
    val selectedNoFlyZone = remember(renderedNoFlyZones, selectedNoFlyZoneId) {
        val selectedId = selectedNoFlyZoneId ?: return@remember null
        renderedNoFlyZones.firstOrNull { it.zone.id == selectedId }?.zone
    }
    val shouldClusterPins = remember(useDenseDotMarkers, mapClusteringEnabled) {
        useDenseDotMarkers && mapClusteringEnabled
    }
    val mapRenderItems by produceState(
        initialValue = emptyList<MapRenderItem>(),
        key1 = renderedPins,
        key2 = shouldClusterPins,
        key3 = "$zoomBucket|$mapClusterRangeLevel"
    ) {
        value = withContext(Dispatchers.Default) {
            if (shouldClusterPins) {
                clusterPinsForRender(renderedPins, zoomBucket / 2f, mapClusterRangeLevel)
            } else {
                renderedPins.map { pin -> MapRenderItem.SinglePin(pin) }
            }
        }
    }
    val sweepAnimationActive = remember(
        mapScannerSweepAnimationEnabled,
        mapTouchInProgress,
        renderedPins.size
    ) {
        mapScannerSweepAnimationEnabled &&
            !mapTouchInProgress &&
            renderedPins.size <= MAP_SWEEP_DISABLE_PIN_THRESHOLD
    }
    val radarSweepHeadingDeg by produceState(
        initialValue = 0f,
        key1 = sweepAnimationActive
    ) {
        if (!sweepAnimationActive) {
            value = 0f
            return@produceState
        }
        while (true) {
            delay(MAP_SWEEP_ANIMATION_FRAME_MS)
            val next = value + MAP_SWEEP_ANIMATION_STEP_DEGREES
            value = if (next >= 360f) next - 360f else next
        }
    }
    val renderedPinSnippetCache by produceState(
        initialValue = emptyMap<String, String?>(),
        key1 = mapRenderItems,
        key2 = preciseDotsEnabled,
        key3 = useDenseDotMarkers
    ) {
        value = withContext(Dispatchers.Default) {
            if (preciseDotsEnabled) {
                emptyMap()
            } else {
                mapRenderItems
                    .asSequence()
                    .mapNotNull { item ->
                        val pin = (item as? MapRenderItem.SinglePin)?.pin ?: return@mapNotNull null
                        val key = "${pin.source}|${pin.primaryId}|${pin.timestampEpochMs}"
                        key to pin.snippetBuilder?.invoke()
                    }
                    .toMap()
            }
        }
    }

    LaunchedEffect(currentLocation, hasMapsApiKey, mapLoaded, initialMyLocationPositioned) {
        if (!hasMapsApiKey || !mapLoaded || initialMyLocationPositioned || centerOnAircraftCoverageOnOpen) return@LaunchedEffect
        val location = currentLocation ?: return@LaunchedEffect

        mapError = null
        runCatching {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.lat, location.lon),
                    17f
                )
            )
        }.onFailure {
            mapError = "Failed to center on current location: ${it.message ?: "unknown error"}"
        }.onSuccess {
            initialMyLocationPositioned = true
        }
    }

    LaunchedEffect(
        centerOnAircraftCoverageOnOpen,
        hasMapsApiKey,
        mapLoaded,
        initialFallbackPositioned,
        stableAircraftCoverageCenter,
        stableAircraftCoverageRadiusMeters
    ) {
        if (!centerOnAircraftCoverageOnOpen || !hasMapsApiKey || !mapLoaded || initialFallbackPositioned) {
            return@LaunchedEffect
        }

        val center = stableAircraftCoverageCenter ?: return@LaunchedEffect
        val radiusMeters = stableAircraftCoverageRadiusMeters
            .coerceAtLeast(100.0)

        val centerLatLng = LatLng(center.lat, center.lon)
        val sphereBounds = LatLngBounds.Builder()
            .include(offsetLatLng(centerLatLng, radiusMeters, 0.0))
            .include(offsetLatLng(centerLatLng, radiusMeters, 90.0))
            .include(offsetLatLng(centerLatLng, radiusMeters, 180.0))
            .include(offsetLatLng(centerLatLng, radiusMeters, 270.0))
            .build()

        mapError = null
        runCatching {
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(sphereBounds, 120))
        }.onFailure {
            mapError = "Failed to center on aircraft coverage sphere: ${it.message ?: "unknown error"}"
        }.onSuccess {
            initialFallbackPositioned = true
            initialMyLocationPositioned = true
        }
    }

    LaunchedEffect(cameraFocusPins, hasMapsApiKey, initialFallbackPositioned, mapLoaded, initialMyLocationPositioned) {
        if (!hasMapsApiKey || initialFallbackPositioned || !mapLoaded || initialMyLocationPositioned) {
            return@LaunchedEffect
        }

        val focusPins = cameraFocusPins

        when {
            focusPins.size > 1 -> {
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

        initialFallbackPositioned = true
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

        tickerFlow(periodMs = liveMapUpdateIntervalSeconds.coerceAtLeast(1L) * 1000L).collect {
            liveCollectInProgress = true
            liveStatusMessage = onLiveCollect()
            liveCollectInProgress = false
        }
    }

    LaunchedEffect(useScrollableLayout) {
        if (!useScrollableLayout) {
            mapTouchInProgress = false
        }
    }

    LaunchedEffect(legendSources) {
        hiddenLegendSources = hiddenLegendSources.intersect(legendSources)
    }

    LaunchedEffect(renderedNoFlyZones, selectedNoFlyZoneId) {
        if (selectedNoFlyZoneId == null) return@LaunchedEffect
        if (renderedNoFlyZones.none { it.zone.id == selectedNoFlyZoneId }) {
            selectedNoFlyZoneId = null
        }
    }

    LaunchedEffect(renderedPins, selectedMapPin) {
        val selected = selectedMapPin ?: return@LaunchedEffect
        val stillVisible = renderedPins.any { pin ->
            pin.source == selected.source &&
                pin.primaryId == selected.primaryId &&
                pin.timestampEpochMs == selected.timestampEpochMs
        }
        if (!stillVisible) {
            selectedMapPin = null
        }
    }

    LaunchedEffect(mapTouchInProgress, useScrollableLayout) {
        if (!useScrollableLayout || !mapTouchInProgress) return@LaunchedEffect
        // Failsafe: occasionally ACTION_UP/CANCEL is missed when touch ends off-map.
        delay(1200)
        mapTouchInProgress = false
    }

    val contentModifier = if (useScrollableLayout && scrollState != null) {
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState, enabled = !mapTouchInProgress)
    } else {
        Modifier.fillMaxSize()
    }

    Column(
        modifier = contentModifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = androidx.compose.ui.Alignment.CenterEnd
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { controlsVisible = !controlsVisible },
                        label = {
                            Text(
                                text = if (controlsVisible) "Panels: On" else "Panels: Off",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                    AssistChip(
                        onClick = {
                            val location = currentLocation
                            if (location == null) {
                                mapError = "Current location unavailable. Wait for GPS fix and try again."
                                return@AssistChip
                            }
                            mapError = null
                            scope.launch {
                                runCatching {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(location.lat, location.lon),
                                            17f
                                        ),
                                        650
                                    )
                                }.onFailure {
                                    mapError = "Failed to center on current location: ${it.message ?: "unknown error"}"
                                }
                            }
                        },
                        enabled = currentLocation != null,
                        label = { Text("My Location", style = MaterialTheme.typography.labelSmall) }
                    )
                    AssistChip(
                        onClick = { preciseDotsEnabled = !preciseDotsEnabled },
                        label = {
                            Text(
                                text = if (preciseDotsEnabled) "Dots: On" else "Dots: Off",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                    AssistChip(
                        onClick = { legendPanelVisible = !legendPanelVisible },
                        label = {
                            Text(
                                if (legendPanelVisible) "Hide Legend" else "Legend",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }
        if (!legendPanelVisible && legendItems.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            "Device Type Filters",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { deviceTypeFiltersCollapsed = !deviceTypeFiltersCollapsed }) {
                            Text(if (deviceTypeFiltersCollapsed) "[+]" else "[-]")
                        }
                    }
                    if (!deviceTypeFiltersCollapsed) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            legendItems.forEach { item ->
                                val sourceVisible = item.source !in hiddenLegendSources
                                FilterChip(
                                    selected = sourceVisible,
                                    onClick = {
                                        hiddenLegendSources = if (sourceVisible) {
                                            hiddenLegendSources + item.source
                                        } else {
                                            hiddenLegendSources - item.source
                                        }
                                    },
                                    label = {
                                        Text(
                                            item.label,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp)
                                        )
                                    },
                                    leadingIcon = {
                                        Text(
                                            text = "●",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                            color = if (sourceVisible) item.color else item.color.copy(alpha = 0.45f)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = legendPanelVisible,
            enter = slideInHorizontally(initialOffsetX = { -it / 2 }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it / 2 }) + fadeOut()
        ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (compactMapLayout) {
                    Text("Pin Color Legend", fontWeight = FontWeight.Bold)
                    Text(mapDescription, style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier)
                        Button(onClick = { controlsVisible = !controlsVisible }) {
                            Text(if (controlsVisible) "Hide Controls" else "Show Controls")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("Pin Color Legend", fontWeight = FontWeight.Bold)
                    }

                    Text(mapDescription, style = MaterialTheme.typography.bodySmall)

                    OutlinedTextField(
                        value = metadataFilterQuery,
                        onValueChange = { metadataFilterQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Pin Metadata Search") },
                        placeholder = { Text("Filter by ID, source, snippet, payload...") },
                        singleLine = true
                    )
                    Text(
                        "Matches ${displayFilteredPins.size}/${visiblePins.size} visible pins",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (legendItems.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            legendItems.forEach { item ->
                                val sourceVisible = item.source !in hiddenLegendSources
                                FilterChip(
                                    selected = sourceVisible,
                                    onClick = {
                                        hiddenLegendSources = if (sourceVisible) {
                                            hiddenLegendSources + item.source
                                        } else {
                                            hiddenLegendSources - item.source
                                        }
                                    },
                                    label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = {
                                        Text(
                                            text = "●",
                                            color = if (sourceVisible) item.color else item.color.copy(alpha = 0.45f)
                                        )
                                    }
                                )
                            }
                        }
                    } else {
                        Text("No source pins available for legend yet.")
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
            if (controlsVisible && !compactMapLayout) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val splitControlPanels = maxWidth >= 700.dp
                    if (splitControlPanels) {
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
                                    if (showLiveOnlyControl) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            Text("Live Only")
                                            Switch(
                                                checked = liveOnlyEnabled,
                                                onCheckedChange = onLiveOnlyEnabledChange
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Text("Identity Mode")
                                        Switch(
                                            checked = identityModeEnabled,
                                            onCheckedChange = onIdentityModeEnabledChange
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Text("Clustering")
                                        Switch(
                                            checked = mapClusteringEnabled,
                                            onCheckedChange = onMapClusteringEnabledChange
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Text("Cluster Range")
                                        Button(
                                            onClick = { clusterRangeExpanded = true },
                                            enabled = mapClusteringEnabled
                                        ) {
                                            Text(mapClusterRangeLabel(mapClusterRangeLevel))
                                        }
                                        DropdownMenu(
                                            expanded = clusterRangeExpanded,
                                            onDismissRequest = { clusterRangeExpanded = false }
                                        ) {
                                            ScanSettings.ALLOWED_MAP_CLUSTER_RANGE_LEVELS.forEach { level ->
                                                DropdownMenuItem(
                                                    text = { Text(mapClusterRangeLabel(level)) },
                                                    onClick = {
                                                        onMapClusterRangeLevelChange(level)
                                                        clusterRangeExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    if (showRadiusControl) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            Text(radiusControlLabel)
                                            Button(onClick = { radiusExpanded = true }) {
                                                Text(radiusMiles.toString())
                                            }
                                            DropdownMenu(
                                                expanded = radiusExpanded,
                                                onDismissRequest = { radiusExpanded = false }
                                            ) {
                                                radiusOptions.forEach { option ->
                                                    DropdownMenuItem(
                                                        text = { Text(option.toString()) },
                                                        onClick = {
                                                            onRadiusMilesChange(option)
                                                            radiusExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
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
                                    if (showNoFlyZoneControl) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            Text("No-fly zones")
                                            Switch(
                                                checked = noFlyZonesVisible,
                                                onCheckedChange = { noFlyZonesVisible = it }
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
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(modifier = Modifier.fillMaxWidth()) {
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
                            Card(modifier = Modifier.fillMaxWidth()) {
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
                                    if (showLiveOnlyControl) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            Text("Live Only")
                                            Switch(
                                                checked = liveOnlyEnabled,
                                                onCheckedChange = onLiveOnlyEnabledChange
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Text("Identity Mode")
                                        Switch(
                                            checked = identityModeEnabled,
                                            onCheckedChange = onIdentityModeEnabledChange
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Text("Clustering")
                                        Switch(
                                            checked = mapClusteringEnabled,
                                            onCheckedChange = onMapClusteringEnabledChange
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Text("Cluster Range")
                                        Button(
                                            onClick = { clusterRangeExpanded = true },
                                            enabled = mapClusteringEnabled
                                        ) {
                                            Text(mapClusterRangeLabel(mapClusterRangeLevel))
                                        }
                                        DropdownMenu(
                                            expanded = clusterRangeExpanded,
                                            onDismissRequest = { clusterRangeExpanded = false }
                                        ) {
                                            ScanSettings.ALLOWED_MAP_CLUSTER_RANGE_LEVELS.forEach { level ->
                                                DropdownMenuItem(
                                                    text = { Text(mapClusterRangeLabel(level)) },
                                                    onClick = {
                                                        onMapClusterRangeLevelChange(level)
                                                        clusterRangeExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    if (showRadiusControl) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            Text(radiusControlLabel)
                                            Button(onClick = { radiusExpanded = true }) {
                                                Text(radiusMiles.toString())
                                            }
                                            DropdownMenu(
                                                expanded = radiusExpanded,
                                                onDismissRequest = { radiusExpanded = false }
                                            ) {
                                                radiusOptions.forEach { option ->
                                                    DropdownMenuItem(
                                                        text = { Text(option.toString()) },
                                                        onClick = {
                                                            onRadiusMilesChange(option)
                                                            radiusExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
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
                                    if (showNoFlyZoneControl) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            Text("No-fly zones")
                                            Switch(
                                                checked = noFlyZonesVisible,
                                                onCheckedChange = { noFlyZonesVisible = it }
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
                            val currentLocationSnapshot = currentLocation
                            Text("Loaded: ${if (mapLoaded) "yes" else "no"}")
                            Text("API key: $mapsApiKeyDiagnostic")
                            Text("Play Services: $playServicesDiagnostic")
                            Text("Network: ${if (hasNetwork) "available" else "unavailable"}")
                            Text("Pins rendered: ${renderedPins.size}/${pins.size}")
                            if (showNoFlyZoneControl) {
                                Text("No-fly zones: ${renderedNoFlyZones.size}/${noFlyZones.size} (${if (noFlyZonesVisible) "visible" else "hidden"})")
                            }
                            if (showCoverageRadiusCircle && stableCoverageRadiusMeters > 10.0) {
                                Text("Proximity coverage radius (orange): ${formatDistanceFeetMiles(stableCoverageRadiusMeters)}")
                            }
                            if (showCoverageRadiusCircle && stableAircraftCoverageRadiusMeters > 10.0) {
                                Text("Aircraft coverage radius (blue): ${formatDistanceFeetMiles(stableAircraftCoverageRadiusMeters)}")
                            }
                            Text(
                                text = if (currentLocationSnapshot != null) {
                                    "Current location: ${"%.5f".format(currentLocationSnapshot.lat)}, ${"%.5f".format(currentLocationSnapshot.lon)}"
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
                            if (mapTitle == "Aircraft Map") {
                                val perf = aviationPerfSnapshot
                                if (perf != null) {
                                    Text("Aviation perf:", fontWeight = FontWeight.Bold)
                                    Text("Source: ${perf.lastSource} • Last update: ${if (perf.lastUpdatedEpochMs > 0L) formatEpoch(perf.lastUpdatedEpochMs) else "n/a"}")
                                    Text("Payload bytes: ${perf.lastPayloadBytes} • Parsed aircraft: ${perf.lastParsedAircraftCount}")
                                    Text("Filtered out of radius: ${perf.lastFilteredOutOfRadius} • Missing coords dropped: ${perf.lastDroppedMissingCoordinates}")
                                    Text("Parse ms: ${perf.lastParseDurationMs}")
                                    Text("Fetches: ${perf.networkFetches} • Cache hits: ${perf.cacheHits} • Rate-limited skips: ${perf.rateLimitedSkips}")
                                    Text("HTTP failures: ${perf.httpFailures} • Parse failures: ${perf.parseFailures}")
                                }
                            }
                        }
                    }
                }
            }
            Box(
                modifier = if (useScrollableLayout) {
                    Modifier
                        .pointerInteropFilter { motionEvent ->
                            when (motionEvent.actionMasked) {
                                android.view.MotionEvent.ACTION_DOWN,
                                android.view.MotionEvent.ACTION_POINTER_DOWN,
                                android.view.MotionEvent.ACTION_MOVE -> mapTouchInProgress = true

                                android.view.MotionEvent.ACTION_UP,
                                android.view.MotionEvent.ACTION_POINTER_UP,
                                android.view.MotionEvent.ACTION_CANCEL -> mapTouchInProgress = false
                            }
                            false
                        }
                        .fillMaxWidth()
                        .height(420.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                }
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = mapProperties,
                    onMapClick = {
                        selectedNoFlyZoneId = null
                        selectedMapPin = null
                    },
                    onMapLoaded = {
                        mapLoaded = true
                        initialFallbackPositioned = false
                        initialMyLocationPositioned = false
                    },
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        zoomGesturesEnabled = true,
                        scrollGesturesEnabled = true,
                        tiltGesturesEnabled = false,
                        rotationGesturesEnabled = false,
                        myLocationButtonEnabled = hasLocationPermission
                    )
                ) {
                    NoFlyZoneLayer(
                        renderedNoFlyZones = renderedNoFlyZones,
                        renderedNoFlyZoneMarkers = renderedNoFlyZoneMarkers,
                        selectedNoFlyZoneId = selectedNoFlyZoneId,
                        onSelectZone = { zoneId -> selectedNoFlyZoneId = zoneId }
                    )
                    CoverageSweepLayer(
                        showCoverageRadiusCircle = showCoverageRadiusCircle,
                        proximityRenderCenter = proximityRenderCenter,
                        stableCoverageRadiusMeters = stableCoverageRadiusMeters,
                        aircraftRenderCenter = aircraftRenderCenter,
                        stableAircraftCoverageRadiusMeters = stableAircraftCoverageRadiusMeters,
                        mapScannerSweepAnimationEnabled = mapScannerSweepAnimationEnabled,
                        radarSweepHeadingDeg = radarSweepHeadingDeg
                    )
                    MapRenderLayer(
                        mapRenderItems = mapRenderItems,
                        preciseDotsEnabled = preciseDotsEnabled,
                        useDenseDotMarkers = useDenseDotMarkers,
                        useSourceOnlyPinColors = useSourceOnlyPinColors,
                        showTrackerFamilyBadge = showTrackerFamilyBadge,
                        renderedPinSnippetCache = renderedPinSnippetCache,
                        selectedMapPin = selectedMapPin,
                        onSelectMapPin = { pin ->
                            selectedMapPin = pin
                            selectedNoFlyZoneId = null
                        },
                        onPinDetailsClick = { pin ->
                            selectedMapPin = null
                            onPinDetailsClick(pin)
                        }
                    )
                }

                if (selectedNoFlyZone != null) {
                    Card(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .padding(10.dp)
                            .fillMaxWidth(if (compactMapLayout) 0.95f else 0.7f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(selectedNoFlyZone.label, fontWeight = FontWeight.Bold)
                            Text("Source: ${selectedNoFlyZone.source}")
                            Text(
                                "Rule: ${selectedNoFlyZone.regulationHint?.takeIf { it.isNotBlank() } ?: "Unspecified"}"
                            )
                            Text(noFlyZoneAltitudeLabel(selectedNoFlyZone))
                            Text("Boundary points: ${selectedNoFlyZone.boundary.size}")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { selectedNoFlyZoneId = null }) {
                                    Text("Close")
                                }
                            }
                        }
                    }
                }

                selectedMapPin?.let { pin ->
                    Card(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .padding(10.dp)
                            .fillMaxWidth(if (compactMapLayout) 0.95f else 0.7f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(pin.title, fontWeight = FontWeight.Bold)
                            Text("Primary ID: ${pin.primaryId}")
                            Text("Secondary ID: ${pin.secondaryId?.takeIf { it.isNotBlank() } ?: "n/a"}")
                            Text(pin.snippetBuilder?.invoke() ?: "No details available")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { selectedMapPin = null }) {
                                    Text("Close")
                                }
                                Button(
                                    onClick = {
                                        selectedMapPin = null
                                        onPinDetailsClick(pin)
                                    }
                                ) {
                                    Text("Open Details")
                                }
                            }
                        }
                    }
                }

                if (compactMapLayout && controlsVisible) {
                    Card(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.TopEnd)
                            .padding(10.dp)
                            .fillMaxWidth(0.72f)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Map Menu", fontWeight = FontWeight.Bold)
                            AssistChip(
                                onClick = {
                                    val location = currentLocation
                                    if (location == null) {
                                        mapError = "Current location unavailable. Wait for GPS fix and try again."
                                        return@AssistChip
                                    }
                                    mapError = null
                                    scope.launch {
                                        runCatching {
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newLatLngZoom(
                                                    LatLng(location.lat, location.lon),
                                                    17f
                                                ),
                                                650
                                            )
                                        }.onFailure {
                                            mapError = "Failed to center on current location: ${it.message ?: "unknown error"}"
                                        }
                                    }
                                },
                                enabled = currentLocation != null,
                                label = { Text("My Location", style = MaterialTheme.typography.labelSmall) }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("Precise dots", style = MaterialTheme.typography.labelSmall)
                                Switch(
                                    checked = preciseDotsEnabled,
                                    onCheckedChange = { preciseDotsEnabled = it }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("Clustering", style = MaterialTheme.typography.labelSmall)
                                Switch(
                                    checked = mapClusteringEnabled,
                                    onCheckedChange = onMapClusteringEnabledChange
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("Cluster Range", style = MaterialTheme.typography.labelSmall)
                                Button(
                                    onClick = { clusterRangeExpanded = true },
                                    enabled = mapClusteringEnabled
                                ) {
                                    Text(mapClusterRangeLabel(mapClusterRangeLevel))
                                }
                            }
                            DropdownMenu(
                                expanded = clusterRangeExpanded,
                                onDismissRequest = { clusterRangeExpanded = false }
                            ) {
                                ScanSettings.ALLOWED_MAP_CLUSTER_RANGE_LEVELS.forEach { level ->
                                    DropdownMenuItem(
                                        text = { Text(mapClusterRangeLabel(level)) },
                                        onClick = {
                                            onMapClusterRangeLevelChange(level)
                                            clusterRangeExpanded = false
                                        }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("Pin Limit", fontWeight = FontWeight.Bold)
                                Button(onClick = { pinLimitExpanded = true }) {
                                    Text("$pinLimit")
                                }
                            }
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
                            Text("Showing ${visiblePins.size}/${pins.size}", style = MaterialTheme.typography.bodySmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("Live updates", fontWeight = FontWeight.Bold)
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
                            if (showLiveOnlyControl) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("Live Only")
                                    Switch(
                                        checked = liveOnlyEnabled,
                                        onCheckedChange = onLiveOnlyEnabledChange
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("Identity Mode")
                                Switch(
                                    checked = identityModeEnabled,
                                    onCheckedChange = onIdentityModeEnabledChange
                                )
                            }
                            if (showRadiusControl) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text(radiusControlLabel)
                                    Button(onClick = { radiusExpanded = true }) {
                                        Text(radiusMiles.toString())
                                    }
                                }
                                DropdownMenu(expanded = radiusExpanded, onDismissRequest = { radiusExpanded = false }) {
                                    radiusOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.toString()) },
                                            onClick = {
                                                onRadiusMilesChange(option)
                                                radiusExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
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
                            if (showNoFlyZoneControl) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("No-fly zones")
                                    Switch(
                                        checked = noFlyZonesVisible,
                                        onCheckedChange = { noFlyZonesVisible = it }
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
                                        text = snapshotEpochMs?.let { "Snapshot: ${formatEpoch(it)}" } ?: "Snapshot: not captured",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Button(onClick = onCaptureSnapshot) {
                                        Text("Capture")
                                    }
                                }
                            }
                            Text("Pin Color Legend", fontWeight = FontWeight.Bold)
                            if (legendItems.isNotEmpty()) {
                                OutlinedTextField(
                                    value = metadataFilterQuery,
                                    onValueChange = { metadataFilterQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Pin Metadata Search") },
                                    placeholder = { Text("Filter by ID, source, snippet, payload...") },
                                    singleLine = true
                                )
                                Text(
                                    "Matches ${displayFilteredPins.size}/${visiblePins.size} visible pins",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    legendItems.forEach { item ->
                                        val sourceVisible = item.source !in hiddenLegendSources
                                        FilterChip(
                                            selected = sourceVisible,
                                            onClick = {
                                                hiddenLegendSources = if (sourceVisible) {
                                                    hiddenLegendSources + item.source
                                                } else {
                                                    hiddenLegendSources - item.source
                                                }
                                            },
                                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                                            leadingIcon = {
                                                Text(
                                                    text = "●",
                                                    color = if (sourceVisible) item.color else item.color.copy(alpha = 0.45f)
                                                )
                                            }
                                        )
                                    }
                                }
                            } else {
                                Text("No source pins available for legend yet.")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoFlyZoneLayer(
    renderedNoFlyZones: List<NoFlyZoneRenderShape>,
    renderedNoFlyZoneMarkers: List<NoFlyZoneRenderShape>,
    selectedNoFlyZoneId: String?,
    onSelectZone: (String) -> Unit
) {
    if (renderedNoFlyZones.isEmpty()) return

    renderedNoFlyZones.forEach { zoneRender ->
        val zone = zoneRender.zone
        Polygon(
            points = zoneRender.polygonPoints,
            fillColor = Color(0x30E53935),
            strokeColor = Color(0xFFE53935),
            strokeWidth = 2f,
            clickable = true,
            onClick = { onSelectZone(zone.id) }
        )
    }

    renderedNoFlyZoneMarkers.forEach { zoneRender ->
        val zone = zoneRender.zone
        val point = zoneRender.centroid ?: return@forEach
        val markerState = remember(point) { MarkerState(position = point) }
        Marker(
            state = markerState,
            title = zone.label,
            snippet = noFlyZoneSnippet(zone),
            icon = noFlyZoneMarkerIcon(
                selected = selectedNoFlyZoneId == zone.id,
                compact = true
            ),
            anchor = Offset(0.5f, 0.5f),
            onClick = {
                onSelectZone(zone.id)
                false
            }
        )
    }
}

@Composable
private fun CoverageSweepLayer(
    showCoverageRadiusCircle: Boolean,
    proximityRenderCenter: DetectionLocation?,
    stableCoverageRadiusMeters: Double,
    aircraftRenderCenter: DetectionLocation?,
    stableAircraftCoverageRadiusMeters: Double,
    mapScannerSweepAnimationEnabled: Boolean,
    radarSweepHeadingDeg: Float
) {
    if (!showCoverageRadiusCircle) return

    val proximityCenter = proximityRenderCenter
    if (proximityCenter != null && stableCoverageRadiusMeters > 10.0) {
        val coverageCenter = LatLng(proximityCenter.lat, proximityCenter.lon)
        Circle(
            center = coverageCenter,
            radius = stableCoverageRadiusMeters,
            strokeWidth = 2f,
            strokeColor = Color(0xFFE65100),
            fillColor = Color(0x1AE65100)
        )
        if (mapScannerSweepAnimationEnabled) {
            val sweepRadiusMeters = containedSweepRadiusMeters(stableCoverageRadiusMeters)
            Polygon(
                points = buildRadarSweepSectorPoints(
                    center = coverageCenter,
                    radiusMeters = sweepRadiusMeters,
                    headingDegrees = radarSweepHeadingDeg,
                    halfWidthDegrees = 22f,
                    arcStepDegrees = 6f
                ),
                fillColor = Color(0x33FFB74D),
                strokeColor = Color.Transparent,
                strokeWidth = 0f
            )
        }
    }

    val aircraftCenter = aircraftRenderCenter
    if (aircraftCenter != null && stableAircraftCoverageRadiusMeters > 10.0) {
        val coverageCenter = LatLng(aircraftCenter.lat, aircraftCenter.lon)
        Circle(
            center = coverageCenter,
            radius = stableAircraftCoverageRadiusMeters,
            strokeWidth = 2f,
            strokeColor = Color(0xFF1565C0),
            fillColor = Color(0x1A1565C0)
        )
        if (mapScannerSweepAnimationEnabled) {
            val sweepRadiusMeters = containedSweepRadiusMeters(stableAircraftCoverageRadiusMeters)
            Polygon(
                points = buildRadarSweepSectorPoints(
                    center = coverageCenter,
                    radiusMeters = sweepRadiusMeters,
                    headingDegrees = (radarSweepHeadingDeg + 120f) % 360f,
                    halfWidthDegrees = 18f,
                    arcStepDegrees = 6f
                ),
                fillColor = Color(0x3D4FC3F7),
                strokeColor = Color.Transparent,
                strokeWidth = 0f
            )
        }
    }
}

@Composable
private fun MapRenderLayer(
    mapRenderItems: List<MapRenderItem>,
    preciseDotsEnabled: Boolean,
    useDenseDotMarkers: Boolean,
    useSourceOnlyPinColors: Boolean,
    showTrackerFamilyBadge: Boolean,
    renderedPinSnippetCache: Map<String, String?>,
    selectedMapPin: MapPin?,
    onSelectMapPin: (MapPin) -> Unit,
    onPinDetailsClick: (MapPin) -> Unit
) {
    mapRenderItems.forEach { item ->
        when (item) {
            is MapRenderItem.SinglePin -> {
                val pin = item.pin
                val markerState = remember(pin.position) { MarkerState(position = pin.position) }
                val aircraftHeading = if (pin.source == SourceCatalog.SOURCE_AIRCRAFT) {
                    pin.headingDegrees?.toFloat()?.let { value ->
                        ((value % 360f) + 360f) % 360f
                    } ?: 0f
                } else {
                    0f
                }
                val showMarkerDetails = !preciseDotsEnabled
                val markerKey = "${pin.source}|${pin.primaryId}|${pin.timestampEpochMs}"
                Marker(
                    state = markerState,
                    title = if (showMarkerDetails) pin.title else null,
                    snippet = if (showMarkerDetails) renderedPinSnippetCache[markerKey] else null,
                    icon = if (preciseDotsEnabled) {
                        markerDotIconForPin(pin, useSourceOnlyPinColors)
                    } else {
                        markerIconForPin(pin, useSourceOnlyPinColors, showTrackerFamilyBadge)
                    },
                    anchor = if (pin.source == SourceCatalog.SOURCE_AIRCRAFT) Offset(0.5f, 0.5f) else Offset(0.5f, 1f),
                    rotation = aircraftHeading,
                    flat = pin.source == SourceCatalog.SOURCE_AIRCRAFT,
                    onClick = {
                        if (!showMarkerDetails) {
                            val selected = selectedMapPin
                            val isSameSelection =
                                selected != null &&
                                    selected.source == pin.source &&
                                    selected.primaryId == pin.primaryId &&
                                    selected.timestampEpochMs == pin.timestampEpochMs

                            if (isSameSelection) {
                                onPinDetailsClick(pin)
                            } else {
                                onSelectMapPin(pin)
                            }
                            true
                        } else {
                            false
                        }
                    },
                    onInfoWindowClick = {
                        onPinDetailsClick(pin)
                    }
                )
            }

            is MapRenderItem.Cluster -> {
                val markerState = remember(item.position) { MarkerState(position = item.position) }
                Marker(
                    state = markerState,
                    title = "Cluster (${item.count})",
                    snippet = item.summary,
                    icon = clusterMarkerIcon(item),
                    anchor = Offset(0.5f, 0.5f),
                    flat = false
                )
            }
        }
    }
}

@Composable
private fun MovingDevicePathMapPage(
    source: String,
    primaryId: String,
    encounters: List<Encounter>,
    incidentZoneSummary: String? = null,
    incidentEventEpochMs: Long? = null,
    fallbackIncidentLocation: DetectionLocation? = null,
    enteredZoneIds: Set<String> = emptySet(),
    onOpenDeviceDetails: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mapStyleOptions = rememberMapStyleOptionsForTheme()
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
                val resolvedLatLon = when (encounter.source) {
                    EncounterSource.REMOTE_ID -> {
                        remoteIdBroadcastLatLon(encounter)
                            ?: if (isValidLatLon(encounter.lat, encounter.lon)) {
                                encounter.lat!! to encounter.lon!!
                            } else {
                                null
                            }
                    }

                    else -> if (isValidLatLon(encounter.lat, encounter.lon)) {
                        encounter.lat!! to encounter.lon!!
                    } else {
                        null
                    }
                } ?: return@mapNotNull null

                LatLng(resolvedLatLon.first, resolvedLatLon.second)
            }
            .fold(mutableListOf<LatLng>()) { acc, point ->
                val previous = acc.lastOrNull()
                if (previous == null || previous.latitude != point.latitude || previous.longitude != point.longitude) {
                    acc += point
                }
                acc
            }
    }
    val renderedPathPoints = remember(pathPoints) {
        decimateLatLngPoints(pathPoints, MOVING_PATH_RENDER_POINT_LIMIT)
    }
    val cameraPathPoints = remember(pathPoints) {
        decimateLatLngPoints(pathPoints, MAP_CAMERA_BOUNDS_SAMPLE_LIMIT)
    }
    val latestMotion = remember(deviceEncounters) { analyzeMotionSignal(deviceEncounters) }
    val latestAircraftMarkerPin = remember(deviceEncounters, pathPoints, source, primaryId) {
        val latestEncounter = deviceEncounters.lastOrNull() ?: return@remember null
        val latestPoint = pathPoints.lastOrNull() ?: return@remember null
        val hints = readAircraftVisualHints(latestEncounter.rawPayloadJson)
        MapPin(
            position = latestPoint,
            title = "Latest aircraft position",
            snippetBuilder = { formatEpoch(latestEncounter.timestampEpochMs) },
            timestampEpochMs = latestEncounter.timestampEpochMs,
            source = SourceCatalog.SOURCE_AIRCRAFT,
            primaryId = primaryId,
            secondaryId = latestEncounter.secondaryId,
            encounterTimestampEpochMs = latestEncounter.timestampEpochMs,
            aircraftIconType = hints.iconType,
            headingDegrees = hints.headingDegrees,
            isLive = true
        )
    }
    val latestAircraftLocation = remember(pathPoints) {
        pathPoints.lastOrNull()?.let { point -> DetectionLocation(point.latitude, point.longitude) }
    }
    val noFlyAnchorLocation = latestAircraftLocation ?: fallbackIncidentLocation
    val noFlyZones by produceState(
        initialValue = emptyList<NoFlyZoneOverlayProvider.NoFlyZonePolygon>(),
        key1 = noFlyAnchorLocation
    ) {
        val anchor = noFlyAnchorLocation
        value = if (anchor == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                NoFlyZoneOverlayProvider.read(context, near = anchor)
            }
        }
    }
    val renderedNoFlyZones by produceState(
        initialValue = emptyList<Pair<NoFlyZoneOverlayProvider.NoFlyZonePolygon, List<LatLng>>>(),
        key1 = noFlyZones
    ) {
        value = withContext(Dispatchers.Default) {
            noFlyZones.mapNotNull { zone ->
                val simplifiedBoundary = simplifyNoFlyBoundary(
                    boundary = zone.boundary,
                    maxPoints = INCIDENT_NO_FLY_BOUNDARY_POINT_LIMIT
                )
                val boundary = simplifiedBoundary
                    .mapNotNull { vertex ->
                        if (!isValidLatLon(vertex.lat, vertex.lon)) return@mapNotNull null
                        LatLng(vertex.lat, vertex.lon)
                    }
                if (boundary.size < 3) {
                    null
                } else {
                    zone to boundary
                }
            }
        }
    }
    val activeNoFlyZoneIds = remember(noFlyZones, noFlyAnchorLocation) {
        val location = noFlyAnchorLocation ?: return@remember emptySet<String>()
        noFlyZones
            .asSequence()
            .filter { zone -> noFlyZoneContainsPoint(zone, location.lat, location.lon) }
            .map { zone -> zone.id }
            .toSet()
    }
    val incidentRenderedNoFlyZones = remember(renderedNoFlyZones, enteredZoneIds, activeNoFlyZoneIds) {
        when {
            enteredZoneIds.isNotEmpty() -> renderedNoFlyZones.filter { (zone, _) -> zone.id in enteredZoneIds }
            activeNoFlyZoneIds.isNotEmpty() -> renderedNoFlyZones.filter { (zone, _) -> zone.id in activeNoFlyZoneIds }
            else -> emptyList()
        }
    }

    LaunchedEffect(cameraPathPoints) {
        when {
            cameraPathPoints.size > 1 -> {
                val boundsBuilder = LatLngBounds.Builder()
                cameraPathPoints.forEach { point -> boundsBuilder.include(point) }
                runCatching {
                    cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
                }
            }

            cameraPathPoints.size == 1 -> {
                runCatching {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(cameraPathPoints.first(), 15f)
                    )
                }
            }

            fallbackIncidentLocation != null -> {
                runCatching {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(fallbackIncidentLocation.lat, fallbackIncidentLocation.lon),
                            15f
                        )
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
        if (!incidentZoneSummary.isNullOrBlank()) {
            Text(
                "Incident: Entered no-fly zone $incidentZoneSummary",
                color = Color(0xFFB3261E),
                fontWeight = FontWeight.SemiBold
            )
            incidentEventEpochMs?.let { epochMs ->
                Text(
                    "Alert time: ${formatEpoch(epochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val statusText = latestMotion?.let {
            if (it.isInMotion) {
                "Status: MOVING ${formatSpeedLabel(it.speedMps)} ${formatHeadingCardinal(it.headingDeg)}"
            } else {
                "Status: STATIC ${formatSpeedLabel(it.speedMps)}"
            }
        } ?: "Status: n/a"
        Text(statusText)
        Text("Historical encounters used: ${deviceEncounters.size}")
        Text("No-fly zones rendered: ${incidentRenderedNoFlyZones.size} • Aircraft inside: ${activeNoFlyZoneIds.size}")
        Text("Path points: ${renderedPathPoints.size}/${pathPoints.size}")
        Text("Blue path direction: starts at GREEN marker and ends at RED marker.")

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(mapStyleOptions = mapStyleOptions),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    zoomGesturesEnabled = true,
                    scrollGesturesEnabled = true,
                    tiltGesturesEnabled = false,
                    rotationGesturesEnabled = false,
                    myLocationButtonEnabled = false
                )
            ) {
                incidentRenderedNoFlyZones.forEach { (zone, boundary) ->
                    val isActive = zone.id in activeNoFlyZoneIds
                    Polygon(
                        points = boundary,
                        fillColor = if (isActive) Color(0x55D32F2F) else Color(0x30E53935),
                        strokeColor = if (isActive) Color(0xFFD32F2F) else Color(0xFFE53935),
                        strokeWidth = if (isActive) 4f else 2f
                    )
                }

                if (renderedPathPoints.size >= 2) {
                    Polyline(
                        points = renderedPathPoints,
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
                    val heading = latestAircraftMarkerPin?.headingDegrees?.toFloat()?.let { value ->
                        ((value % 360f) + 360f) % 360f
                    } ?: 0f
                    Marker(
                        state = MarkerState(position = end),
                        title = "Latest device position",
                        snippet = formatEpoch(deviceEncounters.lastOrNull()?.timestampEpochMs ?: 0L),
                        icon = latestAircraftMarkerPin?.let { markerAircraftIconForPin(it) }
                            ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                        anchor = Offset(0.5f, 0.5f),
                        rotation = heading,
                        flat = true
                    )
                }

                if (pathPoints.isEmpty() && fallbackIncidentLocation != null) {
                    val incidentMarkerPin = latestAircraftMarkerPin ?: MapPin(
                        position = LatLng(fallbackIncidentLocation.lat, fallbackIncidentLocation.lon),
                        title = "Incident aircraft location",
                        snippetBuilder = { incidentEventEpochMs?.let(::formatEpoch) ?: "No timestamp" },
                        timestampEpochMs = incidentEventEpochMs ?: System.currentTimeMillis(),
                        source = SourceCatalog.SOURCE_AIRCRAFT,
                        primaryId = primaryId,
                        secondaryId = null,
                        encounterTimestampEpochMs = incidentEventEpochMs,
                        aircraftIconType = "plane",
                        headingDegrees = null,
                        isLive = true
                    )
                    Marker(
                        state = MarkerState(position = LatLng(fallbackIncidentLocation.lat, fallbackIncidentLocation.lon)),
                        title = "Incident location",
                        snippet = incidentEventEpochMs?.let(::formatEpoch) ?: "No timestamp",
                        icon = markerAircraftIconForPin(incidentMarkerPin),
                        anchor = Offset(0.5f, 0.5f),
                        flat = true
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
    val snippetBuilder: (() -> String)? = null,
    val searchableMetadata: String = "",
    val timestampEpochMs: Long,
    val source: String,
    val primaryId: String,
    val secondaryId: String?,
    val markerGlyphOverride: String? = null,
    val trackerFamilyBadge: String? = null,
    val encounterTimestampEpochMs: Long?,
    val aircraftIconType: String? = null,
    val headingDegrees: Double? = null,
    val motionBadge: String? = null,
    val motionSpeedMps: Double? = null,
    val isLive: Boolean = true
)

private fun encounterFreshnessEpochMs(encounter: Encounter): Long {
    val observedEpochMs = encounter.timestampEpochMs
    val receivedEpochMs = encounter.provenanceReceivedAtEpochMs ?: 0L
    return maxOf(observedEpochMs, receivedEpochMs)
}

private fun bestSecondaryId(encounters: List<Encounter>): String? {
    return encounters
        .asSequence()
        .sortedByDescending { encounter -> encounterFreshnessEpochMs(encounter) }
        .mapNotNull { encounter ->
            if (encounter.source == EncounterSource.CELL) {
                deriveCellSecondaryId(encounter)
            } else {
                encounter.secondaryId?.trim()
            }
        }
        .firstOrNull { value -> value.isNotBlank() }
}

private fun deriveCellSecondaryId(encounter: Encounter): String? {
    if (encounter.source != EncounterSource.CELL) return encounter.secondaryId?.trim()

    val payload = runCatching { JSONObject(encounter.rawPayloadJson) }.getOrNull()
    val operator = payload
        ?.optString("networkOperatorName", "")
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }

    val radio = payload
        ?.optString("radio", "")
        ?.trim()
        ?.uppercase(Locale.US)
        ?.takeIf { it.isNotBlank() }

    val existing = normalizeCellSecondaryLabel(encounter.secondaryId)
    if (operator != null && radio != null) {
        return "$operator Cell ($radio tower)"
    }
    if (operator != null) {
        return "$operator Cell"
    }
    if (radio != null) {
        return "Cell ($radio tower)"
    }
    return existing
}

private data class PinLegendItem(
    val source: String,
    val label: String,
    val color: Color
)

private sealed class MapRenderItem {
    data class SinglePin(val pin: MapPin) : MapRenderItem()

    data class Cluster(
        val position: LatLng,
        val count: Int,
        val source: String,
        val summary: String,
        val isLive: Boolean
    ) : MapRenderItem()
}

private fun pinMatchesMetadataQuery(pin: MapPin, query: String): Boolean {
    val normalizedQuery = query.trim().lowercase(Locale.US)
    if (normalizedQuery.isBlank()) return true

    val searchable = if (pin.searchableMetadata.isNotBlank()) {
        pin.searchableMetadata
    } else {
        buildPinSearchableMetadata(
            source = pin.source,
            primaryId = pin.primaryId,
            secondaryId = pin.secondaryId,
            title = pin.title,
            snippet = pin.snippetBuilder?.invoke(),
            rawPayloads = emptyList()
        )
    }

    return searchable.contains(normalizedQuery)
}

private fun buildPinSearchableMetadata(
    source: String,
    primaryId: String,
    secondaryId: String?,
    title: String,
    snippet: String?,
    rawPayloads: List<String>,
    maxPayloads: Int = 8,
    maxPayloadCharsEach: Int = 900
): String {
    val payloadSection = rawPayloads
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(maxPayloads)
        .joinToString(separator = " ") { it.take(maxPayloadCharsEach) }

    return buildString {
        append(source)
        append(' ')
        append(primaryId)
        append(' ')
        append(secondaryId.orEmpty())
        append(' ')
        append(title)
        append(' ')
        append(snippet.orEmpty())
        append(' ')
        append(payloadSection)
    }.lowercase(Locale.US)
}

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

private fun buildRadarSweepSectorPoints(
    center: LatLng,
    radiusMeters: Double,
    headingDegrees: Float,
    halfWidthDegrees: Float = 20f,
    arcStepDegrees: Float = 5f
): List<LatLng> {
    if (radiusMeters <= 0.0) return listOf(center)

    val start = headingDegrees - halfWidthDegrees
    val end = headingDegrees + halfWidthDegrees
    val step = arcStepDegrees.coerceAtLeast(1f)

    val points = ArrayList<LatLng>(32)
    points.add(center)

    var angle = start
    while (angle <= end) {
        points.add(offsetLatLng(center, radiusMeters, angle.toDouble()))
        angle += step
    }

    points.add(offsetLatLng(center, radiusMeters, end.toDouble()))
    return points
}

private fun normalizeCoverageRadiusMeters(radiusMeters: Double): Double {
    if (!radiusMeters.isFinite() || radiusMeters <= 0.0) return 0.0
    val stepMeters = 50.0
    return (radiusMeters / stepMeters).roundToInt() * stepMeters
}

private fun calculateRealtimeCoverageRadiusMeters(
    center: DetectionLocation,
    pins: List<MapPin>,
    nowEpochMs: Long,
    recentWindowMs: Long,
    percentile: Double
): Double? {
    if (pins.isEmpty()) return null

    val clampedPercentile = percentile.coerceIn(0.50, 1.0)
    fun distancesFrom(candidatePins: List<MapPin>): List<Double> = candidatePins
        .mapNotNull { pin ->
            distanceFromLocationMeters(
                fromLat = center.lat,
                fromLon = center.lon,
                toLat = pin.position.latitude,
                toLon = pin.position.longitude
            )
        }
        .filter { it.isFinite() && it > 0.0 }
        .sorted()

    val recentPins = pins.filter { pin ->
        val ageMs = nowEpochMs - pin.timestampEpochMs
        ageMs in 0..recentWindowMs
    }

    val candidateDistances = distancesFrom(recentPins).ifEmpty {
        distancesFrom(pins)
    }
    if (candidateDistances.isEmpty()) return null

    val percentileIndex = ((candidateDistances.lastIndex.toDouble()) * clampedPercentile)
        .roundToInt()
        .coerceIn(0, candidateDistances.lastIndex)
    return normalizeCoverageRadiusMeters(candidateDistances[percentileIndex])
}

private fun stabilizeCoverageRadius(previousRadiusMeters: Double, targetRadiusMeters: Double): Double {
    if (previousRadiusMeters <= 0.0) return targetRadiusMeters

    val radiusDeltaMeters = targetRadiusMeters - previousRadiusMeters
    val radiusDeadband = maxOf(
        MAP_COVERAGE_RADIUS_JITTER_METERS * 1.6,
        previousRadiusMeters * 0.08,
        targetRadiusMeters * 0.05
    )
    if (abs(radiusDeltaMeters) < radiusDeadband) {
        return previousRadiusMeters
    }

    val softenedDelta = radiusDeltaMeters * 0.28
    val limitedDelta = softenedDelta.coerceIn(
        -MAP_COVERAGE_RADIUS_MAX_STEP_METERS * 0.75,
        MAP_COVERAGE_RADIUS_MAX_STEP_METERS * 0.75
    )
    return normalizeCoverageRadiusMeters(
        (previousRadiusMeters + limitedDelta).coerceAtLeast(0.0)
    )
}

private fun containedSweepRadiusMeters(containerRadiusMeters: Double): Double {
    val clampedRadius = containerRadiusMeters.coerceAtLeast(0.0)
    if (clampedRadius <= 0.0) return 0.0
    return clampedRadius
}

private fun centroidOfDetectionPolygon(points: List<DetectionLocation>): DetectionLocation? {
    if (points.isEmpty()) return null
    var latTotal = 0.0
    var lonTotal = 0.0
    points.forEach { point ->
        latTotal += point.lat
        lonTotal += point.lon
    }
    val count = points.size.toDouble()
    return DetectionLocation(
        lat = latTotal / count,
        lon = lonTotal / count
    )
}

private data class NoFlyZoneRenderShape(
    val zone: NoFlyZoneOverlayProvider.NoFlyZonePolygon,
    val polygonPoints: List<LatLng>,
    val centroid: LatLng?
)

private fun buildNoFlyZoneRenderShapes(
    noFlyZones: List<NoFlyZoneOverlayProvider.NoFlyZonePolygon>,
    showNoFlyZoneControl: Boolean,
    noFlyZonesVisible: Boolean,
    zoomBucket: Int,
    anchor: DetectionLocation?,
    renderRadiusMeters: Double?,
    selectedZoneId: String?,
    qualityLevel: Int,
    maxRenderCount: Int
): List<NoFlyZoneRenderShape> {
    if (!showNoFlyZoneControl || !noFlyZonesVisible || noFlyZones.isEmpty()) return emptyList()

    val valid = noFlyZones.filter { zone -> zone.boundary.size >= 3 }
    if (valid.isEmpty()) return emptyList()

    val safeLimit = maxRenderCount.coerceAtLeast(1)
    val prioritized = if (anchor != null) {
        val byDistance = valid
            .mapNotNull { zone ->
                val centroid = centroidOfDetectionPolygon(zone.boundary) ?: return@mapNotNull null
                val distance = distanceFromLocationMeters(
                    fromLat = anchor.lat,
                    fromLon = anchor.lon,
                    toLat = centroid.lat,
                    toLon = centroid.lon
                ) ?: Double.MAX_VALUE
                zone to distance
            }
            .sortedBy { it.second }

        if (renderRadiusMeters != null && renderRadiusMeters > 0.0) {
            val inRadius = byDistance
                .filter { (_, distance) -> distance <= renderRadiusMeters }
                .map { it.first }
            val outOfRadius = byDistance
                .filter { (_, distance) -> distance > renderRadiusMeters }
                .map { it.first }
                .take((safeLimit - inRadius.size).coerceAtLeast(0))
            (inRadius + outOfRadius).toMutableList()
        } else {
            byDistance
                .take(safeLimit)
                .map { it.first }
                .toMutableList()
        }
    } else {
        valid.take(safeLimit).toMutableList()
    }

    if (!selectedZoneId.isNullOrBlank() && prioritized.none { it.id == selectedZoneId }) {
        valid.firstOrNull { it.id == selectedZoneId }?.let { prioritized += it }
    }

    val baseBoundaryPointLimit = when {
        zoomBucket < 18 -> 60
        zoomBucket < 24 -> 100
        else -> 160
    }
    val pointScale = noFlyRenderQualityProfile(qualityLevel).pointScale
    val boundaryPointLimit = (baseBoundaryPointLimit * pointScale)
        .roundToInt()
        .coerceAtLeast(24)

    return prioritized.distinctBy { it.id }.mapNotNull { zone ->
        val simplifiedBoundary = simplifyNoFlyBoundary(zone.boundary, boundaryPointLimit)
        if (simplifiedBoundary.size < 3) return@mapNotNull null
        val polygonPoints = simplifiedBoundary.map { point -> LatLng(point.lat, point.lon) }
        val centroid = centroidOfDetectionPolygon(simplifiedBoundary)?.let { LatLng(it.lat, it.lon) }
        NoFlyZoneRenderShape(
            zone = zone,
            polygonPoints = polygonPoints,
            centroid = centroid
        )
    }
}

private fun simplifyNoFlyBoundary(
    boundary: List<DetectionLocation>,
    maxPoints: Int
): List<DetectionLocation> {
    if (boundary.size <= maxPoints || maxPoints < 3) return boundary

    val safeLimit = maxPoints.coerceAtLeast(3)
    val lastIndex = boundary.lastIndex
    val sampled = ArrayList<DetectionLocation>(safeLimit)
    val step = lastIndex.toDouble() / (safeLimit - 1).toDouble()
    var previousIndex = -1

    for (sampleIndex in 0 until safeLimit) {
        val boundaryIndex = (sampleIndex * step).roundToInt().coerceIn(0, lastIndex)
        if (boundaryIndex != previousIndex) {
            sampled += boundary[boundaryIndex]
            previousIndex = boundaryIndex
        }
    }

    if (sampled.size < 3) return boundary.take(3)
    return sampled
}

private fun noFlyZoneSnippet(zone: NoFlyZoneOverlayProvider.NoFlyZonePolygon): String {
    val altitudeLabel = noFlyZoneAltitudeLabel(zone)
    val ruleLabel = zone.regulationHint?.takeIf { it.isNotBlank() } ?: "Unspecified"
    return "Source: ${zone.source}\nRule: $ruleLabel\n$altitudeLabel"
}

private fun noFlyZoneAltitudeLabel(zone: NoFlyZoneOverlayProvider.NoFlyZonePolygon): String = when {
    zone.lowerAltitudeFeet != null && zone.upperAltitudeFeet != null -> "Altitude: ${zone.lowerAltitudeFeet}-${zone.upperAltitudeFeet} ft"
    zone.upperAltitudeFeet != null -> "Altitude: up to ${zone.upperAltitudeFeet} ft"
    zone.lowerAltitudeFeet != null -> "Altitude: from ${zone.lowerAltitudeFeet} ft"
    else -> "Altitude: unspecified"
}

private fun selectVisiblePinsWithSourceCoverage(pins: List<MapPin>, pinLimit: Int): List<MapPin> {
    if (pins.isEmpty()) return emptyList()
    val safeLimit = pinLimit.coerceAtLeast(1)
    if (pins.size <= safeLimit) return pins

    // Preserve one newest pin per source so map/legend do not silently drop source types.
    val newestPerSourceMap = LinkedHashMap<String, MapPin>()
    pins.forEach { pin ->
        val existing = newestPerSourceMap[pin.source]
        if (existing == null || pin.timestampEpochMs > existing.timestampEpochMs) {
            newestPerSourceMap[pin.source] = pin
        }
    }
    val newestPerSource = newestPerSourceMap.values.sortedByDescending { it.timestampEpochMs }

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

private fun spreadOverlappingMapPinsMinimal(
    pins: List<MapPin>,
    minSeparationMeters: Double
): List<MapPin> {
    if (pins.size < 2) return pins

    val safeMinSeparationMeters = minSeparationMeters.coerceIn(0.5, 3.0)
    val maxIterations = if (pins.size > 180) 4 else 8
    val placed = mutableListOf<MapPin>()

    pins
        .sortedByDescending { it.timestampEpochMs }
        .forEachIndexed { index, pin ->
            var candidate = pin.position

            repeat(maxIterations) { iteration ->
                var hadConflict = false

                placed.forEach { other ->
                    val distance = distanceFromLocationMeters(
                        fromLat = candidate.latitude,
                        fromLon = candidate.longitude,
                        toLat = other.position.latitude,
                        toLon = other.position.longitude
                    ) ?: return@forEach

                    if (distance < safeMinSeparationMeters) {
                        hadConflict = true
                        val neededShiftMeters = (safeMinSeparationMeters - distance + 0.03).coerceAtLeast(0.04)
                        val awayBearing = bearingDegrees(
                            from = other.position,
                            to = candidate
                        ) ?: (((index * 53) + (iteration * 37)) % 360).toDouble()
                        candidate = offsetLatLng(
                            base = candidate,
                            distanceMeters = neededShiftMeters,
                            bearingDegrees = awayBearing
                        )
                    }
                }

                if (!hadConflict) {
                    return@repeat
                }
            }

            placed += pin.copy(position = candidate)
        }

    return placed
}

private fun bearingDegrees(from: LatLng, to: LatLng): Double? {
    if (from.latitude == to.latitude && from.longitude == to.longitude) return null

    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2)
    val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
        kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLon)
    val raw = Math.toDegrees(kotlin.math.atan2(y, x))
    return ((raw % 360.0) + 360.0) % 360.0
}

private suspend fun buildStartupPrewarmedDeviceMapPins(
    encounters: List<Encounter>,
    ownedDeviceKeys: Set<String>,
    approachDetectionEnabled: Boolean,
    sourceScanIntervals: Map<String, Long>,
    liveMapUpdateIntervalSeconds: Long,
    maxDeviceCandidates: Int,
    suppressLikelyRandomizedWifiOneOffs: Boolean = true,
    suppressLikelyRandomizedBleOneOffs: Boolean = true
): List<MapPin> {
    if (encounters.isEmpty()) return emptyList()
    val nowEpochMs = System.currentTimeMillis()

    val latestSnapshotEpochMs = encounters.maxOfOrNull { encounter ->
        encounterFreshnessEpochMs(encounter)
    } ?: return emptyList()
    val recentSnapshotEncounters = encounters.filter { encounter ->
        val sourceType = scanTypeKeyForSourceName(encounter.source.name)
        val recentWindowMs = mapRecentWindowMsForSource(
            sourceType = sourceType,
            sourceScanIntervals = sourceScanIntervals,
            liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds
        )
        encounterFreshnessEpochMs(encounter) >= latestSnapshotEpochMs - recentWindowMs
    }

    if (recentSnapshotEncounters.isEmpty()) return emptyList()

    val groupedByDevice = recentSnapshotEncounters
        .asSequence()
        .groupBy { "${it.source.name}|${it.primaryId}" }

    val topDeviceGroups = groupedByDevice
        .values
        .asSequence()
        .mapNotNull { deviceEncounters ->
            val latest = deviceEncounters.maxByOrNull { encounter ->
                encounterFreshnessEpochMs(encounter)
            } ?: return@mapNotNull null
            if (
                shouldSuppressLikelyRandomizedWifiNoise(
                    source = latest.source,
                    primaryId = latest.primaryId,
                    seenCount = deviceEncounters.size,
                    wifiSuppressionEnabled = suppressLikelyRandomizedWifiOneOffs,
                    bleSuppressionEnabled = suppressLikelyRandomizedBleOneOffs
                )
            ) {
                return@mapNotNull null
            }
            latest to deviceEncounters
        }
        .sortedByDescending { (latest, _) -> encounterFreshnessEpochMs(latest) }
        .take(maxDeviceCandidates.coerceAtLeast(100))

    val pins = mutableListOf<MapPin>()
    topDeviceGroups.forEach { (latest, deviceEncounters) ->
        val sourceEnum = latest.source
        val sourceName = sourceEnum.name
        val preferredSecondaryId = bestSecondaryId(deviceEncounters)
        val resolved = resolveDeviceLocation(sourceEnum, deviceEncounters) ?: return@forEach
        if (!isValidLatLon(resolved.lat, resolved.lon)) return@forEach

        val isCameraSource = sourceEnum == EncounterSource.CAMERA
        val motionSignal = if (isCameraSource) null else analyzeMotionSignal(deviceEncounters)
        val approachSignal = if (approachDetectionEnabled && isApproachEligibleSource(sourceEnum)) {
            analyzeApproachSignal(deviceEncounters)
        } else {
            null
        }
        val sourceType = scanTypeKeyForSourceName(sourceName)
        val liveWindowMs = mapLiveWindowMsForSource(
            sourceType = sourceType,
            sourceScanIntervals = sourceScanIntervals,
            liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds
        )
        val latestFreshnessEpochMs = encounterFreshnessEpochMs(latest)
        val isLive = latestFreshnessEpochMs >= (nowEpochMs - liveWindowMs)
        val motionBadge = if (isCameraSource) {
            null
        } else {
            motionSignal?.let { if (it.isInMotion) "MOVING" else "STATIC" }
        }
        val ownershipSnippet = if (OwnedDeviceRegistry.keyFor(sourceName, latest.primaryId) in ownedDeviceKeys) {
            " • Marked as Mine"
        } else {
            ""
        }
        val approachSnippet = approachSignal
            ?.takeIf { it.isApproaching }
            ?.let { signal -> " • Approaching (${(signal.confidence * 100.0).toInt()}%)" }
            .orEmpty()
        val freshnessSnippet = if (isLive) {
            "Live • Seen ${formatMapPinAge(latestFreshnessEpochMs)} ago"
        } else {
            "Recent • Seen ${formatMapPinAge(latestFreshnessEpochMs)} ago"
        }
        val sourceLabel = listSourceLabel(sourceName, preferredSecondaryId)
        val pinTitle = buildPinTitle(
            sourceLabel = sourceLabel,
            primaryId = latest.primaryId,
            secondaryId = preferredSecondaryId,
            motionBadge = motionBadge
        )
        val line1 = "Seen ${deviceEncounters.size}x"
        val line2 = "$freshnessSnippet • Last ${formatEpoch(latestFreshnessEpochMs)}$approachSnippet"
        val line3 = "Approx range ${resolved.approximateRangeMeters?.let { formatDistanceFeetMiles(it) } ?: "n/a"}$ownershipSnippet"
        val pinSnippet = buildThreeLineSnippet(
            line1 = line1,
            line2 = line2,
            line3 = line3
        )

        pins += MapPin(
            position = LatLng(resolved.lat, resolved.lon),
            title = pinTitle,
            snippetBuilder = {
                pinSnippet
            },
            searchableMetadata = buildPinSearchableMetadata(
                source = sourceName,
                primaryId = latest.primaryId,
                secondaryId = preferredSecondaryId,
                title = pinTitle,
                snippet = pinSnippet,
                rawPayloads = deviceEncounters
                    .sortedByDescending { it.timestampEpochMs }
                    .map { it.rawPayloadJson }
            ),
            timestampEpochMs = latestFreshnessEpochMs,
            source = sourceName,
            primaryId = latest.primaryId,
            secondaryId = preferredSecondaryId,
            encounterTimestampEpochMs = latest.timestampEpochMs,
            aircraftIconType = null,
            headingDegrees = null,
            motionBadge = motionBadge,
            motionSpeedMps = motionSignal?.speedMps,
            isLive = isLive
        )
    }

    return spreadOverlappingMapPins(pins)
}

private fun samplePinsForCamera(pins: List<MapPin>, maxPoints: Int): List<MapPin> {
    if (pins.size <= maxPoints) return pins
    val safeLimit = maxPoints.coerceAtLeast(8)
    val selected = LinkedHashMap<String, MapPin>()

    fun add(pin: MapPin?) {
        if (pin == null || selected.size >= safeLimit) return
        val key = "${pin.source}|${pin.primaryId}|${pin.timestampEpochMs}"
        selected[key] = pin
    }

    add(pins.firstOrNull())
    add(pins.lastOrNull())
    add(pins.maxByOrNull { it.position.latitude })
    add(pins.minByOrNull { it.position.latitude })
    add(pins.maxByOrNull { it.position.longitude })
    add(pins.minByOrNull { it.position.longitude })

    if (selected.size < safeLimit) {
        val step = pins.size.toDouble() / safeLimit.toDouble()
        var index = 0.0
        while (selected.size < safeLimit && index < pins.size) {
            add(pins[index.toInt().coerceIn(0, pins.lastIndex)])
            index += step
        }
    }

    return selected.values.toList()
}

private fun samplePinsForRender(pins: List<MapPin>, maxPoints: Int): List<MapPin> {
    if (pins.size <= maxPoints) return pins
    val safeLimit = maxPoints.coerceAtLeast(24)

    val newestPerSourceMap = LinkedHashMap<String, MapPin>()
    pins.forEach { pin ->
        val existing = newestPerSourceMap[pin.source]
        if (existing == null || pin.timestampEpochMs > existing.timestampEpochMs) {
            newestPerSourceMap[pin.source] = pin
        }
    }
    val newestPerSource = newestPerSourceMap.values.sortedByDescending { it.timestampEpochMs }

    val selected = LinkedHashMap<String, MapPin>()
    newestPerSource.take(safeLimit).forEach { pin ->
        val key = "${pin.source}|${pin.primaryId}|${pin.timestampEpochMs}"
        selected[key] = pin
    }

    if (selected.size < safeLimit) {
        val step = pins.size.toDouble() / safeLimit.toDouble()
        var index = 0.0
        while (selected.size < safeLimit && index < pins.size) {
            val pin = pins[index.toInt().coerceIn(0, pins.lastIndex)]
            val key = "${pin.source}|${pin.primaryId}|${pin.timestampEpochMs}"
            selected[key] = pin
            index += step
        }
    }

    return selected.values
        .sortedByDescending { it.timestampEpochMs }
        .take(safeLimit)
}

private fun decimateLatLngPoints(points: List<LatLng>, maxPoints: Int): List<LatLng> {
    if (points.size <= maxPoints || maxPoints < 3) return points

    val lastIndex = points.lastIndex
    val sampled = ArrayList<LatLng>(maxPoints)
    val step = lastIndex.toDouble() / (maxPoints - 1).toDouble()
    var previousIndex = -1

    for (sampleIndex in 0 until maxPoints) {
        val pointIndex = (sampleIndex * step).roundToInt().coerceIn(0, lastIndex)
        if (pointIndex != previousIndex) {
            sampled += points[pointIndex]
            previousIndex = pointIndex
        }
    }

    if (sampled.lastOrNull() != points.last()) {
        sampled += points.last()
    }

    return sampled
}

private data class SourceTypeUiMeta(
    val source: String,
    val scanType: String,
    val settingsLabel: String,
    val legendLabel: String,
    val listLabel: String,
    val glyph: String,
    val hue: Float,
    val color: Color,
    val supportsSecondaryId: Boolean = false,
    val secondaryIdLabel: String = "Secondary ID"
)

private val SOURCE_TYPE_UI_META_ORDERED = listOf(
    SourceTypeUiMeta(
        source = SourceCatalog.SOURCE_CELL,
        scanType = SourceCatalog.KEY_CELLULAR,
        settingsLabel = "Cellular",
        legendLabel = "CELL TOWER",
        listLabel = "CELL TOWER",
        glyph = "CELL",
        hue = BitmapDescriptorFactory.HUE_AZURE,
        color = Color(0xFF1E88E5)
    ),
    SourceTypeUiMeta(
        source = SourceCatalog.SOURCE_CAMERA,
        scanType = SourceCatalog.KEY_CAMERA,
        settingsLabel = "Camera",
        legendLabel = "CAMERA",
        listLabel = "CAMERA",
        glyph = "CAM",
        hue = BitmapDescriptorFactory.HUE_ROSE,
        color = Color(0xFFD81B60),
        supportsSecondaryId = true,
        secondaryIdLabel = "Camera Label"
    ),
    SourceTypeUiMeta(
        source = SourceCatalog.SOURCE_ARGUS_MESH,
        scanType = "mesh",
        settingsLabel = "Argus Mesh",
        legendLabel = "ARGUS MESH",
        listLabel = "ARGUS MESH",
        glyph = "MSH",
        hue = BitmapDescriptorFactory.HUE_AZURE,
        color = Color(0xFF1976D2),
        supportsSecondaryId = true,
        secondaryIdLabel = "Peer Name"
    ),
    SourceTypeUiMeta(
        source = SourceCatalog.SOURCE_WIFI,
        scanType = SourceCatalog.KEY_WIFI,
        settingsLabel = "Wi-Fi",
        legendLabel = "WIFI",
        listLabel = "WIFI",
        glyph = "WIFI",
        hue = BitmapDescriptorFactory.HUE_ORANGE,
        color = Color(0xFFFB8C00),
        supportsSecondaryId = true,
        secondaryIdLabel = "SSID"
    ),
    SourceTypeUiMeta(
        source = SourceCatalog.SOURCE_WIFI_SWEEP,
        scanType = SourceCatalog.KEY_WIFI,
        settingsLabel = "Wi-Fi Sweep",
        legendLabel = "WIFI SWEEP",
        listLabel = "WIFI SWEEP",
        glyph = "WFS",
        hue = BitmapDescriptorFactory.HUE_ORANGE,
        color = Color(0xFFFFA726)
    ),
    SourceTypeUiMeta(
        source = SourceCatalog.SOURCE_WIFI_DIRECT,
        scanType = SourceCatalog.KEY_WIFI_DIRECT,
        settingsLabel = "Wi-Fi Direct",
        legendLabel = "WIFI DIRECT",
        listLabel = "WIFI DIRECT",
        glyph = "WFD",
        hue = BitmapDescriptorFactory.HUE_YELLOW,
        color = Color(0xFFFBC02D),
        supportsSecondaryId = true,
        secondaryIdLabel = "Peer Name"
    ),
    SourceTypeUiMeta(
        source = SourceCatalog.SOURCE_BLUETOOTH_LE,
        scanType = SourceCatalog.KEY_BLE,
        settingsLabel = "Bluetooth (LE + Classic + Remote ID)",
        legendLabel = "BLUETOOTH",
        listLabel = "BLUETOOTH",
        glyph = "BLE",
        hue = BitmapDescriptorFactory.HUE_GREEN,
        color = Color(0xFF43A047),
        supportsSecondaryId = true,
        secondaryIdLabel = "Device Name"
    ),
    SourceTypeUiMeta(
        source = SourceCatalog.SOURCE_BLUETOOTH_LE_SWEEP,
        scanType = SourceCatalog.KEY_BLE,
        settingsLabel = "Bluetooth LE Sweep",
        legendLabel = "BLE SWEEP",
        listLabel = "BLE SWEEP",
        glyph = "BLS",
        hue = BitmapDescriptorFactory.HUE_GREEN,
        color = Color(0xFF66BB6A)
    ),
    SourceTypeUiMeta(
        source = SourceCatalog.SOURCE_BLUETOOTH_CLASSIC,
        scanType = SourceCatalog.KEY_BT_CLASSIC,
        settingsLabel = "Bluetooth Classic",
        legendLabel = "BLUETOOTH CLASSIC",
        listLabel = "BLUETOOTH CLASSIC",
        glyph = "BT",
        hue = BitmapDescriptorFactory.HUE_CYAN,
        color = Color(0xFF00ACC1),
        supportsSecondaryId = true,
        secondaryIdLabel = "Device Name"
    ),
    SourceTypeUiMeta(
        source = SourceCatalog.SOURCE_NFC,
        scanType = SourceCatalog.KEY_NFC,
        settingsLabel = "NFC",
        legendLabel = "NFC",
        listLabel = "NFC",
        glyph = "NFC",
        hue = BitmapDescriptorFactory.HUE_RED,
        color = Color(0xFFEF5350),
        supportsSecondaryId = true,
        secondaryIdLabel = "Tech"
    ),
    SourceTypeUiMeta(
        source = SourceCatalog.SOURCE_REMOTE_ID,
        scanType = SourceCatalog.KEY_REMOTE_ID,
        settingsLabel = "Remote ID",
        legendLabel = "REMOTE ID",
        listLabel = "REMOTE ID",
        glyph = "RID",
        hue = BitmapDescriptorFactory.HUE_VIOLET,
        color = Color(0xFF8E24AA)
    ),
    SourceTypeUiMeta(
        source = SourceCatalog.SOURCE_AIRCRAFT,
        scanType = SourceCatalog.KEY_AIRCRAFT,
        settingsLabel = "Aircraft",
        legendLabel = "AIRCRAFT",
        listLabel = "AIRCRAFT",
        glyph = "AIR",
        hue = BitmapDescriptorFactory.HUE_BLUE,
        color = Color(0xFF1565C0),
        supportsSecondaryId = true,
        secondaryIdLabel = "Callsign"
    ),
    SourceTypeUiMeta(
        source = SourceCatalog.SOURCE_SDR,
        scanType = SourceCatalog.KEY_SDR,
        settingsLabel = "SDR",
        legendLabel = "SDR",
        listLabel = "SDR",
        glyph = "SDR",
        hue = BitmapDescriptorFactory.HUE_RED,
        color = Color(0xFFE53935)
    ),
    SourceTypeUiMeta(
        source = SourceCatalog.SOURCE_UNKNOWN_RF,
        scanType = "unknown_rf",
        settingsLabel = "Unknown RF",
        legendLabel = "UNKNOWN RF",
        listLabel = "UNKNOWN RF",
        glyph = "RF",
        hue = BitmapDescriptorFactory.HUE_ROSE,
        color = Color(0xFFE91E63)
    )
)

private val SOURCE_TYPE_UI_META_BY_SOURCE = SOURCE_TYPE_UI_META_ORDERED.associateBy { it.source }
private val SOURCE_TYPE_UI_META_BY_SCAN_TYPE = SOURCE_TYPE_UI_META_ORDERED
    .groupBy { it.scanType }
    .mapValues { (_, entries) -> entries.first() }

private val EXTRA_SCAN_SOURCE_LABELS = mapOf(
    SourceCatalog.KEY_ACOUSTIC to "Acoustic",
    SourceCatalog.KEY_MAGNETIC to "Magnetometer"
)

private fun markerHueForSource(source: String): Float =
    SOURCE_TYPE_UI_META_BY_SOURCE[source]?.hue ?: BitmapDescriptorFactory.HUE_RED

private fun markerLegendColorForSource(source: String): Color =
    SOURCE_TYPE_UI_META_BY_SOURCE[source]?.color ?: Color(0xFFD32F2F)

private fun markerLegendLabelForSource(source: String): String =
    SOURCE_TYPE_UI_META_BY_SOURCE[source]?.legendLabel ?: source

private fun legendItemsForPins(pins: List<MapPin>): List<PinLegendItem> {
    val preferredOrder = SOURCE_TYPE_UI_META_ORDERED.map { it.source }
    val sourcesInPins = pins.map { it.source }.toSet()
    val orderedSources = preferredOrder.filter { it in sourcesInPins } +
        sourcesInPins.filterNot { it in preferredOrder }.sorted()

    return orderedSources.map { source ->
        PinLegendItem(
            source = source,
            label = markerLegendLabelForSource(source),
            color = markerLegendColorForSource(source)
        )
    }
}

private fun buildPinTitle(sourceLabel: String, primaryId: String, secondaryId: String?, motionBadge: String?): String {
    val shortId = if (primaryId.length <= 18) primaryId else primaryId.take(15) + "..."
    val shortSecondary = secondaryId
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { value -> if (value.length <= 16) value else value.take(13) + "..." }
    val badge = when (motionBadge) {
        "MOVING" -> "[M] "
        "STATIC" -> "[S] "
        else -> ""
    }
    val secondarySegment = shortSecondary?.let { " • $it" }.orEmpty()
    return "$badge$sourceLabel • $shortId$secondarySegment"
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
private val deviceDotMarkerIconCache = mutableMapOf<String, BitmapDescriptor>()
private val aircraftMarkerIconCache = mutableMapOf<String, BitmapDescriptor>()
private val clusterMarkerIconCache = mutableMapOf<String, BitmapDescriptor>()
private val noFlyZoneMarkerIconCache = mutableMapOf<String, BitmapDescriptor>()

private fun noFlyZoneMarkerIcon(selected: Boolean, compact: Boolean): BitmapDescriptor {
    val key = "warn|$selected|$compact"
    noFlyZoneMarkerIconCache[key]?.let { return it }

    val size = if (compact) 24 else 30
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val triangleColor = if (selected) Color(0xFFFFA000) else Color(0xFFFDD835)
    val borderColor = if (selected) Color(0xFFD32F2F) else Color(0xFFB71C1C)
    val textColor = Color(0xFF2A0808)

    val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = triangleColor.toArgb()
    }
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = if (compact) 1.6f else 2.0f
        color = borderColor.toArgb()
    }
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = textColor.toArgb()
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = if (compact) 14f else 17f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    }

    val pad = if (compact) 2.5f else 3.0f
    val path = android.graphics.Path().apply {
        moveTo(size / 2f, pad)
        lineTo(size - pad, size - pad)
        lineTo(pad, size - pad)
        close()
    }

    canvas.drawPath(path, fillPaint)
    canvas.drawPath(path, strokePaint)

    val textY = (size * 0.67f) - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText("!", size / 2f, textY, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap).also {
        noFlyZoneMarkerIconCache[key] = it
    }
}

private fun mapClusterRangeLabel(level: Int): String = when (level) {
    1 -> "Tight"
    2 -> "Compact"
    3 -> "Balanced"
    4 -> "Wide"
    5 -> "Very Wide"
    else -> "Balanced"
}

private fun mapNoFlyRenderQualityLabel(level: Int): String = when (level) {
    1 -> "Speed"
    2 -> "Balanced"
    3 -> "Detail"
    else -> "Balanced"
}

private data class NoFlyRenderQualityProfile(
    val maxRenderCount: Int,
    val maxMarkerCount: Int,
    val pointScale: Double
)

private fun noFlyRenderQualityProfile(level: Int): NoFlyRenderQualityProfile = when (level) {
    1 -> NoFlyRenderQualityProfile(
        maxRenderCount = NO_FLY_ZONE_RENDER_COUNT_LOW,
        maxMarkerCount = NO_FLY_ZONE_MARKER_COUNT_LOW,
        pointScale = 0.60
    )
    3 -> NoFlyRenderQualityProfile(
        maxRenderCount = NO_FLY_ZONE_RENDER_COUNT_HIGH,
        maxMarkerCount = NO_FLY_ZONE_MARKER_COUNT_HIGH,
        pointScale = 1.55
    )
    else -> NoFlyRenderQualityProfile(
        maxRenderCount = NO_FLY_ZONE_RENDER_COUNT_BALANCED,
        maxMarkerCount = NO_FLY_ZONE_MARKER_COUNT_BALANCED,
        pointScale = 1.00
    )
}

private fun mapClusterRangeScale(level: Int): Double = when (level) {
    1 -> 0.60
    2 -> 0.80
    3 -> 1.00
    4 -> 1.25
    5 -> 1.55
    else -> 1.00
}

private fun clusterPinsForRender(pins: List<MapPin>, zoom: Float, rangeLevel: Int): List<MapRenderItem> {
    if (pins.size < 8) {
        return pins.map { MapRenderItem.SinglePin(it) }
    }

    val baseCellDegrees = when {
        zoom < 7.0f -> 0.25
        zoom < 9.0f -> 0.12
        zoom < 11.0f -> 0.06
        else -> 0.03
    }
    val cellDegrees = (baseCellDegrees * mapClusterRangeScale(rangeLevel)).coerceAtLeast(0.01)

    val grouped = LinkedHashMap<String, MutableList<MapPin>>()
    pins.forEach { pin ->
        val latKey = kotlin.math.floor(pin.position.latitude / cellDegrees).toInt()
        val lonKey = kotlin.math.floor(pin.position.longitude / cellDegrees).toInt()
        val key = "$latKey:$lonKey"
        grouped.getOrPut(key) { mutableListOf() }.add(pin)
    }

    return grouped.values.map { bucket ->
        if (bucket.size == 1) {
            MapRenderItem.SinglePin(bucket.first())
        } else {
            val lat = bucket.map { it.position.latitude }.average()
            val lon = bucket.map { it.position.longitude }.average()
            val sourceCounts = bucket.groupingBy { it.source }.eachCount()
            val dominantSource = sourceCounts.maxByOrNull { it.value }?.key ?: "UNKNOWN_RF"
            val liveCount = bucket.count { it.isLive }
            val topSourcesSummary = sourceCounts.entries
                .sortedByDescending { it.value }
                .take(3)
                .joinToString(" • ") { (source, count) ->
                    "${markerLegendLabelForSource(source)} $count"
                }
            val summary = "${bucket.size} pins • Live $liveCount • $topSourcesSummary"
            MapRenderItem.Cluster(
                position = LatLng(lat, lon),
                count = bucket.size,
                source = dominantSource,
                summary = summary,
                isLive = liveCount > 0
            )
        }
    }
}

private fun clusterMarkerIcon(cluster: MapRenderItem.Cluster): BitmapDescriptor {
    val sourceColor = markerLegendColorForSource(cluster.source)
    val baseColor = if (cluster.isLive) sourceColor else sourceColor.copy(alpha = 0.55f)
    val bucket = when {
        cluster.count >= 100 -> "100+"
        cluster.count >= 50 -> "50+"
        cluster.count >= 20 -> "20+"
        else -> cluster.count.toString()
    }
    val key = "cluster|${cluster.source}|$bucket|${baseColor.toArgb()}|${cluster.isLive}"
    clusterMarkerIconCache[key]?.let { return it }

    val size = when {
        cluster.count >= 100 -> 54
        cluster.count >= 50 -> 50
        cluster.count >= 20 -> 46
        else -> 42
    }
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = baseColor.toArgb()
    }
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
        color = android.graphics.Color.argb(if (cluster.isLive) 230 else 150, 8, 16, 18)
    }
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = android.graphics.Color.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = if (size >= 50) 15f else 13f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    }

    val center = size / 2f
    val radius = center - 3f
    canvas.drawCircle(center, center, radius, fillPaint)
    canvas.drawCircle(center, center, radius, strokePaint)

    val text = bucket
    val textY = center - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(text, center, textY, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap).also {
        clusterMarkerIconCache[key] = it
    }
}

private fun markerIconForPin(
    pin: MapPin,
    useSourceOnlyPinColors: Boolean = false,
    showTrackerFamilyBadge: Boolean = false
): BitmapDescriptor {
    if (pin.source == SourceCatalog.SOURCE_AIRCRAFT) {
        return markerAircraftIconForPin(pin, useSourceOnlyPinColors)
    }
    val glyph = markerGlyphForPin(pin)
    val badgeLabel = if (showTrackerFamilyBadge) {
        pin.trackerFamilyBadge
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { label -> if (label.length <= 12) label else label.take(10).trimEnd() + ".." }
    } else {
        null
    }
    val hasBadge = badgeLabel != null
    val bgColor = markerBackgroundColorForPin(pin, useSourceOnlyPinColors)
    val key = "${pin.source}|${glyph}|${bgColor.toArgb()}|${badgeLabel.orEmpty()}"
    deviceMarkerIconCache[key]?.let { return it }
    val alpha = if (pin.isLive) 255 else 140

    val bodyWidth = when {
        glyph.length > 9 -> 148
        glyph.length > 6 -> 116
        else -> 92
    }
    val badgeWidth = if (hasBadge) {
        ((badgeLabel!!.length * 9) + 24).coerceIn(72, 156)
    } else {
        0
    }
    val width = maxOf(bodyWidth, badgeWidth)
    val height = if (hasBadge) 62 else 42
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val badgeFillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = android.graphics.Color.argb(alpha, 24, 44, 48)
    }
    val badgeTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = android.graphics.Color.argb(alpha, 236, 247, 245)
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = 14f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    }
    val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = bgColor.toArgb()
    }
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
        color = android.graphics.Color.argb(alpha, 16, 33, 34)
    }
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = android.graphics.Color.argb(alpha, 15, 21, 22)
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = if (glyph.length > 9) 14f else if (glyph.length > 6) 16f else 18f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    }

    val topOffset = if (hasBadge) 18f else 0f
    if (hasBadge) {
        val badgeRect = android.graphics.RectF(
            (width - badgeWidth) / 2f,
            2f,
            (width + badgeWidth) / 2f,
            22f
        )
        canvas.drawRoundRect(badgeRect, 10f, 10f, badgeFillPaint)
        val badgeTextY = badgeRect.centerY() - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
        canvas.drawText(badgeLabel!!, badgeRect.centerX(), badgeTextY, badgeTextPaint)
    }

    val rect = android.graphics.RectF(
        2f,
        2f + topOffset,
        width - 2f,
        (height - 10f)
    )
    canvas.drawRoundRect(rect, 18f, 18f, fillPaint)
    canvas.drawRoundRect(rect, 18f, 18f, strokePaint)

    val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(glyph, rect.centerX(), textY, textPaint)

    val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
    deviceMarkerIconCache[key] = descriptor
    return descriptor
}

private fun markerAircraftIconForPin(pin: MapPin, useSourceOnlyPinColors: Boolean = false): BitmapDescriptor {
    return markerAircraftIconForPin(pin, useSourceOnlyPinColors, compact = false)
}

private fun markerAircraftIconForPin(
    pin: MapPin,
    useSourceOnlyPinColors: Boolean = false,
    compact: Boolean
): BitmapDescriptor {
    val aircraftIconType = pin.aircraftIconType?.trim()?.lowercase(Locale.US).orEmpty()
    val isHelicopter = aircraftIconType.contains("heli")
    val bgColor = markerBackgroundColorForPin(pin, useSourceOnlyPinColors)
    val alpha = if (pin.isLive) 255 else 150
    val key = "aircraft|${if (isHelicopter) "heli" else "plane"}|${if (compact) "compact" else "regular"}|${bgColor.toArgb()}|$alpha"
    aircraftMarkerIconCache[key]?.let { return it }

    val width = if (compact) 28 else 34
    val height = if (compact) 28 else 34
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val planePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL_AND_STROKE
        strokeWidth = if (compact) 1.1f else 1.3f
        color = android.graphics.Color.argb(alpha, bgColor.red.times(255f).toInt(), bgColor.green.times(255f).toInt(), bgColor.blue.times(255f).toInt())
    }

    val outlinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = if (compact) 0.9f else 1.1f
        color = android.graphics.Color.argb(alpha, 8, 16, 18)
    }

    val cx = width / 2f
    val cy = height / 2f
    if (isHelicopter) {
        val bodyWidth = if (compact) 7.5f else 9.0f
        val bodyHeight = if (compact) 10.5f else 12.5f
        val bodyRect = android.graphics.RectF(
            cx - bodyWidth / 2f,
            cy - bodyHeight / 2f,
            cx + bodyWidth / 2f,
            cy + bodyHeight / 2f
        )
        canvas.drawRoundRect(bodyRect, 3.2f, 3.2f, planePaint)
        canvas.drawRoundRect(bodyRect, 3.2f, 3.2f, outlinePaint)

        val tailLength = if (compact) 9.5f else 12.0f
        val tailWidth = if (compact) 1.5f else 1.8f
        val tailRect = android.graphics.RectF(
            cx - tailWidth / 2f,
            bodyRect.bottom - 0.5f,
            cx + tailWidth / 2f,
            bodyRect.bottom + tailLength
        )
        canvas.drawRoundRect(tailRect, 1.2f, 1.2f, planePaint)
        canvas.drawRoundRect(tailRect, 1.2f, 1.2f, outlinePaint)

        val mastTopY = bodyRect.top - (if (compact) 2.0f else 2.4f)
        val rotorHalf = if (compact) 10.8f else 13.2f
        canvas.drawLine(cx - rotorHalf, mastTopY, cx + rotorHalf, mastTopY, outlinePaint)
        canvas.drawLine(cx, mastTopY - 1.5f, cx, mastTopY + 1.5f, outlinePaint)

        val skidY = bodyRect.bottom + (if (compact) 2.2f else 2.8f)
        val skidHalf = if (compact) 7.0f else 8.2f
        canvas.drawLine(cx - skidHalf, skidY, cx + skidHalf, skidY, outlinePaint)
    } else {
        val bodyHalf = if (compact) 7f else 8.5f
        val wingHalf = if (compact) 11f else 13f
        val nose = if (compact) 11.5f else 13.5f
        val tail = if (compact) 7.5f else 9f
        val planePath = android.graphics.Path().apply {
            moveTo(cx, cy - nose)
            lineTo(cx + (if (compact) 2.2f else 2.6f), cy - bodyHalf)
            lineTo(cx + wingHalf, cy - (if (compact) 1.0f else 1.2f))
            lineTo(cx + wingHalf, cy + (if (compact) 1.6f else 2.0f))
            lineTo(cx + (if (compact) 2.8f else 3.2f), cy + (if (compact) 2.5f else 3.0f))
            lineTo(cx + (if (compact) 1.4f else 1.7f), cy + tail)
            lineTo(cx - (if (compact) 1.4f else 1.7f), cy + tail)
            lineTo(cx - (if (compact) 2.8f else 3.2f), cy + (if (compact) 2.5f else 3.0f))
            lineTo(cx - wingHalf, cy + (if (compact) 1.6f else 2.0f))
            lineTo(cx - wingHalf, cy - (if (compact) 1.0f else 1.2f))
            lineTo(cx - (if (compact) 2.2f else 2.6f), cy - bodyHalf)
            close()
        }
        canvas.drawPath(planePath, planePaint)
        canvas.drawPath(planePath, outlinePaint)

        val tailPath = android.graphics.Path().apply {
            moveTo(cx - (if (compact) 1.3f else 1.6f), cy + (if (compact) 7f else 8.2f))
            lineTo(cx - (if (compact) 4.2f else 5.0f), cy + (if (compact) 10.6f else 12.0f))
            lineTo(cx + (if (compact) 4.2f else 5.0f), cy + (if (compact) 10.6f else 12.0f))
            lineTo(cx + (if (compact) 1.3f else 1.6f), cy + (if (compact) 7f else 8.2f))
            close()
        }
        canvas.drawPath(tailPath, planePaint)
        canvas.drawPath(tailPath, outlinePaint)
    }

    val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
    aircraftMarkerIconCache[key] = descriptor
    return descriptor
}

private fun markerDotIconForPin(pin: MapPin, useSourceOnlyPinColors: Boolean = false): BitmapDescriptor {
    if (pin.source == SourceCatalog.SOURCE_AIRCRAFT) {
        return markerAircraftIconForPin(pin, useSourceOnlyPinColors, compact = true)
    }
    val dotColor = markerBackgroundColorForPin(pin, useSourceOnlyPinColors)
    val key = "dot|${pin.source}|${pin.motionBadge.orEmpty()}|${dotColor.toArgb()}"
    deviceDotMarkerIconCache[key]?.let { return it }
    val strokeAlpha = if (pin.isLive) 220 else 130

    val size = 32
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = dotColor.toArgb()
    }
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 4f
        color = android.graphics.Color.argb(strokeAlpha, 13, 24, 24)
    }

    val center = size / 2f
    val radius = 9.2f
    canvas.drawCircle(center, center, radius, fillPaint)
    canvas.drawCircle(center, center, radius, strokePaint)

    val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
    deviceDotMarkerIconCache[key] = descriptor
    return descriptor
}

private fun markerGlyphForPin(pin: MapPin): String {
    val raw = pin.markerGlyphOverride
        ?.trim()
        ?.takeIf { value -> value.isNotBlank() }
        ?: deviceGlyphForSource(pin.source)
    val normalized = raw.replace(Regex("\\s+"), " ")
    return if (normalized.length <= 14) normalized else normalized.take(11).trimEnd() + "..."
}

private fun trackerFamilyBadgeLabel(family: String?): String {
    return when (family?.trim()?.lowercase(Locale.US)) {
        "apple_find_my" -> "APPLE"
        "google_find_my" -> "GOOGLE"
        "tile" -> "TILE"
        "unknown_tracker" -> "UNKNOWN"
        "non_tracker_or_unknown" -> "UNKNOWN"
        null, "" -> "UNKNOWN"
        else -> family
            .split('_')
            .joinToString(" ") { token -> token.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.US) else c.toString() } }
            .uppercase(Locale.US)
    }
}

private fun markerBackgroundColorForPin(pin: MapPin, useSourceOnlyPinColors: Boolean): Color {
    val baseColor = if (!useSourceOnlyPinColors && pin.motionBadge == "MOVING") {
        Color(0xFF43A047)
    } else {
        markerLegendColorForSource(pin.source)
    }

    return if (pin.isLive) baseColor else baseColor.copy(alpha = 0.45f)
}

private fun formatMapPinAge(timestampEpochMs: Long, nowEpochMs: Long = System.currentTimeMillis()): String {
    val ageSeconds = ((nowEpochMs - timestampEpochMs).coerceAtLeast(0L)) / 1000L
    return when {
        ageSeconds < 60L -> "${ageSeconds}s"
        ageSeconds < 3600L -> "${ageSeconds / 60L}m"
        else -> "${ageSeconds / 3600L}h"
    }
}

private fun deviceGlyphForSource(source: String): String =
    SOURCE_TYPE_UI_META_BY_SOURCE[source]?.glyph ?: "RF"

private fun formatLiveMapIntervalLabel(seconds: Long): String = when (seconds) {
    1L -> "1s"
    3L -> "3s"
    5L -> "5s"
    15L -> "15s"
    30L -> "30s"
    60L -> "1 minute"
    300L -> "5 minutes"
    900L -> "15 minutes"
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

private fun formatSourceTypeLabel(sourceType: String): String {
    return SOURCE_TYPE_UI_META_BY_SCAN_TYPE[sourceType]?.settingsLabel
        ?: EXTRA_SCAN_SOURCE_LABELS[sourceType]
        ?: sourceType.replace('_', ' ').uppercase()
}

private fun scanTypeKeyForSourceName(sourceName: String): String {
    return SOURCE_TYPE_UI_META_BY_SOURCE[sourceName]?.scanType ?: sourceName.lowercase(Locale.US)
}

private fun mapSourceIntervalSeconds(
    sourceType: String,
    sourceScanIntervals: Map<String, Long>
): Long {
    val canonicalType = canonicalScanSourceType(sourceType)
    return sourceScanIntervals[canonicalType]
        ?: sourceScanIntervals[sourceType]
        ?: when (canonicalType) {
            SourceCatalog.KEY_AIRCRAFT -> ScanSettings.DEFAULT_AIRCRAFT_SOURCE_SCAN_INTERVAL_SECONDS
            SourceCatalog.KEY_CAMERA -> ScanSettings.DEFAULT_CAMERA_SOURCE_SCAN_INTERVAL_SECONDS
            else -> ScanSettings.DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS
        }
}

private fun canonicalScanSourceType(sourceType: String): String = when (sourceType) {
    SourceCatalog.KEY_WIFI_DIRECT -> SourceCatalog.KEY_WIFI
    SourceCatalog.KEY_BT_CLASSIC,
    SourceCatalog.KEY_REMOTE_ID -> SourceCatalog.KEY_BLE
    else -> sourceType
}

private fun effectiveRawObservationEpochMsForSource(
    sourceType: String,
    sourceLastRawObservationEpochs: Map<String, Long>
): Long {
    val directEpoch = sourceLastRawObservationEpochs[sourceType] ?: 0L
    val canonicalType = canonicalScanSourceType(sourceType)
    val canonicalEpoch = if (canonicalType != sourceType) {
        sourceLastRawObservationEpochs[canonicalType] ?: 0L
    } else {
        0L
    }
    return maxOf(directEpoch, canonicalEpoch)
}

private fun mapLiveWindowMsForSource(
    sourceType: String,
    sourceScanIntervals: Map<String, Long>,
    liveMapUpdateIntervalSeconds: Long
): Long {
    val intervalMs = mapSourceIntervalSeconds(sourceType, sourceScanIntervals).coerceAtLeast(1L) * 1000L
    val mapTickMs = liveMapUpdateIntervalSeconds.coerceAtLeast(1L) * 1000L
    val minimumMs = when (sourceType) {
        SourceCatalog.KEY_AIRCRAFT -> 300_000L
        // NFC is event-driven and does not scan continuously, so keep a longer live grace window.
        SourceCatalog.KEY_NFC -> 300_000L
        else -> 15_000L
    }
    return maxOf(minimumMs, intervalMs * 2L, mapTickMs * 2L)
}

private fun mapRecentWindowMsForSource(
    sourceType: String,
    sourceScanIntervals: Map<String, Long>,
    liveMapUpdateIntervalSeconds: Long
): Long {
    val liveWindowMs = mapLiveWindowMsForSource(sourceType, sourceScanIntervals, liveMapUpdateIntervalSeconds)
    val minimumRecentMs = if (sourceType == SourceCatalog.KEY_AIRCRAFT) 600_000L else 120_000L
    return maxOf(minimumRecentMs, liveWindowMs * 2L)
}

private fun isMapPinLiveNow(
    pin: MapPin,
    nowEpochMs: Long,
    sourceScanIntervals: Map<String, Long>,
    liveMapUpdateIntervalSeconds: Long
): Boolean {
    val sourceType = scanTypeKeyForSourceName(pin.source)
    val liveWindowMs = mapLiveWindowMsForSource(
        sourceType = sourceType,
        sourceScanIntervals = sourceScanIntervals,
        liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds
    )
    return pin.timestampEpochMs >= (nowEpochMs - liveWindowMs)
}

private fun suggestSafeIntervalSeconds(referenceDurationMs: Long): Long {
    val targetMs = (referenceDurationMs.coerceAtLeast(0L) * 1.25).toLong().coerceAtLeast(1000L)
    val targetSeconds = (targetMs + 999L) / 1000L
    return targetSeconds.coerceIn(
        ScanSettings.MIN_SOURCE_SCAN_INTERVAL_SECONDS,
        ScanSettings.MAX_SOURCE_SCAN_INTERVAL_SECONDS
    )
}

private fun tickerFlow(periodMs: Long): Flow<Long> = flow {
    val safePeriodMs = periodMs.coerceAtLeast(250L)
    emit(System.currentTimeMillis())
    while (true) {
        delay(safePeriodMs)
        emit(System.currentTimeMillis())
    }
}.conflate()

private fun enabledSourceTypes(sensorGateSettings: SensorGateSettings): List<String> = buildList {
    if (sensorGateSettings.wifiEnabled) {
        add(SourceCatalog.KEY_WIFI)
        add(SourceCatalog.KEY_WIFI_DIRECT)
    }
    if (sensorGateSettings.bluetoothLeEnabled) {
        add(SourceCatalog.KEY_BLE)
        add(SourceCatalog.KEY_BT_CLASSIC)
        add(SourceCatalog.KEY_REMOTE_ID)
    }
    if (sensorGateSettings.cellularEnabled) add(SourceCatalog.KEY_CELLULAR)
    if (sensorGateSettings.sdrEnabled) add(SourceCatalog.KEY_CAMERA)
    if (sensorGateSettings.aviationAdsbEnabled || sensorGateSettings.aviationPublicEnabled) add(SourceCatalog.KEY_AIRCRAFT)
    if (sensorGateSettings.sdrEnabled) add(SourceCatalog.KEY_SDR)
    if (sensorGateSettings.directAcousticEnabled) add(SourceCatalog.KEY_ACOUSTIC)
    if (sensorGateSettings.directMagneticEnabled) add(SourceCatalog.KEY_MAGNETIC)
}

private fun enabledIntervalSourceTypes(sensorGateSettings: SensorGateSettings): List<String> = buildList {
    if (sensorGateSettings.wifiEnabled) add(SourceCatalog.KEY_WIFI)
    if (sensorGateSettings.bluetoothLeEnabled) add(SourceCatalog.KEY_BLE)
    if (sensorGateSettings.cellularEnabled) add(SourceCatalog.KEY_CELLULAR)
    if (sensorGateSettings.sdrEnabled) add(SourceCatalog.KEY_CAMERA)
    if (sensorGateSettings.aviationAdsbEnabled || sensorGateSettings.aviationPublicEnabled) add(SourceCatalog.KEY_AIRCRAFT)
    if (sensorGateSettings.sdrEnabled) add(SourceCatalog.KEY_SDR)
    if (sensorGateSettings.directAcousticEnabled) add(SourceCatalog.KEY_ACOUSTIC)
    if (sensorGateSettings.directMagneticEnabled) add(SourceCatalog.KEY_MAGNETIC)
}

private fun requiresAllEncountersRoute(route: String): Boolean {
    return route == DETECTION_ROUTE ||
        route == LOGS_ROUTE ||
        route == LOGS_ENCOUNTERS_ROUTE ||
        route == DEVICES_ENCOUNTERS_ROUTE ||
        route == APPROACH_ALERT_MAP_ROUTE ||
        route == MOVING_DEVICE_PATH_ROUTE ||
    route == NO_FLY_INCIDENT_PATH_ROUTE ||
        route == DEVICE_DETAIL_ROUTE ||
        route == ENCOUNTER_DETAIL_ROUTE
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
    "manual-camera" -> "Manual camera interval change"
    "manual-aircraft" -> "Manual aircraft interval change"
    "manual-wifi_direct" -> "Manual Wi-Fi Direct interval change"
    "manual-sdr" -> "Manual SDR interval change"
    "manual-acoustic" -> "Manual acoustic interval change"
    "manual-magnetic" -> "Manual magnetometer interval change"
    "auto-overrun-wifi" -> "Auto-adjust Wi-Fi overrun protection"
    "auto-overrun-wifi_direct" -> "Auto-adjust Wi-Fi Direct overrun protection"
    "auto-overrun-ble" -> "Auto-adjust Bluetooth LE overrun protection"
    "auto-overrun-bt_classic" -> "Auto-adjust Bluetooth Classic overrun protection"
    "auto-overrun-cellular" -> "Auto-adjust Cellular overrun protection"
    "auto-overrun-remote_id" -> "Auto-adjust Remote ID overrun protection"
    "auto-overrun-camera" -> "Auto-adjust camera overrun protection"
    "auto-overrun-aircraft" -> "Auto-adjust aircraft overrun protection"
    "auto-overrun-sdr" -> "Auto-adjust SDR overrun protection"
    "auto-overrun-acoustic" -> "Auto-adjust acoustic overrun protection"
    "auto-overrun-magnetic" -> "Auto-adjust magnetometer overrun protection"
    "auto-stable-wifi" -> "Auto-adjust Wi-Fi stable downshift"
    "auto-stable-wifi_direct" -> "Auto-adjust Wi-Fi Direct stable downshift"
    "auto-stable-ble" -> "Auto-adjust Bluetooth LE stable downshift"
    "auto-stable-bt_classic" -> "Auto-adjust Bluetooth Classic stable downshift"
    "auto-stable-cellular" -> "Auto-adjust Cellular stable downshift"
    "auto-stable-remote_id" -> "Auto-adjust Remote ID stable downshift"
    "auto-stable-camera" -> "Auto-adjust camera stable downshift"
    "auto-stable-aircraft" -> "Auto-adjust aircraft stable downshift"
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
    SOURCE_TYPE_UI_META_BY_SOURCE[source]?.supportsSecondaryId == true

private fun secondaryIdLabel(source: String): String =
    SOURCE_TYPE_UI_META_BY_SOURCE[source]?.secondaryIdLabel ?: "Secondary ID"

private fun listSourceLabel(source: String, secondaryId: String?): String {
    val meta = SOURCE_TYPE_UI_META_BY_SOURCE[source]
    if (source == SourceCatalog.SOURCE_CELL) {
        val normalized = normalizeCellSecondaryLabel(secondaryId)
        if (!normalized.isNullOrBlank()) {
            return if (normalized.contains("Cell", ignoreCase = true)) {
                normalized
            } else {
                "${meta?.listLabel ?: source} ($normalized)"
            }
        }
        return "Cell"
    }
    return meta?.listLabel ?: source
}

private fun normalizeCellSecondaryLabel(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    if (trimmed.contains("Cell", ignoreCase = true)) return trimmed

    val upper = trimmed.uppercase(Locale.US)
    return when (upper) {
        "NR", "LTE", "WCDMA", "GSM", "CDMA" -> "Cell ($upper tower)"
        else -> trimmed
    }
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
            "Scan Timestamp (us)" to "timestampMicros",
            "Mode" to "mode",
            "Access Point Count" to "apCount",
            "Unique BSSID Count" to "uniqueBssidCount",
            "Hidden SSID Count" to "hiddenSsidCount",
            "Strongest RSSI (dBm)" to "strongestRssiDbm",
            "Median RSSI (dBm)" to "medianRssiDbm",
            "2.4 GHz AP Count" to "band24Count",
            "5 GHz AP Count" to "band5Count",
            "6 GHz AP Count" to "band6Count",
            "Top Role Hints" to "topRoleHints",
            "Newest Scan Timestamp (us)" to "timestampMicrosMax"
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
            "Manufacturer Company IDs" to "manufacturerCompanyIds",
            "Service Data Count" to "serviceDataSize",
            "Remote ID Candidate" to "remoteIdCandidate",
            "Tracker Family" to "trackerFamilyHint",
            "Tracker Family Confidence" to "trackerFamilyConfidence",
            "Tracker Likely" to "trackerLikely",
            "Tracker Evidence" to "trackerEvidence"
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

private fun readAircraftFields(rawPayloadJson: String): List<Pair<String, String>> {
    val payload = runCatching { JSONObject(rawPayloadJson) }.getOrNull() ?: return emptyList()
    val fields = mutableListOf<Pair<String, String>>()

    fun readFirst(keyCandidates: List<String>): String? {
        keyCandidates.forEach { key ->
            val value = payload.opt(key)
            if (value != null && value != JSONObject.NULL) {
                val str = value.toString().trim()
                if (str.isNotBlank()) return str
            }
        }
        return null
    }

    fun readFirstDouble(keyCandidates: List<String>): Double? {
        keyCandidates.forEach { key ->
            payload.optDoubleOrNull(key)?.let { return it }
            payload.optString(key, "").trim().toDoubleOrNull()?.let { return it }
        }
        return null
    }

    fun readFirstLong(keyCandidates: List<String>): Long? {
        keyCandidates.forEach { key ->
            if (payload.has(key) && !payload.isNull(key)) {
                payload.optLong(key, Long.MIN_VALUE)
                    .takeIf { it != Long.MIN_VALUE }
                    ?.let { return it }
            }
            payload.optString(key, "").trim().toLongOrNull()?.let { return it }
        }
        return null
    }

    fun readFirstBoolean(keyCandidates: List<String>): Boolean? {
        keyCandidates.forEach { key ->
            if (!payload.has(key) || payload.isNull(key)) return@forEach
            return when (val value = payload.opt(key)) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value.equals("true", ignoreCase = true) || value == "1"
                else -> null
            }
        }
        return null
    }

    readFirst(listOf("icao24", "hex", "id", "primaryId"))?.let { fields += "ICAO24" to it }
    readFirst(listOf("callsign", "flight", "registration", "label"))?.let { fields += "Callsign" to it }
    readFirst(listOf("registration", "tail", "tailNumber"))?.let { fields += "Registration" to it }
    readFirst(listOf("aircraftTypeHint", "aircraftType", "type", "category"))?.let { fields += "Aircraft Type" to it }
    readFirst(listOf("originCountry", "country"))?.let { fields += "Origin Country" to it }
    readFirstDouble(listOf("altitudeMeters", "baroAltitudeMeters", "geoAltitudeMeters", "alt_baro", "alt_geom"))
        ?.let { altitudeMeters ->
            val altitudeFeet = altitudeMeters * 3.28084
            fields += "Altitude" to String.format(Locale.US, "%.0f m (%.0f ft)", altitudeMeters, altitudeFeet)
        }
    readFirstDouble(listOf("speedMetersPerSecond", "speed", "gs"))?.let { speedMps ->
        val speedKnots = speedMps * 1.94384
        fields += "Speed" to String.format(Locale.US, "%.1f m/s (%.0f kt)", speedMps, speedKnots)
    }
    readFirstDouble(listOf("headingDegrees", "track", "trueTrack"))?.let { heading ->
        val normalized = ((heading % 360.0) + 360.0) % 360.0
        fields += "Heading" to String.format(
            Locale.US,
            "%.0f deg (%s)",
            normalized,
            formatHeadingCardinal(normalized)
        )
    }
    readFirstDouble(listOf("verticalRateMps", "verticalSpeed", "verticalRate"))?.let { verticalRateMps ->
        val verticalRateFpm = verticalRateMps * 196.8504
        fields += "Vertical Rate" to String.format(Locale.US, "%.1f m/s (%.0f fpm)", verticalRateMps, verticalRateFpm)
    }
    readFirstBoolean(listOf("onGround", "ground"))?.let { onGround ->
        fields += "On Ground" to if (onGround) "Yes" else "No"
    }
    readFirst(listOf("squawk", "transponderCode"))?.let { fields += "Squawk" to it }
    readFirstLong(listOf("lastContactEpochMs", "lastContactSeconds"))?.let { lastContactRaw ->
        val epochMs = if (lastContactRaw < 3_000_000_000L) lastContactRaw * 1000L else lastContactRaw
        fields += "Last Contact" to formatEpoch(epochMs)
    }
    readFirst(listOf("provider"))?.let { fields += "Provider" to it }

    return if (fields.isNotEmpty()) fields else readGenericPayloadFields(rawPayloadJson)
}

private fun readAircraftHeadingDegrees(rawPayloadJson: String): Double? {
    val payload = runCatching { JSONObject(rawPayloadJson) }.getOrNull() ?: return null
    val rawHeading = payload.optDoubleOrNull("headingDegrees")
        ?: payload.optDoubleOrNull("track")
        ?: payload.optDoubleOrNull("trueTrack")
        ?: return null
    if (!rawHeading.isFinite()) return null
    return ((rawHeading % 360.0) + 360.0) % 360.0
}

private fun readAircraftIconType(rawPayloadJson: String): String {
    val payload = runCatching { JSONObject(rawPayloadJson) }.getOrNull() ?: return "plane"
    val hint = payload.optString("aircraftTypeHint", "")
        .ifBlank { payload.optString("aircraftType", "") }
        .ifBlank { payload.optString("type", "") }
        .ifBlank { payload.optString("category", "") }
        .trim()
        .lowercase(Locale.US)
    return if (hint.contains("heli")) "helicopter" else "plane"
}

private data class AircraftVisualHints(
    val iconType: String,
    val headingDegrees: Double?
)

private fun readAircraftVisualHints(rawPayloadJson: String): AircraftVisualHints {
    val payload = runCatching { JSONObject(rawPayloadJson) }.getOrNull()
        ?: return AircraftVisualHints(iconType = "plane", headingDegrees = null)
    val hint = payload.optString("aircraftTypeHint", "")
        .ifBlank { payload.optString("aircraftType", "") }
        .ifBlank { payload.optString("type", "") }
        .ifBlank { payload.optString("category", "") }
        .trim()
        .lowercase(Locale.US)
    val iconType = if (hint.contains("heli")) "helicopter" else "plane"
    val rawHeading = payload.optDoubleOrNull("headingDegrees")
        ?: payload.optDoubleOrNull("track")
        ?: payload.optDoubleOrNull("trueTrack")
    val heading = rawHeading?.takeIf { it.isFinite() }?.let { ((it % 360.0) + 360.0) % 360.0 }
    return AircraftVisualHints(iconType = iconType, headingDegrees = heading)
}

private fun estimateHeadingFromEncounters(previous: Encounter?, latest: Encounter): Double? {
    val fromLat = previous?.lat ?: return null
    val fromLon = previous.lon ?: return null
    val toLat = latest.lat ?: return null
    val toLon = latest.lon ?: return null

    if (fromLat == toLat && fromLon == toLon) return null

    val lat1 = Math.toRadians(fromLat)
    val lat2 = Math.toRadians(toLat)
    val dLon = Math.toRadians(toLon - fromLon)
    val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2)
    val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
        kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLon)
    val bearing = Math.toDegrees(kotlin.math.atan2(y, x))
    return ((bearing % 360.0) + 360.0) % 360.0
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
    val remoteIdAssessment = assessRemoteIdEntityType(normalized)

    val semantic = mutableListOf<Pair<String, String>>()
    semantic += "Entity Assessment" to remoteIdAssessment
    semantic += "UAS ID" to normalized.primaryId
    normalized.secondaryId?.let { semantic += "Operator ID" to it }
    decoded?.let {
        semantic += "Message Type" to it.messageType
        semantic += "Parse Confidence" to it.parseConfidence.name
        it.droneLat?.let { value ->
            semantic += if (remoteIdAssessment.startsWith("Likely Drone")) {
                "Drone Lat" to formatCoordinate(value)
            } else {
                "Broadcast Lat" to formatCoordinate(value)
            }
        }
        it.droneLon?.let { value ->
            semantic += if (remoteIdAssessment.startsWith("Likely Drone")) {
                "Drone Lon" to formatCoordinate(value)
            } else {
                "Broadcast Lon" to formatCoordinate(value)
            }
        }
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

private fun readCameraFields(rawPayloadJson: String): List<Pair<String, String>> {
    val payload = runCatching { JSONObject(rawPayloadJson) }.getOrNull() ?: return emptyList()
    val fields = mutableListOf<Pair<String, String>>()

    payload.optString("cameraType", "").trim().takeIf { it.isNotBlank() }?.let {
        fields += "Camera Type" to it.replace('_', ' ').uppercase(Locale.US)
    }
    payload.optString("evidenceType", "").trim().takeIf { it.isNotBlank() }?.let {
        fields += "Evidence" to it.replace('_', ' ')
    }
    payload.optString("provider", "").trim().takeIf { it.isNotBlank() }?.let {
        fields += "Provider" to it
    }
    payload.optString("signalClass", "").trim().takeIf { it.isNotBlank() }?.let {
        fields += "Signal Class" to it
    }
    payload.opt("frequencyMhz")?.toString()?.takeIf { it.isNotBlank() }?.let {
        fields += "Frequency MHz" to it
    }
    payload.opt("rssiDbm")?.toString()?.takeIf { it.isNotBlank() }?.let {
        fields += "RSSI dBm" to it
    }
    payload.optString("osmType", "").trim().takeIf { it.isNotBlank() }?.let {
        fields += "OSM Type" to it
    }
    payload.opt("osmId")?.toString()?.takeIf { it.isNotBlank() }?.let {
        fields += "OSM ID" to it
    }

    return if (fields.isNotEmpty()) fields else readGenericPayloadFields(rawPayloadJson)
}

private fun assessRemoteIdEntityType(normalized: dev.argus.tracker.sensing.remoteid.RemoteIdNormalizedPayload): String {
    val decoded = normalized.decoded ?: return "Unverified Remote ID-like broadcast"
    val hasPosition = decoded.droneLat != null && decoded.droneLon != null
    val hasKinematics = decoded.altitudeMeters != null || decoded.speedMetersPerSecond != null || decoded.headingDegrees != null
    val hasUasId = decoded.uasId?.isNotBlank() == true || !normalized.primaryId.startsWith("remote-id-unknown")
    val message = decoded.messageType.lowercase(Locale.US)
    val remoteIdMessageMatch = message.contains("basic") || message.contains("location") || message.contains("operator") || message.contains("self")

    val likelyDrone =
        decoded.parseConfidence == RemoteIdParseConfidence.HIGH ||
            (hasPosition && (hasUasId || remoteIdMessageMatch)) ||
            (hasUasId && hasKinematics && decoded.parseConfidence != RemoteIdParseConfidence.NONE)

    return if (likelyDrone) {
        "Likely Drone (Remote ID evidence)"
    } else {
        "Unverified Remote ID-like broadcast"
    }
}

private fun formatCoordinate(value: Double): String =
    String.format(Locale.US, "%.6f", value)

private fun sourceSpecificDetails(encounter: Encounter): Pair<String, List<Pair<String, String>>> =
    when (encounter.source) {
        EncounterSource.ARGUS_MESH -> "Argus Mesh Peer Details" to readGenericPayloadFields(encounter.rawPayloadJson)
        EncounterSource.WIFI -> "Wi-Fi Access Point Details" to readWifiAccessPointFields(encounter.rawPayloadJson)
        EncounterSource.WIFI_SWEEP -> "Wi-Fi Sweep Aggregate Details" to readWifiAccessPointFields(encounter.rawPayloadJson)
        EncounterSource.WIFI_DIRECT -> "Wi-Fi Direct Peer Details" to readGenericPayloadFields(encounter.rawPayloadJson)
        EncounterSource.BLUETOOTH_LE -> "Bluetooth LE Device Details" to readBleDeviceFields(encounter.rawPayloadJson)
        EncounterSource.BLUETOOTH_LE_SWEEP -> "Bluetooth LE Sweep Aggregate Details" to readGenericPayloadFields(encounter.rawPayloadJson)
        EncounterSource.BLUETOOTH_CLASSIC -> "Bluetooth Classic Device Details" to readGenericPayloadFields(encounter.rawPayloadJson)
        EncounterSource.NFC -> "NFC Tag Details" to readGenericPayloadFields(encounter.rawPayloadJson)
        EncounterSource.CELL -> "Cell Tower Details" to readCellTowerFields(encounter.rawPayloadJson)
        EncounterSource.REMOTE_ID -> "Remote ID Details" to readRemoteIdFields(encounter.rawPayloadJson)
        EncounterSource.CAMERA -> "Road Camera Details" to readCameraFields(encounter.rawPayloadJson)
        EncounterSource.AIRCRAFT -> "Aircraft Track Details" to readAircraftFields(encounter.rawPayloadJson)
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
            val nciRaw = payload.optString("nciRaw", "").trim()
            if (nciRaw.isNotBlank()) {
                fields += "NR Cell ID (NCI)" to nciRaw
            } else {
                addIfPresent("NR Cell ID (NCI)", "nci")
            }
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

private fun resolveTrustedRemoteIdLocation(encounters: List<Encounter>): RemoteIdResolvedLocation? {
    if (encounters.isEmpty()) return null

    val latestBroadcast = latestRemoteIdBroadcastPoint(encounters)
    val latestObserved = encounters
        .asSequence()
        .filter { it.source == EncounterSource.REMOTE_ID }
        .filter { isValidLatLon(it.lat, it.lon) }
        .maxByOrNull { it.timestampEpochMs }

    if (latestBroadcast != null) {
        return RemoteIdResolvedLocation(
            lat = latestBroadcast.lat,
            lon = latestBroadcast.lon,
            timestampEpochMs = latestBroadcast.timestampEpochMs,
            method = "Remote ID broadcast location"
        )
    }

    if (latestObserved != null) {
        return RemoteIdResolvedLocation(
            lat = latestObserved.lat!!,
            lon = latestObserved.lon!!,
            timestampEpochMs = latestObserved.timestampEpochMs,
            method = "Observed encounter location fallback"
        )
    }

    return null
}

private fun isLikelyRemoteIdLocationSpoofed(encounters: List<Encounter>): Boolean {
    if (encounters.isEmpty()) return false
    val latestBroadcast = latestRemoteIdBroadcastPoint(encounters) ?: return false
    val latestObserved = encounters
        .asSequence()
        .filter { it.source == EncounterSource.REMOTE_ID }
        .filter { isValidLatLon(it.lat, it.lon) }
        .maxByOrNull { it.timestampEpochMs }
        ?: return false

    val offsetMeters = distanceFromLocationMeters(
        fromLat = latestObserved.lat!!,
        fromLon = latestObserved.lon!!,
        toLat = latestBroadcast.lat,
        toLon = latestBroadcast.lon
    ) ?: return false
    return offsetMeters > REMOTE_ID_BROADCAST_MAX_OFFSET_METERS
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
            val remoteIdLocation = resolveTrustedRemoteIdLocation(encounters)
            if (remoteIdLocation != null) {
                ResolvedDeviceLocation(
                    lat = remoteIdLocation.lat,
                    lon = remoteIdLocation.lon,
                    method = remoteIdLocation.method,
                    approximateRangeMeters = null,
                    resolvedFromTimestampEpochMs = remoteIdLocation.timestampEpochMs
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

private fun isApproachEligibleSource(source: EncounterSource): Boolean {
    return source != EncounterSource.CAMERA &&
        source != EncounterSource.WIFI_SWEEP &&
    source != EncounterSource.BLUETOOTH_LE_SWEEP
}

private fun isApproachEligibleSource(source: String): Boolean {
    return source != EncounterSource.CAMERA.name &&
        source != EncounterSource.WIFI_SWEEP.name &&
    source != EncounterSource.BLUETOOTH_LE_SWEEP.name
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

private data class BleTrackerProfile(
    val family: String,
    val confidence: Double,
    val likely: Boolean
)

private fun inferBleTrackerProfile(encounters: List<Encounter>): BleTrackerProfile? {
    return encounters
        .asSequence()
        .filter { it.source == EncounterSource.BLUETOOTH_LE }
        .mapNotNull { encounter ->
            val payload = parseEncounterPayload(encounter) ?: return@mapNotNull null
            val family = payload.optString("trackerFamilyHint", "").trim().ifBlank { return@mapNotNull null }
            val confidence = payload.optDouble("trackerFamilyConfidence", Double.NaN)
                .takeIf { it.isFinite() }
                ?.coerceIn(0.0, 1.0)
                ?: 0.0
            val likely = payload.optBoolean("trackerLikely", false)
            BleTrackerProfile(
                family = family,
                confidence = confidence,
                likely = likely
            )
        }
        .sortedByDescending { it.confidence }
        .firstOrNull()
}

private fun formatTrackerFamilyLabel(family: String?): String {
    if (family.isNullOrBlank()) return "Unknown"
    return family
        .split('_')
        .joinToString(" ") { token -> token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() } }
}

private fun analyzeTrackerRisk(
    encounters: List<Encounter>,
    isOwned: Boolean,
    approachSignal: ApproachSignal?,
    homePoint: ScanSettings.HomePoint? = null
): TrackerRiskSignal? {
    if (isOwned) {
        return TrackerRiskSignal(
            level = TrackerRiskLevel.NONE,
            confidence = 1.0,
            uniqueLocationCells = 0,
            spreadMeters = 0.0,
            activeWindowMinutes = 0.0,
            summary = "Marked as owned by user",
            seenAtHome = false,
            seenAwayFromHome = false,
            trackerFamilyHint = null,
            trackerFamilyConfidence = null
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

    var seenAtHome = false
    var seenAwayFromHome = false
    val trackerProfile = inferBleTrackerProfile(ordered)
    if (homePoint != null) {
        val awayThresholdMeters = homePoint.radiusMeters + 120.0
        validLocations.forEach { (lat, lon) ->
            val metersFromHome = distanceFromLocationMeters(homePoint.lat, homePoint.lon, lat, lon) ?: return@forEach
            if (metersFromHome <= homePoint.radiusMeters) {
                seenAtHome = true
            }
            if (metersFromHome >= awayThresholdMeters) {
                seenAwayFromHome = true
            }
        }
    }

    val locationScore = ((uniqueCells - 1).toDouble() / 5.0).coerceIn(0.0, 1.0)
    val spreadScore = (maxSpreadMeters / 1500.0).coerceIn(0.0, 1.0)
    val durationScore = (activeWindowMinutes / 90.0).coerceIn(0.0, 1.0)
    val approachScore = when {
        approachSignal?.isApproaching == true -> approachSignal.confidence.coerceIn(0.0, 1.0)
        else -> 0.0
    }
    val homeTransitionBoost = if (seenAtHome && seenAwayFromHome) 0.12 else 0.0
    val trackerSignatureBoost = if (trackerProfile?.likely == true) {
        0.10 * trackerProfile.confidence
    } else {
        0.0
    }
    val confidence = (
        0.35 * locationScore +
            0.30 * spreadScore +
            0.20 * durationScore +
            0.15 * approachScore +
            homeTransitionBoost +
            trackerSignatureBoost
        ).coerceIn(0.0, 1.0)

    val level = when {
        uniqueCells >= 5 && maxSpreadMeters >= 1200.0 && activeWindowMinutes >= 40.0 &&
            confidence >= if (seenAtHome && seenAwayFromHome) 0.68 else 0.75 -> {
            TrackerRiskLevel.HIGH
        }

        uniqueCells >= 3 && maxSpreadMeters >= 450.0 && activeWindowMinutes >= 20.0 &&
            confidence >= if (seenAtHome && seenAwayFromHome) 0.48 else 0.55 -> {
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
    } + if (seenAtHome && seenAwayFromHome) {
        " (Observed both near Home Point and away from it)"
    } else {
        ""
    } + if (trackerProfile?.likely == true) {
        " (BLE signature: ${formatTrackerFamilyLabel(trackerProfile.family)} ${String.format(Locale.US, "%.0f%%", trackerProfile.confidence * 100.0)})"
    } else {
        ""
    }

    return TrackerRiskSignal(
        level = level,
        confidence = confidence,
        uniqueLocationCells = uniqueCells,
        spreadMeters = maxSpreadMeters,
        activeWindowMinutes = activeWindowMinutes,
        summary = summary,
        seenAtHome = seenAtHome,
        seenAwayFromHome = seenAwayFromHome,
        trackerFamilyHint = trackerProfile?.family,
        trackerFamilyConfidence = trackerProfile?.confidence
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

private fun readLatestTwoMagneticSamples(encounters: List<Encounter>): Pair<Pair<Long, Double>, Pair<Long, Double>>? {
    var latest: Pair<Long, Double>? = null
    var previous: Pair<Long, Double>? = null

    encounters.forEach { encounter ->
        if (!isDirectSignalChannel(encounter, "magnetic")) return@forEach
        val payload = parseEncounterPayload(encounter) ?: return@forEach
        val magnitude = payload
            .optDouble("magnitudeMicroTesla", Double.NaN)
            .takeIf { it.isFinite() }
            ?: return@forEach
        val sample = encounter.timestampEpochMs to magnitude

        if (latest == null || sample.first > latest!!.first) {
            previous = latest
            latest = sample
        } else if ((previous == null || sample.first > previous!!.first) && sample.first < latest!!.first) {
            previous = sample
        }
    }

    val first = previous ?: return null
    val second = latest ?: return null
    return first to second
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

private fun parseEncounterPayload(encounter: Encounter): JSONObject? =
    runCatching { JSONObject(encounter.rawPayloadJson) }.getOrNull()

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
        val remoteIdLocation = resolveTrustedRemoteIdLocation(encounters)
        if (remoteIdLocation != null) {
            return LatLng(remoteIdLocation.lat, remoteIdLocation.lon) to remoteIdLocation.method
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

private fun noFlyDetectionCacheKey(location: DetectionLocation): String {
    val latBucket = (location.lat * 2.0).roundToInt()
    val lonBucket = (location.lon * 2.0).roundToInt()
    return "$latBucket:$lonBucket"
}

private fun noFlyZoneContainsPoint(
    zone: NoFlyZoneOverlayProvider.NoFlyZonePolygon,
    lat: Double,
    lon: Double
): Boolean {
    val boundary = zone.boundary
    if (boundary.size < 3) return false
    return polygonContainsPoint(boundary, lat, lon)
}

private fun extractEncounterAltitudeFeet(encounter: Encounter): Double? {
    val payload = parseEncounterPayload(encounter) ?: return null

    fun readFirstDouble(keyCandidates: List<String>): Double? {
        keyCandidates.forEach { key ->
            payload.optDoubleOrNull(key)?.let { return it }
            payload.optString(key, "").trim().toDoubleOrNull()?.let { return it }
        }
        return null
    }

    readFirstDouble(
        listOf(
            "altitudeFeet",
            "altitudeFt",
            "aircraftAltitudeFeet",
            "droneAltitudeFeet",
            "alt_ft"
        )
    )?.let { return it }

    val altitudeMeters = readFirstDouble(
        listOf(
            "altitudeMeters",
            "baroAltitudeMeters",
            "geoAltitudeMeters",
            "aircraftAltitudeMeters",
            "droneAltitudeMeters",
            "aircraft_altitude_m",
            "drone_altitude_m",
            "altitude_m",
            "alt_baro",
            "alt_geom",
            "altitude"
        )
    ) ?: return null

    return altitudeMeters * 3.28084
}

private fun noFlyZoneAltitudeAllowsEntry(
    zone: NoFlyZoneOverlayProvider.NoFlyZonePolygon,
    aircraftAltitudeFeet: Double?
): Boolean {
    if (aircraftAltitudeFeet == null || !aircraftAltitudeFeet.isFinite()) return true
    val lowerBoundFeet = zone.lowerAltitudeFeet?.toDouble()
    val upperBoundFeet = zone.upperAltitudeFeet?.toDouble()

    if (lowerBoundFeet != null && aircraftAltitudeFeet < lowerBoundFeet) return false
    if (upperBoundFeet != null && aircraftAltitudeFeet > upperBoundFeet) return false
    return true
}

private fun polygonContainsPoint(boundary: List<DetectionLocation>, lat: Double, lon: Double): Boolean {
    var inside = false
    var previousIndex = boundary.size - 1
    for (index in boundary.indices) {
        val currentPoint = boundary[index]
        val previousPoint = boundary[previousIndex]

        val currentLon = currentPoint.lon
        val currentLat = currentPoint.lat
        val previousLon = previousPoint.lon
        val previousLat = previousPoint.lat

        val intersects =
            ((currentLat > lat) != (previousLat > lat)) &&
                (lon < (previousLon - currentLon) * (lat - currentLat) / ((previousLat - currentLat) + 1e-12) + currentLon)

        if (intersects) inside = !inside
        previousIndex = index
    }
    return inside
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

private fun ensureFlockNotificationChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val existing = manager.getNotificationChannel(FLOCK_ALERT_CHANNEL_ID)
    if (existing != null) return

    val channel = NotificationChannel(
        FLOCK_ALERT_CHANNEL_ID,
        "Flock Alerts",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Alerts when likely related devices are detected traveling together"
    }
    manager.createNotificationChannel(channel)
}

private fun ensureNoFlyPassThroughNotificationChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val existing = manager.getNotificationChannel(NO_FLY_PASS_THROUGH_ALERT_CHANNEL_ID)
    if (existing != null) return

    val channel = NotificationChannel(
        NO_FLY_PASS_THROUGH_ALERT_CHANNEL_ID,
        "No-Fly Zone Pass-Through Alerts",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Alerts when tracked aircraft or Remote ID drones enter a configured no-fly zone"
    }
    manager.createNotificationChannel(channel)
}

private fun ensureNfcNotificationChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val existing = manager.getNotificationChannel(NFC_ALERT_CHANNEL_ID)
    if (existing != null) return

    val channel = NotificationChannel(
        NFC_ALERT_CHANNEL_ID,
        "NFC Tap Alerts",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Alerts when NFC tags/devices are tapped and ingested"
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

private fun sendFlockNotification(context: android.content.Context, flock: DeviceFlock) {
    val title = "Flock detected"
    val memberPreview = flock.members
        .take(3)
        .joinToString(separator = ", ") { member -> "${member.source}:${member.primaryId}" }
    val overflow = (flock.members.size - 3).coerceAtLeast(0)
    val overflowLabel = if (overflow > 0) " +$overflow" else ""
    val content = "${flock.members.size} devices • ${flock.coTravelEventCount} co-travel events • Span ${formatDistanceFeetMiles(flock.travelSpanMeters)} • $memberPreview$overflowLabel"

    val notification = NotificationCompat.Builder(context, FLOCK_ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle(title)
        .setContentText(content)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    val signature = flock.members
        .map { member -> "${member.source}|${member.primaryId}" }
        .sorted()
        .joinToString(separator = ",")
    val notificationId = ("flock:$signature").hashCode()
    NotificationManagerCompat.from(context).notify(notificationId, notification)
}

private fun sendNoFlyPassThroughNotification(
    context: android.content.Context,
    source: EncounterSource,
    primaryId: String,
    sourceLabel: String,
    zones: List<NoFlyZoneOverlayProvider.NoFlyZonePolygon>,
    eventEpochMs: Long,
    eventLat: Double?,
    eventLon: Double?
) {
    if (zones.isEmpty()) return

    val title = if (source == EncounterSource.REMOTE_ID) {
        "Drone entered no-fly zone"
    } else {
        "Aircraft entered no-fly zone"
    }
    val zoneHeadline = zones
        .take(2)
        .joinToString(" / ") { zone -> zone.label.takeIf { it.isNotBlank() } ?: "Unnamed zone" }
    val overflowSuffix = if (zones.size > 2) " +${zones.size - 2} more" else ""
    val content = "$sourceLabel $primaryId • $zoneHeadline$overflowSuffix"
    val enteredZoneIds = zones
        .asSequence()
        .map { zone -> zone.id }
        .sorted()
        .joinToString(",")
    val tapIntent = Intent(context, MainActivity::class.java).apply {
        action = ACTION_OPEN_NO_FLY_INCIDENT_PATH
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_NO_FLY_SOURCE, source.name)
        putExtra(EXTRA_NO_FLY_PRIMARY_ID, primaryId)
        putExtra(EXTRA_NO_FLY_ZONE_SUMMARY, "$zoneHeadline$overflowSuffix")
        putExtra(EXTRA_NO_FLY_EVENT_EPOCH_MS, eventEpochMs)
        putExtra(EXTRA_NO_FLY_ZONE_IDS, enteredZoneIds)
        if (isValidLatLon(eventLat, eventLon)) {
            putExtra(EXTRA_NO_FLY_LAT, eventLat!!)
            putExtra(EXTRA_NO_FLY_LON, eventLon!!)
        }
    }

    val zoneIdSignature = enteredZoneIds
    val notificationId = ("no-fly:$primaryId:$zoneIdSignature").hashCode()
    val tapPendingIntent = PendingIntent.getActivity(
        context,
        notificationId,
        tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, NO_FLY_PASS_THROUGH_ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setContentTitle(title)
        .setContentText(content)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(tapPendingIntent)
        .build()

    NotificationManagerCompat.from(context).notify(notificationId, notification)
}

private fun sendNfcNotification(context: android.content.Context, encounter: Encounter) {
    val title = "NFC tag detected"
    val sourceLabel = listSourceLabel(encounter.source.name, encounter.secondaryId)
    val content = "$sourceLabel ${encounter.primaryId} • ${formatEpoch(encounter.timestampEpochMs)}"

    val notification = NotificationCompat.Builder(context, NFC_ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_more)
        .setContentTitle(title)
        .setContentText(content)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    val notificationId = ("nfc:${encounter.primaryId}:${encounter.timestampEpochMs}").hashCode()
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
    wifiRandomizedOneOffSuppressionEnabled: Boolean,
    bleRandomizedOneOffSuppressionEnabled: Boolean,
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
                    allEncounters = allEncounters,
                    approachDetectionEnabled = approachDetectionEnabled,
                    ownedDeviceKeys = ownedDeviceKeys,
                    wifiRandomizedOneOffSuppressionEnabled = wifiRandomizedOneOffSuppressionEnabled,
                    bleRandomizedOneOffSuppressionEnabled = bleRandomizedOneOffSuppressionEnabled,
                    onDeviceClick = onDeviceClick
                )
            } else {
                EncountersPage(
                    allEncounters = allEncounters,
                    ownedDeviceKeys = ownedDeviceKeys,
                    onEncounterClick = onEncounterClick
                )
            }
        }
    }
}

@Composable
private fun CompactSwitchControl(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DevicesPage(
    allEncounters: List<Encounter>,
    approachDetectionEnabled: Boolean,
    ownedDeviceKeys: Set<String>,
    wifiRandomizedOneOffSuppressionEnabled: Boolean = true,
    bleRandomizedOneOffSuppressionEnabled: Boolean = true,
    onDeviceClick: (DeviceItem) -> Unit
) {
    val context = LocalContext.current
    val homePoint = ScanSettings.getHomePoint(context)
    var sortMode by remember { mutableStateOf(DeviceSortMode.LAST_SEEN) }
    var sourceFilter by remember { mutableStateOf<String?>(null) }
    var queryFilter by remember { mutableStateOf("") }
    var showSecondaryIds by rememberSaveable { mutableStateOf(false) }
    var showDistance by rememberSaveable { mutableStateOf(false) }
    var sortByDistance by rememberSaveable { mutableStateOf(false) }
    var showOwnedOnly by rememberSaveable { mutableStateOf(false) }
    var showTrackerRiskOnly by rememberSaveable { mutableStateOf(false) }
    var showHomeAwaySuspiciousOnly by rememberSaveable { mutableStateOf(false) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val currentLocation by if (showDistance) {
        LocationSnapshotProvider.observe(context).collectAsState(
            initial = LocationSnapshotProvider.read(context)
        )
    } else {
        remember { mutableStateOf<DetectionLocation?>(null) }
    }
    val selectedEncounters = allEncounters
    var devices by remember { mutableStateOf<List<DeviceItem>>(emptyList()) }
    var devicesComputing by remember { mutableStateOf(false) }
    LaunchedEffect(
        selectedEncounters,
        sortMode,
        approachDetectionEnabled,
        ownedDeviceKeys,
        homePoint,
        wifiRandomizedOneOffSuppressionEnabled,
        bleRandomizedOneOffSuppressionEnabled
    ) {
        devicesComputing = true
        val computed = withContext(Dispatchers.Default) {
            buildDeviceItems(
                encounters = selectedEncounters,
                sortMode = sortMode,
                approachDetectionEnabled = approachDetectionEnabled,
                ownedDeviceKeys = ownedDeviceKeys,
                homePoint = homePoint,
                suppressLikelyRandomizedWifiOneOffs = wifiRandomizedOneOffSuppressionEnabled,
                suppressLikelyRandomizedBleOneOffs = bleRandomizedOneOffSuppressionEnabled
            )
        }
        devices = computed
        devicesComputing = false
    }
    val sourceOptions = remember(devices) {
        orderedEncounterSourceOptions(devices.map { it.source }.toSet())
    }
    var filteredDeviceCount by remember { mutableStateOf(0) }
    var displayedDevices by remember { mutableStateOf<List<DeviceItem>>(emptyList()) }
    var deviceDisplayComputing by remember { mutableStateOf(false) }
    LaunchedEffect(
        devices,
        sourceFilter,
        queryFilter,
        showOwnedOnly,
        showTrackerRiskOnly,
        showHomeAwaySuspiciousOnly,
        homePoint,
        showDistance,
        sortByDistance,
        currentLocation
    ) {
        deviceDisplayComputing = true
        val (filtered, displayed) = withContext(Dispatchers.Default) {
            val filteredDevices = devices.filter { device ->
                val sourceMatches = sourceFilter == null || device.source == sourceFilter
                val queryMatches = queryFilter.isBlank() ||
                    device.primaryId.contains(queryFilter, ignoreCase = true) ||
                    (device.secondaryId?.contains(queryFilter, ignoreCase = true) == true)
                val ownedMatches = !showOwnedOnly || device.isOwned
                val riskMatches = !showTrackerRiskOnly ||
                    (device.trackerRisk?.level == TrackerRiskLevel.HIGH || device.trackerRisk?.level == TrackerRiskLevel.MEDIUM)
                val homeAwaySuspiciousMatches = !showHomeAwaySuspiciousOnly || homePoint == null || (
                    !device.isOwned &&
                        device.trackerRisk?.seenAtHome == true &&
                        device.trackerRisk?.seenAwayFromHome == true
                    )
                sourceMatches && queryMatches && ownedMatches && riskMatches && homeAwaySuspiciousMatches
            }
            val displayed = if (!showDistance || !sortByDistance) {
                filteredDevices
            } else {
                filteredDevices
                    .map { device -> device to (distanceForDeviceMeters(device, currentLocation) ?: Double.MIN_VALUE) }
                    .sortedWith(
                        compareByDescending<Pair<DeviceItem, Double>> { it.second }
                            .thenByDescending { it.first.lastSeenEpochMs }
                    )
                    .map { it.first }
            }
            filteredDevices to displayed
        }
        filteredDeviceCount = filtered.size
        displayedDevices = displayed
        deviceDisplayComputing = false
    }
    val distanceByDeviceKey = remember(displayedDevices, showDistance, currentLocation) {
        if (!showDistance || currentLocation == null) {
            emptyMap()
        } else {
            displayedDevices.associate { device ->
                "${device.source}|${device.primaryId}" to distanceForDeviceMeters(device, currentLocation)
            }
        }
    }
    var visibleDeviceCount by rememberSaveable(displayedDevices.size) { mutableStateOf(DETECTION_LIST_PAGE_SIZE) }
    val pagedDevices = remember(displayedDevices, visibleDeviceCount) {
        displayedDevices.take(visibleDeviceCount.coerceAtMost(displayedDevices.size))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Detected Devices", style = MaterialTheme.typography.headlineMedium)
        Text("Tap any device for detailed history.")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Filters & Display", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { filtersExpanded = !filtersExpanded }) {
                        Text(if (filtersExpanded) "Hide" else "Show")
                    }
                }
                AnimatedVisibility(
                    visible = filtersExpanded,
                    enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth / 4 }),
                    exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth / 4 })
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DeviceSortDropdown(
                                selectedSort = sortMode,
                                onSortSelected = { sortMode = it }
                            )
                            SourceFilterDropdown(
                                selectedSource = sourceFilter,
                                sourceOptions = sourceOptions,
                                onSourceSelected = { sourceFilter = it }
                            )
                        }
                        OutlinedTextField(
                            value = queryFilter,
                            onValueChange = { queryFilter = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search device ID or label") },
                            singleLine = true
                        )
                        CompactSwitchControl(
                            label = "Show Secondary IDs",
                            checked = showSecondaryIds,
                            onCheckedChange = { showSecondaryIds = it }
                        )
                        CompactSwitchControl(
                            label = "Show Distance",
                            checked = showDistance,
                            onCheckedChange = {
                                showDistance = it
                                if (!it) sortByDistance = false
                            }
                        )
                        CompactSwitchControl(
                            label = "Sort by Distance",
                            checked = sortByDistance,
                            onCheckedChange = { sortByDistance = it },
                            enabled = showDistance
                        )
                        CompactSwitchControl(
                            label = "Show Owned Only",
                            checked = showOwnedOnly,
                            onCheckedChange = { showOwnedOnly = it }
                        )
                        CompactSwitchControl(
                            label = "Tracker Risk Only",
                            checked = showTrackerRiskOnly,
                            onCheckedChange = { showTrackerRiskOnly = it }
                        )
                        CompactSwitchControl(
                            label = "Home→Away Suspicious Only",
                            checked = showHomeAwaySuspiciousOnly,
                            onCheckedChange = { showHomeAwaySuspiciousOnly = it },
                            enabled = homePoint != null
                        )
                    }
                }
            }
        }
        if (devicesComputing || deviceDisplayComputing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.height(16.dp).widthIn(min = 16.dp))
                Text("Refreshing device analysis...")
            }
        }
        Text("Showing ${pagedDevices.size} of ${displayedDevices.size} (filtered ${filteredDeviceCount})")
        Text(
            "Filters and search run across the full dataset; Load More only controls how many rows render at once.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn {
            items(
                items = pagedDevices,
                key = { device -> "${device.source}|${device.primaryId}" },
                contentType = { "device" }
            ) { device ->
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
                        if (device.trackerRisk?.seenAtHome == true) {
                            Text(
                                text = if (device.trackerRisk.seenAwayFromHome) {
                                    "Seen near Home Point and away"
                                } else {
                                    "Seen near Home Point"
                                },
                                color = Color(0xFF6A4F00),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (device.gpsSpoofSuspected) {
                            Text(
                                text = "GPS Spoof? Remote ID location looks inconsistent",
                                color = Color(0xFFB26A00),
                                fontWeight = FontWeight.Bold
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
                        val isCameraSource = device.source == EncounterSource.CAMERA.name
                        if (!isCameraSource && device.motionSpeedMps != null && device.motionHeadingDeg != null) {
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
                            val distanceMeters = distanceByDeviceKey["${device.source}|${device.primaryId}"]
                            Text(
                                "Distance: ${distanceMeters?.let(::formatDistanceFeetMiles) ?: "n/a"}"
                            )
                        }
                        Text("Seen ${device.seenCount} times")
                        Text("Last seen ${formatEpoch(device.lastSeenEpochMs)}")
                    }
                }
            }
            if (pagedDevices.size < displayedDevices.size) {
                item {
                    val remaining = (displayedDevices.size - pagedDevices.size).coerceAtLeast(0)
                    val nextBatch = remaining.coerceAtMost(DETECTION_LIST_PAGE_SIZE)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                visibleDeviceCount = (visibleDeviceCount + DETECTION_LIST_PAGE_SIZE)
                                    .coerceAtMost(displayedDevices.size)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Load $nextBatch More")
                        }
                        Text(
                            "Loaded ${pagedDevices.size} of ${displayedDevices.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FlocksPage(
    allEncounters: List<Encounter>
) {
    val selectedEncounters = allEncounters

    var flocks by remember { mutableStateOf<List<DeviceFlock>>(emptyList()) }
    var flocksComputing by remember { mutableStateOf(false) }
    LaunchedEffect(selectedEncounters) {
        flocksComputing = true
        flocks = withContext(Dispatchers.Default) {
            detectDeviceFlocks(
                encounters = selectedEncounters,
                minTravelSpanMeters = 10.0
            )
        }
        flocksComputing = false
    }

    val trustedLocationEncounterCount = remember(selectedEncounters) {
        selectedEncounters.count { encounter ->
            when (encounter.source) {
                EncounterSource.AIRCRAFT -> isValidLatLon(encounter.lat, encounter.lon)
                EncounterSource.REMOTE_ID -> remoteIdBroadcastLatLon(encounter) != null
                else -> false
            }
        }
    }
    val trustedLocationDeviceCount = remember(selectedEncounters) {
        selectedEncounters
            .asSequence()
            .filter { encounter ->
                when (encounter.source) {
                    EncounterSource.AIRCRAFT -> isValidLatLon(encounter.lat, encounter.lon)
                    EncounterSource.REMOTE_ID -> remoteIdBroadcastLatLon(encounter) != null
                    else -> false
                }
            }
            .map { encounter -> "${encounter.source.name}|${encounter.primaryId}" }
            .toSet()
            .size
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text("Flocks", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Text("Detects likely co-traveling groups and summarizes their repeated movement evidence.")
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Analyzed encounters: ${selectedEncounters.size}")
                    Text("Trusted-location encounters: $trustedLocationEncounterCount")
                    Text("Trusted-location devices: $trustedLocationDeviceCount")
                    Text("Active flocks: ${flocks.size}")
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("How this detector avoids false positives", fontWeight = FontWeight.Bold)
                    Text("Uses trusted moving-source locations only (Aircraft tracks and Remote ID drone broadcast coordinates).")
                    Text("Requires repeated co-travel events, movement span, and independent movement evidence per member.")
                    Text("Fixed or observer-tethered sources are intentionally excluded from flock links.")
                }
            }
        }

        if (flocksComputing) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp).widthIn(min = 16.dp))
                    Text("Analyzing flock graph...")
                }
            }
        }

        if (!flocksComputing && flocks.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No flocks detected in this scope yet. More repeated trusted-location co-travel observations are needed.",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        items(
            items = flocks,
            key = { flock -> "flock-${flock.id}-${flock.members.size}-${flock.lastSeenEpochMs}" },
            contentType = { "flock" }
        ) { flock ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Flock ${flock.id} • ${flock.members.size} devices",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${flock.coTravelEventCount} co-travel events • ${flock.pairLinkCount} pair links • Span ${formatDistanceFeetMiles(flock.travelSpanMeters)}"
                    )
                    Text("Window: ${formatEpoch(flock.firstSeenEpochMs)} -> ${formatEpoch(flock.lastSeenEpochMs)}")
                    val sourceMix = flock.members
                        .groupingBy { it.source }
                        .eachCount()
                        .entries
                        .sortedByDescending { it.value }
                        .joinToString(separator = ", ") { entry -> "${entry.key}:${entry.value}" }
                    Text("Source mix: $sourceMix")
                    val memberPreview = flock.members
                        .take(10)
                        .joinToString(separator = " • ") { member -> "${member.source}:${member.primaryId}" }
                    val overflow = (flock.members.size - 10).coerceAtLeast(0)
                    Text("Members: $memberPreview${if (overflow > 0) " +$overflow more" else ""}")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EncountersPage(
    allEncounters: List<Encounter>,
    ownedDeviceKeys: Set<String>,
    onEncounterClick: (Encounter) -> Unit
) {
    val context = LocalContext.current
    var sourceFilter by remember { mutableStateOf<String?>(null) }
    var queryFilter by remember { mutableStateOf("") }
    var showSecondaryIds by rememberSaveable { mutableStateOf(false) }
    var showDistance by rememberSaveable { mutableStateOf(false) }
    var sortByDistance by rememberSaveable { mutableStateOf(false) }
    var showOwnedOnly by rememberSaveable { mutableStateOf(false) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val currentLocation by if (showDistance) {
        LocationSnapshotProvider.observe(context).collectAsState(
            initial = LocationSnapshotProvider.read(context)
        )
    } else {
        remember { mutableStateOf<DetectionLocation?>(null) }
    }
    val encounters = allEncounters
    val sourceOptions = remember(encounters) {
        orderedEncounterSourceOptions(encounters.map { it.source.name }.toSet())
    }
    var filteredEncounterCount by remember { mutableStateOf(0) }
    var displayedEncounters by remember { mutableStateOf<List<Encounter>>(emptyList()) }
    var encounterDisplayComputing by remember { mutableStateOf(false) }
    LaunchedEffect(encounters, sourceFilter, queryFilter, showOwnedOnly, ownedDeviceKeys, showDistance, sortByDistance, currentLocation) {
        encounterDisplayComputing = true
        val (filtered, displayed) = withContext(Dispatchers.Default) {
            val filteredEncounters = encounters.filter { encounter ->
                val sourceMatches = sourceFilter == null || encounter.source.name == sourceFilter
                val queryMatches = queryFilter.isBlank() ||
                    encounter.primaryId.contains(queryFilter, ignoreCase = true) ||
                    (encounter.secondaryId?.contains(queryFilter, ignoreCase = true) == true) ||
                    encounter.rawPayloadJson.contains(queryFilter, ignoreCase = true)
                val ownedMatches = !showOwnedOnly ||
                    (OwnedDeviceRegistry.keyFor(encounter.source.name, encounter.primaryId) in ownedDeviceKeys)
                sourceMatches && queryMatches && ownedMatches
            }
            val displayed = if (!showDistance || !sortByDistance) {
                filteredEncounters
            } else {
                filteredEncounters
                    .map { encounter -> encounter to (distanceForEncounterMeters(encounter, currentLocation) ?: Double.MIN_VALUE) }
                    .sortedWith(
                        compareByDescending<Pair<Encounter, Double>> { it.second }
                            .thenByDescending { it.first.timestampEpochMs }
                    )
                    .map { it.first }
            }
            filteredEncounters to displayed
        }
        filteredEncounterCount = filtered.size
        displayedEncounters = displayed
        encounterDisplayComputing = false
    }
    val distanceByEncounterKey = remember(displayedEncounters, showDistance, currentLocation) {
        if (!showDistance || currentLocation == null) {
            emptyMap()
        } else {
            displayedEncounters.associate { encounter ->
                "${encounter.timestampEpochMs}|${encounter.source.name}|${encounter.primaryId}" to
                    distanceForEncounterMeters(encounter, currentLocation)
            }
        }
    }
    var visibleEncounterCount by rememberSaveable(displayedEncounters.size) { mutableStateOf(DETECTION_LIST_PAGE_SIZE) }
    val pagedEncounters = remember(displayedEncounters, visibleEncounterCount) {
        displayedEncounters.take(visibleEncounterCount.coerceAtMost(displayedEncounters.size))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Encounters", style = MaterialTheme.typography.headlineMedium)
        Text("Tap any encounter for full telemetry.")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Filters & Display", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { filtersExpanded = !filtersExpanded }) {
                        Text(if (filtersExpanded) "Hide" else "Show")
                    }
                }
                AnimatedVisibility(
                    visible = filtersExpanded,
                    enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth / 4 }),
                    exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth / 4 })
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SourceFilterDropdown(
                            selectedSource = sourceFilter,
                            sourceOptions = sourceOptions,
                            onSourceSelected = { sourceFilter = it }
                        )
                        OutlinedTextField(
                            value = queryFilter,
                            onValueChange = { queryFilter = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search ID, label, or payload") },
                            singleLine = true
                        )
                        CompactSwitchControl(
                            label = "Show Secondary IDs",
                            checked = showSecondaryIds,
                            onCheckedChange = { showSecondaryIds = it }
                        )
                        CompactSwitchControl(
                            label = "Show Distance",
                            checked = showDistance,
                            onCheckedChange = {
                                showDistance = it
                                if (!it) sortByDistance = false
                            }
                        )
                        CompactSwitchControl(
                            label = "Sort by Distance",
                            checked = sortByDistance,
                            onCheckedChange = { sortByDistance = it },
                            enabled = showDistance
                        )
                        CompactSwitchControl(
                            label = "Show Owned Only",
                            checked = showOwnedOnly,
                            onCheckedChange = { showOwnedOnly = it }
                        )
                    }
                }
            }
        }
        if (encounterDisplayComputing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.height(16.dp).widthIn(min = 16.dp))
                Text("Refreshing encounter list...")
            }
        }
        Text("Showing ${pagedEncounters.size} of ${displayedEncounters.size} (filtered ${filteredEncounterCount})")
        Text(
            "Filters and search run across the full dataset; Load More only controls how many rows render at once.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn {
            items(
                items = pagedEncounters,
                key = { encounter -> "${encounter.timestampEpochMs}|${encounter.source.name}|${encounter.primaryId}" },
                contentType = { "encounter" }
            ) { encounter ->
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
                            val distanceMeters = distanceByEncounterKey[
                                "${encounter.timestampEpochMs}|${encounter.source.name}|${encounter.primaryId}"
                            ]
                            Text(
                                "Distance: ${distanceMeters?.let(::formatDistanceFeetMiles) ?: "n/a"}"
                            )
                        }
                        Text("RSSI=${encounter.rssiDbm ?: "n/a"} dBm, Freq=${encounter.frequencyMhz ?: "n/a"} MHz")
                        Text(formatEpoch(encounter.timestampEpochMs))
                    }
                }
            }
            if (pagedEncounters.size < displayedEncounters.size) {
                item {
                    val remaining = (displayedEncounters.size - pagedEncounters.size).coerceAtLeast(0)
                    val nextBatch = remaining.coerceAtMost(DETECTION_LIST_PAGE_SIZE)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                visibleEncounterCount = (visibleEncounterCount + DETECTION_LIST_PAGE_SIZE)
                                    .coerceAtMost(displayedEncounters.size)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Load $nextBatch More")
                        }
                        Text(
                            "Loaded ${pagedEncounters.size} of ${displayedEncounters.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
    ownedDeviceKeys: Set<String> = emptySet(),
    homePoint: ScanSettings.HomePoint? = null,
    suppressLikelyRandomizedWifiOneOffs: Boolean = true,
    suppressLikelyRandomizedBleOneOffs: Boolean = true
): List<DeviceItem> =
    encounters
        .groupBy { it.source.name to it.primaryId }
        .mapNotNull { (key, groupedEncounters) ->
            buildDeviceItemForGroup(
                source = key.first,
                primaryId = key.second,
                groupedEncounters = groupedEncounters,
                approachDetectionEnabled = approachDetectionEnabled,
                ownedDeviceKeys = ownedDeviceKeys,
                homePoint = homePoint,
                suppressLikelyRandomizedWifiOneOffs = suppressLikelyRandomizedWifiOneOffs,
                suppressLikelyRandomizedBleOneOffs = suppressLikelyRandomizedBleOneOffs
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
    ownedDeviceKeys: Set<String>,
    homePoint: ScanSettings.HomePoint? = null,
    suppressLikelyRandomizedWifiOneOffs: Boolean = true,
    suppressLikelyRandomizedBleOneOffs: Boolean = true
): DeviceItem? = buildDeviceItemForGroup(
    source = source,
    primaryId = primaryId,
    groupedEncounters = groupedEncounters,
    approachDetectionEnabled = approachDetectionEnabled,
    ownedDeviceKeys = ownedDeviceKeys,
    homePoint = homePoint,
    suppressLikelyRandomizedWifiOneOffs = suppressLikelyRandomizedWifiOneOffs,
    suppressLikelyRandomizedBleOneOffs = suppressLikelyRandomizedBleOneOffs
)

private fun buildDeviceItemForGroup(
    source: String,
    primaryId: String,
    groupedEncounters: List<Encounter>,
    approachDetectionEnabled: Boolean,
    ownedDeviceKeys: Set<String>,
    homePoint: ScanSettings.HomePoint? = null,
    suppressLikelyRandomizedWifiOneOffs: Boolean = true,
    suppressLikelyRandomizedBleOneOffs: Boolean = true
): DeviceItem? {
    if (groupedEncounters.isEmpty()) return null
    if (
        shouldSuppressLikelyRandomizedWifiNoise(
            source = source,
            primaryId = primaryId,
            seenCount = groupedEncounters.size,
            wifiSuppressionEnabled = suppressLikelyRandomizedWifiOneOffs,
            bleSuppressionEnabled = suppressLikelyRandomizedBleOneOffs
        )
    ) {
        return null
    }

    val latest = groupedEncounters.maxByOrNull { it.timestampEpochMs } ?: groupedEncounters.first()
    val remoteIdResolvedLocation = if (source == EncounterSource.REMOTE_ID.name) {
        resolveTrustedRemoteIdLocation(groupedEncounters)
    } else {
        null
    }
    val owned = OwnedDeviceRegistry.keyFor(source, primaryId) in ownedDeviceKeys
    val isCameraSource = source == EncounterSource.CAMERA.name
    val approachSignal = if (approachDetectionEnabled && isApproachEligibleSource(source)) {
        analyzeApproachSignal(groupedEncounters)
    } else {
        null
    }
    val trackerRisk = if (isCameraSource) {
        null
    } else {
        analyzeTrackerRisk(
            encounters = groupedEncounters,
            isOwned = owned,
            approachSignal = approachSignal,
            homePoint = homePoint
        )
    }
    val motionSignal = if (isCameraSource) null else analyzeMotionSignal(groupedEncounters)
    val gpsSpoofSuspected =
        source == EncounterSource.REMOTE_ID.name &&
            isLikelyRemoteIdLocationSpoofed(groupedEncounters)

    return DeviceItem(
        source = source,
        primaryId = primaryId,
        secondaryId = latest.secondaryId,
        seenCount = groupedEncounters.size,
        lastSeenEpochMs = latest.timestampEpochMs,
        lastRssiDbm = latest.rssiDbm,
        lastFrequencyMhz = latest.frequencyMhz,
        lastLat = if (source == EncounterSource.REMOTE_ID.name) {
            remoteIdResolvedLocation?.lat
        } else {
            latest.lat
        },
        lastLon = if (source == EncounterSource.REMOTE_ID.name) {
            remoteIdResolvedLocation?.lon
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
        gpsSpoofSuspected = gpsSpoofSuspected,
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

private fun shouldSuppressLikelyRandomizedWifiNoise(
    source: EncounterSource,
    primaryId: String,
    seenCount: Int,
    wifiSuppressionEnabled: Boolean = true,
    bleSuppressionEnabled: Boolean = true
): Boolean {
    val wifiShouldSuppress =
        source == EncounterSource.WIFI &&
            wifiSuppressionEnabled &&
            isLikelyRandomizedMacAddress(primaryId) &&
            seenCount < WIFI_RANDOMIZED_MIN_SIGHTINGS
    val bleShouldSuppress =
        source == EncounterSource.BLUETOOTH_LE &&
            bleSuppressionEnabled &&
            isLikelyRandomizedMacAddress(primaryId) &&
            seenCount < WIFI_RANDOMIZED_MIN_SIGHTINGS

    return wifiShouldSuppress || bleShouldSuppress
}

private fun shouldSuppressLikelyRandomizedWifiNoise(
    source: String,
    primaryId: String,
    seenCount: Int,
    wifiSuppressionEnabled: Boolean = true,
    bleSuppressionEnabled: Boolean = true
): Boolean {
    val wifiShouldSuppress =
        source == EncounterSource.WIFI.name &&
            wifiSuppressionEnabled &&
            isLikelyRandomizedMacAddress(primaryId) &&
            seenCount < WIFI_RANDOMIZED_MIN_SIGHTINGS
    val bleShouldSuppress =
        source == EncounterSource.BLUETOOTH_LE.name &&
            bleSuppressionEnabled &&
            isLikelyRandomizedMacAddress(primaryId) &&
            seenCount < WIFI_RANDOMIZED_MIN_SIGHTINGS
    return wifiShouldSuppress || bleShouldSuppress
}

private fun isLikelyRandomizedMacAddress(macAddress: String?): Boolean {
    val mac = macAddress?.trim() ?: return false
    val firstOctetHex = mac.split(':').firstOrNull()?.takeIf { it.length == 2 } ?: return false
    val firstOctet = firstOctetHex.toIntOrNull(16) ?: return false
    val isLocallyAdministered = (firstOctet and 0x02) != 0
    val isUnicast = (firstOctet and 0x01) == 0
    return isLocallyAdministered && isUnicast
}

private fun countSuppressedLikelyRandomizedWifiOneOffDevices(
    encounters: List<Encounter>,
    suppressionEnabled: Boolean
): Int {
    if (!suppressionEnabled || encounters.isEmpty()) return 0
    return encounters
        .groupBy { it.source.name to it.primaryId }
        .count { (key, groupedEncounters) ->
            shouldSuppressLikelyRandomizedWifiNoise(
                source = key.first,
                primaryId = key.second,
                seenCount = groupedEncounters.size,
                wifiSuppressionEnabled = suppressionEnabled,
                bleSuppressionEnabled = false
            )
        }
}

private fun countSuppressedLikelyRandomizedBleOneOffDevices(
    encounters: List<Encounter>,
    suppressionEnabled: Boolean
): Int {
    if (!suppressionEnabled || encounters.isEmpty()) return 0
    return encounters
        .groupBy { it.source.name to it.primaryId }
        .count { (key, groupedEncounters) ->
            shouldSuppressLikelyRandomizedWifiNoise(
                source = key.first,
                primaryId = key.second,
                seenCount = groupedEncounters.size,
                wifiSuppressionEnabled = false,
                bleSuppressionEnabled = suppressionEnabled
            )
        }
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

private fun buildMeshPeerEncounters(snapshot: ChainMeshSnapshot): List<Encounter> {
    if (snapshot.peers.isEmpty()) return emptyList()
    val now = System.currentTimeMillis()

    return snapshot.peers.map { peer ->
        val lastSeen = peer.lastSeenEpochMs.takeIf { it > 0L } ?: now
        val payload = JSONObject().apply {
            put("meshNodeId", peer.nodeId)
            put("meshDeviceName", peer.deviceName)
            put("meshHost", peer.host)
            put("meshState", peer.state.name)
            put("meshLastSuccessfulSyncEpochMs", peer.lastSuccessfulSyncEpochMs)
            put("meshLastLinkRequestEpochMs", peer.lastLinkRequestEpochMs)
            put("meshLastFailure", peer.lastFailure)
            put("sharedLocationAccuracyMeters", peer.sharedLocationAccuracyMeters)
            put("sharedLocationTimestampEpochMs", peer.sharedLocationTimestampEpochMs)
            put("snapshotLocalNodeId", snapshot.localNodeId)
        }.toString()

        Encounter(
            timestampEpochMs = lastSeen,
            source = EncounterSource.ARGUS_MESH,
            primaryId = peer.nodeId,
            secondaryId = peer.deviceName?.ifBlank { null } ?: peer.host,
            rssiDbm = null,
            frequencyMhz = null,
            lat = peer.sharedLocationLat,
            lon = peer.sharedLocationLon,
            rawPayloadJson = payload,
            encounterFingerprint = "mesh:${peer.nodeId}:$lastSeen:${peer.state.name}",
            provenance = EncounterProvenance.CHAIN_LINKED,
            provenanceNodeId = peer.nodeId,
            provenanceOriginNodeId = peer.nodeId,
            provenancePathNodeIds = "${snapshot.localNodeId}->${peer.nodeId}",
            provenanceReceivedAtEpochMs = lastSeen,
            provenanceHopCount = 1
        )
    }
}

private fun mergePipelineEncounters(
    persistedEncounters: List<Encounter>,
    meshPeerEncounters: List<Encounter>
): List<Encounter> {
    if (meshPeerEncounters.isEmpty()) return persistedEncounters
    return (persistedEncounters + meshPeerEncounters)
        .sortedByDescending { it.timestampEpochMs }
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
                    resolveTrustedRemoteIdLocation(deviceEncounters)?.let { it.lat to it.lon }
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
    val currentLocation by LocationSnapshotProvider.observe(context).collectAsState(
        initial = LocationSnapshotProvider.read(context)
    )
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
    val itemIsCameraSource = remember(item?.source) {
        item?.source == EncounterSource.CAMERA.name
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

        if (item.gpsSpoofSuspected) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "GPS Spoof? Remote ID broadcast location was rejected as an outlier.",
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFFB26A00),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        ResponsiveDetailColumns(
            left = {
                DetailRow("Source", listSourceLabel(item.source, item.secondaryId))
                if (item.gpsSpoofSuspected) {
                    DetailRow("GPS Spoof Check", "Likely spoofed or inconsistent broadcast coordinates")
                }
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
                if (!itemIsCameraSource) {
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
                }
            },

            right = {
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
                    item.trackerRisk.trackerFamilyHint?.let { family ->
                        DetailRow("Tracker Family", formatTrackerFamilyLabel(family))
                    }
                    item.trackerRisk.trackerFamilyConfidence?.let { familyConfidence ->
                        DetailRow("Tracker Family Confidence", String.format(Locale.US, "%.0f%%", familyConfidence * 100.0))
                    }
                    DetailRow("Cross-Location Cells", item.trackerRisk.uniqueLocationCells.toString())
                    DetailRow("Observed Spread", formatDistanceFeetMiles(item.trackerRisk.spreadMeters))
                    DetailRow("Observed Window", String.format(Locale.US, "%.1f min", item.trackerRisk.activeWindowMinutes))
                    DetailRow("Seen Near Home Point", if (item.trackerRisk.seenAtHome) "Yes" else "No")
                    DetailRow("Seen Away From Home Point", if (item.trackerRisk.seenAwayFromHome) "Yes" else "No")
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
    val currentLocation by LocationSnapshotProvider.observe(context).collectAsState(
        initial = LocationSnapshotProvider.read(context)
    )

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
                val currentLocationSnapshot = currentLocation
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
                    if (currentLocationSnapshot != null && isValidLatLon(currentLocationSnapshot.lat, currentLocationSnapshot.lon)) {
                        String.format(Locale.US, "%.6f, %.6f", currentLocationSnapshot.lat, currentLocationSnapshot.lon)
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
    val mapStyleOptions = rememberMapStyleOptionsForTheme()
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
                    properties = MapProperties(mapStyleOptions = mapStyleOptions),
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
    val mapStyleOptions = rememberMapStyleOptionsForTheme()
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
                    properties = MapProperties(mapStyleOptions = mapStyleOptions),
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
            Text(selectedSource?.let { markerLegendLabelForSource(it) } ?: "All Sources")
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
                    text = { Text(markerLegendLabelForSource(source)) },
                    onClick = {
                        onSourceSelected(source)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun orderedEncounterSourceOptions(sources: Set<String>): List<String> {
    val preferredOrder = SOURCE_TYPE_UI_META_ORDERED.map { it.source }
    return preferredOrder.filter { it in sources } +
        sources.filterNot { it in preferredOrder }.sorted()
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
