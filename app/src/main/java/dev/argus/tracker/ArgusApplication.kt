package dev.argus.tracker

import android.app.Application
import dev.argus.tracker.data.AppContainer
import dev.argus.tracker.data.DefaultAppContainer
import dev.argus.tracker.data.OperationalErrorLogStore
import dev.argus.tracker.data.chain.MeshForegroundServiceController
import dev.argus.tracker.sensing.RemoteIdForegroundServiceController
import dev.argus.tracker.worker.WorkScheduler
import java.io.PrintWriter
import java.io.StringWriter

class ArgusApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        installCrashLoggingHandler()
        container = DefaultAppContainer(this)
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
}
