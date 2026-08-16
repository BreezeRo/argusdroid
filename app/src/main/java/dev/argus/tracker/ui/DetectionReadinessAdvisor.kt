package dev.argus.tracker.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import dev.argus.tracker.domain.SourceCatalog
import dev.argus.tracker.worker.ScanSettings

data class DetectionReadinessItem(
    val id: String,
    val title: String,
    val recommendedValue: String,
    val currentValue: String,
    val isMissing: Boolean,
    val openSettingsLabel: String,
    val settingsIntent: Intent
)

object DetectionReadinessAdvisor {
    private const val WIFI_SCAN_ALWAYS_AVAILABLE_KEY = "wifi_scan_always_enabled"
    private const val BLE_SCAN_ALWAYS_AVAILABLE_KEY = "ble_scan_always_enabled"
    private const val ACTION_LOCATION_SCANNING_SETTINGS = "android.settings.LOCATION_SCANNING_SETTINGS"

    fun evaluate(context: Context): List<DetectionReadinessItem> {
        val appSettingsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )

        val locationServicesOn = runCatching {
            val mode = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )
            mode != Settings.Secure.LOCATION_MODE_OFF
        }.getOrDefault(false)

        val wifiScanningOn = runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                WIFI_SCAN_ALWAYS_AVAILABLE_KEY,
                0
            ) == 1
        }.getOrDefault(false)

        val bluetoothScanningOn = runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                BLE_SCAN_ALWAYS_AVAILABLE_KEY,
                0
            ) == 1
        }.getOrDefault(false)

        val wifiOn = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.isWifiEnabled == true

        val bluetoothOn = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter
            ?.isEnabled == true

        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasReadPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasBleScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasNearbyWifiDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasMicrophone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hasMagnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null

        val batteryOptimizationsIgnored = runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        }.getOrDefault(false)

        val nfcAdapter = runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()
        val nfcHardwareAvailable = nfcAdapter != null
        val nfcEnabled = nfcAdapter?.isEnabled == true
        val lastNfcObservationEpochMs = ScanSettings.getSourceLastRawObservationEpochMs(context, SourceCatalog.KEY_NFC)
        val nowEpochMs = System.currentTimeMillis()
        val nfcLastSeenCurrentValue = when {
            !nfcHardwareAvailable -> "Not supported"
            lastNfcObservationEpochMs > 0L -> "${formatAgeShort(nowEpochMs - lastNfcObservationEpochMs)} ago"
            else -> "No events observed yet"
        }
        val ingestDir = context.filesDir.resolve("ingest")
        val adsbFeedConfigured = ingestDir.resolve("adsb.jsonl").let { it.exists() && it.isFile && it.length() > 0L }
        val sdrFeedConfigured = ingestDir.resolve("sdr.jsonl").let { it.exists() && it.isFile && it.length() > 0L }
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork
        val networkCapabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val internetReady = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        return listOf(
            DetectionReadinessItem(
                id = "perm_fine_location",
                title = "Fine Location Permission",
                recommendedValue = "Granted",
                currentValue = if (hasFineLocation) "Granted" else "Missing",
                isMissing = !hasFineLocation,
                openSettingsLabel = "Open App Permissions",
                settingsIntent = appSettingsIntent
            ),
            DetectionReadinessItem(
                id = "perm_read_phone_state",
                title = "Phone State Permission",
                recommendedValue = "Granted",
                currentValue = if (hasReadPhoneState) "Granted" else "Missing",
                isMissing = !hasReadPhoneState,
                openSettingsLabel = "Open App Permissions",
                settingsIntent = appSettingsIntent
            ),
            DetectionReadinessItem(
                id = "perm_background_location",
                title = "Background Location Permission",
                recommendedValue = "Granted",
                currentValue = if (hasBackgroundLocation) "Granted" else "Missing",
                isMissing = !hasBackgroundLocation,
                openSettingsLabel = "Open App Permissions",
                settingsIntent = appSettingsIntent
            ),
            DetectionReadinessItem(
                id = "perm_ble_scan",
                title = "Bluetooth Scan Permission",
                recommendedValue = "Granted",
                currentValue = if (hasBleScan) "Granted" else "Missing",
                isMissing = !hasBleScan,
                openSettingsLabel = "Open App Permissions",
                settingsIntent = appSettingsIntent
            ),
            DetectionReadinessItem(
                id = "perm_nearby_wifi",
                title = "Nearby Wi-Fi Devices Permission",
                recommendedValue = "Granted",
                currentValue = if (hasNearbyWifiDevices) "Granted" else "Missing",
                isMissing = !hasNearbyWifiDevices,
                openSettingsLabel = "Open App Permissions",
                settingsIntent = appSettingsIntent
            ),
            DetectionReadinessItem(
                id = "perm_notifications",
                title = "Notifications Permission",
                recommendedValue = "Granted",
                currentValue = if (hasNotifications) "Granted" else "Missing",
                isMissing = !hasNotifications,
                openSettingsLabel = "Open App Notifications",
                settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            ),
            DetectionReadinessItem(
                id = "perm_microphone",
                title = "Microphone Permission",
                recommendedValue = "Granted",
                currentValue = if (hasMicrophone) "Granted" else "Missing",
                isMissing = !hasMicrophone,
                openSettingsLabel = "Open App Permissions",
                settingsIntent = appSettingsIntent
            ),
            DetectionReadinessItem(
                id = "setting_location_services",
                title = "Location Services",
                recommendedValue = "Enabled",
                currentValue = if (locationServicesOn) "Enabled" else "Disabled",
                isMissing = !locationServicesOn,
                openSettingsLabel = "Open Location Settings",
                settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            ),
            DetectionReadinessItem(
                id = "setting_wifi_scanning",
                title = "Wi-Fi Scanning",
                recommendedValue = "Enabled",
                currentValue = if (wifiScanningOn) "Enabled" else "Disabled",
                isMissing = !wifiScanningOn,
                openSettingsLabel = "Open Wi-Fi Scanning Settings",
                settingsIntent = Intent(ACTION_LOCATION_SCANNING_SETTINGS)
            ),
            DetectionReadinessItem(
                id = "setting_bluetooth_scanning",
                title = "Bluetooth Scanning",
                recommendedValue = "Enabled",
                currentValue = if (bluetoothScanningOn) "Enabled" else "Disabled",
                isMissing = !bluetoothScanningOn,
                openSettingsLabel = "Open Bluetooth Scanning Settings",
                settingsIntent = Intent(ACTION_LOCATION_SCANNING_SETTINGS)
            ),
            DetectionReadinessItem(
                id = "setting_wifi",
                title = "Wi-Fi",
                recommendedValue = "Enabled",
                currentValue = if (wifiOn) "Enabled" else "Disabled",
                isMissing = !wifiOn,
                openSettingsLabel = "Open Wi-Fi Settings",
                settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
            ),
            DetectionReadinessItem(
                id = "setting_bluetooth",
                title = "Bluetooth",
                recommendedValue = "Enabled",
                currentValue = if (bluetoothOn) "Enabled" else "Disabled",
                isMissing = !bluetoothOn,
                openSettingsLabel = "Open Bluetooth Settings",
                settingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            ),
            DetectionReadinessItem(
                id = "setting_battery_optimization",
                title = "Battery Optimization for Argus",
                recommendedValue = "Not optimized",
                currentValue = if (batteryOptimizationsIgnored) "Not optimized" else "Optimized",
                isMissing = !batteryOptimizationsIgnored,
                openSettingsLabel = "Open Battery Optimization Settings",
                settingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            ),
            DetectionReadinessItem(
                id = "sensor_magnetometer",
                title = "Magnetometer Sensor Availability",
                recommendedValue = "Available",
                currentValue = if (hasMagnetometer) "Available" else "Not available",
                isMissing = !hasMagnetometer,
                openSettingsLabel = "Open Device Settings",
                settingsIntent = Intent(Settings.ACTION_SETTINGS)
            ),
            DetectionReadinessItem(
                id = "source_nfc_hardware",
                title = "NFC Hardware Availability",
                recommendedValue = "Available",
                currentValue = if (nfcHardwareAvailable) "Available" else "Not available",
                isMissing = !nfcHardwareAvailable,
                openSettingsLabel = "Open System Settings",
                settingsIntent = Intent(Settings.ACTION_SETTINGS)
            ),
            DetectionReadinessItem(
                id = "setting_nfc_enabled",
                title = "NFC Radio",
                recommendedValue = "Enabled",
                currentValue = when {
                    !nfcHardwareAvailable -> "Not supported"
                    nfcEnabled -> "Enabled"
                    else -> "Disabled"
                },
                isMissing = nfcHardwareAvailable && !nfcEnabled,
                openSettingsLabel = "Open NFC Settings",
                settingsIntent = Intent(Settings.ACTION_NFC_SETTINGS)
            ),
            DetectionReadinessItem(
                id = "source_nfc_recent_event",
                title = "NFC Last Event Age",
                recommendedValue = "Seen recently",
                currentValue = nfcLastSeenCurrentValue,
                isMissing = nfcHardwareAvailable && nfcEnabled && lastNfcObservationEpochMs <= 0L,
                openSettingsLabel = "Open NFC Settings",
                settingsIntent = Intent(Settings.ACTION_NFC_SETTINGS)
            ),
            DetectionReadinessItem(
                id = "source_sdr",
                title = "SDR Ingest Feed",
                recommendedValue = "SDR ingest feed configured",
                currentValue = if (sdrFeedConfigured) "Configured" else "Not configured",
                isMissing = !sdrFeedConfigured,
                openSettingsLabel = "Open App Storage Settings",
                settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
            ),
            DetectionReadinessItem(
                id = "source_adsb",
                title = "ADS-B Ingest Feed",
                recommendedValue = "ADS-B ingest feed configured (adsb.jsonl)",
                currentValue = if (adsbFeedConfigured) "Configured" else "Not configured",
                isMissing = !adsbFeedConfigured,
                openSettingsLabel = "Open App Storage Settings",
                settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
            ),
            DetectionReadinessItem(
                id = "source_public_aviation",
                title = "Public Flight Radar Internet Access",
                recommendedValue = "Connected",
                currentValue = if (internetReady) "Connected" else "Offline",
                isMissing = !internetReady,
                openSettingsLabel = "Open Network Settings",
                settingsIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            )
        )
    }

    private fun formatAgeShort(ageMs: Long): String {
        val safeAgeMs = ageMs.coerceAtLeast(0L)
        val seconds = safeAgeMs / 1000L
        val days = seconds / 86_400L
        if (days > 0L) return "${days}d"
        val hours = seconds / 3_600L
        if (hours > 0L) return "${hours}h"
        val minutes = seconds / 60L
        if (minutes > 0L) return "${minutes}m"
        return "${seconds}s"
    }
}
