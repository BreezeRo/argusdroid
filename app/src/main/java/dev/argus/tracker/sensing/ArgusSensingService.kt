package dev.argus.tracker.sensing

import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.SourceCatalog
import dev.argus.tracker.domain.SignalScanner
import android.os.SystemClock
import android.content.Context
import dev.argus.tracker.data.OwnedSignalRegistry
import dev.argus.tracker.data.OperationalErrorLogStore
import dev.argus.tracker.worker.ScanSettings
import kotlinx.coroutines.CancellationException

data class ScanBatchResult(
    val encounters: List<Encounter>,
    val sourceDurationsMs: Map<String, Long>,
    val totalDurationMs: Long
)

class ArgusSensingService(
    private val context: Context,
    private val scanners: List<SignalScanner>
) {
    suspend fun collectBatch(): List<Encounter> = collectBatchWithMetrics().encounters

    suspend fun collectBatchWithMetrics(): ScanBatchResult {
        if (ScanSettings.isMeshWipeGateEnabled(context)) {
            return ScanBatchResult(
                encounters = emptyList(),
                sourceDurationsMs = emptyMap(),
                totalDurationMs = 0L
            )
        }

        val startedAt = SystemClock.elapsedRealtime()
        val allEncounters = mutableListOf<Encounter>()
        val sourceDurations = linkedMapOf<String, Long>()
        val processedIntervalSources = mutableSetOf<String>()

        scanners.forEach { scanner ->
            val sourceType = scannerSourceType(scanner)
            val intervalGroupType = intervalSourceType(sourceType)
            if (!processedIntervalSources.add(intervalGroupType)) {
                return@forEach
            }

            val groupedScanners = scanners.filter {
                intervalSourceType(scannerSourceType(it)) == intervalGroupType
            }
            val nowEpochMs = System.currentTimeMillis()
            val sourceIntervalSeconds = ScanSettings.getSourceScanIntervalSeconds(context, intervalGroupType)
            val sourceLastScanEpochMs = ScanSettings.getSourceLastScanEpochMs(context, intervalGroupType)
            val elapsedSinceLast = nowEpochMs - sourceLastScanEpochMs
            val minElapsedMs = sourceIntervalSeconds * 1000L

            if (sourceLastScanEpochMs > 0L && elapsedSinceLast in 0 until minElapsedMs) {
                return@forEach
            }

            groupedScanners.forEach { groupedScanner ->
                val groupedSourceType = scannerSourceType(groupedScanner)
                val scanStartedAt = SystemClock.elapsedRealtime()
                val scannerBatch = runCatching { groupedScanner.scanOnce() }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        OperationalErrorLogStore.append(
                            context = context,
                            category = "SCAN_SOURCE",
                            source = groupedSourceType,
                            message = "Source scan failed: ${error.message ?: "unknown error"}"
                        )
                    }
                    .getOrDefault(emptyList())
                if (scannerBatch.isNotEmpty()) {
                    val latestRawEpochMs = scannerBatch
                        .maxOfOrNull { encounter -> encounter.timestampEpochMs }
                        ?.coerceAtLeast(0L)
                        ?: nowEpochMs
                    ScanSettings.setSourceLastRawObservationEpochMs(context, groupedSourceType, latestRawEpochMs)
                }
                val gatedBatch = applySourceGate(groupedSourceType, scannerBatch)
                val durationMs = (SystemClock.elapsedRealtime() - scanStartedAt).coerceAtLeast(0L)
                sourceDurations[groupedSourceType] = durationMs
                allEncounters += gatedBatch
                ScanSettings.setSourceLastScanEpochMs(context, groupedSourceType, nowEpochMs)
            }

            if (intervalGroupType != sourceType) {
                ScanSettings.setSourceLastScanEpochMs(context, intervalGroupType, nowEpochMs)
            }
        }

        markLocalSweepAggregatesAsOwned(allEncounters)

        val totalDuration = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        return ScanBatchResult(
            encounters = allEncounters,
            sourceDurationsMs = sourceDurations,
            totalDurationMs = totalDuration
        )
    }

    private fun markLocalSweepAggregatesAsOwned(encounters: List<Encounter>) {
        if (encounters.isEmpty()) return

        encounters.forEach { encounter ->
            when (encounter.source) {
                dev.argus.tracker.domain.EncounterSource.WIFI_SWEEP,
                dev.argus.tracker.domain.EncounterSource.BLUETOOTH_LE_SWEEP -> {
                    OwnedSignalRegistry.setOwned(
                        context = context,
                        source = encounter.source.name,
                        primaryId = encounter.primaryId,
                        owned = true
                    )
                }

                else -> Unit
            }
        }
    }

    private fun scannerSourceType(scanner: SignalScanner): String = when (scanner) {
        is WifiScanner -> SourceCatalog.KEY_WIFI
        is WifiDirectScanner -> SourceCatalog.KEY_WIFI_DIRECT
        is BleScanner -> SourceCatalog.KEY_BLE
        is BluetoothClassicScanner -> SourceCatalog.KEY_BT_CLASSIC
        is NfcScanner -> SourceCatalog.KEY_NFC
        is CellularScanner -> SourceCatalog.KEY_CELLULAR
        is RemoteIdScanner -> SourceCatalog.KEY_REMOTE_ID
        is CameraScanner -> SourceCatalog.KEY_CAMERA
        is AviationScanner -> SourceCatalog.KEY_AIRCRAFT
        is ExternalFeedScanner -> scanner.sourceTypeKey
        is AcousticSignatureScanner -> SourceCatalog.KEY_ACOUSTIC
        is MagnetometerDisturbanceScanner -> SourceCatalog.KEY_MAGNETIC
        else -> scanner::class.java.simpleName.lowercase()
    }

    private fun intervalSourceType(sourceType: String): String = when (sourceType) {
        SourceCatalog.KEY_WIFI_DIRECT -> SourceCatalog.KEY_WIFI
        SourceCatalog.KEY_REMOTE_ID -> SourceCatalog.KEY_BLE
        else -> sourceType
    }

    private fun applySourceGate(sourceType: String, encounters: List<Encounter>): List<Encounter> {
        if (encounters.isEmpty()) return encounters

        return when (sourceType) {
            SourceCatalog.KEY_BLE -> {
                if (ScanSettings.isBleSensorEnabled(context)) {
                    encounters
                } else {
                    OperationalErrorLogStore.append(
                        context = context,
                        category = "SCAN_SOURCE_DIAGNOSTIC",
                        source = sourceType,
                        severity = "WARNING",
                        message = "Dropped ${encounters.size} BLE scanner encounter(s) because Bluetooth LE source is disabled"
                    )
                    emptyList()
                }
            }

            SourceCatalog.KEY_BT_CLASSIC -> {
                if (ScanSettings.isBluetoothClassicSensorEnabled(context)) {
                    encounters
                } else {
                    OperationalErrorLogStore.append(
                        context = context,
                        category = "SCAN_SOURCE_DIAGNOSTIC",
                        source = sourceType,
                        severity = "WARNING",
                        message = "Dropped ${encounters.size} Bluetooth Classic scanner encounter(s) because Bluetooth source is disabled"
                    )
                    emptyList()
                }
            }

            else -> encounters
        }
    }
}
