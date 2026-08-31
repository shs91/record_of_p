package com.recordofp.app.ui.editor

import com.recordofp.app.data.location.LocationProvider
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.PoiResolution
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    private class FakeRepo : ReminderRepository {
        var saved: Reminder? = null
        override fun observeActive(): Flow<List<Reminder>> = emptyFlow()
        override suspend fun upsert(reminder: Reminder): Long { saved = reminder; return 1 }
        override suspend fun complete(id: Long) {}
        override suspend fun muteUntil(id: Long, untilEpochMs: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun activeTriggers() = emptyList<com.recordofp.app.domain.model.TriggerSpec>()
    }

    private class FakePoi : PoiRepository {
        override suspend fun search(
            resolution: PoiResolution, query: String, center: GeoPoint, radiusM: Int, maxResults: Int,
        ) = listOf(PoiCandidate("k1", "$query 역삼점", GeoPoint(37.49, 127.03), 300.0))
    }

    private val repo = FakeRepo()

    private fun vm(location: GeoPoint? = GeoPoint(37.5, 127.0)) = EditorViewModel(
        repository = repo, poiRepository = FakePoi(),
        locationProvider = object : LocationProvider {
            override suspend fun currentOrLast() = location
        },
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `제목과 트리거가 있어야 저장 가능하다`() = runTest {
        val vm = vm()
        assertTrue(!vm.state.value.canSave)
        vm.onTitleChange("건전지 사기")
        assertTrue(!vm.state.value.canSave)
        vm.toggleCategory("convenience")
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `저장하면 선택이 트리거 스펙으로 매핑된다`() = runTest {
        val vm = vm()
        vm.onTitleChange("휴지")
        vm.toggleCategory("convenience")
        vm.toggleCategory("mart")
        vm.addBrand(" GS25 ")
        vm.pickPlace(PickedPlace("우리집 앞 CU", "k9", GeoPoint(37.51, 127.0)))
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        val saved = repo.saved!!
        assertEquals("휴지", saved.title)
        val byType = saved.triggers.groupBy { it.type }
        assertEquals(setOf("convenience", "mart"), byType.getValue(TriggerType.CATEGORY).map { it.categoryId }.toSet())
        assertEquals(listOf("GS25"), byType.getValue(TriggerType.BRAND).map { it.brandKeyword })
        assertEquals("k9", byType.getValue(TriggerType.PLACE).single().placeKakaoId)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `장소 검색은 키워드 결과를 상태에 싣는다`() = runTest {
        val vm = vm()
        vm.onPlaceQueryChange("CU")
        vm.searchPlace()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("CU 역삼점", vm.state.value.placeResults.single().name)
    }

    @Test
    fun `위치를 못 얻으면 검색 실패 플래그가 선다`() = runTest {
        val vm = vm(location = null)
        vm.onPlaceQueryChange("CU")
        vm.searchPlace()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.placeSearchFailed)
    }
}
