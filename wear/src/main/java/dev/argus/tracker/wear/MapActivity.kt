package dev.argus.tracker.wear

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions

private data class MapPoint(
    val label: String,
    val lat: Double,
    val lon: Double,
    val timestampEpochMs: Long
)

class MapActivity : Activity() {
    private var mapView: MapView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val labels = intent.getStringArrayListExtra(EXTRA_POINT_LABELS).orEmpty()
        val lats = intent.getDoubleArrayExtra(EXTRA_POINT_LATS) ?: DoubleArray(0)
        val lons = intent.getDoubleArrayExtra(EXTRA_POINT_LONS) ?: DoubleArray(0)
        val timestamps = intent.getLongArrayExtra(EXTRA_POINT_TIMESTAMPS) ?: LongArray(0)

        val count = minOf(labels.size, lats.size, lons.size, timestamps.size)
        val points = buildList {
            for (i in 0 until count) {
                val lat = lats[i]
                val lon = lons[i]
                if (!lat.isFinite() || !lon.isFinite()) continue
                add(
                    MapPoint(
                        label = labels[i],
                        lat = lat,
                        lon = lon,
                        timestampEpochMs = timestamps[i]
                    )
                )
            }
        }

        if (points.isEmpty()) {
            val fallback = TextView(this).apply {
                text = "No device points available yet."
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(20, 20, 20, 20)
            }
            setContentView(
                fallback,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            return
        }

        MapsInitializer.initialize(this)
        mapView = MapView(this).apply {
            onCreate(savedInstanceState)
            getMapAsync { googleMap ->
                googleMap.uiSettings.isZoomControlsEnabled = false
                googleMap.uiSettings.isCompassEnabled = true
                googleMap.uiSettings.isMapToolbarEnabled = false

                val newestTimestamp = points.maxOf { it.timestampEpochMs }
                points.take(60).forEach { point ->
                    val markerHue = if (point.timestampEpochMs == newestTimestamp) {
                        BitmapDescriptorFactory.HUE_GREEN
                    } else {
                        BitmapDescriptorFactory.HUE_AZURE
                    }
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(point.lat, point.lon))
                            .title(point.label)
                            .icon(BitmapDescriptorFactory.defaultMarker(markerHue))
                    )
                }

                if (points.size == 1) {
                    val only = points.first()
                    googleMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(only.lat, only.lon), 15f)
                    )
                } else {
                    val boundsBuilder = LatLngBounds.Builder()
                    points.forEach { point -> boundsBuilder.include(LatLng(point.lat, point.lon)) }
                    val bounds = boundsBuilder.build()
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 32))
                }
            }
        }

        setContentView(
            mapView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onStop() {
        mapView?.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        mapView?.onDestroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_POINT_LABELS = "point_labels"
        const val EXTRA_POINT_LATS = "point_lats"
        const val EXTRA_POINT_LONS = "point_lons"
        const val EXTRA_POINT_TIMESTAMPS = "point_timestamps"
    }
}
