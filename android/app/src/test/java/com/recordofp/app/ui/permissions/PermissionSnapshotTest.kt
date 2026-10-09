package com.recordofp.app.ui.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionSnapshotTest {

    private val allOn = PermissionSnapshot(
        notifications = true, fineLocation = true, backgroundLocation = true,
        batteryUnrestricted = false, locationServicesOn = true,
    )

    @Test
    fun `모두 켜져 있으면 완전 보호 상태다 - 배터리 예외는 권장일 뿐이다`() {
        assertTrue(allOn.fullyProtected)
    }

    @Test
    fun `기기 위치가 꺼져 있으면 권한이 다 있어도 완전 보호가 아니다`() {
        assertFalse(allOn.copy(locationServicesOn = false).fullyProtected)
    }

    @Test
    fun `알림·정확한 위치·항상 허용 중 하나라도 꺼지면 완전 보호가 아니다`() {
        assertFalse(allOn.copy(notifications = false).fullyProtected)
        assertFalse(allOn.copy(fineLocation = false).fullyProtected)
        assertFalse(allOn.copy(backgroundLocation = false).fullyProtected)
    }
}
