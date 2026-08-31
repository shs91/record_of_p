package com.recordofp.app.ui.onboarding

import com.recordofp.app.data.repo.NotificationPolicySettings
import com.recordofp.app.data.repo.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeStore : SettingsStore {
        val flag = MutableStateFlow(false)
        override val onboardingDone: Flow<Boolean> = flag
        override suspend fun setOnboardingDone() { flag.value = true }
        override val policy: Flow<NotificationPolicySettings> = MutableStateFlow(NotificationPolicySettings())
        override suspend fun updatePolicy(transform: (NotificationPolicySettings) -> NotificationPolicySettings) {}
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `finish는 완료 플래그를 영속화한다`() = runTest {
        val store = FakeStore()
        val vm = OnboardingViewModel(store)
        vm.finish()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, store.flag.value)
    }
}
