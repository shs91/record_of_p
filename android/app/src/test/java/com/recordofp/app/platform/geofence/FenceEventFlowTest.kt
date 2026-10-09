package com.recordofp.app.platform.geofence

import com.recordofp.app.data.engine.AlertGroup
import com.recordofp.app.data.engine.EventOutcome
import com.recordofp.app.domain.engine.SENTINEL_FENCE_KEY
import com.recordofp.app.domain.model.Reminder
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

private class FakeEventSteps(
    private val outcome: EventOutcome? = null,
    private val evaluateError: Exception? = null,
    private val failingFence: String? = null,
    private val hiddenFence: String? = null,
    private val sentinelFails: Boolean = false,
) : FenceEventSteps {
    val calls = mutableListOf<String>()
    val errors = mutableListOf<String>()

    override suspend fun evaluate(ids: List<String>): EventOutcome {
        calls += "evaluate"
        evaluateError?.let { throw it }
        return outcome!!
    }
    override fun scheduleSentinelReseed() {
        if (sentinelFails) throw IllegalStateException("WorkManager not initialized")
        calls += "sentinel"
    }
    override fun show(group: AlertGroup): Boolean {
        if (group.fenceId == failingFence) throw SecurityException("POST_NOTIFICATIONS revoked")
        calls += "show:${group.fenceId}"
        return group.fenceId != hiddenFence
    }
    override suspend fun recordShown(group: AlertGroup) { calls += "shown:${group.fenceId}" }
    override suspend fun recordNotShown(group: AlertGroup) { calls += "notShown:${group.fenceId}" }
    override suspend fun logError(error: Exception) { errors += error.javaClass.simpleName }
}

class FenceEventFlowTest {

    private fun group(fenceId: String) = AlertGroup(
        fenceId, poiId = fenceId, poiName = "CU",
        reminders = listOf(Reminder(id = 1, title = "건전지", createdAt = 0, updatedAt = 0)),
    )

    @Test
    fun `센티널 재배치를 알림보다 먼저 예약한다`() = runTest {
        val steps = FakeEventSteps(EventOutcome(sentinelExited = true, groups = listOf(group("poi:1"))))
        runFenceEvent(listOf("poi:1", SENTINEL_FENCE_KEY), steps)
        assertEquals(listOf("evaluate", "sentinel", "show:poi:1", "shown:poi:1"), steps.calls)
    }

    @Test
    fun `판정이 실패해도 이벤트에 센티널이 있으면 재배치를 예약하고 오류를 기록한다`() = runTest {
        val steps = FakeEventSteps(evaluateError = IllegalStateException("db closed"))
        runFenceEvent(listOf("poi:1", SENTINEL_FENCE_KEY), steps) // 예외가 밖으로 나오지 않는다
        assertEquals(listOf("evaluate", "sentinel"), steps.calls)
        assertEquals(listOf("IllegalStateException"), steps.errors)
    }

    @Test
    fun `판정이 실패하고 센티널이 없으면 오류만 기록한다`() = runTest {
        val steps = FakeEventSteps(evaluateError = IllegalStateException("db closed"))
        runFenceEvent(listOf("poi:1"), steps)
        assertEquals(listOf("evaluate"), steps.calls)
        assertEquals(listOf("IllegalStateException"), steps.errors)
    }

    @Test
    fun `알림 하나가 실패해도 나머지 알림은 발행되고 오류가 기록된다`() = runTest {
        val steps = FakeEventSteps(
            EventOutcome(sentinelExited = false, groups = listOf(group("poi:1"), group("poi:2"))),
            failingFence = "poi:1",
        )
        runFenceEvent(listOf("poi:1", "poi:2"), steps)
        assertEquals(listOf("evaluate", "show:poi:2", "shown:poi:2"), steps.calls)
        assertEquals(listOf("SecurityException"), steps.errors)
    }

    @Test
    fun `표시하지 못한 묶음은 표시 실패로 기록한다`() = runTest {
        val steps = FakeEventSteps(EventOutcome(false, listOf(group("poi:1"))), hiddenFence = "poi:1")
        runFenceEvent(listOf("poi:1"), steps)
        assertEquals(listOf("evaluate", "show:poi:1", "notShown:poi:1"), steps.calls)
    }

    @Test
    fun `센티널 예약이 실패해도 알림은 발행된다`() = runTest {
        val steps = FakeEventSteps(EventOutcome(true, listOf(group("poi:1"))), sentinelFails = true)
        runFenceEvent(listOf("poi:1", SENTINEL_FENCE_KEY), steps)
        assertEquals(listOf("evaluate", "show:poi:1", "shown:poi:1"), steps.calls)
        assertEquals(listOf("IllegalStateException"), steps.errors)
    }

    @Test
    fun `취소는 오류로 기록하지 않고 그대로 전파한다`() = runTest {
        val steps = FakeEventSteps(evaluateError = CancellationException("cancelled"))
        try {
            runFenceEvent(listOf("poi:1"), steps)
            fail("취소 예외가 전파되어야 한다")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
        assertEquals(emptyList<String>(), steps.errors)
    }
}
