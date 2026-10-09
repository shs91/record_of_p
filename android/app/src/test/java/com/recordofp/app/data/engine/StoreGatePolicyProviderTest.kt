package com.recordofp.app.data.engine

import com.recordofp.app.data.repo.NotificationPolicySettings
import com.recordofp.app.data.repo.SettingsStore
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeSettings(initial: NotificationPolicySettings) : SettingsStore {
    val flow = MutableStateFlow(initial)
    override val onboardingDone: Flow<Boolean> = MutableStateFlow(true)
    override suspend fun setOnboardingDone() {}
    override val policy: Flow<NotificationPolicySettings> = flow
    override suspend fun updatePolicy(transform: (NotificationPolicySettings) -> NotificationPolicySettings) {
        flow.value = transform(flow.value)
    }
}

class StoreGatePolicyProviderTest {

    @Test
    fun `설정값이 게이트 정책으로 매핑된다`() = runTest {
        val provider = StoreGatePolicyProvider(FakeSettings(NotificationPolicySettings(cooldownHours = 12, dailyCapTotal = 20)))
        val policy = provider.policy()
        assertEquals(Duration.ofHours(12), policy.cooldownPerItem)
        assertEquals(20, policy.dailyCapTotal)
        assertEquals(22 * 60, policy.quietStartMinute)
    }

    @Test
    fun `무제한(0)은 사실상 무한 상한으로, 방해금지 꺼짐은 시작==끝으로 매핑된다`() = runTest {
        val provider = StoreGatePolicyProvider(
            FakeSettings(NotificationPolicySettings(dailyCapTotal = 0, quietEnabled = false)),
        )
        val policy = provider.policy()
        assertEquals(Int.MAX_VALUE, policy.dailyCapTotal)
        assertEquals(policy.quietStartMinute, policy.quietEndMinute) // NotificationGate: 시작==끝 → 비활성
    }
}
