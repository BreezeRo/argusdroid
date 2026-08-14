package dev.argus.tracker.wear

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class WearPoint(
    val label: String,
    val lat: Double,
    val lon: Double,
    val timestampEpochMs: Long
)

class MainActivity : Activity(), DataClient.OnDataChangedListener {
    private lateinit var messageView: TextView
    private lateinit var openMapButton: Button
    private var latestDevicePoints: List<WearPoint> = emptyList()
    private val dataClient by lazy { Wearable.getDataClient(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        val basePad = (12 * density).toInt()
        val maxContentWidth = (190 * density).toInt()

        messageView = TextView(this).apply {
            text = "Argus Wear\n\nWaiting for phone status..."
            textSize = 14f
            setLineSpacing(0f, 1.15f)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        openMapButton = Button(this).apply {
            text = "Open Device Map"
            isEnabled = true
            setOnClickListener {
                val labels = ArrayList<String>(latestDevicePoints.size)
                val latitudes = DoubleArray(latestDevicePoints.size)
                val longitudes = DoubleArray(latestDevicePoints.size)
                val timestamps = LongArray(latestDevicePoints.size)
                latestDevicePoints.forEachIndexed { index, point ->
                    labels += point.label
                    latitudes[index] = point.lat
                    longitudes[index] = point.lon
                    timestamps[index] = point.timestampEpochMs
                }
                val intent = Intent(this@MainActivity, MapActivity::class.java)
                    .putStringArrayListExtra(MapActivity.EXTRA_POINT_LABELS, labels)
                    .putExtra(MapActivity.EXTRA_POINT_LATS, latitudes)
                    .putExtra(MapActivity.EXTRA_POINT_LONS, longitudes)
                    .putExtra(MapActivity.EXTRA_POINT_TIMESTAMPS, timestamps)
                runCatching {
                    startActivity(intent)
                }.onFailure {
                    Toast.makeText(
                        this@MainActivity,
                        "Unable to open device map",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(basePad, basePad, basePad, basePad)
            addView(
                messageView,
                LinearLayout.LayoutParams(
                    maxContentWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                openMapButton,
                LinearLayout.LayoutParams(
                    maxContentWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (10 * density).toInt()
                }
            )
        }

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(
                contentLayout,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { _, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val leftRightSafe = maxOf(systemInsets.left, systemInsets.right, (26 * density).toInt())
            val topSafe = maxOf(systemInsets.top, (14 * density).toInt())
            val bottomSafe = maxOf(systemInsets.bottom, (20 * density).toInt())
            contentLayout.setPadding(leftRightSafe, topSafe, leftRightSafe, bottomSafe)
            insets
        }

        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(this)
        dataClient.dataItems
            .addOnSuccessListener { items ->
                try {
                    for (item in items) {
                        if (item.uri.path == STATUS_PATH) {
                            renderStatus(DataMapItem.fromDataItem(item))
                        }
                    }
                } finally {
                    items.release()
                }
            }
    }

    override fun onPause() {
        dataClient.removeListener(this)
        super.onPause()
    }

    override fun onDataChanged(dataEvents: com.google.android.gms.wearable.DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            if (item.uri.path != STATUS_PATH) continue
            renderStatus(DataMapItem.fromDataItem(item))
        }
    }

    private fun renderStatus(item: DataMapItem) {
        val data = item.dataMap
        val peersTotal = data.getInt("peersTotal", 0)
        val peersConnected = data.getInt("peersConnected", 0)
        val lastAlertMessage = data.getString("lastAlertMessage") ?: "No recent alerts"
        val lastAlertEpochMs = data.getLong("lastAlertEpochMs", 0L)
        val updatedAtEpochMs = data.getLong("updatedAtEpochMs", 0L)
        val pointMaps: List<DataMap> = data.getDataMapArrayList("devicePoints")?.toList().orEmpty()
        latestDevicePoints = pointMaps.mapNotNull { point ->
            val lat = point.getDouble("lat", Double.NaN)
            val lon = point.getDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) return@mapNotNull null
            WearPoint(
                label = point.getString("label") ?: "DEVICE",
                lat = lat,
                lon = lon,
                timestampEpochMs = point.getLong("timestampEpochMs", 0L)
            )
        }
        val updatedAt = formatEpoch(updatedAtEpochMs)
        val lastAlertAt = if (lastAlertEpochMs > 0L) formatEpoch(lastAlertEpochMs) else "n/a"

        messageView.text = buildString {
            append("Argus Wear\n\n")
            append("Mesh peers: ")
            append(peersConnected)
            append("/")
            append(peersTotal)
            append(" connected\n\n")
            append("Last alert: ")
            append(lastAlertMessage)
            append("\n")
            append("Alert time: ")
            append(lastAlertAt)
            append("\n\n")
            append("Updated: ")
            append(updatedAt)
            append("\n\nMap points: ")
            append(latestDevicePoints.size)
        }
    }

    private fun formatEpoch(epochMs: Long): String {
        if (epochMs <= 0L) return "n/a"
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
        return formatter.format(Date(epochMs))
    }

    companion object {
        private const val STATUS_PATH = "/argus/status"
    }
}
