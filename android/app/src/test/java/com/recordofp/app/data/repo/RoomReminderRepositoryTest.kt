package com.recordofp.app.data.repo

import com.recordofp.app.data.db.ReminderDao
import com.recordofp.app.data.db.ReminderEntity
import com.recordofp.app.data.db.ReminderWithTriggers
import com.recordofp.app.data.db.TriggerSpecDao
import com.recordofp.app.data.db.TriggerSpecEntity
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeDao : ReminderDao {
    val flow = MutableStateFlow<List<ReminderWithTriggers>>(emptyList())
    val statusCalls = mutableListOf<Pair<Long, String>>()
    var byIdResult: ReminderEntity? = null
    override fun observeActive(): Flow<List<ReminderEntity>> = MutableStateFlow(emptyList())
    override fun observeActiveWithTriggers(): Flow<List<ReminderWithTriggers>> = flow
    override suspend fun byId(id: Long): ReminderEntity? = byIdResult
    override suspend fun upsert(entity: ReminderEntity): Long = 42L
    override suspend fun setStatus(id: Long, status: String, completedAt: Long?, updatedAt: Long) {
        statusCalls += id to status
    }
    override suspend fun setSnooze(id: Long, until: Long?, updatedAt: Long) {}
    override suspend fun delete(id: Long) {}
}

private class FakeSpecDao : TriggerSpecDao {
    val upserted = mutableListOf<TriggerSpecEntity>()
    var byReminderResult: List<TriggerSpecEntity> = emptyList()
    override suspend fun byReminder(reminderId: Long) = byReminderResult
    override suspend fun allActive() = emptyList<TriggerSpecEntity>()
    override suspend fun byIds(ids: List<Long>) = emptyList<TriggerSpecEntity>()
    override suspend fun upsertAll(entities: List<TriggerSpecEntity>) { upserted += entities }
    override suspend fun deleteByReminder(reminderId: Long) {}
}

private class RecordingRequester : ReseedRequester {
    var count = 0
    override fun requestItemChange() { count++ }
    override fun requestOpportunistic() {} // 이 테스트의 관심사 아님 (F1은 ProtectionReseedTriggerTest)
}

class RoomReminderRepositoryTest {

    private val dao = FakeDao()
    private val specDao = FakeSpecDao()
    private val requester = RecordingRequester()
    private val repo = RoomReminderRepository(
        dao, specDao, requester, Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC),
    )

    @Test
    fun `observeActive는 트리거가 채워진 도메인 모델을 낸다`() = runTest {
        dao.flow.value = listOf(
            ReminderWithTriggers(
                reminder = ReminderEntity(1, "건전지", null, "ACTIVE", null, 0, 0, null),
                triggers = listOf(
                    TriggerSpecEntity(10, 1, "CATEGORY", "convenience", null, null, null, null, null),
                ),
            ),
        )
        val item = repo.observeActive().first().single()
        assertEquals("건전지", item.title)
        assertEquals("cat:convenience", item.triggers.single().matchKey)
    }

    @Test
    fun `upsert는 트리거를 새 reminderId로 저장하고 재배치를 요청한다`() = runTest {
        val id = repo.upsert(
            Reminder(
                id = 0, title = "휴지", createdAt = 0, updatedAt = 0,
                triggers = listOf(TriggerSpec(type = TriggerType.CATEGORY, categoryId = "mart")),
            ),
        )
        assertEquals(42L, id)
        assertEquals(42L, specDao.upserted.single().reminderId)
        assertEquals(1, requester.count)
    }

    @Test
    fun `complete와 delete도 재배치를 요청한다`() = runTest {
        repo.complete(1)
        repo.delete(2)
        assertEquals(listOf(1L to "DONE"), dao.statusCalls)
        assertEquals(2, requester.count)
    }

    @Test
    fun `byId는 트리거가 채워진 도메인 모델을 낸다`() = runTest {
        dao.byIdResult = ReminderEntity(1, "건전지", null, "ACTIVE", null, 0, 0, null)
        specDao.byReminderResult = listOf(
            TriggerSpecEntity(10, 1, "CATEGORY", "convenience", null, null, null, null, null),
        )

        val item = repo.byId(1)

        assertEquals("건전지", item?.title)
        assertEquals("cat:convenience", item?.triggers?.single()?.matchKey)
    }
}
