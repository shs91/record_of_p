package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffCalculatorTest {

    private val calc = DiffCalculator()

    private fun planned(key: String, lat: Double = 37.5, radius: Float = 120f) = PlannedFence(
        key = key, kind = FenceKind.POI, center = GeoPoint(lat, 127.0), radiusM = radius,
        transition = FenceTransition.DWELL, loiteringDelayMs = 60_000, matchKeys = setOf("cat:a"),
    )

    private fun existing(key: String, lat: Double = 37.5, radius: Float = 120f) =
        ExistingFence(key, GeoPoint(lat, 127.0), radius)

    @Test
    fun `동일한 키·좌표·반경은 건드리지 않는다`() {
        val diff = calc.diff(listOf(existing("poi:1")), listOf(planned("poi:1")))
        assertTrue(diff.isEmpty)
    }

    @Test
    fun `새 펜스는 add, 사라진 펜스는 remove로 분류된다`() {
        val diff = calc.diff(listOf(existing("poi:old")), listOf(planned("poi:new")))
        assertEquals(listOf("poi:old"), diff.removeIds)
        assertEquals(listOf("poi:new"), diff.add.map { it.key })
    }

    @Test
    fun `같은 키인데 좌표가 달라지면 제거+추가로 교체된다`() {
        val diff = calc.diff(listOf(existing("sentinel", lat = 37.5)), listOf(planned("sentinel", lat = 37.6)))
        assertEquals(listOf("sentinel"), diff.removeIds)
        assertEquals(listOf("sentinel"), diff.add.map { it.key })
    }

    @Test
    fun `같은 키인데 반경이 달라져도 교체된다 - EngineParams 튜닝 반영 경로`() {
        val diff = calc.diff(listOf(existing("poi:1", radius = 120f)), listOf(planned("poi:1", radius = 150f)))
        assertEquals(listOf("poi:1"), diff.removeIds)
        assertEquals(1, diff.add.size)
    }

    @Test
    fun `혼합 시나리오 - 유지 1, 교체 1, 추가 1, 제거 1`() {
        val current = listOf(existing("keep"), existing("move", lat = 37.5), existing("gone"))
        val plannedList = listOf(planned("keep"), planned("move", lat = 37.7), planned("fresh"))
        val diff = calc.diff(current, plannedList)
        assertEquals(setOf("move", "gone"), diff.removeIds.toSet())
        assertEquals(setOf("move", "fresh"), diff.add.map { it.key }.toSet())
    }
}
