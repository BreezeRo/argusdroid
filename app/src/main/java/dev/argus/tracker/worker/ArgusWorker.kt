package dev.argus.tracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.argus.tracker.ArgusApplication
import dev.argus.tracker.data.DefaultAppContainer

class ArgusWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? ArgusApplication)?.container
            ?: DefaultAppContainer(applicationContext)
        val sensingService = container.sensingService
        val repository = container.repository
        val chainLinkCoordinator = container.chainLinkCoordinator

        val batch = runCatching { sensingService.collectBatch() }.getOrDefault(emptyList())
        runCatching { repository.insertBatch(batch) }
        runCatching { chainLinkCoordinator.syncNow() }
        runCatching { WorkScheduler.scheduleNextIfNeeded(applicationContext) }
        return Result.success()
    }
}
