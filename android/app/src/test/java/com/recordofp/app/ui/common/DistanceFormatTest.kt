package com.recordofp.app.ui.common

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceFormatTest {

    @Test
    fun `1km 미만은 m, 이상은 km 한 자리로 나눈다`() {
        assertEquals("999" to "m", distanceParts(999))
        assertEquals("1.0" to "km", distanceParts(1000))
        assertEquals("1.1" to "km", distanceParts(1100))
        assertEquals("180m", formatDistance(180))
        assertEquals("2.4km", formatDistance(2400))
    }

    @Test
    fun `1km를 넘어야 멀다`() {
        assertFalse(isFarDistance(1000))
        assertTrue(isFarDistance(1001))
    }

    @Test
    fun `기기 언어가 쉼표 소수점이어도 점으로 쓴다`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.1" to "km", distanceParts(1100))
        } finally {
            Locale.setDefault(saved)
        }
    }
}
