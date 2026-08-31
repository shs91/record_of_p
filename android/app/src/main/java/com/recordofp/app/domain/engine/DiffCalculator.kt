package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint

/** 현재 OS에 등록된 펜스의 도메인 표현 — data 계층이 GeofenceRegEntity에서 변환한다 */
data class ExistingFence(val key: String, val center: GeoPoint, val radiusM: Float)

data class FenceDiff(val removeIds: List<String>, val add: List<PlannedFence>) {
    val isEmpty: Boolean get() = removeIds.isEmpty() && add.isEmpty()
}

/**
 * 차분 적용 계산 (스펙 §6.3.6). 지오펜스는 수정 불가이므로
 * 같은 키의 좌표/반경 변화는 제거+추가로 교체한다.
 * 좌표는 같은 소스(카카오 응답·재배치 좌표)에서 오므로 완전 일치 비교로 충분하다.
 */
class DiffCalculator {

    fun diff(current: List<ExistingFence>, planned: List<PlannedFence>): FenceDiff {
        val currentByKey = current.associateBy { it.key }
        val plannedByKey = planned.associateBy { it.key }

        val toAdd = planned.filter { p ->
            val cur = currentByKey[p.key]
            cur == null || !cur.matches(p)
        }
        val toRemove = current.filter { c ->
            val plan = plannedByKey[c.key]
            plan == null || !c.matches(plan)
        }.map { it.key }

        return FenceDiff(removeIds = toRemove, add = toAdd)
    }

    private fun ExistingFence.matches(p: PlannedFence): Boolean =
        center == p.center && radiusM == p.radiusM
}
