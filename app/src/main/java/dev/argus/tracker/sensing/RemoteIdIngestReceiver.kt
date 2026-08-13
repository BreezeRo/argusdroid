package dev.argus.tracker.sensing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.argus.tracker.worker.ScanSettings
import java.io.File
import org.json.JSONObject

class RemoteIdIngestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INGEST_REMOTE_ID) return

        val configuredToken = ScanSettings.getRemoteIdIngestToken(context)
            .ifBlank { ScanSettings.getChainSharedSecret(context) }
        val receivedToken = intent.getStringExtra(EXTRA_TOKEN).orEmpty().trim()
        if (configuredToken.isNotEmpty() && configuredToken != receivedToken) {
            return
        }

        val jsonLine = intent.getStringExtra(EXTRA_PAYLOAD_JSON)?.trim().orEmpty()
        val jsonl = intent.getStringExtra(EXTRA_PAYLOADS_JSONL)?.trim().orEmpty()
        val lines = buildList {
            if (jsonLine.isNotBlank()) add(jsonLine)
            if (jsonl.isNotBlank()) {
                jsonl.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { add(it) }
            }
        }

        if (lines.isEmpty()) return

        val validLines = lines.mapNotNull { line ->
            runCatching { JSONObject(line) }.getOrNull()?.toString()
        }
        if (validLines.isEmpty()) return

        val ingestDir = context.filesDir.resolve("ingest")
        if (!ingestDir.exists()) ingestDir.mkdirs()
        val remoteIdFeed = ingestDir.resolve("remote_id.jsonl")
        rotateIfLarge(remoteIdFeed)
        remoteIdFeed.appendText(validLines.joinToString(separator = "\n", postfix = "\n"), Charsets.UTF_8)
    }

    private fun rotateIfLarge(file: File) {
        if (!file.exists()) return
        val maxBytes = 5L * 1024L * 1024L
        if (file.length() < maxBytes) return

        val archive = file.parentFile?.resolve("remote_id-${System.currentTimeMillis()}.jsonl")
        runCatching {
            if (archive != null) {
                file.copyTo(archive, overwrite = true)
            }
            file.writeText("", Charsets.UTF_8)
        }
    }

    companion object {
        const val ACTION_INGEST_REMOTE_ID = "dev.argus.tracker.action.INGEST_REMOTE_ID"
        const val EXTRA_PAYLOAD_JSON = "payloadJson"
        const val EXTRA_PAYLOADS_JSONL = "payloadsJsonl"
        const val EXTRA_TOKEN = "token"
    }
}
