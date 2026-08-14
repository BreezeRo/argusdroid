package dev.argus.tracker.data.chain

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
import dev.argus.tracker.worker.ScanSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

class MeshForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !MeshForegroundServiceController.shouldRun(this)) {
            MeshForegroundServiceController.setActive(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        MeshForegroundServiceController.setActive(this, true)
        (applicationContext as? ArgusApplication)
            ?.container
            ?.chainLinkCoordinator
            ?.ensureServerRunning()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        MeshForegroundServiceController.setActive(this, false)
        if (!MeshForegroundServiceController.shouldRun(this)) {
            (applicationContext as? ArgusApplication)
                ?.container
                ?.chainLinkCoordinator
                ?.stopServer()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
            .setContentTitle("Argus Mesh Network")
            .setContentText("Persistent channel active for chain-link peers")
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
            "Mesh Network",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the Argus chain-link mesh active while the app is backgrounded"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "argus_mesh_foreground"
        private const val NOTIFICATION_ID = 22001
        private const val ACTION_STOP = "dev.argus.tracker.mesh.STOP"
    }
}

object MeshForegroundServiceController {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_MESH_FOREGROUND_ACTIVE = "mesh_foreground_active"

    fun shouldRun(context: Context): Boolean =
        ScanSettings.isChainLinkEnabled(context) &&
            ScanSettings.isChainPersistentChannelEnabled(context)

    fun isActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MESH_FOREGROUND_ACTIVE, false)

    fun observeActive(context: Context): Flow<Boolean> = callbackFlow {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == KEY_MESH_FOREGROUND_ACTIVE) {
                trySend(sharedPreferences.getBoolean(KEY_MESH_FOREGROUND_ACTIVE, false))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs.getBoolean(KEY_MESH_FOREGROUND_ACTIVE, false))
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.conflate()

    internal fun setActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MESH_FOREGROUND_ACTIVE, active)
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
        val intent = Intent(context, MeshForegroundService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        setActive(context, false)
        context.stopService(Intent(context, MeshForegroundService::class.java))
    }
}
