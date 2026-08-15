package dev.argus.tracker.data

import android.content.Context
import androidx.room.Room
import dev.argus.tracker.data.chain.ChainLinkCoordinator
import dev.argus.tracker.data.chain.LocalMeshChainLinkCoordinator
import dev.argus.tracker.data.db.ArgusDatabase
import dev.argus.tracker.sensing.ArgusSensingService
import dev.argus.tracker.sensing.AcousticSignatureScanner
import dev.argus.tracker.sensing.AviationScanner
import dev.argus.tracker.sensing.BleScanner
import dev.argus.tracker.sensing.BluetoothClassicScanner
import dev.argus.tracker.sensing.CameraScanner
import dev.argus.tracker.sensing.CellularScanner
import dev.argus.tracker.sensing.ExternalFeedScanner
import dev.argus.tracker.sensing.MagnetometerDisturbanceScanner
import dev.argus.tracker.sensing.NfcScanner
import dev.argus.tracker.sensing.RemoteIdScanner
import dev.argus.tracker.sensing.WifiScanner
import dev.argus.tracker.sensing.WifiDirectScanner
import dev.argus.tracker.domain.EncounterSource

interface AppContainer {
    val repository: EncounterRepository
    val sensingService: ArgusSensingService
    val chainLinkCoordinator: ChainLinkCoordinator
}

class DefaultAppContainer(
    private val context: Context
) : AppContainer {
    private val db: ArgusDatabase by lazy {
        Room.databaseBuilder(
            context,
            ArgusDatabase::class.java,
            "argus.db"
        )
            .addMigrations(ArgusDatabase.MIGRATION_1_2)
            .addMigrations(ArgusDatabase.MIGRATION_2_3)
            .build()
    }

    override val repository: EncounterRepository by lazy {
        RoomEncounterRepository(db.encounterDao())
    }

    override val chainLinkCoordinator: ChainLinkCoordinator by lazy {
        LocalMeshChainLinkCoordinator(context, repository)
    }

    override val sensingService: ArgusSensingService by lazy {
        ArgusSensingService(
            context = context,
            scanners = listOf(
                WifiScanner(context),
                WifiDirectScanner(context),
                BleScanner(context),
                BluetoothClassicScanner(context),
                NfcScanner(context),
                CellularScanner(context),
                RemoteIdScanner(context),
                CameraScanner(context),
                AviationScanner(context),
                ExternalFeedScanner(context, EncounterSource.UWB, "uwb"),
                ExternalFeedScanner(context, EncounterSource.SDR, "sdr"),
                AcousticSignatureScanner(context),
                MagnetometerDisturbanceScanner(context)
            )
        )
    }
}
