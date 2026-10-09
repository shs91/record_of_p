package com.recordofp.app.platform.geofence

import com.recordofp.app.data.engine.AlertGroup
import com.recordofp.app.data.engine.EventOutcome
import com.recordofp.app.domain.engine.SENTINEL_FENCE_KEY
import kotlin.coroutines.cancellation.CancellationException

/** 지오펜스 이벤트 한 번의 부수효과 포트 — 리시버가 구현하고 테스트는 페이크를 쓴다 (최종 리뷰 I1) */
internal interface FenceEventSteps {
    suspend fun evaluate(ids: List<String>): EventOutcome
    fun scheduleSentinelReseed()

    /** @return 실제로 표시했으면 true */
    fun show(group: AlertGroup): Boolean
    suspend fun recordShown(group: AlertGroup)
    suspend fun recordNotShown(group: AlertGroup)

    /** 던지지 않는다 */
    suspend fun logError(error: Exception)
}

/**
 * 지오펜스 이벤트 처리 순서 (스펙 §6.5). 어떤 예외도 밖으로 내보내지 않는다 (최종 리뷰 I1).
 * - 센티널 재배치를 알림보다 먼저 예약한다 — 알림 발행이 실패해도 이동 감지 사슬은 이어진다 (§6.2)
 * - 판정이 실패해도 이벤트에 센티널이 있으면 재배치는 예약한다
 * - 알림은 묶음마다 따로 처리한다 — 하나가 실패해도(예: 방금 알림 권한 회수) 나머지는 보인다
 */
internal suspend fun runFenceEvent(ids: List<String>, steps: FenceEventSteps) {
    try {
        val outcome = try {
            steps.evaluate(ids)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            if (SENTINEL_FENCE_KEY in ids) scheduleSentinelSafely(steps)
            throw e
        }
        if (outcome.sentinelExited) scheduleSentinelSafely(steps)
        outcome.groups.forEach { group ->
            try {
                // 표시가 실제로 성공했을 때만 쿨다운을 소모한다 (M1, 검토 C2)
                if (steps.show(group)) steps.recordShown(group) else steps.recordNotShown(group)
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                steps.logError(e)
            }
        }
    } catch (c: CancellationException) {
        throw c
    } catch (e: Exception) {
        steps.logError(e)
    }
}

/** 센티널 예약이 실패해도(WorkManager 오류) 그 이벤트의 알림은 계속 발행한다 */
private suspend fun scheduleSentinelSafely(steps: FenceEventSteps) {
    try {
        steps.scheduleSentinelReseed()
    } catch (c: CancellationException) {
        throw c
    } catch (e: Exception) {
        steps.logError(e)
    }
}
