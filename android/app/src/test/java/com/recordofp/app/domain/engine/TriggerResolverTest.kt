package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.PoiResolution
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerResolverTest {

    private val resolver = TriggerResolver()

    private fun cat(id: String, reminderId: Long = 1, specId: Long = reminderId * 10) =
        TriggerSpec(id = specId, reminderId = reminderId, type = TriggerType.CATEGORY, categoryId = id)

    @Test
    fun `카테고리는 카탈로그의 해석 방식으로 변환된다`() {
        val out = resolver.resolve(listOf(cat("convenience"), cat("post", reminderId = 2)))
        val byKey = out.filterIsInstance<QueryRequest>().associateBy { it.matchKey }
        assertEquals(PoiResolution.KAKAO_CODE, byKey.getValue("cat:convenience").resolution)
        assertEquals("CS2", byKey.getValue("cat:convenience").query)
        assertEquals(PoiResolution.KEYWORD, byKey.getValue("cat:post").resolution)
        assertEquals("우체국", byKey.getValue("cat:post").query)
    }

    @Test
    fun `여러 리마인더가 같은 카테고리를 쓰면 요청은 1개다`() {
        val out = resolver.resolve(listOf(cat("convenience", 1), cat("convenience", 2), cat("convenience", 3)))
        assertEquals(1, out.size)
    }

    @Test
    fun `브랜드는 트림·소문자 정규화된 matchKey의 키워드 요청이 된다`() {
        val spec = TriggerSpec(id = 5, reminderId = 1, type = TriggerType.BRAND, brandKeyword = " GS25 ")
        val req = resolver.resolve(listOf(spec)).single() as QueryRequest
        assertEquals("brand:gs25", req.matchKey)
        assertEquals(PoiResolution.KEYWORD, req.resolution)
        assertEquals("GS25", req.query) // 검색어는 원문 트림만
    }

    @Test
    fun `PLACE는 좌표 그대로 PlaceRequest가 된다`() {
        val spec = TriggerSpec(
            id = 7, reminderId = 1, type = TriggerType.PLACE,
            placeName = "우리집 앞 GS25", placePoint = GeoPoint(37.5, 127.0),
        )
        val req = resolver.resolve(listOf(spec)).single() as PlaceRequest
        assertEquals("place:7", req.matchKey)
        assertEquals(GeoPoint(37.5, 127.0), req.point)
        assertEquals("우리집 앞 GS25", req.name)
    }

    @Test
    fun `알 수 없는 카테고리 id와 좌표 없는 PLACE는 조용히 제외된다`() {
        val out = resolver.resolve(
            listOf(
                cat("no-such-id"),
                TriggerSpec(id = 9, reminderId = 1, type = TriggerType.PLACE, placePoint = null),
            ),
        )
        assertTrue(out.isEmpty())
    }
}
