package dev.argus.tracker.sensing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import dev.argus.tracker.worker.ScanSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.math.sqrt

class MagnetometerDisturbanceScanner(
    private val context: Context
) : SignalScanner {

    private companion object {
        private const val BURST_WINDOW_MS = 320L
        private const val REGISTRATION_TIMEOUT_MS = 1200L
    }

    override suspend fun scanOnce(): List<Encounter> {
        if (!ScanSettings.isForeignDirectMagneticEnabled(context)) return emptyList()

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return emptyList()
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            ?: return emptyList()

        val sample = readBurstPeakSample(sensorManager, sensor) ?: return emptyList()
        val location = LocationSnapshotProvider.read(context)
        if (!LocationSnapshotProvider.isHighAccuracyFix(context, location)) return emptyList()

        val earthBaselineMicroTesla = 50.0
        val deltaMicroTesla = sample.magnitudeMicroTesla - earthBaselineMicroTesla

        val payload = JSONObject()
            .put("signalChannel", "magnetic")
            .put("directChannel", true)
            .put("xMicroTesla", sample.x)
            .put("yMicroTesla", sample.y)
            .put("zMicroTesla", sample.z)
            .put("magnitudeMicroTesla", sample.magnitudeMicroTesla)
            .put("deltaFromEarthBaselineMicroTesla", deltaMicroTesla)
            .put("sampleCount", sample.sampleCount)
            .put("accuracy", sample.accuracy)
            .put("locationAccuracyMeters", location?.accuracyMeters)
            .put("locationProvider", location?.provider)
            .put("locationFixEpochMs", location?.fixEpochMs)

        return listOf(
            Encounter(
                timestampEpochMs = System.currentTimeMillis(),
                source = EncounterSource.UNKNOWN_RF,
                primaryId = "magnetic:ambient",
                secondaryId = "direct-magnetic",
                rssiDbm = null,
                frequencyMhz = null,
                lat = location?.lat,
                lon = location?.lon,
                rawPayloadJson = payload.toString()
            )
        )
    }

    private suspend fun readBurstPeakSample(
        sensorManager: SensorManager,
        sensor: Sensor
    ): MagneticSample? = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        var completed = false
        var sampleCount = 0
        var latestSample: MagneticSample? = null
        var peakSample: MagneticSample? = null
        lateinit var listener: SensorEventListener

        fun finishWith(sample: MagneticSample?) {
            if (completed) return
            completed = true
            sensorManager.unregisterListener(listener)
            continuation.resume(sample)
        }

        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (completed) return
                if (event.values.size < 3) return

                val x = event.values[0].toDouble()
                val y = event.values[1].toDouble()
                val z = event.values[2].toDouble()
                val magnitude = sqrt(x * x + y * y + z * z)
                if (!magnitude.isFinite()) {
                    return
                }

                sampleCount += 1
                val sample = MagneticSample(
                    x = x,
                    y = y,
                    z = z,
                    magnitudeMicroTesla = magnitude,
                    accuracy = event.accuracy,
                    sampleCount = sampleCount
                )
                latestSample = sample
                val currentPeak = peakSample
                if (currentPeak == null || sample.magnitudeMicroTesla > currentPeak.magnitudeMicroTesla) {
                    peakSample = sample
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

        val finishRunnable = Runnable {
            finishWith(peakSample ?: latestSample)
        }

        val timeoutRunnable = Runnable {
            finishWith(null)
        }

        handler.postDelayed(finishRunnable, BURST_WINDOW_MS)
        handler.postDelayed(timeoutRunnable, REGISTRATION_TIMEOUT_MS)

        continuation.invokeOnCancellation {
            sensorManager.unregisterListener(listener)
            handler.removeCallbacks(finishRunnable)
            handler.removeCallbacks(timeoutRunnable)
        }
    }

    private data class MagneticSample(
        val x: Double,
        val y: Double,
        val z: Double,
        val magnitudeMicroTesla: Double,
        val accuracy: Int,
        val sampleCount: Int
    )
}
