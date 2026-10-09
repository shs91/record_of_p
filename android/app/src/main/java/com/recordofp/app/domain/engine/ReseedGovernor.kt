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
        val age = nowMs - last.atMs
        return when (cause) {
            // 지오펜스 소멸(BOOT·FENCE_LOST)·항목 변경은 즉시 반영 — 코얼레싱은 WorkManager 큐가 담당
            ReseedCause.BOOT, ReseedCause.FENCE_LOST, ReseedCause.ITEM_CHANGE -> true
            ReseedCause.APP_OPEN -> age >= EngineParams.RESEED_MIN_INTERVAL_MS && (
                distanceMeters(last.point, current) >= EngineParams.APP_OPEN_RESEED_DISTANCE_M ||
                    age >= EngineParams.APP_OPEN_RESEED_AGE_MS
                )
            ReseedCause.SENTINEL_EXIT, ReseedCause.PERIODIC, ReseedCause.RETRY ->
                age >= EngineParams.RESEED_MIN_INTERVAL_MS
        }
    }
}
