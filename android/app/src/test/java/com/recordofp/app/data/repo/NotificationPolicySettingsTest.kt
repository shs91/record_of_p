package com.recordofp.app.data.repo

import com.recordofp.app.domain.engine.EngineParams
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicySettingsTest {

    @Test
    fun `기본 설정은 EngineParams의 §4·5 기본값을 그대로 쓴다`() {
        val d = NotificationPolicySettings()
        assertEquals(EngineParams.COOLDOWN_PER_ITEM_MS, Duration.ofHours(d.cooldownHours.toLong()).toMillis())
        assertEquals(EngineParams.DAILY_CAP_TOTAL, d.dailyCapTotal)
        assertEquals(EngineParams.QUIET_START_MINUTE, d.quietStartMinute)
        assertEquals(EngineParams.QUIET_END_MINUTE, d.quietEndMinute)
        assertTrue(d.quietEnabled)
    }
}
