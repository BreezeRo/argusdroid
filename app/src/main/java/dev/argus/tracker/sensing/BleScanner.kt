package dev.argus.tracker.sensing

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class BleScanner(
    private val context: Context
) : SignalScanner {
    override suspend fun scanOnce(): List<Encounter> {
        if (!hasBlePermissions()) return emptyList()
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return emptyList()
        val adapter: BluetoothAdapter = manager.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        val scanner = adapter.bluetoothLeScanner ?: return emptyList()
        val location = LocationSnapshotProvider.read(context)

        return suspendCancellableCoroutine { continuation ->
            val done = AtomicBoolean(false)
            val handler = Handler(Looper.getMainLooper())
            val captured = linkedMapOf<String, Encounter>()

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val encounter = result.toEncounter(location)
                    val existing = captured[encounter.primaryId]
                    if (existing == null || (encounter.rssiDbm ?: Int.MIN_VALUE) > (existing.rssiDbm ?: Int.MIN_VALUE)) {
                        captured[encounter.primaryId] = encounter
                    }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { onScanResult(0, it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    if (done.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(emptyList())
                    }
                }
            }

            val finishScan = {
                if (done.compareAndSet(false, true)) {
                    runCatching { scanner.stopScan(callback) }
                    if (continuation.isActive) continuation.resume(captured.values.toList())
                }
            }

            continuation.invokeOnCancellation {
                if (done.compareAndSet(false, true)) {
                    runCatching { scanner.stopScan(callback) }
                    handler.removeCallbacksAndMessages(null)
                }
            }

            runCatching {
                scanner.startScan(
                    null,
                    ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                    callback
                )
            }.onFailure {
                if (done.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(emptyList())
                }
                return@suspendCancellableCoroutine
            }

            handler.postDelayed({
                finishScan()
            }, 4_000L)
        }
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val btAdmin = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADMIN
            ) == PackageManager.PERMISSION_GRANTED
            fine && btAdmin
        }
    }

    private fun ScanResult.toEncounter(location: DetectionLocation?): Encounter {
        val mac = runCatching { device?.address }.getOrNull() ?: "unknown-ble"
        val name = runCatching {
            if (hasBluetoothConnectPermission()) device?.name else null
        }.getOrNull()
        return Encounter(
            timestampEpochMs = System.currentTimeMillis(),
            source = EncounterSource.BLUETOOTH_LE,
            primaryId = mac,
            secondaryId = name,
            rssiDbm = rssi,
            frequencyMhz = null,
            lat = location?.lat,
            lon = location?.lon,
            rawPayloadJson = buildBlePayload(this)
        )
    }

    private fun buildBlePayload(result: ScanResult): String {
        val address = runCatching { result.device?.address }.getOrNull()
        val name = runCatching {
            if (hasBluetoothConnectPermission()) result.device?.name else null
        }.getOrNull()
        val payload = JSONObject()
            .put("address", address)
            .put("name", name)
            .put("rssi", result.rssi)
            .put("txPower", result.txPower)
            .put("primaryPhy", result.primaryPhy)
            .put("secondaryPhy", result.secondaryPhy)
            .put("isLegacy", result.isLegacy)
            .put("isConnectable", result.isConnectable)
            .put("periodicAdvertisingInterval", result.periodicAdvertisingInterval)
            .put("timestampNanos", result.timestampNanos)

        val record = result.scanRecord
        payload.put("advertiseFlags", record?.advertiseFlags)
        payload.put("txPowerLevel", record?.txPowerLevel)
        payload.put("serviceUuids", record?.serviceUuids?.joinToString(separator = ",") { it.toString() })
        payload.put("manufacturerSpecificDataSize", record?.manufacturerSpecificData?.size() ?: 0)
        payload.put("serviceDataSize", record?.serviceData?.size ?: 0)
        payload.put("rawBytesHex", record?.bytes?.toHex())
        return payload.toString()
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        byte.toInt().and(0xFF).toString(16).padStart(2, '0')
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
