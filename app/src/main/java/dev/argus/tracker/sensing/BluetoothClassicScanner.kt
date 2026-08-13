package dev.argus.tracker.sensing

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import dev.argus.tracker.worker.ScanSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class BluetoothClassicScanner(
    private val context: Context
) : SignalScanner {

    override suspend fun scanOnce(): List<Encounter> {
        if (!ScanSettings.isBleSensorEnabled(context)) return emptyList()
        if (!hasPermissions()) return emptyList()

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return emptyList()
        val adapter = manager.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()

        val location = LocationSnapshotProvider.read(context)
        val bonded = adapter.bondedDevices.orEmpty().map {
            deviceToEncounter(device = it, location = location, discovered = false)
        }

        return suspendCancellableCoroutine { continuation ->
            val done = AtomicBoolean(false)
            val handler = Handler(Looper.getMainLooper())
            val captured = linkedMapOf<String, Encounter>()

            bonded.forEach { captured[it.primaryId] = it }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != BluetoothDevice.ACTION_FOUND) return
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    } ?: return
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    val key = device.address ?: return
                    val encounter = deviceToEncounter(device, location, discovered = true, discoveredRssi = rssi)
                    val existing = captured[key]
                    if (existing == null || (encounter.rssiDbm ?: Int.MIN_VALUE) > (existing.rssiDbm ?: Int.MIN_VALUE)) {
                        captured[key] = encounter
                    }
                }
            }

            val finish = {
                if (done.compareAndSet(false, true)) {
                    runCatching { context.unregisterReceiver(receiver) }
                    runCatching { adapter.cancelDiscovery() }
                    if (continuation.isActive) continuation.resume(captured.values.toList())
                }
            }

            continuation.invokeOnCancellation {
                if (done.compareAndSet(false, true)) {
                    runCatching { context.unregisterReceiver(receiver) }
                    runCatching { adapter.cancelDiscovery() }
                    handler.removeCallbacksAndMessages(null)
                }
            }

            val registered = runCatching {
                val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    context.registerReceiver(receiver, filter)
                }
            }.isSuccess

            if (!registered) {
                if (continuation.isActive) continuation.resume(captured.values.toList())
                return@suspendCancellableCoroutine
            }

            runCatching {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                adapter.startDiscovery()
            }.onFailure {
                finish()
                return@suspendCancellableCoroutine
            }

            handler.postDelayed({ finish() }, 5_000L)
        }
    }

    private fun deviceToEncounter(
        device: BluetoothDevice,
        location: DetectionLocation?,
        discovered: Boolean,
        discoveredRssi: Int? = null
    ): Encounter {
        val majorClass = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
        val deviceClass = runCatching { device.bluetoothClass?.deviceClass }.getOrNull()
        val codLabel = classifyClassicDevice(majorClass, deviceClass)
        val name = runCatching {
            if (hasConnectPermission()) device.name else null
        }.getOrNull()

        return Encounter(
            timestampEpochMs = System.currentTimeMillis(),
            source = EncounterSource.BLUETOOTH_CLASSIC,
            primaryId = device.address ?: "unknown-bt-classic",
            secondaryId = name,
            rssiDbm = discoveredRssi,
            frequencyMhz = null,
            lat = location?.lat,
            lon = location?.lon,
            rawPayloadJson = JSONObject()
                .put("address", device.address)
                .put("name", name)
                .put("bondState", device.bondState)
                .put("type", device.type)
                .put("majorClass", majorClass)
                .put("deviceClass", deviceClass)
                .put("classLabel", codLabel)
                .put("source", if (discovered) "inquiry" else "bonded")
                .toString()
        )
    }

    private fun classifyClassicDevice(majorClass: Int?, deviceClass: Int?): String {
        return when (majorClass) {
            BluetoothClass.Device.Major.PHONE -> "phone"
            BluetoothClass.Device.Major.COMPUTER -> "computer"
            BluetoothClass.Device.Major.AUDIO_VIDEO -> "audio-video"
            BluetoothClass.Device.Major.PERIPHERAL -> "peripheral"
            BluetoothClass.Device.Major.IMAGING -> "imaging"
            BluetoothClass.Device.Major.WEARABLE -> "wearable"
            BluetoothClass.Device.Major.NETWORKING -> "networking"
            BluetoothClass.Device.Major.HEALTH -> "health"
            BluetoothClass.Device.Major.TOY -> "toy"
            BluetoothClass.Device.Major.UNCATEGORIZED -> "uncategorized"
            else -> "unknown-${deviceClass ?: -1}"
        }
    }

    private fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val bt = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
            fine && bt
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
