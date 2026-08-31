package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.ReminderStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * 알림 필터 체인 (설계 §6.5). 순수 로직 — Android API 무의존.
 * 평가 순서: 상태 → 스누즈 → 방해금지 → 항목 쿨다운 → 항목·지점 쿨다운
 *           → 항목당 하루 상한 → 전체 하루 상한
 * 차단 사유는 EngineRunLog로 남겨 진단 화면("왜 안 울렸는지")에 노출한다.
 */
class NotificationGate(private val zone: ZoneId) {

    data class History(
        /** 이 항목의 마지막 알림 시각 */
        val lastShownForItem: Instant? = null,
        /** 이 항목이 이 지점(POI)에서 마지막으로 알림된 시각 */
        val lastShownForItemAtPoi: Instant? = null,
        /** 오늘(로컬 날짜) 이 항목의 알림 수 */
        val shownTodayForItem: Int = 0,
        /** 오늘(로컬 날짜) 전체 알림 수 */
        val shownTodayTotal: Int = 0,
    )

    data class Policy(
        val cooldownPerItem: Duration = Duration.ofMillis(EngineParams.COOLDOWN_PER_ITEM_MS),
        val cooldownPerItemPlace: Duration = Duration.ofMillis(EngineParams.COOLDOWN_PER_ITEM_PLACE_MS),
        val dailyCapPerItem: Int = EngineParams.DAILY_CAP_PER_ITEM,
        val dailyCapTotal: Int = EngineParams.DAILY_CAP_TOTAL,
        /** 분 단위 [시작, 끝). 자정 걸침 허용. 시작==끝이면 비활성 */
        val quietStartMinute: Int = EngineParams.QUIET_START_MINUTE,
        val quietEndMinute: Int = EngineParams.QUIET_END_MINUTE,
    )

    enum class Decision {
        PASS,
        BLOCK_STATUS,
        BLOCK_SNOOZED,
        BLOCK_QUIET_HOURS,
        BLOCK_ITEM_COOLDOWN,
        BLOCK_PLACE_COOLDOWN,
        BLOCK_ITEM_DAILY_CAP,
        BLOCK_TOTAL_DAILY_CAP,
    }

    fun evaluate(
        now: Instant,
        status: ReminderStatus,
        snoozeUntil: Instant?,
        history: History,
        policy: Policy = Policy(),
    ): Decision {
        if (status != ReminderStatus.ACTIVE) return Decision.BLOCK_STATUS

        if (snoozeUntil != null && now < snoozeUntil) return Decision.BLOCK_SNOOZED

        if (inQuietHours(now, policy)) return Decision.BLOCK_QUIET_HOURS

        val lastItem = history.lastShownForItem
        if (lastItem != null && Duration.between(lastItem, now) < policy.cooldownPerItem) {
            return Decision.BLOCK_ITEM_COOLDOWN
        }

        val lastPlace = history.lastShownForItemAtPoi
        if (lastPlace != null && Duration.between(lastPlace, now) < policy.cooldownPerItemPlace) {
            return Decision.BLOCK_PLACE_COOLDOWN
        }

        if (history.shownTodayForItem >= policy.dailyCapPerItem) return Decision.BLOCK_ITEM_DAILY_CAP

        if (history.shownTodayTotal >= policy.dailyCapTotal) return Decision.BLOCK_TOTAL_DAILY_CAP

        return Decision.PASS
    }

    private fun inQuietHours(now: Instant, policy: Policy): Boolean {
        val start = policy.quietStartMinute
        val end = policy.quietEndMinute
        if (start == end) return false // 비활성
        val local = now.atZone(zone)
        val minuteOfDay = local.hour * 60 + local.minute
        return if (start < end) {
            minuteOfDay in start until end
        } else {
            // 자정 걸침 (예: 22:00~08:00)
            minuteOfDay >= start || minuteOfDay < end
        }
    }
}
