package com.recordofp.app.data.engine

import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import com.recordofp.app.data.db.GeofenceRegDao
import com.recordofp.app.data.db.GeofenceRegEntity
import com.recordofp.app.data.db.RegTriggerEntity
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.DiffCalculator
import com.recordofp.app.domain.engine.EngineParams
import com.recordofp.app.domain.engine.ExistingFence
import com.recordofp.app.domain.engine.FenceDiff
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** OS 지오펜스 반영 지점 — GMS 의존을 이 인터페이스 뒤로 격리한다 */
interface FenceApplier {
    suspend fun apply(diff: FenceDiff)
}

/** EngineStateStore가 구현 — 테스트에서 페이크 주입용 */
interface ReseedStateStore {
    suspend fun lastReseed(): ReseedStamp?
    suspend fun recordReseed(stamp: ReseedStamp)
}

enum class ReseedResult { APPLIED, SKIPPED_DEBOUNCE, CLEARED_NO_TRIGGERS, FAILED, STOOD_DOWN }

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

    /**
     * F5: 강한 큐(BOOT/ITEM_CHANGE/…)와 기회적 큐(APP_OPEN)는 WorkManager 유니크 큐가 서로
     * 달라 동시에 돌 수 있다 — 실행을 직렬화하고 거버너 판정을 락 안에서 해, 뒤에 든 쪽이
     * 앞선 실행의 스탬프를 보고 디바운스되게 한다 (실기기: 카카오 호출·GMS 등록 2배 관찰).
     */
    private val mutex = Mutex()

    suspend fun reseed(cause: ReseedCause, current: GeoPoint): ReseedResult =
        mutex.withLock { reseedLocked(cause, current) }

    private suspend fun reseedLocked(cause: ReseedCause, current: GeoPoint): ReseedResult {
        val now = clock.millis()
        if (!governor.shouldReseed(cause, now, stateStore.lastReseed(), current)) {
            // APP_OPEN 디바운스 스킵은 매 앱 진입마다 일어나는 정상 소음 — 로그 생략 (스팸 방지)
            if (cause == ReseedCause.APP_OPEN) return ReseedResult.SKIPPED_DEBOUNCE
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
                            candidates = poiRepository.search(
                                req.resolution, req.query, current,
                                maxResults = EngineParams.QUERY_MAX_RESULTS,
                            ),
                        )
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                // §6.4: 기존 등록 유지, 아무것도 바꾸지 않는다. 재시도는 호출부(Worker) 몫.
                return log(cause, ReseedResult.FAILED, 0, now, e.message)
            }
            planned = planner.plan(current, candidates)
        }

        val existing = regDao.all().map { ExistingFence(it.geofenceId, GeoPoint(it.lat, it.lng), it.radiusM) }
        val diff = differ.diff(existing, planned)
        // §6.1: BOOT(재부팅·앱 업데이트)는 OS 펜스가 소멸한 상태 — 미러와 무관하게 전량 재등록.
        // PERIODIC 헬스체크도 동일하게 상태를 단언한다(같은 requestId 재등록은 GMS에서 안전한 교체).
        val toApply = when (cause) {
            ReseedCause.BOOT, ReseedCause.PERIODIC -> FenceDiff(diff.removeIds, planned)
            else -> diff
        }
        try {
            applier.apply(toApply)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            return log(cause, ReseedResult.FAILED, 0, now, e.message)
        }

        val batchId = UUID.randomUUID().toString()
        val addRegs = toApply.add.map { it.toEntity(batchId, now) }
        val links = planned.flatMap { fence ->
            fence.matchKeys.flatMap { key ->
                triggerIdsByMatchKey[key].orEmpty().map { RegTriggerEntity(fence.key, it) }
            }
        }
        regDao.applyReseed(toApply.removeIds, addRegs, linkFenceIds = planned.map { it.key }, links = links)
        stateStore.recordReseed(ReseedStamp(now, current))

        val result = if (planned.isEmpty()) ReseedResult.CLEARED_NO_TRIGGERS else ReseedResult.APPLIED
        return log(cause, result, planned.size, now, null)
    }

    /** 권한 부재/회수 시: OS·미러의 등록을 전부 걷어낸다 (§6.4 고아 지오펜스 방지). 이미 비어 있으면 로그만. */
    suspend fun standDown(cause: ReseedCause): ReseedResult =
        mutex.withLock { standDownLocked(cause) } // F5: 재배치와 교차하면 고아 등록이 남는다

    private suspend fun standDownLocked(cause: ReseedCause): ReseedResult {
        val now = clock.millis()
        val existing = regDao.all()
        if (existing.isEmpty()) return log(cause, ReseedResult.STOOD_DOWN, 0, now, "no registrations")
        try {
            applier.apply(FenceDiff(removeIds = existing.map { it.geofenceId }, add = emptyList()))
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            return log(cause, ReseedResult.FAILED, 0, now, e.message)
        }
        regDao.applyReseed(existing.map { it.geofenceId }, emptyList(), emptyList(), emptyList())
        return log(cause, ReseedResult.STOOD_DOWN, 0, now, null)
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
