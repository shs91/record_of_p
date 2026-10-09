package com.recordofp.app.data.engine

import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import com.recordofp.app.data.db.GeofenceRegDao
import com.recordofp.app.data.db.NotificationLogDao
import com.recordofp.app.data.db.NotificationLogEntity
import com.recordofp.app.data.db.ReminderDao
import com.recordofp.app.data.db.TriggerSpecDao
import com.recordofp.app.domain.engine.FenceKind
import com.recordofp.app.domain.engine.NotificationGate
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.ReminderStatus
import com.recordofp.app.domain.model.distanceMeters
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** fenceId: 알림 ID의 기준 — 같은 POI를 대변하는 서로 다른 펜스(PLACE·CATEGORY)의 알림이 서로 덮어쓰지 않게 한다 */
data class AlertGroup(
    val fenceId: String,
    val poiId: String?,
    val poiName: String?,
    val reminders: List<Reminder>,
    val distanceM: Int? = null,
)

data class EventOutcome(val sentinelExited: Boolean, val groups: List<AlertGroup>)

/** 알림 정책 공급 (스펙 §4.5) — 구현은 [StoreGatePolicyProvider] (SettingsStore 기반). */
interface GatePolicyProvider { suspend fun policy(): NotificationGate.Policy }

/** 지오펜스 이벤트 → 필터 체인 → POI 그룹 (스펙 §6.5). 발행은 NearbyNotifier 몫. */
@Singleton
class GeofenceEventHandler @Inject constructor(
    private val regDao: GeofenceRegDao,
    private val triggerSpecDao: TriggerSpecDao,
    private val reminderDao: ReminderDao,
    private val notificationLogDao: NotificationLogDao,
    private val runLogDao: EngineRunLogDao,
    private val policyProvider: GatePolicyProvider,
    private val gate: NotificationGate,
    private val zone: ZoneId,
    private val clock: Clock,
) {

    suspend fun onFenceEvent(fenceIds: List<String>, triggeringPoint: GeoPoint?): EventOutcome {
        val now = clock.instant()
        val startOfDay = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val policy = policyProvider.policy()
        var sentinelExited = false
        val groups = mutableListOf<AlertGroup>()
        // 한 이벤트에서 같은 항목이 여러 펜스로 통과해도 알림은 한 번만 — 항목 쿨다운은 표시 뒤(recordShown)에야
        // 기록되므로 같은 이벤트 안에서는 걸리지 않는다 (실기기 09-03 14:57:09)
        val passedInThisEvent = mutableSetOf<Long>()

        for (fenceId in fenceIds) {
            val reg = regDao.byId(fenceId) ?: continue // stale 이벤트 폐기 (§6.5.1)
            if (reg.kind == FenceKind.SENTINEL.name) { sentinelExited = true; continue }

            val reminderIds = triggerSpecDao.byIds(regDao.triggerIdsFor(fenceId))
                .map { it.reminderId }.distinct()
            val passed = mutableListOf<Reminder>()

            for (reminderId in reminderIds) {
                if (reminderId in passedInThisEvent) {
                    logEvent(now, BLOCK_SAME_EVENT, "reminder=$reminderId poi=${reg.poiName}")
                    continue
                }
                val row = reminderDao.byId(reminderId) ?: continue
                val history = NotificationGate.History(
                    lastShownForItem = notificationLogDao.lastShownForItem(reminderId)?.let(Instant::ofEpochMilli),
                    lastShownForItemAtPoi = reg.poiKakaoId?.let {
                        notificationLogDao.lastShownForItemAtPoi(reminderId, it)?.let(Instant::ofEpochMilli)
                    },
                    shownTodayForItem = notificationLogDao.countForItemSince(reminderId, startOfDay),
                    shownTodayTotal = notificationLogDao.countTotalSince(startOfDay),
                )
                val decision = gate.evaluate(
                    now = now,
                    status = ReminderStatus.valueOf(row.status),
                    snoozeUntil = row.snoozeUntil?.let(Instant::ofEpochMilli),
                    history = history,
                    policy = policy,
                )
                if (decision == NotificationGate.Decision.PASS) {
                    passed += Reminder(
                        id = row.id, title = row.title, memo = row.memo,
                        status = ReminderStatus.valueOf(row.status), snoozeUntil = row.snoozeUntil,
                        createdAt = row.createdAt, updatedAt = row.updatedAt, completedAt = row.completedAt,
                    )
                    passedInThisEvent += reminderId
                    // NotificationLog 기록은 실제 표시 후(recordShown) — 여기서 기록하면 표시 전 카운트가 된다 (M1)
                } else {
                    logEvent(now, decision.name, "reminder=$reminderId poi=${reg.poiName}")
                }
            }
            if (passed.isNotEmpty()) {
                val distanceM = triggeringPoint?.let {
                    distanceMeters(it, GeoPoint(reg.lat, reg.lng)).roundToInt()
                }
                groups += AlertGroup(reg.geofenceId, reg.poiKakaoId, reg.poiName, passed, distanceM)
            }
        }
        return EventOutcome(sentinelExited, groups)
    }

    private suspend fun logEvent(at: Instant, result: String, note: String) = runLogDao.insert(
        EngineRunLogEntity(at = at.toEpochMilli(), cause = LOG_CAUSE, result = result, registeredCount = 0, note = note),
    )

    /** 알림이 실제로 화면에 뜬 뒤에만 쿨다운 계산의 원본을 남긴다 (표시 전 기록 금지, §6.5 5단계) */
    suspend fun recordShown(reminderIds: List<Long>, poiId: String?) {
        val shownAt = clock.millis()
        reminderIds.forEach { id ->
            notificationLogDao.insert(NotificationLogEntity(reminderId = id, poiKakaoId = poiId, shownAt = shownAt))
        }
    }

    companion object {
        /** EngineRunLog.cause — 리시버의 오류 기록도 같은 원인으로 남긴다 */
        const val LOG_CAUSE = "FENCE_EVENT"

        /** 같은 이벤트에서 이미 다른 펜스로 통과한 항목 (진단 화면에서 차단으로 보인다) */
        const val BLOCK_SAME_EVENT = "BLOCK_SAME_EVENT"
    }
}
