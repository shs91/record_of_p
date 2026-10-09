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
import com.recordofp.app.data.db.ReminderWithTriggers
import com.recordofp.app.data.db.TriggerSpecDao
import com.recordofp.app.data.db.TriggerSpecEntity
import com.recordofp.app.domain.engine.NotificationGate
import com.recordofp.app.domain.engine.SENTINEL_FENCE_KEY
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.distanceMeters
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    override fun observeActiveWithTriggers(): Flow<List<ReminderWithTriggers>> = emptyFlow()
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
    private fun poiReg(
        fenceId: String, poiId: String, name: String, lat: Double = 37.5, lng: Double = 127.0,
    ) = GeofenceRegEntity(
        geofenceId = fenceId, kind = "POI", lat = lat, lng = lng, radiusM = 120f,
        poiName = name, poiKakaoId = poiId, matchKey = "cat:convenience", reseedBatchId = "b", registeredAt = 0,
    )
    private fun spec(id: Long, reminderId: Long) = TriggerSpecEntity(
        id = id, reminderId = reminderId, type = "CATEGORY", categoryId = "convenience",
        brandKeyword = null, placeName = null, placeKakaoId = null, placeLat = null, placeLng = null,
    )
    private fun placeReg(fenceId: String, kakaoId: String?, name: String) = GeofenceRegEntity(
        geofenceId = fenceId, kind = "PLACE", lat = 37.5, lng = 127.0, radiusM = 150f,
        poiName = name, poiKakaoId = kakaoId, matchKey = fenceId, reseedBatchId = "b", registeredAt = 0,
    )
    private fun placeSpec(id: Long, reminderId: Long, kakaoId: String?) = TriggerSpecEntity(
        id = id, reminderId = reminderId, type = "PLACE", categoryId = null, brandKeyword = null,
        placeName = "CU 역삼점", placeKakaoId = kakaoId, placeLat = 37.5, placeLng = 127.0,
    )

    private fun build(
        regs: FakeRegs, specs: FakeSpecs, reminders: FakeReminderDao,
        notifLog: FakeNotifLog = FakeNotifLog(), runs: FakeRuns = FakeRuns(),
        policy: NotificationGate.Policy = NotificationGate.Policy(),
    ) = GeofenceEventHandler(
        regDao = regs, triggerSpecDao = specs, reminderDao = reminders,
        notificationLogDao = notifLog, runLogDao = runs,
        policyProvider = object : GatePolicyProvider {
            override suspend fun policy() = policy
        },
        gate = NotificationGate(zone), zone = zone,
        clock = Clock.fixed(noon, zone),
    )

    @Test
    fun `통과한 리마인더는 POI 그룹으로 묶이고 표시 후 기록이 NotificationLog와 PASS를 남긴다`() = runTest {
        val regs = FakeRegs().apply {
            regs["poi:100"] = poiReg("poi:100", "100", "CU 역삼점")
            links += listOf(RegTriggerEntity("poi:100", 11), RegTriggerEntity("poi:100", 12))
        }
        val notifLog = FakeNotifLog(); val runs = FakeRuns()
        val handler = build(
            regs,
            FakeSpecs(mapOf(11L to spec(11, 1), 12L to spec(12, 2))),
            FakeReminderDao(mapOf(1L to reminder(1), 2L to reminder(2))),
            notifLog = notifLog, runs = runs,
        )
        val out = handler.onFenceEvent(listOf("poi:100"), null)
        val group = out.groups.single()
        assertEquals("CU 역삼점", group.poiName)
        assertEquals("poi:100", group.fenceId)
        assertEquals(listOf(1L, 2L), group.reminders.map { it.id })
        assertTrue(notifLog.rows.isEmpty()) // 표시 전이므로 아직 기록되지 않는다 (M1)

        handler.recordShown(group)

        assertEquals(2, notifLog.rows.size) // 리마인더별 1행 (쿨다운 원본)
        assertEquals(2, runs.entries.count { it.result == "PASS" }) // 진단에 발화로 보인다 (검토 B7)
    }

    @Test
    fun `한 이벤트에서 같은 항목이 두 펜스로 통과해도 한 묶음에만 들어간다`() = runTest {
        // 같은 카테고리의 두 지점 DWELL이 한 이벤트로 함께 들어온다.
        // 실기기 09-03 14:57:09: 같은 항목 알림이 CU·이마트24 두 곳에서 동시에 떴다
        val regs = FakeRegs().apply {
            regs["poi:100"] = poiReg("poi:100", "100", "CU 역삼점")
            regs["poi:200"] = poiReg("poi:200", "200", "이마트24 역삼점")
            links += listOf(RegTriggerEntity("poi:100", 11), RegTriggerEntity("poi:200", 11))
        }
        val runs = FakeRuns()
        val handler = build(
            regs,
            FakeSpecs(mapOf(11L to spec(11, 1))),
            FakeReminderDao(mapOf(1L to reminder(1))),
            runs = runs,
        )

        val out = handler.onFenceEvent(listOf("poi:100", "poi:200"), null)

        assertEquals(listOf("poi:100"), out.groups.map { it.fenceId })
        assertTrue(runs.entries.any { it.result == "BLOCK_SAME_EVENT" })
    }

    @Test
    fun `같은 항목이 여러 펜스로 통과하면 이벤트 위치에서 가까운 펜스의 묶음에 들어간다`() = runTest {
        val regs = FakeRegs().apply {
            regs["poi:far"] = poiReg("poi:far", "100", "먼 CU", lat = 37.5018)
            regs["poi:near"] = poiReg("poi:near", "200", "가까운 이마트24", lat = 37.5003)
            links += listOf(RegTriggerEntity("poi:far", 11), RegTriggerEntity("poi:near", 11))
        }
        val handler = build(regs, FakeSpecs(mapOf(11L to spec(11, 1))), FakeReminderDao(mapOf(1L to reminder(1))))
        val point = GeoPoint(37.5, 127.0)

        val out = handler.onFenceEvent(listOf("poi:far", "poi:near"), point)

        val group = out.groups.single()
        assertEquals("poi:near", group.fenceId)
        assertEquals(distanceMeters(point, GeoPoint(37.5003, 127.0)).roundToInt(), group.distanceM)
    }

    @Test
    fun `하루 전체 상한은 같은 이벤트에서 이미 통과한 항목까지 센다`() = runTest {
        val regs = FakeRegs().apply {
            regs["poi:100"] = poiReg("poi:100", "100", "CU 역삼점")
            links += listOf(RegTriggerEntity("poi:100", 11), RegTriggerEntity("poi:100", 12))
        }
        val runs = FakeRuns()
        val handler = build(
            regs, FakeSpecs(mapOf(11L to spec(11, 1), 12L to spec(12, 2))),
            FakeReminderDao(mapOf(1L to reminder(1), 2L to reminder(2))),
            runs = runs, policy = NotificationGate.Policy(dailyCapTotal = 1),
        )

        val out = handler.onFenceEvent(listOf("poi:100"), null)

        assertEquals(listOf(1L), out.groups.single().reminders.map { it.id })
        assertTrue(runs.entries.any { it.result == "BLOCK_TOTAL_DAILY_CAP" })
    }

    @Test
    fun `같은 이벤트라도 서로 다른 항목은 각자의 펜스 묶음에 남는다`() = runTest {
        val regs = FakeRegs().apply {
            regs["poi:100"] = poiReg("poi:100", "100", "CU 역삼점")
            regs["poi:200"] = poiReg("poi:200", "200", "GS25 역삼점")
            links += listOf(RegTriggerEntity("poi:100", 11), RegTriggerEntity("poi:200", 12))
        }
        val handler = build(
            regs,
            FakeSpecs(mapOf(11L to spec(11, 1), 12L to spec(12, 2))),
            FakeReminderDao(mapOf(1L to reminder(1), 2L to reminder(2))),
        )

        val out = handler.onFenceEvent(listOf("poi:100", "poi:200"), null)

        assertEquals(listOf("poi:100", "poi:200"), out.groups.map { it.fenceId })
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
        val out = handler.onFenceEvent(listOf("poi:100"), null)
        assertTrue(out.groups.isEmpty())
        assertTrue(runs.entries.any { it.result == "BLOCK_ITEM_COOLDOWN" })
    }

    @Test
    fun `PLACE 펜스도 같은 항목·같은 지점 24시간 쿨다운이 걸린다 (검토 C1)`() = runTest {
        val regs = FakeRegs().apply {
            regs["place:20"] = placeReg("place:20", kakaoId = "k9", name = "집 앞 CU")
            links += RegTriggerEntity("place:20", 20)
        }
        val notifLog = FakeNotifLog().apply { // 5시간 전 같은 지점 알림 — 항목 쿨다운(4h)은 지났지만 24h 안
            rows += NotificationLogEntity(reminderId = 1, poiKakaoId = "k9", shownAt = noon.toEpochMilli() - 5 * 3_600_000)
        }
        val runs = FakeRuns()
        val handler = build(
            regs, FakeSpecs(mapOf(20L to placeSpec(20, 1, "k9"))), FakeReminderDao(mapOf(1L to reminder(1))), notifLog, runs,
        )

        val out = handler.onFenceEvent(listOf("place:20"), null)

        assertTrue(out.groups.isEmpty())
        assertTrue(runs.entries.any { it.result == "BLOCK_PLACE_COOLDOWN" })
    }

    @Test
    fun `등록에 없는 stale 이벤트는 무시되고 진단에 남는다`() = runTest {
        val runs = FakeRuns()
        val handler = build(FakeRegs(), FakeSpecs(emptyMap()), FakeReminderDao(emptyMap()), runs = runs)
        val out = handler.onFenceEvent(listOf("poi:ghost"), null)
        assertTrue(out.groups.isEmpty())
        assertTrue(!out.sentinelExited)
        assertEquals("STALE", runs.entries.single().result)
    }

    @Test
    fun `표시하지 못한 묶음은 쿨다운을 소모하지 않고 사유를 남긴다`() = runTest {
        val regs = FakeRegs().apply {
            regs["poi:100"] = poiReg("poi:100", "100", "CU 역삼점")
            links += RegTriggerEntity("poi:100", 11)
        }
        val notifLog = FakeNotifLog(); val runs = FakeRuns()
        val handler = build(regs, FakeSpecs(mapOf(11L to spec(11, 1))), FakeReminderDao(mapOf(1L to reminder(1))), notifLog, runs)
        val group = handler.onFenceEvent(listOf("poi:100"), null).groups.single()

        handler.recordNotShown(group)

        assertTrue(notifLog.rows.isEmpty()) // 보이지 않은 알림으로 상한·쿨다운을 소모하지 않는다 (검토 C2)
        assertTrue(runs.entries.any { it.result == "BLOCK_NOTIFICATIONS_OFF" })
    }

    @Test
    fun `센티널 이탈은 플래그로 보고된다`() = runTest {
        val regs = FakeRegs().apply {
            regs["sentinel"] = GeofenceRegEntity("sentinel", "SENTINEL", 37.5, 127.0, 1000f, null, null, null, "b", 0)
        }
        val out = build(regs, FakeSpecs(emptyMap()), FakeReminderDao(emptyMap())).onFenceEvent(listOf("sentinel"), null)
        assertTrue(out.sentinelExited)
    }

    @Test
    fun `triggeringPoint가 없으면 거리 없이, 있으면 미터로 반올림해 그룹에 채운다`() = runTest {
        val regs = FakeRegs().apply {
            regs["poi:100"] = poiReg("poi:100", "100", "CU 역삼점") // lat=37.5, lng=127.0
            links += RegTriggerEntity("poi:100", 11)
        }
        val handler = build(regs, FakeSpecs(mapOf(11L to spec(11, 1))), FakeReminderDao(mapOf(1L to reminder(1))))

        val withoutPoint = handler.onFenceEvent(listOf("poi:100"), null)
        assertNull(withoutPoint.groups.single().distanceM)

        val triggeringPoint = GeoPoint(37.5009, 127.0) // 약 100m 북쪽
        val expected = distanceMeters(triggeringPoint, GeoPoint(37.5, 127.0)).roundToInt()
        val withPoint = handler.onFenceEvent(listOf("poi:100"), triggeringPoint)
        assertEquals(expected, withPoint.groups.single().distanceM)
    }

    @Test
    fun `미러에 센티널 행이 없어도 센티널 키 이벤트는 이탈로 보고하고 stale로 남기지 않는다`() = runTest {
        // 첫 재배치 직후 즉시 이탈(INITIAL_TRIGGER_EXIT)이 미러 기록보다 먼저 도착하는 경우 (묶음 A 인계)
        val runs = FakeRuns()
        val out = build(FakeRegs(), FakeSpecs(emptyMap()), FakeReminderDao(emptyMap()), runs = runs)
            .onFenceEvent(listOf(SENTINEL_FENCE_KEY), null)
        assertTrue(out.sentinelExited)
        assertTrue(runs.entries.isEmpty())
    }
}
