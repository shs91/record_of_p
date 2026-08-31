package com.recordofp.app.data.repo

import com.recordofp.app.data.db.ReminderDao
import com.recordofp.app.data.db.ReminderEntity
import com.recordofp.app.data.db.TriggerSpecDao
import com.recordofp.app.data.db.TriggerSpecEntity
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.ReminderStatus
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ReminderRepository {
    fun observeActive(): Flow<List<Reminder>>
    suspend fun upsert(reminder: Reminder): Long
    suspend fun complete(id: Long)
    suspend fun muteUntil(id: Long, untilEpochMs: Long)
    suspend fun delete(id: Long)
    suspend fun activeTriggers(): List<TriggerSpec>
}

@Singleton
class RoomReminderRepository @Inject constructor(
    private val reminderDao: ReminderDao,
    private val triggerSpecDao: TriggerSpecDao,
    private val clock: Clock,
) : ReminderRepository {

    override fun observeActive(): Flow<List<Reminder>> =
        // 목록 화면은 트리거 요약이 필요하다 — v1 구현 시 트리거 조인 쿼리로 확장한다.
        reminderDao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(reminder: Reminder): Long {
        val now = clock.millis()
        val id = reminderDao.upsert(reminder.toEntity(now))
        val reminderId = if (reminder.id == 0L) id else reminder.id
        triggerSpecDao.deleteByReminder(reminderId)
        triggerSpecDao.upsertAll(reminder.triggers.map { it.toEntity(reminderId) })
        return reminderId
    }

    override suspend fun complete(id: Long) {
        val now = clock.millis()
        reminderDao.setStatus(id, ReminderStatus.DONE.name, completedAt = now, updatedAt = now)
    }

    override suspend fun muteUntil(id: Long, untilEpochMs: Long) {
        reminderDao.setSnooze(id, untilEpochMs, updatedAt = clock.millis())
    }

    override suspend fun delete(id: Long) = reminderDao.delete(id)

    override suspend fun activeTriggers(): List<TriggerSpec> =
        triggerSpecDao.allActive().map { it.toDomain() }
}

private fun ReminderEntity.toDomain() = Reminder(
    id = id,
    title = title,
    memo = memo,
    status = ReminderStatus.valueOf(status),
    snoozeUntil = snoozeUntil,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
)

private fun Reminder.toEntity(now: Long) = ReminderEntity(
    id = id,
    title = title,
    memo = memo,
    status = status.name,
    snoozeUntil = snoozeUntil,
    createdAt = if (id == 0L) now else createdAt,
    updatedAt = now,
    completedAt = completedAt,
)

private fun TriggerSpecEntity.toDomain() = TriggerSpec(
    id = id,
    reminderId = reminderId,
    type = TriggerType.valueOf(type),
    categoryId = categoryId,
    brandKeyword = brandKeyword,
    placeName = placeName,
    placeKakaoId = placeKakaoId,
    placePoint = if (placeLat != null && placeLng != null) GeoPoint(placeLat, placeLng) else null,
)

private fun TriggerSpec.toEntity(parentId: Long) = TriggerSpecEntity(
    id = id,
    reminderId = parentId,
    type = type.name,
    categoryId = categoryId,
    brandKeyword = brandKeyword,
    placeName = placeName,
    placeKakaoId = placeKakaoId,
    placeLat = placePoint?.lat,
    placeLng = placePoint?.lng,
)
