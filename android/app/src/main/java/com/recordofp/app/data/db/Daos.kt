package com.recordofp.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<ReminderEntity>>

    @Transaction
    @Query("SELECT * FROM reminder WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    fun observeActiveWithTriggers(): Flow<List<ReminderWithTriggers>>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun byId(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReminderEntity): Long

    @Query("UPDATE reminder SET status = :status, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, completedAt: Long?, updatedAt: Long)

    @Query("UPDATE reminder SET snoozeUntil = :until, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSnooze(id: Long, until: Long?, updatedAt: Long)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TriggerSpecDao {
    @Query("SELECT * FROM trigger_spec WHERE reminderId = :reminderId")
    suspend fun byReminder(reminderId: Long): List<TriggerSpecEntity>

    @Query(
        "SELECT t.* FROM trigger_spec t JOIN reminder r ON r.id = t.reminderId " +
            "WHERE r.status = 'ACTIVE'",
    )
    suspend fun allActive(): List<TriggerSpecEntity>

    @Query("SELECT * FROM trigger_spec WHERE id IN (:ids) ORDER BY id")
    suspend fun byIds(ids: List<Long>): List<TriggerSpecEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TriggerSpecEntity>)

    @Query("DELETE FROM trigger_spec WHERE reminderId = :reminderId")
    suspend fun deleteByReminder(reminderId: Long)
}

@Dao
interface GeofenceRegDao {
    @Query("SELECT * FROM geofence_reg")
    suspend fun all(): List<GeofenceRegEntity>

    @Query("SELECT * FROM geofence_reg WHERE geofenceId = :geofenceId")
    suspend fun byId(geofenceId: String): GeofenceRegEntity?

    @Query("SELECT triggerId FROM reg_trigger WHERE geofenceId = :geofenceId")
    suspend fun triggerIdsFor(geofenceId: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegs(regs: List<GeofenceRegEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegTriggers(links: List<RegTriggerEntity>)

    @Query("DELETE FROM geofence_reg WHERE geofenceId IN (:ids)")
    suspend fun deleteRegs(ids: List<String>)

    @Query("DELETE FROM reg_trigger WHERE geofenceId IN (:ids)")
    suspend fun deleteRegTriggers(ids: List<String>)

    /**
     * 재배치 차분 적용 (설계 §6.3.6). 링크는 계획된 모든 펜스에 대해 매 재배치마다 재계산한다 —
     * 펜스는 안 변해도 그 펜스를 쓰는 리마인더 집합은 변할 수 있다.
     */
    @Transaction
    suspend fun applyReseed(
        removeIds: List<String>,
        addRegs: List<GeofenceRegEntity>,
        linkFenceIds: List<String>,
        links: List<RegTriggerEntity>,
    ) {
        val unlinkIds = removeIds + linkFenceIds
        if (unlinkIds.isNotEmpty()) deleteRegTriggers(unlinkIds)
        if (removeIds.isNotEmpty()) deleteRegs(removeIds)
        if (addRegs.isNotEmpty()) insertRegs(addRegs)
        if (links.isNotEmpty()) insertRegTriggers(links)
    }
}

@Dao
interface NotificationLogDao {
    @Insert
    suspend fun insert(entity: NotificationLogEntity)

    @Query("SELECT MAX(shownAt) FROM notification_log WHERE reminderId = :reminderId")
    suspend fun lastShownForItem(reminderId: Long): Long?

    @Query(
        "SELECT MAX(shownAt) FROM notification_log " +
            "WHERE reminderId = :reminderId AND poiKakaoId = :poiKakaoId",
    )
    suspend fun lastShownForItemAtPoi(reminderId: Long, poiKakaoId: String): Long?

    @Query("SELECT COUNT(*) FROM notification_log WHERE reminderId = :reminderId AND shownAt >= :since")
    suspend fun countForItemSince(reminderId: Long, since: Long): Int

    @Query("SELECT COUNT(*) FROM notification_log WHERE shownAt >= :since")
    suspend fun countTotalSince(since: Long): Int
}

@Dao
interface EngineRunLogDao {
    @Insert
    suspend fun insert(entity: EngineRunLogEntity)

    @Query("SELECT * FROM engine_run_log ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<EngineRunLogEntity>>

    @Query("DELETE FROM engine_run_log WHERE at < :before")
    suspend fun pruneOlderThan(before: Long)
}
