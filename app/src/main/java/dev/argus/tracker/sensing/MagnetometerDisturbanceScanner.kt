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
import kotlin.math.abs
import kotlin.math.sqrt

class MagnetometerDisturbanceScanner(
    private val context: Context
) : SignalScanner {

    override suspend fun scanOnce(): List<Encounter> {
        if (!ScanSettings.isForeignDirectMagneticEnabled(context)) return emptyList()

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return emptyList()
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            ?: return emptyList()

        val sample = readSingleSample(sensorManager, sensor) ?: return emptyList()
        val location = LocationSnapshotProvider.read(context)

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
            .put("accuracy", sample.accuracy)

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

    private suspend fun readSingleSample(
        sensorManager: SensorManager,
        sensor: Sensor
    ): MagneticSample? = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        var completed = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (completed) return
                if (event.values.size < 3) return

                completed = true
                sensorManager.unregisterListener(this)

                val x = event.values[0].toDouble()
                val y = event.values[1].toDouble()
                val z = event.values[2].toDouble()
                val magnitude = sqrt(x * x + y * y + z * z)
                if (!magnitude.isFinite()) {
                    continuation.resume(null)
                    return
                }

                continuation.resume(
                    MagneticSample(
                        x = x,
                        y = y,
                        z = z,
                        magnitudeMicroTesla = magnitude,
                        accuracy = event.accuracy
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = sensorManager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            handler
        )

        if (!registered) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val timeoutRunnable = Runnable {
            if (completed) return@Runnable
            completed = true
            sensorManager.unregisterListener(listener)
            continuation.resume(null)
        }

        handler.postDelayed(timeoutRunnable, 1200L)

        continuation.invokeOnCancellation {
            sensorManager.unregisterListener(listener)
            handler.removeCallbacks(timeoutRunnable)
        }
    }

    private data class MagneticSample(
        val x: Double,
        val y: Double,
        val z: Double,
        val magnitudeMicroTesla: Double,
        val accuracy: Int
    )
}
