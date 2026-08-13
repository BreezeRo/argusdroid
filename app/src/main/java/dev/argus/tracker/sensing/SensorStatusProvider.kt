package dev.argus.tracker.sensing

import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.wifi.WifiManager
import android.provider.Settings
import android.telephony.TelephonyManager
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
            )
        )
    }
}
