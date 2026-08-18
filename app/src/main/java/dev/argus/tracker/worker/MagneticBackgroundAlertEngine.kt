package dev.argus.tracker.worker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.coroutines.resume

object MagneticBackgroundAlertEngine {
    private const val PREFS_NAME = "argus_magnetic_background_alerts"
    private const val KEY_LAST_MAGNITUDE_UT = "last_magnitude_ut"
    private const val KEY_LAST_BEEP_EPOCH_MS = "last_beep_epoch_ms"

    private const val MAGNETIC_INCREASE_DELTA_THRESHOLD_UT = 12.0
    private const val MAGNETIC_INCREASE_MIN_CURRENT_UT = 55.0
    private const val MAGNETIC_SUSTAINED_HIGH_THRESHOLD_UT = 72.0
    private const val MAGNETIC_RHYTHM_MIN_BPM = 72
    private const val MAGNETIC_RHYTHM_MAX_BPM = 220
    private const val MIN_BEEP_COOLDOWN_MS = 1200L
    private const val BEEP_PLAY_MS = 1200L

    private const val BURST_WINDOW_MS = 300L
    private const val REGISTRATION_TIMEOUT_MS = 1200L

    suspend fun maybeAlertFromBatch(context: Context) {
        if (!ScanSettings.isMagneticRhythmBeepEnabled(context)) return
        if (!ScanSettings.isForeignDirectMagneticEnabled(context)) return

        val latestMagnitude = readCurrentMagneticMagnitude(context) ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousMagnitude = prefs.getFloat(KEY_LAST_MAGNITUDE_UT, Float.NaN)
            .takeIf { it.isFinite() }
            ?.toDouble()

        prefs.edit().putFloat(KEY_LAST_MAGNITUDE_UT, latestMagnitude.toFloat()).apply()

        val previous = previousMagnitude ?: return
        val deltaMicroTesla = latestMagnitude - previous
        val triggerThresholdMicroTesla = ScanSettings.getMagneticEventTriggerThresholdMicroTesla(context)

        val crossedDisturbanceBand =
            previous < triggerThresholdMicroTesla &&
                latestMagnitude >= triggerThresholdMicroTesla
        val sharpIncrease =
            deltaMicroTesla >= MAGNETIC_INCREASE_DELTA_THRESHOLD_UT &&
                latestMagnitude >= minOf(MAGNETIC_INCREASE_MIN_CURRENT_UT, triggerThresholdMicroTesla)
        val sustainedHighThresholdMicroTesla = max(
            triggerThresholdMicroTesla + 8.0,
            MAGNETIC_SUSTAINED_HIGH_THRESHOLD_UT
        )
        val sustainedHigh = latestMagnitude >= sustainedHighThresholdMicroTesla
        if (!crossedDisturbanceBand && !sharpIncrease && !sustainedHigh) return

        val now = System.currentTimeMillis()
        val lastBeepEpochMs = prefs.getLong(KEY_LAST_BEEP_EPOCH_MS, 0L)
        if (now - lastBeepEpochMs < MIN_BEEP_COOLDOWN_MS) return

        val severity = magneticAlertSeverity(
            currentMagnitudeMicroTesla = latestMagnitude,
            deltaMicroTesla = deltaMicroTesla
        )
        playMagneticRhythm(
            severity = severity,
            playMs = BEEP_PLAY_MS
        )
        prefs.edit().putLong(KEY_LAST_BEEP_EPOCH_MS, now).apply()
    }

    private suspend fun readCurrentMagneticMagnitude(context: Context): Double? {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return null
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            ?: return null

        return suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            var completed = false
            var peakMagnitude: Double? = null
            lateinit var listener: SensorEventListener

            fun finishWith(value: Double?) {
                if (completed) return
                completed = true
                runCatching { sensorManager.unregisterListener(listener) }
                continuation.resume(value)
            }

            listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (completed) return
                    val values = event.values
                    if (values.size < 3) return
                    val x = values[0].toDouble()
                    val y = values[1].toDouble()
                    val z = values[2].toDouble()
                    val magnitude = sqrt(x * x + y * y + z * z)
                    if (!magnitude.isFinite()) return

                    val currentPeak = peakMagnitude
                    if (currentPeak == null || magnitude > currentPeak) {
                        peakMagnitude = magnitude
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            val registered = sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_FASTEST,
                handler
            )
            if (!registered) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val finishRunnable = Runnable { finishWith(peakMagnitude) }
            val timeoutRunnable = Runnable { finishWith(null) }
            handler.postDelayed(finishRunnable, BURST_WINDOW_MS)
            handler.postDelayed(timeoutRunnable, REGISTRATION_TIMEOUT_MS)

            continuation.invokeOnCancellation {
                runCatching { sensorManager.unregisterListener(listener) }
                handler.removeCallbacks(finishRunnable)
                handler.removeCallbacks(timeoutRunnable)
            }
        }
    }

    private fun magneticAlertSeverity(
        currentMagnitudeMicroTesla: Double,
        deltaMicroTesla: Double
    ): Double {
        val magnitudeComponent = ((currentMagnitudeMicroTesla - 65.0) / 35.0)
            .coerceIn(0.0, 1.0)
        val deltaComponent = (deltaMicroTesla / 28.0).coerceIn(0.0, 1.0)
        return max(magnitudeComponent, deltaComponent)
    }

    private suspend fun playMagneticRhythm(severity: Double, playMs: Long) {
        val clampedSeverity = severity.coerceIn(0.0, 1.0)
        val bpmRange = (MAGNETIC_RHYTHM_MAX_BPM - MAGNETIC_RHYTHM_MIN_BPM).toDouble()
        val bpm = (MAGNETIC_RHYTHM_MIN_BPM + bpmRange * clampedSeverity).toInt()
            .coerceIn(MAGNETIC_RHYTHM_MIN_BPM, MAGNETIC_RHYTHM_MAX_BPM)
        val intervalMs = (60_000.0 / bpm.toDouble()).toLong().coerceIn(120L, 900L)
        val toneMs = (intervalMs * 0.38).toInt().coerceIn(70, 220)
        val deadline = SystemClock.elapsedRealtime() + playMs.coerceAtLeast(400L)

        val tone = createMagneticToneGenerator() ?: return
        try {
            while (SystemClock.elapsedRealtime() < deadline) {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, toneMs)
                delay(intervalMs)
            }
        } finally {
            tone.release()
        }
    }

    private fun createMagneticToneGenerator(): ToneGenerator? {
        val candidates = listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_ALARM
        )
        for (stream in candidates) {
            val tone = runCatching { ToneGenerator(stream, 90) }.getOrNull()
            if (tone != null) return tone
        }
        return null
    }
}
