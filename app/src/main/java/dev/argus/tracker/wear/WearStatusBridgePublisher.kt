package dev.argus.tracker.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

data class WearDevicePoint(
    val label: String,
    val lat: Double,
    val lon: Double,
    val timestampEpochMs: Long
)

object WearStatusBridgePublisher {
    private const val TAG = "WearStatusBridge"
    private const val STATUS_PATH = "/argus/status"

    fun publishStatus(
        context: Context,
        peersTotal: Int,
        peersConnected: Int,
        lastAlertMessage: String,
        lastAlertEpochMs: Long?,
        devicePoints: List<WearDevicePoint>
    ) {
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
            dataMap.putLong("updatedAtEpochMs", System.currentTimeMillis())
        }

        val request = dataMapRequest.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context.applicationContext)
            .putDataItem(request)
            .addOnFailureListener { error ->
                Log.w(TAG, "Failed to publish watch status", error)
            }
    }
}
