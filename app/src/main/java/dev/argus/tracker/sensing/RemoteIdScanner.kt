package dev.argus.tracker.sensing

import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner

class RemoteIdScanner : SignalScanner {
    override suspend fun scanOnce(): List<Encounter> {
        // Android devices do not expose a universal Remote ID API yet. This scanner is a hook point
        // for OEM SDKs or USB SDR integrations that parse ASTM F3411 broadcasts.
        return listOf(
            Encounter(
                timestampEpochMs = System.currentTimeMillis(),
                source = EncounterSource.REMOTE_ID,
                primaryId = "remote-id-unavailable",
                secondaryId = null,
                rssiDbm = null,
                frequencyMhz = null,
                lat = null,
                lon = null,
                rawPayloadJson = "{\"status\":\"not-supported-on-stock-android\"}"
            )
        )
    }
}
