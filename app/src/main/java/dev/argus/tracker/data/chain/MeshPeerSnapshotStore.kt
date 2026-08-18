package dev.argus.tracker.data.chain

import android.content.Context
import dev.argus.tracker.data.SecureSettingsStore
import org.json.JSONArray
import org.json.JSONObject

data class MeshPeerSnapshot(
    val localNodeId: String,
    val connectedPeerNodeIds: Set<String>,
    val lastUpdatedEpochMs: Long
)

object MeshPeerSnapshotStore {
    private const val PREFS_NAME = "argus_mesh_state"
    private const val KEY_SNAPSHOT_JSON = "mesh_peer_snapshot_json"

    fun write(context: Context, snapshot: ChainMeshSnapshot) {
        val connectedPeerIds = snapshot.peers
            .asSequence()
            .filter { it.state == ChainPeerState.CONNECTED }
            .map { it.nodeId.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()

        val root = JSONObject().apply {
            put("localNodeId", snapshot.localNodeId)
            put("lastUpdatedEpochMs", System.currentTimeMillis())
            put("connectedPeerNodeIds", JSONArray().apply {
                connectedPeerIds.forEach { put(it) }
            })
        }

        SecureSettingsStore.prefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_SNAPSHOT_JSON, root.toString())
            .apply()
    }

    fun read(context: Context): MeshPeerSnapshot? {
        val raw = SecureSettingsStore.prefs(context, PREFS_NAME)
            .getString(KEY_SNAPSHOT_JSON, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val localNodeId = root.optString("localNodeId", "").trim().ifBlank { return null }
        val lastUpdatedEpochMs = root.optLong("lastUpdatedEpochMs", 0L)
        if (lastUpdatedEpochMs <= 0L) return null

        val connectedPeerIds = buildSet {
            val arr = root.optJSONArray("connectedPeerNodeIds") ?: return@buildSet
            for (i in 0 until arr.length()) {
                val id = arr.optString(i, "").trim()
                if (id.isNotBlank()) {
                    add(id)
                }
            }
        }

        return MeshPeerSnapshot(
            localNodeId = localNodeId,
            connectedPeerNodeIds = connectedPeerIds,
            lastUpdatedEpochMs = lastUpdatedEpochMs
        )
    }
}


