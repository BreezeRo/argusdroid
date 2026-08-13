package dev.argus.tracker.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val PERIODIC_WORK_NAME = "argus-periodic-scan"
    private const val ONE_TIME_WORK_NAME = "argus-one-time-scan"

    data class StartResult(
        val success: Boolean,
        val message: String
    )

    fun start(context: Context) {
        ScanSettings.setTrackingEnabled(context, true)
        enqueueAccordingToInterval(context)
    }

    suspend fun startAndVerify(context: Context): StartResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = buildPeriodicRequest(context)
            ScanSettings.setTrackingEnabled(context, true)
            val operation = enqueueAccordingToInterval(context)

            // Wait for enqueue operation completion before checking active state.
            operation.result.get()

            // WorkManager DB updates can lag briefly after operation completion.
            var latestInfos: List<WorkInfo> = emptyList()
            var hasActiveOrPending = false
            repeat(10) {
                latestInfos = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(PERIODIC_WORK_NAME)
                    .get() + WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(ONE_TIME_WORK_NAME)
                    .get()

                hasActiveOrPending = latestInfos.any { info ->
                    info.state == WorkInfo.State.ENQUEUED ||
                        info.state == WorkInfo.State.RUNNING ||
                        info.state == WorkInfo.State.BLOCKED
                }
                if (hasActiveOrPending) {
                    val intervalSeconds = ScanSettings.getScanIntervalSeconds(context)
                    return@withContext StartResult(
                        true,
                        "Tracking started successfully (${ScanSettings.formatInterval(intervalSeconds)} interval)."
                    )
                }
                delay(200)
            }

            val states = if (latestInfos.isEmpty()) "none" else latestInfos.joinToString { it.state.name }
            StartResult(false, "Failed to start tracking. Observed WorkManager states: $states")
        }.getOrElse { error ->
            StartResult(false, "Failed to start tracking: ${error.message ?: "unknown error"}")
        }
    }

    private fun buildPeriodicRequest(context: Context): androidx.work.PeriodicWorkRequest {
        val intervalSeconds = ScanSettings.getScanIntervalSeconds(context)
        return PeriodicWorkRequestBuilder<ArgusWorker>(intervalSeconds, TimeUnit.SECONDS)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
    }

    private fun buildOneTimeRequest(context: Context) =
        OneTimeWorkRequestBuilder<ArgusWorker>()
            .setInitialDelay(ScanSettings.getScanIntervalSeconds(context), TimeUnit.SECONDS)
            .build()

    private fun enqueueAccordingToInterval(context: Context): Operation {
        val workManager = WorkManager.getInstance(context)
        val intervalSeconds = ScanSettings.getScanIntervalSeconds(context)
        return if (intervalSeconds < ScanSettings.MIN_PERIODIC_INTERVAL_SECONDS) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            workManager.enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                buildOneTimeRequest(context)
            )
        } else {
            workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                buildPeriodicRequest(context)
            )
        }
    }

    fun scheduleNextIfNeeded(context: Context) {
        if (!ScanSettings.isTrackingEnabled(context)) return
        val intervalSeconds = ScanSettings.getScanIntervalSeconds(context)
        if (intervalSeconds >= ScanSettings.MIN_PERIODIC_INTERVAL_SECONDS) return

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            buildOneTimeRequest(context)
        )
    }

    fun stop(context: Context) {
        ScanSettings.setTrackingEnabled(context, false)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME_WORK_NAME)
    }

    suspend fun isTrackingActive(context: Context): Boolean = withContext(Dispatchers.IO) {
        val periodicInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(PERIODIC_WORK_NAME)
            .get()
        val oneTimeInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(ONE_TIME_WORK_NAME)
            .get()

        (periodicInfos + oneTimeInfos).any { info ->
                info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING
            }
    }
}
