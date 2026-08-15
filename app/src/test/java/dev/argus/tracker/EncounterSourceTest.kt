package dev.argus.tracker

import dev.argus.tracker.domain.EncounterSource
import org.junit.Assert.assertEquals
import org.junit.Test

class EncounterSourceTest {
    @Test
    fun enumContainsWifi() {
        assertEquals("WIFI", EncounterSource.WIFI.name)
    }

    @Test
    fun enumContainsAircraft() {
        assertEquals("AIRCRAFT", EncounterSource.AIRCRAFT.name)
    }
}
