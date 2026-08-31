package com.recordofp.app.ui.home

import app.cash.turbine.test
import com.recordofp.app.data.repo.ReminderRepository
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
        override fun observeActive(): Flow<List<Reminder>> = flow
        override suspend fun upsert(reminder: Reminder) = 0L
        override suspend fun complete(id: Long) { completed += id }
        override suspend fun muteUntil(id: Long, untilEpochMs: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun activeTriggers() = emptyList<com.recordofp.app.domain.model.TriggerSpec>()
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `목록을 구독하고 완료를 저장소에 위임한다`() = runTest {
        val repo = FakeRepo()
        val vm = HomeViewModel(repo)
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
