package com.recordofp.app.domain.engine

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** "오늘 그만" 해제 시각: 다음 05:00 (스펙 §4.5) */
object MuteToday {
    fun releaseInstant(now: Instant, zone: ZoneId): Instant {
        val local = now.atZone(zone)
        val todayFive = local.toLocalDate().atTime(LocalTime.of(EngineParams.MUTE_TODAY_RELEASE_HOUR, 0)).atZone(zone)
        val release = if (local.toInstant() < todayFive.toInstant()) todayFive else todayFive.plusDays(1)
        return release.toInstant()
    }
}
