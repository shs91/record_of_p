package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReseedGovernorTest {

    private val governor = ReseedGovernor()
    private val origin = GeoPoint(37.5000, 127.0000)
    private val moved600m = GeoPoint(37.5054, 127.0000) // ~600m 북쪽
    private val now = 1_000_000_000_000L
    private fun stamp(ageMs: Long, at: GeoPoint = origin) = ReseedStamp(now - ageMs, at)
    private val min = 60_000L

    @Test
    fun `첫 실행(스탬프 없음)은 원인과 무관하게 허용된다`() {
        ReseedCause.entries.forEach { cause ->
            assertTrue("$cause", governor.shouldReseed(cause, now, last = null, current = origin))
        }
    }

    @Test
    fun `BOOT·FENCE_LOST·ITEM_CHANGE는 디바운스를 무시한다`() {
        val fresh = stamp(ageMs = 1 * min) // 1분 전 — 10분 미만
        listOf(ReseedCause.BOOT, ReseedCause.FENCE_LOST, ReseedCause.ITEM_CHANGE).forEach { c ->
            assertTrue("$c", governor.shouldReseed(c, now, fresh, origin))
        }
    }

    @Test
    fun `SENTINEL_EXIT·RETRY는 10분 디바운스를 따른다`() {
        listOf(ReseedCause.SENTINEL_EXIT, ReseedCause.RETRY).forEach { c ->
            assertFalse("$c 9분", governor.shouldReseed(c, now, stamp(9 * min), origin))
            assertTrue("$c 10분", governor.shouldReseed(c, now, stamp(10 * min), origin))
        }
    }

    @Test
    fun `PERIODIC은 디바운스를 받지 않는다 - 다른 신호가 다 죽었을 때의 최후 방어선`() {
        assertTrue(governor.shouldReseed(ReseedCause.PERIODIC, now, stamp(1 * min), origin))
    }

    @Test
    fun `시계가 거꾸로 가 스탬프가 미래에 있으면 간격이 지난 것으로 본다`() {
        val future = stamp(ageMs = -5 * min) // 스탬프가 5분 뒤에 있다
        assertTrue(governor.shouldReseed(ReseedCause.SENTINEL_EXIT, now, future, origin))
        assertTrue(governor.shouldReseed(ReseedCause.APP_OPEN, now, future, origin))
    }

    @Test
    fun `APP_OPEN은 500m 이상 이동 또는 6시간 경과 시에만, 그리고 10분 디바운스 안에서 허용된다`() {
        // 12분 전, 이동 없음 → 조건 불충족
        assertFalse(governor.shouldReseed(ReseedCause.APP_OPEN, now, stamp(12 * min), origin))
        // 12분 전, 600m 이동 → 허용
        assertTrue(governor.shouldReseed(ReseedCause.APP_OPEN, now, stamp(12 * min), moved600m))
        // 5분 전, 600m 이동 → 디바운스에 걸림
        assertFalse(governor.shouldReseed(ReseedCause.APP_OPEN, now, stamp(5 * min), moved600m))
        // 7시간 전, 이동 없음 → 허용
        assertTrue(governor.shouldReseed(ReseedCause.APP_OPEN, now, stamp(7 * 60 * min), origin))
    }
}
