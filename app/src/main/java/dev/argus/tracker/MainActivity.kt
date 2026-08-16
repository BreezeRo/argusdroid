package dev.argus.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dev.argus.tracker.domain.SourceCatalog
import dev.argus.tracker.sensing.LocationSnapshotProvider
import dev.argus.tracker.sensing.NfcScanner
import dev.argus.tracker.sensing.NfcTagIngestStore
import dev.argus.tracker.ui.ArgusApp
import dev.argus.tracker.worker.ScanSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var currentIntent by mutableStateOf<Intent?>(null)
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
            ArgusApp(notificationIntent = currentIntent)
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
        setIntent(intent)
        ingestNfcIntent(intent)
        currentIntent = intent
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions += Manifest.permission.ACTIVITY_RECOGNITION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        return permissions
    }
}
