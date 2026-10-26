package com.example.youtubeblocker.protection

/** Main-thread state. A stale save completion must never unlock a newer attempt. */
class SessionGate {
    enum class State { IDLE, INPUT, SAVING, ALLOWED }
    var state = State.IDLE
        private set
    var generation = 0L
        private set

    fun enter(): Boolean {
        if (state != State.IDLE) return false
        generation++
        state = State.INPUT
        return true
    }

    fun beginSave(): Long? {
        if (state != State.INPUT) return null
        state = State.SAVING
        return generation
    }

    fun saved(token: Long): Boolean {
        if (generation != token || state != State.SAVING) return false
        state = State.ALLOWED
        return true
    }

    fun failed(token: Long) {
        if (generation == token && state == State.SAVING) state = State.INPUT
    }

    fun leave() {
        generation++
        state = State.IDLE
    }
}
