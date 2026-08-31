package com.recordofp.app.ui.settings

import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-08-31T03:00:00Z")

    private class FakeRuns : EngineRunLogDao {
        val flow = MutableStateFlow<List<EngineRunLogEntity>>(emptyList())
        var prunedBefore: Long? = null
        override suspend fun insert(entity: EngineRunLogEntity) {}
        override fun observeRecent(limit: Int): Flow<List<EngineRunLogEntity>> = flow
        override suspend fun pruneOlderThan(before: Long) { prunedBefore = before }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `시작 시 14일 이전 로그를 정리한다`() = runTest {
        val dao = FakeRuns()
        DiagnosticsViewModel(dao, Clock.fixed(now, ZoneId.of("Asia/Seoul")))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(now.toEpochMilli() - 14L * 24 * 3600 * 1000, dao.prunedBefore)
    }

    @Test
    fun `내보내기 텍스트는 시각·원인·결과를 담는다`() = runTest {
        val dao = FakeRuns()
        val vm = DiagnosticsViewModel(dao, Clock.fixed(now, ZoneId.of("Asia/Seoul")))
        dao.flow.value = listOf(
            EngineRunLogEntity(at = now.toEpochMilli(), cause = "SENTINEL_EXIT", result = "APPLIED", registeredCount = 12, note = null),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val text = vm.buildExport()
        assertTrue(text.contains("SENTINEL_EXIT"))
        assertTrue(text.contains("APPLIED"))
        assertTrue(text.contains("12"))
    }
}
