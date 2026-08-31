package com.recordofp.app.domain.engine

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MuteTodayTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun local(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant()

    @Test
    fun `새벽 5시 이전이면 오늘 5시에 해제된다`() {
        assertEquals(local(2026, 8, 31, 5, 0), MuteToday.releaseInstant(local(2026, 8, 31, 2, 30), zone))
    }

    @Test
    fun `5시 이후면 다음날 5시에 해제된다`() {
        assertEquals(local(2026, 9, 1, 5, 0), MuteToday.releaseInstant(local(2026, 8, 31, 14, 0), zone))
        assertEquals(local(2026, 9, 1, 5, 0), MuteToday.releaseInstant(local(2026, 8, 31, 5, 0), zone))
    }
}
