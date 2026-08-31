package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.distanceMeters

/**
 * 재배치 플래너 (설계 §6.3). 순수 로직 — Android API 무의존.
 *
 * 1. 센티널 1개 (이동 감지용 EXIT 펜스)
 * 2. PLACE 트리거 우선 등록 (가까운 순, 예산 내 전부)
 * 3. CATEGORY/BRAND: 트리거 내 근접 중복 제거 → 균등 배분(1차) → 잔여 예산 라운드로빈(2차)
 * 4. 같은 POI를 여러 트리거가 매칭하면 펜스 1개에 matchKey 합집합
 */
class ReseedPlanner(
    private val poiBudget: Int = EngineParams.POI_BUDGET,
    private val perTriggerCap: Int = EngineParams.PER_TRIGGER_CAP,
    private val mergeDistanceM: Double = EngineParams.MERGE_DISTANCE_M,
) {

    fun plan(current: GeoPoint, triggers: List<TriggerCandidates>): List<PlannedFence> {
        val fences = mutableListOf<PlannedFence>()
        fences += PlannedFence(
            key = "sentinel",
            kind = FenceKind.SENTINEL,
            center = current,
            radiusM = EngineParams.SENTINEL_RADIUS_M,
            transition = FenceTransition.EXIT,
        )

        var budget = poiBudget

        // 1) PLACE: 사용자가 명시한 지점은 거리 무관 등록, 예산 초과 시 가까운 순
        val places = triggers
            .filter { it.isPlace && it.placePoint != null }
            .sortedBy { distanceMeters(current, it.placePoint!!) }
        for (p in places) {
            if (budget <= 0) break
            fences += PlannedFence(
                key = p.matchKey,
                kind = FenceKind.PLACE,
                center = p.placePoint!!,
                radiusM = EngineParams.PLACE_FENCE_RADIUS_M,
                transition = FenceTransition.ENTER,
                matchKeys = setOf(p.matchKey),
                poiName = p.placeName,
            )
            budget--
        }

        // 2) CATEGORY/BRAND
        val cats = triggers.filter { !it.isPlace }
        if (cats.isEmpty() || budget <= 0) return fences

        val deduped: Map<String, List<PoiCandidate>> =
            cats.associate { t -> t.matchKey to dedupeByProximity(t.candidates) }

        val picks = mutableListOf<Pair<String, PoiCandidate>>() // (matchKey, poi)
        val takenCount = HashMap<String, Int>()
        val nextIndex = HashMap<String, Int>()

        // 1차: 균등 배분 (예산/트리거 수, 트리거당 상한 이내)
        val quota = minOf(perTriggerCap, budget / cats.size)
        for (t in cats) {
            val list = deduped.getValue(t.matchKey)
            var i = 0
            while (i < list.size && (takenCount[t.matchKey] ?: 0) < quota) {
                picks += t.matchKey to list[i]
                takenCount[t.matchKey] = (takenCount[t.matchKey] ?: 0) + 1
                i++
            }
            nextIndex[t.matchKey] = i
        }

        // 2차: 잔여 예산 라운드로빈 재배분 (후보가 남은 트리거에)
        var left = budget - picks.size
        var progressed = true
        while (left > 0 && progressed) {
            progressed = false
            for (t in cats) {
                if (left <= 0) break
                val list = deduped.getValue(t.matchKey)
                val i = nextIndex.getValue(t.matchKey)
                if (i < list.size && (takenCount[t.matchKey] ?: 0) < perTriggerCap) {
                    picks += t.matchKey to list[i]
                    nextIndex[t.matchKey] = i + 1
                    takenCount[t.matchKey] = (takenCount[t.matchKey] ?: 0) + 1
                    left--
                    progressed = true
                }
            }
        }

        // 3) 같은 POI를 여러 트리거가 선택 → 펜스 1개로 병합
        val byPoi = picks.groupBy { it.second.id }
        for ((poiId, group) in byPoi) {
            val poi = group.first().second
            fences += PlannedFence(
                key = "poi:$poiId",
                kind = FenceKind.POI,
                center = poi.point,
                radiusM = EngineParams.CATEGORY_FENCE_RADIUS_M,
                transition = FenceTransition.DWELL,
                loiteringDelayMs = EngineParams.LOITERING_DELAY_MS,
                matchKeys = group.map { it.first }.toSet(),
                poiName = poi.name,
                poiId = poiId,
            )
        }
        return fences
    }

    /** 같은 트리거 안에서 서로 mergeDistanceM 이내인 후보는 앞선 것만 남긴다 (설계 §6.3.3) */
    private fun dedupeByProximity(sorted: List<PoiCandidate>): List<PoiCandidate> {
        val kept = mutableListOf<PoiCandidate>()
        for (c in sorted) {
            if (kept.none { distanceMeters(it.point, c.point) < mergeDistanceM }) kept += c
        }
        return kept
    }
}
