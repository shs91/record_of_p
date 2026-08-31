package com.recordofp.app.data.engine

import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import com.recordofp.app.data.db.GeofenceRegDao
import com.recordofp.app.data.db.GeofenceRegEntity
import com.recordofp.app.data.db.RegTriggerEntity
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.DiffCalculator
import com.recordofp.app.domain.engine.FenceDiff
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.domain.engine.ReseedGovernor
import com.recordofp.app.domain.engine.ReseedPlanner
import com.recordofp.app.domain.engine.TriggerResolver
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.PoiResolution
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeReminders(var triggers: List<TriggerSpec>) : ReminderRepository {
    override fun observeActive(): Flow<List<Reminder>> = emptyFlow()
    override suspend fun upsert(reminder: Reminder) = 0L
    override suspend fun complete(id: Long) {}
    override suspend fun muteUntil(id: Long, untilEpochMs: Long) {}
    override suspend fun delete(id: Long) {}
    override suspend fun activeTriggers() = triggers
}

private class FakePoi(
    var byQuery: Map<String, List<PoiCandidate>> = emptyMap(),
    var throwOn: String? = null,
) : PoiRepository {
    override suspend fun search(
        resolution: PoiResolution, query: String, center: GeoPoint, radiusM: Int, maxResults: Int,
    ): List<PoiCandidate> {
        if (query == throwOn) throw RuntimeException("poi down")
        return byQuery[query].orEmpty()
    }
}

private class FakeRegDao : GeofenceRegDao {
    val regs = mutableMapOf<String, GeofenceRegEntity>()
    val links = mutableListOf<RegTriggerEntity>()
    override suspend fun all() = regs.values.toList()
    override suspend fun byId(geofenceId: String) = regs[geofenceId]
    override suspend fun triggerIdsFor(geofenceId: String) =
        links.filter { it.geofenceId == geofenceId }.map { it.triggerId }
    override suspend fun insertRegs(regs: List<GeofenceRegEntity>) =
        regs.forEach { this.regs[it.geofenceId] = it }
    override suspend fun insertRegTriggers(links: List<RegTriggerEntity>) { this.links += links }
    override suspend fun deleteRegs(ids: List<String>) = ids.forEach { regs.remove(it) }
    override suspend fun deleteRegTriggers(ids: List<String>) {
        links.removeAll { it.geofenceId in ids }
    }
    override suspend fun applyReseed(
        removeIds: List<String>, addRegs: List<GeofenceRegEntity>,
        linkFenceIds: List<String>, links: List<RegTriggerEntity>,
    ) { // Room @Transaction 기본 구현과 동일 순서
        deleteRegTriggers(removeIds + linkFenceIds); deleteRegs(removeIds)
        insertRegs(addRegs); insertRegTriggers(links)
    }
}

private class FakeRunLog : EngineRunLogDao {
    val entries = mutableListOf<EngineRunLogEntity>()
    override suspend fun insert(entity: EngineRunLogEntity) { entries += entity }
    override fun observeRecent(limit: Int): Flow<List<EngineRunLogEntity>> = emptyFlow()
    override suspend fun pruneOlderThan(before: Long) {}
}

private class FakeApplier : FenceApplier {
    val applied = mutableListOf<FenceDiff>()
    override suspend fun apply(diff: FenceDiff) { applied += diff }
}

class ReseedServiceTest {

    private val here = GeoPoint(37.5, 127.0)
    private val convenience = TriggerSpec(id = 10, reminderId = 1, type = TriggerType.CATEGORY, categoryId = "convenience")
    private val place = TriggerSpec(
        id = 20, reminderId = 2, type = TriggerType.PLACE,
        placeName = "회사 우체국", placePoint = GeoPoint(37.51, 127.0),
    )
    private fun poi(id: String, lat: Double) =
        PoiCandidate(id, "CU $id", GeoPoint(lat, 127.0), 100.0)

    private fun build(
        reminders: FakeReminders,
        poiRepo: FakePoi,
        regDao: FakeRegDao = FakeRegDao(),
        runLog: FakeRunLog = FakeRunLog(),
        applier: FakeApplier = FakeApplier(),
        stateStore: FakeStateStore = FakeStateStore(),
    ) = ReseedService(
        reminderRepository = reminders, poiRepository = poiRepo, regDao = regDao,
        runLogDao = runLog, applier = applier, stateStore = stateStore,
        governor = ReseedGovernor(), resolver = TriggerResolver(),
        planner = ReseedPlanner(), differ = DiffCalculator(),
        clock = Clock.fixed(Instant.ofEpochMilli(1_000_000_000_000), ZoneOffset.UTC),
    )

    @Test
    fun `해피 패스 - 펜스 적용, 미러·링크 갱신, 스탬프 기록`() = runTest {
        val regDao = FakeRegDao(); val applier = FakeApplier(); val state = FakeStateStore()
        val service = build(
            FakeReminders(listOf(convenience, place)),
            FakePoi(byQuery = mapOf("CS2" to listOf(poi("1", 37.501), poi("2", 37.503)))),
            regDao = regDao, applier = applier, stateStore = state,
        )
        val result = service.reseed(ReseedCause.BOOT, here)
        assertEquals(ReseedResult.APPLIED, result)
        // 센티널 1 + PLACE 1 + POI 2
        assertEquals(4, regDao.regs.size)
        assertTrue(regDao.regs.containsKey("sentinel"))
        // 링크: POI 펜스 2개는 트리거 10에, PLACE 펜스는 트리거 20에
        assertEquals(setOf(10L), regDao.links.filter { it.geofenceId == "poi:1" }.map { it.triggerId }.toSet())
        assertEquals(setOf(20L), regDao.links.filter { it.geofenceId == "place:20" }.map { it.triggerId }.toSet())
        assertEquals(1, applier.applied.size)
        assertEquals(1_000_000_000_000, state.stamp?.atMs)
    }

    @Test
    fun `POI 조회 실패 시 기존 등록을 유지하고 FAILED를 반환한다`() = runTest {
        val regDao = FakeRegDao().apply {
            regs["poi:old"] = GeofenceRegEntity("poi:old", "POI", 37.5, 127.0, 120f, "CU", "old", "cat:convenience", "b0", 0)
        }
        val applier = FakeApplier()
        val service = build(
            FakeReminders(listOf(convenience)), FakePoi(throwOn = "CS2"),
            regDao = regDao, applier = applier,
        )
        assertEquals(ReseedResult.FAILED, service.reseed(ReseedCause.SENTINEL_EXIT, here))
        assertTrue(applier.applied.isEmpty())          // OS 호출 없음
        assertTrue(regDao.regs.containsKey("poi:old")) // 미러 보존 (§6.4)
    }

    @Test
    fun `디바운스에 걸리면 아무것도 하지 않는다`() = runTest {
        val state = FakeStateStore().apply {
            stamp = com.recordofp.app.domain.engine.ReseedStamp(1_000_000_000_000 - 60_000, here) // 1분 전
        }
        val applier = FakeApplier()
        val service = build(FakeReminders(listOf(convenience)), FakePoi(), stateStore = state, applier = applier)
        assertEquals(ReseedResult.SKIPPED_DEBOUNCE, service.reseed(ReseedCause.PERIODIC, here))
        assertTrue(applier.applied.isEmpty())
    }

    @Test
    fun `활성 트리거가 없으면 등록 전부(센티널 포함)를 걷어낸다`() = runTest {
        val regDao = FakeRegDao().apply {
            regs["sentinel"] = GeofenceRegEntity("sentinel", "SENTINEL", 37.5, 127.0, 1000f, null, null, null, "b0", 0)
            regs["poi:1"] = GeofenceRegEntity("poi:1", "POI", 37.501, 127.0, 120f, "CU", "1", "cat:convenience", "b0", 0)
        }
        val applier = FakeApplier()
        val service = build(FakeReminders(emptyList()), FakePoi(), regDao = regDao, applier = applier)
        assertEquals(ReseedResult.CLEARED_NO_TRIGGERS, service.reseed(ReseedCause.ITEM_CHANGE, here))
        assertTrue(regDao.regs.isEmpty())
        assertEquals(setOf("sentinel", "poi:1"), applier.applied.single().removeIds.toSet())
    }

    @Test
    fun `BOOT은 미러와 계획이 동일해도 전량 재등록한다`() = runTest {
        val regDao = FakeRegDao(); val applier = FakeApplier(); val state = FakeStateStore()
        val service = build(
            FakeReminders(listOf(convenience, place)),
            FakePoi(byQuery = mapOf("CS2" to listOf(poi("1", 37.501), poi("2", 37.503)))),
            regDao = regDao, applier = applier, stateStore = state,
        )
        service.reseed(ReseedCause.BOOT, here) // 1회차 — 미러를 계획과 비트일치하게 채운다
        applier.applied.clear() // 재부팅 재현: OS 쪽 흔적만 지운다 (미러는 재부팅에도 살아남는다)

        val result = service.reseed(ReseedCause.BOOT, here)

        assertEquals(ReseedResult.APPLIED, result)
        // 미러·diff가 완전히 일치해도(=diff.add가 비어도) OS 펜스는 죽어 있으니 전량 재등록해야 한다
        // 센티널 1 + PLACE 1 + POI 2 = 4
        assertEquals(4, applier.applied.single().add.size)
    }

    @Test
    fun `standDown은 OS와 미러 등록을 전부 걷어낸다`() = runTest {
        val regDao = FakeRegDao().apply {
            regs["sentinel"] = GeofenceRegEntity("sentinel", "SENTINEL", 37.5, 127.0, 1000f, null, null, null, "b0", 0)
            regs["poi:1"] = GeofenceRegEntity("poi:1", "POI", 37.501, 127.0, 120f, "CU", "1", "cat:convenience", "b0", 0)
        }
        val applier = FakeApplier()
        val service = build(FakeReminders(listOf(convenience)), FakePoi(), regDao = regDao, applier = applier)

        val result = service.standDown(ReseedCause.PERIODIC)

        assertEquals(ReseedResult.STOOD_DOWN, result)
        assertEquals(setOf("sentinel", "poi:1"), applier.applied.single().removeIds.toSet())
        assertTrue(applier.applied.single().add.isEmpty())
        assertTrue(regDao.regs.isEmpty())
    }

    @Test
    fun `APP_OPEN 디바운스 스킵은 로그를 남기지 않는다`() = runTest {
        val state = FakeStateStore().apply {
            stamp = com.recordofp.app.domain.engine.ReseedStamp(1_000_000_000_000 - 60_000, here) // 1분 전 — 디바운스 구간
        }
        val runLog = FakeRunLog()
        val service = build(FakeReminders(listOf(convenience)), FakePoi(), stateStore = state, runLog = runLog)
        assertEquals(ReseedResult.SKIPPED_DEBOUNCE, service.reseed(ReseedCause.APP_OPEN, here))
        assertTrue(runLog.entries.isEmpty()) // 매 앱 진입마다 남는 정상 소음 — 스팸 방지 (M3)
    }
}

class FakeStateStore : ReseedStateStore {
    var stamp: com.recordofp.app.domain.engine.ReseedStamp? = null
    override suspend fun lastReseed() = stamp
    override suspend fun recordReseed(stamp: com.recordofp.app.domain.engine.ReseedStamp) { this.stamp = stamp }
}
