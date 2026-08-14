package dev.argus.tracker.wear

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.net.Inet4Address
import java.net.NetworkInterface

data class WearDevicePoint(
    val label: String,
    val lat: Double,
    val lon: Double,
    val timestampEpochMs: Long
)

object WearStatusBridgePublisher {
    private const val TAG = "WearStatusBridge"
    private const val STATUS_PATH = "/argus/status"
    private const val DEFAULT_DASHBOARD_PORT = 8091

    fun publishStatus(
        context: Context,
        peersTotal: Int,
        peersConnected: Int,
        lastAlertMessage: String,
        lastAlertEpochMs: Long?,
        devicePoints: List<WearDevicePoint>,
        dashboardMapUrlOverride: String? = null
    ) {
        val dashboardMapUrl = dashboardMapUrlOverride?.takeIf { it.isNotBlank() }
            ?: guessDashboardMapUrl(context)
        val serializedPoints = ArrayList<DataMap>(devicePoints.size)
        devicePoints.forEach { point ->
            serializedPoints += DataMap().apply {
                putString("label", point.label)
                putDouble("lat", point.lat)
                putDouble("lon", point.lon)
                putLong("timestampEpochMs", point.timestampEpochMs)
            }
        }
        val dataMapRequest = PutDataMapRequest.create(STATUS_PATH).apply {
            dataMap.putInt("peersTotal", peersTotal)
            dataMap.putInt("peersConnected", peersConnected)
            dataMap.putString("lastAlertMessage", lastAlertMessage)
            dataMap.putLong("lastAlertEpochMs", lastAlertEpochMs ?: 0L)
            dataMap.putDataMapArrayList("devicePoints", serializedPoints)
            dataMap.putString("dashboardMapUrl", dashboardMapUrl ?: "")
            dataMap.putLong("updatedAtEpochMs", System.currentTimeMillis())
        }

        val request = dataMapRequest.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context.applicationContext)
            .putDataItem(request)
            .addOnFailureListener { error ->
                Log.w(TAG, "Failed to publish watch status", error)
            }
    }

    private fun guessDashboardMapUrl(context: Context): String? {
        val localIp = wifiIpv4(context) ?: networkInterfaceIpv4() ?: return null
        return "http://$localIp:$DEFAULT_DASHBOARD_PORT/"
    }

    private fun wifiIpv4(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        @Suppress("DEPRECATION")
        val address = wifiManager.connectionInfo?.ipAddress ?: 0
        if (address == 0) return null
        return listOf(
            address and 0xff,
            address shr 8 and 0xff,
            address shr 16 and 0xff,
            address shr 24 and 0xff
        ).joinToString(".")
    }

    private fun networkInterfaceIpv4(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .asSequence()
                .flatMap { iface -> iface.inetAddresses.toList().asSequence() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress }
                .firstOrNull { ip -> !ip.isNullOrBlank() && ip != "127.0.0.1" }
        }.getOrNull()
    }
}
