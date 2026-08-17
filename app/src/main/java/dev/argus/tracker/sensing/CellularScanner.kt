package dev.argus.tracker.sensing

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellIdentityNr
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import dev.argus.tracker.domain.SignalScanner
import dev.argus.tracker.worker.ScanSettings
import java.math.BigInteger
import org.json.JSONObject

class CellularScanner(
    private val context: Context
) : SignalScanner {
    @SuppressLint("MissingPermission")
    override suspend fun scanOnce(): List<Encounter> {
        if (!ScanSettings.isCellularSensorEnabled(context)) return emptyList()
        if (!hasCellPermissions()) {
            Log.w(TAG, "Skipping cellular scan: required permissions not granted")
            return emptyList()
        }
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: run {
                Log.w(TAG, "Skipping cellular scan: telephony service unavailable")
                return emptyList()
            }

        val allCells = runCatching { telephonyManager.allCellInfo }
            .onFailure { error -> Log.w(TAG, "Failed to read allCellInfo", error) }
            .getOrNull()
            ?: return emptyList()
        if (allCells.isEmpty()) {
            Log.i(TAG, "Cellular scan returned no cells")
        }
        val now = System.currentTimeMillis()
        val location = LocationSnapshotProvider.read(context)

        return allCells.mapNotNull { info -> info.toEncounter(now, telephonyManager, location) }
    }

    private fun hasCellPermissions(): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasReadPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        return hasFineLocation && hasReadPhoneState
    }

    @SuppressLint("MissingPermission")
    private fun CellInfo.toEncounter(
        now: Long,
        telephonyManager: TelephonyManager,
        location: DetectionLocation?
    ): Encounter? {
        val payload = JSONObject()
            .put("registered", isRegistered)
            .put("timestampMillis", timestampMillis)
            .put("networkOperator", telephonyManager.networkOperator)
            .put("networkOperatorName", telephonyManager.networkOperatorName)
            .put("dataNetworkType", telephonyManager.dataNetworkType)

        return when (this) {
            is CellInfoLte -> {
                val id = cellIdentity
                payload
                    .put("radio", "LTE")
                    .put("ci", id.ci)
                    .put("pci", id.pci)
                    .put("tac", id.tac)
                    .put("earfcn", id.earfcn)
                    .put("bandwidth", id.bandwidth)
                    .put("mcc", id.mccString)
                    .put("mnc", id.mncString)
                    .put("ta", cellSignalStrength.timingAdvance)
                    .put("asu", cellSignalStrength.asuLevel)
                    .put("level", cellSignalStrength.level)

                Encounter(
                    timestampEpochMs = now,
                    source = EncounterSource.CELL,
                    primaryId = "lte:${id.ci}:${id.pci}:${id.tac}",
                    secondaryId = buildCellSecondaryId(telephonyManager, "LTE"),
                    rssiDbm = cellSignalStrength.dbm,
                    frequencyMhz = null,
                    lat = location?.lat,
                    lon = location?.lon,
                    rawPayloadJson = payload.toString()
                )
            }

            is CellInfoNr -> {
                val id = cellIdentity as? CellIdentityNr ?: return null
                val nciIdentity = extractNrNciIdentity(id)
                val pci = runCatching { id.pci }.getOrNull()
                    ?: runCatching { id.javaClass.getMethod("getPci").invoke(id) as Int }.getOrNull()
                    ?: Int.MAX_VALUE
                val tac = runCatching { id.tac }.getOrNull()
                    ?: runCatching { id.javaClass.getMethod("getTac").invoke(id) as Int }.getOrNull()
                    ?: Int.MAX_VALUE
                val nrarfcn = runCatching { id.nrarfcn }.getOrNull()
                    ?: runCatching { id.javaClass.getMethod("getNrarfcn").invoke(id) as Int }.getOrNull()
                    ?: Int.MAX_VALUE
                val mcc = runCatching {
                    id.mccString
                }.getOrNull()
                val mnc = runCatching {
                    id.mncString
                }.getOrNull()
                val asu = runCatching {
                    cellSignalStrength.javaClass.getMethod("getAsuLevel").invoke(cellSignalStrength) as Int
                }.getOrElse {
                    Int.MAX_VALUE
                }
                val level = runCatching {
                    cellSignalStrength.javaClass.getMethod("getLevel").invoke(cellSignalStrength) as Int
                }.getOrElse {
                    Int.MAX_VALUE
                }
                val csiRsrp = runCatching {
                    cellSignalStrength.javaClass.getMethod("getCsiRsrp").invoke(cellSignalStrength) as Int
                }.getOrElse {
                    Int.MAX_VALUE
                }
                val ssRsrp = runCatching {
                    cellSignalStrength.javaClass.getMethod("getSsRsrp").invoke(cellSignalStrength) as Int
                }.getOrElse {
                    Int.MAX_VALUE
                }
                val dbm = runCatching {
                    cellSignalStrength.javaClass.getMethod("getDbm").invoke(cellSignalStrength) as Int
                }.getOrElse {
                    Int.MIN_VALUE
                }
                payload
                    .put("radio", "NR")
                    .put("nci", nciIdentity.nci ?: JSONObject.NULL)
                    .put("nciRaw", nciIdentity.rawValue ?: JSONObject.NULL)
                    .put("pci", if (pci == Int.MAX_VALUE) JSONObject.NULL else pci)
                    .put("tac", if (tac == Int.MAX_VALUE) JSONObject.NULL else tac)
                    .put("nrarfcn", if (nrarfcn == Int.MAX_VALUE) JSONObject.NULL else nrarfcn)
                    .put("mcc", mcc ?: JSONObject.NULL)
                    .put("mnc", mnc ?: JSONObject.NULL)
                    .put("asu", if (asu == Int.MAX_VALUE) JSONObject.NULL else asu)
                    .put("level", if (level == Int.MAX_VALUE) JSONObject.NULL else level)
                    .put("csiRsrp", if (csiRsrp == Int.MAX_VALUE) JSONObject.NULL else csiRsrp)
                    .put("ssRsrp", if (ssRsrp == Int.MAX_VALUE) JSONObject.NULL else ssRsrp)

                Encounter(
                    timestampEpochMs = now,
                    source = EncounterSource.CELL,
                    primaryId = "nr:${nciIdentity.displayValue ?: "unknown"}:${if (pci == Int.MAX_VALUE) "unknown" else pci}:${if (tac == Int.MAX_VALUE) "unknown" else tac}",
                    secondaryId = buildCellSecondaryId(telephonyManager, "NR"),
                    rssiDbm = if (dbm == Int.MIN_VALUE) null else dbm,
                    frequencyMhz = null,
                    lat = location?.lat,
                    lon = location?.lon,
                    rawPayloadJson = payload.toString()
                )
            }

            is CellInfoWcdma -> {
                val id = cellIdentity
                payload
                    .put("radio", "WCDMA")
                    .put("cid", id.cid)
                    .put("lac", id.lac)
                    .put("psc", id.psc)
                    .put("uarfcn", id.uarfcn)
                    .put("mcc", id.mccString)
                    .put("mnc", id.mncString)
                    .put("asu", cellSignalStrength.asuLevel)
                    .put("level", cellSignalStrength.level)

                Encounter(
                    timestampEpochMs = now,
                    source = EncounterSource.CELL,
                    primaryId = "wcdma:${id.cid}:${id.psc}:${id.lac}",
                    secondaryId = buildCellSecondaryId(telephonyManager, "WCDMA"),
                    rssiDbm = cellSignalStrength.dbm,
                    frequencyMhz = null,
                    lat = location?.lat,
                    lon = location?.lon,
                    rawPayloadJson = payload.toString()
                )
            }

            is CellInfoGsm -> {
                val id = cellIdentity
                payload
                    .put("radio", "GSM")
                    .put("cid", id.cid)
                    .put("lac", id.lac)
                    .put("arfcn", id.arfcn)
                    .put("bsic", id.bsic)
                    .put("mcc", id.mccString)
                    .put("mnc", id.mncString)
                    .put("asu", cellSignalStrength.asuLevel)
                    .put("level", cellSignalStrength.level)

                Encounter(
                    timestampEpochMs = now,
                    source = EncounterSource.CELL,
                    primaryId = "gsm:${id.cid}:${id.lac}:${id.arfcn}",
                    secondaryId = buildCellSecondaryId(telephonyManager, "GSM"),
                    rssiDbm = cellSignalStrength.dbm,
                    frequencyMhz = null,
                    lat = location?.lat,
                    lon = location?.lon,
                    rawPayloadJson = payload.toString()
                )
            }

            is CellInfoCdma -> {
                val id = cellIdentity
                payload
                    .put("radio", "CDMA")
                    .put("basestationId", id.basestationId)
                    .put("networkId", id.networkId)
                    .put("systemId", id.systemId)
                    .put("asu", cellSignalStrength.asuLevel)
                    .put("level", cellSignalStrength.level)

                Encounter(
                    timestampEpochMs = now,
                    source = EncounterSource.CELL,
                    primaryId = "cdma:${id.basestationId}:${id.networkId}:${id.systemId}",
                    secondaryId = buildCellSecondaryId(telephonyManager, "CDMA"),
                    rssiDbm = cellSignalStrength.dbm,
                    frequencyMhz = null,
                    lat = location?.lat,
                    lon = location?.lon,
                    rawPayloadJson = payload.toString()
                )
            }

            else -> null
        }
    }

    private fun buildCellSecondaryId(telephonyManager: TelephonyManager, radio: String): String {
        val operator = telephonyManager.networkOperatorName
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        val deviceLabel = "${radio.uppercase()} tower"
        return if (operator != null) {
            "$operator Cell ($deviceLabel)"
        } else {
            "Cell ($deviceLabel)"
        }
    }

    private data class NrNciIdentity(
        val nci: Long?,
        val rawValue: String?,
        val displayValue: String?
    )

    private fun extractNrNciIdentity(id: CellIdentityNr): NrNciIdentity {
        val raw = runCatching<Any?> { id.nci }.getOrNull()
            ?: runCatching<Any?> { id.javaClass.getMethod("getNci").invoke(id) }.getOrNull()
        if (raw == null) {
            return NrNciIdentity(nci = null, rawValue = null, displayValue = null)
        }

        val rawString = raw.toString().trim().takeIf { it.isNotEmpty() }
        val parsedBigInt = rawString?.let { runCatching { BigInteger(it) }.getOrNull() }

        if (parsedBigInt != null) {
            if (parsedBigInt.signum() < 0) {
                return NrNciIdentity(nci = null, rawValue = null, displayValue = null)
            }
            return if (parsedBigInt <= BigInteger.valueOf(Long.MAX_VALUE)) {
                val safeNci = parsedBigInt.toLong()
                if (safeNci == Long.MAX_VALUE) {
                    NrNciIdentity(nci = null, rawValue = null, displayValue = null)
                } else {
                    NrNciIdentity(
                        nci = safeNci,
                        rawValue = parsedBigInt.toString(),
                        displayValue = parsedBigInt.toString()
                    )
                }
            } else {
                NrNciIdentity(
                    nci = null,
                    rawValue = parsedBigInt.toString(),
                    displayValue = parsedBigInt.toString()
                )
            }
        }

        val numericLong = (raw as? Number)?.toLong()
        if (numericLong != null && numericLong >= 0L && numericLong != Long.MAX_VALUE) {
            return NrNciIdentity(
                nci = numericLong,
                rawValue = numericLong.toString(),
                displayValue = numericLong.toString()
            )
        }

        return NrNciIdentity(nci = null, rawValue = rawString, displayValue = rawString)
    }

    private companion object {
        private const val TAG = "CellularScanner"
    }
}
