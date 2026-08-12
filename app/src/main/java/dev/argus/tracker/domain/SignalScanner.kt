package dev.argus.tracker.domain

interface SignalScanner {
    suspend fun scanOnce(): List<Encounter>
}
