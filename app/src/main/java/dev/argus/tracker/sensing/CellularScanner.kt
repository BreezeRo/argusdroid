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
        val serviceState = runCatching { telephonyManager.serviceState }.getOrNull()
        val payload = JSONObject()
            .put("registered", isRegistered)
            .put("timestampMillis", timestampMillis)
            .put("networkOperator", telephonyManager.networkOperator)
            .put("networkOperatorName", telephonyManager.networkOperatorName)
            .put("dataNetworkType", telephonyManager.dataNetworkType)
            .put("voiceNetworkType", telephonyManager.voiceNetworkType)
            .put("dataState", telephonyManager.dataState)
            .put("networkCountryIso", telephonyManager.networkCountryIso)
            .put("simOperator", telephonyManager.simOperator)
            .put("simOperatorName", telephonyManager.simOperatorName)
            .put("simCountryIso", telephonyManager.simCountryIso)
            .put("phoneType", telephonyManager.phoneType)
            .put("isDataRoamingEnabled", runCatching { telephonyManager.isDataRoamingEnabled }.getOrDefault(false))

        serviceState?.let { state: android.telephony.ServiceState ->
            val emergencyOnly = try {
                state.javaClass.getMethod("isEmergencyOnly").invoke(state) as Boolean
            } catch (_: Throwable) {
                false
            }
            val roaming = try {
                state.javaClass.getMethod("getRoaming").invoke(state) as Boolean
            } catch (_: Throwable) {
                false
            }
            payload
                .put("serviceState", state.state)
                .put("isEmergencyOnly", emergencyOnly)
                .put("isRoaming", roaming)

            val nrState = runCatching {
                state.javaClass.getMethod("getNrState").invoke(state) as Int
            }.getOrNull()
            if (nrState != null) {
                payload.put("nrState", nrState)
            }
        }

        val connectionStatus = runCatching {
            this.javaClass.getMethod("getCellConnectionStatus").invoke(this) as Int
        }.getOrNull()
        if (connectionStatus != null) {
            payload.put("cellConnectionStatus", connectionStatus)
        }

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

                runCatching { payload.put("rsrp", cellSignalStrength.rsrp) }
                runCatching { payload.put("rsrq", cellSignalStrength.rsrq) }
                runCatching { payload.put("rssnr", cellSignalStrength.rssnr) }
                runCatching { payload.put("cqi", cellSignalStrength.cqi) }

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

                val ssRsrq = runCatching {
                    cellSignalStrength.javaClass.getMethod("getSsRsrq").invoke(cellSignalStrength) as Int
                }.getOrElse {
                    Int.MAX_VALUE
                }
                val ssSinr = runCatching {
                    cellSignalStrength.javaClass.getMethod("getSsSinr").invoke(cellSignalStrength) as Int
                }.getOrElse {
                    Int.MAX_VALUE
                }
                payload
                    .put("ssRsrq", if (ssRsrq == Int.MAX_VALUE) JSONObject.NULL else ssRsrq)
                    .put("ssSinr", if (ssSinr == Int.MAX_VALUE) JSONObject.NULL else ssSinr)

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

                val ecNo = runCatching {
                    cellSignalStrength.javaClass.getMethod("getEcNo").invoke(cellSignalStrength) as Int
                }.getOrNull()
                if (ecNo != null) {
                    payload.put("ecNo", ecNo)
                }

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

                val bitErrorRate = runCatching {
                    cellSignalStrength.javaClass.getMethod("getBitErrorRate").invoke(cellSignalStrength) as Int
                }.getOrNull()
                if (bitErrorRate != null) {
                    payload.put("bitErrorRate", bitErrorRate)
                }

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

                val evdoSnr = runCatching {
                    cellSignalStrength.javaClass.getMethod("getEvdoSnr").invoke(cellSignalStrength) as Int
                }.getOrNull()
                if (evdoSnr != null) {
                    payload.put("evdoSnr", evdoSnr)
                }

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
