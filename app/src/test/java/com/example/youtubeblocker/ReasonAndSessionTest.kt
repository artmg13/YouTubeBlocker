package com.example.youtubeblocker

import com.example.youtubeblocker.protection.SessionGate
import org.junit.Assert.*
import org.junit.Test

class ReasonAndSessionTest {
    @Test fun unicodeAndLengthBoundaries() {
        assertEquals(7, ReasonRules.length("Моя цель"))
        assertEquals(2, ReasonRules.length(" я\n\t\u00A0😀 "))
        assertFalse(ReasonRules.valid(" ".repeat(200), 50))
        assertFalse(ReasonRules.valid("я".repeat(49), 50))
        assertTrue(ReasonRules.valid("😀".repeat(50), 50))
        assertFalse(ReasonRules.valid("я".repeat(10001), 50))
        assertEquals("Моя\n цель", ReasonRules.clean(" \nМоя\n цель\u00A0"))
    }

    @Test fun oneConfirmationPerSessionAndFreshEntryAfterLeaving() {
        val gate = SessionGate()
        assertTrue(gate.enter())
        assertFalse(gate.enter())
        val token = gate.beginSave()!!
        assertNull(gate.beginSave())
        assertTrue(gate.saved(token))
        assertFalse(gate.saved(token))
        assertFalse(gate.enter())
        gate.leave()
        assertTrue(gate.enter())
    }

    @Test fun staleSaveCannotUnlockNewAttemptAndFailedSaveCanRetry() {
        val gate = SessionGate()
        gate.enter()
        val old = gate.beginSave()!!
        gate.leave()
        gate.enter()
        assertFalse(gate.saved(old))
        val current = gate.beginSave()!!
        gate.failed(current)
        assertEquals(current, gate.beginSave())
        assertTrue(gate.saved(current))
    }
}
