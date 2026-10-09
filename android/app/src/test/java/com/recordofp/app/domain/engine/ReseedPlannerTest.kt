package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReseedPlannerTest {

    private val here = GeoPoint(37.5000, 127.0000)

    /** i * 0.001도 ≈ 111m 간격 — 근접 병합(50m)에 걸리지 않는 후보 생성 */
    private fun cand(id: String, i: Int) = PoiCandidate(
        id = id,
        name = "poi-$id",
        point = GeoPoint(37.5000 + i * 0.001, 127.0000),
        distanceM = i * 111.0,
    )

    private fun trigger(key: String, count: Int, idPrefix: String = key) = TriggerCandidates(
        matchKey = key,
        candidates = (1..count).map { cand("$idPrefix-$it", it) },
    )

    private fun List<PlannedFence>.pois() = filter { it.kind == FenceKind.POI }

    private fun List<PlannedFence>.countFor(matchKey: String) =
        pois().count { matchKey in it.matchKeys }

    @Test
    fun `센티널은 항상 1개, EXIT, 지정 반경`() {
        val plan = ReseedPlanner().plan(here, emptyList())
        assertEquals(1, plan.size)
        val sentinel = plan.single()
        assertEquals(FenceKind.SENTINEL, sentinel.kind)
        assertEquals(FenceTransition.EXIT, sentinel.transition)
        assertEquals(EngineParams.SENTINEL_RADIUS_M, sentinel.radiusM)
    }

    @Test
    fun `PLACE 트리거는 예산을 우선 차지하고 ENTER로 등록된다`() {
        val place1 = TriggerCandidates(
            matchKey = "place:1", isPlace = true,
            placePoint = GeoPoint(37.5100, 127.0000), placeName = "우리집 앞 GS25",
        )
        val place2 = TriggerCandidates(
            matchKey = "place:2", isPlace = true,
            placePoint = GeoPoint(37.5200, 127.0000), placeName = "회사 우체국",
        )
        val cat = trigger("cat:convenience", 5)

        val plan = ReseedPlanner(poiBudget = 2).plan(here, listOf(cat, place1, place2))

        val places = plan.filter { it.kind == FenceKind.PLACE }
        assertEquals(2, places.size)
        assertTrue(places.all { it.transition == FenceTransition.ENTER })
        assertEquals(0, plan.pois().size) // 예산 소진 → 카테고리 몫 없음
    }

    @Test
    fun `카테고리 트리거는 예산을 균등 분할한다`() {
        val plan = ReseedPlanner(poiBudget = 9).plan(
            here,
            listOf(trigger("cat:a", 10), trigger("cat:b", 10), trigger("cat:c", 10)),
        )
        assertEquals(3, plan.countFor("cat:a"))
        assertEquals(3, plan.countFor("cat:b"))
        assertEquals(3, plan.countFor("cat:c"))
    }

    @Test
    fun `트리거당 상한을 넘지 않는다`() {
        val plan = ReseedPlanner(poiBudget = 95, perTriggerCap = 20)
            .plan(here, listOf(trigger("cat:a", 30)))
        assertEquals(20, plan.pois().size)
    }

    @Test
    fun `후보가 모자란 트리거의 잔여 예산은 재배분된다`() {
        val plan = ReseedPlanner(poiBudget = 10).plan(
            here,
            listOf(trigger("cat:a", 2), trigger("cat:b", 8)),
        )
        assertEquals(2, plan.countFor("cat:a"))
        assertEquals(8, plan.countFor("cat:b"))
        assertEquals(10, plan.pois().size)
    }

    @Test
    fun `예산이 트리거 수보다 작아도 라운드로빈으로 공평 배분된다`() {
        val triggers = (1..5).map { trigger("cat:$it", 2) }
        val plan = ReseedPlanner(poiBudget = 3).plan(here, triggers)
        assertEquals(3, plan.pois().size)
        // 앞선 트리거부터 1개씩
        assertEquals(1, plan.countFor("cat:1"))
        assertEquals(1, plan.countFor("cat:2"))
        assertEquals(1, plan.countFor("cat:3"))
    }

    @Test
    fun `같은 트리거 안의 50m 이내 근접 후보는 병합된다`() {
        val nearPair = TriggerCandidates(
            matchKey = "cat:a",
            candidates = listOf(
                PoiCandidate("a1", "본점", GeoPoint(37.5000, 127.0000), 10.0),
                // 0.0002도 ≈ 22m — 병합 대상
                PoiCandidate("a2", "분점", GeoPoint(37.5002, 127.0000), 30.0),
                // 0.001도 ≈ 111m — 유지
                PoiCandidate("a3", "별관", GeoPoint(37.5010, 127.0000), 111.0),
            ),
        )
        val plan = ReseedPlanner(poiBudget = 10).plan(here, listOf(nearPair))
        assertEquals(2, plan.pois().size)
        assertTrue(plan.pois().none { it.poiId == "a2" })
    }

    @Test
    fun `여러 트리거가 같은 POI를 매칭하면 펜스 1개에 matchKey가 합쳐진다`() {
        val shared = PoiCandidate("shared", "GS25 역삼점", GeoPoint(37.5010, 127.0000), 111.0)
        val t1 = TriggerCandidates(matchKey = "cat:convenience", candidates = listOf(shared))
        val t2 = TriggerCandidates(matchKey = "brand:gs25", candidates = listOf(shared))

        val plan = ReseedPlanner(poiBudget = 10).plan(here, listOf(t1, t2))

        assertEquals(1, plan.pois().size)
        assertEquals(setOf("cat:convenience", "brand:gs25"), plan.pois().single().matchKeys)
    }

    @Test
    fun `POI 펜스는 DWELL과 loiteringDelay로 등록된다`() {
        val plan = ReseedPlanner(poiBudget = 10).plan(here, listOf(trigger("cat:a", 1)))
        val poi = plan.pois().single()
        assertEquals(FenceTransition.DWELL, poi.transition)
        assertEquals(EngineParams.LOITERING_DELAY_MS, poi.loiteringDelayMs)
        assertEquals(EngineParams.CATEGORY_FENCE_RADIUS_M, poi.radiusM)
    }

    @Test
    fun `iOS 파라미터(예산 18, 상한 6)에서도 같은 알고리즘이 동작한다`() {
        val plan = ReseedPlanner(poiBudget = 18, perTriggerCap = 6).plan(
            here,
            listOf(trigger("cat:a", 10), trigger("cat:b", 10), trigger("cat:c", 10)),
        )
        assertEquals(18, plan.pois().size)
        assertEquals(6, plan.countFor("cat:a"))
        assertEquals(6, plan.countFor("cat:b"))
        assertEquals(6, plan.countFor("cat:c"))
    }

    @Test
    fun `PLACE 펜스는 카카오 지점 id를 poiId로 싣는다 (검토 C1)`() {
        val place = TriggerCandidates(
            matchKey = "place:1", isPlace = true,
            placePoint = GeoPoint(37.5100, 127.0000), placeName = "우리집 앞 GS25", placeKakaoId = "k9",
        )
        val fence = ReseedPlanner().plan(here, listOf(place)).single { it.kind == FenceKind.PLACE }
        assertEquals("k9", fence.poiId)
    }

}
