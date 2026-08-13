package dev.argus.tracker.data

import android.content.Context
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterProvenance
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.worker.ScanSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object AppBackupManager {
    private const val PREFS_NAME = "argus_settings"
    private const val BACKUP_DIR = "backups"
    private const val BACKUP_FILE_PREFIX = "argus-backup-"
    private const val BACKUP_FILE_EXT = ".json"
    private const val BACKUP_SCHEMA = "argus-backup-v1"
    private const val KEY_CHAIN_NODE_ID = "chain_node_id"
    private const val MAX_EXPORT_ENCOUNTERS = 200_000

    suspend fun exportSnapshot(
        context: Context,
        repository: EncounterRepository,
        reason: String
    ): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.filesDir, BACKUP_DIR)
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val now = System.currentTimeMillis()
        val encounters = repository.listAll(MAX_EXPORT_ENCOUNTERS)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val root = JSONObject().apply {
            put("schema", BACKUP_SCHEMA)
            put("createdEpochMs", now)
            put("reason", reason)
            put("deviceNodeId", ScanSettings.getChainNodeId(context))
            put("deviceName", ScanSettings.getChainDeviceName(context))
            put("encounters", encodeEncounters(encounters))
            put("settings", encodeSettings(prefs.all))
        }

        val file = File(backupDir, "$BACKUP_FILE_PREFIX$now$BACKUP_FILE_EXT")
        file.writeText(root.toString(2), Charsets.UTF_8)
        file
    }

    suspend fun importLatestSnapshot(
        context: Context,
        repository: EncounterRepository
    ): String = withContext(Dispatchers.IO) {
        val backupDir = File(context.filesDir, BACKUP_DIR)
        val latest = backupDir
            .listFiles { file -> file.isFile && file.name.startsWith(BACKUP_FILE_PREFIX) && file.name.endsWith(BACKUP_FILE_EXT) }
            ?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("No backup snapshots found")

        importFromFile(context, repository, latest)
        latest.name
    }

    suspend fun importFromFile(
        context: Context,
        repository: EncounterRepository,
        file: File
    ) = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("Backup file not found")
        }

        val root = JSONObject(file.readText(Charsets.UTF_8))
        val schema = root.optString("schema", "")
        if (schema != BACKUP_SCHEMA) {
            throw IllegalArgumentException("Unsupported backup schema: $schema")
        }

        val encounterArray = root.optJSONArray("encounters") ?: JSONArray()
        val settingsArray = root.optJSONArray("settings") ?: JSONArray()

        repository.clearEncounters()
        repository.clearDevices()
        val encounters = decodeEncounters(encounterArray)
        if (encounters.isNotEmpty()) {
            repository.insertBatch(encounters)
        }

        applySettings(context, settingsArray)
    }

    fun backupDirectoryPath(context: Context): String = File(context.filesDir, BACKUP_DIR).absolutePath

    private fun encodeEncounters(encounters: List<Encounter>): JSONArray {
        val out = JSONArray()
        encounters.forEach { encounter ->
            out.put(
                JSONObject().apply {
                    put("timestampEpochMs", encounter.timestampEpochMs)
                    put("source", encounter.source.name)
                    put("primaryId", encounter.primaryId)
                    put("secondaryId", encounter.secondaryId)
                    put("rssiDbm", encounter.rssiDbm)
                    put("frequencyMhz", encounter.frequencyMhz)
                    put("lat", encounter.lat)
                    put("lon", encounter.lon)
                    put("rawPayloadJson", encounter.rawPayloadJson)
                    put("encounterFingerprint", encounter.encounterFingerprint)
                    put("provenance", encounter.provenance.name)
                    put("provenanceNodeId", encounter.provenanceNodeId)
                    put("provenanceOriginNodeId", encounter.provenanceOriginNodeId)
                    put("provenancePathNodeIds", encounter.provenancePathNodeIds)
                    put("provenanceReceivedAtEpochMs", encounter.provenanceReceivedAtEpochMs)
                    put("provenanceHopCount", encounter.provenanceHopCount)
                }
            )
        }
        return out
    }

    private fun decodeEncounters(array: JSONArray): List<Encounter> {
        val out = mutableListOf<Encounter>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val source = runCatching { EncounterSource.valueOf(obj.optString("source", "UNKNOWN_RF")) }
                .getOrDefault(EncounterSource.UNKNOWN_RF)
            val provenance = runCatching { EncounterProvenance.valueOf(obj.optString("provenance", "LOCAL")) }
                .getOrDefault(EncounterProvenance.LOCAL)
            val timestamp = obj.optLong("timestampEpochMs", -1L)
            val primaryId = obj.optString("primaryId", "").trim()
            if (timestamp <= 0L || primaryId.isBlank()) continue

            out += Encounter(
                timestampEpochMs = timestamp,
                source = source,
                primaryId = primaryId,
                secondaryId = obj.optString("secondaryId", null),
                rssiDbm = obj.optIntOrNull("rssiDbm"),
                frequencyMhz = obj.optIntOrNull("frequencyMhz"),
                lat = obj.optDoubleOrNull("lat")?.takeIf { it.isFinite() },
                lon = obj.optDoubleOrNull("lon")?.takeIf { it.isFinite() },
                rawPayloadJson = obj.optString("rawPayloadJson", "{}"),
                encounterFingerprint = obj.optString("encounterFingerprint", null),
                provenance = provenance,
                provenanceNodeId = obj.optString("provenanceNodeId", null),
                provenanceOriginNodeId = obj.optString("provenanceOriginNodeId", null),
                provenancePathNodeIds = obj.optString("provenancePathNodeIds", null),
                provenanceReceivedAtEpochMs = obj.optLongOrNull("provenanceReceivedAtEpochMs"),
                provenanceHopCount = obj.optInt("provenanceHopCount", 0).coerceAtLeast(0)
            )
        }
        return out
    }

    private fun encodeSettings(all: Map<String, *>): JSONArray {
        val out = JSONArray()
        all.forEach { (key, value) ->
            val entry = JSONObject().apply { put("key", key) }
            when (value) {
                is Boolean -> {
                    entry.put("type", "boolean")
                    entry.put("value", value)
                }

                is Int -> {
                    entry.put("type", "int")
                    entry.put("value", value)
                }

                is Long -> {
                    entry.put("type", "long")
                    entry.put("value", value)
                }

                is Float -> {
                    entry.put("type", "float")
                    entry.put("value", value)
                }

                is String -> {
                    entry.put("type", "string")
                    entry.put("value", value)
                }

                is Set<*> -> {
                    entry.put("type", "string_set")
                    val values = JSONArray()
                    value.mapNotNull { it?.toString() }.forEach(values::put)
                    entry.put("value", values)
                }

                else -> return@forEach
            }
            out.put(entry)
        }
        return out
    }

    private fun applySettings(context: Context, array: JSONArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val preservedChainNodeId = prefs.getString(KEY_CHAIN_NODE_ID, null)
        val edit = prefs.edit()
        edit.clear()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val key = obj.optString("key", "").trim()
            if (key.isBlank()) continue
            when (obj.optString("type", "")) {
                "boolean" -> edit.putBoolean(key, obj.optBoolean("value", false))
                "int" -> edit.putInt(key, obj.optInt("value", 0))
                "long" -> edit.putLong(key, obj.optLong("value", 0L))
                "float" -> edit.putFloat(key, obj.optDouble("value", 0.0).toFloat())
                "string" -> edit.putString(key, obj.optString("value", ""))
                "string_set" -> {
                    val arr = obj.optJSONArray("value") ?: JSONArray()
                    val values = mutableSetOf<String>()
                    for (j in 0 until arr.length()) {
                        val item = arr.optString(j, "").trim()
                        if (item.isNotBlank()) values += item
                    }
                    edit.putStringSet(key, values)
                }
            }
        }

        if (!preservedChainNodeId.isNullOrBlank()) {
            edit.putString(KEY_CHAIN_NODE_ID, preservedChainNodeId)
        }

        edit.apply()
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return optLong(key)
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key)
    }
}
