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
    private companion object {
        private const val COMPANY_ID_APPLE = 0x004C
        private const val COMPANY_ID_GOOGLE = 0x00E0
        private const val COMPANY_ID_GOOGLE_LLC = 0x018E
        private const val COMPANY_ID_SAMSUNG = 0x0075
        private const val COMPANY_ID_TILE = 0x067C
        private const val COMPANY_ID_CHIPOLO = 0x08C3

        private const val SERVICE_UUID_FAST_PAIR = "fe2c"
        private const val SERVICE_UUID_EDDYSTONE = "feaa"
    }

    private data class BleTrackerSignature(
        val family: String,
        val confidence: Double,
        val likelyTracker: Boolean,
        val evidence: List<String>
    )

    private var lastSkipLogEpochMs: Long = 0L

    override suspend fun scanOnce(): List<Encounter> {
        if (!ArgusScanSettings.isBleSensorEnabled(context)) {
            logSkipped("Bluetooth LE sensor disabled in settings")
            return emptyList()
        }
        val hasBleScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        if (!hasBleScanPermission) {
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
                    runCatching {
                        result.toEncounter(location)
                    }.onSuccess { encounter ->
                        val encounterKey = "${encounter.source.name}|${encounter.primaryId}"
                        val existing = captured[encounterKey]
                        if (existing == null || (encounter.rssiDbm ?: Int.MIN_VALUE) > (existing.rssiDbm ?: Int.MIN_VALUE)) {
                            captured[encounterKey] = encounter
                        }
                    }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { scanResult ->
                        onScanResult(0, scanResult)
                    }
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

    private fun ScanResult.toEncounter(location: DetectionLocation?): Encounter {
        val canReadConnectData = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        val mac = if (canReadConnectData) {
            runCatching { device?.address }.getOrNull() ?: "unknown-ble"
        } else {
            "unknown-ble"
        }
        val name = runCatching {
            if (canReadConnectData) device?.name else null
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
        val canReadConnectData = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        val address = if (canReadConnectData) runCatching { result.device?.address }.getOrNull() else null
        val name = runCatching {
            if (canReadConnectData) result.device?.name else null
        }.getOrNull()
        val record = result.scanRecord
        val classification = classifyBleDevice(name, record)
        val remoteIdCandidate = isLikelyRemoteId(record)
        val trackerSignature = classifyTrackerSignature(
            name = name,
            record = record,
            remoteIdCandidate = remoteIdCandidate || decodedRemoteId != null
        )
        val manufacturerCompanyIds = readManufacturerCompanyIds(record)
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
            .put("trackerFamilyHint", trackerSignature.family)
            .put("trackerFamilyConfidence", trackerSignature.confidence)
            .put("trackerLikely", trackerSignature.likelyTracker)
            .put("trackerEvidence", trackerSignature.evidence.joinToString(separator = ","))
            .put("manufacturerCompanyIds", manufacturerCompanyIds.joinToString(separator = ","))

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

    private fun readManufacturerCompanyIds(record: android.bluetooth.le.ScanRecord?): List<Int> {
        val manufacturerData = record?.manufacturerSpecificData ?: return emptyList()
        return buildList {
            for (index in 0 until manufacturerData.size()) {
                add(manufacturerData.keyAt(index))
            }
        }
    }

    private fun classifyTrackerSignature(
        name: String?,
        record: android.bluetooth.le.ScanRecord?,
        remoteIdCandidate: Boolean
    ): BleTrackerSignature {
        if (remoteIdCandidate) {
            return BleTrackerSignature(
                family = "remote_id",
                confidence = 0.95,
                likelyTracker = false,
                evidence = listOf("remote-id-signature")
            )
        }

        val normalizedName = name.orEmpty().trim().lowercase()
        val serviceUuid16 = readServiceUuid16(record)
        val manufacturerIds = readManufacturerCompanyIds(record)
        val trackerKeywordHit = containsAnyKeyword(
            normalizedName,
            listOf("airtag", "smarttag", "tile", "chipolo", "pebblebee", "tracker", "locator", "find my", "findmy")
        )
        val trackerIntentScore = buildTrackerIntentScore(
            normalizedName = normalizedName,
            serviceUuid16 = serviceUuid16,
            trackerKeywordHit = trackerKeywordHit
        )

        val candidates = buildList {
            add(scoreAppleFamily(normalizedName, serviceUuid16, manufacturerIds, record))
            add(scoreGoogleFamily(normalizedName, serviceUuid16, manufacturerIds, record))
            add(scoreTileFamily(normalizedName, serviceUuid16, manufacturerIds))
            add(scoreChipoloFamily(normalizedName, manufacturerIds))
            add(scoreSamsungFamily(normalizedName, serviceUuid16, manufacturerIds))
        }

        val best = candidates.maxByOrNull { it.confidence }
        val bestFamily = best?.family ?: "unclassified"
        val bestScore = (best?.confidence ?: 0.0).coerceIn(0.0, 1.0)
        val likelyTracker = when {
            bestFamily in setOf("tile", "chipolo", "samsung_smarttag", "apple_find_my") &&
                bestScore >= 0.62 && trackerIntentScore >= 0.45 -> true
            bestFamily == "google_find_my" && bestScore >= 0.72 && trackerIntentScore >= 0.58 -> true
            else -> false
        }

        if (bestScore >= 0.40) {
            return BleTrackerSignature(
                family = bestFamily,
                confidence = bestScore,
                likelyTracker = likelyTracker,
                evidence = best?.evidence.orEmpty().distinct().sorted()
            )
        }

        val genericTrackerSignal = trackerKeywordHit

        if (genericTrackerSignal) {
            return BleTrackerSignature(
                family = "unknown_tracker",
                confidence = 0.46,
                likelyTracker = true,
                evidence = listOf("generic-tracker-name")
            )
        }

        return BleTrackerSignature(
            family = "non_tracker_or_unknown",
            confidence = 0.15,
            likelyTracker = false,
            evidence = if (manufacturerIds.isEmpty()) emptyList() else listOf("manufacturer-id-unmapped")
        )
    }

    private data class FamilyScore(
        val family: String,
        val confidence: Double,
        val evidence: List<String>
    )

    private fun scoreAppleFamily(
        normalizedName: String,
        serviceUuid16: Set<String>,
        manufacturerIds: List<Int>,
        record: android.bluetooth.le.ScanRecord?
    ): FamilyScore {
        var score = 0.0
        val evidence = mutableListOf<String>()
        if (manufacturerIds.contains(COMPANY_ID_APPLE)) {
            score += 0.18
            evidence += "apple-manufacturer-id"
        }
        if (containsAnyKeyword(normalizedName, listOf("airtag", "find my", "findmy"))) {
            score += 0.55
            evidence += "airtag-findmy-name"
        }
        if (serviceUuid16.any { it == "fd44" || it == "fd45" || it == "fd46" }) {
            score += 0.28
            evidence += "apple-findmy-service-uuid"
        }
        if (looksLikeAppleContinuityFrame(record)) {
            score += 0.12
            evidence += "apple-continuity-frame"
        }
        return FamilyScore("apple_find_my", score.coerceIn(0.0, 1.0), evidence)
    }

    private fun scoreGoogleFamily(
        normalizedName: String,
        serviceUuid16: Set<String>,
        manufacturerIds: List<Int>,
        record: android.bluetooth.le.ScanRecord?
    ): FamilyScore {
        var score = 0.0
        val evidence = mutableListOf<String>()
        if (manufacturerIds.contains(COMPANY_ID_GOOGLE) || manufacturerIds.contains(COMPANY_ID_GOOGLE_LLC)) {
            score += 0.24
            evidence += "google-manufacturer-id"
        }
        if (containsAnyKeyword(normalizedName, listOf("google", "find my", "findmy", "chipolo", "pebblebee"))) {
            score += 0.34
            evidence += "google-ecosystem-name"
        }
        if (serviceUuid16.contains(SERVICE_UUID_FAST_PAIR)) {
            score += 0.20
            evidence += "fast-pair-service-uuid"
            if (hasFastPairModelId(record)) {
                score += 0.12
                evidence += "fast-pair-model-id"
            }
        }
        if (serviceUuid16.contains(SERVICE_UUID_EDDYSTONE)) {
            score += 0.06
            evidence += "eddystone-service-uuid"
        }
        return FamilyScore("google_find_my", score.coerceIn(0.0, 1.0), evidence)
    }

    private fun scoreTileFamily(
        normalizedName: String,
        serviceUuid16: Set<String>,
        manufacturerIds: List<Int>
    ): FamilyScore {
        var score = 0.0
        val evidence = mutableListOf<String>()
        if (manufacturerIds.contains(COMPANY_ID_TILE)) {
            score += 0.62
            evidence += "tile-manufacturer-id"
        }
        if (normalizedName.contains("tile")) {
            score += 0.34
            evidence += "tile-name"
        }
        if (serviceUuid16.any { it == "feed" || it == "feec" }) {
            score += 0.12
            evidence += "tile-service-uuid-pattern"
        }
        return FamilyScore("tile", score.coerceIn(0.0, 1.0), evidence)
    }

    private fun scoreChipoloFamily(
        normalizedName: String,
        manufacturerIds: List<Int>
    ): FamilyScore {
        var score = 0.0
        val evidence = mutableListOf<String>()
        if (manufacturerIds.contains(COMPANY_ID_CHIPOLO)) {
            score += 0.68
            evidence += "chipolo-manufacturer-id"
        }
        if (normalizedName.contains("chipolo")) {
            score += 0.30
            evidence += "chipolo-name"
        }
        return FamilyScore("chipolo", score.coerceIn(0.0, 1.0), evidence)
    }

    private fun scoreSamsungFamily(
        normalizedName: String,
        serviceUuid16: Set<String>,
        manufacturerIds: List<Int>
    ): FamilyScore {
        var score = 0.0
        val evidence = mutableListOf<String>()
        if (manufacturerIds.contains(COMPANY_ID_SAMSUNG)) {
            score += 0.24
            evidence += "samsung-manufacturer-id"
        }
        if (containsAnyKeyword(normalizedName, listOf("smarttag", "galaxy tag", "samsung tag"))) {
            score += 0.60
            evidence += "smarttag-name"
        }
        if (serviceUuid16.contains("fd5a")) {
            score += 0.12
            evidence += "samsung-smarttag-service-uuid"
        }
        return FamilyScore("samsung_smarttag", score.coerceIn(0.0, 1.0), evidence)
    }

    private fun containsAnyKeyword(value: String, keywords: List<String>): Boolean {
        return keywords.any { keyword -> value.contains(keyword) }
    }

    private fun buildTrackerIntentScore(
        normalizedName: String,
        serviceUuid16: Set<String>,
        trackerKeywordHit: Boolean
    ): Double {
        var score = 0.0
        if (trackerKeywordHit) score += 0.56
        if (serviceUuid16.any { it == "fd44" || it == "fd45" || it == "fd46" || it == "fd5a" }) {
            score += 0.30
        }
        if (normalizedName.contains("tag") || normalizedName.contains("tracker") || normalizedName.contains("locator")) {
            score += 0.24
        }
        return score.coerceIn(0.0, 1.0)
    }

    private fun readServiceUuid16(record: android.bluetooth.le.ScanRecord?): Set<String> {
        return record?.serviceUuids
            ?.mapNotNull { parcelUuid ->
                val value = parcelUuid.toString().lowercase()
                when {
                    value.length == 4 -> value
                    value.startsWith("0000") && value.length >= 8 -> value.substring(4, 8)
                    else -> null
                }
            }
            ?.toSet()
            .orEmpty()
    }

    private fun looksLikeAppleContinuityFrame(record: android.bluetooth.le.ScanRecord?): Boolean {
        val manufacturerData = record?.manufacturerSpecificData ?: return false
        val payload = manufacturerData.get(COMPANY_ID_APPLE) ?: return false
        if (payload.size < 2) return false
        val frameType = payload[0].toInt() and 0xFF
        val frameLength = payload[1].toInt() and 0xFF
        return frameType in setOf(0x10, 0x12, 0x19) && frameLength <= payload.size
    }

    private fun hasFastPairModelId(record: android.bluetooth.le.ScanRecord?): Boolean {
        val serviceData = record?.serviceData ?: return false
        val entry = serviceData.entries
            .asSequence()
            .mapNotNull { (key, value) ->
                val uuid = key.toString().lowercase()
                val short = if (uuid.startsWith("0000") && uuid.length >= 8) uuid.substring(4, 8) else uuid
                if (short == SERVICE_UUID_FAST_PAIR) value else null
            }
            .firstOrNull()
            ?: return false

        // Fast Pair service data starts with a 3-byte model identifier for discoverable advertisements.
        return entry.size >= 3
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
