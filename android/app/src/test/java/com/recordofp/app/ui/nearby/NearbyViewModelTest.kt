package com.recordofp.app.ui.nearby

import com.recordofp.app.data.location.LocationProvider
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.engine.TriggerResolver
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.PoiResolution
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import com.recordofp.app.ui.common.TriggerVisual
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
class NearbyViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeRepo(val triggers: List<TriggerSpec>) : ReminderRepository {
        override fun observeActive(): Flow<List<Reminder>> = emptyFlow()
        override suspend fun upsert(reminder: Reminder) = 0L
        override suspend fun complete(id: Long) {}
        override suspend fun reactivate(id: Long) {}
        override suspend fun muteUntil(id: Long, untilEpochMs: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun activeTriggers() = triggers
        override suspend fun byId(id: Long): Reminder? = null
    }

    private class FakePoi : PoiRepository {
        override suspend fun search(
            resolution: PoiResolution, query: String, center: GeoPoint, radiusM: Int, maxResults: Int,
        ) = listOf(PoiCandidate("p-$query", "$query 지점", GeoPoint(37.501, 127.0), 150.0))
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(triggers: List<TriggerSpec>, location: GeoPoint?) = NearbyViewModel(
        repository = FakeRepo(triggers), poiRepository = FakePoi(), resolver = TriggerResolver(),
        locationProvider = object : LocationProvider { override suspend fun currentOrLast() = location },
    )

    @Test
    fun `활성 트리거별로 주변 POI 그룹을 만든다 - PLACE는 거리 계산으로 포함`() = runTest {
        val vm = vm(
            listOf(
                TriggerSpec(id = 1, reminderId = 1, type = TriggerType.CATEGORY, categoryId = "convenience"),
                TriggerSpec(
                    id = 2, reminderId = 1, type = TriggerType.PLACE,
                    placeName = "회사 우체국", placePoint = GeoPoint(37.509, 127.0), // ~1km
                ),
            ),
            location = GeoPoint(37.5, 127.0),
        )
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.groups.size)
        val cat = state.groups.first { it.matchKey == "cat:convenience" }
        assertEquals("CS2 지점", cat.pois.single().name)
        val place = state.groups.first { it.matchKey == "place:2" }
        assertEquals("회사 우체국", place.pois.single().name)
        assertTrue(place.pois.single().distanceM in 900.0..1100.0)
    }

    @Test
    fun `위치를 못 얻으면 locationUnavailable이 선다`() = runTest {
        val vm = vm(emptyList(), location = null)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.locationUnavailable)
    }

    @Test
    fun `그룹은 머리에 그릴 트리거를 함께 낸다 - 브랜드는 입력한 대소문자 그대로`() = runTest {
        val vm = vm(
            listOf(
                TriggerSpec(id = 1, reminderId = 1, type = TriggerType.BRAND, brandKeyword = "GS25"),
                TriggerSpec(id = 2, reminderId = 1, type = TriggerType.CATEGORY, categoryId = "daiso"),
                TriggerSpec(id = 3, reminderId = 1, type = TriggerType.CATEGORY, categoryId = "pharmacy"),
                TriggerSpec(
                    id = 4, reminderId = 1, type = TriggerType.PLACE,
                    placeName = "회사 우체국", placePoint = GeoPoint(37.509, 127.0),
                ),
            ),
            location = GeoPoint(37.5, 127.0),
        )
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        val visuals = vm.state.value.groups.associate { it.matchKey to it.visual }
        assertEquals(TriggerVisual.BrandKeyword("GS25"), visuals["brand:gs25"])
        assertEquals(TriggerVisual.BrandPreset("daiso"), visuals["cat:daiso"])
        assertEquals(TriggerVisual.Category("pharmacy"), visuals["cat:pharmacy"])
        assertEquals(TriggerVisual.Place("회사 우체국"), visuals["place:4"])
    }
}
