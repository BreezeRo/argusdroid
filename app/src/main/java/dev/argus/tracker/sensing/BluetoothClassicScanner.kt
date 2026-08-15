package dev.argus.tracker.sensing

import android.Manifest
import android.bluetooth.BluetoothAdapter
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
import dev.argus.tracker.data.OperationalErrorLogStore
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

    companion object {
        private const val DISCOVERY_TIMEOUT_MS = 16_000L
        private const val EMPTY_SCAN_LOG_INTERVAL_MS = 2 * 60_000L
        private const val EMPTY_SCAN_LOG_CONSECUTIVE_THRESHOLD = 3
    }

    private var lastSkipLogEpochMs: Long = 0L
    private var lastEmptyScanLogEpochMs: Long = 0L
    private var consecutiveEmptyScans: Int = 0

    override suspend fun scanOnce(): List<Encounter> {
        if (!ScanSettings.isBluetoothClassicSensorEnabled(context)) {
            logSkipped("Bluetooth Classic sensor disabled in settings")
            return emptyList()
        }
        if (!hasScanPermission()) {
            logSkipped("Missing Bluetooth scan permission")
            return emptyList()
        }

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return emptyList()
        val adapter = manager.adapter ?: return emptyList()
        if (!adapter.isEnabled) {
            logSkipped("Bluetooth adapter disabled")
            return emptyList()
        }

        val location = LocationSnapshotProvider.read(context)
        val bonded = if (hasConnectPermission()) {
            runCatching {
                adapter.bondedDevices.orEmpty().map {
                    val key = resolveDeviceKey(it)
                    deviceToEncounter(
                        device = it,
                        primaryId = key,
                        location = location,
                        discovered = false
                    )
                }
            }.onFailure { error ->
                OperationalErrorLogStore.append(
                    context = context,
                    category = "SCAN_SOURCE",
                    source = "bt_classic",
                    message = "Unable to read bonded devices: ${error.message ?: "unknown error"}"
                )
            }.getOrDefault(emptyList())
        } else {
            logSkipped("BLUETOOTH_CONNECT not granted; bonded devices unavailable")
            emptyList()
        }

        return suspendCancellableCoroutine { continuation ->
            val done = AtomicBoolean(false)
            val handler = Handler(Looper.getMainLooper())
            val captured = linkedMapOf<String, Encounter>()
            var discoveredCount = 0
            var discoveryStarted = false
            lateinit var finish: () -> Unit

            bonded.forEach { captured[it.primaryId] = it }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (intent?.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            runCatching {
                                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                } ?: return@runCatching
                                val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                                val key = resolveDeviceKey(device)
                                val encounter = deviceToEncounter(
                                    device = device,
                                    primaryId = key,
                                    location = location,
                                    discovered = true,
                                    discoveredRssi = rssi
                                )
                                discoveredCount += 1
                                val existing = captured[key]
                                if (existing == null || (encounter.rssiDbm ?: Int.MIN_VALUE) > (existing.rssiDbm ?: Int.MIN_VALUE)) {
                                    captured[key] = encounter
                                }
                            }.onFailure { error ->
                                logSkipped("Bluetooth ACTION_FOUND processing failed: ${error.message ?: "unknown error"}")
                            }
                        }

                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            finish()
                        }
                    }
                }
            }

            finish = {
                if (done.compareAndSet(false, true)) {
                    runCatching { context.unregisterReceiver(receiver) }
                    runCatching { adapter.cancelDiscovery() }
                    val resultCount = captured.size
                    if (resultCount == 0) {
                        consecutiveEmptyScans += 1
                        maybeLogEmptyScanDiagnostics(
                            discoveryStarted = discoveryStarted,
                            bondedCount = bonded.size,
                            discoveredCount = discoveredCount,
                            hasConnectPermission = hasConnectPermission()
                        )
                    } else {
                        consecutiveEmptyScans = 0
                    }
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
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    context.registerReceiver(receiver, filter)
                }
            }.isSuccess

            if (!registered) {
                logSkipped("Unable to register Bluetooth discovery receiver")
                if (continuation.isActive) continuation.resume(captured.values.toList())
                return@suspendCancellableCoroutine
            }

            runCatching {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                val started = adapter.startDiscovery()
                if (!started) {
                    throw IllegalStateException("Bluetooth discovery did not start")
                }
                discoveryStarted = true
            }.onFailure {
                logSkipped("Bluetooth discovery failed to start")
                finish()
                return@suspendCancellableCoroutine
            }

            handler.postDelayed({ finish() }, DISCOVERY_TIMEOUT_MS)
        }
    }

    private fun deviceToEncounter(
        device: BluetoothDevice,
        primaryId: String,
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
            primaryId = primaryId,
            secondaryId = name,
            rssiDbm = discoveredRssi,
            frequencyMhz = null,
            lat = location?.lat,
            lon = location?.lon,
            rawPayloadJson = JSONObject()
                .put("address", safeDeviceAddress(device))
                .put("name", name)
                .put("bondState", runCatching { device.bondState }.getOrNull())
                .put("type", runCatching { device.type }.getOrNull())
                .put("majorClass", majorClass)
                .put("deviceClass", deviceClass)
                .put("classLabel", codLabel)
                .put("source", if (discovered) "inquiry" else "bonded")
                .toString()
        )
    }

    private fun safeDeviceAddress(device: BluetoothDevice): String? =
        runCatching { device.address?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun resolveDeviceKey(device: BluetoothDevice): String {
        val address = safeDeviceAddress(device)
        if (address != null) return address

        val nameFallback = runCatching {
            if (hasConnectPermission()) device.name?.trim() else null
        }.getOrNull().orEmpty()
        if (nameFallback.isNotBlank()) {
            return "name:${nameFallback.lowercase()}"
        }

        val classFallback = runCatching { device.bluetoothClass?.deviceClass }.getOrNull()?.toString().orEmpty()
        val identityFallback = runCatching { device.toString() }.getOrDefault("bt-classic")
        return "anon:${classFallback}:${identityFallback}"
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

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val bt = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
            fine && bt
        }
    }

    private fun logSkipped(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastSkipLogEpochMs < 60_000L) return
        lastSkipLogEpochMs = now
        OperationalErrorLogStore.append(
            context = context,
            category = "SCAN_SOURCE",
            source = "bt_classic",
            message = "Bluetooth Classic scanner skipped: $reason"
        )
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun maybeLogEmptyScanDiagnostics(
        discoveryStarted: Boolean,
        bondedCount: Int,
        discoveredCount: Int,
        hasConnectPermission: Boolean
    ) {
        val now = System.currentTimeMillis()
        val intervalElapsed = now - lastEmptyScanLogEpochMs >= EMPTY_SCAN_LOG_INTERVAL_MS
        val hitConsecutiveThreshold = consecutiveEmptyScans >= EMPTY_SCAN_LOG_CONSECUTIVE_THRESHOLD
        if (!intervalElapsed && !hitConsecutiveThreshold) return

        lastEmptyScanLogEpochMs = now

        val hint = when {
            !discoveryStarted -> "Discovery did not start"
            !hasConnectPermission -> "BLUETOOTH_CONNECT missing (bonded list unavailable)"
            discoveredCount == 0 && bondedCount == 0 -> "No discoverable or bonded Classic devices observed"
            discoveredCount == 0 -> "No discoverable Classic devices observed during inquiry"
            else -> "Classic candidates were observed but filtered to zero unique encounters"
        }

        OperationalErrorLogStore.append(
            context = context,
            category = "SCAN_SOURCE_DIAGNOSTIC",
            source = "bt_classic",
            severity = "WARNING",
            message = "Bluetooth Classic empty scan x$consecutiveEmptyScans (started=$discoveryStarted, bonded=$bondedCount, found=$discoveredCount). $hint"
        )
    }
}
