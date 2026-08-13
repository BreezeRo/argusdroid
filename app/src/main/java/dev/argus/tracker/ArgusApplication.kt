package dev.argus.tracker

import android.app.Application
import dev.argus.tracker.data.AppContainer
import dev.argus.tracker.data.DefaultAppContainer
import dev.argus.tracker.data.chain.MeshForegroundServiceController

class ArgusApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        container.chainLinkCoordinator.ensureServerRunning()
        MeshForegroundServiceController.ensureState(this)
    }
}
