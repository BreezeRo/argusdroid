package dev.argus.tracker.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.net.Uri
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.foundation.clickable
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
import com.google.maps.android.compose.rememberCameraPositionState
import dev.argus.tracker.ArgusApplication
import dev.argus.tracker.domain.Encounter
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
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
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
private const val DEVICES_ROUTE = "devices"
private const val ENCOUNTERS_ROUTE = "encounters"
private const val DEVICE_DETAIL_ROUTE = "deviceDetail/{source}/{primaryId}"
private const val ENCOUNTER_DETAIL_ROUTE = "encounterDetail/{source}/{primaryId}/{timestamp}"

private val topLevelRoutes = setOf(HOME_ROUTE, DETECTION_ROUTE, DEVICES_ROUTE, ENCOUNTERS_ROUTE, SETTINGS_ROUTE)
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private const val LIVE_SCAN_INTERVAL_MS = 5000L
private const val APPROACH_ALERT_CHANNEL_ID = "argus_approach_alerts"
private const val APPROACH_ALERT_COOLDOWN_MS = 2 * 60 * 1000L
private const val TRACKER_ALERT_CHANNEL_ID = "argus_tracker_alerts"
private const val TRACKER_ALERT_COOLDOWN_MS = 5 * 60 * 1000L

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
    val isApproaching: Boolean,
    val approachConfidence: Double?,
    val approachDeltaMeters: Double?,
    val isOwned: Boolean,
    val trackerRisk: TrackerRiskSignal?
)

private data class ApproachSignal(
    val isApproaching: Boolean,
    val confidence: Double,
    val deltaMeters: Double
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
    val approachSignal: ApproachSignal? = null,
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

private fun readSensorGateSettings(context: android.content.Context): SensorGateSettings =
    SensorGateSettings(
        wifiEnabled = ScanSettings.isWifiSensorEnabled(context),
        bluetoothEnabled = ScanSettings.isBleSensorEnabled(context),
        cellularEnabled = ScanSettings.isCellularSensorEnabled(context),
        remoteIdEnabled = ScanSettings.isRemoteIdSensorEnabled(context)
    )

@Composable
fun ArgusApp() {
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
    var sensorGateSettings by remember { mutableStateOf(readSensorGateSettings(context)) }
    var approachDetectionEnabled by remember { mutableStateOf(ScanSettings.isApproachDetectionEnabled(context)) }
    var approachNotificationsEnabled by remember { mutableStateOf(ScanSettings.isApproachNotificationsEnabled(context)) }
    var trackerNotificationsEnabled by remember { mutableStateOf(ScanSettings.isTrackerNotificationsEnabled(context)) }
    var ownedDeviceKeys by remember { mutableStateOf(OwnedDeviceRegistry.read(context)) }
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
    val lastScanEpochMs = remember(recent) { recent.maxOfOrNull { it.timestampEpochMs } }

    LaunchedEffect(Unit) {
        viewModel.refreshSummary()
        trackingActive = WorkScheduler.isTrackingActive(context)
        sensorGateSettings = readSensorGateSettings(context)
        sensorStatuses = SensorStatusProvider.read(context)
        readinessItems = DetectionReadinessAdvisor.evaluate(context)
    }

    LaunchedEffect(context) {
        while (true) {
            trackingActive = WorkScheduler.isTrackingActive(context)
            delay(1000)
        }
    }

    LaunchedEffect(allEncounters, ownedDeviceKeys, approachDetectionEnabled, approachNotificationsEnabled) {
        if (!approachDetectionEnabled || !approachNotificationsEnabled) return@LaunchedEffect
        if (!hasPostNotificationsPermission(context)) return@LaunchedEffect

        ensureApproachNotificationChannel(context)
        val now = System.currentTimeMillis()
        val devices = buildDeviceItems(
            encounters = allEncounters,
            approachDetectionEnabled = true,
            ownedDeviceKeys = ownedDeviceKeys
        )
        val seenKeys = mutableSetOf<String>()

        devices.forEach { device ->
            val key = "${device.source}|${device.primaryId}"
            seenKeys += key
            val wasApproaching = approachStateByDevice[key] ?: false
            val isApproaching = device.isApproaching

            if (isApproaching && !wasApproaching) {
                val lastNotified = lastApproachNotificationEpochByDevice[key] ?: 0L
                if (now - lastNotified >= APPROACH_ALERT_COOLDOWN_MS) {
                    sendApproachNotification(context, device)
                    lastApproachNotificationEpochByDevice[key] = now
                }
            }

            approachStateByDevice[key] = isApproaching
        }

        val staleKeys = approachStateByDevice.keys.filter { it !in seenKeys }
        staleKeys.forEach { staleKey ->
            approachStateByDevice.remove(staleKey)
            lastApproachNotificationEpochByDevice.remove(staleKey)
        }
    }

    LaunchedEffect(allEncounters, ownedDeviceKeys, approachDetectionEnabled, trackerNotificationsEnabled) {
        if (!approachDetectionEnabled || !trackerNotificationsEnabled) return@LaunchedEffect
        if (!hasPostNotificationsPermission(context)) return@LaunchedEffect

        ensureTrackerNotificationChannel(context)
        val now = System.currentTimeMillis()
        val devices = buildDeviceItems(
            encounters = allEncounters,
            approachDetectionEnabled = approachDetectionEnabled,
            ownedDeviceKeys = ownedDeviceKeys
        )
        val seenKeys = mutableSetOf<String>()

        devices.forEach { device ->
            val key = "${device.source}|${device.primaryId}"
            seenKeys += key
            val currentRisk = device.trackerRisk?.level ?: TrackerRiskLevel.NONE
            val previousRisk = trackerStateByDevice[key] ?: TrackerRiskLevel.NONE

            if (currentRisk == TrackerRiskLevel.HIGH && previousRisk != TrackerRiskLevel.HIGH && !device.isOwned) {
                val lastNotified = lastTrackerNotificationEpochByDevice[key] ?: 0L
                if (now - lastNotified >= TRACKER_ALERT_COOLDOWN_MS) {
                    sendTrackerRiskNotification(context, device)
                    lastTrackerNotificationEpochByDevice[key] = now
                }
            }

            trackerStateByDevice[key] = currentRisk
        }

        val staleKeys = trackerStateByDevice.keys.filter { it !in seenKeys }
        staleKeys.forEach { staleKey ->
            trackerStateByDevice.remove(staleKey)
            lastTrackerNotificationEpochByDevice.remove(staleKey)
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
                        selected = currentRoute == DEVICES_ROUTE,
                        onClick = { navController.navigate(DEVICES_ROUTE) },
                        icon = { Text("D") },
                        label = { Text("Devices") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == ENCOUNTERS_ROUTE,
                        onClick = { navController.navigate(ENCOUNTERS_ROUTE) },
                        icon = { Text("E") },
                        label = { Text("Encounters") }
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
                    approachDetectionEnabled = approachDetectionEnabled,
                    approachNotificationsEnabled = approachNotificationsEnabled,
                    trackerNotificationsEnabled = trackerNotificationsEnabled,
                    onScanIntervalSelected = { seconds ->
                        scope.launch {
                            scanIntervalSeconds = seconds
                            ScanSettings.setScanIntervalSeconds(context, seconds)
                            if (trackingActive) {
                                WorkScheduler.stop(context)
                                val restartResult = WorkScheduler.startAndVerify(context)
                                trackingStartMessage = if (restartResult.success) {
                                    "Scan interval updated to ${ScanSettings.formatInterval(seconds)} and tracking restarted."
                                } else {
                                    "Scan interval saved, but restart failed: ${restartResult.message}"
                                }
                                trackingStartMessageIsError = !restartResult.success
                                trackingActive = WorkScheduler.isTrackingActive(context)
                            } else {
                                trackingStartMessage = "Scan interval updated to ${ScanSettings.formatInterval(seconds)}."
                                trackingStartMessageIsError = false
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
                    }
                )
            }

            composable(DETECTION_ROUTE) {
                DetectionPage(
                    readinessItems = readinessItems,
                    encounters = recent,
                    approachDetectionEnabled = approachDetectionEnabled,
                    ownedDeviceKeys = ownedDeviceKeys,
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
                    onRefresh = {
                        readinessItems = DetectionReadinessAdvisor.evaluate(context)
                    },
                    onLiveCollect = {
                        runCatching {
                            val batch = app.container.sensingService.collectBatch()
                            app.container.repository.insertBatch(batch)
                            viewModel.refreshSummary()
                            readinessItems = DetectionReadinessAdvisor.evaluate(context)
                            if (batch.isEmpty()) {
                                "Live scan completed: no detections this cycle."
                            } else {
                                "Live scan added ${batch.size} detections."
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
                    }
                )
            }

            composable(DEVICES_ROUTE) {
                DevicesPage(
                    recentEncounters = recent100,
                    allEncounters = allEncounters,
                    approachDetectionEnabled = approachDetectionEnabled,
                    ownedDeviceKeys = ownedDeviceKeys,
                    onDeviceClick = { device ->
                        navController.navigate(
                            "deviceDetail/${Uri.encode(device.source)}/${Uri.encode(device.primaryId)}"
                        )
                    }
                )
            }

            composable(ENCOUNTERS_ROUTE) {
                EncountersPage(
                    recentEncounters = recent100,
                    allEncounters = allEncounters,
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
        }
    }
}

@Composable
private fun HomePage(
    trackingActive: Boolean,
    lastScanEpochMs: Long?,
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
                },
                fontWeight = FontWeight.Medium
            )
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
    approachDetectionEnabled: Boolean,
    approachNotificationsEnabled: Boolean,
    trackerNotificationsEnabled: Boolean,
    onScanIntervalSelected: (Long) -> Unit,
    onApproachDetectionChanged: (Boolean) -> Unit,
    onApproachNotificationsChanged: (Boolean) -> Unit,
    onTrackerNotificationsChanged: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
private fun DetectionPage(
    readinessItems: List<DetectionReadinessItem>,
    encounters: List<Encounter>,
    approachDetectionEnabled: Boolean,
    ownedDeviceKeys: Set<String>,
    onEncounterMapPinClick: (source: String, primaryId: String, timestampEpochMs: Long) -> Unit,
    onDeviceMapPinClick: (source: String, primaryId: String) -> Unit,
    onRefresh: () -> Unit,
    onLiveCollect: suspend () -> String,
    onOpenReadinessSetting: (DetectionReadinessItem) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var encounterPinLimit by rememberSaveable { mutableStateOf(1000) }
    var cellDevicePinLimit by rememberSaveable { mutableStateOf(1000) }
    val tabs = listOf("Readiness", "Device Encounters Map", "Device Location Map")

    val encounterPins = remember(encounters) {
        encounters
            .asSequence()
            .mapNotNull { encounter ->
                val lat = encounter.lat
                val lon = encounter.lon
                if (!isValidLatLon(lat, lon)) {
                    null
                } else {
                    MapPin(
                        position = LatLng(lat!!, lon!!),
                        title = "${encounter.source} • ${encounter.primaryId}",
                        snippet = formatEpoch(encounter.timestampEpochMs),
                        timestampEpochMs = encounter.timestampEpochMs,
                        source = encounter.source.name,
                        primaryId = encounter.primaryId,
                        encounterTimestampEpochMs = encounter.timestampEpochMs
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
                DeviceLocationCandidate(
                    source = latest.source.name,
                    primaryId = latest.primaryId,
                    secondaryId = latest.secondaryId,
                    latestTimestampEpochMs = latest.timestampEpochMs,
                    seenCount = deviceEncounters.size,
                    encounters = deviceEncounters,
                    isOwned = owned,
                    approachSignal = approachSignal,
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
                                approximateMethod = "Inferred from range + movement",
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
            val methodSnippet = candidate.approximateMethod
                ?.let { "$it" }
                ?: "Approx location"
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
            val trackerSnippet = when (candidate.trackerRisk?.level) {
                TrackerRiskLevel.HIGH -> " • Tracker Risk HIGH"
                TrackerRiskLevel.MEDIUM -> " • Tracker Risk MEDIUM"
                TrackerRiskLevel.LOW -> " • Tracker Risk LOW"
                else -> ""
            }

            MapPin(
                position = LatLng(location.lat, location.lon),
                title = "${listSourceLabel(candidate.source, candidate.secondaryId)} • ${candidate.primaryId}",
                snippet = "$methodSnippet • Seen ${candidate.seenCount} times • Last ${formatEpoch(candidate.latestTimestampEpochMs)}$rangeSnippet$approachSnippet$ownershipSnippet$trackerSnippet",
                timestampEpochMs = candidate.latestTimestampEpochMs,
                source = candidate.source,
                primaryId = candidate.primaryId,
                encounterTimestampEpochMs = null
            )
        }

        estimatedDeviceLocationPins = resolvedPins
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
                onLiveCollect = onLiveCollect
            )
        } else {
            DetectionMapPage(
                mapTitle = "Device Location Map",
                mapDescription = "Pins show estimated cell tower locations and inferred Wi-Fi/BLE device locations.",
                pins = estimatedDeviceLocationPins,
                pinLimit = cellDevicePinLimit,
                onPinLimitChange = { cellDevicePinLimit = it },
                onPinDetailsClick = { pin ->
                    onDeviceMapPinClick(pin.source, pin.primaryId)
                },
                onLiveCollect = onLiveCollect
            )
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
    onLiveCollect: suspend () -> String
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
    val visiblePins = remember(pins, pinLimit) { pins.take(pinLimit) }
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

    LaunchedEffect(liveModeEnabled) {
        if (!liveModeEnabled) {
            liveCollectInProgress = false
            liveStatusMessage = "Live mode is off."
            return@LaunchedEffect
        }

        while (liveModeEnabled) {
            liveCollectInProgress = true
            liveStatusMessage = onLiveCollect()
            liveCollectInProgress = false
            delay(LIVE_SCAN_INTERVAL_MS)
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
                                    checked = liveModeEnabled,
                                    onCheckedChange = { liveModeEnabled = it }
                                )
                            }
                            Text("Foreground scan every 5s while open.")
                            Text(
                                text = if (liveCollectInProgress) "Live scan running..." else liveStatusMessage,
                                color = if (liveStatusMessage.startsWith("Live scan failed")) Color(0xFFB3261E) else Color.Unspecified
                            )
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
                            icon = BitmapDescriptorFactory.defaultMarker(markerHueForSource(pin.source)),
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

private data class MapPin(
    val position: LatLng,
    val title: String,
    val snippet: String,
    val timestampEpochMs: Long,
    val source: String,
    val primaryId: String,
    val encounterTimestampEpochMs: Long?
)

private data class PinLegendItem(
    val label: String,
    val color: Color
)

private fun markerHueForSource(source: String): Float = when (source) {
    "CELL" -> BitmapDescriptorFactory.HUE_AZURE
    "WIFI" -> BitmapDescriptorFactory.HUE_ORANGE
    "BLUETOOTH_LE" -> BitmapDescriptorFactory.HUE_GREEN
    "REMOTE_ID" -> BitmapDescriptorFactory.HUE_VIOLET
    "UNKNOWN_RF" -> BitmapDescriptorFactory.HUE_ROSE
    else -> BitmapDescriptorFactory.HUE_RED
}

private fun markerLegendColorForSource(source: String): Color = when (source) {
    "CELL" -> Color(0xFF1E88E5)
    "WIFI" -> Color(0xFFFB8C00)
    "BLUETOOTH_LE" -> Color(0xFF43A047)
    "REMOTE_ID" -> Color(0xFF8E24AA)
    "UNKNOWN_RF" -> Color(0xFFE91E63)
    else -> Color(0xFFD32F2F)
}

private fun markerLegendLabelForSource(source: String): String = when (source) {
    "CELL" -> "CELL TOWER"
    "WIFI" -> "WIFI"
    "BLUETOOTH_LE" -> "BLUETOOTH LE"
    "REMOTE_ID" -> "REMOTE ID"
    "UNKNOWN_RF" -> "UNKNOWN RF"
    else -> source
}

private fun legendItemsForPins(pins: List<MapPin>): List<PinLegendItem> {
    val preferredOrder = listOf("CELL", "WIFI", "BLUETOOTH_LE", "REMOTE_ID", "UNKNOWN_RF")
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
    source == "WIFI" || source == "BLUETOOTH_LE"

private fun secondaryIdLabel(source: String): String = when (source) {
    "WIFI" -> "SSID"
    "BLUETOOTH_LE" -> "Device Name"
    else -> "Secondary ID"
}

private fun listSourceLabel(source: String, secondaryId: String?): String {
    if (source == "CELL") {
        if (!secondaryId.isNullOrBlank()) {
            return "CELL TOWER (${secondaryId})"
        }
        return "CELL TOWER"
    }
    return source
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
        EncounterSource.BLUETOOTH_LE -> "Bluetooth LE Device Details" to readBleDeviceFields(encounter.rawPayloadJson)
        EncounterSource.CELL -> "Cell Tower Details" to readCellTowerFields(encounter.rawPayloadJson)
        EncounterSource.REMOTE_ID -> "Remote ID Details" to readGenericPayloadFields(encounter.rawPayloadJson)
        EncounterSource.UNKNOWN_RF -> "Unknown RF Details" to readGenericPayloadFields(encounter.rawPayloadJson)
    }

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

    val ordered = encounters.sortedBy { it.timestampEpochMs }
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
    val notification = NotificationCompat.Builder(context, APPROACH_ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_more)
        .setContentTitle(title)
        .setContentText(content)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    val notificationId = ("${device.source}|${device.primaryId}").hashCode()
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
                isApproaching = approachSignal?.isApproaching == true,
                approachConfidence = approachSignal?.confidence,
                approachDeltaMeters = approachSignal?.deltaMeters,
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
