package dev.argus.tracker.sensing

import android.content.Context
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import dev.argus.tracker.worker.ScanSettings

class RemoteIdScanner(
    private val context: Context
) : SignalScanner {
    private val feedScanner = ExternalFeedScanner(
        context = context,
        source = EncounterSource.REMOTE_ID,
        feedName = "remote_id"
    )

    override suspend fun scanOnce(): List<Encounter> {
        if (!ScanSettings.isRemoteIdSensorEnabled(context)) return emptyList()
        // Path 1: ingested Remote ID payloads dropped into files/ingest/remote_id.jsonl.
        val feedEncounters = feedScanner.scanOnce()
        if (feedEncounters.isNotEmpty()) return feedEncounters

        // Path 2: BLE-based candidates are promoted by BleScanner when Remote ID signatures appear.
        return emptyList()
    }
}
