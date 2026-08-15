package dev.argus.tracker.domain

/**
 * Canonical source key registry shared by sensing, scheduling, and UI mapping layers.
 */
object SourceCatalog {
    data class ScanSourceDefinition(
        val scanTypeKey: String,
        val encounterSource: EncounterSource?
    )

    const val KEY_WIFI = "wifi"
    const val KEY_WIFI_DIRECT = "wifi_direct"
    const val KEY_BLE = "ble"
    const val KEY_BT_CLASSIC = "bt_classic"
    const val KEY_CELLULAR = "cellular"
    const val KEY_REMOTE_ID = "remote_id"
    const val KEY_CAMERA = "camera"
    const val KEY_AIRCRAFT = "aircraft"
    const val KEY_UWB = "uwb"
    const val KEY_SDR = "sdr"
    const val KEY_ACOUSTIC = "acoustic"
    const val KEY_MAGNETIC = "magnetic"

    val SOURCE_WIFI: String = EncounterSource.WIFI.name
    val SOURCE_WIFI_SWEEP: String = EncounterSource.WIFI_SWEEP.name
    val SOURCE_WIFI_DIRECT: String = EncounterSource.WIFI_DIRECT.name
    val SOURCE_BLUETOOTH_LE: String = EncounterSource.BLUETOOTH_LE.name
    val SOURCE_BLUETOOTH_CLASSIC: String = EncounterSource.BLUETOOTH_CLASSIC.name
    val SOURCE_REMOTE_ID: String = EncounterSource.REMOTE_ID.name
    val SOURCE_CELL: String = EncounterSource.CELL.name
    val SOURCE_CAMERA: String = EncounterSource.CAMERA.name
    val SOURCE_AIRCRAFT: String = EncounterSource.AIRCRAFT.name
    val SOURCE_UWB: String = EncounterSource.UWB.name
    val SOURCE_SDR: String = EncounterSource.SDR.name
    val SOURCE_UNKNOWN_RF: String = EncounterSource.UNKNOWN_RF.name

    val SCAN_SOURCES: List<ScanSourceDefinition> = listOf(
        ScanSourceDefinition(KEY_WIFI, EncounterSource.WIFI),
        ScanSourceDefinition(KEY_WIFI_DIRECT, EncounterSource.WIFI_DIRECT),
        ScanSourceDefinition(KEY_BLE, EncounterSource.BLUETOOTH_LE),
        ScanSourceDefinition(KEY_BT_CLASSIC, EncounterSource.BLUETOOTH_CLASSIC),
        ScanSourceDefinition(KEY_CELLULAR, EncounterSource.CELL),
        ScanSourceDefinition(KEY_REMOTE_ID, EncounterSource.REMOTE_ID),
        ScanSourceDefinition(KEY_CAMERA, EncounterSource.CAMERA),
        ScanSourceDefinition(KEY_AIRCRAFT, EncounterSource.AIRCRAFT),
        ScanSourceDefinition(KEY_UWB, EncounterSource.UWB),
        ScanSourceDefinition(KEY_SDR, EncounterSource.SDR),
        ScanSourceDefinition(KEY_ACOUSTIC, null),
        ScanSourceDefinition(KEY_MAGNETIC, null)
    )

    val SCAN_SOURCE_KEYS: List<String> = SCAN_SOURCES.map { it.scanTypeKey }
}
