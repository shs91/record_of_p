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
import com.recordofp.app.domain.engine.FenceKind
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
import com.recordofp.app.domain.engine.loiteringDelayMs
import com.recordofp.app.domain.engine.transition
import com.recordofp.app.domain.model.GeoPoint
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** OS 지오펜스 반영 지점 — GMS 의존을 이 인터페이스 뒤로 격리한다 */
interface FenceApplier {
    /** 차분 적용: removeIds를 지우고 add를 등록한다 */
    suspend fun apply(diff: FenceDiff)

    /** 이 앱의 OS 펜스를 전부(미러에 없는 고아 포함) 지우고 fences를 등록한다. 빈 목록이면 해제만 한다 (검토 B1) */
    suspend fun replaceAll(fences: List<PlannedFence>)
}

/** EngineStateStore가 구현 — 테스트에서 페이크 주입용 */
interface ReseedStateStore {
    suspend fun lastReseed(): ReseedStamp?
    suspend fun recordReseed(stamp: ReseedStamp)

    /** 재배치 스탬프를 지운다 — 다음 재배치가 디바운스에 걸리지 않게 한다 (권한 회수 정리, 검토 B3) */
    suspend fun clearReseedStamp()

    /** OS 펜스가 사라졌거나 어디까지 반영됐는지 몰라 미러를 믿을 수 없는 상태인가 (검토 B1) */
    suspend fun fencesLost(): Boolean
    suspend fun setFencesLost(lost: Boolean)
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

    /**
     * OS 펜스가 사라졌다는 신호(BOOT·FENCE_LOST)를 기록한다. 워커가 권한·위치 확인보다 먼저 부른다 —
     * 그 작업이 재시도로 밀리거나 큐에서 다른 원인으로 대체돼도, 다음에 성공하는 재배치가 전체 재등록한다 (검토 B1)
     */
    suspend fun markFencesLost() = mutex.withLock { stateStore.setFencesLost(true) }

    suspend fun reseed(cause: ReseedCause, current: GeoPoint): ReseedResult =
        mutex.withLock { reseedLocked(cause, current) }

    private suspend fun reseedLocked(cause: ReseedCause, current: GeoPoint): ReseedResult {
        val now = clock.millis()
        val fencesLost = stateStore.fencesLost()
        // 펜스 소실 표시가 있으면 디바운스와 무관하게 바로 전체 재등록한다 (검토 B1)
        if (!fencesLost && !governor.shouldReseed(cause, now, stateStore.lastReseed(), current)) {
            // APP_OPEN 디바운스 스킵은 매 앱 진입마다 일어나는 정상 소음 — 로그 생략 (스팸 방지)
            if (cause == ReseedCause.APP_OPEN) return ReseedResult.SKIPPED_DEBOUNCE
            return log(cause, ReseedResult.SKIPPED_DEBOUNCE, 0, now, null)
        }
        // OS 펜스를 믿을 수 없다: 재부팅·앱 업데이트(BOOT, §6.1) 또는 소실 표시(검토 B1)
        val osUntrusted = fencesLost || cause == ReseedCause.BOOT
        // PERIODIC은 OS 등록을 조회할 수 없으니 주기적으로 전부 다시 등록하는 것이 §6.2 "등록 상태 검증"이다
        val fullResync = osUntrusted || cause == ReseedCause.PERIODIC

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
                            placePoint = req.point, placeName = req.name, placeKakaoId = req.kakaoId,
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
                // 단 OS 펜스가 사라진 상태(소실 표시·BOOT)라면 "유지할 기존 등록"이 OS에 없다 — 미러대로 되살린다 (최종 리뷰 I2)
                return if (osUntrusted) restoreFromMirror(cause, now, e) else log(cause, ReseedResult.FAILED, 0, now, e.message)
            }
            planned = planner.plan(current, candidates)
        }

        val existing = regDao.all()
        val diff = if (fullResync) {
            // 이 앱의 OS 펜스를 전부 지우고(replaceAll) 계획을 전부 등록한다 — 미러도 통째로 바꾼다
            FenceDiff(removeIds = existing.map { it.geofenceId }, add = planned)
        } else {
            differ.diff(existing.map { ExistingFence(it.geofenceId, GeoPoint(it.lat, it.lng), it.radiusM) }, planned)
        }
        val touchesOs = fullResync || !diff.isEmpty

        // OS에 손대기 시작하면 미러 갱신까지 마친다 — reseed_now 큐의 REPLACE가 실행 중인 작업을 취소해도
        // OS와 미러가 어긋나지 않게 한다 (검토 B4)
        return withContext(NonCancellable) {
            // 선기록(write-ahead): OS 호출 전에 소실 표시를 남긴다. OS 호출·미러 쓰기 중 실패하거나 프로세스가 죽으면
            // 둘의 일치를 보장할 수 없으므로 다음 재배치가 원인과 무관하게 전체 재등록한다 (검토 B1)
            if (touchesOs) stateStore.setFencesLost(true)
            try {
                when {
                    fullResync -> applier.replaceAll(planned)
                    touchesOs -> applier.apply(diff)
                }
                val batchId = UUID.randomUUID().toString()
                val links = planned.flatMap { fence ->
                    fence.matchKeys.flatMap { key ->
                        triggerIdsByMatchKey[key].orEmpty().map { RegTriggerEntity(fence.key, it) }
                    }
                }
                regDao.applyReseed(
                    removeIds = diff.removeIds,
                    addRegs = diff.add.map { it.toEntity(batchId, now) },
                    linkFenceIds = planned.map { it.key },
                    links = links,
                )
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                // 소실 표시는 이미 남아 있다 — 기록만 하고 끝낸다. 재시도는 호출부(Worker) 몫
                return@withContext log(cause, ReseedResult.FAILED, 0, now, e.message)
            }
            stateStore.recordReseed(ReseedStamp(now, current))
            if (touchesOs) stateStore.setFencesLost(false) // OS와 미러가 다시 일치한다
            val result = if (planned.isEmpty()) ReseedResult.CLEARED_NO_TRIGGERS else ReseedResult.APPLIED
            log(cause, result, planned.size, now, if (fullResync) "full-resync" else null)
        }
    }

    /**
     * 위치 권한이 없어 지오펜스를 유지할 수 없을 때 (§6.4 권한 회수, 검토 B3).
     * OS의 이 앱 펜스 전부(미러에 없는 고아 포함)와 미러를 비우고 재배치 스탬프를 지운다 —
     * 권한이 돌아오면 다음 재배치(F1의 APP_OPEN 포함)가 디바운스 없이 바로 돈다.
     * @param note 진단 화면에 남길 사유 (예: 없는 권한 이름)
     */
    suspend fun standDown(cause: ReseedCause, note: String? = null): ReseedResult =
        mutex.withLock { standDownLocked(cause, note) } // F5: 재배치와 교차하면 고아 등록이 남는다

    private suspend fun standDownLocked(cause: ReseedCause, note: String?): ReseedResult = withContext(NonCancellable) {
        val now = clock.millis()
        stateStore.clearReseedStamp()
        val existing = regDao.all()
        if (existing.isEmpty() && !stateStore.fencesLost()) {
            // 걷어낼 것이 없다. 권한이 없는 동안 워커가 시도할 때마다 같은 행이 쌓이므로 기록하지 않는다 (F2)
            return@withContext ReseedResult.STOOD_DOWN
        }
        stateStore.setFencesLost(true) // 선기록 (검토 B1)
        try {
            applier.replaceAll(emptyList())
            regDao.applyReseed(existing.map { it.geofenceId }, emptyList(), emptyList(), emptyList())
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            return@withContext log(cause, ReseedResult.FAILED, 0, now, e.message)
        }
        stateStore.setFencesLost(false)
        log(cause, ReseedResult.STOOD_DOWN, 0, now, note)
    }

    /**
     * OS를 믿을 수 없는 상태에서 POI 조회가 실패했을 때 OS를 미러대로 되돌린다 (§6.4 "구 데이터가 무등록보다 낫다", 최종 리뷰 I2).
     * 부팅 직후 네트워크가 없으면, 이것이 없을 때 OS에 펜스가 하나도 없다(PLACE·센티널 포함).
     * 미러·링크·스탬프는 그대로 두고, 조회는 다시 해야 하므로 결과는 FAILED다.
     */
    private suspend fun restoreFromMirror(cause: ReseedCause, now: Long, lookupError: Exception): ReseedResult =
        withContext(NonCancellable) {
            try {
                val fences = regDao.all().map { it.toPlannedFence() }
                if (fences.isEmpty()) return@withContext log(cause, ReseedResult.FAILED, 0, now, lookupError.message)
                stateStore.setFencesLost(true) // 선기록 — 복구가 끝나야 지운다 (검토 B1)
                applier.replaceAll(fences)
                stateStore.setFencesLost(false) // OS가 다시 미러와 같다
                log(cause, ReseedResult.FAILED, fences.size, now, "restored-from-mirror: ${lookupError.message}")
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                log(cause, ReseedResult.FAILED, 0, now, "restore-failed: ${e.message}")
            }
        }

    private suspend fun log(
        cause: ReseedCause, result: ReseedResult, count: Int, at: Long, note: String?,
    ): ReseedResult {
        runLogDao.insert(EngineRunLogEntity(at = at, cause = cause.name, result = result.name, registeredCount = count, note = note))
        return result
    }

    private fun GeofenceRegEntity.toPlannedFence(): PlannedFence {
        val fenceKind = FenceKind.valueOf(kind)
        return PlannedFence(
            key = geofenceId, kind = fenceKind, center = GeoPoint(lat, lng), radiusM = radiusM,
            transition = fenceKind.transition(), loiteringDelayMs = fenceKind.loiteringDelayMs(),
            matchKeys = matchKey?.split(",")?.filter { it.isNotEmpty() }?.toSet().orEmpty(),
            poiName = poiName, poiId = poiKakaoId,
        )
    }

    private fun PlannedFence.toEntity(batchId: String, at: Long) = GeofenceRegEntity(
        geofenceId = key, kind = kind.name, lat = center.lat, lng = center.lng, radiusM = radiusM,
        poiName = poiName, poiKakaoId = poiId, matchKey = matchKeys.joinToString(","),
        reseedBatchId = batchId, registeredAt = at,
    )
}
