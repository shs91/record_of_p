package com.recordofp.app.domain.engine

import com.recordofp.app.domain.engine.NotificationGate.Decision
import com.recordofp.app.domain.engine.NotificationGate.History
import com.recordofp.app.domain.engine.NotificationGate.Policy
import com.recordofp.app.domain.model.ReminderStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationGateTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val gate = NotificationGate(zone)

    /** 2026-08-31 로컬 시각의 Instant */
    private fun at(hour: Int, minute: Int = 0): Instant =
        LocalDate.of(2026, 8, 31).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun decide(
        now: Instant = at(12),
        status: ReminderStatus = ReminderStatus.ACTIVE,
        snoozeUntil: Instant? = null,
        history: History = History(),
        policy: Policy = Policy(),
    ): Decision = gate.evaluate(now, status, snoozeUntil, history, policy)

    @Test
    fun `깨끗한 상태의 낮 시간이면 통과한다`() {
        assertEquals(Decision.PASS, decide())
    }

    @Test
    fun `완료·보관 항목은 차단된다`() {
        assertEquals(Decision.BLOCK_STATUS, decide(status = ReminderStatus.DONE))
        assertEquals(Decision.BLOCK_STATUS, decide(status = ReminderStatus.ARCHIVED))
    }

    @Test
    fun `스누즈 중이면 차단되고, 해제 시각부터 통과한다`() {
        assertEquals(Decision.BLOCK_SNOOZED, decide(now = at(12), snoozeUntil = at(13)))
        assertEquals(Decision.PASS, decide(now = at(13), snoozeUntil = at(13)))
    }

    @Test
    fun `방해금지 시간대는 자정 걸침을 포함해 차단된다`() {
        assertEquals(Decision.BLOCK_QUIET_HOURS, decide(now = at(23, 30)))
        assertEquals(Decision.BLOCK_QUIET_HOURS, decide(now = at(7, 59)))
        assertEquals(Decision.BLOCK_QUIET_HOURS, decide(now = at(22, 0)))
        assertEquals(Decision.PASS, decide(now = at(8, 0)))
        assertEquals(Decision.PASS, decide(now = at(21, 59)))
    }

    @Test
    fun `방해금지 시작==끝이면 비활성이다`() {
        val noQuiet = Policy(quietStartMinute = 0, quietEndMinute = 0)
        assertEquals(Decision.PASS, decide(now = at(23, 30), policy = noQuiet))
    }

    @Test
    fun `필터 순서 - 상태와 스누즈가 방해금지보다 먼저 평가된다`() {
        assertEquals(
            Decision.BLOCK_STATUS,
            decide(now = at(23, 30), status = ReminderStatus.DONE),
        )
        assertEquals(
            Decision.BLOCK_SNOOZED,
            decide(now = at(23, 30), snoozeUntil = at(23, 59)),
        )
    }

    @Test
    fun `항목 쿨다운 이내면 차단, 경과하면 통과한다`() {
        val now = at(12)
        assertEquals(
            Decision.BLOCK_ITEM_COOLDOWN,
            decide(now = now, history = History(lastShownForItem = now - Duration.ofHours(2))),
        )
        assertEquals(
            Decision.PASS,
            decide(now = now, history = History(lastShownForItem = now - Duration.ofHours(4))),
        )
    }

    @Test
    fun `같은 항목·같은 지점은 24시간 이내 재알림이 차단된다`() {
        val now = at(12)
        val history = History(
            lastShownForItem = now - Duration.ofHours(5), // 항목 쿨다운은 통과
            lastShownForItemAtPoi = now - Duration.ofHours(23),
        )
        assertEquals(Decision.BLOCK_PLACE_COOLDOWN, decide(now = now, history = history))
        assertEquals(
            Decision.PASS,
            decide(now = now, history = history.copy(lastShownForItemAtPoi = now - Duration.ofHours(25))),
        )
    }

    @Test
    fun `하루 상한 - 항목당 3건, 전체 10건`() {
        assertEquals(
            Decision.BLOCK_ITEM_DAILY_CAP,
            decide(history = History(shownTodayForItem = 3)),
        )
        assertEquals(
            Decision.BLOCK_TOTAL_DAILY_CAP,
            decide(history = History(shownTodayForItem = 2, shownTodayTotal = 10)),
        )
    }
}
