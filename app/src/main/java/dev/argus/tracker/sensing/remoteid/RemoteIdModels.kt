package dev.argus.tracker.sensing.remoteid

enum class RemoteIdParseConfidence {
    NONE,
    LOW,
    MEDIUM,
    HIGH
}

enum class RemoteIdTransport {
    BLE,
    WIFI,
    EXTERNAL,
    UNKNOWN
}

data class RemoteIdDecoded(
    val messageType: String,
    val uasId: String?,
    val operatorId: String?,
    val operatorLat: Double?,
    val operatorLon: Double?,
    val droneLat: Double?,
    val droneLon: Double?,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Double?,
    val headingDegrees: Double?,
    val emergencyStatus: String?,
    val messageTimestampEpochMs: Long?,
    val parseConfidence: RemoteIdParseConfidence,
    val parserVersion: String,
    val parseNotes: List<String>
)

data class RemoteIdNormalizedPayload(
    val primaryId: String,
    val secondaryId: String?,
    val timestampEpochMs: Long,
    val normalizedPayloadJson: String,
    val decoded: RemoteIdDecoded?
)
