package dev.argus.tracker.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class TrackedRuntimePermissionState(
    val id: String,
    val title: String,
    val granted: Boolean
)

data class TrackedSpecialPermissionState(
    val id: String,
    val title: String,
    val available: Boolean
)

object AppPermissions {
    private data class RuntimePermissionSpec(
        val id: String,
        val title: String,
        val permission: String,
        val minSdk: Int = Build.VERSION_CODES.BASE,
        val maxSdk: Int = Int.MAX_VALUE,
        val startupRequested: Boolean
    )

    private data class SpecialPermissionSpec(
        val id: String,
        val title: String,
        val permission: String,
        val minSdk: Int = Build.VERSION_CODES.BASE,
        val maxSdk: Int = Int.MAX_VALUE
    )

    private val runtimePermissionSpecs = listOf(
        RuntimePermissionSpec(
            id = "perm_fine_location",
            title = "Fine Location Permission",
            permission = Manifest.permission.ACCESS_FINE_LOCATION,
            startupRequested = true
        ),
        RuntimePermissionSpec(
            id = "perm_coarse_location",
            title = "Coarse Location Permission",
            permission = Manifest.permission.ACCESS_COARSE_LOCATION,
            startupRequested = false
        ),
        RuntimePermissionSpec(
            id = "perm_background_location",
            title = "Background Location Permission",
            permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            minSdk = Build.VERSION_CODES.Q,
            startupRequested = false
        ),
        RuntimePermissionSpec(
            id = "perm_read_phone_state",
            title = "Phone State Permission",
            permission = Manifest.permission.READ_PHONE_STATE,
            startupRequested = true
        ),
        RuntimePermissionSpec(
            id = "perm_microphone",
            title = "Microphone Permission",
            permission = Manifest.permission.RECORD_AUDIO,
            startupRequested = true
        ),
        RuntimePermissionSpec(
            id = "perm_ble_scan",
            title = "Bluetooth Scan Permission",
            permission = Manifest.permission.BLUETOOTH_SCAN,
            minSdk = Build.VERSION_CODES.S,
            startupRequested = true
        ),
        RuntimePermissionSpec(
            id = "perm_ble_connect",
            title = "Bluetooth Connect Permission",
            permission = Manifest.permission.BLUETOOTH_CONNECT,
            minSdk = Build.VERSION_CODES.S,
            startupRequested = true
        ),
        RuntimePermissionSpec(
            id = "perm_nearby_wifi",
            title = "Nearby Wi-Fi Devices Permission",
            permission = Manifest.permission.NEARBY_WIFI_DEVICES,
            minSdk = Build.VERSION_CODES.TIRAMISU,
            startupRequested = true
        ),
        RuntimePermissionSpec(
            id = "perm_notifications",
            title = "Notifications Permission",
            permission = Manifest.permission.POST_NOTIFICATIONS,
            minSdk = Build.VERSION_CODES.TIRAMISU,
            startupRequested = true
        )
    )

    private val specialPermissionSpecs = listOf(
        SpecialPermissionSpec(
            id = "perm_special_internet",
            title = "Internet Permission",
            permission = Manifest.permission.INTERNET
        ),
        SpecialPermissionSpec(
            id = "perm_special_wifi_state",
            title = "Wi-Fi State Permission",
            permission = Manifest.permission.ACCESS_WIFI_STATE
        ),
        SpecialPermissionSpec(
            id = "perm_special_change_wifi_state",
            title = "Change Wi-Fi State Permission",
            permission = Manifest.permission.CHANGE_WIFI_STATE
        ),
        SpecialPermissionSpec(
            id = "perm_special_bluetooth_legacy",
            title = "Bluetooth Permission (Legacy)",
            permission = Manifest.permission.BLUETOOTH,
            maxSdk = Build.VERSION_CODES.R
        ),
        SpecialPermissionSpec(
            id = "perm_special_bluetooth_admin_legacy",
            title = "Bluetooth Admin Permission (Legacy)",
            permission = Manifest.permission.BLUETOOTH_ADMIN,
            maxSdk = Build.VERSION_CODES.R
        ),
        SpecialPermissionSpec(
            id = "perm_special_foreground_service",
            title = "Foreground Service Permission",
            permission = Manifest.permission.FOREGROUND_SERVICE,
            minSdk = Build.VERSION_CODES.P
        ),
        SpecialPermissionSpec(
            id = "perm_special_foreground_service_data_sync",
            title = "Foreground Service Data Sync Permission",
            permission = Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC,
            minSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ),
        SpecialPermissionSpec(
            id = "perm_special_foreground_service_microphone",
            title = "Foreground Service Microphone Permission",
            permission = Manifest.permission.FOREGROUND_SERVICE_MICROPHONE,
            minSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ),
        SpecialPermissionSpec(
            id = "perm_special_nfc",
            title = "NFC Permission",
            permission = Manifest.permission.NFC
        ),
        SpecialPermissionSpec(
            id = "perm_special_biometric",
            title = "Use Biometric Permission",
            permission = Manifest.permission.USE_BIOMETRIC,
            minSdk = Build.VERSION_CODES.P
        ),
        SpecialPermissionSpec(
            id = "perm_special_high_sampling_rate_sensors",
            title = "High Sampling Rate Sensors Permission",
            permission = Manifest.permission.HIGH_SAMPLING_RATE_SENSORS,
            minSdk = Build.VERSION_CODES.S
        )
    )

    fun startupRuntimePermissions(): List<String> {
        return runtimePermissionSpecs
            .asSequence()
            .filter { it.startupRequested }
            .filter { Build.VERSION.SDK_INT in it.minSdk..it.maxSdk }
            .map { it.permission }
            .distinct()
            .toList()
    }

    fun missingStartupRuntimePermissions(context: Context): List<String> {
        return startupRuntimePermissions().filterNot { permission ->
            isPermissionGranted(context, permission)
        }
    }

    fun trackedRuntimePermissionStates(context: Context): List<TrackedRuntimePermissionState> {
        return runtimePermissionSpecs
            .filter { Build.VERSION.SDK_INT in it.minSdk..it.maxSdk }
            .map { spec ->
                TrackedRuntimePermissionState(
                    id = spec.id,
                    title = spec.title,
                    granted = isPermissionGranted(context, spec.permission)
                )
            }
    }

    fun trackedSpecialPermissionStates(context: Context): List<TrackedSpecialPermissionState> {
        val manifestStates = specialPermissionSpecs
            .filter { Build.VERSION.SDK_INT in it.minSdk..it.maxSdk }
            .map { spec ->
                TrackedSpecialPermissionState(
                    id = spec.id,
                    title = spec.title,
                    available = isPermissionGranted(context, spec.permission)
                )
            }

        val remoteIdContract = TrackedSpecialPermissionState(
            id = "perm_special_remote_id_ingest_contract",
            title = "Remote ID Ingest Permission Contract",
            available = isPermissionDeclared(
                context = context,
                permission = "dev.argus.tracker.permission.INGEST_REMOTE_ID"
            )
        )

        return manifestStates + remoteIdContract
    }

    fun hasAnyLocationPermission(context: Context): Boolean {
        return hasFineLocationPermission(context) || hasCoarseLocationPermission(context)
    }

    fun hasFineLocationPermission(context: Context): Boolean {
        return isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun hasCoarseLocationPermission(context: Context): Boolean {
        return isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
    }

    fun hasCellularScanPermissions(context: Context): Boolean {
        return hasFineLocationPermission(context) &&
            isPermissionGranted(context, Manifest.permission.READ_PHONE_STATE)
    }

    fun hasWifiDirectPermissions(context: Context): Boolean {
        val nearbyWifiGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            true
        }
        return hasFineLocationPermission(context) && nearbyWifiGranted
    }

    fun hasWifiScanPermissions(context: Context): Boolean {
        return hasWifiDirectPermissions(context) &&
            isPermissionGranted(context, Manifest.permission.ACCESS_WIFI_STATE)
    }

    fun hasBleScanPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isPermissionGranted(context, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            hasFineLocationPermission(context) &&
                isPermissionGranted(context, Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    fun hasBluetoothClassicScanPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isPermissionGranted(context, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            hasFineLocationPermission(context) &&
                isPermissionGranted(context, Manifest.permission.BLUETOOTH)
        }
    }

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isPermissionGranted(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }
    }

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isPermissionDeclared(context: Context, permission: String): Boolean {
        return runCatching {
            context.packageManager.getPermissionInfo(permission, 0)
            true
        }.getOrDefault(false)
    }
}