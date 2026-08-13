package dev.argus.tracker.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.argus.tracker.ArgusApplication
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.sensing.SensorStatus
import dev.argus.tracker.sensing.SensorStatusProvider
import dev.argus.tracker.worker.WorkScheduler
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val SETTINGS_ROUTE = "settings"
private const val DEVICES_ROUTE = "devices"
private const val ENCOUNTERS_ROUTE = "encounters"
private const val DEVICE_DETAIL_ROUTE = "deviceDetail/{source}/{primaryId}"
private const val ENCOUNTER_DETAIL_ROUTE = "encounterDetail/{source}/{primaryId}/{timestamp}"

private val topLevelRoutes = setOf(SETTINGS_ROUTE, DEVICES_ROUTE, ENCOUNTERS_ROUTE)
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private data class DeviceItem(
    val source: String,
    val primaryId: String,
    val secondaryId: String?,
    val seenCount: Int,
    val lastSeenEpochMs: Long,
    val lastRssiDbm: Int?,
    val lastFrequencyMhz: Int?
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

    val recent by viewModel.recentEncounters.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val devices = remember(recent) {
        recent.groupBy { it.source.name to it.primaryId }
            .map { (key, encounters) ->
                val latest = encounters.maxByOrNull { it.timestampEpochMs } ?: encounters.first()
                DeviceItem(
                    source = key.first,
                    primaryId = key.second,
                    secondaryId = latest.secondaryId,
                    seenCount = encounters.size,
                    lastSeenEpochMs = latest.timestampEpochMs,
                    lastRssiDbm = latest.rssiDbm,
                    lastFrequencyMhz = latest.frequencyMhz
                )
            }
            .sortedByDescending { it.lastSeenEpochMs }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshSummary()
        trackingActive = WorkScheduler.isTrackingActive(context)
        sensorStatuses = SensorStatusProvider.read(context)
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: SETTINGS_ROUTE

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == SETTINGS_ROUTE,
                        onClick = { navController.navigate(SETTINGS_ROUTE) },
                        icon = { Text("S") },
                        label = { Text("Settings") }
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
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = SETTINGS_ROUTE,
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            composable(SETTINGS_ROUTE) {
                SettingsPage(
                    trackingActive = trackingActive,
                    sensorStatuses = sensorStatuses,
                    summary = summary,
                    onStart = {
                        WorkScheduler.start(context)
                        scope.launch {
                            trackingActive = WorkScheduler.isTrackingActive(context)
                            sensorStatuses = SensorStatusProvider.read(context)
                        }
                    },
                    onStop = {
                        WorkScheduler.stop(context)
                        viewModel.refreshSummary()
                        scope.launch {
                            trackingActive = WorkScheduler.isTrackingActive(context)
                            sensorStatuses = SensorStatusProvider.read(context)
                        }
                    },
                    onRefresh = {
                        viewModel.refreshSummary()
                        scope.launch {
                            trackingActive = WorkScheduler.isTrackingActive(context)
                            sensorStatuses = SensorStatusProvider.read(context)
                        }
                    }
                )
            }

            composable(DEVICES_ROUTE) {
                DevicesPage(
                    devices = devices,
                    onDeviceClick = { device ->
                        navController.navigate(
                            "deviceDetail/${Uri.encode(device.source)}/${Uri.encode(device.primaryId)}"
                        )
                    }
                )
            }

            composable(ENCOUNTERS_ROUTE) {
                EncountersPage(
                    encounters = recent,
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
                val item = devices.firstOrNull { it.source == source && it.primaryId == primaryId }
                DeviceDetailPage(item = item, onBack = { navController.popBackStack() })
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
private fun SettingsPage(
    trackingActive: Boolean,
    sensorStatuses: List<SensorStatus>,
    summary: List<SourceSummary>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Argus Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Tracking controls and source health.")
        Text(
            text = if (trackingActive) "Tracking Status: Running" else "Tracking Status: Stopped",
            fontWeight = FontWeight.Medium
        )

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

        Text("Sensors", fontWeight = FontWeight.Bold)
        sensorStatuses.forEach { sensor ->
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

        Text("Last 24h Summary", fontWeight = FontWeight.Bold)
        LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
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
}

@Composable
private fun DevicesPage(
    devices: List<DeviceItem>,
    onDeviceClick: (DeviceItem) -> Unit
) {
    var sourceFilter by remember { mutableStateOf<String?>(null) }
    var queryFilter by remember { mutableStateOf("") }
    val sourceOptions = remember(devices) { devices.map { it.source }.distinct().sorted() }
    val filteredDevices = remember(devices, sourceFilter, queryFilter) {
        devices.filter { device ->
            val sourceMatches = sourceFilter == null || device.source == sourceFilter
            val queryMatches = queryFilter.isBlank() ||
                device.primaryId.contains(queryFilter, ignoreCase = true) ||
                (device.secondaryId?.contains(queryFilter, ignoreCase = true) == true)
            sourceMatches && queryMatches
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Detected Devices", style = MaterialTheme.typography.headlineMedium)
        Text("Tap any device for detailed history.")
        SourceFilterDropdown(
            selectedSource = sourceFilter,
            sourceOptions = sourceOptions,
            onSourceSelected = { sourceFilter = it }
        )
        OutlinedTextField(
            value = queryFilter,
            onValueChange = { queryFilter = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search device ID or label") },
            singleLine = true
        )
        Text("Showing ${filteredDevices.size} of ${devices.size}")
        LazyColumn {
            items(filteredDevices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable { onDeviceClick(device) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${device.source} • ${device.primaryId}")
                        Text("Seen ${device.seenCount} times")
                        Text("Last seen ${formatEpoch(device.lastSeenEpochMs)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun EncountersPage(
    encounters: List<Encounter>,
    onEncounterClick: (Encounter) -> Unit
) {
    var sourceFilter by remember { mutableStateOf<String?>(null) }
    var queryFilter by remember { mutableStateOf("") }
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Encounters", style = MaterialTheme.typography.headlineMedium)
        Text("Tap any encounter for full telemetry.")
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
        Text("Showing ${filteredEncounters.size} of ${encounters.size}")
        LazyColumn {
            items(filteredEncounters.take(100)) { encounter ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable { onEncounterClick(encounter) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${encounter.source} • ${encounter.primaryId}")
                        Text("RSSI=${encounter.rssiDbm ?: "n/a"} dBm, Freq=${encounter.frequencyMhz ?: "n/a"} MHz")
                        Text(formatEpoch(encounter.timestampEpochMs))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceDetailPage(
    item: DeviceItem?,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onBack) {
            Text("Back")
        }
        Text("Device Details", style = MaterialTheme.typography.headlineMedium)
        if (item == null) {
            Text("Device not found in current encounter window.")
            return
        }
        DetailRow("Source", item.source)
        DetailRow("Primary ID", item.primaryId)
        DetailRow("Secondary ID", item.secondaryId ?: "n/a")
        DetailRow("Seen Count", item.seenCount.toString())
        DetailRow("Last Seen", formatEpoch(item.lastSeenEpochMs))
        DetailRow("Last RSSI", item.lastRssiDbm?.toString() ?: "n/a")
        DetailRow("Last Frequency", item.lastFrequencyMhz?.toString() ?: "n/a")
    }
}

@Composable
private fun EncounterDetailPage(
    encounter: Encounter?,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        DetailRow("Payload", encounter.rawPayloadJson)
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
