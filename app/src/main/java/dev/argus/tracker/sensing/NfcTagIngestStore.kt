package dev.argus.tracker.sensing

import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Parcelable
import dev.argus.tracker.data.SecureSettingsStore
import org.json.JSONArray
import org.json.JSONObject

object NfcTagIngestStore {
    private const val PREFS_NAME = "nfc_ingest"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 200

    fun ingestFromIntent(
        context: Context,
        intent: Intent?,
        persistForScanner: Boolean = true
    ): List<JSONObject> {
        val payload = parseNfcIntent(intent) ?: return emptyList()
        if (persistForScanner) {
            append(context, payload)
        }
        return listOf(payload)
    }

    fun ingestFromTag(
        context: Context,
        tag: Tag?,
        persistForScanner: Boolean = true,
        action: String = "android.nfc.action.READER_MODE"
    ): List<JSONObject> {
        val payload = parseNfcTag(tag = tag, action = action) ?: return emptyList()
        if (persistForScanner) {
            append(context, payload)
        }
        return listOf(payload)
    }

    fun drain(context: Context, maxItems: Int = 100): List<JSONObject> {
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val raw = prefs.getString(KEY_EVENTS, "[]").orEmpty()
        val source = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        if (source.length() == 0) return emptyList()

        val limit = maxItems.coerceAtLeast(1)
        val startIndex = (source.length() - limit).coerceAtLeast(0)
        val out = mutableListOf<JSONObject>()
        for (index in startIndex until source.length()) {
            val item = source.optJSONObject(index) ?: continue
            out += JSONObject(item.toString())
        }

        prefs.edit().putString(KEY_EVENTS, "[]").apply()
        return out
    }

    private fun append(context: Context, payload: JSONObject) {
        val prefs = SecureSettingsStore.prefs(context, PREFS_NAME)
        val raw = prefs.getString(KEY_EVENTS, "[]").orEmpty()
        val existing = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }

        val updated = JSONArray()
        val keepFrom = (existing.length() - (MAX_EVENTS - 1)).coerceAtLeast(0)
        for (index in keepFrom until existing.length()) {
            updated.put(existing.opt(index))
        }
        updated.put(payload)

        prefs.edit().putString(KEY_EVENTS, updated.toString()).apply()
    }

    private fun parseNfcIntent(intent: Intent?): JSONObject? {
        if (intent == null) return null
        val action = intent.action ?: return null
        if (action !in NFC_ACTIONS) return null

        val tag = getParcelableExtraCompat(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)
        return parseNfcTag(tag = tag, action = action)
    }

    private fun parseNfcTag(tag: Tag?, action: String): JSONObject? {
        if (tag == null) return null
        val tagIdHex = tag.id?.toHexString()
        val techList = tag.techList?.toList().orEmpty()

        val ndefSummaries = readTagNdefSummaries(tag)

        val payload = JSONObject()
            .put("schema", "argus.nfc.v1")
            .put("timestampEpochMs", System.currentTimeMillis())
            .put("action", action)
            .put("tagIdHex", tagIdHex)
            .put("techList", JSONArray(techList))
            .put("ndefRecords", JSONArray(ndefSummaries))

        val primaryId = tagIdHex?.takeIf { it.isNotBlank() }
            ?: ndefSummaries.firstOrNull()?.optString("text", null)
            ?: "nfc-tag"
        val secondaryId = techList.firstOrNull()
            ?.substringAfterLast('.')
            ?.takeIf { it.isNotBlank() }

        payload.put("primaryId", primaryId)
        payload.put("secondaryId", secondaryId)

        return payload
    }

    private fun readTagNdefSummaries(tag: Tag): List<JSONObject> {
        val ndef = runCatching { android.nfc.tech.Ndef.get(tag) }.getOrNull() ?: return emptyList()
        val message = runCatching { ndef.cachedNdefMessage ?: ndef.ndefMessage }.getOrNull() ?: return emptyList()
        return message.records.map { record -> summarizeRecord(record) }
    }

    private fun parseNdefSummaries(intent: Intent): List<JSONObject> {
        val array = getParcelableArrayExtraCompat(intent, NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (array.isNullOrEmpty()) return emptyList()

        val out = mutableListOf<JSONObject>()
        array.forEach { parcelable ->
            val message = parcelable as? NdefMessage ?: return@forEach
            message.records.forEach { record ->
                out += summarizeRecord(record)
            }
        }
        return out
    }

    private fun summarizeRecord(record: NdefRecord): JSONObject {
        val payloadBytes = record.payload ?: ByteArray(0)
        val text = decodeTextRecord(record)
        return JSONObject()
            .put("tnf", record.tnf)
            .put("typeHex", record.type?.toHexString())
            .put("idHex", record.id?.toHexString())
            .put("payloadLength", payloadBytes.size)
            .put("payloadHex", payloadBytes.toHexString())
            .put("text", text)
    }

    private fun decodeTextRecord(record: NdefRecord): String? {
        val payload = record.payload ?: return null
        if (payload.isEmpty()) return null

        return runCatching {
            val status = payload[0].toInt()
            val isUtf16 = (status and 0x80) != 0
            val languageLength = status and 0x3F
            if (payload.size <= languageLength + 1) return@runCatching null
            val textBytes = payload.copyOfRange(languageLength + 1, payload.size)
            val charset = if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8
            String(textBytes, charset).trim().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun <T : Parcelable> getParcelableExtraCompat(intent: Intent, key: String, clazz: Class<T>): T? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, clazz)
        } else {
            intent.getParcelableExtra(key)
        }
    }

    @Suppress("DEPRECATION")
    private fun getParcelableArrayExtraCompat(intent: Intent, key: String): Array<Parcelable>? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(key, Parcelable::class.java)
        } else {
            intent.getParcelableArrayExtra(key)
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
        byte.toInt().and(0xFF).toString(16).padStart(2, '0')
    }

    private val NFC_ACTIONS = setOf(
        NfcAdapter.ACTION_TAG_DISCOVERED,
        NfcAdapter.ACTION_TECH_DISCOVERED,
        NfcAdapter.ACTION_NDEF_DISCOVERED
    )
}


