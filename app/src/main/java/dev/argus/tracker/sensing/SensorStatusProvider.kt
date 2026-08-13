package dev.argus.tracker.sensing

import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.wifi.WifiManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import dev.argus.tracker.worker.ScanSettings

data class SensorStatus(
    val name: String,
    val isOn: Boolean,
    val factoredByArgus: Boolean
)

object SensorStatusProvider {
    fun read(context: Context): List<SensorStatus> {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiOn = wifiManager?.isWifiEnabled == true

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bleOn = bluetoothManager?.adapter?.isEnabled == true

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val airplaneModeOn = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) == 1
        val cellularOn = (telephonyManager?.phoneType ?: TelephonyManager.PHONE_TYPE_NONE) !=
            TelephonyManager.PHONE_TYPE_NONE && !airplaneModeOn

        val audioPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val magneticAvailable = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
        val uwbHardwareAvailable = context.packageManager.hasSystemFeature("android.hardware.uwb")

        val ingestDir = context.filesDir.resolve("ingest")
        val uwbFeedConfigured = ingestDir.resolve("uwb.jsonl").let { it.exists() && it.isFile && it.length() > 0L }
        val sdrFeedConfigured = ingestDir.resolve("sdr.jsonl").let { it.exists() && it.isFile && it.length() > 0L }

        return listOf(
            SensorStatus(
                name = "Wi-Fi",
                isOn = wifiOn,
                factoredByArgus = ScanSettings.isWifiSensorEnabled(context)
            ),
            SensorStatus(
                name = "Bluetooth LE",
                isOn = bleOn,
                factoredByArgus = ScanSettings.isBleSensorEnabled(context)
            ),
            SensorStatus(
                name = "Remote ID",
                isOn = ScanSettings.isRemoteIdSensorEnabled(context),
                factoredByArgus = ScanSettings.isRemoteIdSensorEnabled(context)
            ),
            SensorStatus(
                name = "Cellular",
                isOn = cellularOn,
                factoredByArgus = ScanSettings.isCellularSensorEnabled(context)
            ),
            SensorStatus(
                name = "UWB",
                isOn = uwbHardwareAvailable || uwbFeedConfigured,
                factoredByArgus = true
            ),
            SensorStatus(
                name = "SDR",
                isOn = sdrFeedConfigured,
                factoredByArgus = true
            ),
            SensorStatus(
                name = "Acoustic (Direct)",
                isOn = audioPermissionGranted,
                factoredByArgus = ScanSettings.isForeignDirectAcousticEnabled(context)
            ),
            SensorStatus(
                name = "Magnetometer (Direct)",
                isOn = magneticAvailable,
                factoredByArgus = ScanSettings.isForeignDirectMagneticEnabled(context)
            )
        )
    }
}
