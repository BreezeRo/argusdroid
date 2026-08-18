package dev.argus.tracker.sensing

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import dev.argus.tracker.permissions.AppPermissions
import dev.argus.tracker.worker.ScanSettings
import org.json.JSONObject
import kotlin.math.roundToInt

class WifiScanner(
    private val context: Context
) : SignalScanner {

    @SuppressLint("MissingPermission")
    override suspend fun scanOnce(): List<Encounter> {
        if (!ScanSettings.isWifiSensorEnabled(context)) return emptyList()
        if (!hasWifiPermissions()) return emptyList()
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return emptyList()

        // Best-effort scan request; platform throttling may still return cached results.
        val scanRequested = runCatching { wifiManager.startScan() }.getOrDefault(false)

        @Suppress("DEPRECATION")
        val results = runCatching { wifiManager.scanResults }.getOrNull() ?: emptyList()
        if (results.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val location = LocationSnapshotProvider.read(context)
        val aggregateOnly = ScanSettings.isWifiAggregateOnlyEnabled(context)

        val aggregate = buildWifiSweepEncounter(
            results = results,
            now = now,
            location = location,
            scanRequested = scanRequested
        )
        val perAccessPoint = results.map { result ->
            Encounter(
                timestampEpochMs = now,
                source = EncounterSource.WIFI,
                primaryId = result.BSSID ?: "unknown-bssid",
                secondaryId = result.SSID,
                rssiDbm = result.level,
                frequencyMhz = result.frequency,
                lat = location?.lat,
                lon = location?.lon,
                rawPayloadJson = buildWifiPayload(result, scanRequested)
            )
        }

        if (aggregateOnly) {
            val namedAccessPoints = perAccessPoint.filter { encounter ->
                hasUsableSsid(encounter.secondaryId)
            }
            return if (namedAccessPoints.isEmpty()) {
                listOf(aggregate)
            } else {
                listOf(aggregate) + namedAccessPoints
            }
        }

        return listOf(aggregate) + perAccessPoint
    }

    private fun hasUsableSsid(ssid: String?): Boolean {
        val normalized = ssid?.trim().orEmpty()
        if (normalized.isBlank()) return false
        val canonical = normalized.lowercase()
        return canonical != "<unknown ssid>" && canonical != "unknown ssid"
    }

    private fun buildWifiSweepEncounter(
        results: List<android.net.wifi.ScanResult>,
        now: Long,
        location: DetectionLocation?,
        scanRequested: Boolean
    ): Encounter {
        val strongest = results.maxByOrNull { it.level }
        val strongestRssi = strongest?.level
        val medianRssi = median(results.map { it.level })
        val uniqueBssidCount = results.mapNotNull { it.BSSID?.takeIf { bssid -> bssid.isNotBlank() } }
            .toSet()
            .size
        val hiddenSsidCount = results.count { it.SSID.isNullOrBlank() }
        val band24Count = results.count { it.frequency in 2400..2500 }
        val band5Count = results.count { it.frequency in 4900..5900 }
        val band6Count = results.count { it.frequency in 5925..7125 }
        val roleCounts = linkedMapOf<String, Int>()
        results.forEach { result ->
            val role = classifyWifiRole(result.SSID, result.capabilities)
            roleCounts[role] = (roleCounts[role] ?: 0) + 1
        }
        val topRoles = roleCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString(" | ") { (role, count) -> "$role:$count" }

        return Encounter(
            timestampEpochMs = now,
            source = EncounterSource.WIFI_SWEEP,
            primaryId = "wifi-scan-aggregate",
            secondaryId = "${results.size} APs",
            rssiDbm = strongestRssi,
            frequencyMhz = strongest?.frequency,
            lat = location?.lat,
            lon = location?.lon,
            rawPayloadJson = JSONObject()
                .put("mode", "aggregate")
                .put("scanRequestAccepted", scanRequested)
                .put("apCount", results.size)
                .put("uniqueBssidCount", uniqueBssidCount)
                .put("hiddenSsidCount", hiddenSsidCount)
                .put("strongestRssiDbm", strongestRssi)
                .put("medianRssiDbm", medianRssi)
                .put("band24Count", band24Count)
                .put("band5Count", band5Count)
                .put("band6Count", band6Count)
                .put("topRoleHints", topRoles)
                .put("timestampMicrosMax", results.maxOfOrNull { it.timestamp } ?: 0L)
                .toString()
        )
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

    private fun buildWifiPayload(result: android.net.wifi.ScanResult, scanRequested: Boolean): String {
        val roleHint = classifyWifiRole(result.SSID, result.capabilities)
        val payload = JSONObject()
            .put("ssid", result.SSID)
            .put("bssid", result.BSSID)
            .put("capabilities", result.capabilities)
            .put("deviceRoleHint", roleHint)
            .put("channelWidth", result.channelWidth)
            .put("centerFreq0", result.centerFreq0)
            .put("centerFreq1", result.centerFreq1)
            .put("wifiStandard", result.wifiStandard)
            .put("isPasspoint", result.isPasspointNetwork)
            .put("is80211mcResponder", result.is80211mcResponder)
            .put("timestampMicros", result.timestamp)
            .put("scanRequestAccepted", scanRequested)

        runCatching { payload.put("operatorFriendlyName", result.operatorFriendlyName?.toString()) }
        runCatching { payload.put("venueName", result.venueName?.toString()) }
        return payload.toString()
    }

    private fun classifyWifiRole(ssid: String?, capabilities: String?): String {
        val name = ssid.orEmpty().lowercase()
        val caps = capabilities.orEmpty().lowercase()
        return when {
            name.contains("drone") || name.contains("uav") -> "drone-control"
            name.contains("cam") || name.contains("camera") -> "camera"
            name.contains("print") -> "printer"
            name.contains("tv") || name.contains("roku") || name.contains("chromecast") -> "media-device"
            name.contains("iphone") || name.contains("pixel") || name.contains("galaxy") || name.contains("hotspot") -> "phone-hotspot"
            caps.contains("[ibss]") -> "ad-hoc"
            else -> "access-point"
        }
    }

    private fun hasWifiPermissions(): Boolean {
        return AppPermissions.hasWifiScanPermissions(context)
    }
}
