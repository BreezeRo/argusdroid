package dev.argus.tracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.argus.tracker.ArgusApplication
import dev.argus.tracker.data.DefaultAppContainer
import dev.argus.tracker.data.OperationalErrorLogStore

class ArgusWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (ScanSettings.isMeshWipeGateEnabled(applicationContext)) {
            runCatching { WorkScheduler.scheduleNextIfNeeded(applicationContext) }
            return Result.success()
        }

        val container = (applicationContext as? ArgusApplication)?.container
            ?: DefaultAppContainer(applicationContext)
        val sensingService = container.sensingService
        val repository = container.repository
        val chainLinkCoordinator = container.chainLinkCoordinator

        val scanResult = runCatching { sensingService.collectBatchWithMetrics() }
            .getOrDefault(dev.argus.tracker.sensing.ScanBatchResult(emptyList(), emptyMap(), 0L))
        runCatching { repository.insertBatch(scanResult.encounters) }
        runCatching {
            FlockAlertMonitor.evaluateAndNotify(
                context = applicationContext,
                repository = repository
            )
        }.onFailure { error ->
            OperationalErrorLogStore.append(
                context = applicationContext,
                category = "ALERT_MONITOR",
                source = "FLOCK",
                message = "Background flock monitor failed: ${error.message ?: "unknown"}",
                severity = "WARN"
            )
        }
        runCatching { chainLinkCoordinator.syncNow() }
        runCatching { WorkScheduler.scheduleNextIfNeeded(applicationContext) }
        ScanSettings.setLastScanDurationMs(applicationContext, scanResult.totalDurationMs)
        scanResult.sourceDurationsMs.forEach { (sourceType, durationMs) ->
            ScanSettings.recordSourceScanDurationMs(applicationContext, sourceType, durationMs)
        }
        return Result.success()
    }
}
