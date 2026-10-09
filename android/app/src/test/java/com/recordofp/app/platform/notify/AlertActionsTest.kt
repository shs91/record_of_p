package com.recordofp.app.platform.notify

import com.recordofp.app.data.engine.AlertGroup
import com.recordofp.app.domain.model.Reminder
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertActionsTest {

    private fun group(vararg ids: Long) = AlertGroup(
        fenceId = "poi:1", poiId = "1", poiName = "CU",
        reminders = ids.map { Reminder(id = it, title = "할일$it", createdAt = 0, updatedAt = 0) },
    )

    @Test
    fun `항목이 하나면 완료와 오늘 그만을 둘 다 단다`() {
        assertEquals(listOf(AlertAction.COMPLETE, AlertAction.MUTE_TODAY), alertActionsFor(group(1)))
    }

    @Test
    fun `여러 항목 묶음에는 어느 항목인지 모호한 완료를 빼고 오늘 그만만 단다`() {
        assertEquals(listOf(AlertAction.MUTE_TODAY), alertActionsFor(group(1, 2)))
    }
}
