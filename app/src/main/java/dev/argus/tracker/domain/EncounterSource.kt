package dev.argus.tracker.domain

enum class EncounterSource {
    WIFI,
    WIFI_DIRECT,
    BLUETOOTH_LE,
    BLUETOOTH_CLASSIC,
    REMOTE_ID,
    CELL,
    UWB,
    SDR,
    UNKNOWN_RF
}
