package dev.argus.tracker.sensing

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.argus.tracker.permissions.AppPermissions
import dev.argus.tracker.worker.ScanSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

data class DetectionLocation(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float? = null,
    val provider: String? = null,
    val fixEpochMs: Long? = null
)

object LocationSnapshotProvider {
    private const val MAX_STALE_AGE_MS = 2 * 60 * 60 * 1000L
    private const val OBSERVED_CANDIDATE_MAX_AGE_MS = 30_000L
    private const val REQUEST_MIN_DISTANCE_METERS = 4f
    private const val MIN_EMIT_MOVEMENT_METERS = 6f
    private const val SIGNIFICANT_ACCURACY_GAIN_METERS = 8f
    fun isHighAccuracyFix(
        context: Context,
        location: DetectionLocation?,
        thresholdMeters: Double = ScanSettings.getMagneticGpsAccuracyRequirementMeters(context)
    ): Boolean {
        val accuracyMeters = location?.accuracyMeters ?: return false
        val threshold = thresholdMeters
            .coerceIn(
                ScanSettings.MIN_MAGNETIC_GPS_ACCURACY_REQUIREMENT_METERS,
                ScanSettings.MAX_MAGNETIC_GPS_ACCURACY_REQUIREMENT_METERS
            )
            .toFloat()
        return accuracyMeters in 0f..threshold
    }

    @SuppressLint("MissingPermission")
    fun read(context: Context): DetectionLocation? {
        val hasFine = AppPermissions.hasFineLocationPermission(context)
        val hasCoarse = AppPermissions.hasCoarseLocationPermission(context)
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

        return DetectionLocation(
            lat = latest.latitude,
            lon = latest.longitude,
            accuracyMeters = latest.accuracy.takeIf { latest.hasAccuracy() },
            provider = latest.provider,
            fixEpochMs = latest.time.takeIf { it > 0L }
        )
    }

    @SuppressLint("MissingPermission")
    fun observe(context: Context, minUpdateIntervalMs: Long = 5_000L): Flow<DetectionLocation?> = callbackFlow {
        val hasFine = AppPermissions.hasFineLocationPermission(context)
        val hasCoarse = AppPermissions.hasCoarseLocationPermission(context)
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
        val recentLocationsByProvider = linkedMapOf<String, Location>()
        var lastEmittedLocation: Location? = null

        fun shouldEmit(candidate: Location): Boolean {
            val previous = lastEmittedLocation ?: return true
            val movedMeters = previous.distanceTo(candidate)
            if (movedMeters >= MIN_EMIT_MOVEMENT_METERS) {
                return true
            }

            val previousAccuracy = if (previous.hasAccuracy()) previous.accuracy else Float.MAX_VALUE
            val candidateAccuracy = if (candidate.hasAccuracy()) candidate.accuracy else Float.MAX_VALUE
            return (previousAccuracy - candidateAccuracy) >= SIGNIFICANT_ACCURACY_GAIN_METERS
        }

        fun emitBestCandidate(nowEpochMs: Long) {
            val bestCandidate = recentLocationsByProvider
                .values
                .filter { location -> nowEpochMs - location.time in 0..OBSERVED_CANDIDATE_MAX_AGE_MS }
                .maxByOrNull { location ->
                    locationScore(
                        location = location,
                        nowEpochMs = nowEpochMs,
                        hasFinePermission = hasFine
                    )
                }
                ?: return

            if (shouldEmit(bestCandidate)) {
                lastEmittedLocation = bestCandidate
                trySend(
                    DetectionLocation(
                        lat = bestCandidate.latitude,
                        lon = bestCandidate.longitude,
                        accuracyMeters = bestCandidate.accuracy.takeIf { bestCandidate.hasAccuracy() },
                        provider = bestCandidate.provider,
                        fixEpochMs = bestCandidate.time.takeIf { it > 0L }
                    )
                )
            }
        }

        val listener = LocationListener { location ->
            if (location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0) {
                val provider = location.provider ?: "unknown"
                recentLocationsByProvider[provider] = location
                emitBestCandidate(System.currentTimeMillis())
            }
        }

        providers.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    safeIntervalMs,
                    REQUEST_MIN_DISTANCE_METERS,
                    listener,
                    Looper.getMainLooper()
                )
            }
        }

        val initial = read(context)
        if (initial != null) {
            lastEmittedLocation = Location("snapshot").apply {
                latitude = initial.lat
                longitude = initial.lon
            }
        }
        trySend(initial)

        awaitClose {
            runCatching { locationManager.removeUpdates(listener) }
        }
    }.conflate()

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
