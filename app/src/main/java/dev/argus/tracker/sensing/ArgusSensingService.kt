package dev.argus.tracker.sensing

import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.SignalScanner
import android.os.SystemClock
import android.content.Context
import dev.argus.tracker.worker.ScanSettings

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
        val startedAt = SystemClock.elapsedRealtime()
        val allEncounters = mutableListOf<Encounter>()
        val sourceDurations = linkedMapOf<String, Long>()

        scanners.forEach { scanner ->
            val sourceType = scannerSourceType(scanner)
            val nowEpochMs = System.currentTimeMillis()
            val sourceIntervalSeconds = ScanSettings.getSourceScanIntervalSeconds(context, sourceType)
            val sourceLastScanEpochMs = ScanSettings.getSourceLastScanEpochMs(context, sourceType)
            val elapsedSinceLast = nowEpochMs - sourceLastScanEpochMs
            val minElapsedMs = sourceIntervalSeconds * 1000L

            if (sourceLastScanEpochMs > 0L && elapsedSinceLast in 0 until minElapsedMs) {
                return@forEach
            }

            val scanStartedAt = SystemClock.elapsedRealtime()
            val scannerBatch = runCatching { scanner.scanOnce() }.getOrDefault(emptyList())
            val durationMs = (SystemClock.elapsedRealtime() - scanStartedAt).coerceAtLeast(0L)
            sourceDurations[sourceType] = durationMs
            allEncounters += scannerBatch
            ScanSettings.setSourceLastScanEpochMs(context, sourceType, nowEpochMs)
        }

        val totalDuration = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        return ScanBatchResult(
            encounters = allEncounters,
            sourceDurationsMs = sourceDurations,
            totalDurationMs = totalDuration
        )
    }

    private fun scannerSourceType(scanner: SignalScanner): String = when (scanner) {
        is WifiScanner -> "wifi"
        is BleScanner -> "ble"
        is CellularScanner -> "cellular"
        is RemoteIdScanner -> "remote_id"
        else -> scanner::class.java.simpleName.lowercase()
    }
}
