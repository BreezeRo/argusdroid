package dev.argus.tracker.ui

import android.Manifest
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import dev.argus.tracker.permissions.AppPermissions
import dev.argus.tracker.ArgusApplication
import dev.argus.tracker.data.AppBackupManager
import dev.argus.tracker.data.AppEncryptionManager
import dev.argus.tracker.data.SecureSettingsStore
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private enum class DeviceSortMode {
    LAST_SEEN,
    MOST_SEEN
}

private const val DETECTION_LIST_PAGE_SIZE = 100

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
private const val CHAIN_EVENT_SYNC_DEBOUNCE_MS = 2500L
private const val CHAIN_SYNC_MIN_GAP_MS = 10_000L
private const val FLOCK_ALERT_COOLDOWN_MS = 10 * 60 * 1000L
private const val CAMERA_IN_VIEW_ALERT_CHANNEL_ID = "argus_camera_in_view_alerts"
private const val CAMERA_IN_VIEW_ALERT_COOLDOWN_MS = 3 * 60 * 1000L
private const val CAMERA_IN_VIEW_DISTANCE_THRESHOLD_METERS = 120.0
private const val NO_FLY_PASS_THROUGH_ALERT_CHANNEL_ID = "argus_no_fly_pass_through_alerts"
private const val NO_FLY_PASS_THROUGH_ALERT_COOLDOWN_MS = 2 * 60 * 1000L
private const val NFC_ALERT_CHANNEL_ID = "argus_nfc_alerts"
private const val NFC_ALERT_COOLDOWN_MS = 30 * 1000L
private const val STINGRAY_ALERT_CHANNEL_ID = "argus_stingray_alerts"
private const val STINGRAY_ALERT_COOLDOWN_MS = 5 * 60 * 1000L
private const val MAGNETIC_INCREASE_ALERT_CHANNEL_ID = "argus_magnetic_increase_alerts"
private const val MAGNETIC_INCREASE_ALERT_COOLDOWN_MS = 90 * 1000L
private const val MAGNETIC_INCREASE_DELTA_THRESHOLD_UT = 12.0
private const val MAGNETIC_INCREASE_MIN_CURRENT_UT = 55.0
private const val MAGNETIC_DISTURBANCE_UPPER_BOUND_UT = 65.0
private const val MAGNETIC_SUSTAINED_HIGH_THRESHOLD_UT = 72.0
private const val MAGNETIC_RHYTHM_COOLDOWN_MS = 1200L
private const val MAGNETIC_EVENT_POPUP_COOLDOWN_MS = 20 * 1000L
private const val MAGNETIC_RHYTHM_MIN_BPM = 72
private const val MAGNETIC_RHYTHM_MAX_BPM = 220
private const val MAGNETIC_RHYTHM_PLAY_MS = 2200L
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
private const val CHAIN_SHARED_SECRET_MIN_LENGTH = 12
private const val MAP_COVERAGE_RADIUS_PERCENTILE = 0.85
private const val MAP_PIN_LIMIT_MAX = 10_000
private const val DEVICE_MAP_PIN_CACHE_TTL_MS = 10L * 60L * 1000L
private const val DEVICE_MAP_PIN_DISK_CACHE_TTL_MS = 20L * 60L * 1000L
private const val WIFI_RANDOMIZED_MIN_SIGHTINGS = 2
private const val MAP_CAMERA_BOUNDS_SAMPLE_LIMIT = 220
private const val MOVING_PATH_RENDER_POINT_LIMIT = 900
private const val MAP_RENDER_PIN_LIMIT_FAR = 260
private const val MAP_RENDER_PIN_LIMIT_MID = 420
private const val MAP_RENDER_PIN_LIMIT_NEAR_SAFE = 650
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
private const val NO_FLY_ZONE_DETECTION_CACHE_MAX_BUCKETS = 24
private const val NO_FLY_ZONE_OVERLAY_CACHE_MAX_BUCKETS = 18
private const val DETECTION_TAB_MESH_INDEX = 4
private const val SIGNAL_INTEL_WINDOW_MS = 30L * 60L * 1000L
private const val SIGNAL_INTEL_MAX_ENCOUNTERS = 4000
private const val SIGNAL_INTEL_WINDOW_MINUTES = SIGNAL_INTEL_WINDOW_MS / 60_000L
private const val FIXED_LIVE_MAP_UPDATE_INTERVAL_SECONDS = 5L
private const val OPERATIONAL_ANALYSIS_WINDOW_MS = 60L * 60L * 1000L
private const val OPERATIONAL_ANALYSIS_MAX_ENCOUNTERS = 6000
private const val DEVICE_ANALYSIS_WINDOW_MS = 24L * 60L * 60L * 1000L
private const val DEVICE_ANALYSIS_MAX_ENCOUNTERS = 12_000
private const val ENCOUNTERS_TAB_MAX_DATASET = 20_000
private const val DEVICE_MAP_CANDIDATE_ENCOUNTER_CAP = 140
private const val ACTION_OPEN_APPROACH_MAP = "dev.argus.tracker.action.OPEN_APPROACH_MAP"
private const val ACTION_OPEN_NO_FLY_INCIDENT_PATH = "dev.argus.tracker.action.OPEN_NO_FLY_INCIDENT_PATH"
private const val ACTION_OPEN_MAGNETIC_MONITOR = "dev.argus.tracker.action.OPEN_MAGNETIC_MONITOR"
private const val ACTION_PIP_ZOOM_IN = "dev.argus.tracker.action.PIP_ZOOM_IN"
private const val ACTION_PIP_ZOOM_OUT = "dev.argus.tracker.action.PIP_ZOOM_OUT"
private const val EXTRA_APPROACH_SOURCE = "extra_approach_source"
private const val EXTRA_APPROACH_PRIMARY_ID = "extra_approach_primary_id"
private const val EXTRA_NO_FLY_SOURCE = "extra_no_fly_source"
private const val EXTRA_NO_FLY_PRIMARY_ID = "extra_no_fly_primary_id"
private const val EXTRA_NO_FLY_ZONE_SUMMARY = "extra_no_fly_zone_summary"
private const val EXTRA_NO_FLY_EVENT_EPOCH_MS = "extra_no_fly_event_epoch_ms"
private const val EXTRA_NO_FLY_LAT = "extra_no_fly_lat"
private const val EXTRA_NO_FLY_LON = "extra_no_fly_lon"
private const val EXTRA_NO_FLY_ZONE_IDS = "extra_no_fly_zone_ids"
private const val EXTRA_MAGNETIC_OPEN_POPUP = "extra_magnetic_open_popup"
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
    CAMERA_IN_VIEW,
    NO_FLY_PASS_THROUGH,
    NFC,
    STINGRAY
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

private enum class CellThreatLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH
}

private data class CellThreatSignal(
    val level: CellThreatLevel,
    val confidence: Double,
    val score: Double,
    val sampleCount: Int,
    val summary: String,
    val indicators: List<String>
)

private data class ConnectionSecuritySignal(
    val isInsecure: Boolean,
    val confidence: Double,
    val summary: String,
    val indicators: List<String>
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
    val trackerRisk: TrackerRiskSignal?,
    val cellThreat: CellThreatSignal?,
    val connectionSecurity: ConnectionSecuritySignal?
)

private data class DeviceDetailComputed(
    val deviceEncounters: List<Encounter>,
    val item: DeviceItem?
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
    val cellThreat: CellThreatSignal? = null,
    val connectionSecurity: ConnectionSecuritySignal? = null,
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
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val raw = prefs.getString(KEY_ALERT_LOG_ENTRIES, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
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
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject().apply {
                put("timestampEpochMs", entry.timestampEpochMs)
                put("type", entry.type.name)
                put("source", entry.source)
                put("primaryId", entry.primaryId)
                put("message", entry.message)
                if (entry.confidence != null) put("confidence", entry.confidence)
            }
            array.put(obj)
        }
        SecureSettingsStore.prefs(context, PREFS_NAME).edit {
            putString(KEY_ALERT_LOG_ENTRIES, array.toString())
        }
    }
}
private object ApproachTrackStore {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_APPROACH_TRACK_STARTS = "approach_track_starts"

    fun getTrackStartEpochMs(context: android.content.Context, source: String, primaryId: String): Long? {
        val key = "${source}|${primaryId}"
        val raw = SecureSettingsStore.prefs(context, PREFS_NAME)
            .getString(KEY_APPROACH_TRACK_STARTS, "{}")
            .orEmpty()
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        return obj.optLong(key, -1L).takeIf { it > 0L }
    }

    fun setTrackStartEpochMs(context: android.content.Context, source: String, primaryId: String, epochMs: Long) {
        val key = "${source}|${primaryId}"
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val raw = prefs.getString(KEY_APPROACH_TRACK_STARTS, "{}").orEmpty()
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        obj.put(key, epochMs.coerceAtLeast(0L))
        prefs.edit {
            putString(KEY_APPROACH_TRACK_STARTS, obj.toString())
        }
    }
}

private object DeviceSpeedRecordStore {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_DEVICE_TOP_SPEEDS = "device_top_speeds_mps"

    fun getAllRecordSpeedsMps(context: android.content.Context): Map<String, Double> {
        val raw = SecureSettingsStore.prefs(context, PREFS_NAME)
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
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val raw = prefs.getString(KEY_DEVICE_TOP_SPEEDS, "{}").orEmpty()
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        val existing = obj.optDouble(key, Double.NaN)
        if (existing.isFinite() && existing >= currentSpeedMps) return false
        obj.put(key, currentSpeedMps)
        prefs.edit {
            putString(KEY_DEVICE_TOP_SPEEDS, obj.toString())
        }
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
@OptIn(FlowPreview::class)
fun ArgusApp(
    notificationIntent: Intent? = null,
    inPictureInPictureMode: Boolean = false
) {
    val context = LocalContext.current
    val app = context.applicationContext as ArgusApplication
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var fullEncryptionEnabled by remember { mutableStateOf(ScanSettings.isFullEncryptionEnabled(context)) }
    var fullEncryptionUnlockMethod by remember { mutableStateOf(ScanSettings.getFullEncryptionUnlockMethod(context)) }
    var fullEncryptionSessionUnlocked by remember {
        mutableStateOf(!fullEncryptionEnabled || AppEncryptionManager.isSessionUnlocked())
    }
    var fullEncryptionUnlockInProgress by remember { mutableStateOf(false) }
    var fullEncryptionUnlockError by remember { mutableStateOf<String?>(null) }
    var fullEncryptionPinEntry by rememberSaveable { mutableStateOf("") }
    var fullEncryptionPasswordEntry by rememberSaveable { mutableStateOf("") }
    var fullEncryptionAutoLockTimeoutSeconds by remember {
        mutableStateOf(ScanSettings.getFullEncryptionAutoLockTimeoutSeconds(context))
    }
    var fullEncryptionPinWipeEnabled by remember {
        mutableStateOf(ScanSettings.isFullEncryptionPinWipeEnabled(context))
    }
    var fullEncryptionPinFailedAttempts by remember { mutableStateOf(0) }
    var fullEncryptionPinLockoutUntilEpochMs by remember { mutableStateOf(0L) }
    var fullEncryptionLastWipeEpochMs by remember { mutableStateOf<Long?>(null) }
    var fullEncryptionWrapRotationCount by remember { mutableStateOf(0) }
    var fullEncryptionWrapLastRotationEpochMs by remember { mutableStateOf<Long?>(null) }
    var fullEncryptionMigrationTelemetry by remember {
        mutableStateOf(SecureSettingsStore.readMigrationTelemetry(context))
    }
    val secureDataUnlocked = !fullEncryptionEnabled || fullEncryptionSessionUnlocked
    val viewModel = if (secureDataUnlocked) {
        viewModel<ArgusViewModel>(
            factory = ArgusViewModel.Factory(app.container.repository)
        )
    } else {
        null
    }
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
    var mapScannerSweepAnimationSpeedPreset by remember {
        mutableStateOf(ScanSettings.getMapScannerSweepAnimationSpeedPreset(context))
    }
    var mapIdentityFullNamesEnabled by remember {
        mutableStateOf(ScanSettings.isMapIdentityFullNamesEnabled(context))
    }
    var stickyCompassMapEnabled by remember {
        mutableStateOf(ScanSettings.isStickyCompassMapEnabled(context))
    }
    var stickyCompassMapLiveMode by remember {
        mutableStateOf(ScanSettings.getStickyCompassMapLiveMode(context))
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
    var liveModeOnlyEnabled by remember { mutableStateOf(ScanSettings.isLiveModeOnlyEnabled(context)) }
    var approachNotificationsEnabled by remember { mutableStateOf(ScanSettings.isApproachNotificationsEnabled(context)) }
    var trackerNotificationsEnabled by remember { mutableStateOf(ScanSettings.isTrackerNotificationsEnabled(context)) }
    var flockNotificationsEnabled by remember { mutableStateOf(ScanSettings.isFlockNotificationsEnabled(context)) }
    var cameraInViewNotificationsEnabled by remember {
        mutableStateOf(ScanSettings.isCameraInViewNotificationsEnabled(context))
    }
    var noFlyPassThroughNotificationsEnabled by remember {
        mutableStateOf(ScanSettings.isNoFlyPassThroughNotificationsEnabled(context))
    }
    var nfcNotificationsEnabled by remember { mutableStateOf(ScanSettings.isNfcNotificationsEnabled(context)) }
    var stingrayNotificationsEnabled by remember { mutableStateOf(ScanSettings.isStingrayNotificationsEnabled(context)) }
    var magneticIncreaseNotificationsEnabled by remember { mutableStateOf(ScanSettings.isMagneticIncreaseNotificationsEnabled(context)) }
    var magneticRhythmBeepEnabled by remember { mutableStateOf(ScanSettings.isMagneticRhythmBeepEnabled(context)) }
    var magneticEventTriggerThresholdMicroTesla by remember {
        mutableStateOf(ScanSettings.getMagneticEventTriggerThresholdMicroTesla(context))
    }
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
    var lastChainSyncEpochMs by remember { mutableStateOf(0L) }
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
    val cellThreatStateByDevice = remember { mutableMapOf<String, CellThreatLevel>() }
    val lastStingrayNotificationEpochByDevice = remember { mutableMapOf<String, Long>() }
    val lastFlockNotificationEpochBySignature = remember { mutableMapOf<String, Long>() }
    val cameraInViewStateByDevice = remember { mutableMapOf<String, Boolean>() }
    val lastCameraInViewNotificationEpochByDevice = remember { mutableMapOf<String, Long>() }
    val noFlyZoneStateByTrack = remember { mutableMapOf<String, Set<String>>() }
    val lastNoFlyZoneNotificationEpochByTrackZone = remember { mutableMapOf<String, Long>() }
    val noFlyZoneDetectionCache = remember {
        boundedLruMutableMap<String, List<NoFlyZoneOverlayProvider.NoFlyZonePolygon>>(
            NO_FLY_ZONE_DETECTION_CACHE_MAX_BUCKETS
        )
    }
    var lastNfcAlertEpochMs by remember { mutableStateOf(0L) }
    var lastNfcObservedEncounterEpochMs by remember { mutableStateOf(0L) }
    var lastWearStatusSignature by remember { mutableStateOf<String?>(null) }
    var lastWearStatusPublishEpochMs by remember { mutableStateOf(0L) }
    var lastOperationalStateSignature by remember { mutableStateOf("") }
    var startupConfigLoaded by remember { mutableStateOf(false) }
    var trackingObserverInitialized by remember { mutableStateOf(false) }
    var operationalObserverInitialized by remember { mutableStateOf(false) }
    var mapDataPrewarmReady by remember { mutableStateOf(true) }
    var startupMapPrewarmCompleted by remember { mutableStateOf(false) }
    var startupBootstrapScanCompleted by remember { mutableStateOf(false) }
    var startupBootstrapWaitRequired by remember { mutableStateOf(true) }
    var startupRuntimeGateReleased by remember { mutableStateOf(false) }
    var startupPrewarmedDevicePins by remember { mutableStateOf<List<MapPin>>(emptyList()) }
    var startupPrewarmedNoFlyZones by remember { mutableStateOf<List<NoFlyZoneOverlayProvider.NoFlyZonePolygon>>(emptyList()) }
    val intervalManagedSourceTypes = remember {
        ScanSettings.SOURCE_TYPES.filterNot {
            it == SourceCatalog.KEY_WIFI_DIRECT ||
                it == SourceCatalog.KEY_REMOTE_ID ||
                it == SourceCatalog.KEY_NFC
        }
    }
    var mapResetGeneration by remember { mutableStateOf(0L) }
    var analyzedDevices by remember { mutableStateOf<List<DeviceItem>>(emptyList()) }
    var detectionInitialTabRequest by rememberSaveable { mutableStateOf<Int?>(null) }
    var detectionInitialMagneticPopupRequest by rememberSaveable { mutableStateOf<Long?>(null) }
    var globalMagneticDetectionPopup by remember { mutableStateOf<MagneticDetectionPopupState?>(null) }
    var globalLastMagneticPopupEpochMs by remember { mutableStateOf(0L) }
    var globalLastMagneticNotificationEpochMs by remember { mutableStateOf(0L) }
    var globalLastMagneticRhythmEpochMs by remember { mutableStateOf(0L) }
    var globalPreviousLiveMagneticMagnitudeMicroTesla by remember { mutableStateOf<Double?>(null) }
    var globalMagneticRhythmJob by remember { mutableStateOf<Job?>(null) }
    var pipZoomCommandNonce by remember { mutableStateOf(0L) }
    var pipZoomCommandDelta by remember { mutableStateOf(0f) }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: HOME_ROUTE
    val globalMagneticMonitorEnabled = foreignDirectMagneticEnabled &&
        currentRoute != DETECTION_ROUTE &&
        (magneticRhythmBeepEnabled || magneticIncreaseNotificationsEnabled)
    val globalLiveMagneticMagnitudeMicroTesla by rememberRealtimeMagneticMagnitudeMicroTesla(
        enabled = globalMagneticMonitorEnabled
    )
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

    fun refreshEncryptionPolicyState() {
        fullEncryptionEnabled = ScanSettings.isFullEncryptionEnabled(context)
        fullEncryptionUnlockMethod = ScanSettings.getFullEncryptionUnlockMethod(context)
        fullEncryptionAutoLockTimeoutSeconds = ScanSettings.getFullEncryptionAutoLockTimeoutSeconds(context)
        fullEncryptionPinWipeEnabled = ScanSettings.isFullEncryptionPinWipeEnabled(context)
        val pinState = AppEncryptionManager.readPinUnlockState(context)
        fullEncryptionPinFailedAttempts = pinState.failedAttempts
        fullEncryptionPinLockoutUntilEpochMs = pinState.lockoutUntilEpochMs
        fullEncryptionLastWipeEpochMs = pinState.lastWipeEpochMs
        val wrapState = AppEncryptionManager.readWrapRotationState(context)
        fullEncryptionWrapRotationCount = wrapState.rotationCount
        fullEncryptionWrapLastRotationEpochMs = wrapState.lastRotationEpochMs
        fullEncryptionMigrationTelemetry = SecureSettingsStore.readMigrationTelemetry(context)
    }

    val requireAllEncounterStream = remember(currentRoute) {
        requiresAllEncountersRoute(currentRoute)
    }

    LaunchedEffect(
        currentRoute,
        foreignDirectMagneticEnabled,
        magneticRhythmBeepEnabled,
        magneticIncreaseNotificationsEnabled,
        magneticEventTriggerThresholdMicroTesla,
        globalLiveMagneticMagnitudeMicroTesla
    ) {
        if (!globalMagneticMonitorEnabled) {
            globalPreviousLiveMagneticMagnitudeMicroTesla = null
            globalMagneticRhythmJob?.cancel()
            globalMagneticRhythmJob = null
            return@LaunchedEffect
        }

        val currentMagnitude = globalLiveMagneticMagnitudeMicroTesla ?: return@LaunchedEffect
        val previousMagnitude = globalPreviousLiveMagneticMagnitudeMicroTesla
        globalPreviousLiveMagneticMagnitudeMicroTesla = currentMagnitude
        if (previousMagnitude == null) return@LaunchedEffect

        val deltaMicroTesla = currentMagnitude - previousMagnitude
        val triggerThresholdMicroTesla = magneticEventTriggerThresholdMicroTesla
        val crossedDisturbanceBand =
            previousMagnitude < triggerThresholdMicroTesla &&
                currentMagnitude >= triggerThresholdMicroTesla
        val sharpIncrease =
            deltaMicroTesla >= MAGNETIC_INCREASE_DELTA_THRESHOLD_UT &&
                currentMagnitude >= minOf(MAGNETIC_INCREASE_MIN_CURRENT_UT, triggerThresholdMicroTesla)
        val sustainedHighThresholdMicroTesla = maxOf(
            triggerThresholdMicroTesla + 8.0,
            MAGNETIC_SUSTAINED_HIGH_THRESHOLD_UT
        )
        val sustainedHigh = currentMagnitude >= sustainedHighThresholdMicroTesla
        val magneticEventDetected = crossedDisturbanceBand || sharpIncrease || sustainedHigh
        if (!magneticEventDetected) return@LaunchedEffect

        val severity = magneticAlertSeverity(
            currentMagnitudeMicroTesla = currentMagnitude,
            deltaMicroTesla = deltaMicroTesla
        )
        val now = System.currentTimeMillis()

        if (
            magneticIncreaseNotificationsEnabled &&
            hasPostNotificationsPermission(context) &&
            now - globalLastMagneticNotificationEpochMs >= MAGNETIC_INCREASE_ALERT_COOLDOWN_MS
        ) {
            ensureMagneticIncreaseNotificationChannel(context)
            sendMagneticIncreaseNotification(
                context = context,
                previousMagnitudeMicroTesla = previousMagnitude,
                currentMagnitudeMicroTesla = currentMagnitude,
                deltaMicroTesla = deltaMicroTesla
            )
            globalLastMagneticNotificationEpochMs = now
        }

        if (now - globalLastMagneticPopupEpochMs >= MAGNETIC_EVENT_POPUP_COOLDOWN_MS) {
            globalMagneticDetectionPopup = buildMagneticDetectionPopupState(
                detectionEpochMs = now,
                previousMagnitudeMicroTesla = previousMagnitude,
                currentMagnitudeMicroTesla = currentMagnitude,
                deltaMicroTesla = deltaMicroTesla,
                triggerThresholdMicroTesla = triggerThresholdMicroTesla,
                crossedDisturbanceBand = crossedDisturbanceBand,
                sharpIncrease = sharpIncrease,
                sustainedHigh = sustainedHigh,
                severity = severity
            )
            globalLastMagneticPopupEpochMs = now
        }

        if (!magneticRhythmBeepEnabled) return@LaunchedEffect
        if (now - globalLastMagneticRhythmEpochMs < MAGNETIC_RHYTHM_COOLDOWN_MS) return@LaunchedEffect

        globalMagneticRhythmJob?.cancel()
        globalMagneticRhythmJob = scope.launch(Dispatchers.Default) {
            playMagneticRhythm(
                severity = severity,
                playMs = MAGNETIC_RHYTHM_PLAY_MS
            )
        }
        globalLastMagneticRhythmEpochMs = now
    }

    val recent by if (viewModel != null) {
        viewModel.recentEncounters.collectAsState()
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    val recent100 by if (viewModel != null) {
        viewModel.recent100Encounters.collectAsState()
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    val allEncounters by if (viewModel != null && requireAllEncounterStream) {
        viewModel.allEncounters.collectAsState()
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    val summary by if (viewModel != null) {
        viewModel.summary.collectAsState()
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    val chainMesh by if (secureDataUnlocked) {
        app.container.chainLinkCoordinator.observeMesh().collectAsState()
    } else {
        remember {
            mutableStateOf(
                ChainMeshSnapshot(
                    localNodeId = ScanSettings.getChainNodeId(context),
                    localDeviceName = ScanSettings.getChainDeviceName(context),
                    peers = emptyList(),
                    incomingRequests = emptyList(),
                    wipeNotices = emptyList(),
                    lastRefreshEpochMs = null,
                    lastSyncEpochMs = null
                )
            )
        }
    }
    val meshPeerEncounters = remember(chainMesh) {
        buildMeshPeerEncounters(chainMesh)
    }
    val recent100PipelineEncounters = remember(recent100, meshPeerEncounters) {
        mergePipelineEncounters(recent100, meshPeerEncounters)
    }
    val allPipelineEncounters = remember(allEncounters, meshPeerEncounters) {
        mergePipelineEncounters(allEncounters, meshPeerEncounters)
    }
    LaunchedEffect(allPipelineEncounters) {
        RuntimeUiListMemoryGauge.updatePipelineEncounters(allPipelineEncounters)
    }
    val hasMapsApiKey by remember(context) { mutableStateOf(hasGoogleMapsApiKey(context)) }
    var mapPrewarmReady by remember { mutableStateOf(!hasMapsApiKey) }
    val appStartupReady = startupConfigLoaded && trackingObserverInitialized && operationalObserverInitialized
    val encryptionGateSatisfied = !fullEncryptionEnabled || fullEncryptionSessionUnlocked
    val appRuntimeReady = appStartupReady && mapPrewarmReady && mapDataPrewarmReady && encryptionGateSatisfied
    val startupBootstrapGateSatisfied = !startupBootstrapWaitRequired || startupBootstrapScanCompleted
    val lastScanEpochMs = remember(recent) { recent.maxOfOrNull { it.timestampEpochMs } }
    val operationalAnalysisWindow = remember(recent) {
        selectRecentEncounterWindow(
            encounters = recent,
            windowMs = OPERATIONAL_ANALYSIS_WINDOW_MS,
            maxEncounters = OPERATIONAL_ANALYSIS_MAX_ENCOUNTERS
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
        delay(200.milliseconds)
        viewModel?.refreshSummary()
    }

    val hostActivity = remember(context) { context.findFragmentActivity() }

    LaunchedEffect(encryptionGateSatisfied) {
        if (!encryptionGateSatisfied) return@LaunchedEffect
        app.onSecureDataUnlocked()
    }

    LaunchedEffect(fullEncryptionLastWipeEpochMs, viewModel) {
        val wipeEpochMs = fullEncryptionLastWipeEpochMs ?: return@LaunchedEffect
        if (wipeEpochMs <= 0L) return@LaunchedEffect

        // After protective wipe, force-refresh derived counters so stale in-memory 24h summaries disappear.
        lastScanDurationMs = ScanSettings.getLastScanDurationMs(context)
        sourceScanTimings = ScanSettings.getSourceScanTimings(context)
        sourceLastScanEpochs = ScanSettings.getAllSourceLastScanEpochMs(context)
        sourceLastRawObservationEpochs = ScanSettings.getAllSourceLastRawObservationEpochMs(context)
        scanIntervalChangeEvents = ScanSettings.getScanIntervalChangeEvents(context, 10)
        alertLogs = AlertLogStore.read(context)
        errorLogs = OperationalErrorLogStore.read(context)
        viewModel?.refreshSummary()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, fullEncryptionEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (fullEncryptionEnabled) {
                        AppEncryptionManager.onAppBackgrounded(context)
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (fullEncryptionEnabled) {
                        val unlocked = AppEncryptionManager.onAppForegrounded(context)
                        fullEncryptionSessionUnlocked = unlocked
                        if (!unlocked) {
                            fullEncryptionUnlockError = "Session locked after inactivity. Authenticate to continue."
                        }
                        refreshEncryptionPolicyState()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val nowTickerEnabled = remember(currentRoute) { routeNeedsNowTicker(currentRoute) }
    LaunchedEffect(nowTickerEnabled) {
        if (!nowTickerEnabled) return@LaunchedEffect
        tickerFlow(periodMs = 1000L).collect { now ->
            appNowEpochMs = now
        }
    }

    LaunchedEffect(chainMesh, alertLogs, recent100PipelineEncounters) {
        delay(750.milliseconds)
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

            ACTION_OPEN_MAGNETIC_MONITOR -> {
                detectionInitialTabRequest = 0
                if (intent.getBooleanExtra(EXTRA_MAGNETIC_OPEN_POPUP, false)) {
                    detectionInitialMagneticPopupRequest = System.currentTimeMillis()
                }
                navController.navigate(DETECTION_ROUTE) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                }
            }

            ACTION_PIP_ZOOM_IN -> {
                pipZoomCommandDelta = 1f
                pipZoomCommandNonce = SystemClock.elapsedRealtimeNanos()
                navController.navigate(DETECTION_ROUTE) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                }
            }

            ACTION_PIP_ZOOM_OUT -> {
                pipZoomCommandDelta = -1f
                pipZoomCommandNonce = SystemClock.elapsedRealtimeNanos()
                navController.navigate(DETECTION_ROUTE) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
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
            ?.coerceAtLeast(0L)
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
        refreshEncryptionPolicyState()
        fullEncryptionSessionUnlocked = !fullEncryptionEnabled || AppEncryptionManager.isSessionUnlocked()
        fullEncryptionUnlockError = null

        viewModel?.refreshSummary()
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
        mapScannerSweepAnimationSpeedPreset = ScanSettings.getMapScannerSweepAnimationSpeedPreset(context)
        mapIdentityFullNamesEnabled = ScanSettings.isMapIdentityFullNamesEnabled(context)
        stickyCompassMapEnabled = ScanSettings.isStickyCompassMapEnabled(context)
        stickyCompassMapLiveMode = ScanSettings.getStickyCompassMapLiveMode(context)
        wifiRandomizedOneOffSuppressionEnabled = ScanSettings.isWifiRandomizedOneOffSuppressionEnabled(context)
        wifiAggregateOnlyEnabled = ScanSettings.isWifiAggregateOnlyEnabled(context)
        bleRandomizedOneOffSuppressionEnabled = ScanSettings.isBleRandomizedOneOffSuppressionEnabled(context)
        bleAggregateOnlyEnabled = ScanSettings.isBleAggregateOnlyEnabled(context)
        sourceScanIntervals = ScanSettings.getAllSourceScanIntervalSeconds(context)
        sourceLastScanEpochs = ScanSettings.getAllSourceLastScanEpochMs(context)
        sourceLastRawObservationEpochs = ScanSettings.getAllSourceLastRawObservationEpochMs(context)
        foreignDirectAcousticEnabled = ScanSettings.isForeignDirectAcousticEnabled(context)
        foreignDirectMagneticEnabled = ScanSettings.isForeignDirectMagneticEnabled(context)
        liveModeOnlyEnabled = ScanSettings.isLiveModeOnlyEnabled(context)
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

    LaunchedEffect(currentRoute, startupMapPrewarmCompleted, secureDataUnlocked) {
        if (startupMapPrewarmCompleted) return@LaunchedEffect
        if (!secureDataUnlocked) return@LaunchedEffect
        if (currentRoute != DETECTION_ROUTE) return@LaunchedEffect

        val prewarmedPins = runCatching {
            withTimeoutOrNull(2500.milliseconds) {
                val startupEncounters = withContext(Dispatchers.IO) {
                    app.container.repository.observeRecent(limit = 2500).first()
                }
                withContext(Dispatchers.Default) {
                    buildStartupPrewarmedDeviceMapPins(
                        encounters = startupEncounters,
                        ownedDeviceKeys = ownedDeviceKeys,
                        approachDetectionEnabled = approachDetectionEnabled,
                        sourceScanIntervals = sourceScanIntervals,
                        liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds,
                        maxDeviceCandidates = 700,
                        suppressLikelyRandomizedWifiOneOffs = wifiRandomizedOneOffSuppressionEnabled,
                        suppressLikelyRandomizedBleOneOffs = bleRandomizedOneOffSuppressionEnabled
                    )
                }
            }
        }.getOrElse {
            OperationalErrorLogStore.append(
                context = context,
                category = "MAP_PREWARM",
                source = "startup",
                message = it.message ?: "unknown error",
                severity = "WARNING"
            )
            null
        }.orEmpty()

        startupPrewarmedDevicePins = prewarmedPins
        DeviceMapPinRuntimeCache.put(prewarmedPins)
        DeviceMapPinDiskCache.put(context, prewarmedPins)

        val prewarmedNoFlyZones = if (mapNoFlyZonesEnabled) {
            runCatching {
                withContext(Dispatchers.IO) {
                    NoFlyZoneOverlayProvider.read(
                        context = context,
                        near = LocationSnapshotProvider.read(context),
                        allowNetworkFetch = false
                    )
                }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        startupPrewarmedNoFlyZones = prewarmedNoFlyZones

        withContext(Dispatchers.Default) {
            prewarmedPins.take(80).forEach { pin ->
                markerDotIconForPin(pin, useSourceOnlyPinColors = true)
                markerIconForPin(pin, useSourceOnlyPinColors = true)
            }
        }

        startupMapPrewarmCompleted = true
    }

    LaunchedEffect(
        chainLinkEnabled,
        chainAutoSyncEnabled,
        chainAutoSyncIntervalSeconds,
        chainSharedSecret,
        secureDataUnlocked
    ) {
        if (!secureDataUnlocked) return@LaunchedEffect
        if (!chainLinkEnabled || !chainAutoSyncEnabled) return@LaunchedEffect
        if (chainSharedSecret.isBlank()) return@LaunchedEffect

        while (true) {
            val nowEpochMs = System.currentTimeMillis()
            if (nowEpochMs - lastChainSyncEpochMs >= CHAIN_SYNC_MIN_GAP_MS) {
                runCatching { app.container.chainLinkCoordinator.syncNow() }
                    .onSuccess { lastChainSyncEpochMs = System.currentTimeMillis() }
            }
            delay(chainAutoSyncIntervalSeconds.seconds)
        }
    }

    LaunchedEffect(
        chainLinkEnabled,
        chainAutoSyncEnabled,
        chainSharedSecret,
        secureDataUnlocked
    ) {
        if (!secureDataUnlocked) return@LaunchedEffect
        if (!chainLinkEnabled || !chainAutoSyncEnabled) return@LaunchedEffect
        if (chainSharedSecret.isBlank()) return@LaunchedEffect

        app.container.repository.observeRecent(limit = 1)
            .map { encounters ->
                encounters.firstOrNull { it.provenance == EncounterProvenance.LOCAL }
                    ?.let { encounter ->
                        encounter.encounterFingerprint
                            ?: "${encounter.timestampEpochMs}|${encounter.source.name}|${encounter.primaryId}|${encounter.id}"
                    }
            }
            .filterNotNull()
            .distinctUntilChanged()
            .drop(1)
            .collect {
                delay(CHAIN_EVENT_SYNC_DEBOUNCE_MS)
                val nowEpochMs = System.currentTimeMillis()
                if (nowEpochMs - lastChainSyncEpochMs < CHAIN_SYNC_MIN_GAP_MS) return@collect
                runCatching { app.container.chainLinkCoordinator.syncNow() }
                    .onSuccess { lastChainSyncEpochMs = System.currentTimeMillis() }
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
        chainAutoSyncIntervalSeconds,
        secureDataUnlocked
    ) {
        if (!secureDataUnlocked) return@LaunchedEffect
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
            .debounce(250.milliseconds)
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

    LaunchedEffect(analyzedDevices, stingrayNotificationsEnabled) {
        val now = System.currentTimeMillis()
        val seenKeys = mutableSetOf<String>()
        val stingrayLogsToAppend = mutableListOf<AlertLogEntry>()

        analyzedDevices.forEach { device ->
            val key = "${device.source}|${device.primaryId}"
            seenKeys += key

            val currentLevel = device.cellThreat?.level ?: CellThreatLevel.NONE
            val previousLevel = cellThreatStateByDevice[key] ?: CellThreatLevel.NONE
            val currentlySuspicious =
                currentLevel == CellThreatLevel.HIGH || currentLevel == CellThreatLevel.MEDIUM
            val previouslySuspicious =
                previousLevel == CellThreatLevel.HIGH || previousLevel == CellThreatLevel.MEDIUM

            if (currentlySuspicious && !previouslySuspicious) {
                val threat = device.cellThreat
                val confidence = threat?.confidence
                val confidencePct = ((confidence ?: 0.0) * 100.0).toInt().coerceIn(0, 100)
                val summary = threat?.summary?.takeIf { it.isNotBlank() } ?: "Cell threat indicators increased"
                stingrayLogsToAppend += AlertLogEntry(
                    timestampEpochMs = now,
                    type = AlertLogType.STINGRAY,
                    source = device.source,
                    primaryId = device.primaryId,
                    message = "Stingray risk ${currentLevel.name} for ${listSourceLabel(device.source, device.secondaryId)} ${device.primaryId} ($confidencePct% confidence): $summary",
                    confidence = confidence
                )

                val lastNotified = lastStingrayNotificationEpochByDevice[key] ?: 0L
                if (stingrayNotificationsEnabled && hasPostNotificationsPermission(context) && now - lastNotified >= STINGRAY_ALERT_COOLDOWN_MS) {
                    ensureStingrayNotificationChannel(context)
                    sendStingrayNotification(context, device)
                    lastStingrayNotificationEpochByDevice[key] = now
                }
            }

            cellThreatStateByDevice[key] = currentLevel
        }

        if (stingrayLogsToAppend.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                AlertLogStore.appendAll(context, stingrayLogsToAppend)
            }
            alertLogs = AlertLogStore.read(context)
        }

        val staleKeys = cellThreatStateByDevice.keys.filter { it !in seenKeys }
        staleKeys.forEach { staleKey ->
            cellThreatStateByDevice.remove(staleKey)
            lastStingrayNotificationEpochByDevice.remove(staleKey)
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

    LaunchedEffect(analyzedDevices, cameraInViewNotificationsEnabled) {
        if (!cameraInViewNotificationsEnabled) {
            cameraInViewStateByDevice.clear()
            lastCameraInViewNotificationEpochByDevice.clear()
            return@LaunchedEffect
        }

        val observerLocation = LocationSnapshotProvider.read(context)
        if (observerLocation == null) {
            cameraInViewStateByDevice.clear()
            return@LaunchedEffect
        }

        val now = System.currentTimeMillis()
        val seenKeys = mutableSetOf<String>()
        val cameraLogsToAppend = mutableListOf<AlertLogEntry>()

        analyzedDevices
            .asSequence()
            .filter { device -> device.source == EncounterSource.CAMERA.name }
            .forEach { device ->
                val key = "${device.source}|${device.primaryId}"
                seenKeys += key
                val distanceMeters = distanceFromLocationMeters(
                    fromLat = observerLocation.lat,
                    fromLon = observerLocation.lon,
                    toLat = device.lastLat,
                    toLon = device.lastLon
                )
                val isInView = distanceMeters != null && distanceMeters <= CAMERA_IN_VIEW_DISTANCE_THRESHOLD_METERS
                val wasInView = cameraInViewStateByDevice[key] ?: false

                if (isInView && !wasInView) {
                    val sourceLabel = listSourceLabel(device.source, device.secondaryId)
                    val rangeLabel = formatDistanceFeetMiles(distanceMeters!!)
                    cameraLogsToAppend += AlertLogEntry(
                        timestampEpochMs = now,
                        type = AlertLogType.CAMERA_IN_VIEW,
                        source = device.source,
                        primaryId = device.primaryId,
                        message = "In view of camera $sourceLabel ${device.primaryId} ($rangeLabel)",
                        confidence = null
                    )

                    val lastNotified = lastCameraInViewNotificationEpochByDevice[key] ?: 0L
                    if (hasPostNotificationsPermission(context) && now - lastNotified >= CAMERA_IN_VIEW_ALERT_COOLDOWN_MS) {
                        ensureCameraInViewNotificationChannel(context)
                        sendCameraInViewNotification(
                            context = context,
                            device = device,
                            distanceMeters = distanceMeters
                        )
                        lastCameraInViewNotificationEpochByDevice[key] = now
                    }
                }

                cameraInViewStateByDevice[key] = isInView
            }

        if (cameraLogsToAppend.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                AlertLogStore.appendAll(context, cameraLogsToAppend)
            }
            alertLogs = AlertLogStore.read(context)
        }

        val staleKeys = cameraInViewStateByDevice.keys.filter { staleKey -> staleKey !in seenKeys }
        staleKeys.forEach { staleKey ->
            cameraInViewStateByDevice.remove(staleKey)
            lastCameraInViewNotificationEpochByDevice.remove(staleKey)
        }
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
                if (!inPictureInPictureMode && currentRoute in topLevelRoutes) {
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
                    .padding(if (inPictureInPictureMode) PaddingValues(0.dp) else padding)
            ) {
            globalMagneticDetectionPopup?.let { popup ->
                val popupLiveCurrentMagnitudeMicroTesla =
                    globalLiveMagneticMagnitudeMicroTesla ?: popup.currentMagnitudeMicroTesla
                MagneticDetectionPopupDialog(
                    popup = popup,
                    liveCurrentMagnitudeMicroTesla = popupLiveCurrentMagnitudeMicroTesla,
                    onDismiss = { globalMagneticDetectionPopup = null },
                    confirmLabel = "Open Detection",
                    onConfirm = {
                        globalMagneticDetectionPopup = null
                        detectionInitialMagneticPopupRequest = System.currentTimeMillis()
                        navigateTopLevel(DETECTION_ROUTE)
                    }
                )
            }
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
                    .padding(if (inPictureInPictureMode) 0.dp else 16.dp)
            ) {
            composable(HOME_ROUTE) {
                HomePage(
                    trackingActive = trackingActive,
                    nowEpochMs = appNowEpochMs,
                    fullEncryptionEnabled = fullEncryptionEnabled,
                    fullEncryptionUnlockedInSession = fullEncryptionSessionUnlocked,
                    fullEncryptionAutoLockTimeoutSeconds = fullEncryptionAutoLockTimeoutSeconds,
                    fullEncryptionPinLockoutUntilEpochMs = fullEncryptionPinLockoutUntilEpochMs,
                    fullEncryptionPinWipeEnabled = fullEncryptionPinWipeEnabled,
                    lastScanEpochMs = lastScanEpochMs,
                    scanIntervalSeconds = scanIntervalSeconds,
                    sourceScanTimings = sourceScanTimings,
                    sourceScanIntervals = sourceScanIntervals,
                    sourceLastScanEpochs = sourceLastScanEpochs,
                    sensorStatuses = sensorStatuses,
                    sensorGateSettings = sensorGateSettings,
                    summary = summary,
                    homePoint = homePoint,
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
                        viewModel?.refreshSummary()
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
                        viewModel?.refreshSummary()
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
                    onOpenDevicesEncounters = {
                        navController.navigate(LOGS_ENCOUNTERS_ROUTE) {
                            launchSingleTop = true
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
                    mapScannerSweepAnimationSpeedPreset = mapScannerSweepAnimationSpeedPreset,
                    mapIdentityFullNamesEnabled = mapIdentityFullNamesEnabled,
                    stickyCompassMapEnabled = stickyCompassMapEnabled,
                    stickyCompassMapLiveMode = stickyCompassMapLiveMode,
                    wifiRandomizedOneOffSuppressionEnabled = wifiRandomizedOneOffSuppressionEnabled,
                    wifiAggregateOnlyEnabled = wifiAggregateOnlyEnabled,
                    bleRandomizedOneOffSuppressionEnabled = bleRandomizedOneOffSuppressionEnabled,
                    bleAggregateOnlyEnabled = bleAggregateOnlyEnabled,
                    suppressedWifiRandomizedOneOffCount = suppressedWifiRandomizedOneOffCount,
                    suppressedBleRandomizedOneOffCount = suppressedBleRandomizedOneOffCount,
                    ownedDeviceCount = ownedDeviceKeys.size,
                    ownedDeviceEstimatedBytes = estimateOwnedDeviceKeySetBytes(ownedDeviceKeys),
                    recentEncounterCount = recent.size,
                    recentEncounterEstimatedBytes = recent.sumOf { estimateEncounterHeapBytes(it) },
                    alertLogCount = alertLogs.size,
                    alertLogEstimatedBytes = estimateAlertLogListBytes(alertLogs),
                    errorLogCount = errorLogs.size,
                    errorLogEstimatedBytes = estimateOperationalErrorLogListBytes(errorLogs),
                    sourceScanIntervals = sourceScanIntervals,
                    sourceLastScanEpochs = sourceLastScanEpochs,
                    lastScanDurationMs = lastScanDurationMs,
                    sourceScanTimings = sourceScanTimings,
                    sourceLastRawObservationEpochs = sourceLastRawObservationEpochs,
                    scanIntervalChangeEvents = scanIntervalChangeEvents,
                    appThemeMode = appThemeMode,
                    approachDetectionEnabled = approachDetectionEnabled,
                    liveModeOnlyEnabled = liveModeOnlyEnabled,
                    approachNotificationsEnabled = approachNotificationsEnabled,
                    trackerNotificationsEnabled = trackerNotificationsEnabled,
                    flockNotificationsEnabled = flockNotificationsEnabled,
                    cameraInViewNotificationsEnabled = cameraInViewNotificationsEnabled,
                    noFlyPassThroughNotificationsEnabled = noFlyPassThroughNotificationsEnabled,
                    nfcNotificationsEnabled = nfcNotificationsEnabled,
                    stingrayNotificationsEnabled = stingrayNotificationsEnabled,
                    magneticIncreaseNotificationsEnabled = magneticIncreaseNotificationsEnabled,
                    magneticRhythmBeepEnabled = magneticRhythmBeepEnabled,
                    meshConnectivityNotificationsEnabled = meshConnectivityNotificationsEnabled,
                    meshWipeNotificationsEnabled = meshWipeNotificationsEnabled,
                    foreignDirectAcousticEnabled = foreignDirectAcousticEnabled,
                    foreignDirectMagneticEnabled = foreignDirectMagneticEnabled,
                    fullEncryptionEnabled = fullEncryptionEnabled,
                    fullEncryptionUnlockMethod = fullEncryptionUnlockMethod,
                    fullEncryptionUnlockedInSession = fullEncryptionSessionUnlocked,
                    fullEncryptionBiometricAvailable = AppEncryptionManager.canUseBiometricOrDeviceCredential(context),
                    fullEncryptionAutoLockTimeoutSeconds = fullEncryptionAutoLockTimeoutSeconds,
                    fullEncryptionPinWipeEnabled = fullEncryptionPinWipeEnabled,
                    fullEncryptionPinFailedAttempts = fullEncryptionPinFailedAttempts,
                    fullEncryptionPinLockoutUntilEpochMs = fullEncryptionPinLockoutUntilEpochMs,
                    fullEncryptionLastWipeEpochMs = fullEncryptionLastWipeEpochMs,
                    fullEncryptionWrapRotationCount = fullEncryptionWrapRotationCount,
                    fullEncryptionWrapLastRotationEpochMs = fullEncryptionWrapLastRotationEpochMs,
                    fullEncryptionMigrationTelemetry = fullEncryptionMigrationTelemetry,
                    onSourceScanIntervalSelected = { sourceType, seconds ->
                        val previous = sourceScanIntervals[sourceType]
                            ?: ScanSettings.DEFAULT_SOURCE_SCAN_INTERVAL_SECONDS
                        val updated = if (sourceType == SourceCatalog.KEY_MAGNETIC) {
                            seconds.coerceIn(
                                ScanSettings.MAGNETIC_SOURCE_SCAN_INTERVAL_REALTIME_SECONDS,
                                ScanSettings.MAX_SOURCE_SCAN_INTERVAL_SECONDS
                            )
                        } else {
                            seconds.coerceIn(
                                ScanSettings.MIN_SOURCE_SCAN_INTERVAL_SECONDS,
                                ScanSettings.MAX_SOURCE_SCAN_INTERVAL_SECONDS
                            )
                        }
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
                    onLiveModeOnlyChanged = { enabled ->
                        liveModeOnlyEnabled = enabled
                        ScanSettings.setLiveModeOnlyEnabled(context, enabled)
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
                    onCameraInViewNotificationsChanged = { enabled ->
                        cameraInViewNotificationsEnabled = enabled
                        ScanSettings.setCameraInViewNotificationsEnabled(context, enabled)
                    },
                    onNoFlyPassThroughNotificationsChanged = { enabled ->
                        noFlyPassThroughNotificationsEnabled = enabled
                        ScanSettings.setNoFlyPassThroughNotificationsEnabled(context, enabled)
                    },
                    onNfcNotificationsChanged = { enabled ->
                        nfcNotificationsEnabled = enabled
                        ScanSettings.setNfcNotificationsEnabled(context, enabled)
                    },
                    onStingrayNotificationsChanged = { enabled ->
                        stingrayNotificationsEnabled = enabled
                        ScanSettings.setStingrayNotificationsEnabled(context, enabled)
                    },
                    onMagneticIncreaseNotificationsChanged = { enabled ->
                        magneticIncreaseNotificationsEnabled = enabled
                        ScanSettings.setMagneticIncreaseNotificationsEnabled(context, enabled)
                    },
                    onMagneticRhythmBeepEnabledChanged = { enabled ->
                        magneticRhythmBeepEnabled = enabled
                        ScanSettings.setMagneticRhythmBeepEnabled(context, enabled)
                        if (enabled) {
                            scope.launch(Dispatchers.Default) {
                                playMagneticRhythm(severity = 0.35, playMs = 900L)
                            }
                        }
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
                    onMapScannerSweepAnimationSpeedPresetSelected = { preset ->
                        mapScannerSweepAnimationSpeedPreset = preset
                        ScanSettings.setMapScannerSweepAnimationSpeedPreset(context, preset)
                    },
                    onMapIdentityFullNamesEnabledChanged = { enabled ->
                        mapIdentityFullNamesEnabled = enabled
                        ScanSettings.setMapIdentityFullNamesEnabled(context, enabled)
                    },
                    onStickyCompassMapEnabledChanged = { enabled ->
                        stickyCompassMapEnabled = enabled
                        ScanSettings.setStickyCompassMapEnabled(context, enabled)
                        if (!enabled) {
                            ScanSettings.setStickyCompassMapPipEligible(context, false)
                        }
                    },
                    onStickyCompassMapLiveModeSelected = { mode ->
                        stickyCompassMapLiveMode = mode
                        ScanSettings.setStickyCompassMapLiveMode(context, mode)
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
                    onEnableFullEncryptionBiometric = {
                        if (!AppEncryptionManager.canUseBiometricOrDeviceCredential(context)) {
                            "Biometric or device credential auth is unavailable on this device."
                        } else {
                            AppEncryptionManager.enableLaunchLockWithBiometric(context)
                            fullEncryptionEnabled = true
                            fullEncryptionUnlockMethod = ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_BIOMETRIC
                            fullEncryptionSessionUnlocked = true
                            fullEncryptionUnlockError = null
                            refreshEncryptionPolicyState()
                            "Full encryption launch lock enabled with biometric/device credential."
                        }
                    },
                    onEnableFullEncryptionPin = { pin ->
                        AppEncryptionManager.enableLaunchLockWithPin(context, pin)
                        fullEncryptionEnabled = true
                        fullEncryptionUnlockMethod = ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_PIN
                        fullEncryptionSessionUnlocked = true
                        fullEncryptionUnlockError = null
                        refreshEncryptionPolicyState()
                        "Full encryption launch lock enabled with PIN."
                    },
                    onEnableFullEncryptionPassword = { password ->
                        AppEncryptionManager.enableLaunchLockWithPassword(context, password)
                        fullEncryptionEnabled = true
                        fullEncryptionUnlockMethod = ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_PASSWORD
                        fullEncryptionSessionUnlocked = true
                        fullEncryptionUnlockError = null
                        refreshEncryptionPolicyState()
                        "Full encryption launch lock enabled with password."
                    },
                    onDisableFullEncryptionLaunchLock = {
                        AppEncryptionManager.disableLaunchLock(context)
                        fullEncryptionEnabled = false
                        fullEncryptionSessionUnlocked = true
                        fullEncryptionUnlockError = null
                        refreshEncryptionPolicyState()
                        "Launch lock disabled. Data remains encrypted at rest."
                    },
                    onFullEncryptionAutoLockTimeoutSecondsChanged = { seconds ->
                        ScanSettings.setFullEncryptionAutoLockTimeoutSeconds(context, seconds)
                        fullEncryptionAutoLockTimeoutSeconds = ScanSettings.getFullEncryptionAutoLockTimeoutSeconds(context)
                    },
                    onFullEncryptionPinWipeEnabledChanged = { enabled ->
                        ScanSettings.setFullEncryptionPinWipeEnabled(context, enabled)
                        fullEncryptionPinWipeEnabled = ScanSettings.isFullEncryptionPinWipeEnabled(context)
                    },
                    onRotateFullEncryptionWrapping = { maybePin ->
                        val rotated = AppEncryptionManager.rotateWrappingMaterial(
                            context = context,
                            pinForPinMode = maybePin
                        )
                        refreshEncryptionPolicyState()
                        if (rotated) {
                            "Encryption wrap material rotated."
                        } else {
                            "Wrap rotation failed. Unlock first, and provide PIN/password when using credential launch lock."
                        }
                    },
                    onLockFullEncryptionSessionNow = {
                        AppEncryptionManager.forceLockNow(context)
                        fullEncryptionSessionUnlocked = AppEncryptionManager.isSessionUnlocked()
                        refreshEncryptionPolicyState()
                        "Session locked. Unlock required to continue."
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
                        mapScannerSweepAnimationSpeedPreset = ScanSettings.getMapScannerSweepAnimationSpeedPreset(context)
                        mapIdentityFullNamesEnabled = ScanSettings.isMapIdentityFullNamesEnabled(context)
                        stickyCompassMapEnabled = ScanSettings.isStickyCompassMapEnabled(context)
                        stickyCompassMapLiveMode = ScanSettings.getStickyCompassMapLiveMode(context)
                        wifiRandomizedOneOffSuppressionEnabled = ScanSettings.isWifiRandomizedOneOffSuppressionEnabled(context)
                        wifiAggregateOnlyEnabled = ScanSettings.isWifiAggregateOnlyEnabled(context)
                        bleRandomizedOneOffSuppressionEnabled = ScanSettings.isBleRandomizedOneOffSuppressionEnabled(context)
                        bleAggregateOnlyEnabled = ScanSettings.isBleAggregateOnlyEnabled(context)
                        liveModeOnlyEnabled = ScanSettings.isLiveModeOnlyEnabled(context)
                        sourceScanIntervals = ScanSettings.getAllSourceScanIntervalSeconds(context)
                        sourceLastScanEpochs = ScanSettings.getAllSourceLastScanEpochMs(context)
                        sourceLastRawObservationEpochs = ScanSettings.getAllSourceLastRawObservationEpochMs(context)
                        appThemeMode = runCatching { AppThemeMode.valueOf(ScanSettings.getAppThemeMode(context)) }
                            .getOrDefault(AppThemeMode.DARK)
                        approachNotificationsEnabled = ScanSettings.isApproachNotificationsEnabled(context)
                        trackerNotificationsEnabled = ScanSettings.isTrackerNotificationsEnabled(context)
                        flockNotificationsEnabled = ScanSettings.isFlockNotificationsEnabled(context)
                        cameraInViewNotificationsEnabled = ScanSettings.isCameraInViewNotificationsEnabled(context)
                        noFlyPassThroughNotificationsEnabled = ScanSettings.isNoFlyPassThroughNotificationsEnabled(context)
                        nfcNotificationsEnabled = ScanSettings.isNfcNotificationsEnabled(context)
                        stingrayNotificationsEnabled = ScanSettings.isStingrayNotificationsEnabled(context)
                        magneticIncreaseNotificationsEnabled = ScanSettings.isMagneticIncreaseNotificationsEnabled(context)
                        magneticRhythmBeepEnabled = ScanSettings.isMagneticRhythmBeepEnabled(context)
                        magneticEventTriggerThresholdMicroTesla =
                            ScanSettings.getMagneticEventTriggerThresholdMicroTesla(context)
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
                        refreshEncryptionPolicyState()
                        fullEncryptionSessionUnlocked = !fullEncryptionEnabled || AppEncryptionManager.isSessionUnlocked()
                        MeshForegroundServiceController.ensureState(context)
                        viewModel?.refreshSummary()
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
                        mapScannerSweepAnimationSpeedPreset = ScanSettings.getMapScannerSweepAnimationSpeedPreset(context)
                        mapIdentityFullNamesEnabled = ScanSettings.isMapIdentityFullNamesEnabled(context)
                        stickyCompassMapEnabled = ScanSettings.isStickyCompassMapEnabled(context)
                        stickyCompassMapLiveMode = ScanSettings.getStickyCompassMapLiveMode(context)
                        wifiRandomizedOneOffSuppressionEnabled = ScanSettings.isWifiRandomizedOneOffSuppressionEnabled(context)
                        wifiAggregateOnlyEnabled = ScanSettings.isWifiAggregateOnlyEnabled(context)
                        bleRandomizedOneOffSuppressionEnabled = ScanSettings.isBleRandomizedOneOffSuppressionEnabled(context)
                        bleAggregateOnlyEnabled = ScanSettings.isBleAggregateOnlyEnabled(context)
                        liveModeOnlyEnabled = ScanSettings.isLiveModeOnlyEnabled(context)
                        sourceScanIntervals = ScanSettings.getAllSourceScanIntervalSeconds(context)
                        sourceLastScanEpochs = ScanSettings.getAllSourceLastScanEpochMs(context)
                        sourceLastRawObservationEpochs = ScanSettings.getAllSourceLastRawObservationEpochMs(context)
                        appThemeMode = runCatching { AppThemeMode.valueOf(ScanSettings.getAppThemeMode(context)) }
                            .getOrDefault(AppThemeMode.DARK)
                        approachNotificationsEnabled = ScanSettings.isApproachNotificationsEnabled(context)
                        trackerNotificationsEnabled = ScanSettings.isTrackerNotificationsEnabled(context)
                        flockNotificationsEnabled = ScanSettings.isFlockNotificationsEnabled(context)
                        cameraInViewNotificationsEnabled = ScanSettings.isCameraInViewNotificationsEnabled(context)
                        noFlyPassThroughNotificationsEnabled = ScanSettings.isNoFlyPassThroughNotificationsEnabled(context)
                        nfcNotificationsEnabled = ScanSettings.isNfcNotificationsEnabled(context)
                        stingrayNotificationsEnabled = ScanSettings.isStingrayNotificationsEnabled(context)
                        magneticIncreaseNotificationsEnabled = ScanSettings.isMagneticIncreaseNotificationsEnabled(context)
                        magneticRhythmBeepEnabled = ScanSettings.isMagneticRhythmBeepEnabled(context)
                        magneticEventTriggerThresholdMicroTesla =
                            ScanSettings.getMagneticEventTriggerThresholdMicroTesla(context)
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
                        refreshEncryptionPolicyState()
                        fullEncryptionSessionUnlocked = !fullEncryptionEnabled || AppEncryptionManager.isSessionUnlocked()
                        MeshForegroundServiceController.ensureState(context)
                        viewModel?.refreshSummary()
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
                        ScanSettings.setMapScannerSweepAnimationSpeedPreset(
                            context,
                            ScanSettings.DEFAULT_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESET
                        )
                        ScanSettings.setMapIdentityFullNamesEnabled(
                            context,
                            ScanSettings.DEFAULT_MAP_IDENTITY_FULL_NAMES_ENABLED
                        )
                        ScanSettings.setStickyCompassMapEnabled(
                            context,
                            ScanSettings.DEFAULT_STICKY_COMPASS_MAP_ENABLED
                        )
                        ScanSettings.setStickyCompassMapLiveMode(
                            context,
                            ScanSettings.DEFAULT_STICKY_COMPASS_MAP_LIVE_MODE
                        )
                        ScanSettings.setStickyCompassMapPipEligible(context, false)
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
                        ScanSettings.setLiveModeOnlyEnabled(context, false)
                        ScanSettings.setAppThemeMode(context, ScanSettings.DEFAULT_APP_THEME_MODE)
                        ScanSettings.setApproachDetectionEnabled(context, true)
                        ScanSettings.setApproachNotificationsEnabled(context, false)
                        ScanSettings.setTrackerNotificationsEnabled(context, true)
                        ScanSettings.setFlockNotificationsEnabled(context, true)
                        ScanSettings.setCameraInViewNotificationsEnabled(context, true)
                        ScanSettings.setNoFlyPassThroughNotificationsEnabled(context, true)
                        ScanSettings.setNfcNotificationsEnabled(context, true)
                        ScanSettings.setStingrayNotificationsEnabled(context, true)
                        ScanSettings.setMagneticIncreaseNotificationsEnabled(context, true)
                        ScanSettings.setMagneticRhythmBeepEnabled(context, true)
                        ScanSettings.setMagneticEventTriggerThresholdMicroTesla(
                            context,
                            ScanSettings.DEFAULT_MAGNETIC_EVENT_TRIGGER_THRESHOLD_UT
                        )
                        ScanSettings.setMeshConnectivityNotificationsEnabled(context, false)
                        ScanSettings.setMeshWipeNotificationsEnabled(context, true)
                        ScanSettings.setForeignDirectAcousticEnabled(context, false)
                        ScanSettings.setForeignDirectMagneticEnabled(context, false)
                        ScanSettings.clearHomePoint(context)

                        intervalManagedSourceTypes.forEach { sourceType ->
                            val defaultSeconds = when (sourceType) {
                                SourceCatalog.KEY_AIRCRAFT -> ScanSettings.DEFAULT_AIRCRAFT_SOURCE_SCAN_INTERVAL_SECONDS
                                SourceCatalog.KEY_CAMERA -> ScanSettings.DEFAULT_CAMERA_SOURCE_SCAN_INTERVAL_SECONDS
                                SourceCatalog.KEY_MAGNETIC -> ScanSettings.DEFAULT_MAGNETIC_SOURCE_SCAN_INTERVAL_SECONDS
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
                        mapScannerSweepAnimationSpeedPreset = ScanSettings.DEFAULT_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESET
                        mapIdentityFullNamesEnabled = ScanSettings.DEFAULT_MAP_IDENTITY_FULL_NAMES_ENABLED
                        stickyCompassMapEnabled = ScanSettings.DEFAULT_STICKY_COMPASS_MAP_ENABLED
                        stickyCompassMapLiveMode = ScanSettings.DEFAULT_STICKY_COMPASS_MAP_LIVE_MODE
                        wifiRandomizedOneOffSuppressionEnabled = ScanSettings.DEFAULT_WIFI_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED
                        wifiAggregateOnlyEnabled = ScanSettings.DEFAULT_WIFI_AGGREGATE_ONLY_ENABLED
                        bleRandomizedOneOffSuppressionEnabled = ScanSettings.DEFAULT_BLE_RANDOMIZED_ONE_OFF_SUPPRESSION_ENABLED
                        bleAggregateOnlyEnabled = ScanSettings.DEFAULT_BLE_AGGREGATE_ONLY_ENABLED
                        liveModeOnlyEnabled = false
                        sourceScanIntervals = ScanSettings.getAllSourceScanIntervalSeconds(context)
                        appThemeMode = runCatching { AppThemeMode.valueOf(ScanSettings.DEFAULT_APP_THEME_MODE) }
                            .getOrDefault(AppThemeMode.DARK)
                        approachDetectionEnabled = true
                        approachNotificationsEnabled = false
                        trackerNotificationsEnabled = true
                        flockNotificationsEnabled = true
                        cameraInViewNotificationsEnabled = true
                        noFlyPassThroughNotificationsEnabled = true
                        nfcNotificationsEnabled = true
                        stingrayNotificationsEnabled = true
                        magneticIncreaseNotificationsEnabled = true
                        magneticRhythmBeepEnabled = true
                        magneticEventTriggerThresholdMicroTesla =
                            ScanSettings.DEFAULT_MAGNETIC_EVENT_TRIGGER_THRESHOLD_UT
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
                        DeviceMapPinRuntimeCache.clear()
                        DeviceMapPinDiskCache.clear(context)
                        RuntimeUiListMemoryGauge.reset()
                        approachStateByDevice.clear()
                        lastApproachNotificationEpochByDevice.clear()
                        trackerStateByDevice.clear()
                        lastTrackerNotificationEpochByDevice.clear()
                        cellThreatStateByDevice.clear()
                        lastStingrayNotificationEpochByDevice.clear()
                        lastFlockNotificationEpochBySignature.clear()
                        cameraInViewStateByDevice.clear()
                        lastCameraInViewNotificationEpochByDevice.clear()
                        noFlyZoneStateByTrack.clear()
                        lastNoFlyZoneNotificationEpochByTrackZone.clear()
                        noFlyZoneDetectionCache.clear()
                        analyzedDevices = emptyList()
                        releaseMapUiMemory(
                            context = context,
                            level = ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
                        )
                        startupPrewarmedDevicePins = emptyList()
                        startupPrewarmedNoFlyZones = emptyList()
                        mapResetGeneration += 1L
                        alertLogs = emptyList()
                        errorLogs = emptyList()
                        scanIntervalChangeEvents = emptyList()
                        viewModel?.refreshSummary()
                        "Soft reset completed: local encounters/devices/logs cleared."
                    },
                    onHardReset = {
                        app.container.repository.clearEncounters()
                        app.container.repository.clearDevices()
                        ScanSettings.clearOperationalLogs(context)
                        ScanSettings.resetMeshNetworkSettings(context)
                        DeviceMapPinRuntimeCache.clear()
                        DeviceMapPinDiskCache.clear(context)
                        RuntimeUiListMemoryGauge.reset()
                        approachStateByDevice.clear()
                        lastApproachNotificationEpochByDevice.clear()
                        trackerStateByDevice.clear()
                        lastTrackerNotificationEpochByDevice.clear()
                        cellThreatStateByDevice.clear()
                        lastStingrayNotificationEpochByDevice.clear()
                        lastFlockNotificationEpochBySignature.clear()
                        cameraInViewStateByDevice.clear()
                        lastCameraInViewNotificationEpochByDevice.clear()
                        noFlyZoneStateByTrack.clear()
                        lastNoFlyZoneNotificationEpochByTrackZone.clear()
                        noFlyZoneDetectionCache.clear()
                        analyzedDevices = emptyList()
                        releaseMapUiMemory(
                            context = context,
                            level = ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                        )
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
                        cameraInViewNotificationsEnabled = ScanSettings.isCameraInViewNotificationsEnabled(context)
                        noFlyPassThroughNotificationsEnabled = ScanSettings.isNoFlyPassThroughNotificationsEnabled(context)
                        nfcNotificationsEnabled = ScanSettings.isNfcNotificationsEnabled(context)
                        stingrayNotificationsEnabled = ScanSettings.isStingrayNotificationsEnabled(context)
                        magneticIncreaseNotificationsEnabled = ScanSettings.isMagneticIncreaseNotificationsEnabled(context)
                        magneticRhythmBeepEnabled = ScanSettings.isMagneticRhythmBeepEnabled(context)
                        magneticEventTriggerThresholdMicroTesla =
                            ScanSettings.getMagneticEventTriggerThresholdMicroTesla(context)
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
                        mapScannerSweepAnimationSpeedPreset = ScanSettings.getMapScannerSweepAnimationSpeedPreset(context)
                        mapIdentityFullNamesEnabled = ScanSettings.isMapIdentityFullNamesEnabled(context)
                        stickyCompassMapEnabled = ScanSettings.isStickyCompassMapEnabled(context)
                        stickyCompassMapLiveMode = ScanSettings.getStickyCompassMapLiveMode(context)
                        wifiRandomizedOneOffSuppressionEnabled = ScanSettings.isWifiRandomizedOneOffSuppressionEnabled(context)
                        wifiAggregateOnlyEnabled = ScanSettings.isWifiAggregateOnlyEnabled(context)
                        bleRandomizedOneOffSuppressionEnabled = ScanSettings.isBleRandomizedOneOffSuppressionEnabled(context)
                        bleAggregateOnlyEnabled = ScanSettings.isBleAggregateOnlyEnabled(context)
                        liveModeOnlyEnabled = ScanSettings.isLiveModeOnlyEnabled(context)
                        viewModel?.refreshSummary()
                        "Hard reset completed: local data/logs cleared and mesh settings reset."
                    }
                )
            }

            composable(DETECTION_ROUTE) {
                DetectionPage(
                    initialTabRequest = detectionInitialTabRequest,
                    onInitialTabRequestHandled = { detectionInitialTabRequest = null },
                    initialMagneticPopupRequest = detectionInitialMagneticPopupRequest,
                    onInitialMagneticPopupRequestHandled = { detectionInitialMagneticPopupRequest = null },
                    readinessItems = readinessItems,
                    meshInsightEncounters = allPipelineEncounters,
                    directMagneticChannelEnabled = foreignDirectMagneticEnabled,
                    magneticRhythmBeepEnabled = magneticRhythmBeepEnabled,
                    magneticIncreaseNotificationsEnabled = magneticIncreaseNotificationsEnabled,
                    magneticEventTriggerThresholdMicroTesla = magneticEventTriggerThresholdMicroTesla,
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
                    mapScannerSweepAnimationSpeedPreset = mapScannerSweepAnimationSpeedPreset,
                    mapIdentityFullNamesEnabled = mapIdentityFullNamesEnabled,
                    onMapIdentityFullNamesEnabledChanged = { enabled ->
                        mapIdentityFullNamesEnabled = enabled
                        ScanSettings.setMapIdentityFullNamesEnabled(context, enabled)
                    },
                    stickyCompassMapEnabled = stickyCompassMapEnabled,
                    stickyCompassMapLiveMode = stickyCompassMapLiveMode,
                    liveModeOnlyEnabled = liveModeOnlyEnabled,
                    mapOnlyMode = inPictureInPictureMode,
                    pipZoomCommandNonce = pipZoomCommandNonce,
                    pipZoomCommandDelta = pipZoomCommandDelta,
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
                    onOpenReadinessSetting = { item ->
                        runCatching {
                            context.startActivity(item.settingsIntent)
                        }.recoverCatching {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_SETTINGS)
                            )
                        }
                    },
                    onDirectMagneticChannelEnabledChanged = { enabled ->
                        foreignDirectMagneticEnabled = enabled
                        ScanSettings.setForeignDirectMagneticEnabled(context, enabled)
                        sensorGateSettings = readSensorGateSettings(context)
                        scope.launch {
                            alignWorkerCadenceWithSources(reasonCode = "scheduler-align-direct-magnetic")
                        }
                    },
                    onMagneticRhythmBeepEnabledChanged = { enabled ->
                        magneticRhythmBeepEnabled = enabled
                        ScanSettings.setMagneticRhythmBeepEnabled(context, enabled)
                    },
                    onMagneticEventTriggerThresholdChanged = { thresholdMicroTesla ->
                        magneticEventTriggerThresholdMicroTesla = thresholdMicroTesla
                        ScanSettings.setMagneticEventTriggerThresholdMicroTesla(context, thresholdMicroTesla)
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
                                delay(250.milliseconds)
                                viewModel?.refreshSummary()
                                if (wipeDeferred.isCompleted) return@repeat
                            }

                            val wipe = wipeDeferred.await()
                            viewModel?.refreshSummary()
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
                val computedDetail by produceState<DeviceDetailComputed?>(
                    null,
                    allPipelineEncounters,
                    source,
                    primaryId,
                    approachDetectionEnabled,
                    ownedDeviceKeys,
                    wifiRandomizedOneOffSuppressionEnabled,
                    bleRandomizedOneOffSuppressionEnabled
                ) {
                    value = withContext(Dispatchers.Default) {
                        val limitedDeviceEncounters = ArrayList<Encounter>(DETECTION_LIST_PAGE_SIZE)
                        var deviceEncounterTotal = 0
                        allPipelineEncounters.forEach { encounter ->
                            if (encounter.source.name != source || encounter.primaryId != primaryId) {
                                return@forEach
                            }
                            deviceEncounterTotal += 1
                            if (limitedDeviceEncounters.size < DETECTION_LIST_PAGE_SIZE) {
                                limitedDeviceEncounters += encounter
                            }
                        }
                        val item = buildSingleDeviceItem(
                            source = source,
                            primaryId = primaryId,
                            groupedEncounters = limitedDeviceEncounters,
                            approachDetectionEnabled = approachDetectionEnabled,
                            ownedDeviceKeys = ownedDeviceKeys,
                            suppressLikelyRandomizedWifiOneOffs = wifiRandomizedOneOffSuppressionEnabled,
                            suppressLikelyRandomizedBleOneOffs = bleRandomizedOneOffSuppressionEnabled
                        )?.copy(seenCount = deviceEncounterTotal)
                        DeviceDetailComputed(
                            deviceEncounters = limitedDeviceEncounters,
                            item = item
                        )
                    }
                }

                val detail = computedDetail
                if (detail == null) {
                    DeviceDetailLoadingPage(onBack = { navController.popBackStack() })
                } else {
                    DeviceDetailPage(
                        item = detail.item,
                        deviceEncounters = detail.deviceEncounters,
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
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(onClick = {})
                ) {
                    Column(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.Center)
                            .padding(20.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val reauthOnlyGate = startupRuntimeGateReleased && !encryptionGateSatisfied
                        val startupChecks = listOf(
                            startupConfigLoaded,
                            trackingObserverInitialized,
                            operationalObserverInitialized,
                            mapPrewarmReady,
                            mapDataPrewarmReady,
                            startupBootstrapGateSatisfied,
                            encryptionGateSatisfied
                        )
                        val startupProgress = startupChecks.count { it }.toFloat() / startupChecks.size.toFloat()
                        val startupProgressPercent = (startupProgress * 100f).toInt().coerceIn(0, 100)

                        if (!reauthOnlyGate) {
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
                        }
                        Text(
                            if (appRuntimeReady && !startupRuntimeGateReleased) {
                                if (startupBootstrapWaitRequired) {
                                    "Waiting for startup scan to complete..."
                                } else {
                                    "Preparing Argus runtime..."
                                }
                            } else if (!encryptionGateSatisfied) {
                                "Authenticate to decrypt local data..."
                            } else {
                                "Preparing Argus runtime..."
                            }
                        )
                        if (!reauthOnlyGate) {
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
                                    append(" • Encryption: ")
                                    append(
                                        if (!fullEncryptionEnabled) {
                                            "disabled"
                                        } else if (fullEncryptionSessionUnlocked) {
                                            "unlocked"
                                        } else {
                                            "locked"
                                        }
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!encryptionGateSatisfied) {
                            val credentialUnlockMethod = when (fullEncryptionUnlockMethod) {
                                ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_PIN -> "PIN"
                                ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_PASSWORD -> "password"
                                else -> null
                            }
                            if (credentialUnlockMethod != null) {
                                val isPasswordMethod =
                                    fullEncryptionUnlockMethod == ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_PASSWORD
                                val minLength = if (isPasswordMethod) 8 else 6
                                val credentialValue = if (isPasswordMethod) {
                                    fullEncryptionPasswordEntry
                                } else {
                                    fullEncryptionPinEntry
                                }
                                val credentialLabel = if (isPasswordMethod) "Launch password" else "Launch PIN"
                                OutlinedTextField(
                                    value = credentialValue,
                                    onValueChange = { value ->
                                        if (isPasswordMethod) {
                                            fullEncryptionPasswordEntry = value
                                        } else {
                                            fullEncryptionPinEntry = value
                                        }
                                    },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    label = { Text(credentialLabel) },
                                    modifier = Modifier.widthIn(max = 360.dp)
                                )
                                Button(
                                    enabled = !fullEncryptionUnlockInProgress && credentialValue.trim().length >= minLength &&
                                        (fullEncryptionPinLockoutUntilEpochMs <= System.currentTimeMillis()),
                                    onClick = {
                                        fullEncryptionUnlockInProgress = true
                                        fullEncryptionUnlockError = null
                                        val unlocked = if (isPasswordMethod) {
                                            AppEncryptionManager.unlockWithPassword(
                                                context = context,
                                                password = fullEncryptionPasswordEntry.trim()
                                            )
                                        } else {
                                            AppEncryptionManager.unlockWithPin(
                                                context = context,
                                                pin = fullEncryptionPinEntry.trim()
                                            )
                                        }
                                        fullEncryptionSessionUnlocked = unlocked
                                        if (!unlocked) {
                                            val pinState = AppEncryptionManager.readPinUnlockState(context)
                                            fullEncryptionPinFailedAttempts = pinState.failedAttempts
                                            fullEncryptionPinLockoutUntilEpochMs = pinState.lockoutUntilEpochMs
                                            fullEncryptionLastWipeEpochMs = pinState.lastWipeEpochMs
                                            val credentialName = if (isPasswordMethod) "Password" else "PIN"
                                            val triesLeftSuffix = pinState.attemptsUntilWipe
                                                ?.let { triesLeft ->
                                                    " $triesLeft ${if (triesLeft == 1) "try" else "tries"} left before wipe."
                                                }
                                                .orEmpty()
                                            fullEncryptionUnlockError = when {
                                                pinState.lastWipeEpochMs != null ->
                                                    "Too many failed $credentialName attempts. Local encrypted data was wiped."
                                                pinState.remainingLockoutMs > 0L ->
                                                    "$credentialName locked. Try again in ${(pinState.remainingLockoutMs / 1000L).coerceAtLeast(1L)}s.$triesLeftSuffix"
                                                else -> "$credentialName unlock failed. Check $credentialName and try again.$triesLeftSuffix"
                                            }
                                        }
                                        refreshEncryptionPolicyState()
                                        fullEncryptionUnlockInProgress = false
                                    }
                                ) {
                                    Text(
                                        if (fullEncryptionUnlockInProgress) "Unlocking..."
                                        else if (isPasswordMethod) "Unlock with Password"
                                        else "Unlock with PIN"
                                    )
                                }
                            } else {
                                Button(
                                    enabled = !fullEncryptionUnlockInProgress,
                                    onClick = {
                                        val activity = hostActivity ?: run {
                                            fullEncryptionUnlockError = "Unable to open biometric prompt from current context."
                                            return@Button
                                        }
                                        scope.launch {
                                            fullEncryptionUnlockInProgress = true
                                            fullEncryptionUnlockError = null
                                            val authenticated = requestSecureLaunchAuthentication(activity)
                                            val unlocked = authenticated && AppEncryptionManager.unlockWithBiometric(context)
                                            fullEncryptionSessionUnlocked = unlocked
                                            if (!unlocked) {
                                                fullEncryptionUnlockError = "Unlock failed. Authenticate and try again."
                                            }
                                            refreshEncryptionPolicyState()
                                            fullEncryptionUnlockInProgress = false
                                        }
                                    }
                                ) {
                                    Text(if (fullEncryptionUnlockInProgress) "Unlocking..." else "Unlock Encrypted Data")
                                }
                            }
                            if (fullEncryptionPinLockoutUntilEpochMs > System.currentTimeMillis()) {
                                val credentialLabel = if (
                                    fullEncryptionUnlockMethod == ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_PASSWORD
                                ) {
                                    "Password"
                                } else {
                                    "PIN"
                                }
                                Text(
                                    "$credentialLabel unlock temporarily locked until ${formatEpoch(fullEncryptionPinLockoutUntilEpochMs)}",
                                    color = Color(0xFFB3261E)
                                )
                            }
                            if (!fullEncryptionUnlockError.isNullOrBlank()) {
                                Text(
                                    text = fullEncryptionUnlockError!!,
                                    color = Color(0xFFB3261E)
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
    onOpenDeviceDetails: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mapStyleOptions = rememberMapStyleOptionsForTheme()
    val liveMapUpdateIntervalSeconds = FIXED_LIVE_MAP_UPDATE_INTERVAL_SECONDS
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
    fullEncryptionEnabled: Boolean,
    fullEncryptionUnlockedInSession: Boolean,
    fullEncryptionAutoLockTimeoutSeconds: Long,
    fullEncryptionPinLockoutUntilEpochMs: Long,
    fullEncryptionPinWipeEnabled: Boolean,
    lastScanEpochMs: Long?,
    scanIntervalSeconds: Long,
    sourceScanTimings: List<ScanSettings.SourceScanTiming>,
    sourceScanIntervals: Map<String, Long>,
    sourceLastScanEpochs: Map<String, Long>,
    sensorStatuses: List<SensorStatus>,
    sensorGateSettings: SensorGateSettings,
    summary: List<SourceSummary>,
    homePoint: ScanSettings.HomePoint?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onSensorGateChanged: (String, Boolean) -> Unit,
    onOpenDevicesEncounters: () -> Unit,
    onSetHomePointFromCurrentLocation: () -> String,
    onSetHomePointRadiusMeters: (Double) -> String,
    onClearHomePoint: () -> String,
    startMessage: String?,
    startMessageIsError: Boolean
) {
    var homePointRadiusExpanded by remember { mutableStateOf(false) }
    var homePointStatusMessage by remember { mutableStateOf<String?>(null) }
    val homeLiveMagneticMagnitudeMicroTesla by rememberRealtimeMagneticMagnitudeMicroTesla(
        enabled = sensorGateSettings.directMagneticEnabled
    )
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
    val fullEncryptionPinLocked = fullEncryptionPinLockoutUntilEpochMs > nowEpochMs
    val securityBadgeLabel = when {
        !fullEncryptionEnabled -> "Security: Launch lock off"
        fullEncryptionPinLocked -> "Security: PIN lockout"
        fullEncryptionUnlockedInSession -> "Security: Unlocked"
        else -> "Security: Locked"
    }
    val securityBadgeColor = when {
        !fullEncryptionEnabled -> Color(0xFFE65100)
        fullEncryptionPinLocked -> Color(0xFFB3261E)
        fullEncryptionUnlockedInSession -> Color(0xFF2E7D32)
        else -> Color(0xFFB3261E)
    }
    val securityBadgeDetail = buildString {
        append("Auto-lock: ")
        append(if (fullEncryptionAutoLockTimeoutSeconds <= 0L) "disabled" else "${fullEncryptionAutoLockTimeoutSeconds}s")
        append(" | Wipe-on-fail: ")
        append(if (fullEncryptionPinWipeEnabled) "on" else "off")
        if (fullEncryptionPinLocked) {
            append(" | Locked until ")
            append(formatEpoch(fullEncryptionPinLockoutUntilEpochMs))
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("●", color = securityBadgeColor, fontWeight = FontWeight.Bold)
                    Column {
                        Text(securityBadgeLabel, color = securityBadgeColor, fontWeight = FontWeight.SemiBold)
                        Text(
                            securityBadgeDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("🧲 Magnetic (Live)", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = homeLiveMagneticMagnitudeMicroTesla?.let { value ->
                            String.format(Locale.US, "%.1f uT", value)
                        } ?: "n/a",
                        color = if (homeLiveMagneticMagnitudeMicroTesla == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        item {
            val wideActionLayout = LocalConfiguration.current.screenWidthDp.dp >= HOME_TWO_COLUMN_MIN_WIDTH
            Box(modifier = Modifier.fillMaxWidth()) {
                if (wideActionLayout) {
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
                val columnSpacing = 8.dp
                val targetCardMinWidth = 170.dp
                val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                val computedColumns = ((screenWidth + columnSpacing) / (targetCardMinWidth + columnSpacing))
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
                            "Bluetooth (LE + Remote ID)",
                            "Combined Bluetooth sensor collection",
                            sensorGateSettings.bluetoothLeEnabled,
                            sensorStatusByName["Bluetooth (LE + Remote ID)"]
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
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val columns = when {
        screenWidth >= threeColumnMinWidth -> 3
        screenWidth >= twoColumnMinWidth -> 2
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

private suspend fun requestSecureLaunchAuthentication(activity: FragmentActivity): Boolean =
    suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(false) { }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(true) { }
                }

                override fun onAuthenticationFailed() {
                    // Keep prompt active; explicit success or terminal error will resume continuation.
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Argus")
            .setSubtitle("Authenticate to decrypt local data")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        continuation.invokeOnCancellation {
            prompt.cancelAuthentication()
        }
        prompt.authenticate(promptInfo)
    }

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
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
    mapScannerSweepAnimationSpeedPreset: String,
    mapIdentityFullNamesEnabled: Boolean,
    stickyCompassMapEnabled: Boolean,
    stickyCompassMapLiveMode: String,
    wifiRandomizedOneOffSuppressionEnabled: Boolean,
    wifiAggregateOnlyEnabled: Boolean,
    bleRandomizedOneOffSuppressionEnabled: Boolean,
    bleAggregateOnlyEnabled: Boolean,
    suppressedWifiRandomizedOneOffCount: Int,
    suppressedBleRandomizedOneOffCount: Int,
    ownedDeviceCount: Int,
    ownedDeviceEstimatedBytes: Long,
    recentEncounterCount: Int,
    recentEncounterEstimatedBytes: Long,
    alertLogCount: Int,
    alertLogEstimatedBytes: Long,
    errorLogCount: Int,
    errorLogEstimatedBytes: Long,
    sourceScanIntervals: Map<String, Long>,
    sourceLastScanEpochs: Map<String, Long>,
    lastScanDurationMs: Long?,
    sourceScanTimings: List<ScanSettings.SourceScanTiming>,
    sourceLastRawObservationEpochs: Map<String, Long>,
    scanIntervalChangeEvents: List<ScanSettings.IntervalChangeEvent>,
    appThemeMode: AppThemeMode,
    approachDetectionEnabled: Boolean,
    liveModeOnlyEnabled: Boolean,
    approachNotificationsEnabled: Boolean,
    trackerNotificationsEnabled: Boolean,
    flockNotificationsEnabled: Boolean,
    cameraInViewNotificationsEnabled: Boolean,
    noFlyPassThroughNotificationsEnabled: Boolean,
    nfcNotificationsEnabled: Boolean,
    stingrayNotificationsEnabled: Boolean,
    magneticIncreaseNotificationsEnabled: Boolean,
    magneticRhythmBeepEnabled: Boolean,
    meshConnectivityNotificationsEnabled: Boolean,
    meshWipeNotificationsEnabled: Boolean,
    foreignDirectAcousticEnabled: Boolean,
    foreignDirectMagneticEnabled: Boolean,
    fullEncryptionEnabled: Boolean,
    fullEncryptionUnlockMethod: String,
    fullEncryptionUnlockedInSession: Boolean,
    fullEncryptionBiometricAvailable: Boolean,
    fullEncryptionAutoLockTimeoutSeconds: Long,
    fullEncryptionPinWipeEnabled: Boolean,
    fullEncryptionPinFailedAttempts: Int,
    fullEncryptionPinLockoutUntilEpochMs: Long,
    fullEncryptionLastWipeEpochMs: Long?,
    fullEncryptionWrapRotationCount: Int,
    fullEncryptionWrapLastRotationEpochMs: Long?,
    fullEncryptionMigrationTelemetry: List<SecureSettingsStore.MigrationTelemetry>,
    onSourceScanIntervalSelected: (String, Long) -> Unit,
    onAllSourceScanIntervalsSelected: (Long) -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onApproachDetectionChanged: (Boolean) -> Unit,
    onLiveModeOnlyChanged: (Boolean) -> Unit,
    onApproachNotificationsChanged: (Boolean) -> Unit,
    onTrackerNotificationsChanged: (Boolean) -> Unit,
    onFlockNotificationsChanged: (Boolean) -> Unit,
    onCameraInViewNotificationsChanged: (Boolean) -> Unit,
    onNoFlyPassThroughNotificationsChanged: (Boolean) -> Unit,
    onNfcNotificationsChanged: (Boolean) -> Unit,
    onStingrayNotificationsChanged: (Boolean) -> Unit,
    onMagneticIncreaseNotificationsChanged: (Boolean) -> Unit,
    onMagneticRhythmBeepEnabledChanged: (Boolean) -> Unit,
    onMeshConnectivityNotificationsChanged: (Boolean) -> Unit,
    onMeshWipeNotificationsChanged: (Boolean) -> Unit,
    onForeignDirectAcousticEnabledChanged: (Boolean) -> Unit,
    onForeignDirectMagneticEnabledChanged: (Boolean) -> Unit,
    onMapClusteringEnabledChanged: (Boolean) -> Unit,
    onMapClusterRangeLevelSelected: (Int) -> Unit,
    onMapTrafficEnabledChanged: (Boolean) -> Unit,
    onMapNoFlyZonesEnabledChanged: (Boolean) -> Unit,
    onMapNoFlyRenderQualityLevelSelected: (Int) -> Unit,
    onMapScannerSweepAnimationEnabledChanged: (Boolean) -> Unit,
    onMapScannerSweepAnimationSpeedPresetSelected: (String) -> Unit,
    onMapIdentityFullNamesEnabledChanged: (Boolean) -> Unit,
    onStickyCompassMapEnabledChanged: (Boolean) -> Unit,
    onStickyCompassMapLiveModeSelected: (String) -> Unit,
    onWifiRandomizedOneOffSuppressionEnabledChanged: (Boolean) -> Unit,
    onWifiAggregateOnlyEnabledChanged: (Boolean) -> Unit,
    onBleRandomizedOneOffSuppressionEnabledChanged: (Boolean) -> Unit,
    onBleAggregateOnlyEnabledChanged: (Boolean) -> Unit,
    onEnableFullEncryptionBiometric: suspend () -> String,
    onEnableFullEncryptionPin: suspend (String) -> String,
    onEnableFullEncryptionPassword: suspend (String) -> String,
    onDisableFullEncryptionLaunchLock: suspend () -> String,
    onFullEncryptionAutoLockTimeoutSecondsChanged: (Long) -> Unit,
    onFullEncryptionPinWipeEnabledChanged: (Boolean) -> Unit,
    onRotateFullEncryptionWrapping: suspend (String?) -> String,
    onLockFullEncryptionSessionNow: suspend () -> String,
    onExportBackup: suspend () -> String,
    onExportEncryptedBackup: suspend (String) -> String,
    onImportLatestBackup: suspend () -> String,
    onImportLatestEncryptedBackup: suspend (String) -> String,
    onResetDefaults: suspend () -> String,
    onSoftReset: suspend () -> String,
    onHardReset: suspend () -> String
) {
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findFragmentActivity() }
    val scope = rememberCoroutineScope()
    var sourceIntervalExpandedFor by remember { mutableStateOf<String?>(null) }
    var allSourceIntervalsExpanded by remember { mutableStateOf(false) }
    var themeModeExpanded by remember { mutableStateOf(false) }
    var mapClusterRangeExpanded by remember { mutableStateOf(false) }
    var noFlyRenderQualityExpanded by remember { mutableStateOf(false) }
    var mapScannerSweepSpeedPresetExpanded by remember { mutableStateOf(false) }
    var stickyCompassMapLiveModeExpanded by remember { mutableStateOf(false) }
    var backupActionInProgress by remember { mutableStateOf(false) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var backupPassphrase by rememberSaveable { mutableStateOf("") }
    var fullEncryptionActionInProgress by remember { mutableStateOf(false) }
    var fullEncryptionStatusMessage by remember { mutableStateOf<String?>(null) }
    var fullEncryptionPin by rememberSaveable { mutableStateOf("") }
    var fullEncryptionPinConfirm by rememberSaveable { mutableStateOf("") }
    var fullEncryptionPassword by rememberSaveable { mutableStateOf("") }
    var fullEncryptionPasswordConfirm by rememberSaveable { mutableStateOf("") }
    var fullEncryptionAutoLockExpanded by remember { mutableStateOf(false) }
    var confirmEnablePinWipeDialogVisible by rememberSaveable { mutableStateOf(false) }
    var resetDialogTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultsResetDialogVisible by rememberSaveable { mutableStateOf(false) }
    var resetActionInProgress by remember { mutableStateOf(false) }
    var resetStatusMessage by remember { mutableStateOf<String?>(null) }
    var resetStatusIsError by remember { mutableStateOf(false) }
    var defaultsResetInProgress by remember { mutableStateOf(false) }
    var selectedSettingsTab by rememberSaveable { mutableStateOf(0) }
    var memoryMonitorEnabled by rememberSaveable { mutableStateOf(true) }
    var memoryPrettyViewEnabled by rememberSaveable { mutableStateOf(true) }
    var runtimeSectionExpanded by rememberSaveable { mutableStateOf(true) }
    var deviceSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var encounterSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var logsSectionExpanded by rememberSaveable { mutableStateOf(false) }
    val hasStrongPassphrase = backupPassphrase.trim().length >= 8
    val normalizedUnlockPin = fullEncryptionPin.trim()
    val normalizedUnlockPinConfirm = fullEncryptionPinConfirm.trim()
    val normalizedUnlockPassword = fullEncryptionPassword.trim()
    val normalizedUnlockPasswordConfirm = fullEncryptionPasswordConfirm.trim()
    val hasStrongUnlockPin = normalizedUnlockPin.length >= 6
    val hasStrongUnlockPassword = normalizedUnlockPassword.length >= 8
    val hasMatchingUnlockPin =
        normalizedUnlockPin.isNotEmpty() && normalizedUnlockPin == normalizedUnlockPinConfirm
    val hasMatchingUnlockPassword =
        normalizedUnlockPassword.isNotEmpty() && normalizedUnlockPassword == normalizedUnlockPasswordConfirm
    val settingsTabs = listOf("Look", "Timing", "Detection", "Alerts", "Data")
    val intervalSourceTypes = ScanSettings.SOURCE_TYPES.filterNot {
        it == SourceCatalog.KEY_WIFI_DIRECT ||
            it == SourceCatalog.KEY_REMOTE_ID ||
            it == SourceCatalog.KEY_NFC
    }
    val workerCadenceMs = scanIntervalSeconds * 1000L
    val workerCadenceOverrun = (lastScanDurationMs ?: 0L) > workerCadenceMs
    val memorySnapshot by produceState(
        initialValue = captureRuntimeMemorySnapshot(context),
        key1 = selectedSettingsTab,
        key2 = memoryMonitorEnabled
    ) {
        if (selectedSettingsTab != 4 || !memoryMonitorEnabled) {
            value = captureRuntimeMemorySnapshot(context)
            return@produceState
        }
        while (true) {
            value = captureRuntimeMemorySnapshot(context)
            delay(1000.milliseconds)
        }
    }

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
                Text("Look & Map Presentation", fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Theme", fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
                        Text("System follows your device theme. Light and Dark force a fixed app appearance.")
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Marker Density & Labels", fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Cluster nearby markers")
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
                        Text("Tight keeps clusters smaller. Wide merges markers earlier when zoomed out.")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Show full identity labels")
                            Switch(
                                checked = mapIdentityFullNamesEnabled,
                                onCheckedChange = onMapIdentityFullNamesEnabledChanged
                            )
                        }
                        Text(
                            if (mapIdentityFullNamesEnabled) {
                                "Current: full identity names on map pins"
                            } else {
                                "Current: abbreviated identity names on map pins"
                            }
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Map Layers", fontWeight = FontWeight.Bold)
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
                        Text("No-fly overlays can be slow to load/render and may impact map performance. Enable only when needed.")
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Motion & Floating Mini-Map", fontWeight = FontWeight.Bold)
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Scanner sweep speed")
                            Button(
                                onClick = { mapScannerSweepSpeedPresetExpanded = true },
                                enabled = mapScannerSweepAnimationEnabled
                            ) {
                                Text(mapScannerSweepSpeedPresetLabel(mapScannerSweepAnimationSpeedPreset))
                            }
                        }
                        DropdownMenu(
                            expanded = mapScannerSweepSpeedPresetExpanded,
                            onDismissRequest = { mapScannerSweepSpeedPresetExpanded = false }
                        ) {
                            ScanSettings.ALLOWED_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESETS.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(mapScannerSweepSpeedPresetLabel(preset)) },
                                    onClick = {
                                        onMapScannerSweepAnimationSpeedPresetSelected(preset)
                                        mapScannerSweepSpeedPresetExpanded = false
                                    }
                                )
                            }
                        }
                        Text("Radial sweep overlay animation on maps. Turn off for better performance.")
                        Text(
                            "Preset behavior: Conservative prioritizes stability with dense maps, Balanced is smoother than Conservative, Smooth is the smoothest and disables dynamic pin-count speed adjustment.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Sticky mini-map (PiP)")
                            Switch(
                                checked = stickyCompassMapEnabled,
                                onCheckedChange = onStickyCompassMapEnabledChanged
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Sticky mini-map live mode")
                            Button(
                                onClick = { stickyCompassMapLiveModeExpanded = true },
                                enabled = stickyCompassMapEnabled
                            ) {
                                Text(stickyCompassMapLiveModeLabel(stickyCompassMapLiveMode))
                            }
                        }
                        DropdownMenu(
                            expanded = stickyCompassMapLiveModeExpanded,
                            onDismissRequest = { stickyCompassMapLiveModeExpanded = false }
                        ) {
                            ScanSettings.ALLOWED_STICKY_COMPASS_MAP_LIVE_MODES.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(stickyCompassMapLiveModeLabel(mode)) },
                                    onClick = {
                                        onStickyCompassMapLiveModeSelected(mode)
                                        stickyCompassMapLiveModeExpanded = false
                                    }
                                )
                            }
                        }
                        Text(
                            "When enabled, minimizing from Detection > Device Map keeps a sticky compass-like mini map in Picture-in-Picture."
                        )
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
                    twoColumnMinWidth = 640.dp,
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
                                val allowedIntervals = if (sourceType == SourceCatalog.KEY_MAGNETIC) {
                                    ScanSettings.ALLOWED_MAGNETIC_SOURCE_SCAN_INTERVAL_SECONDS
                                } else {
                                    ScanSettings.ALLOWED_SOURCE_SCAN_INTERVAL_SECONDS.filter { it >= ScanSettings.MIN_SOURCE_SCAN_INTERVAL_SECONDS }
                                }
                                allowedIntervals.forEach { seconds ->
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
                Text("Data Retention", fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Live mode only")
                            Switch(
                                checked = liveModeOnlyEnabled,
                                onCheckedChange = onLiveModeOnlyChanged
                            )
                        }
                        Text("When enabled, local encounters, devices, and logs are cleared every time the app cold-starts after being fully closed.")
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
                            "camera_in_view",
                            "no_fly",
                            "nfc",
                            "stingray",
                            "magnetic",
                            "magnetic_rhythm",
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

                                    "camera_in_view" -> {
                                        Text("Camera in-view alerts")
                                        Switch(
                                            checked = cameraInViewNotificationsEnabled,
                                            onCheckedChange = onCameraInViewNotificationsChanged
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

                                    "stingray" -> {
                                        Text("Stingray detection alerts")
                                        Switch(
                                            checked = stingrayNotificationsEnabled,
                                            onCheckedChange = onStingrayNotificationsChanged
                                        )
                                    }

                                    "magnetic" -> {
                                        Text("Magnetic disturbance alerts")
                                        Switch(
                                            checked = magneticIncreaseNotificationsEnabled,
                                            onCheckedChange = onMagneticIncreaseNotificationsChanged
                                        )
                                    }

                                    "magnetic_rhythm" -> {
                                        Text("Magnetic rhythm beeps")
                                        Switch(
                                            checked = magneticRhythmBeepEnabled,
                                            onCheckedChange = onMagneticRhythmBeepEnabledChanged,
                                            enabled = foreignDirectMagneticEnabled
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
                Text("Runtime Memory", fontWeight = FontWeight.Bold)
            }
            item {
                val heapPressure =
                    if (memorySnapshot.maxHeapBytes > 0L) {
                        (memorySnapshot.usedHeapBytes.toFloat() / memorySnapshot.maxHeapBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                val deviceDataBytes =
                    ownedDeviceEstimatedBytes +
                        memorySnapshot.visibleDeviceRowsEstimatedBytes +
                        memorySnapshot.deviceAnalysisListEstimatedBytes +
                        memorySnapshot.deviceMapCandidateEstimatedBytes +
                        memorySnapshot.deviceMapPinPoolEstimatedBytes +
                        memorySnapshot.activeDeviceMapPinsEstimatedBytes
                val encounterDataBytes =
                    recentEncounterEstimatedBytes +
                        memorySnapshot.visibleEncounterRowsEstimatedBytes +
                        memorySnapshot.pipelineEncounterEstimatedBytes +
                        memorySnapshot.deviceAnalysisWindowEstimatedBytes
                val logsDataBytes =
                    alertLogEstimatedBytes +
                        errorLogEstimatedBytes

                val runtimeRows = listOf(
                    "Heap used" to memorySnapshot.usedHeapBytes,
                    "Native heap allocated" to memorySnapshot.nativeHeapAllocatedBytes,
                    "Process PSS" to memorySnapshot.processTotalPssBytes,
                    "Runtime pin cache" to memorySnapshot.runtimePinEstimatedBytes,
                    "Disk pin cache payload" to memorySnapshot.diskCachePayloadBytes,
                    "Marker icon caches" to memorySnapshot.iconCacheEstimatedBytes
                )
                val deviceRows = listOf(
                    "Owned device registry" to ownedDeviceEstimatedBytes,
                    "Visible device rows" to memorySnapshot.visibleDeviceRowsEstimatedBytes,
                    "Device analysis list" to memorySnapshot.deviceAnalysisListEstimatedBytes,
                    "Device-map candidates" to memorySnapshot.deviceMapCandidateEstimatedBytes,
                    "Device-map pin pool" to memorySnapshot.deviceMapPinPoolEstimatedBytes,
                    "Active device-map pins" to memorySnapshot.activeDeviceMapPinsEstimatedBytes
                )
                val encounterRows = listOf(
                    "Recent encounter stream" to recentEncounterEstimatedBytes,
                    "Visible encounter rows" to memorySnapshot.visibleEncounterRowsEstimatedBytes,
                    "Pipeline encounters" to memorySnapshot.pipelineEncounterEstimatedBytes,
                    "Device analysis window" to memorySnapshot.deviceAnalysisWindowEstimatedBytes
                )
                val logRows = listOf(
                    "Alert logs" to alertLogEstimatedBytes,
                    "Operational error logs" to errorLogEstimatedBytes
                )

                val runtimeMax = runtimeRows.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
                val deviceMax = deviceRows.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
                val encounterMax = encounterRows.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
                val logMax = logRows.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Live memory/cache monitor", fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = memoryMonitorEnabled,
                                onCheckedChange = { enabled -> memoryMonitorEnabled = enabled }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Pretty meter view", fontWeight = FontWeight.Medium)
                            Switch(
                                checked = memoryPrettyViewEnabled,
                                onCheckedChange = { enabled -> memoryPrettyViewEnabled = enabled }
                            )
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("Runtime", fontWeight = FontWeight.SemiBold)
                                    TextButton(onClick = { runtimeSectionExpanded = !runtimeSectionExpanded }) {
                                        Text(if (runtimeSectionExpanded) "Collapse" else "Expand")
                                    }
                                }
                                AnimatedVisibility(visible = runtimeSectionExpanded) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (memoryPrettyViewEnabled) {
                                            LinearProgressIndicator(
                                                progress = { heapPressure },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Text(
                                                "Heap used ${formatBytesMiB(memorySnapshot.usedHeapBytes)} / max ${formatBytesMiB(memorySnapshot.maxHeapBytes)} (${(heapPressure * 100f).toInt()}%)"
                                            )
                                            runtimeRows.forEach { (label, value) ->
                                                val ratio = (value.toFloat() / runtimeMax.toFloat()).coerceIn(0f, 1f)
                                                Text("$label • ${formatBytesAdaptive(value)}")
                                                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                                            }
                                        } else {
                                            Text("Heap free ${formatBytesMiB(memorySnapshot.freeHeapBytes)} • allocated ${formatBytesMiB(memorySnapshot.totalHeapBytes)}")
                                            Text("Native heap ${formatBytesAdaptive(memorySnapshot.nativeHeapAllocatedBytes)} / ${formatBytesAdaptive(memorySnapshot.nativeHeapSizeBytes)} (free ${formatBytesAdaptive(memorySnapshot.nativeHeapFreeBytes)})")
                                            Text("Process PSS ${formatBytesAdaptive(memorySnapshot.processTotalPssBytes)} • private dirty ${formatBytesAdaptive(memorySnapshot.processPrivateDirtyBytes)} • shared dirty ${formatBytesAdaptive(memorySnapshot.processSharedDirtyBytes)}")
                                            Text("Runtime map-pin cache: ${memorySnapshot.runtimePinCount} pins • ${formatBytesAdaptive(memorySnapshot.runtimePinEstimatedBytes)} est • age ${formatAgeSeconds(memorySnapshot.runtimePinAgeMs)}")
                                            Text("Disk map-pin cache payload: ${memorySnapshot.diskCachePayloadChars} chars • ${formatBytesAdaptive(memorySnapshot.diskCachePayloadBytes)} UTF-8 • age ${formatAgeSeconds(memorySnapshot.diskCacheAgeMs)}")
                                            Text("Marker icon caches total: ${memorySnapshot.iconCacheEntryCount} entries • ${formatBytesAdaptive(memorySnapshot.iconCacheEstimatedBytes)} est")
                                        }
                                        Text(
                                            "Estimates exclude native bitmap payload inside map descriptors; use deltas to spot growth trends.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("Device Data", fontWeight = FontWeight.SemiBold)
                                    TextButton(onClick = { deviceSectionExpanded = !deviceSectionExpanded }) {
                                        Text(if (deviceSectionExpanded) "Collapse" else "Expand")
                                    }
                                }
                                AnimatedVisibility(visible = deviceSectionExpanded) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (memoryPrettyViewEnabled) {
                                            Text("Estimated device data footprint: ${formatBytesAdaptive(deviceDataBytes)}")
                                            deviceRows.forEach { (label, value) ->
                                                val ratio = (value.toFloat() / deviceMax.toFloat()).coerceIn(0f, 1f)
                                                Text("$label • ${formatBytesAdaptive(value)}")
                                                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                                            }
                                        } else {
                                            Text("Owned device registry: $ownedDeviceCount entries • ${formatBytesAdaptive(ownedDeviceEstimatedBytes)} est")
                                            Text("Visible device rows: ${memorySnapshot.visibleDeviceRows} • ${formatBytesAdaptive(memorySnapshot.visibleDeviceRowsEstimatedBytes)} est • sampled ${formatAgeSeconds(memorySnapshot.visibleDeviceRowsSampleAgeMs)} ago")
                                            Text("Device analysis list: ${memorySnapshot.deviceAnalysisListCount} • ${formatBytesAdaptive(memorySnapshot.deviceAnalysisListEstimatedBytes)} est • sampled ${formatAgeSeconds(memorySnapshot.deviceAnalysisListSampleAgeMs)} ago")
                                            Text("Device-map candidates: ${memorySnapshot.deviceMapCandidateCount} • ${formatBytesAdaptive(memorySnapshot.deviceMapCandidateEstimatedBytes)} est • sampled ${formatAgeSeconds(memorySnapshot.deviceMapCandidateSampleAgeMs)} ago")
                                            Text("Device-map pin pool: ${memorySnapshot.deviceMapPinPoolCount} • ${formatBytesAdaptive(memorySnapshot.deviceMapPinPoolEstimatedBytes)} est • sampled ${formatAgeSeconds(memorySnapshot.deviceMapPinPoolSampleAgeMs)} ago")
                                            Text("Active device-map pins: ${memorySnapshot.activeDeviceMapPins} • ${formatBytesAdaptive(memorySnapshot.activeDeviceMapPinsEstimatedBytes)} est • sampled ${formatAgeSeconds(memorySnapshot.activeDeviceMapPinsSampleAgeMs)} ago")
                                        }
                                    }
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("Encounter Data", fontWeight = FontWeight.SemiBold)
                                    TextButton(onClick = { encounterSectionExpanded = !encounterSectionExpanded }) {
                                        Text(if (encounterSectionExpanded) "Collapse" else "Expand")
                                    }
                                }
                                AnimatedVisibility(visible = encounterSectionExpanded) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (memoryPrettyViewEnabled) {
                                            Text("Estimated encounter data footprint: ${formatBytesAdaptive(encounterDataBytes)}")
                                            encounterRows.forEach { (label, value) ->
                                                val ratio = (value.toFloat() / encounterMax.toFloat()).coerceIn(0f, 1f)
                                                Text("$label • ${formatBytesAdaptive(value)}")
                                                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                                            }
                                        } else {
                                            Text("Recent encounter stream: $recentEncounterCount • ${formatBytesAdaptive(recentEncounterEstimatedBytes)} est")
                                            Text("Visible encounter rows: ${memorySnapshot.visibleEncounterRows} • ${formatBytesAdaptive(memorySnapshot.visibleEncounterRowsEstimatedBytes)} est • sampled ${formatAgeSeconds(memorySnapshot.visibleEncounterRowsSampleAgeMs)} ago")
                                            Text("Pipeline encounters: ${memorySnapshot.pipelineEncounterCount} • ${formatBytesAdaptive(memorySnapshot.pipelineEncounterEstimatedBytes)} est • sampled ${formatAgeSeconds(memorySnapshot.pipelineEncounterSampleAgeMs)} ago")
                                            Text("Device analysis window: ${memorySnapshot.deviceAnalysisWindowCount} • ${formatBytesAdaptive(memorySnapshot.deviceAnalysisWindowEstimatedBytes)} est • sampled ${formatAgeSeconds(memorySnapshot.deviceAnalysisWindowSampleAgeMs)} ago")
                                        }
                                    }
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("Logs Data", fontWeight = FontWeight.SemiBold)
                                    TextButton(onClick = { logsSectionExpanded = !logsSectionExpanded }) {
                                        Text(if (logsSectionExpanded) "Collapse" else "Expand")
                                    }
                                }
                                AnimatedVisibility(visible = logsSectionExpanded) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (memoryPrettyViewEnabled) {
                                            Text("Estimated log data footprint: ${formatBytesAdaptive(logsDataBytes)}")
                                            logRows.forEach { (label, value) ->
                                                val ratio = (value.toFloat() / logMax.toFloat()).coerceIn(0f, 1f)
                                                Text("$label • ${formatBytesAdaptive(value)}")
                                                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                                            }
                                        } else {
                                            Text("Alert logs: $alertLogCount entries • ${formatBytesAdaptive(alertLogEstimatedBytes)} est")
                                            Text("Operational error logs: $errorLogCount entries • ${formatBytesAdaptive(errorLogEstimatedBytes)} est")
                                            Text("Auto-adjust activity log entries: ${scanIntervalChangeEvents.size}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text("Full Encryption", fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Data storage always uses an encrypted database key. Launch lock controls whether user auth is required before decrypting at startup.")
                        val modeLabel = when {
                            !fullEncryptionEnabled -> "None (launch lock disabled)"
                            fullEncryptionUnlockMethod == ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_PIN -> "PIN"
                            fullEncryptionUnlockMethod == ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_PASSWORD -> "Password"
                            else -> "Biometric or device credential"
                        }
                        val biometricMethodActive =
                            fullEncryptionEnabled &&
                                fullEncryptionUnlockMethod == ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_BIOMETRIC
                        val pinMethodActive =
                            fullEncryptionEnabled &&
                                fullEncryptionUnlockMethod == ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_PIN
                        val passwordMethodActive =
                            fullEncryptionEnabled &&
                                fullEncryptionUnlockMethod == ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_PASSWORD
                        Text("Launch lock: ${if (fullEncryptionEnabled) "Enabled" else "Disabled"}")
                        Text("Current lock method: $modeLabel")
                        Text("Session key status: ${if (fullEncryptionUnlockedInSession) "Unlocked in memory" else "Locked"}")
                        Text("Auto-lock timeout: ${if (fullEncryptionAutoLockTimeoutSeconds <= 0L) "Disabled" else "${fullEncryptionAutoLockTimeoutSeconds}s"}")
                        val triesLeftBeforeWipe = if (fullEncryptionPinWipeEnabled) {
                            (AppEncryptionManager.PIN_FAIL_WIPE_THRESHOLD - fullEncryptionPinFailedAttempts).coerceAtLeast(0)
                        } else {
                            null
                        }
                        val triesLeftLabel = triesLeftBeforeWipe
                            ?.let { tries -> " ($tries ${if (tries == 1) "try" else "tries"} left before wipe)" }
                            .orEmpty()
                        Text("Credential failed attempts: $fullEncryptionPinFailedAttempts$triesLeftLabel")
                        if (fullEncryptionPinLockoutUntilEpochMs > System.currentTimeMillis()) {
                            Text("Credential lockout until ${formatEpoch(fullEncryptionPinLockoutUntilEpochMs)}", color = Color(0xFFB3261E))
                        }
                        if (fullEncryptionLastWipeEpochMs != null) {
                            Text("Last protective wipe: ${formatEpoch(fullEncryptionLastWipeEpochMs)}", color = Color(0xFFE65100))
                        }
                        Text("Wrap rotations: $fullEncryptionWrapRotationCount")
                        if (fullEncryptionWrapLastRotationEpochMs != null) {
                            Text("Last wrap rotation: ${formatEpoch(fullEncryptionWrapLastRotationEpochMs)}")
                        }

                        Text("Lock methods", fontWeight = FontWeight.SemiBold)
                        val activeMethodBorder = BorderStroke(2.dp, Color(0xFF2E7D32))
                        val inactiveMethodBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            border = if (biometricMethodActive) activeMethodBorder else inactiveMethodBorder
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("Biometric / device credential")
                                    Text(
                                        when {
                                            !fullEncryptionBiometricAvailable -> "Unavailable"
                                            biometricMethodActive -> "Active"
                                            else -> "Inactive"
                                        },
                                        color = if (biometricMethodActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Button(
                                    enabled = !fullEncryptionActionInProgress && fullEncryptionBiometricAvailable,
                                    onClick = {
                                        if (biometricMethodActive) {
                                            scope.launch {
                                                fullEncryptionActionInProgress = true
                                                fullEncryptionStatusMessage = runCatching {
                                                    onDisableFullEncryptionLaunchLock()
                                                }.getOrElse { error ->
                                                    "Disable biometric launch lock failed: ${error.message ?: "unknown error"}"
                                                }
                                                fullEncryptionActionInProgress = false
                                            }
                                            return@Button
                                        }

                                        val activity = hostActivity ?: run {
                                            fullEncryptionStatusMessage = "Unable to open biometric prompt from current context."
                                            return@Button
                                        }
                                        scope.launch {
                                            fullEncryptionActionInProgress = true
                                            fullEncryptionStatusMessage = null
                                            val authenticated = requestSecureLaunchAuthentication(activity)
                                            if (!authenticated) {
                                                fullEncryptionStatusMessage = "Biometric/device credential authentication was canceled."
                                            } else {
                                                fullEncryptionStatusMessage = runCatching {
                                                    onEnableFullEncryptionBiometric()
                                                }.getOrElse { error ->
                                                    "Enable biometric launch lock failed: ${error.message ?: "unknown error"}"
                                                }
                                            }
                                            fullEncryptionActionInProgress = false
                                        }
                                    }
                                ) {
                                    Text(if (biometricMethodActive) "Disable Biometric Lock" else "Enable Biometric Lock")
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            border = if (pinMethodActive) activeMethodBorder else inactiveMethodBorder
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("PIN")
                                    Text(
                                        if (pinMethodActive) "Active" else "Inactive",
                                        color = if (pinMethodActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                OutlinedTextField(
                                    value = fullEncryptionPin,
                                    onValueChange = { fullEncryptionPin = it },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    label = { Text("PIN (min 6)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = fullEncryptionPinConfirm,
                                    onValueChange = { fullEncryptionPinConfirm = it },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    label = { Text("Confirm PIN") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    enabled = !fullEncryptionActionInProgress && hasStrongUnlockPin && hasMatchingUnlockPin,
                                    onClick = {
                                        scope.launch {
                                            fullEncryptionActionInProgress = true
                                            fullEncryptionStatusMessage = runCatching {
                                                onEnableFullEncryptionPin(normalizedUnlockPin)
                                            }.getOrElse { error ->
                                                "Enable PIN launch lock failed: ${error.message ?: "unknown error"}"
                                            }
                                            fullEncryptionActionInProgress = false
                                        }
                                    }
                                ) {
                                    Text(if (pinMethodActive) "Update PIN Lock" else "Enable PIN Lock")
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            border = if (passwordMethodActive) activeMethodBorder else inactiveMethodBorder
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("Password")
                                    Text(
                                        if (passwordMethodActive) "Active" else "Inactive",
                                        color = if (passwordMethodActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                OutlinedTextField(
                                    value = fullEncryptionPassword,
                                    onValueChange = { fullEncryptionPassword = it },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    label = { Text("Password (min 8)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = fullEncryptionPasswordConfirm,
                                    onValueChange = { fullEncryptionPasswordConfirm = it },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    label = { Text("Confirm password") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    enabled = !fullEncryptionActionInProgress && hasStrongUnlockPassword && hasMatchingUnlockPassword,
                                    onClick = {
                                        scope.launch {
                                            fullEncryptionActionInProgress = true
                                            fullEncryptionStatusMessage = runCatching {
                                                onEnableFullEncryptionPassword(normalizedUnlockPassword)
                                            }.getOrElse { error ->
                                                "Enable password launch lock failed: ${error.message ?: "unknown error"}"
                                            }
                                            fullEncryptionActionInProgress = false
                                        }
                                    }
                                ) {
                                    Text(if (passwordMethodActive) "Update Password Lock" else "Enable Password Lock")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Auto-lock timeout")
                            Button(
                                enabled = !fullEncryptionActionInProgress,
                                onClick = { fullEncryptionAutoLockExpanded = true }
                            ) {
                                Text(if (fullEncryptionAutoLockTimeoutSeconds <= 0L) "Disabled" else "${fullEncryptionAutoLockTimeoutSeconds}s")
                            }
                            DropdownMenu(
                                expanded = fullEncryptionAutoLockExpanded,
                                onDismissRequest = { fullEncryptionAutoLockExpanded = false }
                            ) {
                                ScanSettings.ALLOWED_FULL_ENCRYPTION_AUTO_LOCK_TIMEOUT_SECONDS.forEach { seconds ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (seconds <= 0L) "Disabled"
                                                else "${seconds}s"
                                            )
                                        },
                                        onClick = {
                                            onFullEncryptionAutoLockTimeoutSecondsChanged(seconds)
                                            fullEncryptionAutoLockExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Wipe after repeated failed PIN attempts")
                            Switch(
                                checked = fullEncryptionPinWipeEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && !fullEncryptionPinWipeEnabled) {
                                        confirmEnablePinWipeDialogVisible = true
                                    } else {
                                        onFullEncryptionPinWipeEnabledChanged(enabled)
                                    }
                                },
                                enabled = !fullEncryptionActionInProgress
                            )
                        }

                        Text("Session controls", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Disable launch lock turns off startup authentication. Lock session now only forgets the in-memory key for this session.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                enabled = !fullEncryptionActionInProgress && fullEncryptionEnabled,
                                onClick = {
                                    scope.launch {
                                        fullEncryptionActionInProgress = true
                                        fullEncryptionStatusMessage = runCatching {
                                            onDisableFullEncryptionLaunchLock()
                                        }.getOrElse { error ->
                                            "Disable launch lock failed: ${error.message ?: "unknown error"}"
                                        }
                                        fullEncryptionActionInProgress = false
                                    }
                                }
                            ) {
                                Text("Disable Launch Lock (Startup Auth Off)")
                            }
                            OutlinedButton(
                                enabled = !fullEncryptionActionInProgress && fullEncryptionEnabled && fullEncryptionUnlockedInSession,
                                onClick = {
                                    scope.launch {
                                        fullEncryptionActionInProgress = true
                                        fullEncryptionStatusMessage = runCatching {
                                            onLockFullEncryptionSessionNow()
                                        }.getOrElse { error ->
                                            "Session lock failed: ${error.message ?: "unknown error"}"
                                        }
                                        fullEncryptionActionInProgress = false
                                    }
                                }
                            ) {
                                Text("Lock Session Now")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val credentialForWrapRotation = when (fullEncryptionUnlockMethod) {
                                ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_PASSWORD -> fullEncryptionPassword.trim()
                                ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_PIN -> fullEncryptionPin.trim()
                                else -> ""
                            }
                            OutlinedButton(
                                enabled = !fullEncryptionActionInProgress,
                                onClick = {
                                    scope.launch {
                                        fullEncryptionActionInProgress = true
                                        fullEncryptionStatusMessage = runCatching {
                                            onRotateFullEncryptionWrapping(credentialForWrapRotation.ifBlank { null })
                                        }.getOrElse { error ->
                                            "Wrap rotation failed: ${error.message ?: "unknown error"}"
                                        }
                                        fullEncryptionActionInProgress = false
                                    }
                                }
                            ) {
                                Text("Rotate Wrapping")
                            }
                        }

                        if (fullEncryptionMigrationTelemetry.isNotEmpty()) {
                            Text("Encrypted migration telemetry", fontWeight = FontWeight.SemiBold)
                            fullEncryptionMigrationTelemetry.forEach { telemetry ->
                                Text(
                                    "${telemetry.namespace}: migrated=${if (telemetry.migrationDone) "yes" else "no"}, encrypted_backend=${if (telemetry.encryptedBackend) "yes" else "no"}, entries=${telemetry.entryCount}"
                                )
                            }
                        }

                        if (!fullEncryptionBiometricAvailable) {
                            Text("Biometric or device credential prompt is not available on this device.")
                        }
                        if (!hasStrongUnlockPin) {
                            Text("Set a PIN of at least 6 characters to enable PIN launch lock.")
                        } else if (!hasMatchingUnlockPin) {
                            Text("PIN and confirmation must match to enable PIN launch lock.")
                        }
                        if (!hasStrongUnlockPassword) {
                            Text("Set a password of at least 8 characters to enable password launch lock.")
                        } else if (!hasMatchingUnlockPassword) {
                            Text("Password and confirmation must match to enable password launch lock.")
                        }
                        if (fullEncryptionStatusMessage != null) {
                            Text(fullEncryptionStatusMessage!!)
                        }
                    }
                }
            }
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

    if (confirmEnablePinWipeDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!fullEncryptionActionInProgress) {
                    confirmEnablePinWipeDialogVisible = false
                }
            },
            title = {
                Text("Enable Wipe On Failed PIN")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This policy will wipe local encrypted data after repeated failed PIN attempts.")
                    Text("Use only if you understand recovery may not be possible without backups.")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !fullEncryptionActionInProgress,
                    onClick = {
                        onFullEncryptionPinWipeEnabledChanged(true)
                        fullEncryptionStatusMessage = "Wipe-on-failed-PIN enabled. Keep encrypted backups updated."
                        confirmEnablePinWipeDialogVisible = false
                    }
                ) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !fullEncryptionActionInProgress,
                    onClick = {
                        confirmEnablePinWipeDialogVisible = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetectionPage(
    initialTabRequest: Int?,
    onInitialTabRequestHandled: () -> Unit,
    initialMagneticPopupRequest: Long?,
    onInitialMagneticPopupRequestHandled: () -> Unit,
    readinessItems: List<DetectionReadinessItem>,
    meshInsightEncounters: List<Encounter>,
    directMagneticChannelEnabled: Boolean,
    magneticRhythmBeepEnabled: Boolean,
    magneticIncreaseNotificationsEnabled: Boolean,
    magneticEventTriggerThresholdMicroTesla: Double,
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
    mapScannerSweepAnimationSpeedPreset: String,
    mapIdentityFullNamesEnabled: Boolean,
    onMapIdentityFullNamesEnabledChanged: (Boolean) -> Unit,
    stickyCompassMapEnabled: Boolean,
    stickyCompassMapLiveMode: String,
    liveModeOnlyEnabled: Boolean,
    mapOnlyMode: Boolean,
    pipZoomCommandNonce: Long,
    pipZoomCommandDelta: Float,
    chainMeshSnapshot: ChainMeshSnapshot,
    onDeviceMapPinClick: (source: String, primaryId: String, lat: Double?, lon: Double?, timestampEpochMs: Long?) -> Unit,
    onDeviceClick: (DeviceItem) -> Unit,
    onMovingDeviceMapPinClick: (source: String, primaryId: String) -> Unit,
    onRefresh: () -> Unit,
    onOpenReadinessSetting: (DetectionReadinessItem) -> Unit,
    onDirectMagneticChannelEnabledChanged: (Boolean) -> Unit,
    onMagneticRhythmBeepEnabledChanged: (Boolean) -> Unit,
    onMagneticEventTriggerThresholdChanged: (Double) -> Unit,
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
    val detectionScope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var permissionsExpanded by rememberSaveable { mutableStateOf(false) }
    var cellDevicePinLimit by rememberSaveable { mutableStateOf(10000) }
    var liveOnlyOnDeviceMap by rememberSaveable { mutableStateOf(false) }
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
    var liveOnlyOnBluetoothMap by rememberSaveable { mutableStateOf(false) }
    var identityModeOnBluetoothMap by rememberSaveable { mutableStateOf(true) }
    var movingOnlyOnBluetoothMap by rememberSaveable { mutableStateOf(false) }
    var sinceSnapshotOnlyOnBluetoothMap by rememberSaveable { mutableStateOf(false) }
    var bluetoothMapSnapshotEpochMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var magneticMapPinLimit by rememberSaveable { mutableStateOf(1500) }
    var magneticMapFocusRequestNonce by rememberSaveable { mutableStateOf(0) }
    var deviceMapAircraftRadiusMiles by rememberSaveable {
        mutableStateOf(ScanSettings.getAviationPublicRadiusMiles(context).coerceIn(25, 75))
    }
    var flightMapRadiusMiles by rememberSaveable {
        mutableStateOf(ScanSettings.getAviationPublicRadiusMiles(context).coerceIn(10, 1000))
    }
    val tabs = listOf("Status", "Device", "Flock", "Map", "Mesh")
    var mapLiveNowEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val stickyMapForcesLiveOnly =
        stickyCompassMapEnabled &&
            stickyCompassMapLiveMode == ScanSettings.STICKY_COMPASS_MAP_LIVE_MODE_FORCE_LIVE_ONLY
    val deviceMapLiveOnlyEnabled = liveOnlyOnDeviceMap || stickyMapForcesLiveOnly

    val stickyPipEligible =
        stickyCompassMapEnabled &&
            selectedTab == 3 &&
            selectedMapSubTab == 0
    SideEffect {
        ScanSettings.setStickyCompassMapPipEligible(context, stickyPipEligible)
    }

    LaunchedEffect(mapOnlyMode) {
        if (!mapOnlyMode) return@LaunchedEffect
        selectedTab = 3
        selectedMapSubTab = 0
    }

    val mapLiveTickerEnabled = selectedTab == 3 &&
        (selectedMapSubTab == 0 || selectedMapSubTab == 1)

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
        (isDeviceMapActive && !deviceMapLiveOnlyEnabled && !movingOnlyOnDeviceMap && !sinceSnapshotOnlyOnDeviceMap) ||
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
        if (selectedTab == 0) {
            runCatching { buildSignalIntelSnapshot(meshInsightEncounters) }
                .getOrElse { buildSignalIntelSnapshot(emptyList()) }
        } else {
            null
        }
    }
    val latestMagneticMagnitudeMicroTesla = remember(meshInsightEncounters) {
        meshInsightEncounters
            .asSequence()
            .filter { encounter -> isDirectSignalChannel(encounter, "magnetic") }
            .maxByOrNull { encounter -> encounter.timestampEpochMs }
            ?.let { encounter ->
                runCatching {
                    JSONObject(encounter.rawPayloadJson).optDoubleOrNull("magnitudeMicroTesla")
                }.getOrNull()
            }
    }
    var magneticDialogVisible by rememberSaveable { mutableStateOf(false) }
    val liveMagneticMagnitudeMicroTesla by rememberRealtimeMagneticMagnitudeMicroTesla(
        enabled = directMagneticChannelEnabled && (magneticDialogVisible || magneticRhythmBeepEnabled)
    )
    var previousLiveMagneticMagnitudeMicroTesla by remember { mutableStateOf<Double?>(null) }
    var lastRealtimeMagneticNotificationEpochMs by remember { mutableStateOf(0L) }
    var lastRealtimeMagneticRhythmEpochMs by remember { mutableStateOf(0L) }
    var lastMagneticDetectionPopupEpochMs by remember { mutableStateOf(0L) }
    var magneticDetectionPopup by remember { mutableStateOf<MagneticDetectionPopupState?>(null) }
    var realtimeMagneticRhythmJob by remember { mutableStateOf<Job?>(null) }
    var magneticThresholdSliderValue by rememberSaveable {
        mutableStateOf(magneticEventTriggerThresholdMicroTesla.toFloat())
    }
    var magneticChartSamples by remember { mutableStateOf<List<Double>>(emptyList()) }
    val headerMagneticMagnitudeMicroTesla = liveMagneticMagnitudeMicroTesla ?: latestMagneticMagnitudeMicroTesla
    val highAlertThresholdMicroTesla = maxOf(
        magneticEventTriggerThresholdMicroTesla + 8.0,
        MAGNETIC_SUSTAINED_HIGH_THRESHOLD_UT
    )
    val magneticReadoutColor = when {
        headerMagneticMagnitudeMicroTesla == null -> MaterialTheme.colorScheme.onSurfaceVariant
        headerMagneticMagnitudeMicroTesla >= highAlertThresholdMicroTesla -> Color(0xFFB3261E)
        headerMagneticMagnitudeMicroTesla >= magneticEventTriggerThresholdMicroTesla -> Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val missingReadinessItems = remember(readinessItems) {
        readinessItems.filter { it.isMissing }
    }
    val runtimePermissionReadinessItems = remember(readinessItems) {
        readinessItems.filter { item -> item.id.startsWith("perm_") && !item.id.startsWith("perm_special_") }
    }
    val specialPermissionReadinessItems = remember(readinessItems) {
        readinessItems.filter { item -> item.id.startsWith("perm_special_") }
    }
    val permissionReadinessItems = remember(runtimePermissionReadinessItems, specialPermissionReadinessItems) {
        runtimePermissionReadinessItems + specialPermissionReadinessItems
    }
    val nonPermissionMissingReadinessItems = remember(missingReadinessItems) {
        missingReadinessItems.filterNot { it.id.startsWith("perm_") }
    }
    val grantedRuntimePermissionReadinessCount = remember(runtimePermissionReadinessItems) {
        runtimePermissionReadinessItems.count { !it.isMissing }
    }
    val grantedSpecialPermissionReadinessCount = remember(specialPermissionReadinessItems) {
        specialPermissionReadinessItems.count { !it.isMissing }
    }
    val grantedPermissionReadinessCount = remember(permissionReadinessItems) {
        permissionReadinessItems.count { !it.isMissing }
    }
    val readyReadinessCount = remember(readinessItems) {
        readinessItems.count { !it.isMissing }
    }
    val permissionSummaryText = remember(
        grantedPermissionReadinessCount,
        permissionReadinessItems,
        grantedRuntimePermissionReadinessCount,
        runtimePermissionReadinessItems,
        grantedSpecialPermissionReadinessCount,
        specialPermissionReadinessItems
    ) {
        buildString {
            append("$grantedPermissionReadinessCount/${permissionReadinessItems.size} available")
            if (runtimePermissionReadinessItems.isNotEmpty()) {
                append(" • Runtime $grantedRuntimePermissionReadinessCount/${runtimePermissionReadinessItems.size}")
            }
            if (specialPermissionReadinessItems.isNotEmpty()) {
                append(" • Manifest/Special $grantedSpecialPermissionReadinessCount/${specialPermissionReadinessItems.size}")
            }
        }
    }
    val flightDisplayRadiusMeters = flightMapRadiusMiles.coerceIn(10, 1000) * 1609.344
    val topSpeedRecords = remember(selectedTab, selectedMapSubTab, meshInsightEncounters.size) {
        if (selectedTab == 3 && (selectedMapSubTab == 0 || selectedMapSubTab == 1)) {
            DeviceSpeedRecordStore.getAllRecordSpeedsMps(context)
        } else {
            emptyMap()
        }
    }

    LaunchedEffect(magneticEventTriggerThresholdMicroTesla, magneticDialogVisible) {
        if (!magneticDialogVisible) {
            magneticThresholdSliderValue = magneticEventTriggerThresholdMicroTesla.toFloat()
        }
    }

    LaunchedEffect(initialMagneticPopupRequest) {
        val requestToken = initialMagneticPopupRequest ?: return@LaunchedEffect
        if (requestToken <= 0L) {
            onInitialMagneticPopupRequestHandled()
            return@LaunchedEffect
        }
        selectedTab = 0
        magneticDialogVisible = true
        onInitialMagneticPopupRequestHandled()
    }

    LaunchedEffect(liveMagneticMagnitudeMicroTesla) {
        val sample = liveMagneticMagnitudeMicroTesla ?: return@LaunchedEffect
        magneticChartSamples = (magneticChartSamples + sample).takeLast(64)
    }

    LaunchedEffect(
        directMagneticChannelEnabled,
        magneticRhythmBeepEnabled,
        magneticIncreaseNotificationsEnabled,
        liveMagneticMagnitudeMicroTesla,
        magneticEventTriggerThresholdMicroTesla
    ) {
        if (!directMagneticChannelEnabled) {
            previousLiveMagneticMagnitudeMicroTesla = null
            realtimeMagneticRhythmJob?.cancel()
            realtimeMagneticRhythmJob = null
            return@LaunchedEffect
        }

        val currentMagnitude = liveMagneticMagnitudeMicroTesla ?: return@LaunchedEffect
        val previousMagnitude = previousLiveMagneticMagnitudeMicroTesla
        previousLiveMagneticMagnitudeMicroTesla = currentMagnitude
        if (previousMagnitude == null) return@LaunchedEffect

        val deltaMicroTesla = currentMagnitude - previousMagnitude
        val triggerThresholdMicroTesla = magneticEventTriggerThresholdMicroTesla
        val crossedDisturbanceBand =
            previousMagnitude < triggerThresholdMicroTesla &&
                currentMagnitude >= triggerThresholdMicroTesla
        val sharpIncrease =
            deltaMicroTesla >= MAGNETIC_INCREASE_DELTA_THRESHOLD_UT &&
                currentMagnitude >= minOf(MAGNETIC_INCREASE_MIN_CURRENT_UT, triggerThresholdMicroTesla)
        val sustainedHighThresholdMicroTesla = maxOf(
            triggerThresholdMicroTesla + 8.0,
            MAGNETIC_SUSTAINED_HIGH_THRESHOLD_UT
        )
        val sustainedHigh = currentMagnitude >= sustainedHighThresholdMicroTesla
        val magneticEventDetected = crossedDisturbanceBand || sharpIncrease || sustainedHigh
        if (!magneticEventDetected) return@LaunchedEffect
        val severity = magneticAlertSeverity(
            currentMagnitudeMicroTesla = currentMagnitude,
            deltaMicroTesla = deltaMicroTesla
        )

        val now = System.currentTimeMillis()

        if (
            magneticIncreaseNotificationsEnabled &&
            hasPostNotificationsPermission(context) &&
            now - lastRealtimeMagneticNotificationEpochMs >= MAGNETIC_INCREASE_ALERT_COOLDOWN_MS
        ) {
            ensureMagneticIncreaseNotificationChannel(context)
            sendMagneticIncreaseNotification(
                context = context,
                previousMagnitudeMicroTesla = previousMagnitude,
                currentMagnitudeMicroTesla = currentMagnitude,
                deltaMicroTesla = deltaMicroTesla
            )
            lastRealtimeMagneticNotificationEpochMs = now
        }

        if (now - lastMagneticDetectionPopupEpochMs >= MAGNETIC_EVENT_POPUP_COOLDOWN_MS) {
            magneticDetectionPopup = buildMagneticDetectionPopupState(
                detectionEpochMs = now,
                previousMagnitudeMicroTesla = previousMagnitude,
                currentMagnitudeMicroTesla = currentMagnitude,
                deltaMicroTesla = deltaMicroTesla,
                triggerThresholdMicroTesla = triggerThresholdMicroTesla,
                crossedDisturbanceBand = crossedDisturbanceBand,
                sharpIncrease = sharpIncrease,
                sustainedHigh = sustainedHigh,
                severity = severity
            )
            lastMagneticDetectionPopupEpochMs = now
        }

        if (!magneticRhythmBeepEnabled) return@LaunchedEffect
        if (now - lastRealtimeMagneticRhythmEpochMs < MAGNETIC_RHYTHM_COOLDOWN_MS) return@LaunchedEffect

        realtimeMagneticRhythmJob?.cancel()
        realtimeMagneticRhythmJob = detectionScope.launch(Dispatchers.Default) {
            playMagneticRhythm(
                severity = severity,
                playMs = MAGNETIC_RHYTHM_PLAY_MS
            )
        }
        lastRealtimeMagneticRhythmEpochMs = now
    }

    val maxDeviceCandidatesToResolve = MAP_PIN_LIMIT_MAX
    var allDeviceCandidates by remember { mutableStateOf<List<DeviceLocationCandidate>>(emptyList()) }
    var deviceCandidatesPrepared by remember { mutableStateOf(false) }
    var deviceMapResolveInProgress by remember { mutableStateOf(false) }
    var deviceMapResolveStartedEpochMs by remember { mutableStateOf<Long?>(null) }
    var deviceMapResolveTotalCandidates by remember { mutableStateOf(0) }
    var deviceMapResolveProcessedCandidates by remember { mutableStateOf(0) }
    var deviceMapResolvePublishedPins by remember { mutableStateOf(0) }
    var deviceMapResolveCompletedEpochMs by remember { mutableStateOf<Long?>(null) }
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
            deviceCandidatesPrepared = false
            deviceMapResolveInProgress = false
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
                maxDeviceCandidatesToResolve.coerceAtLeast(2_000).coerceAtMost(MAP_PIN_LIMIT_MAX)
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
                    val recentDeviceEncounters = deviceEncounters
                        .asSequence()
                        .sortedByDescending { encounter -> encounterFreshnessEpochMs(encounter) }
                        .take(DEVICE_MAP_CANDIDATE_ENCOUNTER_CAP)
                        .toList()

                    val preferredSecondaryId = bestSecondaryId(recentDeviceEncounters)
                    val isCameraSource = latest.source == EncounterSource.CAMERA
                    val isCellSource = latest.source == EncounterSource.CELL
                    val owned = OwnedDeviceRegistry.keyFor(latest.source.name, latest.primaryId) in ownedDeviceKeys
                    val approachSignal = if (approachDetectionEnabled && isApproachEligibleSource(latest.source)) {
                        analyzeApproachSignal(recentDeviceEncounters)
                    } else {
                        null
                    }
                    val motionSignal = if (isCameraSource) null else analyzeMotionSignal(recentDeviceEncounters)
                    val connectionSecurity = analyzeConnectionSecurity(
                        source = latest.source.name,
                        encounters = recentDeviceEncounters
                    )
                    DeviceLocationCandidate(
                        source = latest.source.name,
                        primaryId = latest.primaryId,
                        secondaryId = preferredSecondaryId,
                        latestTimestampEpochMs = encounterFreshnessEpochMs(latest),
                        seenCount = deviceEncounters.size,
                        encounters = recentDeviceEncounters,
                        latestEncounter = latest,
                        previousEncounter = recentDeviceEncounters
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
                                encounters = recentDeviceEncounters,
                                isOwned = owned,
                                approachSignal = approachSignal,
                                homePoint = trackerHomePoint
                            )
                        },
                        cellThreat = if (isCellSource) analyzeCellThreat(recentDeviceEncounters) else null,
                        connectionSecurity = connectionSecurity
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
    LaunchedEffect(allDeviceCandidates) {
        RuntimeUiListMemoryGauge.updateDeviceMapCandidates(allDeviceCandidates)
    }

    val runtimeCachedDevicePins = remember { DeviceMapPinRuntimeCache.getFresh() }
    val diskCachedDevicePins = remember(context) { DeviceMapPinDiskCache.getFresh(context) }
    var estimatedDeviceLocationPins by remember(startupPrewarmedDevicePins, runtimeCachedDevicePins, diskCachedDevicePins) {
        mutableStateOf(
            if (runtimeCachedDevicePins.isNotEmpty()) {
                runtimeCachedDevicePins
            } else if (diskCachedDevicePins.isNotEmpty()) {
                diskCachedDevicePins
            } else {
                startupPrewarmedDevicePins
            }
        )
    }
    LaunchedEffect(estimatedDeviceLocationPins) {
        RuntimeUiListMemoryGauge.updateDeviceMapPinPool(estimatedDeviceLocationPins)
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
    val magneticMapCurrentLocation by if (selectedTab == 3 && selectedMapSubTab == 3) {
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
        3 -> magneticMapCurrentLocation
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
        boundedLruMutableMap<String, List<NoFlyZoneOverlayProvider.NoFlyZonePolygon>>(
            NO_FLY_ZONE_OVERLAY_CACHE_MAX_BUCKETS
        )
    }
    var noFlyZoneOverlays by remember(startupPrewarmedNoFlyZones, mapNoFlyZonesEnabled) {
        mutableStateOf(if (mapNoFlyZonesEnabled) startupPrewarmedNoFlyZones else emptyList())
    }
    var allTrackedAircraftPins by remember { mutableStateOf<List<MapPin>>(emptyList()) }

    DisposableEffect(context, noFlyZoneOverlayCache, resolvedLocationCache) {
        val appContext = context.applicationContext
        val callback = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) = Unit

            override fun onLowMemory() {
                resolvedLocationCache.clear()
                noFlyZoneOverlayCache.clear()
                noFlyZoneOverlays = emptyList()
                allTrackedAircraftPins = emptyList()
                releaseMapUiMemory(context = appContext, level = ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
            }

            override fun onTrimMemory(level: Int) {
                val shouldClearUiCaches =
                    level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                        level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                        level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND

                if (shouldClearUiCaches) {
                    resolvedLocationCache.clear()
                    noFlyZoneOverlayCache.clear()
                    noFlyZoneOverlays = emptyList()
                    if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                        allTrackedAircraftPins = emptyList()
                    }
                }

                releaseMapUiMemory(context = appContext, level = level)
            }
        }

        appContext.registerComponentCallbacks(callback)
        onDispose {
            appContext.unregisterComponentCallbacks(callback)
        }
    }

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
        DeviceMapPinRuntimeCache.clear()
        DeviceMapPinDiskCache.clear(context)
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
                val latestAircraftEpoch = latestAircraftEpochMs
                if (latestAircraftEpoch == null || freshnessEpochMs > latestAircraftEpoch) {
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
                            aircraftLabel = visualHints.displayLabel,
                            aircraftAffiliation = visualHints.affiliation.name.lowercase(Locale.US),
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

    LaunchedEffect(allDeviceCandidates, isDeviceLocationTabActive, sourceScanIntervals) {
        if (!isDeviceLocationTabActive) {
            return@LaunchedEffect
        }

        val nowEpochMs = System.currentTimeMillis()
        val cameraSourceType = SourceCatalog.KEY_CAMERA
        val cameraLastScanEpochMs = ScanSettings.getSourceLastScanEpochMs(context, cameraSourceType)
        val cameraIntervalSeconds = sourceScanIntervals[cameraSourceType]
            ?: ScanSettings.DEFAULT_CAMERA_SOURCE_SCAN_INTERVAL_SECONDS
        val cameraScanFreshnessWindowMs = maxOf(cameraIntervalSeconds * 3000L, 15_000L)
        val hasFreshCameraSourceScan =
            cameraLastScanEpochMs > 0L && (nowEpochMs - cameraLastScanEpochMs) <= cameraScanFreshnessWindowMs
        val cachedCameraPins = estimatedDeviceLocationPins.filter { pin ->
            pin.source == EncounterSource.CAMERA.name
        }
        val hasCameraCandidates = allDeviceCandidates.any { candidate ->
            candidate.source == EncounterSource.CAMERA.name
        }
        val shouldHoldCachedCameraPins = !hasCameraCandidates && !hasFreshCameraSourceScan && cachedCameraPins.isNotEmpty()

        if (!deviceCandidatesPrepared) {
            return@LaunchedEffect
        }

        if (allDeviceCandidates.isEmpty()) {
            estimatedDeviceLocationPins = if (shouldHoldCachedCameraPins) {
                cachedCameraPins
            } else {
                emptyList()
            }
            deviceMapResolveInProgress = false
            deviceMapResolveStartedEpochMs = System.currentTimeMillis()
            deviceMapResolveTotalCandidates = 0
            deviceMapResolveProcessedCandidates = 0
            deviceMapResolvePublishedPins = estimatedDeviceLocationPins.size
            deviceMapResolveCompletedEpochMs = System.currentTimeMillis()
            return@LaunchedEffect
        }

        deviceMapResolveInProgress = true
        deviceMapResolveStartedEpochMs = nowEpochMs
        deviceMapResolveTotalCandidates = allDeviceCandidates.size
        deviceMapResolveProcessedCandidates = 0
        deviceMapResolvePublishedPins = estimatedDeviceLocationPins.size
        deviceMapResolveCompletedEpochMs = null
        val resolvedPins = withContext(Dispatchers.Default) {
            val builtPins = ArrayList<MapPin>(allDeviceCandidates.size)
            allDeviceCandidates.forEachIndexed { index, candidate ->
                val sourceEnum = runCatching { EncounterSource.valueOf(candidate.source) }
                    .getOrDefault(EncounterSource.UNKNOWN_RF)
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

                val location = resolvedLocation ?: run {
                    if (isValidLatLon(candidate.latestEncounter.lat, candidate.latestEncounter.lon)) {
                        ResolvedDeviceLocation(
                            lat = candidate.latestEncounter.lat!!,
                            lon = candidate.latestEncounter.lon!!,
                            method = "Observed encounter location",
                            approximateRangeMeters = null,
                            resolvedFromTimestampEpochMs = candidate.latestEncounter.timestampEpochMs
                        )
                    } else {
                        null
                    }
                } ?: return@forEachIndexed

                if (!isValidLatLon(location.lat, location.lon)) return@forEachIndexed

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

                val rangeSnippet = location.approximateRangeMeters
                    ?.let { " • Approx range ${formatDistanceFeetMiles(it)}" }
                    .orEmpty()
                val approachSnippet = candidate.approachSignal
                    ?.takeIf { it.isApproaching }
                    ?.let { signal ->
                        " • Approaching (${(signal.confidence * 100.0).toInt()}%)"
                    }
                    .orEmpty()
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
                val cellThreatSnippet = when (candidate.cellThreat?.level) {
                    CellThreatLevel.HIGH -> " • Cell Threat HIGH"
                    CellThreatLevel.MEDIUM -> " • Cell Threat MEDIUM"
                    CellThreatLevel.LOW -> " • Cell Threat LOW"
                    else -> ""
                }
                val insecureConnectSnippet = if (candidate.connectionSecurity?.isInsecure == true) {
                    " • Avoid connect"
                } else {
                    ""
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
                val line3 = "$motionLine$topSpeedLine$ownershipSnippet$chainSnippet$trackerSnippet$cellThreatSnippet$insecureConnectSnippet"
                val pinSnippet = buildThreeLineSnippet(
                    line1 = line1,
                    line2 = line2,
                    line3 = line3
                )

                builtPins += MapPin(
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
                            .asSequence()
                            .take(12)
                            .map { it.rawPayloadJson }
                            .toList()
                    ),
                    timestampEpochMs = candidate.latestTimestampEpochMs,
                    source = candidate.source,
                    primaryId = candidate.primaryId,
                    secondaryId = candidate.secondaryId,
                    trackerFamilyBadge = bluetoothFamilyBadge,
                    encounterTimestampEpochMs = candidate.latestEncounter.timestampEpochMs,
                    aircraftIconType = aircraftVisualHints?.iconType,
                    aircraftLabel = aircraftVisualHints?.displayLabel,
                    aircraftAffiliation = aircraftVisualHints?.affiliation?.name?.lowercase(Locale.US),
                    headingDegrees = resolvedAircraftHeading,
                    motionBadge = motionBadge,
                    motionSpeedMps = candidate.motionSignal?.speedMps,
                    isLive = isLive
                )

                deviceMapResolveProcessedCandidates = index + 1
            }

            builtPins
        }

        val resolvedHasCameraPins = resolvedPins.any { pin -> pin.source == EncounterSource.CAMERA.name }
        val mergedPins = if (shouldHoldCachedCameraPins && !resolvedHasCameraPins) {
            (resolvedPins + cachedCameraPins)
                .distinctBy { pin -> "${pin.source}|${pin.primaryId}" }
        } else {
            resolvedPins
        }

        estimatedDeviceLocationPins = mergedPins
        deviceMapResolvePublishedPins = mergedPins.size
        deviceMapResolveProcessedCandidates = deviceMapResolveTotalCandidates
        deviceMapResolveCompletedEpochMs = System.currentTimeMillis()
        deviceMapResolveInProgress = false
        DeviceMapPinRuntimeCache.put(mergedPins)
        DeviceMapPinDiskCache.put(context, mergedPins)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!mapOnlyMode) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Detection", style = MaterialTheme.typography.headlineMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { magneticDialogVisible = true },
                    label = {
                        Text(
                            text = if (headerMagneticMagnitudeMicroTesla == null) {
                                "🧲 MAG n/a"
                            } else {
                                "🧲 MAG ${String.format(Locale.US, "%.1f", headerMagneticMagnitudeMicroTesla)} uT"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = magneticReadoutColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                )
            }
        }
        }

        if (magneticDialogVisible) {
            AlertDialog(
                onDismissRequest = { magneticDialogVisible = false },
                title = { Text("Magnetic Monitor") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (headerMagneticMagnitudeMicroTesla == null) {
                                "Current: n/a"
                            } else {
                                "Current: ${String.format(Locale.US, "%.1f", headerMagneticMagnitudeMicroTesla)} uT"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                        MagneticSignalVisualizer(
                            samples = magneticChartSamples,
                            thresholdMicroTesla = magneticThresholdSliderValue.toDouble(),
                            currentMagnitudeMicroTesla = headerMagneticMagnitudeMicroTesla
                        )
                        Text(
                            text = "Trigger threshold: ${String.format(Locale.US, "%.1f", magneticThresholdSliderValue)} uT"
                        )
                        Slider(
                            value = magneticThresholdSliderValue,
                            onValueChange = { value -> magneticThresholdSliderValue = value },
                            valueRange = ScanSettings.MIN_MAGNETIC_EVENT_TRIGGER_THRESHOLD_UT.toFloat()..
                                ScanSettings.MAX_MAGNETIC_EVENT_TRIGGER_THRESHOLD_UT.toFloat(),
                            onValueChangeFinished = {
                                onMagneticEventTriggerThresholdChanged(magneticThresholdSliderValue.toDouble())
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Direct magnetic channel")
                            Switch(
                                checked = directMagneticChannelEnabled,
                                onCheckedChange = onDirectMagneticChannelEnabledChanged
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Rhythm beep")
                            Switch(
                                checked = magneticRhythmBeepEnabled,
                                onCheckedChange = onMagneticRhythmBeepEnabledChanged,
                                enabled = directMagneticChannelEnabled
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Mag disturbance alerts")
                            Text(
                                text = if (magneticIncreaseNotificationsEnabled) "Enabled" else "Disabled",
                                color = if (magneticIncreaseNotificationsEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "Use Settings > Alerts > Magnetic disturbance alerts to change this.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "High-accuracy GPS capture is required for magnetic heatmap recording.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            selectedTab = 3
                            selectedMapSubTab = 3
                            magneticMapFocusRequestNonce += 1
                            magneticDialogVisible = false
                        }
                    ) {
                        Text("Open Magnetic Map")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { magneticDialogVisible = false }) {
                        Text("Done")
                    }
                }
            )
        }
        magneticDetectionPopup?.let { popup ->
            val popupLiveCurrentMagnitudeMicroTesla =
                liveMagneticMagnitudeMicroTesla ?: popup.currentMagnitudeMicroTesla
            MagneticDetectionPopupDialog(
                popup = popup,
                liveCurrentMagnitudeMicroTesla = popupLiveCurrentMagnitudeMicroTesla,
                onDismiss = { magneticDetectionPopup = null },
                confirmLabel = "Open Magnetic Map",
                onConfirm = {
                    selectedTab = 3
                    selectedMapSubTab = 3
                    magneticMapFocusRequestNonce += 1
                    magneticDetectionPopup = null
                },
                auxiliaryLabel = "Open Magnetic Monitor Controls",
                onAuxiliary = {
                    magneticDialogVisible = true
                    magneticDetectionPopup = null
                }
            )
        }
        if (!mapOnlyMode) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        }

        if (selectedTab == 0 && !mapOnlyMode) {
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
                if (permissionReadinessItems.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Permissions", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = permissionSummaryText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(onClick = { permissionsExpanded = !permissionsExpanded }) {
                                        Text(if (permissionsExpanded) "Hide" else "Expand")
                                    }
                                }

                                if (permissionsExpanded) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (runtimePermissionReadinessItems.isNotEmpty()) {
                                            Text(
                                                text = "Runtime",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            runtimePermissionReadinessItems.forEach { item ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                                ) {
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                                    ) {
                                                        Text(
                                                            text = item.title,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = item.currentValue,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = if (item.isMissing) Color(0xFFB3261E) else Color(0xFF2E7D32)
                                                        )
                                                    }
                                                    OutlinedButton(onClick = { onOpenReadinessSetting(item) }) {
                                                        Text("Open")
                                                    }
                                                }
                                            }
                                        }
                                        if (specialPermissionReadinessItems.isNotEmpty()) {
                                            Text(
                                                text = "Manifest and Special",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            specialPermissionReadinessItems.forEach { item ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                                ) {
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                                    ) {
                                                        Text(
                                                            text = item.title,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = item.currentValue,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = if (item.isMissing) Color(0xFFB3261E) else Color(0xFF2E7D32)
                                                        )
                                                    }
                                                    OutlinedButton(onClick = { onOpenReadinessSetting(item) }) {
                                                        Text("Open")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
                if (nonPermissionMissingReadinessItems.isNotEmpty()) {
                    item {
                        HomeResponsiveGrid(
                            items = nonPermissionMissingReadinessItems,
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
        } else if (selectedTab == 1 && !mapOnlyMode) {
            DevicesPage(
                allEncounters = meshInsightEncounters,
                approachDetectionEnabled = approachDetectionEnabled,
                ownedDeviceKeys = ownedDeviceKeys,
                wifiRandomizedOneOffSuppressionEnabled = wifiRandomizedOneOffSuppressionEnabled,
                bleRandomizedOneOffSuppressionEnabled = bleRandomizedOneOffSuppressionEnabled,
                onDeviceClick = onDeviceClick
            )
        } else if (selectedTab == 2 && !mapOnlyMode) {
            FlocksPage(
                allEncounters = meshInsightEncounters
            )
        } else if (selectedTab == 3) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!mapOnlyMode) {
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
                    Tab(
                        selected = selectedMapSubTab == 3,
                        onClick = { selectedMapSubTab = 3 },
                        text = { Text("Magnetic Map") }
                    )
                }
                }

                if (selectedMapSubTab == 0) {
                    val deviceMapAircraftRadiusMeters = deviceMapAircraftRadiusMiles.coerceIn(25, 75) * 1609.344
                    val allModeEnabled = !deviceMapLiveOnlyEnabled && !movingOnlyOnDeviceMap && !sinceSnapshotOnlyOnDeviceMap
                    val resolveElapsedMs = when {
                        deviceMapResolveStartedEpochMs == null -> 0L
                        deviceMapResolveInProgress -> System.currentTimeMillis() - (deviceMapResolveStartedEpochMs ?: 0L)
                        deviceMapResolveCompletedEpochMs != null ->
                            (deviceMapResolveCompletedEpochMs ?: 0L) - (deviceMapResolveStartedEpochMs ?: 0L)
                        else -> 0L
                    }.coerceAtLeast(0L)
                    val deviceMapPins by produceState(
                        estimatedDeviceLocationPins,
                        estimatedDeviceLocationPins,
                        deviceMapCurrentLocation,
                        deviceMapAircraftRadiusMeters,
                        deviceMapLiveOnlyEnabled,
                        mapLiveNowEpochMs,
                        movingOnlyOnDeviceMap,
                        sinceSnapshotOnlyOnDeviceMap,
                        deviceMapSnapshotEpochMs,
                        stickyCompassMapEnabled
                    ) {
                        value = withContext(Dispatchers.Default) {
                            val nowEpochMs = mapLiveNowEpochMs
                            val basePins = estimatedDeviceLocationPins
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
                                    if (!deviceMapLiveOnlyEnabled) {
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

                            val stickyScopedPins = if (
                                stickyCompassMapEnabled &&
                                    deviceMapLiveOnlyEnabled &&
                                    deviceMapCurrentLocation != null &&
                                    basePins.isNotEmpty()
                            ) {
                                deviceMapCurrentLocation?.let { center ->
                                    val radiusMeters = calculateRealtimeCoverageRadiusMeters(
                                        center = center,
                                        pins = basePins,
                                        nowEpochMs = nowEpochMs,
                                        recentWindowMs = MAP_COVERAGE_RECENT_WINDOW_MS
                                    )
                                    if (radiusMeters == null || radiusMeters <= 0.0) {
                                        basePins
                                    } else {
                                        basePins.filter { pin ->
                                            distanceFromLocationMeters(
                                                fromLat = center.lat,
                                                fromLon = center.lon,
                                                toLat = pin.position.latitude,
                                                toLon = pin.position.longitude
                                            )?.let { distanceMeters ->
                                                distanceMeters <= radiusMeters
                                            } ?: false
                                        }
                                    }
                                } ?: basePins
                            } else {
                                basePins
                            }

                            stickyScopedPins.let(::spreadOverlappingMapPins)
                        }
                    }
                    LaunchedEffect(deviceMapPins) {
                        RuntimeUiListMemoryGauge.updateActiveDeviceMapPins(deviceMapPins)
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
                        additionalDiagnostics = listOf(
                            "Device map mode: ${if (allModeEnabled) "ALL" else "FILTERED"}",
                            "Candidates prepared: ${allDeviceCandidates.size} (ready=${if (deviceCandidatesPrepared) "yes" else "no"})",
                            "Resolve status: ${if (deviceMapResolveInProgress) "running" else "idle"}",
                            "Resolve progress: ${deviceMapResolveProcessedCandidates}/${deviceMapResolveTotalCandidates}",
                            "Published pins: ${deviceMapResolvePublishedPins}",
                            "Resolve elapsed: ${resolveElapsedMs} ms"
                        ),
                        pinLimit = cellDevicePinLimit,
                        onPinLimitChange = { cellDevicePinLimit = it },
                        mapClusteringEnabled = mapClusteringEnabled,
                        onMapClusteringEnabledChange = onMapClusteringEnabledChanged,
                        mapClusterRangeLevel = mapClusterRangeLevel,
                        onMapClusterRangeLevelChange = onMapClusterRangeLevelChanged,
                        mapScannerSweepAnimationEnabled = mapScannerSweepAnimationEnabled,
                        mapScannerSweepAnimationSpeedPreset = mapScannerSweepAnimationSpeedPreset,
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
                        useSourceOnlyPinColors = true,
                        enableVerticalScroll = true,
                        showLiveOnlyControl = true,
                        liveOnlyEnabled = deviceMapLiveOnlyEnabled,
                        onLiveOnlyEnabledChange = { liveOnlyOnDeviceMap = it },
                        identityModeEnabled = identityModeOnDeviceMap,
                        onIdentityModeEnabledChange = { identityModeOnDeviceMap = it },
                        identityShowFullNamesEnabled = mapIdentityFullNamesEnabled,
                        onIdentityShowFullNamesEnabledChange = onMapIdentityFullNamesEnabledChanged,
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
                        mapOnlyPresentation = mapOnlyMode,
                        externalZoomCommandNonce = pipZoomCommandNonce,
                        externalZoomCommandDelta = pipZoomCommandDelta,
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
                                    val bounded = selectVisiblePinsWithSourceCoverage(
                                        pins = pins,
                                        pinLimit = preLimit
                                    )
                                    runCatching {
                                        spreadOverlappingMapPinsMinimal(
                                            pins = bounded
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
                        mapScannerSweepAnimationSpeedPreset = mapScannerSweepAnimationSpeedPreset,
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
                        useSourceOnlyPinColors = true,
                        enableVerticalScroll = true,
                        showLiveOnlyControl = true,
                        liveOnlyEnabled = liveOnlyOnBluetoothMap,
                        onLiveOnlyEnabledChange = { liveOnlyOnBluetoothMap = it },
                        identityModeEnabled = identityModeOnBluetoothMap,
                        onIdentityModeEnabledChange = { identityModeOnBluetoothMap = it },
                        identityShowFullNamesEnabled = mapIdentityFullNamesEnabled,
                        onIdentityShowFullNamesEnabledChange = onMapIdentityFullNamesEnabledChanged,
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
                        liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds
                    )
                } else if (selectedMapSubTab == 2) {
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
                            mapScannerSweepAnimationSpeedPreset = mapScannerSweepAnimationSpeedPreset,
                            onPinDetailsClick = { pin ->
                                onDeviceMapPinClick(
                                    pin.source,
                                    pin.primaryId,
                                    pin.position.latitude,
                                    pin.position.longitude,
                                    pin.encounterTimestampEpochMs ?: pin.timestampEpochMs
                                )
                            },
                            useSourceOnlyPinColors = true,
                            enableVerticalScroll = true,
                            showLiveOnlyControl = true,
                            liveOnlyEnabled = liveOnlyOnFlightMap,
                            onLiveOnlyEnabledChange = { liveOnlyOnFlightMap = it },
                            identityModeEnabled = identityModeOnFlightMap,
                            onIdentityModeEnabledChange = { identityModeOnFlightMap = it },
                            identityShowFullNamesEnabled = mapIdentityFullNamesEnabled,
                            onIdentityShowFullNamesEnabledChange = onMapIdentityFullNamesEnabledChanged,
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
                            liveMapUpdateIntervalSeconds = liveMapUpdateIntervalSeconds
                        )
                    }
                } else {
                    val magneticMapSamples = remember(meshInsightEncounters, magneticMapPinLimit) {
                        extractMagneticHeatmapSamples(
                            encounters = meshInsightEncounters,
                            maxSamples = magneticMapPinLimit
                        )
                    }

                    MagneticMonitorMapPage(
                        samples = magneticMapSamples,
                        currentLocation = magneticMapCurrentLocation,
                        focusRequestNonce = magneticMapFocusRequestNonce,
                        onOpenStatusTab = { selectedTab = 0 },
                        pinLimit = magneticMapPinLimit,
                        onPinLimitChange = { magneticMapPinLimit = it },
                        showTrafficLayer = mapTrafficEnabled
                    )
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

@Composable
private fun MagneticSignalVisualizer(
    samples: List<Double>,
    thresholdMicroTesla: Double,
    currentMagnitudeMicroTesla: Double?
) {
    val panelColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val waveColor = Color(0xFF2E7D32)
    val waveFillColor = Color(0xFF2E7D32).copy(alpha = 0.18f)
    val thresholdColor = Color(0xFFE65100)

    Card(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(panelColor)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas

            drawLine(
                color = trackColor,
                start = Offset(0f, size.height * 0.25f),
                end = Offset(size.width, size.height * 0.25f),
                strokeWidth = 1f
            )
            drawLine(
                color = trackColor,
                start = Offset(0f, size.height * 0.5f),
                end = Offset(size.width, size.height * 0.5f),
                strokeWidth = 1f
            )
            drawLine(
                color = trackColor,
                start = Offset(0f, size.height * 0.75f),
                end = Offset(size.width, size.height * 0.75f),
                strokeWidth = 1f
            )

            val mergedSamples = if (currentMagnitudeMicroTesla == null) {
                samples
            } else {
                (samples + currentMagnitudeMicroTesla).takeLast(64)
            }
            if (mergedSamples.size < 2) return@Canvas

            val lowBound = (thresholdMicroTesla - 28.0).coerceAtLeast(10.0)
            val highBound = (thresholdMicroTesla + 36.0).coerceAtMost(180.0)
            val valueRange = (highBound - lowBound).coerceAtLeast(1.0)

            val thresholdY = size.height -
                (((thresholdMicroTesla - lowBound) / valueRange).toFloat().coerceIn(0f, 1f) * size.height)
            drawLine(
                color = thresholdColor,
                start = Offset(0f, thresholdY),
                end = Offset(size.width, thresholdY),
                strokeWidth = 2f
            )

            val points = mergedSamples.mapIndexed { index, value ->
                val x = (index.toFloat() / (mergedSamples.lastIndex).toFloat()) * size.width
                val normalized = ((value - lowBound) / valueRange).toFloat().coerceIn(0f, 1f)
                val y = size.height - (normalized * size.height)
                Offset(x, y)
            }

            for (i in 1 until points.size) {
                val previous = points[i - 1]
                val current = points[i]
                drawLine(
                    color = waveFillColor,
                    start = Offset(previous.x, previous.y),
                    end = Offset(previous.x, size.height),
                    strokeWidth = 3.5f
                )
                drawLine(
                    color = waveColor,
                    start = previous,
                    end = current,
                    strokeWidth = 3f
                )
            }
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
    val safeSelectedTab = selectedTab.coerceIn(0, 2)
    val tabs = listOf("Alerts", "Errors", "Encounters")

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TabRow(selectedTabIndex = safeSelectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = safeSelectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (safeSelectedTab == 0) {
                DetectionLogsPage(
                    logs = logs,
                    onClearLogs = onClearLogs,
                    onOpenApproachMap = onOpenApproachMap,
                    onOpenNoFlyIncidentPath = onOpenNoFlyIncidentPath,
                    onOpenDeviceDetails = onOpenDeviceDetails
                )
            } else if (safeSelectedTab == 1) {
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
    val stingrayCount = remember(logs) { logs.count { it.type == AlertLogType.STINGRAY } }
    val cameraInViewCount = remember(logs) { logs.count { it.type == AlertLogType.CAMERA_IN_VIEW } }
    val noFlyCount = remember(logs) { logs.count { it.type == AlertLogType.NO_FLY_PASS_THROUGH } }
    val tabLabels = listOf(
        "All (${logs.size})",
        "Approach ($approachCount)",
        "Tracker ($trackerCount)",
        "Stingray ($stingrayCount)",
        "Camera ($cameraInViewCount)",
        "No-Fly ($noFlyCount)"
    )
    val safeSelectedLogTab = selectedLogTab.coerceIn(0, tabLabels.lastIndex)

    val filteredLogs = remember(logs, safeSelectedLogTab) {
        logs.asSequence()
            .filter { entry ->
                when (safeSelectedLogTab) {
                    1 -> entry.type == AlertLogType.APPROACH
                    2 -> entry.type == AlertLogType.TRACKER
                    3 -> entry.type == AlertLogType.STINGRAY
                    4 -> entry.type == AlertLogType.CAMERA_IN_VIEW
                    5 -> entry.type == AlertLogType.NO_FLY_PASS_THROUGH
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
                AssistChip(onClick = { }, label = { Text("Stingray $stingrayCount") })
                AssistChip(onClick = { }, label = { Text("Camera $cameraInViewCount") })
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
                AlertLogType.STINGRAY -> Color(0xFF8D6E63)
                AlertLogType.CAMERA_IN_VIEW -> Color(0xFF455A64)
                AlertLogType.NO_FLY_PASS_THROUGH -> Color(0xFFEF6C00)
                AlertLogType.NFC -> Color(0xFF2E7D32)
            }
            val typeLabel = when (entry.type) {
                AlertLogType.APPROACH -> "Approach"
                AlertLogType.TRACKER -> "Tracker"
                AlertLogType.STINGRAY -> "Stingray"
                AlertLogType.CAMERA_IN_VIEW -> "Camera"
                AlertLogType.NO_FLY_PASS_THROUGH -> "No-Fly"
                AlertLogType.NFC -> "NFC"
            }
            val isApproachEntry = entry.type == AlertLogType.APPROACH
            val isNoFlyEntry = entry.type == AlertLogType.NO_FLY_PASS_THROUGH
            val noFlyZoneSummary = if (isNoFlyEntry) {
                parseNoFlyZoneSummaryFromLogMessage(entry.message) ?: "No-fly zone"
            } else {
                null
            }
            val confidenceLabel = entry.confidence
                ?.let { " • ${String.format(Locale.US, "%.0f%%", it * 100.0)} confidence" }
                .orEmpty()
            val isClickable = true
            val tapHint = when {
                isApproachEntry -> "Tap to open approach map"
                isNoFlyEntry -> "Tap to open no-fly incident path"
                else -> "Tap to open device details"
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
                                    else -> onOpenDeviceDetails(entry.source, entry.primaryId)
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
    var peerStatusNowEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }

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

    LaunchedEffect(Unit) {
        tickerFlow(periodMs = 5_000L).collect { now ->
            peerStatusNowEpochMs = now
        }
    }

    val connectedCount = chainMeshSnapshot.peers.count { it.state == ChainPeerState.CONNECTED }
    val requestedCount = chainMeshSnapshot.peers.count { it.state == ChainPeerState.REQUESTED }
    val discoveredCount = chainMeshSnapshot.peers.count { it.state == ChainPeerState.DISCOVERED }
    val failedCount = chainMeshSnapshot.peers.count { it.state == ChainPeerState.FAILED }
    val unconnectedCount = chainMeshSnapshot.peers.count { it.state != ChainPeerState.CONNECTED }
    val hasStrongSharedSecret = chainSharedSecret.trim().length >= CHAIN_SHARED_SECRET_MIN_LENGTH
    val meshReady = chainLinkEnabled && hasStrongSharedSecret
    val wipeGateLabel = if (meshWipeGateState.enabled) "Active" else "Inactive"
    val mapStyleOptions = rememberMapStyleOptionsForTheme()
    val hasLocationPermission = remember(context) {
        AppPermissions.hasAnyLocationPermission(context)
    }
    val meshMapProperties = remember(hasLocationPermission, mapStyleOptions) {
        MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            mapStyleOptions = mapStyleOptions
        )
    }
    val meshMapCameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.4219999, -122.0840575), 2f)
    }
    val currentLocation by LocationSnapshotProvider.observe(
        context,
        minUpdateIntervalMs = 2_000L
    ).collectAsState(initial = LocationSnapshotProvider.read(context))
    val meshAutoFocusAccuracyMeters = 20f
    var meshIdentityModeEnabled by rememberSaveable { mutableStateOf(true) }
    var meshIdentityShowFullNamesEnabled by rememberSaveable {
        mutableStateOf(ScanSettings.DEFAULT_MAP_IDENTITY_FULL_NAMES_ENABLED)
    }
    var meshAutoCenteredOnPreciseLocation by rememberSaveable { mutableStateOf(false) }
    var meshLockedToCurrentLocation by rememberSaveable { mutableStateOf(false) }
    val peersWithSharedLocation = remember(chainMeshSnapshot.peers) {
        chainMeshSnapshot.peers.filter { peer ->
            isValidLatLon(peer.sharedLocationLat, peer.sharedLocationLon)
        }
    }
    val peerMapPins = remember(peersWithSharedLocation, meshIdentityModeEnabled, meshIdentityShowFullNamesEnabled) {
        peersWithSharedLocation.map { peer ->
            val peerPoint = LatLng(peer.sharedLocationLat!!, peer.sharedLocationLon!!)
            val identityLabel = peer.deviceName?.trim()?.takeIf { it.isNotBlank() } ?: peer.nodeId
            val glyph = if (!meshIdentityModeEnabled) {
                null
            } else if (meshIdentityShowFullNamesEnabled) {
                identityLabel
            } else {
                identityGlyph(identityLabel)
            }

            peer to MapPin(
                position = peerPoint,
                title = buildPinTitle(
                    sourceLabel = if (meshIdentityModeEnabled) identityLabel else "Mesh Peer",
                    primaryId = peer.nodeId,
                    secondaryId = if (meshIdentityModeEnabled) null else peer.host,
                    motionBadge = null
                ),
                snippetBuilder = {
                    val host = peer.host.takeIf { it.isNotBlank() } ?: "host n/a"
                    val accuracy = peer.sharedLocationAccuracyMeters
                        ?.takeIf { it.isFinite() && it > 0f }
                        ?.let { value -> " • ${String.format(Locale.US, "%.1f m", value)}" }
                        .orEmpty()
                    "${peer.state.name} • $host$accuracy"
                },
                searchableMetadata = "${peer.nodeId} ${peer.deviceName.orEmpty()} ${peer.host} ${peer.state.name}",
                timestampEpochMs = peer.lastSeenEpochMs,
                source = SourceCatalog.SOURCE_ARGUS_MESH,
                primaryId = peer.nodeId,
                secondaryId = peer.deviceName,
                markerGlyphOverride = glyph,
                trackerFamilyBadge = peer.state.name,
                encounterTimestampEpochMs = peer.lastSeenEpochMs,
                isLive = peer.state == ChainPeerState.CONNECTED
            )
        }
    }
    val meshLocationPreciseEnough = currentLocation?.accuracyMeters
        ?.let { accuracy -> accuracy in 0f..meshAutoFocusAccuracyMeters }
        ?: false
    LaunchedEffect(peersWithSharedLocation) {
        if (meshLockedToCurrentLocation) return@LaunchedEffect
        if (peersWithSharedLocation.isEmpty()) return@LaunchedEffect
        if (peersWithSharedLocation.size == 1) {
            val only = peersWithSharedLocation.first()
            runCatching {
                meshMapCameraPositionState.move(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(only.sharedLocationLat!!, only.sharedLocationLon!!),
                        12f
                    )
                )
            }
            return@LaunchedEffect
        }
        val bounds = LatLngBounds.builder().apply {
            peersWithSharedLocation.forEach { peer ->
                include(LatLng(peer.sharedLocationLat!!, peer.sharedLocationLon!!))
            }
        }.build()
        runCatching {
            meshMapCameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        }
    }
    LaunchedEffect(currentLocation, meshLocationPreciseEnough, meshAutoCenteredOnPreciseLocation) {
        if (meshAutoCenteredOnPreciseLocation) return@LaunchedEffect
        if (!meshLocationPreciseEnough) return@LaunchedEffect
        val location = currentLocation ?: return@LaunchedEffect
        runCatching {
            meshMapCameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(location.lat, location.lon), 16f),
                durationMs = 700
            )
        }
        meshAutoCenteredOnPreciseLocation = true
        meshLockedToCurrentLocation = true
    }
    val connectedPeers = remember(chainMeshSnapshot.peers) {
        chainMeshSnapshot.peers
            .filter { it.state == ChainPeerState.CONNECTED }
            .sortedByDescending { it.lastSeenEpochMs }
    }
    val connectingPeers = remember(chainMeshSnapshot.peers) {
        chainMeshSnapshot.peers
            .filter { it.state == ChainPeerState.REQUESTED || it.state == ChainPeerState.DISCOVERED }
            .sortedByDescending { it.lastSeenEpochMs }
    }
    val isConnectingNow = refreshInProgress || syncInProgress || connectingPeers.isNotEmpty()
    val meshConnectionLabel = when {
        !chainLinkEnabled -> "Disabled"
        connectedCount > 0 -> "Connected"
        isConnectingNow -> "Connecting"
        else -> "Disconnected"
    }
    val meshConnectionColor = when {
        !chainLinkEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
        connectedCount > 0 -> Color(0xFF2E7D32)
        isConnectingNow -> Color(0xFFE65100)
        else -> Color(0xFFB3261E)
    }
    val meshTwoColumn = LocalConfiguration.current.screenWidthDp >= 760

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
                    Text("Mesh Map", fontWeight = FontWeight.Bold)
                    Text(
                        "Shared peer locations rendered with device-map style pins. Identity mode shows device names on markers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("Identity Mode")
                        Switch(
                            checked = meshIdentityModeEnabled,
                            onCheckedChange = { meshIdentityModeEnabled = it }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("Full identity labels")
                        Switch(
                            checked = meshIdentityShowFullNamesEnabled,
                            onCheckedChange = { meshIdentityShowFullNamesEnabled = it },
                            enabled = meshIdentityModeEnabled
                        )
                    }
                    Text(
                        "Auto-focus ${if (meshLocationPreciseEnough) "ready" else "waiting"}: requires location accuracy <= ${meshAutoFocusAccuracyMeters.toInt()} m (now ${currentLocation?.accuracyMeters?.let { String.format(Locale.US, "%.1f m", it) } ?: "n/a"}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (meshLocationPreciseEnough) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!hasLocationPermission) {
                        Text(
                            "Grant location permission to use the native My Location button on the map.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE65100)
                        )
                    }
                    if (peersWithSharedLocation.isEmpty()) {
                        Text(
                            "No peers with shared location yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = meshMapCameraPositionState,
                                properties = meshMapProperties,
                                uiSettings = MapUiSettings(
                                    zoomControlsEnabled = true,
                                    myLocationButtonEnabled = hasLocationPermission,
                                    compassEnabled = true
                                )
                            ) {
                                peerMapPins.forEach { (peer, pin) ->
                                    Marker(
                                        state = remember(peer.nodeId, pin.position) { MarkerState(position = pin.position) },
                                        title = pin.title,
                                        snippet = pin.snippetBuilder?.invoke(),
                                        icon = markerIconForPin(
                                            pin = pin,
                                            useSourceOnlyPinColors = true,
                                            showTrackerFamilyBadge = true
                                        )
                                    )
                                }
                            }

                        }
                    }
                }
            }
        }
        item {
            val statusCard: @Composable () -> Unit = {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Status", fontWeight = FontWeight.Bold)
                        Text("Chain Link: ${if (chainLinkEnabled) "On" else "Off"}")
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isConnectingNow && chainLinkEnabled && connectedCount == 0) {
                                ConnectingPulseDot()
                            }
                            Text(
                                "Mesh State: $meshConnectionLabel",
                                color = meshConnectionColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            "Passphrase: ${if (chainSharedSecret.isBlank()) "Missing" else if (hasStrongSharedSecret) "Strong" else "Weak"}"
                        )
                        Text("Peers: $connectedCount connected • $unconnectedCount not connected")
                        Text(
                            "Pending: $requestedCount requested • $discoveredCount discovered • $failedCount failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isConnectingNow && chainLinkEnabled) {
                            Text(
                                "Connection in progress. Keep both devices on the same LAN with app open and matching passphrase.",
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text("Wipe Gate: $wipeGateLabel")
                        if (!meshReady) {
                            Text(
                                "Enable Chain Link and set a strong shared passphrase (min $CHAIN_SHARED_SECRET_MIN_LENGTH chars) to sync securely.",
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
            }
            val setupCard: @Composable () -> Unit = {
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
                        if (chainLinkEnabled && chainSharedSecret.isNotBlank() && !hasStrongSharedSecret) {
                            Text(
                                "Passphrase is too short for secure mesh operations. Use at least $CHAIN_SHARED_SECRET_MIN_LENGTH characters.",
                                color = Color(0xFFB3261E),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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
            val actionsCard: @Composable () -> Unit = {
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
            val peersCard: @Composable () -> Unit = {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Peer Connectivity", fontWeight = FontWeight.Bold)
                        if (connectingPeers.isNotEmpty()) {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ConnectingPulseDot()
                                Text(
                                    "Connecting now",
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            connectingPeers.take(8).forEach { peer ->
                                val peerDisplay = peer.deviceName?.takeIf { it.isNotBlank() } ?: peer.nodeId
                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ConnectingPulseDot()
                                    Text("$peerDisplay @ ${peer.host}")
                                }
                                Text(
                                    "${peer.state.name} • last handshake ${formatAgeFromEpoch(peer.lastSeenEpochMs, peerStatusNowEpochMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!peer.lastFailure.isNullOrBlank()) {
                                    Text(
                                        "Reason: ${peer.lastFailure}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB3261E)
                                    )
                                }
                            }
                        }
                        Text("Connected peers", fontWeight = FontWeight.SemiBold)
                        if (connectedPeers.isEmpty()) {
                            Text("No connected peers.")
                        } else {
                            connectedPeers.take(8).forEach { peer ->
                                val peerDisplay = peer.deviceName?.takeIf { it.isNotBlank() } ?: peer.nodeId
                                Text("$peerDisplay @ ${peer.host}")
                                Text(
                                    "Last handshake ${formatAgeFromEpoch(peer.lastSeenEpochMs, peerStatusNowEpochMs)} • Last sync ${formatAgeFromEpoch(peer.lastSuccessfulSyncEpochMs, peerStatusNowEpochMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (meshTwoColumn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        setupCard()
                        actionsCard()
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        statusCard()
                        peersCard()
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    statusCard()
                    setupCard()
                    actionsCard()
                    peersCard()
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

private data class MagneticHeatSample(
    val lat: Double,
    val lon: Double,
    val magnitudeMicroTesla: Double,
    val timestampEpochMs: Long,
    val locationAccuracyMeters: Double?
)

private data class MagneticHotspotCell(
    val cellKey: String,
    val lat: Double,
    val lon: Double,
    val averageMagnitudeMicroTesla: Double,
    val sampleCount: Int,
    val latestTimestampEpochMs: Long
)

private fun extractMagneticHeatmapSamples(
    encounters: List<Encounter>,
    maxSamples: Int = 1500
): List<MagneticHeatSample> {
    return encounters
        .asSequence()
        .filter { encounter ->
            encounter.provenance == EncounterProvenance.LOCAL &&
                isDirectSignalChannel(encounter, "magnetic") &&
                isValidLatLon(encounter.lat, encounter.lon)
        }
        .mapNotNull { encounter ->
            val payload = parseEncounterPayload(encounter) ?: return@mapNotNull null
            val magnitude = payload.optDoubleOrNull("magnitudeMicroTesla") ?: return@mapNotNull null
            MagneticHeatSample(
                lat = encounter.lat!!,
                lon = encounter.lon!!,
                magnitudeMicroTesla = magnitude,
                timestampEpochMs = encounter.timestampEpochMs,
                locationAccuracyMeters = payload.optDoubleOrNull("locationAccuracyMeters")
            )
        }
        .sortedByDescending { it.timestampEpochMs }
        .take(maxSamples.coerceIn(250, MAP_PIN_LIMIT_MAX))
        .toList()
}

private fun aggregateMagneticHotspots(
    samples: List<MagneticHeatSample>,
    maxCells: Int = 700,
    cellDegrees: Double = 0.00012
): List<MagneticHotspotCell> {
    if (samples.isEmpty()) return emptyList()

    data class Acc(
        var latSum: Double = 0.0,
        var lonSum: Double = 0.0,
        var magSum: Double = 0.0,
        var count: Int = 0,
        var latestTs: Long = 0L
    )

    val cells = linkedMapOf<String, Acc>()
    samples.forEach { sample ->
        val latCell = kotlin.math.floor(sample.lat / cellDegrees).toInt()
        val lonCell = kotlin.math.floor(sample.lon / cellDegrees).toInt()
        val key = "$latCell:$lonCell"
        val acc = cells.getOrPut(key) { Acc() }
        acc.latSum += sample.lat
        acc.lonSum += sample.lon
        acc.magSum += sample.magnitudeMicroTesla
        acc.count += 1
        if (sample.timestampEpochMs > acc.latestTs) {
            acc.latestTs = sample.timestampEpochMs
        }
    }

    return cells.entries
        .map { (cellKey, acc) ->
            MagneticHotspotCell(
                cellKey = cellKey,
                lat = acc.latSum / acc.count,
                lon = acc.lonSum / acc.count,
                averageMagnitudeMicroTesla = acc.magSum / acc.count,
                sampleCount = acc.count,
                latestTimestampEpochMs = acc.latestTs
            )
        }
        .sortedWith(
            compareByDescending<MagneticHotspotCell> { it.sampleCount }
                .thenByDescending { it.latestTimestampEpochMs }
        )
        .take(maxCells.coerceIn(100, 1200))
}

@Composable
private fun MagneticMonitorMapPage(
    samples: List<MagneticHeatSample>,
    currentLocation: DetectionLocation?,
    focusRequestNonce: Int,
    onOpenStatusTab: () -> Unit,
    pinLimit: Int,
    onPinLimitChange: (Int) -> Unit,
    showTrafficLayer: Boolean
) {
    val context = LocalContext.current
    val hasMapsApiKey = remember(context) { hasGoogleMapsApiKey(context) }
    val mapStyleOptions = rememberMapStyleOptionsForTheme()
    val hasLocationPermission = remember(context) {
        AppPermissions.hasAnyLocationPermission(context)
    }
    val mapProperties = remember(hasLocationPermission, mapStyleOptions, showTrafficLayer) {
        MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            isTrafficEnabled = showTrafficLayer,
            mapStyleOptions = mapStyleOptions
        )
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.4219999, -122.0840575), 12f)
    }

    val pinLimitOptions = listOf(250, 500, 1000, 1500, 2500, 5000, 7500, 10000)
    val gpsRequirementOptions = ScanSettings.ALLOWED_MAGNETIC_GPS_ACCURACY_REQUIREMENT_METERS
    var pinLimitExpanded by remember { mutableStateOf(false) }
    var gpsRequirementExpanded by remember { mutableStateOf(false) }
    var controlsExpanded by rememberSaveable { mutableStateOf(false) }
    var hasAppliedInitialFocus by rememberSaveable { mutableStateOf(false) }
    var lastHandledFocusRequestNonce by rememberSaveable { mutableStateOf(Int.MIN_VALUE) }
    var magneticGpsAccuracyRequirementMeters by rememberSaveable {
        mutableStateOf(ScanSettings.getMagneticGpsAccuracyRequirementMeters(context))
    }
    val hasHighAccuracyFix = LocationSnapshotProvider.isHighAccuracyFix(
        context = context,
        location = currentLocation,
        thresholdMeters = magneticGpsAccuracyRequirementMeters
    )
    val lowestSeenAccuracyMeters = remember(samples) {
        samples
            .asSequence()
            .mapNotNull { sample -> sample.locationAccuracyMeters }
            .filter { value -> value.isFinite() && value > 0.0 }
            .minOrNull()
    }
    val visibleSamples = remember(samples, pinLimit) { samples.take(pinLimit.coerceAtLeast(1)) }
    val sampleCellDegrees = 0.00012
    val hotspotCells = remember(visibleSamples) {
        aggregateMagneticHotspots(
            samples = visibleSamples,
            maxCells = 700,
            cellDegrees = sampleCellDegrees
        )
    }
    val samplesByHotspotCellKey = remember(visibleSamples, sampleCellDegrees) {
        visibleSamples
            .groupBy { sample ->
                val latCell = kotlin.math.floor(sample.lat / sampleCellDegrees).toInt()
                val lonCell = kotlin.math.floor(sample.lon / sampleCellDegrees).toInt()
                "$latCell:$lonCell"
            }
            .mapValues { (_, grouped) -> grouped.sortedBy { it.timestampEpochMs } }
    }
    var selectedHotspotCellKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedHotspotCell = remember(hotspotCells, selectedHotspotCellKey) {
        hotspotCells.firstOrNull { it.cellKey == selectedHotspotCellKey }
    }
    val selectedHotspotSamples = remember(samplesByHotspotCellKey, selectedHotspotCellKey) {
        selectedHotspotCellKey?.let { key -> samplesByHotspotCellKey[key] }.orEmpty()
    }
    val recentLiveSamples = remember(visibleSamples) {
        val latestTs = visibleSamples.maxOfOrNull { it.timestampEpochMs } ?: 0L
        val recentCutoff = latestTs - (90_000L)
        visibleSamples.filter { it.timestampEpochMs >= recentCutoff }.take(120)
    }

    val magneticCollectingNow = hasHighAccuracyFix && currentLocation != null
    val magneticCollectionStatusLabel = if (magneticCollectingNow) "Collecting" else "Waiting"
    val magneticCollectionStatusColor = if (magneticCollectingNow) Color(0xFF2E7D32) else Color(0xFFE65100)

    LaunchedEffect(focusRequestNonce, currentLocation, samples) {
        val shouldRefocus = !hasAppliedInitialFocus || focusRequestNonce != lastHandledFocusRequestNonce
        if (!shouldRefocus) return@LaunchedEffect
        val target = currentLocation?.let { LatLng(it.lat, it.lon) }
            ?: samples.firstOrNull()?.let { LatLng(it.lat, it.lon) }
            ?: return@LaunchedEffect
        cameraPositionState.animate(
            update = CameraUpdateFactory.newLatLngZoom(target, 16f),
            durationMs = 700
        )
        hasAppliedInitialFocus = true
        lastHandledFocusRequestNonce = focusRequestNonce
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { controlsExpanded = !controlsExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Magnetic Heatmap", style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("●", color = magneticCollectionStatusColor, fontWeight = FontWeight.Bold)
                        Text(
                            magneticCollectionStatusLabel,
                            color = magneticCollectionStatusColor,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                AnimatedVisibility(visible = controlsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Records high-accuracy GPS magnetic samples and builds hotspot intensity over time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "High-accuracy GPS gate: ${if (hasHighAccuracyFix) "OPEN" else "WAITING"} (required <= ${String.format(Locale.US, "%.1f", magneticGpsAccuracyRequirementMeters)} m${lowestSeenAccuracyMeters?.let { " • lowest seen ${String.format(Locale.US, "%.1f", it)} m" } ?: " • lowest seen n/a"})",
                            color = if (hasHighAccuracyFix) Color(0xFF2E7D32) else Color(0xFFE65100),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "GPS accuracy now: ${currentLocation?.accuracyMeters?.let { String.format(Locale.US, "%.1f m", it) } ?: "n/a"} • Requirement: <= ${String.format(Locale.US, "%.1f m", magneticGpsAccuracyRequirementMeters)} • Hotspot cells: ${hotspotCells.size} • Samples: ${samples.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!hasHighAccuracyFix) {
                            Text(
                                "Live overlay paused while GPS gate is WAITING; historical hotspots remain visible.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.widthIn(min = 190.dp)) {
                                OutlinedButton(onClick = { pinLimitExpanded = true }) {
                                    Text("Samples shown: $pinLimit")
                                }
                                DropdownMenu(
                                    expanded = pinLimitExpanded,
                                    onDismissRequest = { pinLimitExpanded = false }
                                ) {
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

                            Button(
                                onClick = onOpenStatusTab,
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("Open Status")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.widthIn(min = 240.dp)) {
                                OutlinedButton(onClick = { gpsRequirementExpanded = true }) {
                                    Text("GPS gate <= ${String.format(Locale.US, "%.1f", magneticGpsAccuracyRequirementMeters)} m")
                                }
                                DropdownMenu(
                                    expanded = gpsRequirementExpanded,
                                    onDismissRequest = { gpsRequirementExpanded = false }
                                ) {
                                    gpsRequirementOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(String.format(Locale.US, "<= %.1f m", option)) },
                                            onClick = {
                                                magneticGpsAccuracyRequirementMeters = option
                                                ScanSettings.setMagneticGpsAccuracyRequirementMeters(context, option)
                                                gpsRequirementExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!hasMapsApiKey) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Google Maps key missing", fontWeight = FontWeight.Bold)
                    Text("Set MAPS_API_KEY in local.properties or environment to render Magnetic Map.")
                }
            }
            return@Column
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = true,
                    compassEnabled = true
                )
            ) {
                currentLocation?.let { loc ->
                    val currentLatLng = LatLng(loc.lat, loc.lon)
                    Circle(
                        center = currentLatLng,
                        radius = (loc.accuracyMeters ?: 8f).toDouble().coerceIn(3.0, 60.0),
                        strokeWidth = 2f,
                        strokeColor = Color(0xFF1565C0),
                        fillColor = Color(0x331565C0)
                    )
                }

                hotspotCells.forEach { cell ->
                    val magnitudeSeverity = ((cell.averageMagnitudeMicroTesla - 45.0) / 45.0).coerceIn(0.0, 1.0)
                    val densitySeverity = ((cell.sampleCount - 1).toDouble() / 10.0).coerceIn(0.0, 1.0)
                    val severity = (0.6 * magnitudeSeverity + 0.4 * densitySeverity).coerceIn(0.0, 1.0)
                    val hotspotColor = Color(
                        red = (0x2E + (0xB3 - 0x2E) * severity).toInt(),
                        green = (0x7D + (0x26 - 0x7D) * severity).toInt(),
                        blue = (0x32 + (0x1E - 0x32) * severity).toInt(),
                        alpha = (85 + (140 * severity)).toInt()
                    )
                    // Keep each hotspot anchored to recorded coordinates with minimal spatial spread.
                    Circle(
                        center = LatLng(cell.lat, cell.lon),
                        radius = 1.0,
                        strokeWidth = 0f,
                        strokeColor = Color.Transparent,
                        fillColor = hotspotColor,
                        clickable = true,
                        onClick = {
                            selectedHotspotCellKey = cell.cellKey
                        }
                    )
                }

                if (hasHighAccuracyFix) {
                    recentLiveSamples.forEach { sample ->
                        Circle(
                            center = LatLng(sample.lat, sample.lon),
                            radius = 0.8,
                            strokeWidth = 0f,
                            strokeColor = Color.Transparent,
                            fillColor = Color(0x994FC3F7)
                        )
                    }
                }
            }
        }

        selectedHotspotCell?.let { cell ->
            AlertDialog(
                onDismissRequest = { selectedHotspotCellKey = null },
                title = { Text("Heatmap Hotspot") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Avg magnitude: ${String.format(Locale.US, "%.1f", cell.averageMagnitudeMicroTesla)} uT",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("Samples in cell: ${cell.sampleCount}")
                        Text("Latest sample: ${formatEpoch(cell.latestTimestampEpochMs)}")
                        Text(
                            "Location: ${String.format(Locale.US, "%.5f", cell.lat)}, ${String.format(Locale.US, "%.5f", cell.lon)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (selectedHotspotSamples.size >= 2) {
                            Text(
                                "Trend chart (${selectedHotspotSamples.size} samples)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            MagneticSignalVisualizer(
                                samples = selectedHotspotSamples.map { it.magnitudeMicroTesla },
                                thresholdMicroTesla = cell.averageMagnitudeMicroTesla,
                                currentMagnitudeMicroTesla = selectedHotspotSamples.lastOrNull()?.magnitudeMicroTesla
                            )
                        } else {
                            Text(
                                "Chart unavailable for this hotspot (needs at least 2 samples).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedHotspotCellKey = null }) {
                        Text("Close")
                    }
                }
            )
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
    additionalDiagnostics: List<String> = emptyList(),
    pinLimit: Int,
    onPinLimitChange: (Int) -> Unit,
    mapClusteringEnabled: Boolean = true,
    onMapClusteringEnabledChange: (Boolean) -> Unit = {},
    mapClusterRangeLevel: Int = ScanSettings.DEFAULT_MAP_CLUSTER_RANGE_LEVEL,
    onMapClusterRangeLevelChange: (Int) -> Unit = {},
    mapScannerSweepAnimationEnabled: Boolean = ScanSettings.DEFAULT_MAP_SCANNER_SWEEP_ANIMATION_ENABLED,
    mapScannerSweepAnimationSpeedPreset: String = ScanSettings.DEFAULT_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESET,
    onPinDetailsClick: (MapPin) -> Unit,
    useSourceOnlyPinColors: Boolean = false,
    enableVerticalScroll: Boolean = false,
    showLiveOnlyControl: Boolean = false,
    liveOnlyEnabled: Boolean = false,
    onLiveOnlyEnabledChange: (Boolean) -> Unit = {},
    identityModeEnabled: Boolean = false,
    onIdentityModeEnabledChange: (Boolean) -> Unit = {},
    identityShowFullNamesEnabled: Boolean = ScanSettings.DEFAULT_MAP_IDENTITY_FULL_NAMES_ENABLED,
    onIdentityShowFullNamesEnabledChange: (Boolean) -> Unit = {},
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
    mapOnlyPresentation: Boolean = false,
    externalZoomCommandNonce: Long = 0L,
    externalZoomCommandDelta: Float = 0f,
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
        AppPermissions.hasAnyLocationPermission(context)
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
    var preciseDotsEnabled by rememberSaveable { mutableStateOf(false) }
    var mapLoaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var mapTouchInProgress by remember { mutableStateOf(false) }
    var hiddenLegendSources by remember { mutableStateOf(setOf<String>()) }
    var metadataFilterQuery by rememberSaveable { mutableStateOf("") }
    val useScrollableLayout = enableVerticalScroll && controlsVisible && !mapOnlyPresentation
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
    var signalLinkLinesEnabled by rememberSaveable {
        mutableStateOf(ScanSettings.isMapSignalLinkLinesEnabled(context))
    }
    var cameraSignalLinkInViewOnly by rememberSaveable {
        mutableStateOf(ScanSettings.isMapSignalLinkCameraInViewOnlyEnabled(context))
    }
    var signalLinkAllDevicesEnabled by rememberSaveable {
        mutableStateOf(ScanSettings.isMapSignalLinkAllDevicesEnabled(context))
    }
    var signalLinkAllDevicesMaxRangeMeters by rememberSaveable {
        mutableStateOf(ScanSettings.getMapSignalLinkAllDevicesMaxRangeMeters(context))
    }
    var selectedSignalLinkSources by rememberSaveable {
        mutableStateOf(ScanSettings.getMapSignalLinkSelectedSources(context))
    }
    val availableSignalLinkLegendItems = remember(legendItems) {
        legendItems.filter { item -> item.source in SIGNAL_LINK_LINE_SUPPORTED_SOURCES }
    }
    val signalLinkSelectableSources = remember(availableSignalLinkLegendItems, selectedSignalLinkSources) {
        val preferredOrder = SOURCE_TYPE_UI_META_ORDERED.map { it.source }
        val requestedSources = buildSet {
            addAll(ScanSettings.DEFAULT_MAP_SIGNAL_LINK_SELECTED_SOURCES)
            addAll(selectedSignalLinkSources)
            addAll(availableSignalLinkLegendItems.map { it.source })
        }.filter { source -> source in SIGNAL_LINK_LINE_SUPPORTED_SOURCES }
            .toSet()

        val availableBySource = availableSignalLinkLegendItems.associateBy { item -> item.source }
        val orderedSources = preferredOrder.filter { source -> source in requestedSources } +
            requestedSources.filterNot { source -> source in preferredOrder }.sorted()

        orderedSources.map { source ->
            availableBySource[source] ?: PinLegendItem(
                source = source,
                label = markerLegendLabelForSource(source),
                color = markerLegendColorForSource(source)
            )
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
    }
    val showAllHistoryVisualMode =
        showLiveOnlyControl &&
            !liveOnlyEnabled &&
            (!showMovingOnlyControl || !movingOnlyEnabled) &&
            (!showSinceSnapshotControl || !sinceSnapshotEnabled)

    var displayFilteredPins by remember { mutableStateOf<List<MapPin>>(filteredVisiblePins) }
    LaunchedEffect(filteredVisiblePins, identityModeEnabled, identityShowFullNamesEnabled, showAllHistoryVisualMode) {
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
                            markerGlyphOverride = if (identityShowFullNamesEnabled) {
                                identityName
                            } else {
                                identityGlyph(identityName)
                            },
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
    val selectedSignalLinkSourceSet = remember(selectedSignalLinkSources) {
        selectedSignalLinkSources.toSet()
    }
    var nearestSignalLinkTargets by remember { mutableStateOf<List<SignalLinkLineTarget>>(emptyList()) }
    LaunchedEffect(
        currentLocation,
        displayFilteredPins,
        signalLinkLinesEnabled,
        selectedSignalLinkSourceSet,
        cameraSignalLinkInViewOnly,
        signalLinkAllDevicesEnabled,
        signalLinkAllDevicesMaxRangeMeters
    ) {
        nearestSignalLinkTargets = withContext(Dispatchers.Default) {
            val location = currentLocation
            if (
                !signalLinkLinesEnabled ||
                    location == null ||
                    selectedSignalLinkSourceSet.isEmpty()
            ) {
                emptyList()
            } else {
                resolveSignalLinkTargets(
                    currentLocation = location,
                    pins = displayFilteredPins,
                    selectedSources = selectedSignalLinkSourceSet,
                    cameraRequiresInView = cameraSignalLinkInViewOnly,
                    includeAllDevices = signalLinkAllDevicesEnabled,
                    allDevicesMaxRangeMeters = signalLinkAllDevicesMaxRangeMeters
                )
            }
        }
    }
    val signalLinkTargetBySource = remember(nearestSignalLinkTargets) {
        nearestSignalLinkTargets
            .groupBy { it.source }
            .mapValues { (_, targets) ->
                targets.minByOrNull { target -> target.distanceMeters } ?: targets.first()
            }
    }
    val signalLinkWavePhase by produceState(
        initialValue = 0f,
        key1 = signalLinkLinesEnabled,
        key2 = nearestSignalLinkTargets.size
    ) {
        if (!signalLinkLinesEnabled || nearestSignalLinkTargets.isEmpty()) {
            value = 0f
            return@produceState
        }
        while (true) {
            delay(120.milliseconds)
            val next = value + 0.11f
            value = if (next >= 1f) next - 1f else next
        }
    }
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
                    recentWindowMs = MAP_COVERAGE_RECENT_WINDOW_MS
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
                    recentWindowMs = MAP_AIRCRAFT_COVERAGE_RECENT_WINDOW_MS
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
    var lastHandledExternalZoomCommandNonce by remember { mutableStateOf(0L) }
    val zoomBucket = remember(cameraPositionState.position.zoom) {
        (cameraPositionState.position.zoom * 2f).roundToInt()
    }
    val renderPinLimit = remember(pinLimit) {
        pinLimit.coerceAtLeast(1)
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
    val signalLinkForcedSinglePinKeys = remember(nearestSignalLinkTargets) {
        nearestSignalLinkTargets
            .map { target -> mapPinStableKey(target.pin) }
            .toSet()
    }
    val mapRenderItems by produceState(
        initialValue = emptyList<MapRenderItem>(),
        key1 = renderedPins,
        key2 = shouldClusterPins,
        key3 = "$zoomBucket|$mapClusterRangeLevel|${signalLinkForcedSinglePinKeys.size}|${signalLinkForcedSinglePinKeys.hashCode()}"
    ) {
        value = withContext(Dispatchers.Default) {
            if (shouldClusterPins) {
                clusterPinsForRender(
                    pins = renderedPins,
                    zoom = zoomBucket / 2f,
                    rangeLevel = mapClusterRangeLevel,
                    forceSinglePinKeys = signalLinkForcedSinglePinKeys
                )
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
            !mapTouchInProgress
    }
    val safeSweepPreset = remember(mapScannerSweepAnimationSpeedPreset) {
        mapScannerSweepAnimationSpeedPreset
            .takeIf { it in ScanSettings.ALLOWED_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESETS }
            ?: ScanSettings.DEFAULT_MAP_SCANNER_SWEEP_ANIMATION_SPEED_PRESET
    }
    val baseSweepAnimationFrameMs = remember(safeSweepPreset) {
        when (safeSweepPreset) {
            ScanSettings.MAP_SCANNER_SWEEP_SPEED_PRESET_CONSERVATIVE -> 220L
            ScanSettings.MAP_SCANNER_SWEEP_SPEED_PRESET_SMOOTH -> 90L
            else -> 120L // Balanced
        }
    }
    val baseSweepAnimationStepDegrees = remember(safeSweepPreset) {
        when (safeSweepPreset) {
            ScanSettings.MAP_SCANNER_SWEEP_SPEED_PRESET_CONSERVATIVE -> 9f
            ScanSettings.MAP_SCANNER_SWEEP_SPEED_PRESET_SMOOTH -> 5f
            else -> 7f // Balanced
        }
    }
    val sweepDynamicAdjustmentEnabled = remember(safeSweepPreset) {
        safeSweepPreset != ScanSettings.MAP_SCANNER_SWEEP_SPEED_PRESET_SMOOTH
    }
    val sweepAnimationFrameMs = remember(renderedPins.size, baseSweepAnimationFrameMs, sweepDynamicAdjustmentEnabled, safeSweepPreset) {
        if (!sweepDynamicAdjustmentEnabled) {
            baseSweepAnimationFrameMs
        } else {
            when {
                safeSweepPreset == ScanSettings.MAP_SCANNER_SWEEP_SPEED_PRESET_CONSERVATIVE && renderedPins.size > 2_000 -> 420L
                safeSweepPreset == ScanSettings.MAP_SCANNER_SWEEP_SPEED_PRESET_CONSERVATIVE && renderedPins.size > 1_200 -> 330L
                safeSweepPreset == ScanSettings.MAP_SCANNER_SWEEP_SPEED_PRESET_CONSERVATIVE && renderedPins.size > 700 -> 280L
                safeSweepPreset == ScanSettings.MAP_SCANNER_SWEEP_SPEED_PRESET_CONSERVATIVE && renderedPins.size > MAP_SWEEP_DISABLE_PIN_THRESHOLD -> 240L
                renderedPins.size > 2_000 -> 240L
                renderedPins.size > 1_200 -> 190L
                renderedPins.size > 700 -> 155L
                renderedPins.size > MAP_SWEEP_DISABLE_PIN_THRESHOLD -> 135L
                else -> baseSweepAnimationFrameMs
            }
        }
    }
    val sweepAnimationStepDegrees = remember(renderedPins.size, baseSweepAnimationStepDegrees, sweepDynamicAdjustmentEnabled, safeSweepPreset) {
        if (!sweepDynamicAdjustmentEnabled) {
            baseSweepAnimationStepDegrees
        } else {
            when {
                safeSweepPreset == ScanSettings.MAP_SCANNER_SWEEP_SPEED_PRESET_CONSERVATIVE && renderedPins.size > 2_000 -> 12f
                safeSweepPreset == ScanSettings.MAP_SCANNER_SWEEP_SPEED_PRESET_CONSERVATIVE && renderedPins.size > 1_200 -> 11f
                safeSweepPreset == ScanSettings.MAP_SCANNER_SWEEP_SPEED_PRESET_CONSERVATIVE && renderedPins.size > 700 -> 10f
                safeSweepPreset == ScanSettings.MAP_SCANNER_SWEEP_SPEED_PRESET_CONSERVATIVE && renderedPins.size > MAP_SWEEP_DISABLE_PIN_THRESHOLD -> 9.5f
                renderedPins.size > 2_000 -> 10f
                renderedPins.size > 1_200 -> 9f
                renderedPins.size > 700 -> 8.5f
                renderedPins.size > MAP_SWEEP_DISABLE_PIN_THRESHOLD -> 8f
                else -> baseSweepAnimationStepDegrees
            }
        }
    }
    val effectivePreciseDotsEnabled = remember(preciseDotsEnabled) {
        preciseDotsEnabled
    }
    val radarSweepHeadingDeg by produceState(
        initialValue = 0f,
        key1 = sweepAnimationActive,
        key2 = sweepAnimationFrameMs,
        key3 = sweepAnimationStepDegrees
    ) {
        if (!sweepAnimationActive) {
            value = 0f
            return@produceState
        }
        while (true) {
            delay(sweepAnimationFrameMs.milliseconds)
            val next = value + sweepAnimationStepDegrees
            value = if (next >= 360f) next - 360f else next
        }
    }
    val renderedPinSnippetCache by produceState(
        initialValue = emptyMap<String, String?>(),
        key1 = mapRenderItems,
        key2 = effectivePreciseDotsEnabled,
        key3 = useDenseDotMarkers
    ) {
        value = withContext(Dispatchers.Default) {
            if (effectivePreciseDotsEnabled) {
                emptyMap()
            } else {
                mapRenderItems
                    .asSequence()
                    .mapNotNull { item ->
                        val pin = (item as? MapRenderItem.SinglePin)?.pin ?: return@mapNotNull null
                        val key = mapPinStableKey(pin)
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
        delay(7000.milliseconds)
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

    LaunchedEffect(useScrollableLayout) {
        if (!useScrollableLayout) {
            mapTouchInProgress = false
        }
    }

    LaunchedEffect(legendSources) {
        hiddenLegendSources = hiddenLegendSources.intersect(legendSources)
    }

    LaunchedEffect(selectedSignalLinkSources) {
        val sanitized = selectedSignalLinkSources
            .map { source -> source.trim() }
            .filter { source -> source in SIGNAL_LINK_LINE_SUPPORTED_SOURCES }
            .distinct()
        if (sanitized != selectedSignalLinkSources) {
            selectedSignalLinkSources = sanitized
        }
    }
    LaunchedEffect(signalLinkLinesEnabled) {
        ScanSettings.setMapSignalLinkLinesEnabled(context, signalLinkLinesEnabled)
    }
    LaunchedEffect(cameraSignalLinkInViewOnly) {
        ScanSettings.setMapSignalLinkCameraInViewOnlyEnabled(context, cameraSignalLinkInViewOnly)
    }
    LaunchedEffect(signalLinkAllDevicesEnabled) {
        ScanSettings.setMapSignalLinkAllDevicesEnabled(context, signalLinkAllDevicesEnabled)
    }
    LaunchedEffect(signalLinkAllDevicesMaxRangeMeters) {
        ScanSettings.setMapSignalLinkAllDevicesMaxRangeMeters(context, signalLinkAllDevicesMaxRangeMeters)
    }
    LaunchedEffect(selectedSignalLinkSources) {
        ScanSettings.setMapSignalLinkSelectedSources(context, selectedSignalLinkSources)
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
        delay(1200.milliseconds)
        mapTouchInProgress = false
    }

    LaunchedEffect(externalZoomCommandNonce, externalZoomCommandDelta, mapOnlyPresentation) {
        if (!mapOnlyPresentation) return@LaunchedEffect
        if (externalZoomCommandNonce == 0L || externalZoomCommandNonce == lastHandledExternalZoomCommandNonce) {
            return@LaunchedEffect
        }
        lastHandledExternalZoomCommandNonce = externalZoomCommandNonce
        val zoomDelta = externalZoomCommandDelta.coerceIn(-2f, 2f)
        if (zoomDelta == 0f) return@LaunchedEffect
        runCatching {
            cameraPositionState.animate(CameraUpdateFactory.zoomBy(zoomDelta), 250)
        }.onFailure {
            mapError = "Failed to adjust zoom: ${it.message ?: "unknown error"}"
        }
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
        if (!mapOnlyPresentation) {
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
                        onClick = {
                            preciseDotsEnabled = !preciseDotsEnabled
                        },
                        label = {
                            Text(
                                text = if (effectivePreciseDotsEnabled) "Dots: On" else "Dots: Off",
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
        }
        if (!mapOnlyPresentation && !legendPanelVisible && legendItems.isNotEmpty()) {
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
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            if (showLiveOnlyControl) {
                                Text(
                                    text = if (liveOnlyEnabled) "● LIVE" else "● ALL",
                                    color = if (liveOnlyEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            TextButton(onClick = { deviceTypeFiltersCollapsed = !deviceTypeFiltersCollapsed }) {
                                Text(if (deviceTypeFiltersCollapsed) "[+]" else "[-]")
                            }
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
            visible = !mapOnlyPresentation && legendPanelVisible,
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
            if (!mapOnlyPresentation && controlsVisible && !compactMapLayout) {
                val splitControlPanels = LocalConfiguration.current.screenWidthDp.dp >= 700.dp
                Box(modifier = Modifier.fillMaxWidth()) {
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
                                        Text("Show full names")
                                        Switch(
                                            checked = identityShowFullNamesEnabled,
                                            onCheckedChange = onIdentityShowFullNamesEnabledChange,
                                            enabled = identityModeEnabled
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
                                    SignalLinkLinesControlPanel(
                                        enabled = signalLinkLinesEnabled,
                                        onEnabledChange = { signalLinkLinesEnabled = it },
                                        cameraInViewOnlyForLines = cameraSignalLinkInViewOnly,
                                        onCameraInViewOnlyForLinesChange = { cameraSignalLinkInViewOnly = it },
                                        allDevicesEnabled = signalLinkAllDevicesEnabled,
                                        onAllDevicesEnabledChange = { signalLinkAllDevicesEnabled = it },
                                        allDevicesMaxRangeMeters = signalLinkAllDevicesMaxRangeMeters,
                                        onAllDevicesMaxRangeMetersChange = { meters ->
                                            signalLinkAllDevicesMaxRangeMeters = meters
                                        },
                                        allDevicesRangeOptionsMeters = SIGNAL_LINK_ALL_RANGE_OPTIONS_METERS,
                                        availableSources = signalLinkSelectableSources,
                                        selectedSources = selectedSignalLinkSourceSet,
                                        onSourceCheckedChange = { source, checked ->
                                            selectedSignalLinkSources = if (checked) {
                                                (selectedSignalLinkSources + source).distinct()
                                            } else {
                                                selectedSignalLinkSources.filterNot { it == source }
                                            }
                                        },
                                        activeTargetsBySource = signalLinkTargetBySource
                                    )
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
                                        Text("Show full names")
                                        Switch(
                                            checked = identityShowFullNamesEnabled,
                                            onCheckedChange = onIdentityShowFullNamesEnabledChange,
                                            enabled = identityModeEnabled
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
                                    SignalLinkLinesControlPanel(
                                        enabled = signalLinkLinesEnabled,
                                        onEnabledChange = { signalLinkLinesEnabled = it },
                                        cameraInViewOnlyForLines = cameraSignalLinkInViewOnly,
                                        onCameraInViewOnlyForLinesChange = { cameraSignalLinkInViewOnly = it },
                                        allDevicesEnabled = signalLinkAllDevicesEnabled,
                                        onAllDevicesEnabledChange = { signalLinkAllDevicesEnabled = it },
                                        allDevicesMaxRangeMeters = signalLinkAllDevicesMaxRangeMeters,
                                        onAllDevicesMaxRangeMetersChange = { meters ->
                                            signalLinkAllDevicesMaxRangeMeters = meters
                                        },
                                        allDevicesRangeOptionsMeters = SIGNAL_LINK_ALL_RANGE_OPTIONS_METERS,
                                        availableSources = signalLinkSelectableSources,
                                        selectedSources = selectedSignalLinkSourceSet,
                                        onSourceCheckedChange = { source, checked ->
                                            selectedSignalLinkSources = if (checked) {
                                                (selectedSignalLinkSources + source).distinct()
                                            } else {
                                                selectedSignalLinkSources.filterNot { it == source }
                                            }
                                        },
                                        activeTargetsBySource = signalLinkTargetBySource
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
                            val currentLocationSnapshot = currentLocation
                            Text("Loaded: ${if (mapLoaded) "yes" else "no"}")
                            Text("API key: $mapsApiKeyDiagnostic")
                            Text("Play Services: $playServicesDiagnostic")
                            Text("Network: ${if (hasNetwork) "available" else "unavailable"}")
                            Text("Render stages: input=${pins.size}, visible=${visiblePins.size}, filtered=${displayFilteredPins.size}, sampled=${renderedPins.size}, mapItems=${mapRenderItems.size}")
                            Text("Pins rendered: ${renderedPins.size}/${pins.size}")
                            Text("Dense safety: dots=${if (effectivePreciseDotsEnabled) "on" else "off"}, clustering=${if (mapClusteringEnabled) "on" else "off"}, near-limit=$MAP_RENDER_PIN_LIMIT_NEAR_SAFE")
                            additionalDiagnostics.forEach { line ->
                                Text(line)
                            }
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
                        zoomControlsEnabled = !mapOnlyPresentation,
                        zoomGesturesEnabled = true,
                        scrollGesturesEnabled = true,
                        tiltGesturesEnabled = false,
                        rotationGesturesEnabled = true,
                        compassEnabled = true,
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
                    val lineOrigin = currentLocation?.let { location -> LatLng(location.lat, location.lon) }
                    if (signalLinkLinesEnabled && lineOrigin != null) {
                        nearestSignalLinkTargets.forEach { target ->
                            val lineColor = markerLegendColorForSource(target.source)
                            val endpoint = target.pin.position
                            Polyline(
                                points = listOf(lineOrigin, endpoint),
                                color = lineColor.copy(alpha = 0.35f),
                                width = 2.5f
                            )
                            Polyline(
                                points = buildSignalWavePolylinePoints(
                                    start = lineOrigin,
                                    end = endpoint,
                                    phase = signalLinkWavePhase
                                ),
                                color = lineColor.copy(alpha = 0.92f),
                                width = 5.5f
                            )
                        }
                    }
                    MapRenderLayer(
                        mapRenderItems = mapRenderItems,
                        preciseDotsEnabled = effectivePreciseDotsEnabled,
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
                                Text("Map Controls", fontWeight = FontWeight.Bold)
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("Show full names")
                                Switch(
                                    checked = identityShowFullNamesEnabled,
                                    onCheckedChange = onIdentityShowFullNamesEnabledChange,
                                    enabled = identityModeEnabled
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
                            SignalLinkLinesControlPanel(
                                enabled = signalLinkLinesEnabled,
                                onEnabledChange = { signalLinkLinesEnabled = it },
                                cameraInViewOnlyForLines = cameraSignalLinkInViewOnly,
                                onCameraInViewOnlyForLinesChange = { cameraSignalLinkInViewOnly = it },
                                allDevicesEnabled = signalLinkAllDevicesEnabled,
                                onAllDevicesEnabledChange = { signalLinkAllDevicesEnabled = it },
                                allDevicesMaxRangeMeters = signalLinkAllDevicesMaxRangeMeters,
                                onAllDevicesMaxRangeMetersChange = { meters ->
                                    signalLinkAllDevicesMaxRangeMeters = meters
                                },
                                allDevicesRangeOptionsMeters = SIGNAL_LINK_ALL_RANGE_OPTIONS_METERS,
                                availableSources = signalLinkSelectableSources,
                                selectedSources = selectedSignalLinkSourceSet,
                                onSourceCheckedChange = { source, checked ->
                                    selectedSignalLinkSources = if (checked) {
                                        (selectedSignalLinkSources + source).distinct()
                                    } else {
                                        selectedSignalLinkSources.filterNot { it == source }
                                    }
                                },
                                activeTargetsBySource = signalLinkTargetBySource
                            )
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
private fun SignalLinkLinesControlPanel(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    cameraInViewOnlyForLines: Boolean,
    onCameraInViewOnlyForLinesChange: (Boolean) -> Unit,
    allDevicesEnabled: Boolean,
    onAllDevicesEnabledChange: (Boolean) -> Unit,
    allDevicesMaxRangeMeters: Double,
    onAllDevicesMaxRangeMetersChange: (Double) -> Unit,
    allDevicesRangeOptionsMeters: List<Double>,
    availableSources: List<PinLegendItem>,
    selectedSources: Set<String>,
    onSourceCheckedChange: (String, Boolean) -> Unit,
    activeTargetsBySource: Map<String, SignalLinkLineTarget>
) {
    var allDevicesRangeExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Signal Link Lines", fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Draw nearest target link per selected type")
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
        if (!enabled) {
            Text(
                "Enable to render animated source-matched signal lines from your position.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        if (availableSources.isEmpty()) {
            Text(
                "No line-eligible source types are visible on this map.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        Text(
            "One line per selected type (to the nearest visible device).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Camera line requires in-view")
            Switch(
                checked = cameraInViewOnlyForLines,
                onCheckedChange = onCameraInViewOnlyForLinesChange
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Draw lines to all devices")
            Switch(
                checked = allDevicesEnabled,
                onCheckedChange = onAllDevicesEnabledChange
            )
        }
        if (allDevicesEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Max line range")
                Button(onClick = { allDevicesRangeExpanded = true }) {
                    Text(signalLinkRangeLabel(allDevicesMaxRangeMeters))
                }
                DropdownMenu(
                    expanded = allDevicesRangeExpanded,
                    onDismissRequest = { allDevicesRangeExpanded = false }
                ) {
                    allDevicesRangeOptionsMeters.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(signalLinkRangeLabel(option)) },
                            onClick = {
                                onAllDevicesMaxRangeMetersChange(option)
                                allDevicesRangeExpanded = false
                            }
                        )
                    }
                }
            }
            Text(
                "Only devices within this range qualify for line drawing for checked source types.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "Uses camera in-view gate (<= ${String.format(Locale.US, "%.0f", CAMERA_IN_VIEW_DISTANCE_THRESHOLD_METERS)} m).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (allDevicesEnabled) {
            Text(
                "All-devices mode active: draws to all visible devices for checked source types.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        availableSources.forEach { item ->
            val checked = item.source in selectedSources
            val activeTarget = activeTargetsBySource[item.source]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { value -> onSourceCheckedChange(item.source, value) },
                        enabled = true
                    )
                    Text("●", color = item.color, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.widthIn(min = 8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(item.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            activeTarget?.let { target ->
                                "Nearest ${formatDistanceFeetMiles(target.distanceMeters)}"
                            } ?: "No target in view",
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
                true
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
                    halfWidthDegrees = 22f
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
                    halfWidthDegrees = 18f
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
                val markerKey = mapPinStableKey(pin)
                Marker(
                    state = markerState,
                    title = if (showMarkerDetails) pin.title else null,
                    snippet = if (showMarkerDetails) renderedPinSnippetCache[markerKey] else null,
                    icon = if (preciseDotsEnabled) {
                        markerDotIconForPin(pin, useSourceOnlyPinColors)
                    } else {
                        markerIconForPin(pin, useSourceOnlyPinColors, showTrackerFamilyBadge)
                    },
                    anchor = if (pin.source == SourceCatalog.SOURCE_AIRCRAFT) {
                        Offset(0.5f, aircraftMarkerAnchorY(pin, compact = preciseDotsEnabled))
                    } else {
                        Offset(0.5f, 1f)
                    },
                    rotation = aircraftHeading,
                    flat = pin.source == SourceCatalog.SOURCE_AIRCRAFT,
                    onClick = {
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
            aircraftLabel = hints.displayLabel,
            aircraftAffiliation = hints.affiliation.name.lowercase(Locale.US),
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
                        snippet = formatEpoch(deviceEncounters.firstOrNull()?.timestampEpochMs ?: 0L),
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
                        anchor = Offset(
                            0.5f,
                            latestAircraftMarkerPin?.let { aircraftMarkerAnchorY(it, compact = false) } ?: 0.5f
                        ),
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
                        aircraftLabel = "INCIDENT",
                        aircraftAffiliation = AircraftAffiliation.UNKNOWN.name.lowercase(Locale.US),
                        headingDegrees = null,
                        isLive = true
                    )
                    Marker(
                        state = MarkerState(position = LatLng(fallbackIncidentLocation.lat, fallbackIncidentLocation.lon)),
                        title = "Incident location",
                        snippet = incidentEventEpochMs?.let(::formatEpoch) ?: "No timestamp",
                        icon = markerAircraftIconForPin(incidentMarkerPin),
                        anchor = Offset(0.5f, aircraftMarkerAnchorY(incidentMarkerPin, compact = false)),
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
    val aircraftLabel: String? = null,
    val aircraftAffiliation: String? = null,
    val headingDegrees: Double? = null,
    val motionBadge: String? = null,
    val motionSpeedMps: Double? = null,
    val isLive: Boolean = true
)

private data class RuntimeMemorySnapshot(
    val usedHeapBytes: Long,
    val freeHeapBytes: Long,
    val totalHeapBytes: Long,
    val maxHeapBytes: Long,
    val nativeHeapAllocatedBytes: Long,
    val nativeHeapFreeBytes: Long,
    val nativeHeapSizeBytes: Long,
    val processTotalPssBytes: Long,
    val processPrivateDirtyBytes: Long,
    val processSharedDirtyBytes: Long,
    val runtimePinCount: Int,
    val runtimePinAgeMs: Long?,
    val runtimePinEstimatedBytes: Long,
    val diskCachePayloadChars: Int,
    val diskCachePayloadBytes: Long,
    val diskCacheAgeMs: Long?,
    val iconCacheEntryCount: Int,
    val iconCacheEstimatedBytes: Long,
    val iconCacheDeviceEntries: Int,
    val iconCacheDeviceEstimatedBytes: Long,
    val iconCacheDotEntries: Int,
    val iconCacheDotEstimatedBytes: Long,
    val iconCacheAircraftEntries: Int,
    val iconCacheAircraftEstimatedBytes: Long,
    val iconCacheClusterEntries: Int,
    val iconCacheClusterEstimatedBytes: Long,
    val iconCacheNoFlyEntries: Int,
    val iconCacheNoFlyEstimatedBytes: Long,
    val visibleDeviceRows: Int,
    val visibleDeviceRowsEstimatedBytes: Long,
    val visibleDeviceRowsSampleAgeMs: Long?,
    val visibleEncounterRows: Int,
    val visibleEncounterRowsEstimatedBytes: Long,
    val visibleEncounterRowsSampleAgeMs: Long?,
    val activeDeviceMapPins: Int,
    val activeDeviceMapPinsEstimatedBytes: Long,
    val activeDeviceMapPinsSampleAgeMs: Long?,
    val pipelineEncounterCount: Int,
    val pipelineEncounterEstimatedBytes: Long,
    val pipelineEncounterSampleAgeMs: Long?,
    val deviceAnalysisWindowCount: Int,
    val deviceAnalysisWindowEstimatedBytes: Long,
    val deviceAnalysisWindowSampleAgeMs: Long?,
    val deviceAnalysisListCount: Int,
    val deviceAnalysisListEstimatedBytes: Long,
    val deviceAnalysisListSampleAgeMs: Long?,
    val deviceMapCandidateCount: Int,
    val deviceMapCandidateEstimatedBytes: Long,
    val deviceMapCandidateSampleAgeMs: Long?,
    val deviceMapPinPoolCount: Int,
    val deviceMapPinPoolEstimatedBytes: Long,
    val deviceMapPinPoolSampleAgeMs: Long?
)

private data class DeviceMapPinDiskCacheSnapshot(
    val payloadChars: Int,
    val payloadBytes: Long,
    val ageMs: Long?
)

private data class RuntimeUiListMemorySnapshot(
    val visibleDeviceRows: Int,
    val visibleDeviceRowsEstimatedBytes: Long,
    val visibleDeviceRowsSampleAgeMs: Long?,
    val visibleEncounterRows: Int,
    val visibleEncounterRowsEstimatedBytes: Long,
    val visibleEncounterRowsSampleAgeMs: Long?,
    val activeDeviceMapPins: Int,
    val activeDeviceMapPinsEstimatedBytes: Long,
    val activeDeviceMapPinsSampleAgeMs: Long?,
    val pipelineEncounterCount: Int,
    val pipelineEncounterEstimatedBytes: Long,
    val pipelineEncounterSampleAgeMs: Long?,
    val deviceAnalysisWindowCount: Int,
    val deviceAnalysisWindowEstimatedBytes: Long,
    val deviceAnalysisWindowSampleAgeMs: Long?,
    val deviceAnalysisListCount: Int,
    val deviceAnalysisListEstimatedBytes: Long,
    val deviceAnalysisListSampleAgeMs: Long?,
    val deviceMapCandidateCount: Int,
    val deviceMapCandidateEstimatedBytes: Long,
    val deviceMapCandidateSampleAgeMs: Long?,
    val deviceMapPinPoolCount: Int,
    val deviceMapPinPoolEstimatedBytes: Long,
    val deviceMapPinPoolSampleAgeMs: Long?
)

private fun captureRuntimeMemorySnapshot(
    context: android.content.Context,
    nowEpochMs: Long = System.currentTimeMillis()
): RuntimeMemorySnapshot {
    val runtime = Runtime.getRuntime()
    val totalHeap = runtime.totalMemory().coerceAtLeast(0L)
    val freeHeap = runtime.freeMemory().coerceIn(0L, totalHeap)
    val usedHeap = (totalHeap - freeHeap).coerceAtLeast(0L)
    val maxHeap = runtime.maxMemory().coerceAtLeast(totalHeap)
    val nativeHeapAllocated = Debug.getNativeHeapAllocatedSize().coerceAtLeast(0L)
    val nativeHeapFree = Debug.getNativeHeapFreeSize().coerceAtLeast(0L)
    val nativeHeapSize = Debug.getNativeHeapSize().coerceAtLeast(nativeHeapAllocated)
    val processMemoryInfo = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
    val processTotalPssBytes = processMemoryInfo.totalPss.toLong().coerceAtLeast(0L) * 1024L
    val processPrivateDirtyBytes = processMemoryInfo.totalPrivateDirty.toLong().coerceAtLeast(0L) * 1024L
    val processSharedDirtyBytes = processMemoryInfo.totalSharedDirty.toLong().coerceAtLeast(0L) * 1024L

    val runtimePins = DeviceMapPinRuntimeCache.cachedCount()
    val runtimePinAge = DeviceMapPinRuntimeCache.cachedAgeMs(nowEpochMs)
    val runtimePinEstimatedBytes = DeviceMapPinRuntimeCache.cachedEstimatedBytes()
    val diskSnapshot = DeviceMapPinDiskCache.snapshot(context, nowEpochMs)
    val uiListSnapshot = RuntimeUiListMemoryGauge.snapshot(nowEpochMs)

    val deviceEntries = deviceMarkerIconCache.size
    val dotEntries = deviceDotMarkerIconCache.size
    val aircraftEntries = aircraftMarkerIconCache.size
    val clusterEntries = clusterMarkerIconCache.size
    val noFlyEntries = noFlyZoneMarkerIconCache.size
    val deviceEstimatedBytes = estimateDescriptorCacheBytes(deviceMarkerIconCache)
    val dotEstimatedBytes = estimateDescriptorCacheBytes(deviceDotMarkerIconCache)
    val aircraftEstimatedBytes = estimateDescriptorCacheBytes(aircraftMarkerIconCache)
    val clusterEstimatedBytes = estimateDescriptorCacheBytes(clusterMarkerIconCache)
    val noFlyEstimatedBytes = estimateDescriptorCacheBytes(noFlyZoneMarkerIconCache)

    return RuntimeMemorySnapshot(
        usedHeapBytes = usedHeap,
        freeHeapBytes = freeHeap,
        totalHeapBytes = totalHeap,
        maxHeapBytes = maxHeap,
        nativeHeapAllocatedBytes = nativeHeapAllocated,
        nativeHeapFreeBytes = nativeHeapFree,
        nativeHeapSizeBytes = nativeHeapSize,
        processTotalPssBytes = processTotalPssBytes,
        processPrivateDirtyBytes = processPrivateDirtyBytes,
        processSharedDirtyBytes = processSharedDirtyBytes,
        runtimePinCount = runtimePins,
        runtimePinAgeMs = runtimePinAge,
        runtimePinEstimatedBytes = runtimePinEstimatedBytes,
        diskCachePayloadChars = diskSnapshot.payloadChars,
        diskCachePayloadBytes = diskSnapshot.payloadBytes,
        diskCacheAgeMs = diskSnapshot.ageMs,
        iconCacheEntryCount = deviceEntries + dotEntries + aircraftEntries + clusterEntries + noFlyEntries,
        iconCacheEstimatedBytes = deviceEstimatedBytes + dotEstimatedBytes + aircraftEstimatedBytes + clusterEstimatedBytes + noFlyEstimatedBytes,
        iconCacheDeviceEntries = deviceEntries,
        iconCacheDeviceEstimatedBytes = deviceEstimatedBytes,
        iconCacheDotEntries = dotEntries,
        iconCacheDotEstimatedBytes = dotEstimatedBytes,
        iconCacheAircraftEntries = aircraftEntries,
        iconCacheAircraftEstimatedBytes = aircraftEstimatedBytes,
        iconCacheClusterEntries = clusterEntries,
        iconCacheClusterEstimatedBytes = clusterEstimatedBytes,
        iconCacheNoFlyEntries = noFlyEntries,
        iconCacheNoFlyEstimatedBytes = noFlyEstimatedBytes,
        visibleDeviceRows = uiListSnapshot.visibleDeviceRows,
        visibleDeviceRowsEstimatedBytes = uiListSnapshot.visibleDeviceRowsEstimatedBytes,
        visibleDeviceRowsSampleAgeMs = uiListSnapshot.visibleDeviceRowsSampleAgeMs,
        visibleEncounterRows = uiListSnapshot.visibleEncounterRows,
        visibleEncounterRowsEstimatedBytes = uiListSnapshot.visibleEncounterRowsEstimatedBytes,
        visibleEncounterRowsSampleAgeMs = uiListSnapshot.visibleEncounterRowsSampleAgeMs,
        activeDeviceMapPins = uiListSnapshot.activeDeviceMapPins,
        activeDeviceMapPinsEstimatedBytes = uiListSnapshot.activeDeviceMapPinsEstimatedBytes,
        activeDeviceMapPinsSampleAgeMs = uiListSnapshot.activeDeviceMapPinsSampleAgeMs,
        pipelineEncounterCount = uiListSnapshot.pipelineEncounterCount,
        pipelineEncounterEstimatedBytes = uiListSnapshot.pipelineEncounterEstimatedBytes,
        pipelineEncounterSampleAgeMs = uiListSnapshot.pipelineEncounterSampleAgeMs,
        deviceAnalysisWindowCount = uiListSnapshot.deviceAnalysisWindowCount,
        deviceAnalysisWindowEstimatedBytes = uiListSnapshot.deviceAnalysisWindowEstimatedBytes,
        deviceAnalysisWindowSampleAgeMs = uiListSnapshot.deviceAnalysisWindowSampleAgeMs,
        deviceAnalysisListCount = uiListSnapshot.deviceAnalysisListCount,
        deviceAnalysisListEstimatedBytes = uiListSnapshot.deviceAnalysisListEstimatedBytes,
        deviceAnalysisListSampleAgeMs = uiListSnapshot.deviceAnalysisListSampleAgeMs,
        deviceMapCandidateCount = uiListSnapshot.deviceMapCandidateCount,
        deviceMapCandidateEstimatedBytes = uiListSnapshot.deviceMapCandidateEstimatedBytes,
        deviceMapCandidateSampleAgeMs = uiListSnapshot.deviceMapCandidateSampleAgeMs,
        deviceMapPinPoolCount = uiListSnapshot.deviceMapPinPoolCount,
        deviceMapPinPoolEstimatedBytes = uiListSnapshot.deviceMapPinPoolEstimatedBytes,
        deviceMapPinPoolSampleAgeMs = uiListSnapshot.deviceMapPinPoolSampleAgeMs
    )
}

private fun estimateStringHeapBytes(value: String?): Long {
    if (value.isNullOrEmpty()) return 0L
    return 40L + (value.length.toLong() * 2L)
}

private fun estimateOwnedDeviceKeySetBytes(keys: Set<String>): Long {
    if (keys.isEmpty()) return 0L
    var bytes = 56L
    keys.forEach { key ->
        bytes += 72L
        bytes += estimateStringHeapBytes(key)
    }
    return bytes
}

private fun estimateAlertLogHeapBytes(entry: AlertLogEntry): Long {
    var bytes = 152L
    bytes += estimateStringHeapBytes(entry.type.name)
    bytes += estimateStringHeapBytes(entry.source)
    bytes += estimateStringHeapBytes(entry.primaryId)
    bytes += estimateStringHeapBytes(entry.message)
    bytes += 24L
    return bytes
}

private fun estimateAlertLogListBytes(entries: List<AlertLogEntry>): Long {
    if (entries.isEmpty()) return 0L
    var bytes = 56L
    entries.forEach { entry ->
        bytes += estimateAlertLogHeapBytes(entry)
    }
    return bytes
}

private fun estimateOperationalErrorLogHeapBytes(entry: OperationalErrorLogEntry): Long {
    var bytes = 152L
    bytes += estimateStringHeapBytes(entry.category)
    bytes += estimateStringHeapBytes(entry.source)
    bytes += estimateStringHeapBytes(entry.message)
    bytes += estimateStringHeapBytes(entry.severity)
    bytes += 24L
    return bytes
}

private fun estimateOperationalErrorLogListBytes(entries: List<OperationalErrorLogEntry>): Long {
    if (entries.isEmpty()) return 0L
    var bytes = 56L
    entries.forEach { entry ->
        bytes += estimateOperationalErrorLogHeapBytes(entry)
    }
    return bytes
}

private fun estimateMapPinHeapBytes(pin: MapPin): Long {
    var bytes = 160L
    bytes += 16L
    bytes += estimateStringHeapBytes(pin.title)
    bytes += estimateStringHeapBytes(pin.searchableMetadata)
    bytes += estimateStringHeapBytes(pin.source)
    bytes += estimateStringHeapBytes(pin.primaryId)
    bytes += estimateStringHeapBytes(pin.secondaryId)
    bytes += estimateStringHeapBytes(pin.markerGlyphOverride)
    bytes += estimateStringHeapBytes(pin.trackerFamilyBadge)
    bytes += estimateStringHeapBytes(pin.aircraftIconType)
    bytes += estimateStringHeapBytes(pin.aircraftLabel)
    bytes += estimateStringHeapBytes(pin.aircraftAffiliation)
    bytes += estimateStringHeapBytes(pin.motionBadge)
    bytes += 24L
    return bytes
}

private fun estimateDescriptorCacheBytes(cache: Map<String, BitmapDescriptor>): Long {
    if (cache.isEmpty()) return 0L
    var bytes = 56L
    cache.keys.forEach { key ->
        bytes += 96L
        bytes += estimateStringHeapBytes(key)
        bytes += 16L
    }
    return bytes
}

private fun estimateDeviceItemHeapBytes(item: DeviceItem): Long {
    var bytes = 208L
    bytes += estimateStringHeapBytes(item.source)
    bytes += estimateStringHeapBytes(item.primaryId)
    bytes += estimateStringHeapBytes(item.secondaryId)
    bytes += estimateStringHeapBytes(item.lastRawPayloadJson)
    bytes += estimateStringHeapBytes(item.lastProvenanceNodeId)
    bytes += estimateStringHeapBytes(item.lastProvenanceOriginNodeId)
    bytes += estimateStringHeapBytes(item.lastProvenancePathNodeIds)
    bytes += 40L
    return bytes
}

private fun estimateEncounterHeapBytes(encounter: Encounter): Long {
    var bytes = 176L
    bytes += estimateStringHeapBytes(encounter.primaryId)
    bytes += estimateStringHeapBytes(encounter.secondaryId)
    bytes += estimateStringHeapBytes(encounter.rawPayloadJson)
    bytes += estimateStringHeapBytes(encounter.encounterFingerprint)
    bytes += estimateStringHeapBytes(encounter.provenanceNodeId)
    bytes += estimateStringHeapBytes(encounter.provenanceOriginNodeId)
    bytes += estimateStringHeapBytes(encounter.provenancePathNodeIds)
    bytes += 32L
    return bytes
}

private fun estimateDeviceLocationCandidateHeapBytes(candidate: DeviceLocationCandidate): Long {
    var bytes = 224L
    bytes += estimateStringHeapBytes(candidate.source)
    bytes += estimateStringHeapBytes(candidate.primaryId)
    bytes += estimateStringHeapBytes(candidate.secondaryId)
    bytes += 64L
    bytes += 56L + (candidate.encounters.size.toLong() * 8L)
    return bytes
}

private object RuntimeUiListMemoryGauge {
    private val lock = Any()
    private var visibleDeviceRows: Int = 0
    private var visibleDeviceRowsEstimatedBytes: Long = 0L
    private var visibleDeviceRowsUpdatedEpochMs: Long = 0L
    private var visibleEncounterRows: Int = 0
    private var visibleEncounterRowsEstimatedBytes: Long = 0L
    private var visibleEncounterRowsUpdatedEpochMs: Long = 0L
    private var activeDeviceMapPins: Int = 0
    private var activeDeviceMapPinsEstimatedBytes: Long = 0L
    private var activeDeviceMapPinsUpdatedEpochMs: Long = 0L
    private var pipelineEncounterCount: Int = 0
    private var pipelineEncounterEstimatedBytes: Long = 0L
    private var pipelineEncounterUpdatedEpochMs: Long = 0L
    private var deviceAnalysisWindowCount: Int = 0
    private var deviceAnalysisWindowEstimatedBytes: Long = 0L
    private var deviceAnalysisWindowUpdatedEpochMs: Long = 0L
    private var deviceAnalysisListCount: Int = 0
    private var deviceAnalysisListEstimatedBytes: Long = 0L
    private var deviceAnalysisListUpdatedEpochMs: Long = 0L
    private var deviceMapCandidateCount: Int = 0
    private var deviceMapCandidateEstimatedBytes: Long = 0L
    private var deviceMapCandidateUpdatedEpochMs: Long = 0L
    private var deviceMapPinPoolCount: Int = 0
    private var deviceMapPinPoolEstimatedBytes: Long = 0L
    private var deviceMapPinPoolUpdatedEpochMs: Long = 0L

    fun updateVisibleDeviceRows(rows: List<DeviceItem>) {
        val estimated = rows.sumOf { estimateDeviceItemHeapBytes(it) }
        synchronized(lock) {
            visibleDeviceRows = rows.size
            visibleDeviceRowsEstimatedBytes = estimated
            visibleDeviceRowsUpdatedEpochMs = System.currentTimeMillis()
        }
    }

    fun updateVisibleEncounterRows(rows: List<Encounter>) {
        val estimated = rows.sumOf { estimateEncounterHeapBytes(it) }
        synchronized(lock) {
            visibleEncounterRows = rows.size
            visibleEncounterRowsEstimatedBytes = estimated
            visibleEncounterRowsUpdatedEpochMs = System.currentTimeMillis()
        }
    }

    fun updateActiveDeviceMapPins(pins: List<MapPin>) {
        val estimated = pins.sumOf { estimateMapPinHeapBytes(it) }
        synchronized(lock) {
            activeDeviceMapPins = pins.size
            activeDeviceMapPinsEstimatedBytes = estimated
            activeDeviceMapPinsUpdatedEpochMs = System.currentTimeMillis()
        }
    }

    fun updatePipelineEncounters(encounters: List<Encounter>) {
        val estimated = encounters.sumOf { estimateEncounterHeapBytes(it) }
        synchronized(lock) {
            pipelineEncounterCount = encounters.size
            pipelineEncounterEstimatedBytes = estimated
            pipelineEncounterUpdatedEpochMs = System.currentTimeMillis()
        }
    }

    fun updateDeviceAnalysisWindow(encounters: List<Encounter>) {
        val estimated = encounters.sumOf { estimateEncounterHeapBytes(it) }
        synchronized(lock) {
            deviceAnalysisWindowCount = encounters.size
            deviceAnalysisWindowEstimatedBytes = estimated
            deviceAnalysisWindowUpdatedEpochMs = System.currentTimeMillis()
        }
    }

    fun updateDeviceAnalysisList(rows: List<DeviceItem>) {
        val estimated = rows.sumOf { estimateDeviceItemHeapBytes(it) }
        synchronized(lock) {
            deviceAnalysisListCount = rows.size
            deviceAnalysisListEstimatedBytes = estimated
            deviceAnalysisListUpdatedEpochMs = System.currentTimeMillis()
        }
    }

    fun updateDeviceMapCandidates(candidates: List<DeviceLocationCandidate>) {
        val estimated = candidates.sumOf { estimateDeviceLocationCandidateHeapBytes(it) }
        synchronized(lock) {
            deviceMapCandidateCount = candidates.size
            deviceMapCandidateEstimatedBytes = estimated
            deviceMapCandidateUpdatedEpochMs = System.currentTimeMillis()
        }
    }

    fun updateDeviceMapPinPool(pins: List<MapPin>) {
        val estimated = pins.sumOf { estimateMapPinHeapBytes(it) }
        synchronized(lock) {
            deviceMapPinPoolCount = pins.size
            deviceMapPinPoolEstimatedBytes = estimated
            deviceMapPinPoolUpdatedEpochMs = System.currentTimeMillis()
        }
    }

    fun reset() {
        synchronized(lock) {
            visibleDeviceRows = 0
            visibleDeviceRowsEstimatedBytes = 0L
            visibleDeviceRowsUpdatedEpochMs = 0L
            visibleEncounterRows = 0
            visibleEncounterRowsEstimatedBytes = 0L
            visibleEncounterRowsUpdatedEpochMs = 0L
            activeDeviceMapPins = 0
            activeDeviceMapPinsEstimatedBytes = 0L
            activeDeviceMapPinsUpdatedEpochMs = 0L
            pipelineEncounterCount = 0
            pipelineEncounterEstimatedBytes = 0L
            pipelineEncounterUpdatedEpochMs = 0L
            deviceAnalysisWindowCount = 0
            deviceAnalysisWindowEstimatedBytes = 0L
            deviceAnalysisWindowUpdatedEpochMs = 0L
            deviceAnalysisListCount = 0
            deviceAnalysisListEstimatedBytes = 0L
            deviceAnalysisListUpdatedEpochMs = 0L
            deviceMapCandidateCount = 0
            deviceMapCandidateEstimatedBytes = 0L
            deviceMapCandidateUpdatedEpochMs = 0L
            deviceMapPinPoolCount = 0
            deviceMapPinPoolEstimatedBytes = 0L
            deviceMapPinPoolUpdatedEpochMs = 0L
        }
    }

    fun snapshot(nowEpochMs: Long = System.currentTimeMillis()): RuntimeUiListMemorySnapshot {
        synchronized(lock) {
            val deviceAge = if (visibleDeviceRowsUpdatedEpochMs > 0L) {
                (nowEpochMs - visibleDeviceRowsUpdatedEpochMs).coerceAtLeast(0L)
            } else {
                null
            }
            val encounterAge = if (visibleEncounterRowsUpdatedEpochMs > 0L) {
                (nowEpochMs - visibleEncounterRowsUpdatedEpochMs).coerceAtLeast(0L)
            } else {
                null
            }
            val mapPinsAge = if (activeDeviceMapPinsUpdatedEpochMs > 0L) {
                (nowEpochMs - activeDeviceMapPinsUpdatedEpochMs).coerceAtLeast(0L)
            } else {
                null
            }
            val pipelineAge = if (pipelineEncounterUpdatedEpochMs > 0L) {
                (nowEpochMs - pipelineEncounterUpdatedEpochMs).coerceAtLeast(0L)
            } else {
                null
            }
            val analysisWindowAge = if (deviceAnalysisWindowUpdatedEpochMs > 0L) {
                (nowEpochMs - deviceAnalysisWindowUpdatedEpochMs).coerceAtLeast(0L)
            } else {
                null
            }
            val analysisListAge = if (deviceAnalysisListUpdatedEpochMs > 0L) {
                (nowEpochMs - deviceAnalysisListUpdatedEpochMs).coerceAtLeast(0L)
            } else {
                null
            }
            val mapCandidatesAge = if (deviceMapCandidateUpdatedEpochMs > 0L) {
                (nowEpochMs - deviceMapCandidateUpdatedEpochMs).coerceAtLeast(0L)
            } else {
                null
            }
            val mapPinPoolAge = if (deviceMapPinPoolUpdatedEpochMs > 0L) {
                (nowEpochMs - deviceMapPinPoolUpdatedEpochMs).coerceAtLeast(0L)
            } else {
                null
            }
            return RuntimeUiListMemorySnapshot(
                visibleDeviceRows = visibleDeviceRows,
                visibleDeviceRowsEstimatedBytes = visibleDeviceRowsEstimatedBytes,
                visibleDeviceRowsSampleAgeMs = deviceAge,
                visibleEncounterRows = visibleEncounterRows,
                visibleEncounterRowsEstimatedBytes = visibleEncounterRowsEstimatedBytes,
                visibleEncounterRowsSampleAgeMs = encounterAge,
                activeDeviceMapPins = activeDeviceMapPins,
                activeDeviceMapPinsEstimatedBytes = activeDeviceMapPinsEstimatedBytes,
                activeDeviceMapPinsSampleAgeMs = mapPinsAge,
                pipelineEncounterCount = pipelineEncounterCount,
                pipelineEncounterEstimatedBytes = pipelineEncounterEstimatedBytes,
                pipelineEncounterSampleAgeMs = pipelineAge,
                deviceAnalysisWindowCount = deviceAnalysisWindowCount,
                deviceAnalysisWindowEstimatedBytes = deviceAnalysisWindowEstimatedBytes,
                deviceAnalysisWindowSampleAgeMs = analysisWindowAge,
                deviceAnalysisListCount = deviceAnalysisListCount,
                deviceAnalysisListEstimatedBytes = deviceAnalysisListEstimatedBytes,
                deviceAnalysisListSampleAgeMs = analysisListAge,
                deviceMapCandidateCount = deviceMapCandidateCount,
                deviceMapCandidateEstimatedBytes = deviceMapCandidateEstimatedBytes,
                deviceMapCandidateSampleAgeMs = mapCandidatesAge,
                deviceMapPinPoolCount = deviceMapPinPoolCount,
                deviceMapPinPoolEstimatedBytes = deviceMapPinPoolEstimatedBytes,
                deviceMapPinPoolSampleAgeMs = mapPinPoolAge
            )
        }
    }
}

private object DeviceMapPinRuntimeCache {
    private val lock = Any()
    private var cachedAtEpochMs: Long = 0L
    private var cachedPins: List<MapPin> = emptyList()

    fun getFresh(nowEpochMs: Long = System.currentTimeMillis()): List<MapPin> {
        synchronized(lock) {
            if (cachedPins.isEmpty()) return emptyList()
            if (nowEpochMs - cachedAtEpochMs > DEVICE_MAP_PIN_CACHE_TTL_MS) {
                cachedPins = emptyList()
                cachedAtEpochMs = 0L
                return emptyList()
            }
            return cachedPins
        }
    }

    fun put(pins: List<MapPin>, nowEpochMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            if (pins.isEmpty()) return
            cachedPins = pins
            cachedAtEpochMs = nowEpochMs
        }
    }

    fun clear() {
        synchronized(lock) {
            cachedPins = emptyList()
            cachedAtEpochMs = 0L
        }
    }

    fun cachedCount(): Int {
        synchronized(lock) {
            return cachedPins.size
        }
    }

    fun cachedAgeMs(nowEpochMs: Long = System.currentTimeMillis()): Long? {
        synchronized(lock) {
            if (cachedPins.isEmpty() || cachedAtEpochMs <= 0L) return null
            return (nowEpochMs - cachedAtEpochMs).coerceAtLeast(0L)
        }
    }

    fun cachedEstimatedBytes(): Long {
        synchronized(lock) {
            if (cachedPins.isEmpty()) return 0L
            var bytes = 56L
            cachedPins.forEach { pin -> bytes += estimateMapPinHeapBytes(pin) }
            return bytes
        }
    }
}

private object DeviceMapPinDiskCache {
    private const val PREFS = "argus_ui_cache"
    private const val KEY_EPOCH_MS = "device_map_pin_cache_epoch_ms"
    private const val KEY_JSON = "device_map_pin_cache_json"
    private const val MAX_PINS = 2500
    private const val MAX_SEARCHABLE_METADATA_CHARS = 1400
    private const val MAX_SNIPPET_CHARS = 700

    fun getFresh(
        context: android.content.Context,
        nowEpochMs: Long = System.currentTimeMillis()
    ): List<MapPin> {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val cachedAtEpochMs = prefs.getLong(KEY_EPOCH_MS, 0L)
        if (cachedAtEpochMs <= 0L || nowEpochMs - cachedAtEpochMs > DEVICE_MAP_PIN_DISK_CACHE_TTL_MS) {
            clear(context)
            return emptyList()
        }

        val raw = prefs.getString(KEY_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (index in 0 until arr.length()) {
                    val item = arr.optJSONObject(index) ?: continue
                    val lat = item.optDouble("lat", Double.NaN)
                    val lon = item.optDouble("lon", Double.NaN)
                    if (!lat.isFinite() || !lon.isFinite()) continue

                    val savedSnippet = item.optString("snippet", "")
                    val snippetBuilder = if (savedSnippet.isBlank()) {
                        null
                    } else {
                        { savedSnippet }
                    }

                    add(
                        MapPin(
                            position = LatLng(lat, lon),
                            title = item.optString("title", ""),
                            snippetBuilder = snippetBuilder,
                            searchableMetadata = item.optString("searchableMetadata", ""),
                            timestampEpochMs = item.optLong("timestampEpochMs", 0L),
                            source = item.optString("source", ""),
                            primaryId = item.optString("primaryId", ""),
                            secondaryId = item.optStringOrNull("secondaryId"),
                            markerGlyphOverride = item.optStringOrNull("markerGlyphOverride"),
                            trackerFamilyBadge = item.optStringOrNull("trackerFamilyBadge"),
                            encounterTimestampEpochMs = if (item.has("encounterTimestampEpochMs")) {
                                item.optLong("encounterTimestampEpochMs")
                            } else {
                                null
                            },
                            aircraftIconType = item.optStringOrNull("aircraftIconType"),
                            aircraftLabel = item.optStringOrNull("aircraftLabel"),
                            aircraftAffiliation = item.optStringOrNull("aircraftAffiliation"),
                            headingDegrees = if (item.has("headingDegrees")) {
                                item.optDouble("headingDegrees")
                            } else {
                                null
                            },
                            motionBadge = item.optStringOrNull("motionBadge"),
                            motionSpeedMps = if (item.has("motionSpeedMps")) {
                                item.optDouble("motionSpeedMps")
                            } else {
                                null
                            },
                            isLive = item.optBoolean("isLive", true)
                        )
                    )
                }
            }
        }.getOrElse {
            clear(context)
            emptyList()
        }
    }

    fun put(context: android.content.Context, pins: List<MapPin>, nowEpochMs: Long = System.currentTimeMillis()) {
        if (pins.isEmpty()) return
        val payload = JSONArray().apply {
            pins
                .asSequence()
                .take(MAX_PINS)
                .forEach { pin ->
                    put(
                        JSONObject().apply {
                            put("lat", pin.position.latitude)
                            put("lon", pin.position.longitude)
                            put("title", pin.title)
                            put("snippet", pin.snippetBuilder?.invoke()?.take(MAX_SNIPPET_CHARS).orEmpty())
                            put(
                                "searchableMetadata",
                                pin.searchableMetadata.take(MAX_SEARCHABLE_METADATA_CHARS)
                            )
                            put("timestampEpochMs", pin.timestampEpochMs)
                            put("source", pin.source)
                            put("primaryId", pin.primaryId)
                            pin.secondaryId?.let { put("secondaryId", it) }
                            pin.markerGlyphOverride?.let { put("markerGlyphOverride", it) }
                            pin.trackerFamilyBadge?.let { put("trackerFamilyBadge", it) }
                            pin.encounterTimestampEpochMs?.let { put("encounterTimestampEpochMs", it) }
                            pin.aircraftIconType?.let { put("aircraftIconType", it) }
                            pin.aircraftLabel?.let { put("aircraftLabel", it) }
                            pin.aircraftAffiliation?.let { put("aircraftAffiliation", it) }
                            pin.headingDegrees?.let { put("headingDegrees", it) }
                            pin.motionBadge?.let { put("motionBadge", it) }
                            pin.motionSpeedMps?.let { put("motionSpeedMps", it) }
                            put("isLive", pin.isLive)
                        }
                    )
                }
        }

        context
            .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit {
                putLong(KEY_EPOCH_MS, nowEpochMs)
                putString(KEY_JSON, payload.toString())
            }
    }

    fun clear(context: android.content.Context) {
        context
            .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit {
                remove(KEY_EPOCH_MS)
                remove(KEY_JSON)
            }
    }

    fun snapshot(
        context: android.content.Context,
        nowEpochMs: Long = System.currentTimeMillis()
    ): DeviceMapPinDiskCacheSnapshot {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val cachedAtEpochMs = prefs.getLong(KEY_EPOCH_MS, 0L)
        val raw = prefs.getString(KEY_JSON, null)
        val payloadChars = raw?.length ?: 0
        val payloadBytes = raw?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L
        val ageMs = if (cachedAtEpochMs > 0L) {
            (nowEpochMs - cachedAtEpochMs).coerceAtLeast(0L)
        } else {
            null
        }
        return DeviceMapPinDiskCacheSnapshot(
            payloadChars = payloadChars,
            payloadBytes = payloadBytes,
            ageMs = ageMs
        )
    }
}

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

private data class SignalLinkLineTarget(
    val source: String,
    val pin: MapPin,
    val distanceMeters: Double
)

private val SIGNAL_LINK_LINE_SUPPORTED_SOURCES = setOf(
    SourceCatalog.SOURCE_AIRCRAFT,
    SourceCatalog.SOURCE_CAMERA,
    SourceCatalog.SOURCE_REMOTE_ID,
    SourceCatalog.SOURCE_CELL,
    SourceCatalog.SOURCE_WIFI,
    SourceCatalog.SOURCE_WIFI_SWEEP,
    SourceCatalog.SOURCE_WIFI_DIRECT,
    SourceCatalog.SOURCE_BLUETOOTH_LE,
    SourceCatalog.SOURCE_BLUETOOTH_LE_SWEEP,
    SourceCatalog.SOURCE_BLUETOOTH_CLASSIC,
    SourceCatalog.SOURCE_NFC,
    SourceCatalog.SOURCE_SDR,
    SourceCatalog.SOURCE_UNKNOWN_RF,
    SourceCatalog.SOURCE_ARGUS_MESH
)

private val SIGNAL_LINK_ALL_RANGE_OPTIONS_METERS = listOf(
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

private fun resolveSignalLinkTargets(
    currentLocation: DetectionLocation,
    pins: List<MapPin>,
    selectedSources: Set<String>,
    cameraRequiresInView: Boolean = false,
    includeAllDevices: Boolean = false,
    allDevicesMaxRangeMeters: Double = 5000.0
): List<SignalLinkLineTarget> {
    if (pins.isEmpty()) return emptyList()
    if (selectedSources.isEmpty()) return emptyList()

    fun cameraPasses(distanceMeters: Double, source: String): Boolean {
        if (source != SourceCatalog.SOURCE_CAMERA || !cameraRequiresInView) return true
        return distanceMeters <= CAMERA_IN_VIEW_DISTANCE_THRESHOLD_METERS
    }

    if (includeAllDevices) {
        val maxRange = allDevicesMaxRangeMeters.coerceAtLeast(1.0)
        return pins
            .asSequence()
            .filter { pin -> pin.source in selectedSources }
            .mapNotNull { pin ->
                val distance = distanceFromLocationMeters(
                    fromLat = currentLocation.lat,
                    fromLon = currentLocation.lon,
                    toLat = pin.position.latitude,
                    toLon = pin.position.longitude
                ) ?: return@mapNotNull null
                if (distance > maxRange) return@mapNotNull null
                if (!cameraPasses(distance, pin.source)) return@mapNotNull null
                SignalLinkLineTarget(
                    source = pin.source,
                    pin = pin,
                    distanceMeters = distance
                )
            }
            .sortedBy { it.distanceMeters }
            .toList()
    }

    return selectedSources
        .mapNotNull { source ->
            val nearest = pins
                .asSequence()
                .filter { pin -> pin.source == source }
                .mapNotNull { pin ->
                    val distance = distanceFromLocationMeters(
                        fromLat = currentLocation.lat,
                        fromLon = currentLocation.lon,
                        toLat = pin.position.latitude,
                        toLon = pin.position.longitude
                    ) ?: return@mapNotNull null
                    pin to distance
                }
                .filter { (_, distance) -> cameraPasses(distance, source) }
                .minByOrNull { (_, distance) -> distance }

            nearest?.let { (pin, distance) ->
                SignalLinkLineTarget(
                    source = source,
                    pin = pin,
                    distanceMeters = distance
                )
            }
        }
        .sortedBy { it.distanceMeters }
}

private fun signalLinkRangeLabel(meters: Double): String {
    return if (meters >= 1000.0) {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    } else {
        String.format(Locale.US, "%.0f m", meters)
    }
}

private fun buildSignalWavePolylinePoints(
    start: LatLng,
    end: LatLng,
    phase: Float,
    waveCount: Int = 7,
    sampleCount: Int = 56
): List<LatLng> {
    val distanceMeters = distanceFromLocationMeters(
        fromLat = start.latitude,
        fromLon = start.longitude,
        toLat = end.latitude,
        toLon = end.longitude
    ) ?: return listOf(start, end)
    if (!distanceMeters.isFinite() || distanceMeters < 5.0) return listOf(start, end)

    val heading = bearingDegrees(start, end) ?: return listOf(start, end)
    val safeSamples = sampleCount.coerceAtLeast(8)
    val ampMeters = (distanceMeters * 0.012).coerceIn(4.0, 22.0)
    val phaseRad = (phase % 1f) * (2f * Math.PI.toFloat())
    val points = ArrayList<LatLng>(safeSamples + 1)

    for (index in 0..safeSamples) {
        val t = index.toDouble() / safeSamples.toDouble()
        val alongMeters = distanceMeters * t
        val basePoint = offsetLatLng(start, alongMeters, heading)
        val envelope = sin(Math.PI * t).coerceAtLeast(0.0)
        val offsetMeters = sin((t * waveCount * 2.0 * Math.PI) + phaseRad) * ampMeters * envelope
        val point = if (offsetMeters == 0.0) {
            basePoint
        } else {
            offsetLatLng(basePoint, kotlin.math.abs(offsetMeters), heading + if (offsetMeters >= 0.0) 90.0 else -90.0)
        }
        points += point
    }
    return points
}

private fun buildRadarSweepSectorPoints(
    center: LatLng,
    radiusMeters: Double,
    headingDegrees: Float,
    halfWidthDegrees: Float = 20f
): List<LatLng> {
    if (radiusMeters <= 0.0) return listOf(center)

    val start = headingDegrees - halfWidthDegrees
    val end = headingDegrees + halfWidthDegrees
    val step = 6f

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
    recentWindowMs: Long
): Double? {
    if (pins.isEmpty()) return null

    val clampedPercentile = MAP_COVERAGE_RADIUS_PERCENTILE.coerceIn(0.50, 1.0)
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

private fun mapPinStableKey(pin: MapPin): String {
    val secondaryIdPart = pin.secondaryId.orEmpty()
    return "${pin.source}|${pin.primaryId}|$secondaryIdPart|${pin.timestampEpochMs}|${pin.position.latitude}|${pin.position.longitude}"
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
        selected[mapPinStableKey(pin)] = pin
    }

    pins.forEach { pin ->
        if (selected.size >= safeLimit) return@forEach
        val key = mapPinStableKey(pin)
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
    pins: List<MapPin>
): List<MapPin> {
    if (pins.size < 2) return pins

    val safeMinSeparationMeters = 1.2
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
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) -
        sin(lat1) * cos(lat2) * cos(dLon)
    val raw = Math.toDegrees(atan2(y, x))
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
        val key = mapPinStableKey(pin)
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
        val key = mapPinStableKey(pin)
        selected[key] = pin
    }

    if (selected.size < safeLimit) {
        val step = pins.size.toDouble() / safeLimit.toDouble()
        var index = 0.0
        while (selected.size < safeLimit && index < pins.size) {
            val pin = pins[index.toInt().coerceIn(0, pins.lastIndex)]
            val key = mapPinStableKey(pin)
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
        settingsLabel = "Bluetooth (LE + Remote ID)",
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

private const val DEVICE_MARKER_ICON_CACHE_MAX = 320
private const val DEVICE_DOT_MARKER_ICON_CACHE_MAX = 96
private const val AIRCRAFT_MARKER_ICON_CACHE_MAX = 96
private const val CLUSTER_MARKER_ICON_CACHE_MAX = 80
private const val NO_FLY_MARKER_ICON_CACHE_MAX = 24

private val deviceMarkerIconCache = mutableMapOf<String, BitmapDescriptor>()
private val deviceDotMarkerIconCache = mutableMapOf<String, BitmapDescriptor>()
private val aircraftMarkerIconCache = mutableMapOf<String, BitmapDescriptor>()
private val clusterMarkerIconCache = mutableMapOf<String, BitmapDescriptor>()
private val noFlyZoneMarkerIconCache = mutableMapOf<String, BitmapDescriptor>()

private fun clearMapMarkerIconCaches() {
    deviceMarkerIconCache.clear()
    deviceDotMarkerIconCache.clear()
    aircraftMarkerIconCache.clear()
    clusterMarkerIconCache.clear()
    noFlyZoneMarkerIconCache.clear()
}

internal fun releaseMapUiMemory(
    context: android.content.Context? = null,
    level: Int = ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
) {
    val shouldClearMarkerIcons =
        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND

    if (shouldClearMarkerIcons) {
        clearMapMarkerIconCaches()
    }

    val shouldClearDevicePinCaches =
        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND

    if (shouldClearDevicePinCaches) {
        DeviceMapPinRuntimeCache.clear()
        if (context != null) {
            DeviceMapPinDiskCache.clear(context)
        }
    }
}

private fun <K, V> MutableMap<K, V>.putWithLimit(key: K, value: V, maxSize: Int) {
    this[key] = value
    val overflow = size - maxSize
    if (overflow <= 0) return
    val iterator = entries.iterator()
    repeat(overflow) {
        if (!iterator.hasNext()) return
        iterator.next()
        iterator.remove()
    }
}

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
        noFlyZoneMarkerIconCache.putWithLimit(key, it, NO_FLY_MARKER_ICON_CACHE_MAX)
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

private fun stickyCompassMapLiveModeLabel(mode: String): String = when (mode) {
    ScanSettings.STICKY_COMPASS_MAP_LIVE_MODE_FORCE_LIVE_ONLY -> "Force Live Only"
    ScanSettings.STICKY_COMPASS_MAP_LIVE_MODE_FOLLOW_DEVICE_MAP -> "Follow Device Map"
    else -> "Follow Device Map"
}

private fun mapScannerSweepSpeedPresetLabel(preset: String): String = when (preset) {
    ScanSettings.MAP_SCANNER_SWEEP_SPEED_PRESET_CONSERVATIVE -> "Conservative"
    ScanSettings.MAP_SCANNER_SWEEP_SPEED_PRESET_SMOOTH -> "Smooth"
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

private fun clusterPinsForRender(
    pins: List<MapPin>,
    zoom: Float,
    rangeLevel: Int,
    forceSinglePinKeys: Set<String> = emptySet()
): List<MapRenderItem> {
    if (pins.size < 8) {
        return pins.map { MapRenderItem.SinglePin(it) }
    }

    // At high zoom levels, render all pins individually for precise inspection.
    if (zoom >= 13.5f) {
        return pins.map { MapRenderItem.SinglePin(it) }
    }

    val baseClusterRadiusMeters = when {
        zoom < 7.0f -> 18_000.0
        zoom < 9.0f -> 9_000.0
        zoom < 11.0f -> 4_500.0
        else -> 2_200.0
    }
    val clusterRadiusMeters = (baseClusterRadiusMeters * mapClusterRangeScale(rangeLevel)).coerceAtLeast(150.0)

    val latCellDegrees = (clusterRadiusMeters / 111_132.0).coerceAtLeast(0.001)
    val lonCellDegrees = latCellDegrees

    val stableKeys = pins.map(::mapPinStableKey)
    val bucketToIndices = LinkedHashMap<String, MutableList<Int>>()
    pins.forEachIndexed { index, pin ->
        val latKey = kotlin.math.floor(pin.position.latitude / latCellDegrees).toInt()
        val lonKey = kotlin.math.floor(pin.position.longitude / lonCellDegrees).toInt()
        val key = "$latKey:$lonKey"
        bucketToIndices.getOrPut(key) { mutableListOf() }.add(index)
    }

    val visited = BooleanArray(pins.size)
    val result = ArrayList<MapRenderItem>(pins.size)

    pins.indices.forEach { seedIndex ->
        if (visited[seedIndex]) return@forEach

        val seed = pins[seedIndex]
        val seedStableKey = stableKeys[seedIndex]
        if (seedStableKey in forceSinglePinKeys) {
            visited[seedIndex] = true
            result += MapRenderItem.SinglePin(seed)
            return@forEach
        }
        val seedLatKey = kotlin.math.floor(seed.position.latitude / latCellDegrees).toInt()
        val seedLonKey = kotlin.math.floor(seed.position.longitude / lonCellDegrees).toInt()

        val memberIndices = mutableListOf<Int>()
        for (latOffset in -1..1) {
            for (lonOffset in -1..1) {
                val key = "${seedLatKey + latOffset}:${seedLonKey + lonOffset}"
                val candidates = bucketToIndices[key] ?: continue
                candidates.forEach { candidateIndex ->
                    if (visited[candidateIndex]) return@forEach
                    val candidateStableKey = stableKeys[candidateIndex]
                    if (candidateStableKey in forceSinglePinKeys) return@forEach
                    val candidate = pins[candidateIndex]
                    val distanceMeters = distanceFromLocationMeters(
                        fromLat = seed.position.latitude,
                        fromLon = seed.position.longitude,
                        toLat = candidate.position.latitude,
                        toLon = candidate.position.longitude
                    ) ?: return@forEach
                    if (distanceMeters <= clusterRadiusMeters) {
                        visited[candidateIndex] = true
                        memberIndices += candidateIndex
                    }
                }
            }
        }

        if (memberIndices.isEmpty()) {
            visited[seedIndex] = true
            result += MapRenderItem.SinglePin(seed)
            return@forEach
        }

        if (memberIndices.size == 1) {
            result += MapRenderItem.SinglePin(seed)
            return@forEach
        }

        val bucket = memberIndices.map { pins[it] }
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
        result += MapRenderItem.Cluster(
            position = LatLng(lat, lon),
            count = bucket.size,
            source = dominantSource,
            summary = summary,
            isLive = liveCount > 0
        )
    }

    return result
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
        clusterMarkerIconCache.putWithLimit(key, it, CLUSTER_MARKER_ICON_CACHE_MAX)
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
    deviceMarkerIconCache.putWithLimit(key, descriptor, DEVICE_MARKER_ICON_CACHE_MAX)
    return descriptor
}

private fun markerAircraftIconForPin(pin: MapPin, useSourceOnlyPinColors: Boolean = false): BitmapDescriptor {
    return markerAircraftIconForPin(pin, useSourceOnlyPinColors, compact = false)
}

private fun aircraftMarkerAnchorY(pin: MapPin, compact: Boolean): Float {
    return 0.5f
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
    aircraftMarkerIconCache.putWithLimit(key, descriptor, AIRCRAFT_MARKER_ICON_CACHE_MAX)
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
    deviceDotMarkerIconCache.putWithLimit(key, descriptor, DEVICE_DOT_MARKER_ICON_CACHE_MAX)
    return descriptor
}

private fun identityGlyph(identityName: String): String {
    val normalized = identityName.trim()
    if (normalized.isEmpty()) return "ID"

    val tokens = normalized
        .split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }

    val compact = when {
        tokens.size >= 2 -> {
            val first = tokens.first().firstOrNull()
            val last = tokens.last().firstOrNull()
            buildString {
                if (first != null) append(first)
                if (last != null) append(last)
            }
        }

        else -> normalized.filter { it.isLetterOrDigit() }.take(4)
    }

    return compact
        .ifBlank { "ID" }
        .uppercase(Locale.US)
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

private fun formatBytesMiB(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    val mib = safe / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MiB", mib)
}

private fun formatBytesAdaptive(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe >= 1024L * 1024L -> String.format(Locale.US, "%.2f MiB", safe / (1024.0 * 1024.0))
        safe >= 1024L -> String.format(Locale.US, "%.1f KiB", safe / 1024.0)
        else -> "$safe B"
    }
}

private fun formatAgeSeconds(ageMs: Long?): String {
    val safe = ageMs ?: return "n/a"
    return String.format(Locale.US, "%.1fs", safe.coerceAtLeast(0L) / 1000.0)
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
    if (sensorGateSettings.bluetoothLeEnabled) {
        add(SourceCatalog.KEY_BLE)
        add(SourceCatalog.KEY_BT_CLASSIC)
    }
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
            "Wi-Fi Standard" to "wifiStandard",
            "Channel Width" to "channelWidth",
            "Center Freq 1" to "centerFreq1",
            "Center Freq 2" to "centerFreq2",
            "Passpoint" to "isPasspoint",
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

private enum class AircraftAffiliation {
    CIVILIAN,
    MILITARY,
    GOVERNMENT,
    EMERGENCY,
    UNKNOWN
}

private fun normalizeAircraftDisplayLabel(value: String?): String? {
    val normalized = value?.trim().orEmpty()
    if (normalized.isBlank()) return null
    return normalized.uppercase(Locale.US)
        .replace(Regex("\\s+"), " ")
        .take(12)
        .trim()
        .ifBlank { null }
}

private fun readAircraftDisplayLabel(payload: JSONObject): String? {
    return normalizeAircraftDisplayLabel(
        payload.optString("callsign", "")
            .ifBlank { payload.optString("flight", "") }
            .ifBlank { payload.optString("registration", "") }
            .ifBlank { payload.optString("label", "") }
    )
}

private fun readAircraftAffiliation(payload: JSONObject): AircraftAffiliation {
    val militaryFlag = payload.optBoolean("military", false) || payload.optBoolean("isMilitary", false)
    if (militaryFlag) return AircraftAffiliation.MILITARY

    val signals = listOf(
        payload.optString("operator", ""),
        payload.optString("operatorName", ""),
        payload.optString("owner", ""),
        payload.optString("ownerType", ""),
        payload.optString("category", ""),
        payload.optString("type", ""),
        payload.optString("aircraftType", ""),
        payload.optString("aircraftTypeHint", ""),
        payload.optString("originCountry", ""),
        payload.optString("callsign", "")
    )
        .joinToString(" ")
        .lowercase(Locale.US)

    return when {
        signals.contains("military") ||
            signals.contains("air force") ||
            signals.contains("navy") ||
            signals.contains("army") ||
            signals.contains("usaf") ||
            signals.contains("raf") ||
            signals.contains("nato") -> AircraftAffiliation.MILITARY
        signals.contains("medevac") ||
            signals.contains("air ambulance") ||
            signals.contains("lifeflight") ||
            signals.contains("rescue") ||
            signals.contains("sar") -> AircraftAffiliation.EMERGENCY
        signals.contains("coast guard") ||
            signals.contains("police") ||
            signals.contains("state") ||
            signals.contains("government") ||
            signals.contains("customs") -> AircraftAffiliation.GOVERNMENT
        signals.isBlank() -> AircraftAffiliation.UNKNOWN
        else -> AircraftAffiliation.CIVILIAN
    }
}

private data class AircraftVisualHints(
    val iconType: String,
    val headingDegrees: Double?,
    val displayLabel: String?,
    val affiliation: AircraftAffiliation
)

private fun readAircraftVisualHints(rawPayloadJson: String): AircraftVisualHints {
    val payload = runCatching { JSONObject(rawPayloadJson) }.getOrNull()
        ?: return AircraftVisualHints(
            iconType = "plane",
            headingDegrees = null,
            displayLabel = null,
            affiliation = AircraftAffiliation.UNKNOWN
        )
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
    return AircraftVisualHints(
        iconType = iconType,
        headingDegrees = heading,
        displayLabel = readAircraftDisplayLabel(payload),
        affiliation = readAircraftAffiliation(payload)
    )
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
    val simOperatorCode = payload.optString("simOperator", "")
    if (simOperatorCode.isNotBlank()) fields += "SIM Operator Code" to simOperatorCode
    val simOperatorName = payload.optString("simOperatorName", "")
    if (simOperatorName.isNotBlank()) fields += "SIM Operator" to simOperatorName
    val networkCountryIso = payload.optString("networkCountryIso", "")
    if (networkCountryIso.isNotBlank()) fields += "Network Country" to networkCountryIso.uppercase(Locale.US)
    val simCountryIso = payload.optString("simCountryIso", "")
    if (simCountryIso.isNotBlank()) fields += "SIM Country" to simCountryIso.uppercase(Locale.US)

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
            addIfPresent("RSRP", "rsrp")
            addIfPresent("RSRQ", "rsrq")
            addIfPresent("RSSNR", "rssnr")
            addIfPresent("CQI", "cqi")
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
            addIfPresent("SS RSRQ", "ssRsrq")
            addIfPresent("SS SINR", "ssSinr")
        }

        "WCDMA" -> {
            addIfPresent("Cell ID (CID)", "cid")
            addIfPresent("Location Area Code (LAC)", "lac")
            addIfPresent("Primary Scrambling Code (PSC)", "psc")
            addIfPresent("UARFCN", "uarfcn")
            addIfPresent("Ec/No", "ecNo")
        }

        "GSM" -> {
            addIfPresent("Cell ID (CID)", "cid")
            addIfPresent("Location Area Code (LAC)", "lac")
            addIfPresent("ARFCN", "arfcn")
            addIfPresent("BSIC", "bsic")
            addIfPresent("Bit Error Rate", "bitErrorRate")
        }

        "CDMA" -> {
            addIfPresent("Base Station ID", "basestationId")
            addIfPresent("Network ID", "networkId")
            addIfPresent("System ID", "systemId")
            addIfPresent("EVDO SNR", "evdoSnr")
        }
    }

    addIfPresent("Service State", "serviceState")
    addIfPresent("Data Network Type", "dataNetworkType")
    addIfPresent("Voice Network Type", "voiceNetworkType")
    addIfPresent("Data State", "dataState")
    addIfPresent("Cell Connection Status", "cellConnectionStatus")
    addIfPresent("NR State", "nrState")
    addIfPresent("Emergency Only", "isEmergencyOnly")
    addIfPresent("Roaming", "isRoaming")
    addIfPresent("Data Roaming Enabled", "isDataRoamingEnabled")
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
        source != EncounterSource.WIFI &&
        source != EncounterSource.WIFI_DIRECT &&
        source != EncounterSource.WIFI_SWEEP &&
        source != EncounterSource.BLUETOOTH_LE_SWEEP
}

private fun isApproachEligibleSource(source: String): Boolean {
    return source != EncounterSource.CAMERA.name &&
        source != EncounterSource.WIFI.name &&
        source != EncounterSource.WIFI_DIRECT.name &&
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

private data class CellObservation(
    val timestampEpochMs: Long,
    val radio: String,
    val registered: Boolean,
    val operatorCode: String?,
    val mcc: String?,
    val mnc: String?,
    val dataNetworkType: Int?,
    val rssiDbm: Int?,
    val asu: Int?,
    val pci: Int?,
    val tac: Int?,
    val lac: Int?,
    val ci: Long?,
    val nrState: Int?
)

private fun analyzeCellThreat(encounters: List<Encounter>): CellThreatSignal? {
    val cellEncounters = encounters
        .asSequence()
        .filter { it.source == EncounterSource.CELL }
        .sortedByDescending { it.timestampEpochMs }
        .take(40)
        .toList()
    if (cellEncounters.isEmpty()) return null

    val observations = cellEncounters.mapNotNull { encounter ->
        val payload = runCatching { JSONObject(encounter.rawPayloadJson) }.getOrNull() ?: return@mapNotNull null
        CellObservation(
            timestampEpochMs = encounter.timestampEpochMs,
            radio = payload.optString("radio", "").trim().uppercase(Locale.US),
            registered = payload.optBoolean("registered", false),
            operatorCode = payload.optString("networkOperator", "").trim().takeIf { it.isNotBlank() },
            mcc = payload.optString("mcc", "").trim().takeIf { it.isNotBlank() },
            mnc = payload.optString("mnc", "").trim().takeIf { it.isNotBlank() },
            dataNetworkType = payload.optIntOrNull("dataNetworkType"),
            rssiDbm = encounter.rssiDbm,
            asu = payload.optIntOrNull("asu"),
            pci = payload.optIntOrNull("pci"),
            tac = payload.optIntOrNull("tac"),
            lac = payload.optIntOrNull("lac"),
            ci = payload.optLongOrNull("ci") ?: payload.optLongOrNull("cid") ?: payload.optLongOrNull("nci"),
            nrState = payload.optIntOrNull("nrState")
        )
    }
    if (observations.isEmpty()) return null

    val registered = observations.filter { it.registered }
    val baseline = if (registered.isNotEmpty()) registered else observations
    val sampleCount = baseline.size

    val missingPlmnCount = baseline.count {
        it.registered && (it.mcc.isNullOrBlank() || it.mnc.isNullOrBlank())
    }
    val operatorMismatchCount = baseline.count { obs ->
        val op = obs.operatorCode?.filter(Char::isDigit)
        val mcc = obs.mcc?.filter(Char::isDigit)
        val mnc = obs.mnc?.filter(Char::isDigit)
        op != null && mcc != null && mnc != null && op.length >= 5 && !op.startsWith(mcc + mnc)
    }
    val suspiciousIdentityCount = baseline.count { obs ->
        val badPci = obs.pci != null && obs.pci <= 0
        val badTac = (obs.tac != null && obs.tac <= 0) || (obs.lac != null && obs.lac <= 0)
        val badCi = obs.ci != null && obs.ci <= 0L
        obs.registered && (badPci || badTac || badCi)
    }
    val downgradeBiasCount = baseline.count { obs ->
        val lowGen = obs.radio == "GSM" || obs.radio == "WCDMA" || obs.radio == "CDMA"
        val strongSignal = (obs.rssiDbm ?: Int.MIN_VALUE) >= -78 || (obs.asu ?: Int.MIN_VALUE) >= 20
        lowGen && strongSignal && obs.registered
    }

    val sortedOldestFirst = baseline.sortedBy { it.timestampEpochMs }
    val abruptFallbackCount = sortedOldestFirst.zipWithNext().count { (prev, curr) ->
        val elapsedMs = (curr.timestampEpochMs - prev.timestampEpochMs).coerceAtLeast(0L)
        val downgrade = radioGenerationRank(curr.radio) < radioGenerationRank(prev.radio)
        val abrupt = elapsedMs in 1..(4 * 60 * 1000)
        val strongCurrent = (curr.rssiDbm ?: Int.MIN_VALUE) >= -85
        downgrade && abrupt && strongCurrent
    }

    val distinctTacLac = baseline
        .mapNotNull { obs -> obs.tac ?: obs.lac }
        .toSet()
        .size
    val distinctPci = baseline
        .mapNotNull { it.pci }
        .toSet()
        .size

    val missingPlmnRatio = missingPlmnCount / sampleCount.toDouble()
    val mismatchRatio = operatorMismatchCount / sampleCount.toDouble()
    val idAnomalyRatio = suspiciousIdentityCount / sampleCount.toDouble()
    val downgradeRatio = downgradeBiasCount / sampleCount.toDouble()

    var score = 0.0
    val indicators = mutableListOf<String>()

    if (missingPlmnRatio >= 0.20) {
        score += 0.22
        indicators += "Missing PLMN on registered cells"
    }
    if (mismatchRatio >= 0.20) {
        score += 0.18
        indicators += "Operator code mismatch vs MCC/MNC"
    }
    if (idAnomalyRatio >= 0.18) {
        score += 0.24
        indicators += "Invalid/zero identity fields while registered"
    }
    if (downgradeRatio >= 0.20) {
        score += 0.18
        indicators += "Strong-signal low-generation serving cell bias"
    }
    if (abruptFallbackCount >= 2) {
        score += 0.16
        indicators += "Abrupt radio generation fallback transitions"
    }
    if (distinctTacLac >= 4) {
        score += 0.10
        indicators += "High TAC/LAC churn"
    }
    if (distinctPci >= 8) {
        score += 0.08
        indicators += "High PCI churn"
    }

    val nrRestrictedSignals = baseline.count { obs ->
        obs.radio == "NR" && (obs.nrState != null && obs.nrState == 1)
    }
    if (nrRestrictedSignals >= 3) {
        score += 0.08
        indicators += "NR state repeatedly restricted"
    }

    score = score.coerceIn(0.0, 1.0)
    val sampleWeight = (sampleCount / 12.0).coerceIn(0.15, 1.0)
    val confidence = ((0.7 * score) + (0.3 * sampleWeight)).coerceIn(0.0, 1.0)

    val level = when {
        score >= 0.70 && confidence >= 0.62 -> CellThreatLevel.HIGH
        score >= 0.48 && confidence >= 0.48 -> CellThreatLevel.MEDIUM
        score >= 0.30 -> CellThreatLevel.LOW
        else -> CellThreatLevel.NONE
    }

    val summary = when (level) {
        CellThreatLevel.HIGH -> "Strong IMSI-catcher/stingray indicators across recent cell telemetry"
        CellThreatLevel.MEDIUM -> "Multiple suspicious cellular indicators; verify with additional RF evidence"
        CellThreatLevel.LOW -> "Early warning signs in cell metadata; continue monitoring"
        CellThreatLevel.NONE -> "No strong IMSI-catcher indicators in current cellular sample window"
    }

    return CellThreatSignal(
        level = level,
        confidence = confidence,
        score = score,
        sampleCount = sampleCount,
        summary = summary,
        indicators = indicators.take(4)
    )
}

private fun radioGenerationRank(radio: String): Int = when (radio.uppercase(Locale.US)) {
    "NR" -> 5
    "LTE" -> 4
    "WCDMA" -> 3
    "GSM" -> 2
    "CDMA" -> 2
    else -> 1
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

private fun <K, V> boundedLruMutableMap(maxEntries: Int): MutableMap<K, V> {
    val safeMaxEntries = maxOf(1, maxEntries)
    return object : LinkedHashMap<K, V>(safeMaxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > safeMaxEntries
        }
    }
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
    return AppPermissions.hasPostNotificationsPermission(context)
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

private fun ensureCameraInViewNotificationChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val existing = manager.getNotificationChannel(CAMERA_IN_VIEW_ALERT_CHANNEL_ID)
    if (existing != null) return

    val channel = NotificationChannel(
        CAMERA_IN_VIEW_ALERT_CHANNEL_ID,
        "Camera In-View Alerts",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Alerts when your location is near a mapped public camera"
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

private fun ensureStingrayNotificationChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val existing = manager.getNotificationChannel(STINGRAY_ALERT_CHANNEL_ID)
    if (existing != null) return

    val channel = NotificationChannel(
        STINGRAY_ALERT_CHANNEL_ID,
        "Stingray Detection Alerts",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Alerts when cellular telemetry indicates likely IMSI-catcher/stingray activity"
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

    notifyIfPostNotificationsPermitted(context, notificationId, notification)
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
    notifyIfPostNotificationsPermitted(context, notificationId, notification)
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
    notifyIfPostNotificationsPermitted(context, notificationId, notification)
}

private fun sendCameraInViewNotification(
    context: android.content.Context,
    device: DeviceItem,
    distanceMeters: Double
) {
    val title = "Camera nearby"
    val sourceLabel = listSourceLabel(device.source, device.secondaryId)
    val content = "$sourceLabel ${device.primaryId} • ${formatDistanceFeetMiles(distanceMeters)}"
    val notification = NotificationCompat.Builder(context, CAMERA_IN_VIEW_ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_more)
        .setContentTitle(title)
        .setContentText(content)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    val notificationId = ("camera-in-view:${device.source}|${device.primaryId}").hashCode()
    notifyIfPostNotificationsPermitted(context, notificationId, notification)
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

    notifyIfPostNotificationsPermitted(context, notificationId, notification)
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
    notifyIfPostNotificationsPermitted(context, notificationId, notification)
}

private fun sendStingrayNotification(context: android.content.Context, device: DeviceItem) {
    val threat = device.cellThreat ?: return
    val confidencePct = (threat.confidence * 100.0).toInt().coerceIn(0, 100)
    val title = when (threat.level) {
        CellThreatLevel.HIGH -> "High stingray risk detected"
        CellThreatLevel.MEDIUM -> "Possible stingray activity detected"
        CellThreatLevel.LOW -> "Low stingray signal"
        CellThreatLevel.NONE -> "Cell threat update"
    }
    val content = buildString {
        append(listSourceLabel(device.source, device.secondaryId))
        append(" ")
        append(device.primaryId)
        append(" • ")
        append(threat.level.name)
        append(" • ")
        append(confidencePct)
        append("% confidence")
    }

    val notification = NotificationCompat.Builder(context, STINGRAY_ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle(title)
        .setContentText(content)
        .setStyle(NotificationCompat.BigTextStyle().bigText("$content • ${threat.summary}"))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    val notificationId = ("stingray:${device.source}|${device.primaryId}").hashCode()
    notifyIfPostNotificationsPermitted(context, notificationId, notification)
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

    val tapIntent = Intent(context, MainActivity::class.java).apply {
        action = ACTION_OPEN_MAGNETIC_MONITOR
        putExtra(EXTRA_MAGNETIC_OPEN_POPUP, true)
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    val tapPendingIntent = PendingIntent.getActivity(
        context,
        0x4D47, // 'MG'
        tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, MAGNETIC_INCREASE_ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_more)
        .setContentTitle(title)
        .setContentText(content)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(tapPendingIntent)
        .build()

    val notificationId = ("magnetic-increase:${System.currentTimeMillis() / 60_000L}").hashCode()
    notifyIfPostNotificationsPermitted(context, notificationId, notification)
}

private fun notifyIfPostNotificationsPermitted(
    context: android.content.Context,
    notificationId: Int,
    notification: android.app.Notification
) {
    if (!hasPostNotificationsPermission(context)) return
    try {
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    } catch (_: SecurityException) {
    }
}

private fun magneticAlertSeverity(
    currentMagnitudeMicroTesla: Double,
    deltaMicroTesla: Double
): Double {
    val magnitudeComponent = ((currentMagnitudeMicroTesla - MAGNETIC_DISTURBANCE_UPPER_BOUND_UT) / 35.0)
        .coerceIn(0.0, 1.0)
    val deltaComponent = (deltaMicroTesla / 28.0).coerceIn(0.0, 1.0)
    return maxOf(magnitudeComponent, deltaComponent)
}

private data class MagneticDetectionPopupState(
    val detectionEpochMs: Long,
    val previousMagnitudeMicroTesla: Double,
    val currentMagnitudeMicroTesla: Double,
    val deltaMicroTesla: Double,
    val triggerThresholdMicroTesla: Double,
    val triggerModeLabel: String,
    val triggerDetail: String,
    val thresholdContext: String,
    val signalClass: String,
    val confidencePercent: Int
)

private fun buildMagneticDetectionPopupState(
    detectionEpochMs: Long,
    previousMagnitudeMicroTesla: Double,
    currentMagnitudeMicroTesla: Double,
    deltaMicroTesla: Double,
    triggerThresholdMicroTesla: Double,
    crossedDisturbanceBand: Boolean,
    sharpIncrease: Boolean,
    sustainedHigh: Boolean,
    severity: Double
): MagneticDetectionPopupState {
    val signalClass = when {
        currentMagnitudeMicroTesla >= triggerThresholdMicroTesla + 25.0 || deltaMicroTesla >= 20.0 -> "Strong anomaly"
        currentMagnitudeMicroTesla >= triggerThresholdMicroTesla + 10.0 || deltaMicroTesla >= 12.0 -> "Elevated anomaly"
        else -> "Threshold crossing"
    }

    val confidencePercent = (45 + (severity.coerceIn(0.0, 1.0) * 50.0).roundToInt())
        .coerceIn(40, 95)

    val triggerModeLabel = when {
        sharpIncrease && sustainedHigh -> "Rapid spike + sustained high"
        sharpIncrease -> "Rapid spike override"
        sustainedHigh -> "Sustained high field"
        crossedDisturbanceBand -> "Threshold crossing"
        else -> "Anomaly trigger"
    }

    val triggerDetail = when {
        sharpIncrease && sustainedHigh ->
            "Triggered by a sharp increase and high sustained intensity."
        sharpIncrease ->
            "Triggered by a rapid increase; this can fire even when absolute threshold is set high."
        sustainedHigh ->
            "Triggered by sustained high field intensity over baseline safeguards."
        crossedDisturbanceBand ->
            "Triggered by crossing the configured threshold boundary."
        else ->
            "Triggered by magnetic anomaly heuristics."
    }

    val thresholdContext = if (crossedDisturbanceBand) {
        "Threshold: ${String.format(Locale.US, "%.1f", triggerThresholdMicroTesla)} uT"
    } else {
        "Configured threshold: ${String.format(Locale.US, "%.1f", triggerThresholdMicroTesla)} uT (not the direct trigger)"
    }

    return MagneticDetectionPopupState(
        detectionEpochMs = detectionEpochMs,
        previousMagnitudeMicroTesla = previousMagnitudeMicroTesla,
        currentMagnitudeMicroTesla = currentMagnitudeMicroTesla,
        deltaMicroTesla = deltaMicroTesla,
        triggerThresholdMicroTesla = triggerThresholdMicroTesla,
        triggerModeLabel = triggerModeLabel,
        triggerDetail = triggerDetail,
        thresholdContext = thresholdContext,
        signalClass = signalClass,
        confidencePercent = confidencePercent
    )
}

private suspend fun playMagneticRhythm(
    severity: Double,
    playMs: Long
) {
    val clampedSeverity = severity.coerceIn(0.0, 1.0)
    val bpmRange = (MAGNETIC_RHYTHM_MAX_BPM - MAGNETIC_RHYTHM_MIN_BPM).toDouble()
    val bpm = (MAGNETIC_RHYTHM_MIN_BPM + bpmRange * clampedSeverity).toInt()
        .coerceIn(MAGNETIC_RHYTHM_MIN_BPM, MAGNETIC_RHYTHM_MAX_BPM)
    val intervalMs = (60_000.0 / bpm.toDouble()).toLong().coerceIn(120L, 900L)
    val toneMs = (intervalMs * 0.38).toInt().coerceIn(70, 220)
    val deadline = System.currentTimeMillis() + playMs.coerceAtLeast(400L)

    val tone = createMagneticToneGenerator() ?: return
    try {
        while (System.currentTimeMillis() < deadline) {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, toneMs)
            delay(intervalMs)
        }
    } finally {
        tone.release()
    }
}

private fun createMagneticToneGenerator(): ToneGenerator? {
    val candidates = listOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_ALARM
    )
    for (stream in candidates) {
        val tone = runCatching { ToneGenerator(stream, 90) }.getOrNull()
        if (tone != null) return tone
    }
    return null
}

@Composable
private fun MagneticDetectionPopupDialog(
    popup: MagneticDetectionPopupState,
    liveCurrentMagnitudeMicroTesla: Double,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    auxiliaryLabel: String? = null,
    onAuxiliary: (() -> Unit)? = null
) {
    val liveDeltaMicroTesla = liveCurrentMagnitudeMicroTesla - popup.previousMagnitudeMicroTesla
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Potential Device Detected") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "A magnetic anomaly event was detected.",
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = "Signal class: ${popup.signalClass}")
                Text(text = "Trigger mode: ${popup.triggerModeLabel}", fontWeight = FontWeight.Medium)
                Text(
                    text = popup.triggerDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = "Confidence estimate: ${popup.confidencePercent}%")
                Text(
                    text = "Current: ${String.format(Locale.US, "%.1f", liveCurrentMagnitudeMicroTesla)} uT • Delta: ${String.format(Locale.US, "%+.1f", liveDeltaMicroTesla)} uT"
                )
                Text(
                    text = "Event snapshot: ${String.format(Locale.US, "%.1f", popup.currentMagnitudeMicroTesla)} uT • ${String.format(Locale.US, "%+.1f", popup.deltaMicroTesla)} uT",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${popup.thresholdContext} • Detected: ${formatEpoch(popup.detectionEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!auxiliaryLabel.isNullOrBlank() && onAuxiliary != null) {
                    TextButton(onClick = onAuxiliary) {
                        Text(auxiliaryLabel)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        }
    )
}

@Composable
private fun rememberRealtimeMagneticMagnitudeMicroTesla(
    enabled: Boolean
): androidx.compose.runtime.State<Double?> {
    val context = LocalContext.current
    val magnitudeState = remember(context, enabled) { mutableStateOf<Double?>(null) }

    DisposableEffect(context, enabled) {
        if (!enabled) {
            magnitudeState.value = null
            return@DisposableEffect onDispose { }
        }

        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (sensorManager == null || sensor == null) {
            magnitudeState.value = null
            return@DisposableEffect onDispose { }
        }

        val sensorDelay = SensorManager.SENSOR_DELAY_FASTEST

        var lastEmitElapsedMs = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val values = event.values
                if (values.size < 3) return

                val x = values[0].toDouble()
                val y = values[1].toDouble()
                val z = values[2].toDouble()
                val magnitude = sqrt(x * x + y * y + z * z)
                if (!magnitude.isFinite()) return

                // Keep high-rate sensing but throttle UI updates to a manageable cadence.
                val nowElapsedMs = SystemClock.elapsedRealtime()
                if (nowElapsedMs - lastEmitElapsedMs < 150L) return
                lastEmitElapsedMs = nowElapsedMs
                magnitudeState.value = magnitude
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val handler = Handler(Looper.getMainLooper())
        val registered = sensorManager.registerListener(listener, sensor, sensorDelay, handler)
        if (!registered) {
            magnitudeState.value = null
            return@DisposableEffect onDispose { }
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return magnitudeState
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
    var showCellThreatOnly by rememberSaveable { mutableStateOf(false) }
    var showInsecureOnly by rememberSaveable { mutableStateOf(false) }
    var showHomeAwaySuspiciousOnly by rememberSaveable { mutableStateOf(false) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val currentLocation by if (showDistance) {
        LocationSnapshotProvider.observe(context).collectAsState(
            initial = LocationSnapshotProvider.read(context)
        )
    } else {
        remember { mutableStateOf<DetectionLocation?>(null) }
    }
    val selectedEncounters = remember(allEncounters) {
        selectRecentEncounterWindow(
            encounters = allEncounters,
            windowMs = DEVICE_ANALYSIS_WINDOW_MS,
            maxEncounters = DEVICE_ANALYSIS_MAX_ENCOUNTERS
        )
    }
    LaunchedEffect(selectedEncounters) {
        RuntimeUiListMemoryGauge.updateDeviceAnalysisWindow(selectedEncounters)
    }
    var devices by remember { mutableStateOf<List<DeviceItem>>(emptyList()) }
    LaunchedEffect(
        selectedEncounters,
        sortMode,
        approachDetectionEnabled,
        ownedDeviceKeys,
        homePoint,
        wifiRandomizedOneOffSuppressionEnabled,
        bleRandomizedOneOffSuppressionEnabled
    ) {
        val computed = withContext(Dispatchers.Default) {
            runCatching {
                buildDeviceItems(
                    encounters = selectedEncounters,
                    sortMode = sortMode,
                    approachDetectionEnabled = approachDetectionEnabled,
                    ownedDeviceKeys = ownedDeviceKeys,
                    homePoint = homePoint,
                    suppressLikelyRandomizedWifiOneOffs = wifiRandomizedOneOffSuppressionEnabled,
                    suppressLikelyRandomizedBleOneOffs = bleRandomizedOneOffSuppressionEnabled
                )
            }.getOrDefault(emptyList())
        }
        devices = computed
    }
    LaunchedEffect(devices) {
        RuntimeUiListMemoryGauge.updateDeviceAnalysisList(devices)
    }
    val sourceOptions = remember(devices) {
        orderedEncounterSourceOptions(devices.map { it.source }.toSet())
    }
    var filteredDeviceCount by remember { mutableStateOf(0) }
    var pagedDevices by remember { mutableStateOf<List<DeviceItem>>(emptyList()) }
    var hasMoreDevices by remember { mutableStateOf(false) }
    var visibleDeviceCount by rememberSaveable { mutableStateOf(DETECTION_LIST_PAGE_SIZE) }
    LaunchedEffect(
        devices,
        sourceFilter,
        queryFilter,
        showOwnedOnly,
        showTrackerRiskOnly,
        showCellThreatOnly,
        showInsecureOnly,
        showHomeAwaySuspiciousOnly,
        homePoint,
        showDistance,
        sortByDistance,
        currentLocation,
        visibleDeviceCount
    ) {
        val targetVisible = visibleDeviceCount.coerceAtLeast(DETECTION_LIST_PAGE_SIZE)
        val (filteredCount, visibleRows) = withContext(Dispatchers.Default) {
            runCatching {
                val filteredDevices = devices.asSequence().filter { device ->
                    val sourceMatches = sourceFilter == null || device.source == sourceFilter
                    val queryMatches = queryFilter.isBlank() ||
                        device.primaryId.contains(queryFilter, ignoreCase = true) ||
                        (device.secondaryId?.contains(queryFilter, ignoreCase = true) == true)
                    val ownedMatches = !showOwnedOnly || device.isOwned
                    val riskMatches = !showTrackerRiskOnly ||
                        (device.trackerRisk?.level == TrackerRiskLevel.HIGH || device.trackerRisk?.level == TrackerRiskLevel.MEDIUM)
                    val cellThreatMatches = !showCellThreatOnly ||
                        (device.cellThreat?.level == CellThreatLevel.HIGH || device.cellThreat?.level == CellThreatLevel.MEDIUM)
                    val insecureMatches = !showInsecureOnly || device.connectionSecurity?.isInsecure == true
                    val homeAwaySuspiciousMatches = !showHomeAwaySuspiciousOnly || homePoint == null || (
                        !device.isOwned &&
                            device.trackerRisk?.seenAtHome == true &&
                            device.trackerRisk?.seenAwayFromHome == true
                        )
                    sourceMatches && queryMatches && ownedMatches && riskMatches && cellThreatMatches && insecureMatches && homeAwaySuspiciousMatches
                }

                if (!showDistance || !sortByDistance) {
                    var count = 0
                    val visible = ArrayList<DeviceItem>(targetVisible)
                    filteredDevices.forEach { device ->
                        count += 1
                        if (visible.size < targetVisible) {
                            visible += device
                        }
                    }
                    count to visible
                } else {
                    val ranked = filteredDevices
                        .map { device -> device to (distanceForDeviceMeters(device, currentLocation) ?: Double.MIN_VALUE) }
                        .sortedWith(
                            compareByDescending<Pair<DeviceItem, Double>> { it.second }
                                .thenByDescending { it.first.lastSeenEpochMs }
                        )
                        .map { it.first }
                        .toList()
                    ranked.size to ranked.take(targetVisible)
                }
            }.getOrElse {
                0 to emptyList()
            }
        }
        filteredDeviceCount = filteredCount
        pagedDevices = visibleRows
        hasMoreDevices = filteredCount > visibleRows.size
    }
    val distanceByDeviceKey = remember(pagedDevices, showDistance, currentLocation) {
        if (!showDistance || currentLocation == null) {
            emptyMap()
        } else {
            pagedDevices.associate { device ->
                "${device.source}|${device.primaryId}" to distanceForDeviceMeters(device, currentLocation)
            }
        }
    }
    LaunchedEffect(pagedDevices) {
        RuntimeUiListMemoryGauge.updateVisibleDeviceRows(pagedDevices)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                            label = "Cell Threat Only",
                            checked = showCellThreatOnly,
                            onCheckedChange = { showCellThreatOnly = it }
                        )
                        CompactSwitchControl(
                            label = "Insecure Connectivity Only",
                            checked = showInsecureOnly,
                            onCheckedChange = { showInsecureOnly = it }
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
        Text("Showing ${pagedDevices.size} of ${filteredDeviceCount} (filtered ${filteredDeviceCount})")
        Text(
            "Filters and search run across the full dataset; Load More only controls how many rows render at once.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            pagedDevices.forEach { device ->
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
                        if (device.connectionSecurity?.isInsecure == true) {
                            Text(
                                text = "Insecure connectivity risk: ${device.connectionSecurity.summary}. Avoid connecting.",
                                color = Color(0xFFB3261E),
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
                        when (device.cellThreat?.level) {
                            CellThreatLevel.HIGH -> Text(
                                text = "Cell Threat: HIGH (IMSI-catcher indicators)",
                                color = Color(0xFFB3261E),
                                fontWeight = FontWeight.Bold
                            )

                            CellThreatLevel.MEDIUM -> Text(
                                text = "Cell Threat: MEDIUM",
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.SemiBold
                            )

                            CellThreatLevel.LOW -> Text(
                                text = "Cell Threat: LOW",
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
            if (hasMoreDevices) {
                val remaining = (filteredDeviceCount - pagedDevices.size).coerceAtLeast(0)
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
                                .coerceAtMost(filteredDeviceCount)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load $nextBatch More")
                    }
                    Text(
                        "Loaded ${pagedDevices.size} of ${filteredDeviceCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
    var collapseSweepEvents by rememberSaveable { mutableStateOf(true) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val currentLocation by if (showDistance) {
        LocationSnapshotProvider.observe(context).collectAsState(
            initial = LocationSnapshotProvider.read(context)
        )
    } else {
        remember { mutableStateOf<DetectionLocation?>(null) }
    }
    val encounters = remember(allEncounters) {
        if (allEncounters.size <= ENCOUNTERS_TAB_MAX_DATASET) {
            allEncounters
        } else {
            // Keep the newest rows only to avoid UI crashes on very large histories.
            allEncounters.take(ENCOUNTERS_TAB_MAX_DATASET)
        }
    }
    val sourceOptions = remember(encounters) {
        orderedEncounterSourceOptions(encounters.map { it.source.name }.toSet())
    }
    var filteredEncounterCount by remember { mutableStateOf(0) }
    var filteredSourceCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var pagedEncounters by remember { mutableStateOf<List<Encounter>>(emptyList()) }
    var hasMoreEncounters by remember { mutableStateOf(false) }
    var encounterDisplayComputing by remember { mutableStateOf(false) }
    var visibleEncounterCount by rememberSaveable { mutableStateOf(DETECTION_LIST_PAGE_SIZE) }
    LaunchedEffect(
        encounters,
        sourceFilter,
        queryFilter,
        showOwnedOnly,
        collapseSweepEvents,
        ownedDeviceKeys,
        showDistance,
        sortByDistance,
        currentLocation,
        visibleEncounterCount
    ) {
        encounterDisplayComputing = true
        val targetVisible = visibleEncounterCount.coerceAtLeast(DETECTION_LIST_PAGE_SIZE)
        val (filteredCount, sourceCounts, visibleRows) = withContext(Dispatchers.Default) {
            val sourceEncounters = if (collapseSweepEvents) {
                collapseSweepEncounters(encounters)
            } else {
                encounters
            }
            val filteredEncounters = sourceEncounters.asSequence().filter { encounter ->
                val sourceMatches = sourceFilter == null || encounter.source.name == sourceFilter
                val queryMatches = queryFilter.isBlank() ||
                    encounter.primaryId.contains(queryFilter, ignoreCase = true) ||
                    (encounter.secondaryId?.contains(queryFilter, ignoreCase = true) == true) ||
                    encounter.rawPayloadJson.contains(queryFilter, ignoreCase = true)
                val ownedMatches = !showOwnedOnly ||
                    (OwnedDeviceRegistry.keyFor(encounter.source.name, encounter.primaryId) in ownedDeviceKeys)
                sourceMatches && queryMatches && ownedMatches
            }

            if (!showDistance || !sortByDistance) {
                var count = 0
                val sourceCounts = mutableMapOf<String, Int>()
                val visible = ArrayList<Encounter>(targetVisible)
                filteredEncounters.forEach { encounter ->
                    count += 1
                    sourceCounts[encounter.source.name] = (sourceCounts[encounter.source.name] ?: 0) + 1
                    if (visible.size < targetVisible) {
                        visible += encounter
                    }
                }
                Triple(count, sourceCounts.toMap(), visible)
            } else {
                val ranked = filteredEncounters
                    .map { encounter -> encounter to (distanceForEncounterMeters(encounter, currentLocation) ?: Double.MIN_VALUE) }
                    .sortedWith(
                        compareByDescending<Pair<Encounter, Double>> { it.second }
                            .thenByDescending { it.first.timestampEpochMs }
                    )
                    .map { it.first }
                    .toList()
                val sourceCounts = ranked
                    .groupingBy { encounter -> encounter.source.name }
                    .eachCount()
                Triple(ranked.size, sourceCounts, ranked.take(targetVisible))
            }
        }
        filteredEncounterCount = filteredCount
        filteredSourceCounts = sourceCounts
        pagedEncounters = visibleRows
        hasMoreEncounters = filteredCount > visibleRows.size
        encounterDisplayComputing = false
    }
    val distanceByEncounterKey = remember(pagedEncounters, showDistance, currentLocation) {
        if (!showDistance || currentLocation == null) {
            emptyMap()
        } else {
            pagedEncounters.associate { encounter ->
                "${encounter.timestampEpochMs}|${encounter.source.name}|${encounter.primaryId}" to
                    distanceForEncounterMeters(encounter, currentLocation)
            }
        }
    }
    LaunchedEffect(pagedEncounters) {
        RuntimeUiListMemoryGauge.updateVisibleEncounterRows(pagedEncounters)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Encounters", style = MaterialTheme.typography.headlineMedium)
        Text("Tap any encounter for full telemetry.")
        if (allEncounters.size > encounters.size) {
            Text(
                "Showing newest ${encounters.size} encounters for stability (total stored: ${allEncounters.size}).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
                        CompactSwitchControl(
                            label = "Collapse Wi-Fi/BLE Sweep Events",
                            checked = collapseSweepEvents,
                            onCheckedChange = { collapseSweepEvents = it }
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
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(onClick = { }, label = { Text("Total $filteredEncounterCount") })
            filteredSourceCounts
                .toList()
                .sortedByDescending { it.second }
                .forEach { (source, count) ->
                    AssistChip(
                        onClick = { },
                        label = { Text("${formatSourceTypeLabel(source)} $count") }
                    )
                }
        }
        Text("Showing ${pagedEncounters.size} of ${filteredEncounterCount} (filtered ${filteredEncounterCount})")
        Text(
            "Filters and search run across the full dataset; Load More only controls how many rows render at once.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            pagedEncounters.forEach { encounter ->
                val connectionSecurity = remember(
                    encounter.source,
                    encounter.primaryId,
                    encounter.secondaryId,
                    encounter.rawPayloadJson
                ) {
                    analyzeConnectionSecurity(
                        source = encounter.source.name,
                        encounters = listOf(encounter)
                    )
                }
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
                                provenanceLabel
                                    .replace("•", "")
                                    .trimStart(' ', '-'),
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
                        if (connectionSecurity?.isInsecure == true) {
                            Text(
                                text = "Insecure connectivity risk: ${connectionSecurity.summary}. Avoid connecting.",
                                color = Color(0xFFB3261E),
                                fontWeight = FontWeight.Bold
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
            if (hasMoreEncounters) {
                val remaining = (filteredEncounterCount - pagedEncounters.size).coerceAtLeast(0)
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
                                .coerceAtMost(filteredEncounterCount)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load $nextBatch More")
                    }
                    Text(
                        "Loaded ${pagedEncounters.size} of ${filteredEncounterCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

private fun collapseSweepEncounters(encounters: List<Encounter>): List<Encounter> {
    if (encounters.isEmpty()) return emptyList()

    val collapsed = ArrayList<Encounter>(encounters.size)
    val sweepBestByKey = linkedMapOf<String, Encounter>()

    encounters.forEach { encounter ->
        val isSweepSource = encounter.source == EncounterSource.WIFI_SWEEP ||
            encounter.source == EncounterSource.BLUETOOTH_LE_SWEEP
        if (!isSweepSource) {
            collapsed += encounter
            return@forEach
        }

        // Collapse sweep bursts into one representative event per source/time bucket.
        val bucket = encounter.timestampEpochMs / 30_000L
        val key = "${encounter.source.name}|$bucket"
        val current = sweepBestByKey[key]
        if (current == null || encounter.timestampEpochMs > current.timestampEpochMs) {
            sweepBestByKey[key] = encounter
        }
    }

    if (sweepBestByKey.isNotEmpty()) {
        collapsed += sweepBestByKey.values
    }

    return collapsed
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
    val isCellSource = source == EncounterSource.CELL.name
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
    val connectionSecurity = analyzeConnectionSecurity(
        source = source,
        encounters = groupedEncounters
    )
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
        trackerRisk = trackerRisk,
        cellThreat = if (isCellSource) analyzeCellThreat(groupedEncounters) else null,
        connectionSecurity = connectionSecurity
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

private fun analyzeConnectionSecurity(
    source: String,
    encounters: List<Encounter>
): ConnectionSecuritySignal? {
    if (encounters.isEmpty()) return null

    val latest = encounters.maxByOrNull { it.timestampEpochMs } ?: return null

    return when (source) {
        EncounterSource.WIFI.name -> {
            val capabilities = encounters
                .asSequence()
                .mapNotNull { encounter ->
                    runCatching { JSONObject(encounter.rawPayloadJson) }
                        .getOrNull()
                        ?.optString("capabilities", "")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                }
                .toList()

            if (capabilities.isEmpty()) return null

            val openCount = capabilities.count { caps ->
                val normalized = caps.lowercase(Locale.US)
                !normalized.contains("wpa") &&
                    !normalized.contains("sae") &&
                    !normalized.contains("owe") &&
                    !normalized.contains("wep")
            }
            val wepCount = capabilities.count { caps ->
                caps.lowercase(Locale.US).contains("wep")
            }
            val legacyWeakCount = capabilities.count { caps ->
                val normalized = caps.lowercase(Locale.US)
                normalized.contains("tkip") ||
                    (normalized.contains("wpa") &&
                        !normalized.contains("wpa2") &&
                        !normalized.contains("wpa3") &&
                        !normalized.contains("sae") &&
                        !normalized.contains("owe"))
            }

            val indicators = mutableListOf<String>()
            if (openCount > 0) indicators += "Open authentication observed"
            if (wepCount > 0) indicators += "WEP authentication observed"
            if (legacyWeakCount > 0) indicators += "Legacy WPA/TKIP observed"
            if (indicators.isEmpty()) return null

            val riskSampleCount = listOf(openCount, wepCount, legacyWeakCount).maxOrNull() ?: 0
            val confidence = (riskSampleCount.toDouble() / capabilities.size.toDouble())
                .coerceIn(0.35, 0.98)

            val summary = when {
                wepCount > 0 -> "WEP or legacy encryption detected"
                openCount > 0 -> "Open Wi-Fi network (no WPA protections)"
                else -> "Weak Wi-Fi cipher settings detected"
            }

            ConnectionSecuritySignal(
                isInsecure = true,
                confidence = confidence,
                summary = summary,
                indicators = indicators
            )
        }

        EncounterSource.BLUETOOTH_CLASSIC.name -> {
            val payload = runCatching { JSONObject(latest.rawPayloadJson) }.getOrNull() ?: return null
            val bondState = payload.optInt("bondState", -1)
            val discoverySource = payload.optString("source", "")
            val classLabel = payload.optString("classLabel", "")

            val indicators = mutableListOf<String>()
            if (discoverySource.equals("inquiry", ignoreCase = true) && bondState != 12) {
                indicators += "Discovered via inquiry and not bonded"
            }
            if (classLabel.equals("networking", ignoreCase = true)) {
                indicators += "Networking-class Bluetooth device"
            }
            if (indicators.isEmpty()) return null

            ConnectionSecuritySignal(
                isInsecure = true,
                confidence = if (bondState != 12) 0.78 else 0.6,
                summary = "Untrusted Bluetooth Classic device profile",
                indicators = indicators
            )
        }

        EncounterSource.BLUETOOTH_LE.name -> {
            val payload = runCatching { JSONObject(latest.rawPayloadJson) }.getOrNull() ?: return null
            val connectable = payload.optBoolean("isConnectable", false)
            val trackerLikely = payload.optBoolean("trackerLikely", false)
            val name = payload.optString("name", "").trim()

            val indicators = mutableListOf<String>()
            if (connectable && name.isBlank()) {
                indicators += "Connectable BLE device without clear identity"
            }
            if (trackerLikely) {
                indicators += "Tracker-like BLE signature"
            }
            if (connectable && isLikelyRandomizedMacAddress(latest.primaryId)) {
                indicators += "Randomized BLE address"
            }
            if (indicators.isEmpty()) return null

            ConnectionSecuritySignal(
                isInsecure = true,
                confidence = when {
                    trackerLikely && connectable -> 0.86
                    trackerLikely -> 0.75
                    else -> 0.62
                },
                summary = "Potentially unsafe Bluetooth LE peripheral",
                indicators = indicators
            )
        }

        else -> null
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

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    val raw = opt(key) ?: return null
    return when (raw) {
        is Number -> raw.toInt()
        is String -> raw.trim().toIntOrNull()
        else -> null
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    val raw = opt(key) ?: return null
    return when (raw) {
        is Number -> raw.toLong()
        is String -> raw.trim().toLongOrNull()
        else -> null
    }
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
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
private fun DeviceDetailLoadingPage(onBack: () -> Unit) {
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        contentVisible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack) {
            Text("Back")
        }
        Text("Device Details", style = MaterialTheme.typography.headlineMedium)

        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { width -> width / 6 }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { width -> width / 8 })
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Loading device details...")
                }
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                repeat(3) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .height(14.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.90f)
                                    .height(12.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.72f)
                                    .height(12.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                            )
                        }
                    }
                }
            }
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

        val hasSourceSpecificDetails = !item.lastRawPayloadJson.isNullOrBlank() && deviceEncounter != null
        if (hasSourceSpecificDetails) {
            SourceSpecificDetailsSection(
                encounter = deviceEncounter,
                currentLocation = currentLocation,
                relatedEncounters = deviceEncounters
            )
        }

        if (hasSourceSpecificDetails) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            )
        }
        Text(
            text = "Argus Details",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

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
                if (item.cellThreat != null) {
                    DetailRow(
                        "Cell Threat",
                        when (item.cellThreat.level) {
                            CellThreatLevel.HIGH -> "HIGH"
                            CellThreatLevel.MEDIUM -> "MEDIUM"
                            CellThreatLevel.LOW -> "LOW"
                            CellThreatLevel.NONE -> "NONE"
                        }
                    )
                    DetailRow("Cell Threat Confidence", String.format(Locale.US, "%.0f%%", item.cellThreat.confidence * 100.0))
                    DetailRow("Cell Threat Score", String.format(Locale.US, "%.2f", item.cellThreat.score))
                    DetailRow("Cell Threat Samples", item.cellThreat.sampleCount.toString())
                    DetailRow("Cell Threat Summary", item.cellThreat.summary)
                    if (item.cellThreat.indicators.isNotEmpty()) {
                        DetailRow("Cell Threat Indicators", item.cellThreat.indicators.joinToString(" | "))
                    }
                }
                if (item.connectionSecurity != null) {
                    DetailRow(
                        "Connectivity Security",
                        if (item.connectionSecurity.isInsecure) {
                            "INSECURE - Avoid connecting"
                        } else {
                            "No immediate risk"
                        }
                    )
                    DetailRow(
                        "Connectivity Risk Confidence",
                        String.format(Locale.US, "%.0f%%", item.connectionSecurity.confidence * 100.0)
                    )
                    DetailRow("Connectivity Assessment", item.connectionSecurity.summary)
                    if (item.connectionSecurity.indicators.isNotEmpty()) {
                        DetailRow(
                            "Connectivity Indicators",
                            item.connectionSecurity.indicators.joinToString(" | ")
                        )
                    }
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
        val connectionSecurity = remember(
            encounter.source,
            encounter.primaryId,
            encounter.secondaryId,
            encounter.rawPayloadJson
        ) {
            analyzeConnectionSecurity(
                source = encounter.source.name,
                encounters = listOf(encounter)
            )
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
                if (connectionSecurity != null) {
                    DetailRow(
                        "Connectivity Security",
                        if (connectionSecurity.isInsecure) {
                            "INSECURE - Avoid connecting"
                        } else {
                            "No immediate risk"
                        }
                    )
                    DetailRow(
                        "Connectivity Risk Confidence",
                        String.format(Locale.US, "%.0f%%", connectionSecurity.confidence * 100.0)
                    )
                    DetailRow("Connectivity Assessment", connectionSecurity.summary)
                    if (connectionSecurity.indicators.isNotEmpty()) {
                        DetailRow("Connectivity Indicators", connectionSecurity.indicators.joinToString(" | "))
                    }
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
    val twoColumn = LocalConfiguration.current.screenWidthDp.dp >= DETAIL_TWO_COLUMN_MIN_WIDTH
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
    currentLocation: DetectionLocation?,
    relatedEncounters: List<Encounter> = emptyList()
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
        val threatInput = relatedEncounters
            .asSequence()
            .filter { it.source == EncounterSource.CELL }
            .toList()
            .ifEmpty { listOf(encounter) }
        analyzeCellThreat(threatInput)?.let { threat ->
            DetailRow(
                "Cell Threat",
                when (threat.level) {
                    CellThreatLevel.HIGH -> "HIGH"
                    CellThreatLevel.MEDIUM -> "MEDIUM"
                    CellThreatLevel.LOW -> "LOW"
                    CellThreatLevel.NONE -> "NONE"
                }
            )
            DetailRow("Cell Threat Confidence", String.format(Locale.US, "%.0f%%", threat.confidence * 100.0))
            DetailRow("Cell Threat Score", String.format(Locale.US, "%.2f", threat.score))
            DetailRow("Cell Threat Summary", threat.summary)
            if (threat.indicators.isNotEmpty()) {
                DetailRow("Threat Indicators", threat.indicators.joinToString(" | "))
            }
        }

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
    return runCatching {
        val instant = Instant.ofEpochMilli(epochMs)
        timeFormatter.format(instant.atZone(ZoneId.systemDefault()))
    }.getOrElse {
        "invalid-time($epochMs)"
    }
}

private fun formatAgeFromEpoch(epochMs: Long?, nowEpochMs: Long): String {
    val value = epochMs ?: return "n/a"
    val deltaMs = (nowEpochMs - value).coerceAtLeast(0L)
    val seconds = deltaMs / 1000L
    return when {
        seconds < 60L -> "${seconds}s ago"
        seconds < 3600L -> "${seconds / 60L}m ago"
        seconds < 86_400L -> "${seconds / 3600L}h ago"
        else -> "${seconds / 86_400L}d ago"
    }
}

@Composable
private fun ConnectingPulseDot(
    color: Color = Color(0xFFE65100)
) {
    val transition = rememberInfiniteTransition(label = "mesh-connecting-pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mesh-connecting-pulse-alpha"
    )
    Text(
        text = "●",
        color = color.copy(alpha = alpha),
        fontWeight = FontWeight.Bold
    )
}


