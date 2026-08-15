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
import dev.argus.tracker.data.OperationalErrorLogStore
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import dev.argus.tracker.sensing.remoteid.RemoteIdBleDecoder
import dev.argus.tracker.sensing.remoteid.RemoteIdPayloadParser
import dev.argus.tracker.worker.ScanSettings as ArgusScanSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.roundToInt

class BleScanner(
    private val context: Context
) : SignalScanner {
    private var lastSkipLogEpochMs: Long = 0L

    override suspend fun scanOnce(): List<Encounter> {
        if (!ArgusScanSettings.isBleSensorEnabled(context)) {
            logSkipped("Bluetooth LE sensor disabled in settings")
            return emptyList()
        }
        if (!hasBlePermissions()) {
            logSkipped("Missing Bluetooth LE scan permissions")
            return emptyList()
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return emptyList()
        val adapter: BluetoothAdapter = manager.adapter ?: return emptyList()
        if (!adapter.isEnabled) {
            logSkipped("Bluetooth adapter disabled")
            return emptyList()
        }
        val scanner = adapter.bluetoothLeScanner ?: return emptyList()
        val location = LocationSnapshotProvider.read(context)

        return suspendCancellableCoroutine { continuation ->
            val done = AtomicBoolean(false)
            val handler = Handler(Looper.getMainLooper())
            val captured = linkedMapOf<String, Encounter>()
            var observedEvents = 0

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    observedEvents += 1
                    val encounter = result.toEncounter(location)
                    val encounterKey = "${encounter.source.name}|${encounter.primaryId}"
                    val existing = captured[encounterKey]
                    if (existing == null || (encounter.rssiDbm ?: Int.MIN_VALUE) > (existing.rssiDbm ?: Int.MIN_VALUE)) {
                        captured[encounterKey] = encounter
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
                    val finalized = finalizeBleSweepResults(
                        encounters = captured.values.toList(),
                        observedEvents = observedEvents,
                        location = location,
                        aggregateOnly = ArgusScanSettings.isBleAggregateOnlyEnabled(context)
                    )
                    if (continuation.isActive) continuation.resume(finalized)
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
        val record = scanRecord
        val decodedRemoteId = RemoteIdBleDecoder.decodeFromScanRecord(record)
        val remoteIdCandidate = decodedRemoteId != null || isLikelyRemoteId(record)
        val remoteIdPrimaryId = decodedRemoteId?.uasId?.takeIf { it.isNotBlank() }
        val remoteIdSecondaryId = decodedRemoteId?.operatorId?.takeIf { it.isNotBlank() }
        return Encounter(
            timestampEpochMs = System.currentTimeMillis(),
            source = if (remoteIdCandidate) EncounterSource.REMOTE_ID else EncounterSource.BLUETOOTH_LE,
            primaryId = if (remoteIdCandidate) remoteIdPrimaryId ?: mac else mac,
            secondaryId = if (remoteIdCandidate) remoteIdSecondaryId ?: name else name,
            rssiDbm = rssi,
            frequencyMhz = null,
            lat = location?.lat,
            lon = location?.lon,
            rawPayloadJson = buildBlePayload(this, decodedRemoteId)
        )
    }

    private fun buildBlePayload(
        result: ScanResult,
        decodedRemoteId: dev.argus.tracker.sensing.remoteid.RemoteIdDecoded?
    ): String {
        val address = runCatching { result.device?.address }.getOrNull()
        val name = runCatching {
            if (hasBluetoothConnectPermission()) result.device?.name else null
        }.getOrNull()
        val record = result.scanRecord
        val classification = classifyBleDevice(name, record)
        val remoteIdCandidate = isLikelyRemoteId(record)
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
            .put("deviceClassHint", classification)
            .put("remoteIdCandidate", remoteIdCandidate)

        payload.put("advertiseFlags", record?.advertiseFlags)
        payload.put("txPowerLevel", record?.txPowerLevel)
        payload.put("serviceUuids", record?.serviceUuids?.joinToString(separator = ",") { it.toString() })
        payload.put("manufacturerSpecificDataSize", record?.manufacturerSpecificData?.size() ?: 0)
        payload.put("serviceDataSize", record?.serviceData?.size ?: 0)
        payload.put("rawBytesHex", record?.bytes?.toHex())

        decodedRemoteId?.let { decoded ->
            payload.put("remoteIdSchema", RemoteIdPayloadParser.SCHEMA_VERSION)
            payload.put("remoteIdTransport", "BLE")
            payload.put("remoteIdParserVersion", decoded.parserVersion)
            payload.put("remoteIdDecoded", JSONObject()
                .put("messageType", decoded.messageType)
                .put("uasId", decoded.uasId)
                .put("operatorId", decoded.operatorId)
                .put("operatorLat", decoded.operatorLat)
                .put("operatorLon", decoded.operatorLon)
                .put("droneLat", decoded.droneLat)
                .put("droneLon", decoded.droneLon)
                .put("altitudeMeters", decoded.altitudeMeters)
                .put("speedMetersPerSecond", decoded.speedMetersPerSecond)
                .put("headingDegrees", decoded.headingDegrees)
                .put("emergencyStatus", decoded.emergencyStatus)
                .put("messageTimestampEpochMs", decoded.messageTimestampEpochMs)
                .put("parseConfidence", decoded.parseConfidence.name)
                .put("parseNotes", decoded.parseNotes.joinToString(separator = ","))
            )
        }

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

    private fun classifyBleDevice(name: String?, record: android.bluetooth.le.ScanRecord?): String {
        val normalizedName = name.orEmpty().lowercase()
        val uuids = record?.serviceUuids
            ?.map { it.toString().lowercase() }
            .orEmpty()

        return when {
            normalizedName.contains("tile") || normalizedName.contains("airtag") -> "tracker-tag"
            normalizedName.contains("watch") || normalizedName.contains("band") -> "wearable"
            normalizedName.contains("buds") || normalizedName.contains("head") || normalizedName.contains("audio") -> "audio"
            normalizedName.contains("sensor") || uuids.any { it.contains("181a") } -> "environment-sensor"
            uuids.any { it.contains("180d") } -> "heart-rate"
            uuids.any { it.contains("180f") } -> "battery-powered"
            uuids.any { it.contains("1812") } -> "input-device"
            else -> "unknown"
        }
    }

    private fun isLikelyRemoteId(record: android.bluetooth.le.ScanRecord?): Boolean {
        val serviceUuids = record?.serviceUuids
            ?.map { it.toString().lowercase() }
            .orEmpty()
        val remoteIdServiceMatch = serviceUuids.any {
            it.contains("fffa") || it.contains("fffb") || it.contains("fffc")
        }
        if (remoteIdServiceMatch) return true

        val manufacturerData = record?.manufacturerSpecificData ?: return false
        for (index in 0 until manufacturerData.size()) {
            val bytes = manufacturerData.valueAt(index) ?: continue
            val ascii = runCatching { String(bytes, Charsets.US_ASCII) }.getOrDefault("")
            if (ascii.contains("ASTM", ignoreCase = true) || ascii.contains("REMOTEID", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun finalizeBleSweepResults(
        encounters: List<Encounter>,
        observedEvents: Int,
        location: DetectionLocation?,
        aggregateOnly: Boolean
    ): List<Encounter> {
        if (encounters.isEmpty()) {
            return if (observedEvents > 0) {
                listOf(
                    buildBleSweepEncounter(
                        bleEncounters = emptyList(),
                        observedEvents = observedEvents,
                        location = location,
                        remoteIdCount = 0,
                        retainedBleCount = 0,
                        aggregateOnly = aggregateOnly
                    )
                )
            } else {
                emptyList()
            }
        }

        val remoteIdEncounters = encounters.filter { it.source == EncounterSource.REMOTE_ID }
        val bleEncounters = encounters.filter { it.source == EncounterSource.BLUETOOTH_LE }

        val namedBleEncounters = bleEncounters.filter { encounter ->
            hasUsableSecondaryId(encounter.secondaryId)
        }
        val retainedBleEncounters = if (aggregateOnly) namedBleEncounters else bleEncounters

        val sweepEncounter = buildBleSweepEncounter(
            bleEncounters = bleEncounters,
            observedEvents = observedEvents,
            location = location,
            remoteIdCount = remoteIdEncounters.size,
            retainedBleCount = retainedBleEncounters.size,
            aggregateOnly = aggregateOnly
        )

        return buildList {
            add(sweepEncounter)
            addAll(remoteIdEncounters)
            addAll(retainedBleEncounters)
        }
    }

    private fun buildBleSweepEncounter(
        bleEncounters: List<Encounter>,
        observedEvents: Int,
        location: DetectionLocation?,
        remoteIdCount: Int,
        retainedBleCount: Int,
        aggregateOnly: Boolean
    ): Encounter {
        val strongestRssi = bleEncounters.mapNotNull { it.rssiDbm }.maxOrNull()
        val medianRssi = median(bleEncounters.mapNotNull { it.rssiDbm })
        val randomizedLikelyCount = bleEncounters.count { isLikelyRandomizedMacAddress(it.primaryId) }
        val namedCount = bleEncounters.count { hasUsableSecondaryId(it.secondaryId) }

        return Encounter(
            timestampEpochMs = System.currentTimeMillis(),
            source = EncounterSource.BLUETOOTH_LE_SWEEP,
            primaryId = "ble-scan-aggregate",
            secondaryId = "${bleEncounters.size} devices",
            rssiDbm = strongestRssi,
            frequencyMhz = null,
            lat = location?.lat,
            lon = location?.lon,
            rawPayloadJson = JSONObject()
                .put("mode", if (aggregateOnly) "aggregate_only" else "aggregate_all")
                .put("observedEvents", observedEvents)
                .put("uniqueBleCount", bleEncounters.size)
                .put("retainedBleCount", retainedBleCount)
                .put("aggregateOnlyEnabled", aggregateOnly)
                .put("remoteIdCandidateCount", remoteIdCount)
                .put("likelyRandomizedBleCount", randomizedLikelyCount)
                .put("namedBleCount", namedCount)
                .put("strongestRssiDbm", strongestRssi)
                .put("medianRssiDbm", medianRssi)
                .toString()
        )
    }

    private fun hasUsableSecondaryId(value: String?): Boolean {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) return false
        val canonical = normalized.lowercase()
        return canonical != "<unknown>" && canonical != "unknown"
    }

    private fun isLikelyRandomizedMacAddress(macAddress: String?): Boolean {
        val mac = macAddress?.trim() ?: return false
        val firstOctetHex = mac.split(':').firstOrNull()?.takeIf { it.length == 2 } ?: return false
        val firstOctet = firstOctetHex.toIntOrNull(16) ?: return false
        val isLocallyAdministered = (firstOctet and 0x02) != 0
        val isUnicast = (firstOctet and 0x01) == 0
        return isLocallyAdministered && isUnicast
    }

    private fun median(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val ordered = values.sorted()
        val middle = ordered.size / 2
        return if (ordered.size % 2 == 1) {
            ordered[middle]
        } else {
            ((ordered[middle - 1] + ordered[middle]) / 2.0).roundToInt()
        }
    }

    private fun logSkipped(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastSkipLogEpochMs < 60_000L) return
        lastSkipLogEpochMs = now
        OperationalErrorLogStore.append(
            context = context,
            category = "SCAN_SOURCE",
            source = "ble",
            message = "Bluetooth LE scanner skipped: $reason"
        )
    }
}
