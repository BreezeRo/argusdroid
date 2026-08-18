package dev.argus.tracker.sensing

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.argus.tracker.data.OperationalErrorLogStore
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import dev.argus.tracker.permissions.AppPermissions
import dev.argus.tracker.worker.ScanSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class WifiDirectScanner(
    private val context: Context
) : SignalScanner {

    private var lastSkipLogEpochMs: Long = 0L

    @SuppressLint("MissingPermission")
    override suspend fun scanOnce(): List<Encounter> {
        if (!ScanSettings.isWifiSensorEnabled(context)) return emptyList()
        if (!hasPermissions()) {
            logSkipped("Missing Wi-Fi Direct permissions")
            return emptyList()
        }

        val wifiEnabled = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.isWifiEnabled == true
        if (!wifiEnabled) {
            logSkipped("Wi-Fi adapter disabled")
            return emptyList()
        }

        val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            ?: return emptyList()
        val channel = runCatching {
            manager.initialize(context, Looper.getMainLooper(), null)
        }.getOrNull() ?: return emptyList()

        val location = LocationSnapshotProvider.read(context)

        return suspendCancellableCoroutine { continuation ->
            val done = AtomicBoolean(false)
            val handler = Handler(Looper.getMainLooper())

            fun finishWith(list: List<Encounter>) {
                if (done.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(list)
                }
            }

            val collectPeers: () -> Unit = {
                runCatching {
                    manager.requestPeers(channel) { peerList ->
                        val now = System.currentTimeMillis()
                        val encounters = peerList.deviceList.orEmpty().map { peer ->
                            peerToEncounter(peer, now, location)
                        }
                        finishWith(encounters)
                    }
                }.onFailure { finishWith(emptyList()) }
            }

            continuation.invokeOnCancellation {
                if (done.compareAndSet(false, true)) {
                    handler.removeCallbacksAndMessages(null)
                }
            }

            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    handler.postDelayed({ collectPeers() }, 1500L)
                }

                override fun onFailure(reason: Int) {
                    collectPeers()
                }
            })

            handler.postDelayed({ finishWith(emptyList()) }, 4000L)
        }
    }

    private fun peerToEncounter(peer: WifiP2pDevice, now: Long, location: DetectionLocation?): Encounter {
        val status = when (peer.status) {
            WifiP2pDevice.AVAILABLE -> "available"
            WifiP2pDevice.INVITED -> "invited"
            WifiP2pDevice.CONNECTED -> "connected"
            WifiP2pDevice.FAILED -> "failed"
            WifiP2pDevice.UNAVAILABLE -> "unavailable"
            else -> "unknown"
        }

        val role = classifyPeerType(peer.deviceName)
        return Encounter(
            timestampEpochMs = now,
            source = EncounterSource.WIFI_DIRECT,
            primaryId = peer.deviceAddress ?: "unknown-wifi-direct",
            secondaryId = peer.deviceName,
            rssiDbm = null,
            frequencyMhz = null,
            lat = location?.lat,
            lon = location?.lon,
            rawPayloadJson = JSONObject()
                .put("deviceAddress", peer.deviceAddress)
                .put("deviceName", peer.deviceName)
                .put("status", status)
                .put("primaryType", peer.primaryDeviceType)
                .put("secondaryType", peer.secondaryDeviceType)
                .put("wpsPbcSupported", peer.wpsPbcSupported())
                .put("wpsDisplaySupported", peer.wpsDisplaySupported())
                .put("wpsKeypadSupported", peer.wpsKeypadSupported())
                .put("deviceRoleHint", role)
                .toString()
        )
    }

    private fun classifyPeerType(name: String?): String {
        val normalized = name.orEmpty().lowercase()
        return when {
            "camera" in normalized || "cam" in normalized -> "camera"
            "tv" in normalized || "display" in normalized -> "display"
            "printer" in normalized -> "printer"
            "drone" in normalized || "uav" in normalized -> "drone"
            "phone" in normalized || "pixel" in normalized || "galaxy" in normalized -> "phone"
            "laptop" in normalized || "pc" in normalized -> "computer"
            else -> "unknown"
        }
    }

    private fun hasPermissions(): Boolean {
        return AppPermissions.hasWifiDirectPermissions(context)
    }

    private fun logSkipped(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastSkipLogEpochMs < 60_000L) return
        lastSkipLogEpochMs = now
        OperationalErrorLogStore.append(
            context = context,
            category = "SCAN_SOURCE",
            source = "wifi_direct",
            message = "Wi-Fi Direct scanner skipped: $reason"
        )
    }
}
