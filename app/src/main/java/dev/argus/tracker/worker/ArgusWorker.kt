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
                .onFailure { error ->
                    OperationalErrorLogStore.append(
                        context = applicationContext,
                        category = "WORKER",
                        source = "SCHEDULER",
                        message = "Failed to schedule next scan while mesh wipe gate was enabled: ${error.message ?: "unknown"}",
                        severity = "WARN"
                    )
                }
            return Result.success()
        }

        val dependencies = runCatching {
            val container = (applicationContext as? ArgusApplication)?.container
                ?: DefaultAppContainer(applicationContext)
            Triple(container.sensingService, container.repository, container.chainLinkCoordinator)
        }.onFailure { error ->
            OperationalErrorLogStore.append(
                context = applicationContext,
                category = "WORKER",
                source = "INIT",
                message = "Failed to initialize worker dependencies: ${error.message ?: "unknown"}",
                severity = "ERROR"
            )
        }.getOrElse {
            runCatching { WorkScheduler.scheduleNextIfNeeded(applicationContext) }
            return Result.retry()
        }
        val sensingService = dependencies.first
        val repository = dependencies.second
        val chainLinkCoordinator = dependencies.third

        val scanResult = runCatching { sensingService.collectBatchWithMetrics() }
            .onFailure { error ->
                OperationalErrorLogStore.append(
                    context = applicationContext,
                    category = "WORKER",
                    source = "SCANNER",
                    message = "Background sensing batch failed: ${error.message ?: "unknown"}",
                    severity = "WARN"
                )
            }
            .getOrDefault(dev.argus.tracker.sensing.ScanBatchResult(emptyList(), emptyMap(), 0L))
        runCatching { repository.insertBatch(scanResult.encounters) }
            .onFailure { error ->
                OperationalErrorLogStore.append(
                    context = applicationContext,
                    category = "WORKER",
                    source = "REPOSITORY",
                    message = "Failed to persist collected encounters: ${error.message ?: "unknown"}",
                    severity = "ERROR"
                )
            }
        runCatching {
            MagneticBackgroundAlertEngine.maybeAlertFromBatch(
                context = applicationContext
            )
        }.onFailure { error ->
            OperationalErrorLogStore.append(
                context = applicationContext,
                category = "ALERT_MONITOR",
                source = "MAGNETIC",
                message = "Background magnetic alert monitor failed: ${error.message ?: "unknown"}",
                severity = "WARN"
            )
        }
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
            .onFailure { error ->
                OperationalErrorLogStore.append(
                    context = applicationContext,
                    category = "WORKER",
                    source = "CHAIN_LINK",
                    message = "Background chain sync failed: ${error.message ?: "unknown"}",
                    severity = "WARN"
                )
            }
        runCatching { WorkScheduler.scheduleNextIfNeeded(applicationContext) }
            .onFailure { error ->
                OperationalErrorLogStore.append(
                    context = applicationContext,
                    category = "WORKER",
                    source = "SCHEDULER",
                    message = "Failed to schedule next scan: ${error.message ?: "unknown"}",
                    severity = "WARN"
                )
            }
        ScanSettings.setLastScanDurationMs(applicationContext, scanResult.totalDurationMs)
        scanResult.sourceDurationsMs.forEach { (sourceType, durationMs) ->
            ScanSettings.recordSourceScanDurationMs(applicationContext, sourceType, durationMs)
        }
        return Result.success()
    }
}
