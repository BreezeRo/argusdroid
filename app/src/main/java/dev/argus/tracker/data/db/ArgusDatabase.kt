package dev.argus.tracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EncounterEntity::class, DeviceLatestStateEntity::class],
    version = 4,
    exportSchema = true
)
abstract class ArgusDatabase : RoomDatabase() {
    abstract fun encounterDao(): EncounterDao
    abstract fun deviceLatestStateDao(): DeviceLatestStateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE encounters ADD COLUMN encounterFingerprint TEXT")
                db.execSQL("ALTER TABLE encounters ADD COLUMN provenance TEXT NOT NULL DEFAULT 'LOCAL'")
                db.execSQL("ALTER TABLE encounters ADD COLUMN provenanceNodeId TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_encounters_encounterFingerprint ON encounters(encounterFingerprint)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_encounters_provenance ON encounters(provenance)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_encounters_provenanceNodeId ON encounters(provenanceNodeId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE encounters ADD COLUMN provenanceOriginNodeId TEXT")
                db.execSQL("ALTER TABLE encounters ADD COLUMN provenancePathNodeIds TEXT")
                db.execSQL("ALTER TABLE encounters ADD COLUMN provenanceReceivedAtEpochMs INTEGER")
                db.execSQL("ALTER TABLE encounters ADD COLUMN provenanceHopCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_encounters_provenanceOriginNodeId ON encounters(provenanceOriginNodeId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS device_latest_state (
                        source TEXT NOT NULL,
                        primaryId TEXT NOT NULL,
                        timestampEpochMs INTEGER NOT NULL,
                        secondaryId TEXT,
                        rssiDbm INTEGER,
                        frequencyMhz INTEGER,
                        lat REAL,
                        lon REAL,
                        rawPayloadJson TEXT NOT NULL,
                        encounterFingerprint TEXT,
                        provenance TEXT NOT NULL,
                        provenanceNodeId TEXT,
                        provenanceOriginNodeId TEXT,
                        provenancePathNodeIds TEXT,
                        provenanceReceivedAtEpochMs INTEGER,
                        provenanceHopCount INTEGER NOT NULL,
                        PRIMARY KEY(source, primaryId)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_device_latest_state_timestampEpochMs ON device_latest_state(timestampEpochMs)"
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO device_latest_state (
                        source,
                        primaryId,
                        timestampEpochMs,
                        secondaryId,
                        rssiDbm,
                        frequencyMhz,
                        lat,
                        lon,
                        rawPayloadJson,
                        encounterFingerprint,
                        provenance,
                        provenanceNodeId,
                        provenanceOriginNodeId,
                        provenancePathNodeIds,
                        provenanceReceivedAtEpochMs,
                        provenanceHopCount
                    )
                    SELECT
                        e.source,
                        e.primaryId,
                        e.timestampEpochMs,
                        e.secondaryId,
                        e.rssiDbm,
                        e.frequencyMhz,
                        e.lat,
                        e.lon,
                        e.rawPayloadJson,
                        e.encounterFingerprint,
                        e.provenance,
                        e.provenanceNodeId,
                        e.provenanceOriginNodeId,
                        e.provenancePathNodeIds,
                        e.provenanceReceivedAtEpochMs,
                        e.provenanceHopCount
                    FROM encounters e
                    INNER JOIN (
                        SELECT source, primaryId, MAX(timestampEpochMs) AS maxTimestampEpochMs
                        FROM encounters
                        GROUP BY source, primaryId
                    ) latest
                    ON e.source = latest.source
                    AND e.primaryId = latest.primaryId
                    AND e.timestampEpochMs = latest.maxTimestampEpochMs
                    """.trimIndent()
                )
            }
        }
    }
}
