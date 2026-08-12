package dev.argus.tracker.sensing

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner

class BleScanner(
    private val context: Context
) : SignalScanner {
    override suspend fun scanOnce(): List<Encounter> {
        if (!hasBlePermissions()) return emptyList()
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return emptyList()
        val adapter: BluetoothAdapter = manager.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()

        // Active BLE scan is restricted and asynchronous; this is a placeholder record until callback
        // based scanning is added with a foreground service.
        return listOf(
            Encounter(
                timestampEpochMs = System.currentTimeMillis(),
                source = EncounterSource.BLUETOOTH_LE,
                primaryId = "ble-scan-active",
                secondaryId = null,
                rssiDbm = null,
                frequencyMhz = 2402,
                lat = null,
                lon = null,
                rawPayloadJson = "{\"note\":\"BLE adapter enabled\"}"
            )
        )
    }

    private fun hasBlePermissions(): Boolean {
        val scan = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
        return scan
    }
}
