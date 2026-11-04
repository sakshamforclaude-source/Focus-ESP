package com.example.focusesp

class FakeAlarmScheduler : AlarmScheduler {

    val exactAt = mutableListOf<Long>()
    val inexactAt = mutableListOf<Long>()
    var cancelled = 0

    override fun scheduleExactAt(epochMillis: Long) {
        exactAt += epochMillis
    }

    override fun scheduleInexactAt(epochMillis: Long) {
        inexactAt += epochMillis
    }

    override fun cancel() {
        cancelled++
    }
}
