package dev.argus.tracker.data

import android.content.Context

/**
 * Shared ownership registry used by sensing and UI layers.
 */
object OwnedSignalRegistry {
    private const val PREFS_NAME = "argus_settings"
    private const val KEY_OWNED_DEVICE_KEYS = "owned_device_keys"

    fun keyFor(source: String, primaryId: String): String = "$source|$primaryId"

    fun read(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_OWNED_DEVICE_KEYS, emptySet())?.toSet() ?: emptySet()
    }

    fun setOwned(context: Context, source: String, primaryId: String, owned: Boolean) {
        val normalizedSource = source.trim()
        val normalizedPrimaryId = primaryId.trim()
        if (normalizedSource.isBlank() || normalizedPrimaryId.isBlank()) return

        val current = read(context).toMutableSet()
        val key = keyFor(normalizedSource, normalizedPrimaryId)
        if (owned) {
            current += key
        } else {
            current -= key
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_OWNED_DEVICE_KEYS, current)
            .apply()
    }
}
