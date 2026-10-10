package com.recordofp.app.ui.home

import app.cash.turbine.test
import com.recordofp.app.data.engine.ProtectionReseedTrigger
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.data.repo.ReseedRequester
import com.recordofp.app.domain.model.Reminder
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
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeRepo : ReminderRepository {
        val flow = MutableStateFlow<List<Reminder>>(emptyList())
        val completed = mutableListOf<Long>()
        val reactivated = mutableListOf<Long>()
        override fun observeActive(): Flow<List<Reminder>> = flow
        override suspend fun upsert(reminder: Reminder) = 0L
        override suspend fun complete(id: Long) { completed += id }
        override suspend fun reactivate(id: Long) { reactivated += id }
        override suspend fun muteUntil(id: Long, untilEpochMs: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun activeTriggers() = emptyList<com.recordofp.app.domain.model.TriggerSpec>()
        override suspend fun byId(id: Long): Reminder? = null
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    /** 전이 로직 자체는 ProtectionReseedTriggerTest가 검증 — 여기선 no-op 의존성만 채운다 */
    private fun noopTrigger() = ProtectionReseedTrigger(
        object : ReseedRequester {
            override fun requestItemChange() {}
            override fun requestOpportunistic() {}
        },
    )

    @Test
    fun `완료 실행 취소는 저장소의 reactivate에 위임한다`() = runTest {
        val repo = FakeRepo()
        val vm = HomeViewModel(repo, noopTrigger())
        vm.complete(7)
        vm.reactivate(7)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(7L), repo.completed)
        assertEquals(listOf(7L), repo.reactivated)
    }

    @Test
    fun `목록을 구독하고 완료를 저장소에 위임한다`() = runTest {
        val repo = FakeRepo()
        val vm = HomeViewModel(repo, noopTrigger())
        vm.items.test {
            assertEquals(0, awaitItem().size)
            repo.flow.value = listOf(Reminder(id = 1, title = "휴지", createdAt = 0, updatedAt = 0))
            assertEquals("휴지", awaitItem().single().title)
        }
        vm.complete(1)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(1L), repo.completed)
    }
}
