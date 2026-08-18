package dev.argus.tracker.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureSettingsStore {
    private const val MIGRATION_PREFS_NAME = "argus_settings_secure_migration"
    private const val MIGRATION_DONE_PREFIX = "migration_done_"
    private val DEFAULT_TRACKED_NAMESPACES = listOf(
        "argus_settings",
        "nfc_ingest",
        "argus_mesh_state",
        "argus_encryption"
    )

    data class MigrationTelemetry(
        val namespace: String,
        val migrationDone: Boolean,
        val encryptedBackend: Boolean,
        val entryCount: Int
    )

    @Volatile
    private var cached = mutableMapOf<String, SharedPreferences>()

    fun prefs(context: Context, legacyPrefsName: String = "argus_settings"): SharedPreferences {
        cached[legacyPrefsName]?.let { return it }
        return synchronized(this) {
            cached[legacyPrefsName]?.let { return@synchronized it }

            val appContext = context.applicationContext
            val securePrefs = buildSecurePrefs(appContext, legacyPrefsName)
            migrateLegacyPrefsIfNeeded(appContext, legacyPrefsName, securePrefs)
            cached[legacyPrefsName] = securePrefs
            securePrefs
        }
    }

    private fun buildSecurePrefs(context: Context, legacyPrefsName: String): SharedPreferences {
        val securePrefsName = "${legacyPrefsName}_secure"
        return runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                securePrefsName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            // Some emulators/devices can fail encrypted prefs initialization.
            context.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        }
    }

    private fun migrateLegacyPrefsIfNeeded(
        context: Context,
        legacyPrefsName: String,
        securePrefs: SharedPreferences
    ) {
        if (!isEncryptedPrefs(securePrefs)) return

        val migrationPrefs = context.getSharedPreferences(MIGRATION_PREFS_NAME, Context.MODE_PRIVATE)
        val migrationDoneKey = MIGRATION_DONE_PREFIX + legacyPrefsName
        if (migrationPrefs.getBoolean(migrationDoneKey, false)) return

        val legacy = context.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        if (legacy.all.isNotEmpty()) {
            val editor = securePrefs.edit()
            legacy.all.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val asStrings = value.filterIsInstance<String>().toSet()
                        editor.putStringSet(key, asStrings)
                    }
                }
            }
            editor.apply()

            // Keep a backup on older Android where deleting prefs is less reliable.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                legacy.edit().clear().apply()
            }
        }

        migrationPrefs.edit().putBoolean(migrationDoneKey, true).apply()
    }

    private fun isEncryptedPrefs(prefs: SharedPreferences): Boolean =
        prefs.javaClass.name.contains("EncryptedSharedPreferences")

    fun readMigrationTelemetry(
        context: Context,
        namespaces: List<String> = DEFAULT_TRACKED_NAMESPACES
    ): List<MigrationTelemetry> {
        val migrationPrefs = context.getSharedPreferences(MIGRATION_PREFS_NAME, Context.MODE_PRIVATE)
        return namespaces.distinct().map { namespace ->
            val prefs = prefs(context, namespace)
            val migrationDone = migrationPrefs.getBoolean(MIGRATION_DONE_PREFIX + namespace, false)
            MigrationTelemetry(
                namespace = namespace,
                migrationDone = migrationDone,
                encryptedBackend = isEncryptedPrefs(prefs),
                entryCount = prefs.all.size
            )
        }
    }
}
