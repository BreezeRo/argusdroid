package dev.argus.tracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EncounterEntity::class],
    version = 3,
    exportSchema = true
)
abstract class ArgusDatabase : RoomDatabase() {
    abstract fun encounterDao(): EncounterDao

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
    }
}
