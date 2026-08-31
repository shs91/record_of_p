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
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.ReminderStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class AlertGroup(val poiId: String?, val poiName: String?, val reminders: List<Reminder>)

data class EventOutcome(val sentinelExited: Boolean, val groups: List<AlertGroup>)

/** 알림 정책 공급 — v1 기본값. T11에서 SettingsStore 기반 구현으로 교체된다. */
interface GatePolicyProvider { suspend fun policy(): NotificationGate.Policy }

class DefaultGatePolicyProvider @Inject constructor() : GatePolicyProvider {
    override suspend fun policy() = NotificationGate.Policy()
}

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

    suspend fun onFenceEvent(fenceIds: List<String>): EventOutcome {
        val now = clock.instant()
        val startOfDay = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val policy = policyProvider.policy()
        var sentinelExited = false
        val groups = mutableListOf<AlertGroup>()

        for (fenceId in fenceIds) {
            val reg = regDao.byId(fenceId) ?: continue // stale 이벤트 폐기 (§6.5.1)
            if (reg.kind == FenceKind.SENTINEL.name) { sentinelExited = true; continue }

            val reminderIds = triggerSpecDao.byIds(regDao.triggerIdsFor(fenceId))
                .map { it.reminderId }.distinct()
            val passed = mutableListOf<Reminder>()

            for (reminderId in reminderIds) {
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
                    notificationLogDao.insert(
                        NotificationLogEntity(reminderId = reminderId, poiKakaoId = reg.poiKakaoId, shownAt = now.toEpochMilli()),
                    )
                } else {
                    runLogDao.insert(
                        EngineRunLogEntity(
                            at = now.toEpochMilli(), cause = "FENCE_EVENT", result = decision.name,
                            registeredCount = 0, note = "reminder=$reminderId poi=${reg.poiName}",
                        ),
                    )
                }
            }
            if (passed.isNotEmpty()) groups += AlertGroup(reg.poiKakaoId, reg.poiName, passed)
        }
        return EventOutcome(sentinelExited, groups)
    }
}
