package dev.argus.tracker.data

import android.content.Context
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterProvenance
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.worker.ScanSettings
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object AppBackupManager {
    private const val PREFS_NAME = "argus_settings"
    private const val BACKUP_DIR = "backups"
    private const val BACKUP_FILE_PREFIX = "argus-backup-"
    private const val BACKUP_FILE_EXT = ".json"
    private const val BACKUP_ENCRYPTED_FILE_EXT = ".enc"
    private const val BACKUP_SCHEMA = "argus-backup-v1"
    private const val BACKUP_ENCRYPTED_SCHEMA = "argus-backup-encrypted-v1"
    private const val KEY_CHAIN_NODE_ID = "chain_node_id"
    private const val MAX_EXPORT_ENCOUNTERS = 200_000
    private const val MIN_PASSPHRASE_LENGTH = 8
    private const val PBKDF2_ITERATIONS = 180_000
    private const val DERIVED_KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val KDF_SALT_BYTES = 16

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
        val root = buildSnapshotRoot(context, repository, reason, now)

        val file = File(backupDir, "$BACKUP_FILE_PREFIX$now$BACKUP_FILE_EXT")
        file.writeText(root.toString(2), Charsets.UTF_8)
        file
    }

    suspend fun exportEncryptedSnapshot(
        context: Context,
        repository: EncounterRepository,
        reason: String,
        passphrase: String
    ): File = withContext(Dispatchers.IO) {
        val normalizedPassphrase = passphrase.trim()
        require(normalizedPassphrase.length >= MIN_PASSPHRASE_LENGTH) {
            "Passphrase must be at least $MIN_PASSPHRASE_LENGTH characters"
        }

        val backupDir = File(context.filesDir, BACKUP_DIR)
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val now = System.currentTimeMillis()
        val root = buildSnapshotRoot(context, repository, reason, now)
        val plainBytes = root.toString().toByteArray(Charsets.UTF_8)
        val encryptedRoot = encryptSnapshot(plainBytes, normalizedPassphrase, now, reason)

        val file = File(backupDir, "$BACKUP_FILE_PREFIX$now$BACKUP_FILE_EXT$BACKUP_ENCRYPTED_FILE_EXT")
        file.writeText(encryptedRoot.toString(2), Charsets.UTF_8)
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

    suspend fun importLatestEncryptedSnapshot(
        context: Context,
        repository: EncounterRepository,
        passphrase: String
    ): String = withContext(Dispatchers.IO) {
        val normalizedPassphrase = passphrase.trim()
        require(normalizedPassphrase.length >= MIN_PASSPHRASE_LENGTH) {
            "Passphrase must be at least $MIN_PASSPHRASE_LENGTH characters"
        }

        val backupDir = File(context.filesDir, BACKUP_DIR)
        val latest = backupDir
            .listFiles {
                file ->
                file.isFile &&
                    file.name.startsWith(BACKUP_FILE_PREFIX) &&
                    file.name.endsWith("$BACKUP_FILE_EXT$BACKUP_ENCRYPTED_FILE_EXT")
            }
            ?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("No encrypted backup snapshots found")

        importFromFile(context, repository, latest, normalizedPassphrase)
        latest.name
    }

    suspend fun importFromFile(
        context: Context,
        repository: EncounterRepository,
        file: File,
        passphrase: String? = null
    ) = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("Backup file not found")
        }

        val root = JSONObject(file.readText(Charsets.UTF_8))
        val normalized = when (val schema = root.optString("schema", "")) {
            BACKUP_SCHEMA -> root
            BACKUP_ENCRYPTED_SCHEMA -> {
                val normalizedPassphrase = passphrase?.trim().orEmpty()
                if (normalizedPassphrase.length < MIN_PASSPHRASE_LENGTH) {
                    throw IllegalArgumentException("Encrypted backup requires passphrase of at least $MIN_PASSPHRASE_LENGTH characters")
                }
                decryptSnapshot(root, normalizedPassphrase)
            }

            else -> throw IllegalArgumentException("Unsupported backup schema: $schema")
        }

        applySnapshot(context, repository, normalized)
    }

    fun backupDirectoryPath(context: Context): String = File(context.filesDir, BACKUP_DIR).absolutePath

    private suspend fun buildSnapshotRoot(
        context: Context,
        repository: EncounterRepository,
        reason: String,
        createdEpochMs: Long
    ): JSONObject {
        val encounters = repository.listAll(MAX_EXPORT_ENCOUNTERS)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        return JSONObject().apply {
            put("schema", BACKUP_SCHEMA)
            put("createdEpochMs", createdEpochMs)
            put("reason", reason)
            put("deviceNodeId", ScanSettings.getChainNodeId(context))
            put("deviceName", ScanSettings.getChainDeviceName(context))
            put("encounters", encodeEncounters(encounters))
            put("settings", encodeSettings(prefs.all))
        }
    }

    private suspend fun applySnapshot(
        context: Context,
        repository: EncounterRepository,
        root: JSONObject
    ) {
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

    private fun encryptSnapshot(
        plainBytes: ByteArray,
        passphrase: String,
        createdEpochMs: Long,
        reason: String
    ): JSONObject {
        val salt = ByteArray(KDF_SALT_BYTES)
        val iv = ByteArray(GCM_IV_BYTES)
        SecureRandom().nextBytes(salt)
        SecureRandom().nextBytes(iv)

        val secret = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plainBytes)

        return JSONObject().apply {
            put("schema", BACKUP_ENCRYPTED_SCHEMA)
            put("createdEpochMs", createdEpochMs)
            put("reason", reason)
            put("kdf", JSONObject().apply {
                put("algorithm", "PBKDF2WithHmacSHA256")
                put("iterations", PBKDF2_ITERATIONS)
                put("keyBits", DERIVED_KEY_BITS)
                put("saltBase64", encodeBase64(salt))
            })
            put("cipher", JSONObject().apply {
                put("algorithm", "AES/GCM/NoPadding")
                put("ivBase64", encodeBase64(iv))
                put("tagBits", GCM_TAG_BITS)
                put("ciphertextBase64", encodeBase64(ciphertext))
            })
        }
    }

    private fun decryptSnapshot(encryptedRoot: JSONObject, passphrase: String): JSONObject {
        val kdf = encryptedRoot.optJSONObject("kdf")
            ?: throw IllegalArgumentException("Encrypted backup missing KDF metadata")
        val cipherMeta = encryptedRoot.optJSONObject("cipher")
            ?: throw IllegalArgumentException("Encrypted backup missing cipher metadata")

        val iterations = kdf.optInt("iterations", PBKDF2_ITERATIONS).coerceAtLeast(10_000)
        val keyBits = kdf.optInt("keyBits", DERIVED_KEY_BITS).coerceAtLeast(128)
        val tagBits = cipherMeta.optInt("tagBits", GCM_TAG_BITS).coerceAtLeast(96)
        val salt = decodeBase64(kdf.optString("saltBase64", ""))
        val iv = decodeBase64(cipherMeta.optString("ivBase64", ""))
        val ciphertext = decodeBase64(cipherMeta.optString("ciphertextBase64", ""))

        if (salt.isEmpty() || iv.isEmpty() || ciphertext.isEmpty()) {
            throw IllegalArgumentException("Encrypted backup payload is incomplete")
        }

        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, keyBits)
        val derived = keyFactory.generateSecret(keySpec).encoded
        val secret = SecretKeySpec(derived, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        return try {
            cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(tagBits, iv))
            val plainBytes = cipher.doFinal(ciphertext)
            JSONObject(String(plainBytes, Charsets.UTF_8))
        } catch (error: Exception) {
            throw IllegalArgumentException("Failed to decrypt backup. Verify passphrase and retry.")
        }
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, DERIVED_KEY_BITS)
        val keyBytes = keyFactory.generateSecret(keySpec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun encodeBase64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun decodeBase64(value: String): ByteArray =
        runCatching { Base64.getDecoder().decode(value) }.getOrDefault(ByteArray(0))

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
