package dev.argus.tracker.sensing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

data class DetectionLocation(
    val lat: Double,
    val lon: Double
)

object LocationSnapshotProvider {
    private const val MAX_STALE_AGE_MS = 2 * 60 * 60 * 1000L

    fun read(context: Context): DetectionLocation? {
        val hasFine = hasFineLocationPermission(context)
        val hasCoarse = hasCoarseLocationPermission(context)
        if (!hasFine && !hasCoarse) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = runCatching { locationManager.getProviders(true) }.getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        val candidates = providers
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { location ->
                location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0
            }
            .map { location ->
                ScoredLocation(
                    location = location,
                    score = locationScore(location = location, nowEpochMs = now, hasFinePermission = hasFine)
                )
            }

        val latest = candidates
            .filter { scored -> now - scored.location.time <= MAX_STALE_AGE_MS }
            .maxByOrNull { scored -> scored.score }
            ?.location
            ?: candidates.maxByOrNull { scored -> scored.score }?.location
            ?: return null

        return DetectionLocation(lat = latest.latitude, lon = latest.longitude)
    }

    fun observe(context: Context, minUpdateIntervalMs: Long = 5_000L): Flow<DetectionLocation?> = callbackFlow {
        val hasFine = hasFineLocationPermission(context)
        val hasCoarse = hasCoarseLocationPermission(context)
        if (!hasFine && !hasCoarse) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val providers = runCatching { locationManager.getProviders(true) }.getOrDefault(emptyList())
        if (providers.isEmpty()) {
            trySend(read(context))
            close()
            return@callbackFlow
        }

        val safeIntervalMs = minUpdateIntervalMs.coerceAtLeast(1_000L)
        val listener = LocationListener { location ->
            if (location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0) {
                trySend(DetectionLocation(location.latitude, location.longitude))
            }
        }

        providers.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    safeIntervalMs,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            }
        }

        trySend(read(context))

        awaitClose {
            runCatching { locationManager.removeUpdates(listener) }
        }
    }.conflate()

    private data class ScoredLocation(
        val location: Location,
        val score: Int
    )

    private fun hasFineLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasCoarseLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun locationScore(location: Location, nowEpochMs: Long, hasFinePermission: Boolean): Int {
        val ageMs = (nowEpochMs - location.time).coerceAtLeast(0L)
        val ageSeconds = (ageMs / 1000L).toInt()
        val recencyScore = (3600 - ageSeconds).coerceIn(-1800, 3600)

        val providerScore = when (location.provider?.lowercase()) {
            "gps" -> 1200
            "fused" -> 1150
            "network" -> 800
            "passive" -> 500
            else -> 650
        }

        val accuracyScore = if (location.hasAccuracy()) {
            val accuracyMeters = location.accuracy
            val base = (1500f - (accuracyMeters * 10f)).toInt().coerceIn(-600, 1500)
            val fineBonus = if (hasFinePermission && accuracyMeters <= 35f) 240 else 0
            base + fineBonus
        } else {
            -250
        }

        return recencyScore + providerScore + accuracyScore
    }
}
