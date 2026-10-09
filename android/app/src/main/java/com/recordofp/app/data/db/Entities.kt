package com.recordofp.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val memo: String?,
    val status: String,
    val snoozeUntil: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
)

@Entity(
    tableName = "trigger_spec",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("reminderId")],
)
data class TriggerSpecEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val type: String,
    val categoryId: String?,
    val brandKeyword: String?,
    val placeName: String?,
    val placeKakaoId: String?,
    val placeLat: Double?,
    val placeLng: Double?,
)

/** 현재 OS에 등록된 지오펜스의 미러 (설계 §5.3) */
@Entity(tableName = "geofence_reg")
data class GeofenceRegEntity(
    @PrimaryKey val geofenceId: String,
    val kind: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Float,
    val poiName: String?,
    val poiKakaoId: String?,
    /** 카테고리 id 또는 브랜드 키워드 (여러 트리거가 매칭되면 콤마로 합침) */
    val matchKey: String?,
    val reseedBatchId: String,
    val registeredAt: Long,
)

/** 지오펜스 ↔ 트리거 N:M (한 POI가 여러 트리거를 대변) */
@Entity(tableName = "reg_trigger", primaryKeys = ["geofenceId", "triggerId"])
data class RegTriggerEntity(
    val geofenceId: String,
    val triggerId: Long,
)

/** 쿨다운·하루 상한 계산의 원본 (설계 §6.5) */
@Entity(tableName = "notification_log")
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val poiKakaoId: String?,
    val shownAt: Long,
)

/** 진단 화면용 엔진 실행 이력 (설계 §4.4) */
@Entity(tableName = "engine_run_log")
data class EngineRunLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val cause: String,
    val result: String,
    val registeredCount: Int,
    val note: String?,
)

data class ReminderWithTriggers(
    @Embedded val reminder: ReminderEntity,
    @Relation(parentColumn = "id", entityColumn = "reminderId")
    val triggers: List<TriggerSpecEntity>,
)
