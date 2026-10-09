package com.recordofp.app.platform.work

import com.recordofp.app.data.engine.ReseedResult
import com.recordofp.app.domain.engine.EngineParams
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.domain.model.GeoPoint
import kotlin.coroutines.cancellation.CancellationException

/** 워커 한 번의 부수효과 포트 — ReseedWorker가 Android/WorkManager로 구현하고 테스트는 페이크를 쓴다 */
internal interface ReseedWorkSteps {
    suspend fun markFencesLost()
    suspend fun pruneLogs()
    fun missingPermissions(): List<String>
    suspend fun standDown(cause: ReseedCause, missing: List<String>)
    suspend fun currentLocation(): GeoPoint?
    suspend fun logNoLocation(cause: ReseedCause)
    suspend fun reseed(cause: ReseedCause, here: GeoPoint): ReseedResult
    fun scheduleSentinelRetry(delayMs: Long)
}

internal enum class ReseedWorkResult { SUCCESS, RETRY }

/**
 * ReseedWorker의 분기 판단 (스펙 §6.2·§6.4). Android 타입 없이 JVM에서 검증한다.
 * runAttemptCount는 WorkManager 재시도 횟수(첫 시도 0).
 */
internal suspend fun runReseedWork(
    cause: ReseedCause,
    runAttemptCount: Int,
    steps: ReseedWorkSteps,
): ReseedWorkResult {
    // 재부팅·위치 꺼짐으로 OS 펜스가 사라졌다. 다른 무엇보다 먼저 기록해야 이 작업이 재시도로 밀리거나
    // 큐에서 다른 원인으로 대체돼도 다음에 성공하는 재배치가 전체 재등록한다 (검토 B1)
    if (runAttemptCount == 0 && (cause == ReseedCause.BOOT || cause == ReseedCause.FENCE_LOST)) {
        steps.markFencesLost()
    }

    if (cause == ReseedCause.PERIODIC) {
        // §4.4: 진단 로그가 무한정 쌓이지 않게 주기 작업이 돌 때마다 정리한다. 정리 실패가 재배치를 막지 않는다
        try {
            steps.pruneLogs()
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
        }
    }

    val missing = steps.missingPermissions()
    if (missing.isNotEmpty()) {
        // 재시도해도 소용없다. 등록을 전부 걷고(§6.4 권한 회수) 권한은 보호 상태 대시보드가 알린다 (§4.3, 검토 B3)
        steps.standDown(cause, missing)
        return ReseedWorkResult.SUCCESS
    }

    val here = steps.currentLocation()
    if (here == null) {
        steps.logNoLocation(cause)
        return ReseedWorkResult.RETRY // §6.4 위치 미취득 → 백오프 재시도
    }

    return when (steps.reseed(cause, here)) {
        ReseedResult.FAILED -> ReseedWorkResult.RETRY
        ReseedResult.SKIPPED_DEBOUNCE -> {
            // EXIT는 재신호가 없다 — 디바운스 창 이후로 스스로 재예약한다 (§6.2)
            if (cause == ReseedCause.SENTINEL_EXIT) steps.scheduleSentinelRetry(EngineParams.RESEED_MIN_INTERVAL_MS)
            ReseedWorkResult.SUCCESS
        }
        ReseedResult.APPLIED, ReseedResult.CLEARED_NO_TRIGGERS, ReseedResult.STOOD_DOWN -> ReseedWorkResult.SUCCESS
    }
}
