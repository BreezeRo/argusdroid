package dev.argus.tracker.domain

enum class EncounterSource {
    ARGUS_MESH,
    WIFI,
    WIFI_SWEEP,
    WIFI_DIRECT,
    BLUETOOTH_LE,
    BLUETOOTH_LE_SWEEP,
    BLUETOOTH_CLASSIC,
    NFC,
    REMOTE_ID,
    CELL,
    CAMERA,
    AIRCRAFT,
    UWB,
    SDR,
    UNKNOWN_RF
}
