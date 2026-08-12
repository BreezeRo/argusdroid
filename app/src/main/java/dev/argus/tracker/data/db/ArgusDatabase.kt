package dev.argus.tracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [EncounterEntity::class],
    version = 1,
    exportSchema = true
)
abstract class ArgusDatabase : RoomDatabase() {
    abstract fun encounterDao(): EncounterDao
}
