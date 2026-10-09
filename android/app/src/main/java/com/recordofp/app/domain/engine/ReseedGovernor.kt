package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.distanceMeters

/**
 * 재배치 원인 (스펙 §6.2). EngineRunLog.cause에 name이 그대로 남는다.
 * FENCE_LOST는 스펙 표 밖의 원인이다 — GEOFENCE_NOT_AVAILABLE(위치 꺼짐 등으로 OS가 펜스를 전부 지움) 복구 (검토 B1).
 */
enum class ReseedCause { BOOT, FENCE_LOST, SENTINEL_EXIT, PERIODIC, APP_OPEN, ITEM_CHANGE, RETRY }

data class ReseedStamp(val atMs: Long, val point: GeoPoint)

/** 재배치 실행 여부 판정 (스펙 §6.2). current는 판정 시점의 위치. */
class ReseedGovernor {

    fun shouldReseed(cause: ReseedCause, nowMs: Long, last: ReseedStamp?, current: GeoPoint): Boolean {
        if (last == null) return true
        // 시계가 거꾸로 가 스탬프가 미래에 있으면 경과 시간을 알 수 없다 — 간격이 지난 것으로 보고 스탬프를 새로 쓴다
        val age = (nowMs - last.atMs).let { if (it < 0) Long.MAX_VALUE else it }
        return when (cause) {
            // 지오펜스 소멸(BOOT·FENCE_LOST)·항목 변경은 즉시 반영 — 코얼레싱은 WorkManager 큐가 담당.
            // PERIODIC은 다른 신호가 다 죽었을 때의 최후 방어선이라 디바운스를 받지 않는다 (§6.2, 6시간에 카카오 1회분)
            ReseedCause.BOOT, ReseedCause.FENCE_LOST, ReseedCause.ITEM_CHANGE, ReseedCause.PERIODIC -> true
            ReseedCause.APP_OPEN -> age >= EngineParams.RESEED_MIN_INTERVAL_MS && (
                distanceMeters(last.point, current) >= EngineParams.APP_OPEN_RESEED_DISTANCE_M ||
                    age >= EngineParams.APP_OPEN_RESEED_AGE_MS
                )
            ReseedCause.SENTINEL_EXIT, ReseedCause.RETRY ->
                age >= EngineParams.RESEED_MIN_INTERVAL_MS
        }
    }
}
