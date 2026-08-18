package dev.argus.tracker

import android.Manifest
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.argus.tracker.domain.SourceCatalog
import dev.argus.tracker.sensing.LocationSnapshotProvider
import dev.argus.tracker.sensing.NfcScanner
import dev.argus.tracker.sensing.NfcTagIngestStore
import dev.argus.tracker.ui.ArgusApp
import dev.argus.tracker.ui.releaseMapUiMemory
import dev.argus.tracker.worker.ScanSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    companion object {
        private const val ACTION_PIP_ZOOM_IN = "dev.argus.tracker.action.PIP_ZOOM_IN"
        private const val ACTION_PIP_ZOOM_OUT = "dev.argus.tracker.action.PIP_ZOOM_OUT"
        private const val ACTION_PIP_OPEN_APP = "dev.argus.tracker.action.PIP_OPEN_APP"
    }

    private var currentIntent by mutableStateOf<Intent?>(null)
    private var inPictureInPictureModeState by mutableStateOf(false)
    private var nfcAdapter: NfcAdapter? = null

    private val nfcReaderCallback = NfcAdapter.ReaderCallback { tag ->
        ingestNfcTag(tag)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        currentIntent = intent
        ingestNfcIntent(intent)
        requestRequiredPermissionsIfNeeded()
        enableEdgeToEdge()
        setContent {
            ArgusApp(
                notificationIntent = currentIntent,
                inPictureInPictureMode = inPictureInPictureModeState
            )
        }
    }

    override fun onResume() {
        super.onResume()
        enableNfcReaderModeIfAvailable()
    }

    override fun onPause() {
        disableNfcReaderModeIfAvailable()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePipActionIntent(intent)
        setIntent(intent)
        ingestNfcIntent(intent)
        currentIntent = intent
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        maybeEnterStickyCompassPip()
    }

    override fun onPictureInPictureRequested(): Boolean {
        return maybeEnterStickyCompassPip() || super.onPictureInPictureRequested()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPictureModeState = isInPictureInPictureMode
    }

    override fun onLowMemory() {
        releaseMapUiMemory(context = applicationContext, level = ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        releaseMapUiMemory(context = applicationContext, level = level)
        super.onTrimMemory(level)
    }

    private fun ingestNfcIntent(intent: Intent?) {
        if (!ScanSettings.isNfcSensorEnabled(this)) return

        val payloads = NfcTagIngestStore.ingestFromIntent(
            context = this,
            intent = intent,
            persistForScanner = false
        )
        if (payloads.isEmpty()) return

        val location = LocationSnapshotProvider.read(this)
        val encounters = payloads.mapIndexed { index, payload ->
            NfcScanner.buildEncounterFromPayload(
                payload = payload,
                location = location,
                fallbackIndex = index,
                ingestedBy = "NfcIntent"
            )
        }
        val latestEpoch = encounters.maxOfOrNull { it.timestampEpochMs } ?: System.currentTimeMillis()

        val app = applicationContext as? ArgusApplication ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { app.container.repository.insertBatch(encounters) }
            ScanSettings.setSourceLastRawObservationEpochMs(this@MainActivity, SourceCatalog.KEY_NFC, latestEpoch)
            ScanSettings.setSourceLastScanEpochMs(this@MainActivity, SourceCatalog.KEY_NFC, latestEpoch)
        }
    }

    private fun ingestNfcTag(tag: Tag?) {
        if (!ScanSettings.isNfcSensorEnabled(this)) return

        val payloads = NfcTagIngestStore.ingestFromTag(
            context = this,
            tag = tag,
            persistForScanner = false
        )
        if (payloads.isEmpty()) return

        val location = LocationSnapshotProvider.read(this)
        val encounters = payloads.mapIndexed { index, payload ->
            NfcScanner.buildEncounterFromPayload(
                payload = payload,
                location = location,
                fallbackIndex = index,
                ingestedBy = "NfcReaderMode"
            )
        }
        val latestEpoch = encounters.maxOfOrNull { it.timestampEpochMs } ?: System.currentTimeMillis()

        val app = applicationContext as? ArgusApplication ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { app.container.repository.insertBatch(encounters) }
            ScanSettings.setSourceLastRawObservationEpochMs(this@MainActivity, SourceCatalog.KEY_NFC, latestEpoch)
            ScanSettings.setSourceLastScanEpochMs(this@MainActivity, SourceCatalog.KEY_NFC, latestEpoch)
        }
    }

    private fun enableNfcReaderModeIfAvailable() {
        val adapter = nfcAdapter ?: return
        if (!ScanSettings.isNfcSensorEnabled(this)) return

        runCatching {
            adapter.enableReaderMode(
                this,
                nfcReaderCallback,
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null
            )
        }
    }

    private fun disableNfcReaderModeIfAvailable() {
        val adapter = nfcAdapter ?: return
        runCatching { adapter.disableReaderMode(this) }
    }

    private fun requestRequiredPermissionsIfNeeded() {
        val missing = requiredRuntimePermissions()
            .filter { permission ->
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
            }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun maybeEnterStickyCompassPip(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (isInPictureInPictureMode) return false
        if (!ScanSettings.isStickyCompassMapEnabled(this)) return false
        if (!ScanSettings.isStickyCompassMapPipEligible(this)) return false

        val zoomInIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_PIP_ZOOM_IN
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val zoomOutIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_PIP_ZOOM_OUT
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_PIP_OPEN_APP
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val zoomInPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            zoomInIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val zoomOutPendingIntent = PendingIntent.getActivity(
            this,
            1002,
            zoomOutIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            1003,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actions = listOf(
            RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_menu_add),
                "Zoom In",
                "Zoom in map",
                zoomInPendingIntent
            ),
            RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_menu_revert),
                "Zoom Out",
                "Zoom out map",
                zoomOutPendingIntent
            ),
            RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_menu_view),
                "Open App",
                "Return to full app",
                openAppPendingIntent
            )
        )

        val params = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(Rational(9, 16))
            .setActions(actions)
            .build()
        return runCatching { enterPictureInPictureMode(params) }
            .getOrDefault(false)
    }

    private fun handlePipActionIntent(intent: Intent) {
        if (intent.action != ACTION_PIP_OPEN_APP) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!isInPictureInPictureMode) return

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { startActivity(launchIntent) }
    }

    private fun requiredRuntimePermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        return permissions
    }
}
