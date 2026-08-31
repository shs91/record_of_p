package com.recordofp.app.data.engine

import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import com.recordofp.app.data.db.GeofenceRegDao
import com.recordofp.app.data.db.GeofenceRegEntity
import com.recordofp.app.data.db.NotificationLogDao
import com.recordofp.app.data.db.NotificationLogEntity
import com.recordofp.app.data.db.RegTriggerEntity
import com.recordofp.app.data.db.ReminderDao
import com.recordofp.app.data.db.ReminderEntity
import com.recordofp.app.data.db.TriggerSpecDao
import com.recordofp.app.data.db.TriggerSpecEntity
import com.recordofp.app.domain.engine.NotificationGate
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// ── 페이크 (필요 메서드만 실동작, 나머지는 최소 구현)
private class FakeRegs : GeofenceRegDao {
    val regs = mutableMapOf<String, GeofenceRegEntity>()
    val links = mutableListOf<RegTriggerEntity>()
    override suspend fun all() = regs.values.toList()
    override suspend fun byId(geofenceId: String) = regs[geofenceId]
    override suspend fun triggerIdsFor(geofenceId: String) =
        links.filter { it.geofenceId == geofenceId }.map { it.triggerId }
    override suspend fun insertRegs(regs: List<GeofenceRegEntity>) = regs.forEach { this.regs[it.geofenceId] = it }
    override suspend fun insertRegTriggers(links: List<RegTriggerEntity>) { this.links += links }
    override suspend fun deleteRegs(ids: List<String>) = ids.forEach { regs.remove(it) }
    override suspend fun deleteRegTriggers(ids: List<String>) { links.removeAll { it.geofenceId in ids } }
    override suspend fun applyReseed(removeIds: List<String>, addRegs: List<GeofenceRegEntity>, linkFenceIds: List<String>, links: List<RegTriggerEntity>) {}
}
private class FakeSpecs(val specs: Map<Long, TriggerSpecEntity>) : TriggerSpecDao {
    override suspend fun byReminder(reminderId: Long) = specs.values.filter { it.reminderId == reminderId }
    override suspend fun allActive() = specs.values.toList()
    override suspend fun byIds(ids: List<Long>) = ids.mapNotNull { specs[it] }
    override suspend fun upsertAll(entities: List<TriggerSpecEntity>) {}
    override suspend fun deleteByReminder(reminderId: Long) {}
}
private class FakeReminderDao(val rows: Map<Long, ReminderEntity>) : ReminderDao {
    override fun observeActive(): Flow<List<ReminderEntity>> = emptyFlow()
    override suspend fun byId(id: Long) = rows[id]
    override suspend fun upsert(entity: ReminderEntity) = 0L
    override suspend fun setStatus(id: Long, status: String, completedAt: Long?, updatedAt: Long) {}
    override suspend fun setSnooze(id: Long, until: Long?, updatedAt: Long) {}
    override suspend fun delete(id: Long) {}
}
private class FakeNotifLog : NotificationLogDao {
    val rows = mutableListOf<NotificationLogEntity>()
    override suspend fun insert(entity: NotificationLogEntity) { rows += entity }
    override suspend fun lastShownForItem(reminderId: Long) =
        rows.filter { it.reminderId == reminderId }.maxOfOrNull { it.shownAt }
    override suspend fun lastShownForItemAtPoi(reminderId: Long, poiKakaoId: String) =
        rows.filter { it.reminderId == reminderId && it.poiKakaoId == poiKakaoId }.maxOfOrNull { it.shownAt }
    override suspend fun countForItemSince(reminderId: Long, since: Long) =
        rows.count { it.reminderId == reminderId && it.shownAt >= since }
    override suspend fun countTotalSince(since: Long) = rows.count { it.shownAt >= since }
}
private class FakeRuns : EngineRunLogDao {
    val entries = mutableListOf<EngineRunLogEntity>()
    override suspend fun insert(entity: EngineRunLogEntity) { entries += entity }
    override fun observeRecent(limit: Int): Flow<List<EngineRunLogEntity>> = emptyFlow()
    override suspend fun pruneOlderThan(before: Long) {}
}

class GeofenceEventHandlerTest {

    private val zone = ZoneId.of("Asia/Seoul")
    // 2026-08-31 낮 12시(KST) — 방해금지 밖
    private val noon = Instant.parse("2026-08-31T03:00:00Z")

    private fun reminder(id: Long, status: String = "ACTIVE") = ReminderEntity(
        id = id, title = "할일$id", memo = null, status = status, snoozeUntil = null,
        createdAt = 0, updatedAt = 0, completedAt = null,
    )
    private fun poiReg(fenceId: String, poiId: String, name: String) = GeofenceRegEntity(
        geofenceId = fenceId, kind = "POI", lat = 37.5, lng = 127.0, radiusM = 120f,
        poiName = name, poiKakaoId = poiId, matchKey = "cat:convenience", reseedBatchId = "b", registeredAt = 0,
    )
    private fun spec(id: Long, reminderId: Long) = TriggerSpecEntity(
        id = id, reminderId = reminderId, type = "CATEGORY", categoryId = "convenience",
        brandKeyword = null, placeName = null, placeKakaoId = null, placeLat = null, placeLng = null,
    )

    private fun build(
        regs: FakeRegs, specs: FakeSpecs, reminders: FakeReminderDao,
        notifLog: FakeNotifLog = FakeNotifLog(), runs: FakeRuns = FakeRuns(),
    ) = GeofenceEventHandler(
        regDao = regs, triggerSpecDao = specs, reminderDao = reminders,
        notificationLogDao = notifLog, runLogDao = runs,
        policyProvider = object : GatePolicyProvider {
            override suspend fun policy() = NotificationGate.Policy()
        },
        gate = NotificationGate(zone), zone = zone,
        clock = Clock.fixed(noon, zone),
    )

    @Test
    fun `통과한 리마인더는 POI 그룹으로 묶이고 NotificationLog가 기록된다`() = runTest {
        val regs = FakeRegs().apply {
            regs["poi:100"] = poiReg("poi:100", "100", "CU 역삼점")
            links += listOf(RegTriggerEntity("poi:100", 11), RegTriggerEntity("poi:100", 12))
        }
        val notifLog = FakeNotifLog()
        val handler = build(
            regs,
            FakeSpecs(mapOf(11L to spec(11, 1), 12L to spec(12, 2))),
            FakeReminderDao(mapOf(1L to reminder(1), 2L to reminder(2))),
            notifLog = notifLog,
        )
        val out = handler.onFenceEvent(listOf("poi:100"))
        val group = out.groups.single()
        assertEquals("CU 역삼점", group.poiName)
        assertEquals(listOf(1L, 2L), group.reminders.map { it.id })
        assertEquals(2, notifLog.rows.size) // 리마인더별 1행 (쿨다운 원본)
    }

    @Test
    fun `차단된 리마인더는 그룹에서 빠지고 사유가 EngineRunLog에 남는다`() = runTest {
        val regs = FakeRegs().apply {
            regs["poi:100"] = poiReg("poi:100", "100", "CU 역삼점")
            links += RegTriggerEntity("poi:100", 11)
        }
        val notifLog = FakeNotifLog().apply {
            rows += NotificationLogEntity(reminderId = 1, poiKakaoId = "999", shownAt = noon.toEpochMilli() - 60_000)
        } // 1분 전 알림 → 항목 쿨다운 4h 차단
        val runs = FakeRuns()
        val handler = build(regs, FakeSpecs(mapOf(11L to spec(11, 1))), FakeReminderDao(mapOf(1L to reminder(1))), notifLog, runs)
        val out = handler.onFenceEvent(listOf("poi:100"))
        assertTrue(out.groups.isEmpty())
        assertTrue(runs.entries.any { it.result == "BLOCK_ITEM_COOLDOWN" })
    }

    @Test
    fun `등록에 없는 stale 이벤트는 무시된다`() = runTest {
        val handler = build(FakeRegs(), FakeSpecs(emptyMap()), FakeReminderDao(emptyMap()))
        val out = handler.onFenceEvent(listOf("poi:ghost"))
        assertTrue(out.groups.isEmpty())
        assertTrue(!out.sentinelExited)
    }

    @Test
    fun `센티널 이탈은 플래그로 보고된다`() = runTest {
        val regs = FakeRegs().apply {
            regs["sentinel"] = GeofenceRegEntity("sentinel", "SENTINEL", 37.5, 127.0, 1000f, null, null, null, "b", 0)
        }
        val out = build(regs, FakeSpecs(emptyMap()), FakeReminderDao(emptyMap())).onFenceEvent(listOf("sentinel"))
        assertTrue(out.sentinelExited)
    }
}
