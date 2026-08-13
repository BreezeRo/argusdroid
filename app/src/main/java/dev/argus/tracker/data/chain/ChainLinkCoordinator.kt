package dev.argus.tracker.data.chain

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.argus.tracker.data.AppBackupManager
import dev.argus.tracker.data.EncounterRepository
import dev.argus.tracker.data.computeEncounterFingerprint
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterProvenance
import dev.argus.tracker.sensing.LocationSnapshotProvider
import dev.argus.tracker.worker.ScanSettings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Collections
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class ChainSyncStats(
    val enabled: Boolean,
    val authConfigured: Boolean,
    val peersDiscovered: Int,
    val peersSynced: Int,
    val exportedRecords: Int,
    val importedRecords: Int,
    val failures: Int
)

data class ChainWipeStats(
    val enabled: Boolean,
    val authConfigured: Boolean,
    val localCleared: Boolean,
    val peersTargeted: Int,
    val peersWiped: Int,
    val failures: Int
)

enum class ChainPeerState {
    DISCOVERED,
    CONNECTED,
    REQUESTED,
    FAILED
}

data class ChainPeerStatus(
    val nodeId: String,
    val deviceName: String?,
    val host: String,
    val state: ChainPeerState,
    val lastSeenEpochMs: Long,
    val lastSuccessfulSyncEpochMs: Long?,
    val lastLinkRequestEpochMs: Long?,
    val lastFailure: String?,
    val sharedLocationLat: Double?,
    val sharedLocationLon: Double?,
    val sharedLocationAccuracyMeters: Float?,
    val sharedLocationTimestampEpochMs: Long?
)

data class IncomingLinkRequest(
    val requesterNodeId: String,
    val requesterDeviceName: String?,
    val requesterHost: String,
    val message: String?,
    val timestampEpochMs: Long
)

data class MeshWipeNotice(
    val sessionId: String,
    val initiatorNodeId: String,
    val initiatorDeviceName: String?,
    val detail: String,
    val timestampEpochMs: Long
)

data class ChainMeshSnapshot(
    val localNodeId: String,
    val localDeviceName: String,
    val peers: List<ChainPeerStatus>,
    val incomingRequests: List<IncomingLinkRequest>,
    val wipeNotices: List<MeshWipeNotice> = emptyList(),
    val lastRefreshEpochMs: Long?,
    val lastSyncEpochMs: Long?
)

interface ChainLinkCoordinator {
    fun ensureServerRunning()
    fun stopServer()
    fun observeMesh(): StateFlow<ChainMeshSnapshot>
    suspend fun refreshPeers(): ChainMeshSnapshot
    suspend fun sendLinkRequest(host: String, message: String? = null): Boolean
    suspend fun syncNow(): ChainSyncStats
    suspend fun wipeMeshDataAcrossPeers(): ChainWipeStats
}

class LocalMeshChainLinkCoordinator(
    private val context: Context,
    private val repository: EncounterRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : ChainLinkCoordinator {

    private val nodeId: String = ScanSettings.getChainNodeId(context)
    private val peerStatusByNode = mutableMapOf<String, ChainPeerStatus>()
    private val incomingRequests = mutableListOf<IncomingLinkRequest>()
    private val wipeNotices = mutableListOf<MeshWipeNotice>()
    private val meshState = MutableStateFlow(
        ChainMeshSnapshot(
            localNodeId = nodeId,
            localDeviceName = ScanSettings.getChainDeviceName(context),
            peers = emptyList(),
            incomingRequests = emptyList(),
            wipeNotices = emptyList(),
            lastRefreshEpochMs = null,
            lastSyncEpochMs = null
        )
    )
    private var persistentChannelJob: Job? = null
    private val server = ChainLinkServer(
        nodeId = nodeId,
        repository = repository,
        context = context,
        scope = scope,
        authSecretProvider = { ScanSettings.getChainSharedSecret(context) },
        onWipeNotice = { notice ->
            addWipeNotice(notice)
            publishSnapshot()
        },
        onIncomingLinkRequest = { request ->
            synchronized(incomingRequests) {
                incomingRequests.add(0, request)
                if (incomingRequests.size > CHAIN_MAX_INCOMING_REQUESTS) {
                    incomingRequests.removeAt(incomingRequests.lastIndex)
                }
            }
            mergePeerStatus(
                nodeId = request.requesterNodeId,
                deviceName = request.requesterDeviceName,
                host = request.requesterHost,
                state = ChainPeerState.REQUESTED,
                failure = null,
                sharedLocationLat = null,
                sharedLocationLon = null,
                sharedLocationAccuracyMeters = null,
                sharedLocationTimestampEpochMs = null,
                markLinkRequest = true
            )
            publishSnapshot()
        }
    )

    override fun ensureServerRunning() {
        if (!ScanSettings.isChainLinkEnabled(context)) return
        server.start()
        ensurePersistentChannelLoop()
    }

    override fun stopServer() {
        server.stop()
        persistentChannelJob?.cancel()
        persistentChannelJob = null
    }

    override fun observeMesh(): StateFlow<ChainMeshSnapshot> = meshState.asStateFlow()

    override suspend fun refreshPeers(): ChainMeshSnapshot {
        if (!ScanSettings.isChainLinkEnabled(context)) {
            publishSnapshot(lastRefresh = System.currentTimeMillis())
            return meshState.value
        }

        server.start()
        val peers = discoverPeers().take(CHAIN_MAX_PEERS)
        val now = System.currentTimeMillis()
        val canUsePersistent = ScanSettings.isChainPersistentChannelEnabled(context)
        val sharedSecret = ScanSettings.getChainSharedSecret(context)

        peers.forEach { peer ->
            val heartbeatOk = if (canUsePersistent && peer.persistentChannelEnabled && sharedSecret.isNotBlank()) {
                sendHeartbeat(peer, sharedSecret)
            } else {
                false
            }
            mergePeerStatus(
                nodeId = peer.nodeId,
                deviceName = peer.deviceName,
                host = peer.host,
                state = if (heartbeatOk) ChainPeerState.CONNECTED else ChainPeerState.DISCOVERED,
                failure = null,
                sharedLocationLat = peer.sharedLocationLat,
                sharedLocationLon = peer.sharedLocationLon,
                sharedLocationAccuracyMeters = peer.sharedLocationAccuracyMeters,
                sharedLocationTimestampEpochMs = peer.sharedLocationTimestampEpochMs,
                lastSeen = now
            )
        }
        publishSnapshot(lastRefresh = now)
        return meshState.value
    }

    override suspend fun sendLinkRequest(host: String, message: String?): Boolean = withContext(Dispatchers.IO) {
        val target = host.trim()
        if (target.isBlank()) return@withContext false

        val payload = JSONObject().apply {
            put("requesterNodeId", nodeId)
            put("requesterDeviceName", ScanSettings.getChainDeviceName(context))
            put("requesterHost", getLocalIpv4Address()?.hostAddress ?: "")
            if (!message.isNullOrBlank()) put("message", message.trim())
        }.toString()

        val response = postJsonWithoutAuth(
            url = "http://$target:$CHAIN_PORT$CHAIN_LINK_REQUEST_PATH",
            body = payload
        ) ?: run {
            mergePeerStatus(
                nodeId = "unknown@$target",
                deviceName = null,
                host = target,
                state = ChainPeerState.FAILED,
                failure = "Link request failed",
                sharedLocationLat = null,
                sharedLocationLon = null,
                sharedLocationAccuracyMeters = null,
                sharedLocationTimestampEpochMs = null
            )
            publishSnapshot()
            return@withContext false
        }

        val responseObj = runCatching { JSONObject(response) }.getOrNull()
        val peerNodeId = responseObj?.optString("nodeId", "").orEmpty().ifBlank { "unknown@$target" }
        val peerDeviceName = responseObj?.optString("deviceName", null)
        val peerSharedLocationLat = if (responseObj?.has("sharedLocationLat") == true && !responseObj.isNull("sharedLocationLat")) {
            responseObj.optDouble("sharedLocationLat")
        } else null
        val peerSharedLocationLon = if (responseObj?.has("sharedLocationLon") == true && !responseObj.isNull("sharedLocationLon")) {
            responseObj.optDouble("sharedLocationLon")
        } else null
        val peerSharedLocationAccuracyMeters = if (responseObj?.has("sharedLocationAccuracyMeters") == true && !responseObj.isNull("sharedLocationAccuracyMeters")) {
            responseObj.optDouble("sharedLocationAccuracyMeters").toFloat()
        } else null
        val peerSharedLocationTimestampEpochMs = if (responseObj?.has("sharedLocationTimestampEpochMs") == true && !responseObj.isNull("sharedLocationTimestampEpochMs")) {
            responseObj.optLong("sharedLocationTimestampEpochMs")
        } else null
        mergePeerStatus(
            nodeId = peerNodeId,
            deviceName = peerDeviceName,
            host = target,
            state = ChainPeerState.REQUESTED,
            failure = null,
            sharedLocationLat = peerSharedLocationLat,
            sharedLocationLon = peerSharedLocationLon,
            sharedLocationAccuracyMeters = peerSharedLocationAccuracyMeters,
            sharedLocationTimestampEpochMs = peerSharedLocationTimestampEpochMs,
            markLinkRequest = true
        )
        publishSnapshot()
        true
    }

    override suspend fun syncNow(): ChainSyncStats {
        if (!ScanSettings.isChainLinkEnabled(context)) {
            server.stop()
            return ChainSyncStats(
                enabled = false,
                authConfigured = false,
                peersDiscovered = 0,
                peersSynced = 0,
                exportedRecords = 0,
                importedRecords = 0,
                failures = 0
            )
        }

        val sharedSecret = ScanSettings.getChainSharedSecret(context)
        if (sharedSecret.isBlank()) {
            return ChainSyncStats(
                enabled = true,
                authConfigured = false,
                peersDiscovered = 0,
                peersSynced = 0,
                exportedRecords = 0,
                importedRecords = 0,
                failures = 1
            )
        }

        server.start()

        val now = System.currentTimeMillis()
        val since = now - (ScanSettings.getChainSyncWindowMinutes(context) * 60_000L)
        val exportDataset = repository.listSince(since, CHAIN_MAX_DATASET)
            .map { encounter -> encounter.withExportProvenance(localNodeId = nodeId) }
        val peers = refreshPeers().peers
            .map {
                DiscoveredPeer(
                    host = it.host,
                    nodeId = it.nodeId,
                    persistentChannelEnabled = it.state == ChainPeerState.CONNECTED || it.state == ChainPeerState.REQUESTED,
                    deviceName = it.deviceName,
                    sharedLocationLat = it.sharedLocationLat,
                    sharedLocationLon = it.sharedLocationLon,
                    sharedLocationAccuracyMeters = it.sharedLocationAccuracyMeters,
                    sharedLocationTimestampEpochMs = it.sharedLocationTimestampEpochMs
                )
            }
            .take(CHAIN_MAX_PEERS)

        var peersSynced = 0
        var importedRecords = 0
        var failures = 0

        peers.forEach { peer ->
            val response = runCatching {
                syncWithPeer(
                    peer = peer,
                    sinceEpochMs = since,
                    exportDataset = exportDataset,
                    sharedSecret = sharedSecret
                )
            }.getOrNull()

            if (response == null) {
                failures += 1
                mergePeerStatus(
                    nodeId = peer.nodeId,
                    deviceName = peer.deviceName,
                    host = peer.host,
                    state = ChainPeerState.FAILED,
                    failure = "Sync failed",
                    sharedLocationLat = peer.sharedLocationLat,
                    sharedLocationLon = peer.sharedLocationLon,
                    sharedLocationAccuracyMeters = peer.sharedLocationAccuracyMeters,
                    sharedLocationTimestampEpochMs = peer.sharedLocationTimestampEpochMs
                )
            } else {
                peersSynced += 1
                importedRecords += response.imported
                mergePeerStatus(
                    nodeId = peer.nodeId,
                    deviceName = peer.deviceName,
                    host = peer.host,
                    state = ChainPeerState.CONNECTED,
                    failure = null,
                    sharedLocationLat = peer.sharedLocationLat,
                    sharedLocationLon = peer.sharedLocationLon,
                    sharedLocationAccuracyMeters = peer.sharedLocationAccuracyMeters,
                    sharedLocationTimestampEpochMs = peer.sharedLocationTimestampEpochMs,
                    markSynced = true
                )
            }
        }

        publishSnapshot(lastSync = now)

        return ChainSyncStats(
            enabled = true,
            authConfigured = true,
            peersDiscovered = peers.size,
            peersSynced = peersSynced,
            exportedRecords = exportDataset.size * peersSynced,
            importedRecords = importedRecords,
            failures = failures
        )
    }

    override suspend fun wipeMeshDataAcrossPeers(): ChainWipeStats {
        val initiatorName = ScanSettings.getChainDeviceName(context)
        val sessionId = "wipe-${System.currentTimeMillis()}-$nodeId"
        if (!ScanSettings.isChainLinkEnabled(context)) {
            runCatching {
                AppBackupManager.exportSnapshot(
                    context = context,
                    repository = repository,
                    reason = "mesh-soft-reset local-only initiated by $initiatorName"
                )
            }
            repository.clearEncounters()
            repository.clearDevices()
            ScanSettings.clearOperationalLogs(context)
            addWipeNotice(
                MeshWipeNotice(
                    sessionId = sessionId,
                    initiatorNodeId = nodeId,
                    initiatorDeviceName = initiatorName,
                    detail = "Local-only wipe completed (chain disabled).",
                    timestampEpochMs = System.currentTimeMillis()
                )
            )
            ScanSettings.completeMeshWipeGate(context)
            publishSnapshot(lastSync = System.currentTimeMillis())
            return ChainWipeStats(
                enabled = false,
                authConfigured = false,
                localCleared = true,
                peersTargeted = 0,
                peersWiped = 0,
                failures = 0
            )
        }

        val sharedSecret = ScanSettings.getChainSharedSecret(context)
        if (sharedSecret.isBlank()) {
            runCatching {
                AppBackupManager.exportSnapshot(
                    context = context,
                    repository = repository,
                    reason = "mesh-soft-reset local-only (missing mesh auth) initiated by $initiatorName"
                )
            }
            repository.clearEncounters()
            repository.clearDevices()
            ScanSettings.clearOperationalLogs(context)
            addWipeNotice(
                MeshWipeNotice(
                    sessionId = sessionId,
                    initiatorNodeId = nodeId,
                    initiatorDeviceName = initiatorName,
                    detail = "Local wipe completed; remote wipe blocked (missing chain passphrase).",
                    timestampEpochMs = System.currentTimeMillis()
                )
            )
            ScanSettings.completeMeshWipeGate(context)
            publishSnapshot(lastSync = System.currentTimeMillis())
            return ChainWipeStats(
                enabled = true,
                authConfigured = false,
                localCleared = true,
                peersTargeted = 0,
                peersWiped = 0,
                failures = 1
            )
        }

        server.start()
        ScanSettings.beginMeshWipeGate(
            context = context,
            sessionId = sessionId,
            initiatorNodeId = nodeId,
            initiatorDeviceName = initiatorName
        )
        addWipeNotice(
            MeshWipeNotice(
                sessionId = sessionId,
                initiatorNodeId = nodeId,
                initiatorDeviceName = initiatorName,
                detail = "Mesh wipe initiated by this device.",
                timestampEpochMs = System.currentTimeMillis()
            )
        )
        runCatching {
            AppBackupManager.exportSnapshot(
                context = context,
                repository = repository,
                reason = "mesh-soft-reset orchestrator session=$sessionId initiated by $initiatorName"
            )
        }
        repository.clearEncounters()
        repository.clearDevices()
        ScanSettings.clearOperationalLogs(context)

        val peers = refreshPeers().peers
            .map {
                DiscoveredPeer(
                    host = it.host,
                    nodeId = it.nodeId,
                    persistentChannelEnabled = it.state == ChainPeerState.CONNECTED || it.state == ChainPeerState.REQUESTED,
                    deviceName = it.deviceName,
                    sharedLocationLat = it.sharedLocationLat,
                    sharedLocationLon = it.sharedLocationLon,
                    sharedLocationAccuracyMeters = it.sharedLocationAccuracyMeters,
                    sharedLocationTimestampEpochMs = it.sharedLocationTimestampEpochMs
                )
            }
            .take(CHAIN_MAX_PEERS)

        var peersWiped = 0
        var failures = 0
        peers.forEach { peer ->
            val wiped = runCatching {
                sendWipeToPeer(peer, sharedSecret, sessionId, initiatorName)
            }.getOrDefault(false)

            if (wiped) {
                peersWiped += 1
                mergePeerStatus(
                    nodeId = peer.nodeId,
                    deviceName = peer.deviceName,
                    host = peer.host,
                    state = ChainPeerState.CONNECTED,
                    failure = null,
                    sharedLocationLat = peer.sharedLocationLat,
                    sharedLocationLon = peer.sharedLocationLon,
                    sharedLocationAccuracyMeters = peer.sharedLocationAccuracyMeters,
                    sharedLocationTimestampEpochMs = peer.sharedLocationTimestampEpochMs,
                    markSynced = true
                )
                addWipeNotice(
                    MeshWipeNotice(
                        sessionId = sessionId,
                        initiatorNodeId = nodeId,
                        initiatorDeviceName = initiatorName,
                        detail = "${peer.deviceName ?: peer.nodeId} acknowledged wipe.",
                        timestampEpochMs = System.currentTimeMillis()
                    )
                )
            } else {
                failures += 1
                mergePeerStatus(
                    nodeId = peer.nodeId,
                    deviceName = peer.deviceName,
                    host = peer.host,
                    state = ChainPeerState.FAILED,
                    failure = "Remote wipe failed",
                    sharedLocationLat = peer.sharedLocationLat,
                    sharedLocationLon = peer.sharedLocationLon,
                    sharedLocationAccuracyMeters = peer.sharedLocationAccuracyMeters,
                    sharedLocationTimestampEpochMs = peer.sharedLocationTimestampEpochMs
                )
                addWipeNotice(
                    MeshWipeNotice(
                        sessionId = sessionId,
                        initiatorNodeId = nodeId,
                        initiatorDeviceName = initiatorName,
                        detail = "${peer.deviceName ?: peer.nodeId} failed wipe acknowledgement.",
                        timestampEpochMs = System.currentTimeMillis()
                    )
                )
            }
        }

        if (failures == 0) {
            peers.forEach { peer ->
                runCatching {
                    sendWipeReleaseToPeer(peer, sharedSecret, sessionId, initiatorName)
                }
            }
            ScanSettings.completeMeshWipeGate(context)
            addWipeNotice(
                MeshWipeNotice(
                    sessionId = sessionId,
                    initiatorNodeId = nodeId,
                    initiatorDeviceName = initiatorName,
                    detail = "Mesh wipe completed across all targeted peers; scan gate released.",
                    timestampEpochMs = System.currentTimeMillis()
                )
            )
        } else {
            addWipeNotice(
                MeshWipeNotice(
                    sessionId = sessionId,
                    initiatorNodeId = nodeId,
                    initiatorDeviceName = initiatorName,
                    detail = "Mesh wipe incomplete; scan gate remains active until all peers complete wipe.",
                    timestampEpochMs = System.currentTimeMillis()
                )
            )
        }

        publishSnapshot(lastSync = System.currentTimeMillis())

        return ChainWipeStats(
            enabled = true,
            authConfigured = true,
            localCleared = true,
            peersTargeted = peers.size,
            peersWiped = peersWiped,
            failures = failures
        )
    }

    private fun mergePeerStatus(
        nodeId: String,
        deviceName: String?,
        host: String,
        state: ChainPeerState,
        failure: String?,
        sharedLocationLat: Double?,
        sharedLocationLon: Double?,
        sharedLocationAccuracyMeters: Float?,
        sharedLocationTimestampEpochMs: Long?,
        lastSeen: Long = System.currentTimeMillis(),
        markSynced: Boolean = false,
        markLinkRequest: Boolean = false
    ) {
        var previousState: ChainPeerState? = null
        var updatedStatus: ChainPeerStatus? = null
        synchronized(peerStatusByNode) {
            val existing = peerStatusByNode[nodeId]
            previousState = existing?.state
            val updated = ChainPeerStatus(
                nodeId = nodeId,
                deviceName = deviceName?.trim()?.ifBlank { null } ?: existing?.deviceName,
                host = host.ifBlank { existing?.host.orEmpty() },
                state = state,
                lastSeenEpochMs = lastSeen,
                lastSuccessfulSyncEpochMs = if (markSynced) lastSeen else existing?.lastSuccessfulSyncEpochMs,
                lastLinkRequestEpochMs = if (markLinkRequest) lastSeen else existing?.lastLinkRequestEpochMs,
                lastFailure = failure,
                sharedLocationLat = sharedLocationLat ?: existing?.sharedLocationLat,
                sharedLocationLon = sharedLocationLon ?: existing?.sharedLocationLon,
                sharedLocationAccuracyMeters = sharedLocationAccuracyMeters ?: existing?.sharedLocationAccuracyMeters,
                sharedLocationTimestampEpochMs = sharedLocationTimestampEpochMs ?: existing?.sharedLocationTimestampEpochMs
            )
            peerStatusByNode[nodeId] = updated
            updatedStatus = updated
        }
        maybeNotifyPeerConnectivityTransition(previousState, updatedStatus)
    }

    private fun maybeNotifyPeerConnectivityTransition(
        previous: ChainPeerState?,
        updated: ChainPeerStatus?
    ) {
        val current = updated?.state ?: return
        if (previous == current) return
        if (current != ChainPeerState.CONNECTED && previous != ChainPeerState.CONNECTED) return
        if (!hasPostNotificationsPermission(context)) return

        ensureConnectivityNotificationChannel(context)
        val peerName = updated.deviceName?.takeIf { it.isNotBlank() } ?: updated.nodeId
        val content = "$peerName @ ${updated.host}"
        val title = if (current == ChainPeerState.CONNECTED) "Peer connected" else "Peer disconnected"

        val notification = NotificationCompat.Builder(context, MESH_CONNECTIVITY_ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(content.take(180))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationId = ("mesh-peer:${updated.nodeId}:${updated.lastSeenEpochMs}:$current").hashCode()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun publishSnapshot(
        lastRefresh: Long? = null,
        lastSync: Long? = null
    ) {
        val peers = synchronized(peerStatusByNode) {
            peerStatusByNode.values.sortedByDescending { it.lastSeenEpochMs }
        }
        val requests = synchronized(incomingRequests) {
            incomingRequests.toList().sortedByDescending { it.timestampEpochMs }
        }
        val notices = synchronized(wipeNotices) {
            wipeNotices.toList().sortedByDescending { it.timestampEpochMs }
        }
        val current = meshState.value
        meshState.value = current.copy(
            localDeviceName = ScanSettings.getChainDeviceName(context),
            peers = peers,
            incomingRequests = requests,
            wipeNotices = notices,
            lastRefreshEpochMs = lastRefresh ?: current.lastRefreshEpochMs,
            lastSyncEpochMs = lastSync ?: current.lastSyncEpochMs
        )
    }

    private fun addWipeNotice(notice: MeshWipeNotice) {
        synchronized(wipeNotices) {
            wipeNotices.add(0, notice)
            if (wipeNotices.size > CHAIN_MAX_WIPE_NOTICES) {
                wipeNotices.removeAt(wipeNotices.lastIndex)
            }
        }
        maybeNotifyMeshWipeNotice(notice)
    }

    private fun maybeNotifyMeshWipeNotice(notice: MeshWipeNotice) {
        if (!hasPostNotificationsPermission(context)) return
        ensureMeshWipeNotificationChannel(context)

        val initiator = notice.initiatorDeviceName?.takeIf { it.isNotBlank() } ?: notice.initiatorNodeId
        val title = when {
            notice.detail.contains("incomplete", ignoreCase = true) -> "Mesh wipe incomplete"
            notice.detail.contains("completed", ignoreCase = true) -> "Mesh wipe completed"
            notice.detail.contains("release", ignoreCase = true) -> "Mesh wipe released"
            notice.detail.contains("received", ignoreCase = true) -> "Mesh wipe received"
            notice.detail.contains("initiated", ignoreCase = true) -> "Mesh wipe initiated"
            else -> "Mesh wipe update"
        }
        val content = "$initiator • ${notice.detail}"

        val notification = NotificationCompat.Builder(context, MESH_WIPE_ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content.take(180))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationId = ("mesh-wipe:${notice.sessionId}:${notice.timestampEpochMs}:${notice.detail}").hashCode()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private suspend fun discoverPeers(): List<DiscoveredPeer> = withContext(Dispatchers.IO) {
        val localAddress = getLocalIpv4Address() ?: return@withContext emptyList()
        val octets = localAddress.hostAddress?.split('.') ?: return@withContext emptyList()
        if (octets.size != 4) return@withContext emptyList()

        val hostOctet = octets.last().toIntOrNull() ?: return@withContext emptyList()
        val subnetPrefix = "${octets[0]}.${octets[1]}.${octets[2]}"
        val candidates = (1..254)
            .asSequence()
            .filter { it != hostOctet }
            .map { "$subnetPrefix.$it" }
            .toList()

        val semaphore = Semaphore(CHAIN_DISCOVERY_CONCURRENCY)
        val peers = Collections.synchronizedList(mutableListOf<DiscoveredPeer>())

        candidates.map { host ->
            scope.async {
                semaphore.withPermit {
                    val hello = fetchHello(host) ?: return@withPermit
                    if (hello.nodeId == nodeId) return@withPermit
                    peers += DiscoveredPeer(
                        host = host,
                        nodeId = hello.nodeId,
                        persistentChannelEnabled = hello.persistentChannelEnabled,
                        deviceName = hello.deviceName,
                        sharedLocationLat = hello.sharedLocationLat,
                        sharedLocationLon = hello.sharedLocationLon,
                        sharedLocationAccuracyMeters = hello.sharedLocationAccuracyMeters,
                        sharedLocationTimestampEpochMs = hello.sharedLocationTimestampEpochMs
                    )
                }
            }
        }.awaitAll()

        peers.distinctBy { it.nodeId }
    }

    private suspend fun syncWithPeer(
        peer: DiscoveredPeer,
        sinceEpochMs: Long,
        exportDataset: List<Encounter>,
        sharedSecret: String
    ): PeerSyncResult? = withContext(Dispatchers.IO) {
        val request = ChainLinkJson.encodeSyncRequest(
            ChainSyncRequest(
                requesterNodeId = nodeId,
                sinceEpochMs = sinceEpochMs,
                encounters = exportDataset
            )
        )

        val responseRaw = postJson(
            url = "http://${peer.host}:$CHAIN_PORT$CHAIN_SYNC_PATH",
            body = request,
            auth = ChainAuthHeaders.sign(
                sharedSecret = sharedSecret,
                nodeId = nodeId,
                method = "POST",
                path = CHAIN_SYNC_PATH,
                body = request
            )
        ) ?: return@withContext null

        val response = ChainLinkJson.decodeSyncResponse(responseRaw) ?: return@withContext null
        mergePeerStatus(
            nodeId = response.responderNodeId,
            deviceName = response.responderDeviceName,
            host = peer.host,
            state = ChainPeerState.CONNECTED,
            failure = null,
            sharedLocationLat = peer.sharedLocationLat,
            sharedLocationLon = peer.sharedLocationLon,
            sharedLocationAccuracyMeters = peer.sharedLocationAccuracyMeters,
            sharedLocationTimestampEpochMs = peer.sharedLocationTimestampEpochMs,
            markSynced = true
        )
        val receivedAt = System.currentTimeMillis()
        val normalizedRemote = response.encounters.map { incoming ->
            incoming.withInboundProvenance(
                senderNodeId = response.responderNodeId,
                receivedAtEpochMs = receivedAt
            ).copy(id = 0)
        }
        val imported = repository.insertBatch(normalizedRemote)
        PeerSyncResult(imported = imported)
    }

    private suspend fun sendHeartbeat(peer: DiscoveredPeer, sharedSecret: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("nodeId", nodeId)
            put("timestampEpochMs", System.currentTimeMillis())
        }.toString()

        val response = postJson(
            url = "http://${peer.host}:$CHAIN_PORT$CHAIN_HEARTBEAT_PATH",
            body = body,
            auth = ChainAuthHeaders.sign(
                sharedSecret = sharedSecret,
                nodeId = nodeId,
                method = "POST",
                path = CHAIN_HEARTBEAT_PATH,
                body = body
            )
        ) ?: return@withContext false

        runCatching { JSONObject(response).optBoolean("ok", false) }.getOrDefault(false)
    }

    private suspend fun sendWipeToPeer(
        peer: DiscoveredPeer,
        sharedSecret: String,
        sessionId: String,
        initiatorDeviceName: String
    ): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("requesterNodeId", nodeId)
            put("requesterDeviceName", initiatorDeviceName)
            put("sessionId", sessionId)
            put("wipeEncounters", true)
            put("wipeDevices", true)
            put("wipeLogs", true)
            put("timestampEpochMs", System.currentTimeMillis())
        }.toString()

        val response = postJson(
            url = "http://${peer.host}:$CHAIN_PORT$CHAIN_WIPE_PATH",
            body = body,
            auth = ChainAuthHeaders.sign(
                sharedSecret = sharedSecret,
                nodeId = nodeId,
                method = "POST",
                path = CHAIN_WIPE_PATH,
                body = body
            )
        ) ?: return@withContext false

        runCatching { JSONObject(response).optBoolean("ok", false) }.getOrDefault(false)
    }

    private suspend fun sendWipeReleaseToPeer(
        peer: DiscoveredPeer,
        sharedSecret: String,
        sessionId: String,
        initiatorDeviceName: String
    ): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("requesterNodeId", nodeId)
            put("requesterDeviceName", initiatorDeviceName)
            put("sessionId", sessionId)
            put("timestampEpochMs", System.currentTimeMillis())
        }.toString()

        val response = postJson(
            url = "http://${peer.host}:$CHAIN_PORT$CHAIN_WIPE_RELEASE_PATH",
            body = body,
            auth = ChainAuthHeaders.sign(
                sharedSecret = sharedSecret,
                nodeId = nodeId,
                method = "POST",
                path = CHAIN_WIPE_RELEASE_PATH,
                body = body
            )
        ) ?: return@withContext false

        runCatching { JSONObject(response).optBoolean("ok", false) }.getOrDefault(false)
    }

    private fun fetchHello(host: String): ChainHello? {
        val raw = getJson("http://$host:$CHAIN_PORT$CHAIN_HELLO_PATH") ?: return null
        return ChainLinkJson.decodeHello(raw)
    }

    private fun ensurePersistentChannelLoop() {
        if (!ScanSettings.isChainPersistentChannelEnabled(context)) {
            persistentChannelJob?.cancel()
            persistentChannelJob = null
            return
        }
        if (persistentChannelJob?.isActive == true) return

        persistentChannelJob = scope.launch(Dispatchers.IO) {
            while (isActive && ScanSettings.isChainLinkEnabled(context)) {
                if (ScanSettings.isChainPersistentChannelEnabled(context)) {
                    runCatching { refreshPeers() }
                    val interval = ScanSettings.getChainHeartbeatIntervalSeconds(context)
                    delay(interval * 1000L)
                } else {
                    delay(1000L)
                }
            }
        }
    }

    private fun getJson(url: String): String? {
        val connection = (URL(url).openConnection() as? HttpURLConnection) ?: return null
        return runCatching {
            connection.connectTimeout = CHAIN_HTTP_TIMEOUT_MS
            connection.readTimeout = CHAIN_HTTP_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.inputStream.bufferedReader().use(BufferedReader::readText)
        }.getOrNull().also {
            connection.disconnect()
        }
    }

    private fun postJson(url: String, body: String, auth: ChainAuthHeaders): String? {
        val connection = (URL(url).openConnection() as? HttpURLConnection) ?: return null
        return runCatching {
            connection.connectTimeout = CHAIN_HTTP_TIMEOUT_MS
            connection.readTimeout = CHAIN_HTTP_TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty(HEADER_AUTH_NODE_ID, auth.nodeId)
            connection.setRequestProperty(HEADER_AUTH_TIMESTAMP_MS, auth.timestampMs.toString())
            connection.setRequestProperty(HEADER_AUTH_SIGNATURE, auth.signature)
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(body)
                writer.flush()
            }

            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream.bufferedReader().use(BufferedReader::readText)
            }
        }.getOrNull().also {
            connection.disconnect()
        }
    }

    private fun postJsonWithoutAuth(url: String, body: String): String? {
        val connection = (URL(url).openConnection() as? HttpURLConnection) ?: return null
        return runCatching {
            connection.connectTimeout = CHAIN_HTTP_TIMEOUT_MS
            connection.readTimeout = CHAIN_HTTP_TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(body)
                writer.flush()
            }

            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream.bufferedReader().use(BufferedReader::readText)
            }
        }.getOrNull().also {
            connection.disconnect()
        }
    }

    private fun getLocalIpv4Address(): Inet4Address? {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null
        val wifiCandidates = mutableListOf<Inet4Address>()
        val otherCandidates = mutableListOf<Inet4Address>()

        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            val isWifiInterface = networkInterface.name.contains("wlan", ignoreCase = true) ||
                networkInterface.displayName.contains("wlan", ignoreCase = true) ||
                networkInterface.name.contains("wifi", ignoreCase = true) ||
                networkInterface.displayName.contains("wifi", ignoreCase = true)

            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && !address.isLoopbackAddress && address.isSiteLocalAddress) {
                    if (isWifiInterface) {
                        wifiCandidates += address
                    } else {
                        otherCandidates += address
                    }
                }
            }
        }

        return wifiCandidates.firstOrNull() ?: otherCandidates.firstOrNull()
    }
}

private data class DiscoveredPeer(
    val host: String,
    val nodeId: String,
    val persistentChannelEnabled: Boolean,
    val deviceName: String?,
    val sharedLocationLat: Double?,
    val sharedLocationLon: Double?,
    val sharedLocationAccuracyMeters: Float?,
    val sharedLocationTimestampEpochMs: Long?
)

private data class SharedLocationPayload(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float?,
    val timestampEpochMs: Long
)

private fun currentSharedLocationPayload(context: Context): SharedLocationPayload? {
    if (!ScanSettings.isChainSharePreciseLocationEnabled(context)) return null
    val current = LocationSnapshotProvider.read(context) ?: return null
    if (current.lat !in -90.0..90.0 || current.lon !in -180.0..180.0) return null
    return SharedLocationPayload(
        lat = current.lat,
        lon = current.lon,
        accuracyMeters = null,
        timestampEpochMs = System.currentTimeMillis()
    )
}

private data class PeerSyncResult(
    val imported: Int
)

private class ChainLinkServer(
    private val nodeId: String,
    private val repository: EncounterRepository,
    private val context: Context,
    private val scope: CoroutineScope,
    private val authSecretProvider: () -> String,
    private val onWipeNotice: (MeshWipeNotice) -> Unit,
    private val onIncomingLinkRequest: (IncomingLinkRequest) -> Unit
) {
    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var isRunning: Boolean = false

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch(Dispatchers.IO) {
            runCatching {
                val socket = ServerSocket(CHAIN_PORT)
                socket.reuseAddress = true
                socket.soTimeout = CHAIN_SERVER_ACCEPT_TIMEOUT_MS
                serverSocket = socket

                while (isActive && isRunning) {
                    val client = try {
                        socket.accept()
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    scope.launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                }
            }.onFailure {
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private suspend fun handleClient(client: Socket) {
        client.use { socket ->
            runCatching {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

                val requestLine = reader.readLine() ?: return@runCatching
                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    writeHttpResponse(writer, statusCode = 400, body = "bad request")
                    return@runCatching
                }
                val method = parts[0].uppercase()
                val path = parts[1]

                var contentLength = 0
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val headerLine = reader.readLine() ?: break
                    if (headerLine.isBlank()) break
                    val split = headerLine.split(":", limit = 2)
                    if (split.size == 2 && split[0].trim().equals("Content-Length", ignoreCase = true)) {
                        contentLength = split[1].trim().toIntOrNull() ?: 0
                    }
                    if (split.size == 2) {
                        headers[split[0].trim().lowercase()] = split[1].trim()
                    }
                }

                val body = if (contentLength > 0) {
                    val chars = CharArray(contentLength)
                    var offset = 0
                    while (offset < contentLength) {
                        val read = reader.read(chars, offset, contentLength - offset)
                        if (read <= 0) break
                        offset += read
                    }
                    String(chars, 0, offset)
                } else {
                    ""
                }

                if (method == "GET" && path == CHAIN_HELLO_PATH) {
                    val sharedLocation = currentSharedLocationPayload(context)
                    val hello = ChainLinkJson.encodeHello(
                        nodeId = nodeId,
                        persistentChannelEnabled = ScanSettings.isChainPersistentChannelEnabled(context),
                        deviceName = ScanSettings.getChainDeviceName(context),
                        sharedLocationLat = sharedLocation?.lat,
                        sharedLocationLon = sharedLocation?.lon,
                        sharedLocationAccuracyMeters = sharedLocation?.accuracyMeters,
                        sharedLocationTimestampEpochMs = sharedLocation?.timestampEpochMs
                    )
                    writeHttpResponse(writer, statusCode = 200, body = hello)
                    return@runCatching
                }

                if (method == "POST" && path == CHAIN_SYNC_PATH) {
                    if (!ScanSettings.isChainLinkEnabled(context)) {
                        writeHttpResponse(writer, statusCode = 403, body = "disabled")
                        return@runCatching
                    }

                    val sharedSecret = authSecretProvider().trim()
                    if (sharedSecret.isBlank()) {
                        writeHttpResponse(writer, statusCode = 403, body = "missing auth configuration")
                        return@runCatching
                    }

                    val authNodeId = headers[HEADER_AUTH_NODE_ID.lowercase()].orEmpty()
                    val authTimestampMs = headers[HEADER_AUTH_TIMESTAMP_MS.lowercase()]?.toLongOrNull()
                    val authSignature = headers[HEADER_AUTH_SIGNATURE.lowercase()].orEmpty()
                    val authValid = ChainAuthHeaders.verify(
                        sharedSecret = sharedSecret,
                        nodeId = authNodeId,
                        method = method,
                        path = path,
                        body = body,
                        timestampMs = authTimestampMs,
                        signature = authSignature
                    )
                    if (!authValid) {
                        writeHttpResponse(writer, statusCode = 401, body = "unauthorized")
                        return@runCatching
                    }

                    val request = ChainLinkJson.decodeSyncRequest(body)
                    if (request == null) {
                        writeHttpResponse(writer, statusCode = 400, body = "invalid payload")
                        return@runCatching
                    }
                    if (request.requesterNodeId != authNodeId) {
                        writeHttpResponse(writer, statusCode = 401, body = "requester mismatch")
                        return@runCatching
                    }

                    val receivedAt = System.currentTimeMillis()
                    val normalizedRemote = request.encounters.map { incoming ->
                        incoming.withInboundProvenance(
                            senderNodeId = request.requesterNodeId,
                            receivedAtEpochMs = receivedAt
                        ).copy(id = 0)
                    }
                    val importedCount = repository.insertBatch(normalizedRemote)
                    val localDataset = repository
                        .listSince(request.sinceEpochMs, CHAIN_MAX_DATASET)
                        .map { encounter -> encounter.withExportProvenance(localNodeId = nodeId) }

                    val response = ChainLinkJson.encodeSyncResponse(
                        ChainSyncResponse(
                            responderNodeId = nodeId,
                            responderDeviceName = ScanSettings.getChainDeviceName(context),
                            importedCount = importedCount,
                            encounters = localDataset
                        )
                    )
                    writeHttpResponse(writer, statusCode = 200, body = response)
                    return@runCatching
                }

                if (method == "POST" && path == CHAIN_HEARTBEAT_PATH) {
                    if (!ScanSettings.isChainLinkEnabled(context)) {
                        writeHttpResponse(writer, statusCode = 403, body = "disabled")
                        return@runCatching
                    }

                    val sharedSecret = authSecretProvider().trim()
                    if (sharedSecret.isBlank()) {
                        writeHttpResponse(writer, statusCode = 403, body = "missing auth configuration")
                        return@runCatching
                    }

                    val authNodeId = headers[HEADER_AUTH_NODE_ID.lowercase()].orEmpty()
                    val authTimestampMs = headers[HEADER_AUTH_TIMESTAMP_MS.lowercase()]?.toLongOrNull()
                    val authSignature = headers[HEADER_AUTH_SIGNATURE.lowercase()].orEmpty()
                    val authValid = ChainAuthHeaders.verify(
                        sharedSecret = sharedSecret,
                        nodeId = authNodeId,
                        method = method,
                        path = path,
                        body = body,
                        timestampMs = authTimestampMs,
                        signature = authSignature
                    )
                    if (!authValid) {
                        writeHttpResponse(writer, statusCode = 401, body = "unauthorized")
                        return@runCatching
                    }

                    val heartbeatAck = JSONObject().apply {
                        val sharedLocation = currentSharedLocationPayload(context)
                        put("ok", true)
                        put("nodeId", nodeId)
                        put("deviceName", ScanSettings.getChainDeviceName(context))
                        put("persistentChannelEnabled", ScanSettings.isChainPersistentChannelEnabled(context))
                        if (sharedLocation != null) {
                            put("sharedLocationLat", sharedLocation.lat)
                            put("sharedLocationLon", sharedLocation.lon)
                            if (sharedLocation.accuracyMeters != null) {
                                put("sharedLocationAccuracyMeters", sharedLocation.accuracyMeters)
                            }
                            put("sharedLocationTimestampEpochMs", sharedLocation.timestampEpochMs)
                        }
                    }.toString()
                    writeHttpResponse(writer, statusCode = 200, body = heartbeatAck)
                    return@runCatching
                }

                if (method == "POST" && path == CHAIN_WIPE_PATH) {
                    if (!ScanSettings.isChainLinkEnabled(context)) {
                        writeHttpResponse(writer, statusCode = 403, body = "disabled")
                        return@runCatching
                    }

                    val sharedSecret = authSecretProvider().trim()
                    if (sharedSecret.isBlank()) {
                        writeHttpResponse(writer, statusCode = 403, body = "missing auth configuration")
                        return@runCatching
                    }

                    val authNodeId = headers[HEADER_AUTH_NODE_ID.lowercase()].orEmpty()
                    val authTimestampMs = headers[HEADER_AUTH_TIMESTAMP_MS.lowercase()]?.toLongOrNull()
                    val authSignature = headers[HEADER_AUTH_SIGNATURE.lowercase()].orEmpty()
                    val authValid = ChainAuthHeaders.verify(
                        sharedSecret = sharedSecret,
                        nodeId = authNodeId,
                        method = method,
                        path = path,
                        body = body,
                        timestampMs = authTimestampMs,
                        signature = authSignature
                    )
                    if (!authValid) {
                        writeHttpResponse(writer, statusCode = 401, body = "unauthorized")
                        return@runCatching
                    }

                    val requestObj = runCatching { JSONObject(body) }.getOrNull()
                    val requesterNodeId = requestObj?.optString("requesterNodeId", "")?.trim().orEmpty()
                    val requesterDeviceName = requestObj?.optString("requesterDeviceName", null)?.trim()?.ifBlank { null }
                    val sessionId = requestObj?.optString("sessionId", "")?.trim().orEmpty()
                    if (requesterNodeId.isBlank() || requesterNodeId != authNodeId) {
                        writeHttpResponse(writer, statusCode = 401, body = "requester mismatch")
                        return@runCatching
                    }
                    if (sessionId.isBlank()) {
                        writeHttpResponse(writer, statusCode = 400, body = "missing session")
                        return@runCatching
                    }

                    val wipeEncounters = requestObj?.optBoolean("wipeEncounters", true) ?: true
                    val wipeDevices = requestObj?.optBoolean("wipeDevices", true) ?: true
                    val wipeLogs = requestObj?.optBoolean("wipeLogs", true) ?: true

                    ScanSettings.beginMeshWipeGate(
                        context = context,
                        sessionId = sessionId,
                        initiatorNodeId = requesterNodeId,
                        initiatorDeviceName = requesterDeviceName
                    )
                    runCatching {
                        AppBackupManager.exportSnapshot(
                            context = context,
                            repository = repository,
                            reason = "mesh-soft-reset peer backup session=$sessionId initiated by ${requesterDeviceName ?: requesterNodeId}"
                        )
                    }
                    onWipeNotice(
                        MeshWipeNotice(
                            sessionId = sessionId,
                            initiatorNodeId = requesterNodeId,
                            initiatorDeviceName = requesterDeviceName,
                            detail = "Wipe command received from ${requesterDeviceName ?: requesterNodeId}; local wipe started.",
                            timestampEpochMs = System.currentTimeMillis()
                        )
                    )

                    if (wipeEncounters) {
                        repository.clearEncounters()
                    }
                    if (wipeDevices) {
                        repository.clearDevices()
                    }
                    if (wipeLogs) {
                        ScanSettings.clearOperationalLogs(context)
                    }

                    onWipeNotice(
                        MeshWipeNotice(
                            sessionId = sessionId,
                            initiatorNodeId = requesterNodeId,
                            initiatorDeviceName = requesterDeviceName,
                            detail = "Local wipe completed; waiting for mesh release.",
                            timestampEpochMs = System.currentTimeMillis()
                        )
                    )

                    val response = JSONObject().apply {
                        put("ok", true)
                        put("nodeId", nodeId)
                        put("wipedEncounters", wipeEncounters)
                        put("wipedDevices", wipeDevices)
                    }.toString()
                    writeHttpResponse(writer, statusCode = 200, body = response)
                    return@runCatching
                }

                if (method == "POST" && path == CHAIN_WIPE_RELEASE_PATH) {
                    if (!ScanSettings.isChainLinkEnabled(context)) {
                        writeHttpResponse(writer, statusCode = 403, body = "disabled")
                        return@runCatching
                    }

                    val sharedSecret = authSecretProvider().trim()
                    if (sharedSecret.isBlank()) {
                        writeHttpResponse(writer, statusCode = 403, body = "missing auth configuration")
                        return@runCatching
                    }

                    val authNodeId = headers[HEADER_AUTH_NODE_ID.lowercase()].orEmpty()
                    val authTimestampMs = headers[HEADER_AUTH_TIMESTAMP_MS.lowercase()]?.toLongOrNull()
                    val authSignature = headers[HEADER_AUTH_SIGNATURE.lowercase()].orEmpty()
                    val authValid = ChainAuthHeaders.verify(
                        sharedSecret = sharedSecret,
                        nodeId = authNodeId,
                        method = method,
                        path = path,
                        body = body,
                        timestampMs = authTimestampMs,
                        signature = authSignature
                    )
                    if (!authValid) {
                        writeHttpResponse(writer, statusCode = 401, body = "unauthorized")
                        return@runCatching
                    }

                    val requestObj = runCatching { JSONObject(body) }.getOrNull()
                    val requesterNodeId = requestObj?.optString("requesterNodeId", "")?.trim().orEmpty()
                    val requesterDeviceName = requestObj?.optString("requesterDeviceName", null)?.trim()?.ifBlank { null }
                    val sessionId = requestObj?.optString("sessionId", "")?.trim().orEmpty()
                    if (requesterNodeId.isBlank() || requesterNodeId != authNodeId || sessionId.isBlank()) {
                        writeHttpResponse(writer, statusCode = 401, body = "requester mismatch")
                        return@runCatching
                    }

                    ScanSettings.completeMeshWipeGate(context)
                    onWipeNotice(
                        MeshWipeNotice(
                            sessionId = sessionId,
                            initiatorNodeId = requesterNodeId,
                            initiatorDeviceName = requesterDeviceName,
                            detail = "Mesh wipe release received from ${requesterDeviceName ?: requesterNodeId}; scanning resumed.",
                            timestampEpochMs = System.currentTimeMillis()
                        )
                    )

                    val response = JSONObject().apply {
                        put("ok", true)
                        put("nodeId", nodeId)
                        put("sessionId", sessionId)
                    }.toString()
                    writeHttpResponse(writer, statusCode = 200, body = response)
                    return@runCatching
                }

                if (method == "POST" && path == CHAIN_LINK_REQUEST_PATH) {
                    if (!ScanSettings.isChainLinkEnabled(context)) {
                        writeHttpResponse(writer, statusCode = 403, body = "disabled")
                        return@runCatching
                    }

                    val requestObj = runCatching { JSONObject(body) }.getOrNull()
                    val requesterNodeId = requestObj?.optString("requesterNodeId", "")?.trim().orEmpty()
                    val requesterDeviceName = requestObj?.optString("requesterDeviceName", null)
                    val requesterHost = requestObj?.optString("requesterHost", "")?.trim().orEmpty()
                    val message = requestObj?.optString("message", "")?.trim()?.ifBlank { null }
                    if (requesterNodeId.isBlank()) {
                        writeHttpResponse(writer, statusCode = 400, body = "invalid link request")
                        return@runCatching
                    }

                    onIncomingLinkRequest(
                        IncomingLinkRequest(
                            requesterNodeId = requesterNodeId,
                            requesterDeviceName = requesterDeviceName,
                            requesterHost = requesterHost,
                            message = message,
                            timestampEpochMs = System.currentTimeMillis()
                        )
                    )

                    val response = JSONObject().apply {
                        val sharedLocation = currentSharedLocationPayload(context)
                        put("accepted", true)
                        put("nodeId", nodeId)
                        put("deviceName", ScanSettings.getChainDeviceName(context))
                        put("authRequired", authSecretProvider().trim().isNotBlank())
                        if (sharedLocation != null) {
                            put("sharedLocationLat", sharedLocation.lat)
                            put("sharedLocationLon", sharedLocation.lon)
                            if (sharedLocation.accuracyMeters != null) {
                                put("sharedLocationAccuracyMeters", sharedLocation.accuracyMeters)
                            }
                            put("sharedLocationTimestampEpochMs", sharedLocation.timestampEpochMs)
                        }
                    }.toString()
                    writeHttpResponse(writer, statusCode = 200, body = response)
                    return@runCatching
                }

                writeHttpResponse(writer, statusCode = 404, body = "not found")
            }
        }
    }

    private fun writeHttpResponse(
        writer: BufferedWriter,
        statusCode: Int,
        body: String
    ) {
        val statusText = when (statusCode) {
            200 -> "OK"
            401 -> "Unauthorized"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Error"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        writer.write("HTTP/1.1 $statusCode $statusText\r\n")
        writer.write("Content-Type: application/json\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("Connection: close\r\n")
        writer.write("\r\n")
        writer.write(body)
        writer.flush()
    }
}

private fun hasPostNotificationsPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

private fun ensureConnectivityNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val existing = manager.getNotificationChannel(MESH_CONNECTIVITY_ALERT_CHANNEL_ID)
    if (existing != null) return
    val channel = NotificationChannel(
        MESH_CONNECTIVITY_ALERT_CHANNEL_ID,
        "Mesh Connectivity Alerts",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Notifies when mesh peers connect or disconnect."
    }
    manager.createNotificationChannel(channel)
}

private fun ensureMeshWipeNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val existing = manager.getNotificationChannel(MESH_WIPE_ALERT_CHANNEL_ID)
    if (existing != null) return
    val channel = NotificationChannel(
        MESH_WIPE_ALERT_CHANNEL_ID,
        "Mesh Wipe Alerts",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Notifies when mesh-wide wipe/reset operations are initiated, received, and completed."
    }
    manager.createNotificationChannel(channel)
}

private data class ChainAuthHeaders(
    val nodeId: String,
    val timestampMs: Long,
    val signature: String
) {
    companion object {
        fun sign(
            sharedSecret: String,
            nodeId: String,
            method: String,
            path: String,
            body: String
        ): ChainAuthHeaders {
            val timestampMs = System.currentTimeMillis()
            val canonical = canonicalPayload(method, path, nodeId, timestampMs, body)
            return ChainAuthHeaders(
                nodeId = nodeId,
                timestampMs = timestampMs,
                signature = hmacSha256Hex(sharedSecret, canonical)
            )
        }

        fun verify(
            sharedSecret: String,
            nodeId: String,
            method: String,
            path: String,
            body: String,
            timestampMs: Long?,
            signature: String
        ): Boolean {
            if (nodeId.isBlank() || signature.isBlank() || timestampMs == null) return false
            val now = System.currentTimeMillis()
            if (abs(now - timestampMs) > CHAIN_MAX_AUTH_CLOCK_SKEW_MS) return false
            val canonical = canonicalPayload(method, path, nodeId, timestampMs, body)
            val expected = hmacSha256Hex(sharedSecret, canonical)
            return secureEquals(expected, signature)
        }

        private fun canonicalPayload(
            method: String,
            path: String,
            nodeId: String,
            timestampMs: Long,
            body: String
        ): String {
            val normalizedBody = runCatching { JSONObject(body).toString() }.getOrElse { body.trim() }
            return listOf(method.uppercase(), path, nodeId, timestampMs.toString(), normalizedBody)
                .joinToString("\n")
        }

        private fun hmacSha256Hex(secret: String, payload: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val bytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
            return bytes.joinToString(separator = "") { "%02x".format(it) }
        }

        private fun secureEquals(left: String, right: String): Boolean {
            val leftBytes = left.toByteArray(Charsets.UTF_8)
            val rightBytes = right.toByteArray(Charsets.UTF_8)
            return java.security.MessageDigest.isEqual(leftBytes, rightBytes)
        }
    }
}

private const val CHAIN_PORT = 18777
private const val CHAIN_HELLO_PATH = "/argus/v1/hello"
private const val CHAIN_SYNC_PATH = "/argus/v1/sync"
private const val CHAIN_HEARTBEAT_PATH = "/argus/v1/heartbeat"
private const val CHAIN_LINK_REQUEST_PATH = "/argus/v1/link-request"
private const val CHAIN_WIPE_PATH = "/argus/v1/wipe"
private const val CHAIN_WIPE_RELEASE_PATH = "/argus/v1/wipe-release"
private const val HEADER_AUTH_NODE_ID = "X-Argus-Auth-Node"
private const val HEADER_AUTH_TIMESTAMP_MS = "X-Argus-Auth-Timestamp-Ms"
private const val HEADER_AUTH_SIGNATURE = "X-Argus-Auth-Signature"
private const val CHAIN_MAX_PEERS = 24
private const val CHAIN_MAX_DATASET = 5000
private const val CHAIN_HTTP_TIMEOUT_MS = 900
private const val CHAIN_DISCOVERY_CONCURRENCY = 24
private const val CHAIN_SERVER_ACCEPT_TIMEOUT_MS = 1000
private const val CHAIN_MAX_AUTH_CLOCK_SKEW_MS = 2 * 60 * 1000L
private const val CHAIN_MAX_INCOMING_REQUESTS = 50
private const val CHAIN_MAX_WIPE_NOTICES = 80
private const val MESH_WIPE_ALERT_CHANNEL_ID = "argus_mesh_wipe_alerts"
private const val MESH_CONNECTIVITY_ALERT_CHANNEL_ID = "argus_mesh_connectivity_alerts"

private fun Encounter.withExportProvenance(localNodeId: String): Encounter {
    val isLocal = provenance == EncounterProvenance.LOCAL
    val existingPath = decodeProvenancePath(provenancePathNodeIds).toMutableList()
    val originNodeId = when {
        !provenanceOriginNodeId.isNullOrBlank() -> provenanceOriginNodeId
        isLocal -> localNodeId
        !provenanceNodeId.isNullOrBlank() -> provenanceNodeId
        else -> localNodeId
    }

    if (existingPath.isEmpty()) {
        existingPath += originNodeId.orEmpty()
    }
    if (existingPath.lastOrNull() != localNodeId) {
        existingPath += localNodeId
    }

    val hopCount = if (isLocal) {
        0
    } else {
        (existingPath.size - 1).coerceAtLeast(1)
    }

    return copy(
        encounterFingerprint = encounterFingerprint ?: computeEncounterFingerprint(this),
        provenance = if (isLocal) EncounterProvenance.LOCAL else EncounterProvenance.CHAIN_LINKED,
        provenanceNodeId = provenanceNodeId,
        provenanceOriginNodeId = originNodeId,
        provenancePathNodeIds = encodeProvenancePath(existingPath),
        provenanceReceivedAtEpochMs = provenanceReceivedAtEpochMs,
        provenanceHopCount = hopCount
    )
}

private fun Encounter.withInboundProvenance(
    senderNodeId: String,
    receivedAtEpochMs: Long
): Encounter {
    val existingPath = decodeProvenancePath(provenancePathNodeIds).toMutableList()
    val inferredOrigin = when {
        !provenanceOriginNodeId.isNullOrBlank() -> provenanceOriginNodeId
        !provenanceNodeId.isNullOrBlank() -> provenanceNodeId
        provenance == EncounterProvenance.LOCAL -> senderNodeId
        else -> senderNodeId
    }
    if (existingPath.isEmpty()) {
        existingPath += inferredOrigin.orEmpty()
    }
    if (existingPath.lastOrNull() != senderNodeId) {
        existingPath += senderNodeId
    }

    val normalizedPath = existingPath
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .fold(mutableListOf<String>()) { acc, node ->
            if (acc.lastOrNull() != node) acc += node
            acc
        }

    val hops = (normalizedPath.size - 1).coerceAtLeast(1)

    return copy(
        encounterFingerprint = encounterFingerprint ?: computeEncounterFingerprint(this),
        provenance = EncounterProvenance.CHAIN_LINKED,
        provenanceNodeId = senderNodeId,
        provenanceOriginNodeId = inferredOrigin,
        provenancePathNodeIds = encodeProvenancePath(normalizedPath),
        provenanceReceivedAtEpochMs = receivedAtEpochMs,
        provenanceHopCount = hops
    )
}

private fun decodeProvenancePath(raw: String?): List<String> =
    raw
        ?.split("|")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

private fun encodeProvenancePath(path: List<String>): String? {
    val normalized = path
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .fold(mutableListOf<String>()) { acc, node ->
            if (acc.lastOrNull() != node) acc += node
            acc
        }
    return normalized.takeIf { it.isNotEmpty() }?.joinToString("|")
}
