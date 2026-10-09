package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.distanceMeters

enum class ReseedCause { BOOT, SENTINEL_EXIT, PERIODIC, APP_OPEN, ITEM_CHANGE, RETRY }

data class ReseedStamp(val atMs: Long, val point: GeoPoint)

/** 재배치 실행 여부 판정 (스펙 §6.2). current는 판정 시점의 위치. */
class ReseedGovernor {

    fun shouldReseed(cause: ReseedCause, nowMs: Long, last: ReseedStamp?, current: GeoPoint): Boolean {
        if (last == null) return true
        val age = nowMs - last.atMs
        return when (cause) {
            // 지오펜스 소멸(BOOT)·항목 변경은 즉시 반영 — 코얼레싱은 WorkManager 큐가 담당
            ReseedCause.BOOT, ReseedCause.ITEM_CHANGE -> true
            ReseedCause.APP_OPEN -> age >= EngineParams.RESEED_MIN_INTERVAL_MS && (
                distanceMeters(last.point, current) >= EngineParams.APP_OPEN_RESEED_DISTANCE_M ||
                    age >= EngineParams.APP_OPEN_RESEED_AGE_MS
                )
            ReseedCause.SENTINEL_EXIT, ReseedCause.PERIODIC, ReseedCause.RETRY ->
                age >= EngineParams.RESEED_MIN_INTERVAL_MS
        }
    }
}
