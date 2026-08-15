package dev.argus.tracker.sensing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.argus.tracker.ArgusApplication
import dev.argus.tracker.data.DefaultAppContainer
import dev.argus.tracker.worker.ScanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RemoteIdForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !RemoteIdForegroundServiceController.shouldRun(this)) {
            RemoteIdForegroundServiceController.setActive(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        RemoteIdForegroundServiceController.setActive(this, true)
        ensureLoopRunning()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        RemoteIdForegroundServiceController.setActive(this, false)
        loopJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureLoopRunning() {
        if (loopJob?.isActive == true) return

        loopJob = serviceScope.launch {
            val repository = (applicationContext as? ArgusApplication)?.container?.repository
                ?: DefaultAppContainer(applicationContext).repository
            val remoteIdScanner = RemoteIdScanner(applicationContext)

            while (isActive && RemoteIdForegroundServiceController.shouldRun(applicationContext)) {
                val startedAt = System.currentTimeMillis()

                val feedResults = runCatching { remoteIdScanner.scanOnce() }
                    .getOrDefault(emptyList())

                if (feedResults.isNotEmpty()) {
                    runCatching { repository.insertBatch(feedResults) }
                }

                val durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                ScanSettings.recordSourceScanDurationMs(applicationContext, "remote_id", durationMs)
                ScanSettings.setSourceLastScanEpochMs(applicationContext, "remote_id", System.currentTimeMillis())

                val intervalMs = ScanSettings
                    .getSourceScanIntervalSeconds(applicationContext, "remote_id")
                    .coerceAtLeast(1L) * 1000L
                delay(intervalMs)
            }
        }
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Argus Remote ID")
            .setContentText("High-cadence Remote ID collection is active")
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
            "Remote ID Collection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Remote ID collection active while Argus is backgrounded"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "argus_remote_id_foreground"
        private const val NOTIFICATION_ID = 22002
        private const val ACTION_STOP = "dev.argus.tracker.remote_id.STOP"
    }
}

object RemoteIdForegroundServiceController {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_REMOTE_ID_FOREGROUND_ACTIVE = "remote_id_foreground_active"

    fun shouldRun(context: Context): Boolean =
        ScanSettings.isTrackingEnabled(context) &&
            ScanSettings.isRemoteIdSensorEnabled(context)

    fun isActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMOTE_ID_FOREGROUND_ACTIVE, false)

    internal fun setActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REMOTE_ID_FOREGROUND_ACTIVE, active)
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
        val intent = Intent(context, RemoteIdForegroundService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        setActive(context, false)
        context.stopService(Intent(context, RemoteIdForegroundService::class.java))
    }
}
