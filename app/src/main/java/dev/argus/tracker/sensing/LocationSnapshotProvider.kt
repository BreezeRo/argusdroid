package dev.argus.tracker.sensing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

data class DetectionLocation(
    val lat: Double,
    val lon: Double
)

object LocationSnapshotProvider {
    private const val MAX_STALE_AGE_MS = 2 * 60 * 60 * 1000L

    fun read(context: Context): DetectionLocation? {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
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

    private data class ScoredLocation(
        val location: Location,
        val score: Int
    )

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
