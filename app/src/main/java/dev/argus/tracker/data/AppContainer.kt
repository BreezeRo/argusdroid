package dev.argus.tracker.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
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
    companion object {
        private const val TAG = "DefaultAppContainer"
        private const val DB_NAME = "argus.db"
        @Volatile
        private var newSqlCipherLibLoaded = false
        @Volatile
        private var activeDb: ArgusDatabase? = null

        fun forceCloseAndDeleteDatabase(context: Context) {
            runCatching {
                activeDb?.close()
                activeDb = null
            }

            runCatching {
                context.deleteDatabase(DB_NAME)
            }

            listOf(DB_NAME, "$DB_NAME-shm", "$DB_NAME-wal").forEach { name ->
                runCatching {
                    context.getDatabasePath(name)?.delete()
                }
            }
        }
    }

    private val dbRecoveryPrefs by lazy {
        context.getSharedPreferences("argus_db_recovery", Context.MODE_PRIVATE)
    }

    private fun hasTriedLegacyDbRecovery(): Boolean =
        dbRecoveryPrefs.getBoolean("legacy_plaintext_recovered_v1", false)

    private fun markLegacyDbRecoveryAttempted() {
        dbRecoveryPrefs.edit().putBoolean("legacy_plaintext_recovered_v1", true).apply()
    }

    private fun ensureNewSqlCipherNativeLoaded() {
        if (newSqlCipherLibLoaded) return
        synchronized(DefaultAppContainer::class.java) {
            if (newSqlCipherLibLoaded) return
            System.loadLibrary("sqlcipher")
            newSqlCipherLibLoaded = true
        }
    }

    private fun createSqlCipherFactory(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory {
        val newFactoryName = "net.zetetic.database.sqlcipher.SupportOpenHelperFactory"
        val legacyFactoryName = "net.sqlcipher.database.SupportFactory"
        val classNames = listOf(newFactoryName, legacyFactoryName)

        classNames.forEach { className ->
            try {
                if (className == newFactoryName) {
                    // sqlcipher-android expects libsqlcipher to be loaded before first native call.
                    ensureNewSqlCipherNativeLoaded()
                }

                if (className == legacyFactoryName) {
                    // Legacy SQLCipher requires explicit native library loading.
                    val sqliteDbClass = Class.forName("net.sqlcipher.database.SQLiteDatabase")
                    val loadLibs = sqliteDbClass.getMethod("loadLibs", Context::class.java)
                    loadLibs.invoke(null, context)
                }

                val ctor = Class.forName(className).getConstructor(ByteArray::class.java)
                val factory = ctor.newInstance(passphrase.copyOf())
                return factory as SupportSQLiteOpenHelper.Factory
            } catch (_: ClassNotFoundException) {
                // Try next known factory class.
            }
        }

        throw IllegalStateException(
            "No SQLCipher Room factory found. Add net.zetetic:sqlcipher-android or net.zetetic:android-database-sqlcipher."
        )
    }

    private fun isSqlCipherNotADatabaseError(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            val message = current.message.orEmpty()
            if (
                current.javaClass.simpleName.contains("NotADatabase", ignoreCase = true) ||
                message.contains("file is not a database", ignoreCase = true) ||
                message.contains("hmac check failed", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun deleteArgusDbFiles() {
        forceCloseAndDeleteDatabase(context)
    }

    private fun buildEncryptedDatabase(passphrase: ByteArray): ArgusDatabase {
        val db = Room.databaseBuilder(
            context,
            ArgusDatabase::class.java,
            DB_NAME
        )
            .openHelperFactory(createSqlCipherFactory(passphrase))
            .addMigrations(ArgusDatabase.MIGRATION_1_2)
            .addMigrations(ArgusDatabase.MIGRATION_2_3)
            .addMigrations(ArgusDatabase.MIGRATION_3_4)
            .build()

        // Force open at construction time so we can recover instead of crashing later on main.
        db.openHelper.writableDatabase
        activeDb = db
        return db
    }

    private val db: ArgusDatabase by lazy {
        val passphrase = AppEncryptionManager.getDatabasePassphrase()
        runCatching {
            buildEncryptedDatabase(passphrase)
        }.getOrElse { error ->
            if (!hasTriedLegacyDbRecovery() && isSqlCipherNotADatabaseError(error)) {
                Log.w(TAG, "Recovering from legacy/plaintext DB format by recreating encrypted DB", error)
                markLegacyDbRecoveryAttempted()
                deleteArgusDbFiles()
                buildEncryptedDatabase(passphrase)
            } else {
                throw error
            }
        }
    }

    override val repository: EncounterRepository by lazy {
        RoomEncounterRepository(
            dao = db.encounterDao(),
            deviceLatestStateDao = db.deviceLatestStateDao()
        )
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
                ExternalFeedScanner(context, EncounterSource.SDR, "sdr"),
                AcousticSignatureScanner(context),
                MagnetometerDisturbanceScanner(context)
            )
        )
    }
}
