package dev.argus.tracker.data

import android.content.Context
import androidx.room.Room
import dev.argus.tracker.data.db.ArgusDatabase
import dev.argus.tracker.sensing.ArgusSensingService
import dev.argus.tracker.sensing.BleScanner
import dev.argus.tracker.sensing.RemoteIdScanner
import dev.argus.tracker.sensing.WifiScanner

interface AppContainer {
    val repository: EncounterRepository
    val sensingService: ArgusSensingService
}

class DefaultAppContainer(
    private val context: Context
) : AppContainer {
    private val db: ArgusDatabase by lazy {
        Room.databaseBuilder(
            context,
            ArgusDatabase::class.java,
            "argus.db"
        ).build()
    }

    override val repository: EncounterRepository by lazy {
        RoomEncounterRepository(db.encounterDao())
    }

    override val sensingService: ArgusSensingService by lazy {
        ArgusSensingService(
            scanners = listOf(
                WifiScanner(context),
                BleScanner(context),
                RemoteIdScanner()
            )
        )
    }
}
