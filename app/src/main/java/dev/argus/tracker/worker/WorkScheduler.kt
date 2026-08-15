package dev.argus.tracker.worker

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.argus.tracker.sensing.AcousticRealtimeForegroundServiceController
import dev.argus.tracker.sensing.RemoteIdForegroundServiceController
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val PERIODIC_WORK_NAME = "argus-periodic-scan"
    private const val ONE_TIME_WORK_NAME = "argus-one-time-scan"
    private const val STARTUP_BOOTSTRAP_WORK_NAME = "argus-startup-bootstrap-scan"

    data class StartResult(
        val success: Boolean,
        val message: String
    )

    fun start(context: Context) {
        ScanSettings.setTrackingEnabled(context, true)
        enqueueAccordingToInterval(context)
        RemoteIdForegroundServiceController.ensureState(context)
        AcousticRealtimeForegroundServiceController.ensureState(context)
    }

    fun enqueueStartupBootstrapScan(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            STARTUP_BOOTSTRAP_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ArgusWorker>().build()
        )
    }

    suspend fun startAndVerify(context: Context): StartResult = withContext(Dispatchers.IO) {
        runCatching {
            ScanSettings.setTrackingEnabled(context, true)
            val operation = enqueueAccordingToInterval(context)
            RemoteIdForegroundServiceController.ensureState(context)
            AcousticRealtimeForegroundServiceController.ensureState(context)

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
        RemoteIdForegroundServiceController.ensureState(context)
        AcousticRealtimeForegroundServiceController.ensureState(context)
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
        RemoteIdForegroundServiceController.ensureState(context)
        AcousticRealtimeForegroundServiceController.ensureState(context)
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

    fun observeTrackingActive(context: Context): Flow<Boolean> = callbackFlow {
        val workManager = WorkManager.getInstance(context)
        val periodicLiveData = workManager.getWorkInfosForUniqueWorkLiveData(PERIODIC_WORK_NAME)
        val oneTimeLiveData = workManager.getWorkInfosForUniqueWorkLiveData(ONE_TIME_WORK_NAME)

        var periodicInfos: List<WorkInfo> = periodicLiveData.value.orEmpty()
        var oneTimeInfos: List<WorkInfo> = oneTimeLiveData.value.orEmpty()

        fun emitTrackingState() {
            val isActive = (periodicInfos + oneTimeInfos).any { info ->
                info.state == WorkInfo.State.ENQUEUED ||
                    info.state == WorkInfo.State.RUNNING ||
                    info.state == WorkInfo.State.BLOCKED
            }
            trySend(isActive)
        }

        val periodicObserver = Observer<List<WorkInfo>> { infos ->
            periodicInfos = infos.orEmpty()
            emitTrackingState()
        }
        val oneTimeObserver = Observer<List<WorkInfo>> { infos ->
            oneTimeInfos = infos.orEmpty()
            emitTrackingState()
        }

        periodicLiveData.observeForever(periodicObserver)
        oneTimeLiveData.observeForever(oneTimeObserver)
        emitTrackingState()

        awaitClose {
            periodicLiveData.removeObserver(periodicObserver)
            oneTimeLiveData.removeObserver(oneTimeObserver)
        }
    }.conflate()

    fun observeStartupBootstrapScanCompleted(context: Context): Flow<Boolean> = callbackFlow {
        val workManager = WorkManager.getInstance(context)
        val startupLiveData = workManager.getWorkInfosForUniqueWorkLiveData(STARTUP_BOOTSTRAP_WORK_NAME)

        fun isCompleted(infos: List<WorkInfo>): Boolean {
            if (infos.isEmpty()) return false
            return infos.any { info ->
                info.state == WorkInfo.State.SUCCEEDED ||
                    info.state == WorkInfo.State.FAILED ||
                    info.state == WorkInfo.State.CANCELLED
            }
        }

        val observer = Observer<List<WorkInfo>> { infos ->
            trySend(isCompleted(infos.orEmpty()))
        }

        startupLiveData.observeForever(observer)
        trySend(isCompleted(startupLiveData.value.orEmpty()))

        awaitClose {
            startupLiveData.removeObserver(observer)
        }
    }.conflate()
}
