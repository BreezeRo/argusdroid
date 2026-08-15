package dev.argus.tracker.ui

import android.location.Location
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import org.json.JSONObject

data class FlockMember(
    val source: String,
    val primaryId: String,
    val secondaryId: String?
)

data class DeviceFlock(
    val id: Int,
    val members: List<FlockMember>,
    val coTravelEventCount: Int,
    val pairLinkCount: Int,
    val travelSpanMeters: Double,
    val firstSeenEpochMs: Long,
    val lastSeenEpochMs: Long
)

private data class FlockObservation(
    val deviceKey: String,
    val sourceType: EncounterSource,
    val source: String,
    val primaryId: String,
    val secondaryId: String?,
    val timestampEpochMs: Long,
    val lat: Double,
    val lon: Double
)

private data class CoTravelEvent(
    val timestampEpochMs: Long,
    val lat: Double,
    val lon: Double,
    val deviceKeys: Set<String>
)

private data class DevicePair(
    val first: String,
    val second: String
)

private data class PairEvidence(
    val events: MutableList<CoTravelEvent> = mutableListOf()
)

private const val DEFAULT_TIME_WINDOW_MS = 90_000L
private const val DEFAULT_PROXIMITY_RADIUS_METERS = 45.0
private const val DEFAULT_MIN_TRAVEL_SPAN_METERS = 10.0
private const val DEFAULT_MIN_CO_TRAVEL_EVENTS = 2
private const val MIN_MEMBER_MOVEMENT_RATIO = 0.6

fun detectDeviceFlocks(
    encounters: List<Encounter>,
    timeWindowMs: Long = DEFAULT_TIME_WINDOW_MS,
    proximityRadiusMeters: Double = DEFAULT_PROXIMITY_RADIUS_METERS,
    minTravelSpanMeters: Double = DEFAULT_MIN_TRAVEL_SPAN_METERS,
    minCoTravelEvents: Int = DEFAULT_MIN_CO_TRAVEL_EVENTS
): List<DeviceFlock> {
    if (encounters.isEmpty()) return emptyList()

    val observations = encounters
        .asSequence()
        .mapNotNull(::encounterToTrustedObservation)
        .sortedBy { it.timestampEpochMs }
        .toList()

    if (observations.size < 2) return emptyList()

    val movementByDevice = observations
        .groupBy { it.deviceKey }
        .mapValues { (_, deviceObservations) -> maxObservationSpanMeters(deviceObservations) }

    val minMemberMovementMeters = (minTravelSpanMeters * MIN_MEMBER_MOVEMENT_RATIO).coerceAtLeast(6.0)

    val coTravelEvents = buildCoTravelEvents(observations, timeWindowMs, proximityRadiusMeters)
    if (coTravelEvents.isEmpty()) return emptyList()

    val pairEvidence = collectPairEvidence(coTravelEvents)
    if (pairEvidence.isEmpty()) return emptyList()

    val memberIndex = observations
        .groupBy { it.deviceKey }
        .mapValues { (_, items) ->
            val latest = items.maxByOrNull { it.timestampEpochMs } ?: items.first()
            FlockMember(
                source = latest.source,
                primaryId = latest.primaryId,
                secondaryId = latest.secondaryId
            )
        }

    val adjacency = linkedMapOf<String, MutableSet<String>>()
    val acceptedPairEvidence = linkedMapOf<DevicePair, PairEvidence>()

    pairEvidence.forEach { (pair, evidence) ->
        val distinctEvents = distinctEventsByTimeAndCell(evidence.events)
        if (distinctEvents.size < minCoTravelEvents) return@forEach

        val firstMovement = movementByDevice[pair.first] ?: 0.0
        val secondMovement = movementByDevice[pair.second] ?: 0.0
        if (firstMovement < minMemberMovementMeters || secondMovement < minMemberMovementMeters) {
            return@forEach
        }

        val spanMeters = maxPairwiseDistanceMeters(distinctEvents)
        if (spanMeters < minTravelSpanMeters) return@forEach

        acceptedPairEvidence[pair] = PairEvidence(events = distinctEvents.toMutableList())
        adjacency.getOrPut(pair.first) { linkedSetOf() }.add(pair.second)
        adjacency.getOrPut(pair.second) { linkedSetOf() }.add(pair.first)
    }

    if (adjacency.isEmpty()) return emptyList()

    val flocks = connectedComponents(adjacency)
        .mapIndexedNotNull { index, component ->
            if (component.size < 2) return@mapIndexedNotNull null
            val members = component
                .mapNotNull { key -> memberIndex[key] }
                .sortedWith(compareBy<FlockMember> { it.source }.thenBy { it.primaryId })
            if (members.size < 2) return@mapIndexedNotNull null

            val relatedPairs = acceptedPairEvidence
                .filterKeys { pair -> pair.first in component && pair.second in component }
                .values

            val allEvents = relatedPairs
                .flatMap { it.events }
                .distinctBy { event -> "${event.timestampEpochMs}:${event.lat}:${event.lon}" }

            if (allEvents.isEmpty()) return@mapIndexedNotNull null

            DeviceFlock(
                id = index + 1,
                members = members,
                coTravelEventCount = allEvents.size,
                pairLinkCount = relatedPairs.size,
                travelSpanMeters = maxPairwiseDistanceMeters(allEvents),
                firstSeenEpochMs = allEvents.minOf { it.timestampEpochMs },
                lastSeenEpochMs = allEvents.maxOf { it.timestampEpochMs }
            )
        }

    return flocks.sortedWith(
        compareByDescending<DeviceFlock> { it.members.size }
            .thenByDescending { it.coTravelEventCount }
            .thenByDescending { it.travelSpanMeters }
    )
}

private fun encounterToTrustedObservation(encounter: Encounter): FlockObservation? {
    val trusted = when (encounter.source) {
        EncounterSource.AIRCRAFT -> {
            if (!isValidLatLon(encounter.lat, encounter.lon)) return null
            encounter.lat!! to encounter.lon!!
        }

        EncounterSource.REMOTE_ID -> {
            val resolved = extractRemoteIdDeviceLatLon(encounter.rawPayloadJson)
            if (!isValidLatLon(resolved?.first, resolved?.second)) return null
            resolved!!
        }

        // Other sources are usually observer-tethered or fixed for this app's current schema.
        else -> return null
    }

    return FlockObservation(
        deviceKey = "${encounter.source.name}|${encounter.primaryId}",
        sourceType = encounter.source,
        source = encounter.source.name,
        primaryId = encounter.primaryId,
        secondaryId = encounter.secondaryId,
        timestampEpochMs = encounter.timestampEpochMs,
        lat = trusted.first,
        lon = trusted.second
    )
}

private fun extractRemoteIdDeviceLatLon(rawPayloadJson: String): Pair<Double, Double>? {
    val payload = runCatching { JSONObject(rawPayloadJson) }.getOrNull() ?: return null
    val decoded = payload.optJSONObject("remoteIdDecoded") ?: return null
    val lat = decoded.optDoubleOrNull("droneLat")
    val lon = decoded.optDoubleOrNull("droneLon")
    return if (isValidLatLon(lat, lon)) lat!! to lon!! else null
}

private fun maxObservationSpanMeters(observations: List<FlockObservation>): Double {
    if (observations.size < 2) return 0.0
    var maxMeters = 0.0
    for (i in observations.indices) {
        val first = observations[i]
        for (j in i + 1 until observations.size) {
            val second = observations[j]
            val distance = distanceMeters(first.lat, first.lon, second.lat, second.lon)
            if (distance > maxMeters) {
                maxMeters = distance
            }
        }
    }
    return maxMeters
}

private fun buildCoTravelEvents(
    observations: List<FlockObservation>,
    timeWindowMs: Long,
    proximityRadiusMeters: Double
): List<CoTravelEvent> {
    if (observations.isEmpty()) return emptyList()

    val events = mutableListOf<CoTravelEvent>()
    var start = 0

    while (start < observations.size) {
        val anchor = observations[start]
        val windowEnd = anchor.timestampEpochMs + timeWindowMs
        val grouped = mutableListOf<FlockObservation>()
        var cursor = start

        while (cursor < observations.size) {
            val candidate = observations[cursor]
            if (candidate.timestampEpochMs > windowEnd) break
            val distanceToAnchorMeters = distanceMeters(
                anchor.lat,
                anchor.lon,
                candidate.lat,
                candidate.lon
            )
            if (distanceToAnchorMeters <= proximityRadiusMeters) {
                grouped += candidate
            }
            cursor += 1
        }

        val uniqueDevices = grouped
            .map { it.deviceKey }
            .toSet()

        if (uniqueDevices.size >= 2) {
            val lat = grouped.map { it.lat }.average()
            val lon = grouped.map { it.lon }.average()
            val ts = grouped.maxOf { it.timestampEpochMs }
            events += CoTravelEvent(
                timestampEpochMs = ts,
                lat = lat,
                lon = lon,
                deviceKeys = uniqueDevices
            )
        }

        start += 1
    }

    return events
}

private fun collectPairEvidence(events: List<CoTravelEvent>): Map<DevicePair, PairEvidence> {
    val evidence = linkedMapOf<DevicePair, PairEvidence>()

    events.forEach { event ->
        val keys = event.deviceKeys.toList()
        for (i in keys.indices) {
            for (j in i + 1 until keys.size) {
                val first = minOf(keys[i], keys[j])
                val second = maxOf(keys[i], keys[j])
                val pair = DevicePair(first = first, second = second)
                evidence.getOrPut(pair) { PairEvidence() }.events += event
            }
        }
    }

    return evidence
}

private fun distinctEventsByTimeAndCell(events: List<CoTravelEvent>): List<CoTravelEvent> {
    return events
        .sortedBy { it.timestampEpochMs }
        .distinctBy { event ->
            // Coarse cell buckets de-duplicate multiple near-identical detections from one spot.
            val latCell = (event.lat * 10_000.0).toInt()
            val lonCell = (event.lon * 10_000.0).toInt()
            val timeCell = event.timestampEpochMs / 60_000L
            "$timeCell:$latCell:$lonCell"
        }
}

private fun connectedComponents(adjacency: Map<String, Set<String>>): List<Set<String>> {
    val visited = mutableSetOf<String>()
    val components = mutableListOf<Set<String>>()

    adjacency.keys.forEach { node ->
        if (!visited.add(node)) return@forEach

        val stack = ArrayDeque<String>()
        val component = linkedSetOf<String>()
        stack.addLast(node)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (!component.add(current)) continue

            adjacency[current].orEmpty().forEach { neighbor ->
                if (visited.add(neighbor)) {
                    stack.addLast(neighbor)
                }
            }
        }

        if (component.size >= 2) {
            components += component
        }
    }

    return components
}

private fun maxPairwiseDistanceMeters(events: List<CoTravelEvent>): Double {
    if (events.size < 2) return 0.0
    var maxMeters = 0.0
    for (i in events.indices) {
        val first = events[i]
        for (j in i + 1 until events.size) {
            val second = events[j]
            val distance = distanceMeters(first.lat, first.lon, second.lat, second.lon)
            if (distance > maxMeters) {
                maxMeters = distance
            }
        }
    }
    return maxMeters
}

private fun isValidLatLon(lat: Double?, lon: Double?): Boolean {
    return lat != null && lon != null && !lat.isNaN() && !lon.isNaN() && lat in -90.0..90.0 && lon in -180.0..180.0
}

private fun JSONObject.optDoubleOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }
}

private fun distanceMeters(
    fromLat: Double,
    fromLon: Double,
    toLat: Double,
    toLon: Double
): Double {
    val result = FloatArray(1)
    Location.distanceBetween(fromLat, fromLon, toLat, toLon, result)
    return result[0].toDouble()
}
