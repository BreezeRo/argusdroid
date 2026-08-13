package dev.argus.tracker.sensing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import dev.argus.tracker.worker.ScanSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AcousticSignatureScanner(
    private val context: Context
) : SignalScanner {

    override suspend fun scanOnce(): List<Encounter> {
        if (!ScanSettings.isForeignDirectAcousticEnabled(context)) return emptyList()
        if (!hasRecordAudioPermission()) return emptyList()

        val sampleRateHz = 8_000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, encoding)
        if (minBuffer <= 0) return emptyList()

        val bufferSize = (minBuffer * 2).coerceAtLeast(2_048)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateHz,
            channelConfig,
            encoding,
            bufferSize
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return emptyList()
        }

        val location = LocationSnapshotProvider.read(context)
        val shortBuffer = ShortArray(1024)
        val captured = mutableListOf<Short>()

        val readResult = withContext(Dispatchers.Default) {
            runCatching {
                recorder.startRecording()
                repeat(4) {
                    val read = recorder.read(shortBuffer, 0, shortBuffer.size)
                    if (read > 0) {
                        for (i in 0 until read) {
                            captured += shortBuffer[i]
                        }
                    }
                }
            }
        }

        runCatching {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        }
        recorder.release()

        if (readResult.isFailure || captured.isEmpty()) return emptyList()

        val rms = sqrt(captured.map { sample -> sample.toDouble() * sample.toDouble() }.average())
        val peak = captured.maxOfOrNull { abs(it.toInt()) }?.toDouble() ?: 0.0
        if (!rms.isFinite() || rms <= 0.0) return emptyList()

        val fullScale = 32768.0
        val rmsDbFs = (20.0 * log10((rms / fullScale).coerceAtLeast(1e-9))).coerceIn(-120.0, 0.0)
        val peakDbFs = (20.0 * log10((peak / fullScale).coerceAtLeast(1e-9))).coerceIn(-120.0, 0.0)

        val payload = JSONObject()
            .put("signalChannel", "acoustic")
            .put("directChannel", true)
            .put("sampleRateHz", sampleRateHz)
            .put("sampleCount", captured.size)
            .put("rmsDbFs", rmsDbFs)
            .put("peakDbFs", peakDbFs)

        return listOf(
            Encounter(
                timestampEpochMs = System.currentTimeMillis(),
                source = EncounterSource.UNKNOWN_RF,
                primaryId = "acoustic:ambient",
                secondaryId = "direct-acoustic",
                rssiDbm = rmsDbFs.roundToInt(),
                frequencyMhz = null,
                lat = location?.lat,
                lon = location?.lon,
                rawPayloadJson = payload.toString()
            )
        )
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
