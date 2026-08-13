package dev.argus.tracker.sensing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import org.json.JSONObject

class WifiScanner(
    private val context: Context
) : SignalScanner {

    override suspend fun scanOnce(): List<Encounter> {
        if (!hasWifiPermissions()) return emptyList()
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return emptyList()

        @Suppress("DEPRECATION")
        val results = wifiManager.scanResults ?: emptyList()
        val now = System.currentTimeMillis()

        return results.map { result ->
            Encounter(
                timestampEpochMs = now,
                source = EncounterSource.WIFI,
                primaryId = result.BSSID ?: "unknown-bssid",
                secondaryId = result.SSID,
                rssiDbm = result.level,
                frequencyMhz = result.frequency,
                lat = null,
                lon = null,
                rawPayloadJson = buildWifiPayload(result)
            )
        }
    }

    private fun buildWifiPayload(result: android.net.wifi.ScanResult): String {
        val payload = JSONObject()
            .put("ssid", result.SSID)
            .put("bssid", result.BSSID)
            .put("capabilities", result.capabilities)
            .put("channelWidth", result.channelWidth)
            .put("centerFreq0", result.centerFreq0)
            .put("centerFreq1", result.centerFreq1)
            .put("wifiStandard", result.wifiStandard)
            .put("isPasspoint", result.isPasspointNetwork)
            .put("is80211mcResponder", result.is80211mcResponder)
            .put("timestampMicros", result.timestamp)

        runCatching { payload.put("operatorFriendlyName", result.operatorFriendlyName?.toString()) }
        runCatching { payload.put("venueName", result.venueName?.toString()) }
        return payload.toString()
    }

    private fun hasWifiPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val wifi = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED
        return fine && wifi
    }
}
