package dev.argus.tracker.sensing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.PackageManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.argus.tracker.ArgusApplication
import dev.argus.tracker.data.DefaultAppContainer
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.worker.ScanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AcousticRealtimeForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !AcousticRealtimeForegroundServiceController.shouldRun(this)) {
            AcousticRealtimeForegroundServiceController.setActive(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        AcousticRealtimeForegroundServiceController.setActive(this, true)
        ensureLoopRunning()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        AcousticRealtimeForegroundServiceController.setActive(this, false)
        loopJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureLoopRunning() {
        if (loopJob?.isActive == true) return

        loopJob = serviceScope.launch {
            val repository = (applicationContext as? ArgusApplication)?.container?.repository
                ?: DefaultAppContainer(applicationContext).repository
            val locationProviderContext = applicationContext

            while (isActive && AcousticRealtimeForegroundServiceController.shouldRun(applicationContext)) {
                val startedAt = System.currentTimeMillis()
                val window = captureAudioWindow()

                if (window != null && ScanSettings.isForeignDirectAcousticGunshotEnabled(applicationContext)) {
                    val encounter = detectExperimentalGunshot(window, locationProviderContext)
                    if (encounter != null) {
                        runCatching { repository.insertBatch(listOf(encounter)) }
                    }
                }

                val durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                ScanSettings.recordSourceScanDurationMs(applicationContext, "acoustic", durationMs)
                ScanSettings.setSourceLastScanEpochMs(applicationContext, "acoustic", System.currentTimeMillis())
                delay(200L)
            }
        }
    }

    private fun captureAudioWindow(): AudioWindow? {
        val sampleRateHz = 16_000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, encoding)
        if (minBuffer <= 0) return null

        val readSamples = 2_048
        val bufferSize = (minBuffer * 2).coerceAtLeast(readSamples * 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateHz,
            channelConfig,
            encoding,
            bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }

        val samples = ShortArray(readSamples)
        val capturedCount = runCatching {
            recorder.startRecording()
            recorder.read(samples, 0, samples.size)
        }.getOrDefault(0)

        runCatching {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        }
        recorder.release()

        if (capturedCount <= 0) return null
        return AudioWindow(samples.copyOf(capturedCount), sampleRateHz)
    }

    private fun detectExperimentalGunshot(window: AudioWindow, context: Context): Encounter? {
        val samples = window.samples
        if (samples.isEmpty()) return null

        val peak = samples.maxOfOrNull { abs(it.toInt()) }?.toDouble() ?: return null
        if (peak <= 0.0) return null

        val rms = sqrt(samples.map { sample -> sample.toDouble() * sample.toDouble() }.average())
        if (!rms.isFinite() || rms <= 0.0) return null

        val fullScale = 32768.0
        val rmsDbFs = (20.0 * log10((rms / fullScale).coerceAtLeast(1e-9))).coerceIn(-120.0, 0.0)
        val peakDbFs = (20.0 * log10((peak / fullScale).coerceAtLeast(1e-9))).coerceIn(-120.0, 0.0)
        val crestFactor = peak / rms

        var zeroCrossings = 0
        for (i in 1 until samples.size) {
            val previous = samples[i - 1]
            val current = samples[i]
            if ((previous >= 0 && current < 0) || (previous < 0 && current >= 0)) {
                zeroCrossings += 1
            }
        }
        val zeroCrossingRate = zeroCrossings.toDouble() / samples.size.toDouble()

        val looksImpulsive = peakDbFs >= -9.0 && crestFactor >= 7.0
        val looksBroadband = zeroCrossingRate in 0.08..0.62
        if (!(looksImpulsive && looksBroadband)) return null

        val location = LocationSnapshotProvider.read(context)
        val payload = JSONObject()
            .put("signalChannel", "acoustic")
            .put("directChannel", true)
            .put("realtimeChannel", true)
            .put("experimental", true)
            .put("eventType", "gunshot_candidate")
            .put("sampleRateHz", window.sampleRateHz)
            .put("sampleCount", samples.size)
            .put("rmsDbFs", rmsDbFs)
            .put("peakDbFs", peakDbFs)
            .put("crestFactor", crestFactor)
            .put("zeroCrossingRate", zeroCrossingRate)

        return Encounter(
            timestampEpochMs = System.currentTimeMillis(),
            source = EncounterSource.UNKNOWN_RF,
            primaryId = "acoustic:gunshot-candidate",
            secondaryId = "direct-acoustic-realtime-gunshot",
            rssiDbm = peakDbFs.roundToInt(),
            frequencyMhz = null,
            lat = location?.lat,
            lon = location?.lon,
            rawPayloadJson = payload.toString()
        )
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Argus Real-time Acoustic")
            .setContentText("Continuous acoustic listening is active")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Real-time Acoustic Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps continuous acoustic listening active while tracking"
        }
        manager.createNotificationChannel(channel)
    }

    private data class AudioWindow(
        val samples: ShortArray,
        val sampleRateHz: Int
    )

    companion object {
        private const val CHANNEL_ID = "argus_realtime_acoustic_foreground"
        private const val NOTIFICATION_ID = 22003
        private const val ACTION_STOP = "dev.argus.tracker.acoustic_realtime.STOP"
    }
}

object AcousticRealtimeForegroundServiceController {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_ACOUSTIC_REALTIME_FOREGROUND_ACTIVE = "acoustic_realtime_foreground_active"

    fun shouldRun(context: Context): Boolean =
        ScanSettings.isTrackingEnabled(context) &&
            ScanSettings.isForeignDirectAcousticEnabled(context) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            ScanSettings.isForeignDirectAcousticRealtimeEnabled(context)

    fun isActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACOUSTIC_REALTIME_FOREGROUND_ACTIVE, false)

    internal fun setActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACOUSTIC_REALTIME_FOREGROUND_ACTIVE, active)
            .apply()
    }

    fun ensureState(context: Context) {
        if (shouldRun(context)) {
            start(context)
        } else {
            stop(context)
        }
    }

    fun start(context: Context) {
        if (!shouldRun(context)) return
        val intent = Intent(context, AcousticRealtimeForegroundService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        setActive(context, false)
        context.stopService(Intent(context, AcousticRealtimeForegroundService::class.java))
    }
}
