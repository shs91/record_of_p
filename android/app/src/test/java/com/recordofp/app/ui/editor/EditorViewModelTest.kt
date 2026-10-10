package com.recordofp.app.ui.editor

import androidx.lifecycle.SavedStateHandle
import com.recordofp.app.data.location.LocationProvider
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.PoiResolution
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerSpec
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
import okhttp3.ResponseBody.Companion.toResponseBody
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
        var upsertCount = 0
        var upsertError: Exception? = null
        var deleteCount = 0
        var deletedId: Long? = null
        var byIdResult: Reminder? = null
        override fun observeActive(): Flow<List<Reminder>> = emptyFlow()
        override suspend fun upsert(reminder: Reminder): Long {
            upsertCount++
            upsertError?.let { throw it }
            saved = reminder
            return 1
        }
        override suspend fun complete(id: Long) {}
        override suspend fun reactivate(id: Long) {}
        override suspend fun muteUntil(id: Long, untilEpochMs: Long) {}
        override suspend fun delete(id: Long) { deleteCount++; deletedId = id }
        override suspend fun activeTriggers() = emptyList<com.recordofp.app.domain.model.TriggerSpec>()
        override suspend fun byId(id: Long): Reminder? = byIdResult
    }

    private class FakePoi(var error: Throwable? = null) : PoiRepository {
        override suspend fun search(
            resolution: PoiResolution, query: String, center: GeoPoint, radiusM: Int, maxResults: Int,
        ): List<PoiCandidate> {
            error?.let { throw it }
            return listOf(PoiCandidate("k1", "$query 역삼점", GeoPoint(37.49, 127.03), 300.0))
        }
    }

    private val repo = FakeRepo()

    private fun vm(
        location: GeoPoint? = GeoPoint(37.5, 127.0),
        reminderId: Long = -1L,
        poi: FakePoi = FakePoi(),
    ) = EditorViewModel(
        repository = repo, poiRepository = poi,
        locationProvider = object : LocationProvider {
            override suspend fun currentOrLast() = location
        },
        savedStateHandle = SavedStateHandle(mapOf("reminderId" to reminderId)),
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
        vm.onBrandInputChange(" GS25 ")
        vm.commitBrandInput()
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
    fun `위치를 못 얻으면 원인이 NO_LOCATION으로 남는다`() = runTest {
        val vm = vm(location = null)
        vm.onPlaceQueryChange("CU")
        vm.searchPlace()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(PlaceSearchError.NO_LOCATION, vm.state.value.placeSearchError)
    }

    @Test
    fun `네트워크 예외는 NETWORK 원인으로 남는다`() = runTest {
        val vm = vm(poi = FakePoi(error = java.io.IOException("timeout")))
        vm.onPlaceQueryChange("CU")
        vm.searchPlace()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(PlaceSearchError.NETWORK, vm.state.value.placeSearchError)
    }

    @Test
    fun `HTTP 오류 응답은 SERVICE 원인으로 남는다`() = runTest {
        // F0 재발 방지: 401(ip mismatched)이 "네트워크를 확인하라"로 오인되면 진단이 산으로 간다
        val http401 = retrofit2.HttpException(
            retrofit2.Response.error<Any>(401, "".toResponseBody(null)),
        )
        val vm = vm(poi = FakePoi(error = http401))
        vm.onPlaceQueryChange("CU")
        vm.searchPlace()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(PlaceSearchError.SERVICE, vm.state.value.placeSearchError)
    }

    @Test
    fun `검색을 다시 시작하면 이전 오류가 지워진다`() = runTest {
        val vm = vm(location = null)
        vm.onPlaceQueryChange("CU")
        vm.searchPlace()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onPlaceQueryChange("GS")
        assertEquals(null, vm.state.value.placeSearchError)
    }

    @Test
    fun `SavedStateHandle에 id를 주면 기존 항목이 상태로 로드되고 저장 시 id·생성시각을 유지한다`() = runTest {
        repo.byIdResult = Reminder(
            id = 7, title = "건전지 사기", memo = "메모", createdAt = 100, updatedAt = 200,
            triggers = listOf(TriggerSpec(id = 1, reminderId = 7, type = TriggerType.CATEGORY, categoryId = "convenience")),
        )
        val vm = vm(reminderId = 7)
        dispatcher.scheduler.advanceUntilIdle()

        val loaded = vm.state.value
        assertEquals(7L, loaded.editingId)
        assertEquals("건전지 사기", loaded.title)
        assertEquals("메모", loaded.memo)
        assertEquals(setOf("convenience"), loaded.selectedCategoryIds)

        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(7L, repo.saved?.id) // 새 id로 새 레코드가 만들어지면 안 된다
        assertEquals(100L, repo.saved?.createdAt) // 원래 생성 시각 보존
    }

    @Test
    fun `delete는 repository의 delete를 호출하고 saved가 선다`() = runTest {
        repo.byIdResult = Reminder(id = 7, title = "건전지 사기", createdAt = 100, updatedAt = 200)
        val vm = vm(reminderId = 7)
        dispatcher.scheduler.advanceUntilIdle()

        vm.delete()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(7L, repo.deletedId)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `입력만 하고 확정하지 않은 브랜드도 저장된다`() = runTest {
        val vm = vm()
        vm.onTitleChange("휴지")
        vm.onBrandInputChange(" 이마트24 ")
        assertTrue(vm.state.value.canSave) // 입력 중인 브랜드도 트리거로 센다
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("이마트24"), repo.saved!!.triggers.map { it.brandKeyword })
    }

    @Test
    fun `공백만 입력한 브랜드는 트리거로 세지 않는다`() = runTest {
        val vm = vm()
        vm.onTitleChange("휴지")
        vm.onBrandInputChange("   ")
        assertTrue(!vm.state.value.canSave)
        vm.commitBrandInput()
        assertEquals(emptyList<String>(), vm.state.value.brandKeywords)
        assertEquals("", vm.state.value.brandInput)
    }

    @Test
    fun `저장 중에 다시 눌러도 한 번만 저장된다`() = runTest {
        val vm = vm()
        vm.onTitleChange("휴지")
        vm.toggleCategory("convenience")
        vm.save()
        vm.save() // 연타
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.upsertCount)
    }

    @Test
    fun `저장이 실패하면 앱을 죽이지 않고 오류를 표시하며 다시 저장할 수 있다`() = runTest {
        repo.upsertError = IllegalStateException("disk full")
        val vm = vm()
        vm.onTitleChange("휴지")
        vm.toggleCategory("convenience")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s.saveFailed)
        assertTrue(!s.saved)
        assertTrue(s.canSave)
    }

    @Test
    fun `삭제 중에 다시 눌러도 한 번만 삭제된다`() = runTest {
        repo.byIdResult = Reminder(id = 7, title = "건전지 사기", createdAt = 100, updatedAt = 200)
        val vm = vm(reminderId = 7)
        dispatcher.scheduler.advanceUntilIdle()
        vm.delete()
        vm.delete()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.deleteCount)
    }
}
