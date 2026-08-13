package dev.argus.tracker.sensing

import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.SignalScanner

class ArgusSensingService(
    private val scanners: List<SignalScanner>
) {
    suspend fun collectBatch(): List<Encounter> = buildList {
        scanners.forEach { scanner ->
            val scannerBatch = runCatching { scanner.scanOnce() }.getOrDefault(emptyList())
            addAll(scannerBatch)
        }
    }
}
