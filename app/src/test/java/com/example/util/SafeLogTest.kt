package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SafeLogTest {

    @Test
    fun sanitizeUid_anonymizesFirebaseUserUid() {
        assertEquals("anon", SafeLog.sanitizeUid(null))
        assertEquals("anon", SafeLog.sanitizeUid(""))
        assertEquals("abcd***", SafeLog.sanitizeUid("abcd"))
        assertEquals("a1b2***", SafeLog.sanitizeUid("a1b2c3d4e5f6g7h8"))
    }

    @Test
    fun sanitizeCoordinate_roundsGpsToTwoDecimalPlaces() {
        assertEquals("38.71", SafeLog.sanitizeCoordinate(38.711234))
        assertEquals("-9.14", SafeLog.sanitizeCoordinate(-9.142389))
        assertEquals("0.00", SafeLog.sanitizeCoordinate(0.000000))
    }
}
