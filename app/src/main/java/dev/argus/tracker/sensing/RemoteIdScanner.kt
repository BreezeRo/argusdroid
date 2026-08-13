package dev.argus.tracker.sensing

import android.content.Context
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.SignalScanner
import dev.argus.tracker.worker.ScanSettings

class RemoteIdScanner(
    private val context: Context
) : SignalScanner {
    override suspend fun scanOnce(): List<Encounter> {
        if (!ScanSettings.isRemoteIdSensorEnabled(context)) return emptyList()
        // Android devices do not expose a universal Remote ID API yet. This scanner is a hook point
        // for OEM SDKs or USB SDR integrations that parse ASTM F3411 broadcasts.
        return emptyList()
    }
}
