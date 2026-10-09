package com.recordofp.app.data.engine

import com.recordofp.app.data.repo.ReseedRequester
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeRequester : ReseedRequester {
    var itemChanges = 0
    var opportunistics = 0
    override fun requestItemChange() { itemChanges++ }
    override fun requestOpportunistic() { opportunistics++ }
}

class ProtectionReseedTriggerTest {

    @Test
    fun `보호 미완에서 완전으로 전이하면 기회적 재배치를 요청한다`() {
        val requester = FakeRequester()
        val trigger = ProtectionReseedTrigger(requester)
        trigger.onSnapshot(fullyProtected = false)
        trigger.onSnapshot(fullyProtected = true)
        assertEquals(1, requester.opportunistics)
        assertEquals(0, requester.itemChanges) // 강한 큐를 건드리면 안 된다
    }

    @Test
    fun `앱 시작부터 완전 보호면 요청하지 않는다`() {
        // 이 경우는 onCreate의 APP_OPEN이 이미 처리했다 — 중복 요청 금지
        val requester = FakeRequester()
        val trigger = ProtectionReseedTrigger(requester)
        trigger.onSnapshot(fullyProtected = true)
        trigger.onSnapshot(fullyProtected = true)
        assertEquals(0, requester.opportunistics)
    }

    @Test
    fun `미보호 유지 보고는 요청하지 않는다`() {
        val requester = FakeRequester()
        val trigger = ProtectionReseedTrigger(requester)
        trigger.onSnapshot(fullyProtected = false)
        trigger.onSnapshot(fullyProtected = false)
        assertEquals(0, requester.opportunistics)
    }

    @Test
    fun `완전에서 미보호로 떨어졌다가 복구되면 다시 요청한다`() {
        val requester = FakeRequester()
        val trigger = ProtectionReseedTrigger(requester)
        trigger.onSnapshot(fullyProtected = true) // 초기 — 요청 없음
        trigger.onSnapshot(fullyProtected = false) // 권한 회수
        trigger.onSnapshot(fullyProtected = true) // 복구 — 요청
        assertEquals(1, requester.opportunistics)
    }
}
