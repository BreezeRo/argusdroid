package dev.argus.tracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.argus.tracker.ArgusApplication

class ArgusWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ArgusApplication ?: return Result.failure()
        val sensingService = app.container.sensingService
        val repository = app.container.repository

        val batch = sensingService.collectBatch()
        repository.insertBatch(batch)
        return Result.success()
    }
}
