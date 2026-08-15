package dev.argus.tracker.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class OperationalErrorLogEntry(
    val timestampEpochMs: Long,
    val category: String,
    val source: String,
    val message: String,
    val severity: String = "ERROR"
)

object OperationalErrorLogStore {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_ERROR_LOG_ENTRIES = "operational_error_log_entries"
    private const val MAX_LOG_ENTRIES = 500
    private const val DEDUPE_WINDOW_MS = 60_000L

    fun read(context: Context): List<OperationalErrorLogEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ERROR_LOG_ENTRIES, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    OperationalErrorLogEntry(
                        timestampEpochMs = item.optLong("timestampEpochMs", 0L),
                        category = item.optString("category", "UNKNOWN").ifBlank { "UNKNOWN" },
                        source = item.optString("source", "system").ifBlank { "system" },
                        message = item.optString("message", ""),
                        severity = item.optString("severity", "ERROR").ifBlank { "ERROR" }.uppercase()
                    )
                )
            }
        }
    }

    fun append(
        context: Context,
        category: String,
        source: String,
        message: String,
        severity: String = "ERROR",
        timestampEpochMs: Long = System.currentTimeMillis()
    ) {
        if (message.isBlank()) return
        val normalizedCategory = category.trim().ifBlank { "UNKNOWN" }.uppercase()
        val normalizedSource = source.trim().ifBlank { "system" }
        val normalizedMessage = message.trim().take(400)
        val normalizedSeverity = severity.trim().ifBlank { "ERROR" }.uppercase()

        val existing = read(context)
        val latestSame = existing.firstOrNull {
            it.category == normalizedCategory &&
                it.source == normalizedSource &&
                it.message == normalizedMessage &&
                it.severity == normalizedSeverity
        }
        if (latestSame != null && (timestampEpochMs - latestSame.timestampEpochMs) in 0..DEDUPE_WINDOW_MS) {
            return
        }

        val updated = (listOf(
            OperationalErrorLogEntry(
                timestampEpochMs = timestampEpochMs,
                category = normalizedCategory,
                source = normalizedSource,
                message = normalizedMessage,
                severity = normalizedSeverity
            )
        ) + existing)
            .sortedByDescending { it.timestampEpochMs }
            .take(MAX_LOG_ENTRIES)

        write(context, updated)
    }

    fun clear(context: Context) {
        write(context, emptyList())
    }

    private fun write(context: Context, entries: List<OperationalErrorLogEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("timestampEpochMs", entry.timestampEpochMs)
                    put("category", entry.category)
                    put("source", entry.source)
                    put("message", entry.message)
                    put("severity", entry.severity)
                }
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ERROR_LOG_ENTRIES, array.toString())
            .apply()
    }
}
