package dev.argus.tracker.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val WORK_NAME = "argus-periodic-scan"

    data class StartResult(
        val success: Boolean,
        val message: String
    )

    fun start(context: Context) {
        val request = buildPeriodicRequest(context)
        val workManager = WorkManager.getInstance(context)

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun startAndVerify(context: Context): StartResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = buildPeriodicRequest(context)
            val workManager = WorkManager.getInstance(context)

            val operation = workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )

            // Wait for enqueue operation completion before checking active state.
            operation.result.get()

            // WorkManager DB updates can lag briefly after operation completion.
            var latestInfos: List<WorkInfo> = emptyList()
            var hasActiveOrPending = false
            repeat(10) {
                latestInfos = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(WORK_NAME)
                    .get()

                hasActiveOrPending = latestInfos.any { info ->
                    info.state == WorkInfo.State.ENQUEUED ||
                        info.state == WorkInfo.State.RUNNING ||
                        info.state == WorkInfo.State.BLOCKED
                }
                if (hasActiveOrPending) {
                    val intervalMinutes = ScanSettings.getScanIntervalMinutes(context)
                    return@withContext StartResult(true, "Tracking started successfully (${intervalMinutes} min interval).")
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
        val intervalMinutes = ScanSettings.getScanIntervalMinutes(context)
        return PeriodicWorkRequestBuilder<ArgusWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    suspend fun isTrackingActive(context: Context): Boolean = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WORK_NAME)
            .get()
            .any { info ->
                info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING
            }
    }
}
