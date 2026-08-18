package dev.argus.tracker

import android.app.Application
import dev.argus.tracker.data.AppContainer
import dev.argus.tracker.data.AppEncryptionManager
import dev.argus.tracker.data.DefaultAppContainer
import dev.argus.tracker.data.OperationalErrorLogStore
import dev.argus.tracker.data.chain.MeshForegroundServiceController
import dev.argus.tracker.worker.ScanSettings
import dev.argus.tracker.sensing.RemoteIdForegroundServiceController
import dev.argus.tracker.worker.WorkScheduler
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

class ArgusApplication : Application() {
    lateinit var container: AppContainer
    private val postUnlockStartupCompleted = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        installCrashLoggingHandler()
        OperationalErrorLogStore.append(
            context = this,
            category = "STARTUP_STAGE",
            source = "application",
            message = "ArgusApplication onCreate entered",
            severity = "WARNING"
        )
        AppEncryptionManager.initialize(this)
        container = DefaultAppContainer(this)
        if (AppEncryptionManager.requiresLaunchUnlock(this) && !AppEncryptionManager.isSessionUnlocked()) {
            OperationalErrorLogStore.append(
                context = this,
                category = "STARTUP_STAGE",
                source = "application",
                message = "Launch unlock required. Deferred secure startup tasks.",
                severity = "WARNING"
            )
        } else {
            runPostUnlockStartupIfNeeded()
        }
        OperationalErrorLogStore.append(
            context = this,
            category = "STARTUP_STAGE",
            source = "application",
            message = "ArgusApplication onCreate completed",
            severity = "WARNING"
        )
    }

    fun onSecureDataUnlocked() {
        runPostUnlockStartupIfNeeded()
    }

    private fun runPostUnlockStartupIfNeeded() {
        if (!postUnlockStartupCompleted.compareAndSet(false, true)) return
        runLiveModeOnlyStartupResetIfEnabled()
        WorkScheduler.prepareStartupBootstrapOnLaunch(this)
        container.chainLinkCoordinator.ensureServerRunning()
        MeshForegroundServiceController.ensureState(this)
        RemoteIdForegroundServiceController.ensureState(this)
    }

    private fun installCrashLoggingHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stack = StringWriter().use { writer ->
                    PrintWriter(writer).use { printWriter ->
                        throwable.printStackTrace(printWriter)
                    }
                    writer.toString()
                }
                val message = buildString {
                    append("Thread ")
                    append(thread.name)
                    append(": ")
                    append(throwable::class.java.simpleName)
                    append(" - ")
                    append(throwable.message ?: "no message")
                    append("\n")
                    append(stack)
                }

                OperationalErrorLogStore.append(
                    context = this,
                    category = "CRASH",
                    source = thread.name,
                    message = message,
                    severity = "ERROR"
                )
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun runLiveModeOnlyStartupResetIfEnabled() {
        if (!ScanSettings.isLiveModeOnlyEnabled(this)) return

        runBlocking {
            container.repository.clearEncounters()
            container.repository.clearDevices()
        }
        ScanSettings.clearOperationalLogs(this)
        OperationalErrorLogStore.clear(this)

        OperationalErrorLogStore.append(
            context = this,
            category = "LIVE_MODE_ONLY",
            source = "application",
            message = "Cleared historical local data on cold launch.",
            severity = "WARNING"
        )
    }
}
