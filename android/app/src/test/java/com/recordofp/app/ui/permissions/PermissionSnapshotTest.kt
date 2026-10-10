package com.recordofp.app.ui.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `모두 켜져 있으면 배너로 안내할 것이 없다`() {
        assertNull(allOn.topIssue)
    }

    @Test
    fun `꺼진 것이 여럿이면 알림 → 정확한 위치 → 항상 허용 → 기기 위치 순으로 하나만 안내한다`() {
        val allOff = PermissionSnapshot(
            notifications = false, fineLocation = false, backgroundLocation = false,
            batteryUnrestricted = false, locationServicesOn = false,
        )
        assertEquals(ProtectionIssue.NOTIFICATIONS_OFF, allOff.topIssue)
        assertEquals(ProtectionIssue.PRECISE_LOCATION_OFF, allOff.copy(notifications = true).topIssue)
        assertEquals(
            ProtectionIssue.BACKGROUND_LOCATION_OFF,
            allOff.copy(notifications = true, fineLocation = true).topIssue,
        )
        assertEquals(
            ProtectionIssue.LOCATION_SERVICES_OFF,
            allOff.copy(notifications = true, fineLocation = true, backgroundLocation = true).topIssue,
        )
    }

    @Test
    fun `사용 중에만 허용이면 항상 허용 업셀 카드를 보여준다`() {
        // Android 11+ 온보딩은 "사용 중에만"까지만 얻는다 — 워커는 이 상태에서 펜스를 전부 걷는다 (§4.2 4단계)
        assertEquals(ProtectionIssue.BACKGROUND_LOCATION_OFF, allOn.copy(backgroundLocation = false).topIssue)
    }
}
