package com.recordofp.app.platform.work

import com.recordofp.app.data.engine.ReseedResult
import com.recordofp.app.domain.engine.EngineParams
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.domain.model.GeoPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 호출 순서를 기록하는 워커 부수효과 페이크 */
private class FakeSteps(
    var missing: List<String> = emptyList(),
    var location: GeoPoint? = GeoPoint(37.5, 127.0),
    var result: ReseedResult = ReseedResult.APPLIED,
    var pruneFails: Boolean = false,
) : ReseedWorkSteps {
    val calls = mutableListOf<String>()
    val standDowns = mutableListOf<List<String>>()
    val sentinelRetries = mutableListOf<Long>()

    override suspend fun markFencesLost() { calls += "mark" }
    override suspend fun pruneLogs() {
        calls += "prune"
        if (pruneFails) throw IllegalStateException("db locked")
    }
    override fun missingPermissions(): List<String> { calls += "perm"; return missing }
    override suspend fun standDown(cause: ReseedCause, missing: List<String>) {
        calls += "standDown"
        standDowns += missing
    }
    override suspend fun currentLocation(): GeoPoint? { calls += "location"; return location }
    override suspend fun logNoLocation(cause: ReseedCause) { calls += "noLocation" }
    override suspend fun reseed(cause: ReseedCause, here: GeoPoint): ReseedResult { calls += "reseed"; return result }
    override fun scheduleSentinelRetry(delayMs: Long) {
        calls += "sentinelRetry"
        sentinelRetries += delayMs
    }
}

class ReseedWorkFlowTest {

    private suspend fun run(cause: ReseedCause, steps: FakeSteps, attempt: Int = 0) =
        runReseedWork(cause, attempt, steps)

    @Test
    fun `BOOT·FENCE_LOST 첫 시도는 무엇보다 먼저 펜스 소실을 표시한다`() = runTest {
        listOf(ReseedCause.BOOT, ReseedCause.FENCE_LOST).forEach { cause ->
            val steps = FakeSteps()
            assertEquals(ReseedWorkResult.SUCCESS, run(cause, steps))
            assertEquals("$cause", listOf("mark", "perm", "location", "reseed"), steps.calls)
        }
    }

    @Test
    fun `재시도 차례나 다른 원인은 펜스 소실을 표시하지 않는다`() = runTest {
        val retry = FakeSteps()
        run(ReseedCause.BOOT, retry, attempt = 1)
        val sentinel = FakeSteps()
        run(ReseedCause.SENTINEL_EXIT, sentinel)
        assertTrue("mark" !in retry.calls && "mark" !in sentinel.calls)
    }

    @Test
    fun `PERIODIC은 로그를 정리하고, 정리가 실패해도 재배치를 계속한다`() = runTest {
        val steps = FakeSteps(pruneFails = true)
        assertEquals(ReseedWorkResult.SUCCESS, run(ReseedCause.PERIODIC, steps))
        assertEquals(listOf("prune", "perm", "location", "reseed"), steps.calls)
    }

    @Test
    fun `위치 권한이 없으면 사유와 함께 등록을 걷고 재시도하지 않는다`() = runTest {
        val steps = FakeSteps(missing = listOf("android.permission.ACCESS_BACKGROUND_LOCATION"))
        assertEquals(ReseedWorkResult.SUCCESS, run(ReseedCause.ITEM_CHANGE, steps))
        assertEquals(listOf("perm", "standDown"), steps.calls)
        assertEquals(listOf(listOf("android.permission.ACCESS_BACKGROUND_LOCATION")), steps.standDowns)
    }

    @Test
    fun `위치를 못 얻으면 기록하고 재시도한다`() = runTest {
        val steps = FakeSteps(location = null)
        assertEquals(ReseedWorkResult.RETRY, run(ReseedCause.SENTINEL_EXIT, steps))
        assertEquals(listOf("perm", "location", "noLocation"), steps.calls)
    }

    @Test
    fun `재배치가 실패하면 재시도한다`() = runTest {
        assertEquals(ReseedWorkResult.RETRY, run(ReseedCause.ITEM_CHANGE, FakeSteps(result = ReseedResult.FAILED)))
    }

    @Test
    fun `디바운스된 센티널 이탈은 최소 간격 뒤로 다시 예약하고 성공으로 끝낸다`() = runTest {
        // EXIT는 재신호가 없다 — 버리면 사용자는 유일한 센티널 밖에 남는다 (§6.2)
        val steps = FakeSteps(result = ReseedResult.SKIPPED_DEBOUNCE)
        assertEquals(ReseedWorkResult.SUCCESS, run(ReseedCause.SENTINEL_EXIT, steps))
        assertEquals(listOf(EngineParams.RESEED_MIN_INTERVAL_MS), steps.sentinelRetries)
    }

    @Test
    fun `다른 원인의 디바운스는 다시 예약하지 않는다`() = runTest {
        val steps = FakeSteps(result = ReseedResult.SKIPPED_DEBOUNCE)
        run(ReseedCause.APP_OPEN, steps)
        assertTrue(steps.sentinelRetries.isEmpty())
    }

    @Test
    fun `적용·트리거 없음·스탠드다운은 성공으로 끝낸다`() = runTest {
        listOf(ReseedResult.APPLIED, ReseedResult.CLEARED_NO_TRIGGERS, ReseedResult.STOOD_DOWN).forEach { r ->
            assertEquals("$r", ReseedWorkResult.SUCCESS, run(ReseedCause.ITEM_CHANGE, FakeSteps(result = r)))
        }
    }
}
