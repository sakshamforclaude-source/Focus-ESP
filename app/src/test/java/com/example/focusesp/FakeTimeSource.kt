package com.example.focusesp

/**
 * Deterministic [TimeSource] for JVM tests. Advance both clocks together with
 * [advance] unless a test deliberately needs them to diverge (e.g. reboot).
 */
class FakeTimeSource(
    var wallNow: Long = 1_000_000L,
    var elapsedNow: Long = 5_000L
) : TimeSource {

    override fun elapsedRealtimeMs(): Long = elapsedNow

    override fun wallClockMs(): Long = wallNow

    fun advance(millis: Long) {
        wallNow += millis
        elapsedNow += millis
    }
}
