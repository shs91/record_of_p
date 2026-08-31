package com.recordofp.app.data.engine

import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import com.recordofp.app.data.db.GeofenceRegDao
import com.recordofp.app.data.db.GeofenceRegEntity
import com.recordofp.app.data.db.RegTriggerEntity
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.DiffCalculator
import com.recordofp.app.domain.engine.ExistingFence
import com.recordofp.app.domain.engine.FenceDiff
import com.recordofp.app.domain.engine.FenceKind
import com.recordofp.app.domain.engine.PlaceRequest
import com.recordofp.app.domain.engine.PlannedFence
import com.recordofp.app.domain.engine.QueryRequest
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.domain.engine.ReseedGovernor
import com.recordofp.app.domain.engine.ReseedPlanner
import com.recordofp.app.domain.engine.ReseedStamp
import com.recordofp.app.domain.engine.TriggerCandidates
import com.recordofp.app.domain.engine.TriggerResolver
import com.recordofp.app.domain.model.GeoPoint
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** OS 지오펜스 반영 지점 — GMS 의존을 이 인터페이스 뒤로 격리한다 */
interface FenceApplier {
    suspend fun apply(diff: FenceDiff)
}

/** EngineStateStore가 구현 — 테스트에서 페이크 주입용 */
interface ReseedStateStore {
    suspend fun lastReseed(): ReseedStamp?
    suspend fun recordReseed(stamp: ReseedStamp)
}

enum class ReseedResult { APPLIED, SKIPPED_DEBOUNCE, CLEARED_NO_TRIGGERS, FAILED }

/** 재배치 오케스트레이터 (스펙 §6.2~6.4) */
@Singleton
class ReseedService @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val poiRepository: PoiRepository,
    private val regDao: GeofenceRegDao,
    private val runLogDao: EngineRunLogDao,
    private val applier: FenceApplier,
    private val stateStore: ReseedStateStore,
    private val governor: ReseedGovernor,
    private val resolver: TriggerResolver,
    private val planner: ReseedPlanner,
    private val differ: DiffCalculator,
    private val clock: Clock,
) {

    suspend fun reseed(cause: ReseedCause, current: GeoPoint): ReseedResult {
        val now = clock.millis()
        if (!governor.shouldReseed(cause, now, stateStore.lastReseed(), current)) {
            return log(cause, ReseedResult.SKIPPED_DEBOUNCE, 0, now, null)
        }

        val triggers = reminderRepository.activeTriggers()
        val triggerIdsByMatchKey: Map<String, List<Long>> =
            triggers.groupBy({ it.matchKey }, { it.id })
        val requests = resolver.resolve(triggers)

        val planned: List<PlannedFence>
        if (requests.isEmpty()) {
            planned = emptyList() // 볼 것이 없으면 센티널도 걷는다
        } else {
            val candidates = try {
                requests.map { req ->
                    when (req) {
                        is PlaceRequest -> TriggerCandidates(
                            matchKey = req.matchKey, isPlace = true,
                            placePoint = req.point, placeName = req.name,
                        )
                        is QueryRequest -> TriggerCandidates(
                            matchKey = req.matchKey,
                            candidates = poiRepository.search(req.resolution, req.query, current),
                        )
                    }
                }
            } catch (e: Exception) {
                // §6.4: 기존 등록 유지, 아무것도 바꾸지 않는다. 재시도는 호출부(Worker) 몫.
                return log(cause, ReseedResult.FAILED, 0, now, e.message)
            }
            planned = planner.plan(current, candidates)
        }

        val existing = regDao.all().map { ExistingFence(it.geofenceId, GeoPoint(it.lat, it.lng), it.radiusM) }
        val diff = differ.diff(existing, planned)
        try {
            applier.apply(diff)
        } catch (e: Exception) {
            return log(cause, ReseedResult.FAILED, 0, now, e.message)
        }

        val batchId = UUID.randomUUID().toString()
        val addRegs = diff.add.map { it.toEntity(batchId, now) }
        val links = planned.flatMap { fence ->
            fence.matchKeys.flatMap { key ->
                triggerIdsByMatchKey[key].orEmpty().map { RegTriggerEntity(fence.key, it) }
            }
        }
        regDao.applyReseed(diff.removeIds, addRegs, linkFenceIds = planned.map { it.key }, links = links)
        stateStore.recordReseed(ReseedStamp(now, current))

        val result = if (planned.isEmpty()) ReseedResult.CLEARED_NO_TRIGGERS else ReseedResult.APPLIED
        return log(cause, result, planned.size, now, null)
    }

    private suspend fun log(
        cause: ReseedCause, result: ReseedResult, count: Int, at: Long, note: String?,
    ): ReseedResult {
        runLogDao.insert(EngineRunLogEntity(at = at, cause = cause.name, result = result.name, registeredCount = count, note = note))
        return result
    }

    private fun PlannedFence.toEntity(batchId: String, at: Long) = GeofenceRegEntity(
        geofenceId = key, kind = kind.name, lat = center.lat, lng = center.lng, radiusM = radiusM,
        poiName = poiName, poiKakaoId = poiId, matchKey = matchKeys.joinToString(","),
        reseedBatchId = batchId, registeredAt = at,
    )
}
