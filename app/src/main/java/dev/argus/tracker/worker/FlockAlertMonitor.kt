package dev.argus.tracker.worker

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.argus.tracker.permissions.AppPermissions
import dev.argus.tracker.data.EncounterRepository
import dev.argus.tracker.ui.DeviceFlock
import dev.argus.tracker.ui.detectDeviceFlocks
import java.util.Locale

object FlockAlertMonitor {
    private const val CHANNEL_ID = "argus_flock_alerts"
    private const val CHANNEL_NAME = "Flock Alerts"
    private const val CHANNEL_DESCRIPTION = "Alerts when likely related devices are detected traveling together"
    private const val MIN_ANALYSIS_INTERVAL_MS = 2 * 60 * 1000L
    private const val NOTIFICATION_COOLDOWN_MS = 10 * 60 * 1000L
    private const val ANALYSIS_WINDOW_MS = 2 * 60 * 60 * 1000L
    private const val ANALYSIS_MAX_ENCOUNTERS = 2500

    suspend fun evaluateAndNotify(
        context: Context,
        repository: EncounterRepository
    ) {
        if (!ScanSettings.isFlockNotificationsEnabled(context)) return

        val now = System.currentTimeMillis()
        val lastRun = ScanSettings.getFlockMonitorLastRunEpochMs(context)
        if (lastRun > 0L && now - lastRun < MIN_ANALYSIS_INTERVAL_MS) {
            return
        }
        ScanSettings.setFlockMonitorLastRunEpochMs(context, now)

        val since = now - ANALYSIS_WINDOW_MS
        val recent = repository.listSince(sinceEpochMs = since, limit = ANALYSIS_MAX_ENCOUNTERS)
        if (recent.size < 2) return

        val flocks = detectDeviceFlocks(
            encounters = recent,
            minTravelSpanMeters = 10.0
        )
        val topFlock = flocks.firstOrNull() ?: return

        val signature = buildFlockSignature(topFlock)
        if (signature.isBlank()) return

        val lastSignature = ScanSettings.getFlockAlertLastSignature(context)
        val lastNotified = ScanSettings.getFlockAlertLastNotificationEpochMs(context)
        val inCooldown = lastNotified > 0L && now - lastNotified < NOTIFICATION_COOLDOWN_MS
        if (signature == lastSignature || inCooldown) {
            return
        }
        if (!hasPostNotificationsPermission(context)) return

        ensureChannel(context)
        sendNotification(context, topFlock)
        ScanSettings.setFlockAlertLastSignature(context, signature)
        ScanSettings.setFlockAlertLastNotificationEpochMs(context, now)
    }

    private fun buildFlockSignature(flock: DeviceFlock): String {
        return flock.members
            .asSequence()
            .map { member -> "${member.source}|${member.primaryId}" }
            .sorted()
            .joinToString(separator = ",")
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    private fun sendNotification(context: Context, flock: DeviceFlock) {
        val preview = flock.members
            .take(4)
            .joinToString(separator = ", ") { member -> "${member.source}:${member.primaryId}" }
        val overflow = (flock.members.size - 4).coerceAtLeast(0)
        val overflowSuffix = if (overflow > 0) " +$overflow" else ""
        val content = String.format(
            Locale.US,
            "%d devices, %d co-travel events, span %.0f m • %s%s",
            flock.members.size,
            flock.coTravelEventCount,
            flock.travelSpanMeters,
            preview,
            overflowSuffix
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Flock detected")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationId = ("flock:${buildFlockSignature(flock)}").hashCode()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun hasPostNotificationsPermission(context: Context): Boolean {
        return AppPermissions.hasPostNotificationsPermission(context)
    }
}
