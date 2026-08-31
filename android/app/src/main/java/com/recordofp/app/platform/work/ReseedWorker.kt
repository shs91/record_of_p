package com.recordofp.app.platform.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import com.recordofp.app.data.engine.ReseedResult
import com.recordofp.app.data.engine.ReseedService
import com.recordofp.app.domain.engine.EngineParams
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.data.location.LocationProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.util.concurrent.TimeUnit

/** 재배치 실행자 (스펙 §6.2). 판정·오케스트레이션은 ReseedService — 여기는 위치·권한·재시도 접착만. */
@HiltWorker
class ReseedWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val reseedService: ReseedService,
    private val locationProvider: LocationProvider,
    private val runLogDao: EngineRunLogDao,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val cause = inputData.getString(KEY_CAUSE)
            ?.let { runCatching { ReseedCause.valueOf(it) }.getOrNull() }
            ?: ReseedCause.PERIODIC

        if (cause == ReseedCause.PERIODIC) {
            // §4.4: 진단 로그가 무한정 쌓이지 않게 주기 작업이 돌 때마다 정리
            runLogDao.pruneOlderThan(clock.millis() - EngineParams.RUN_LOG_RETENTION_MS)
        }

        val fineGranted = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            // 재시도 무의미 — 권한은 대시보드(§4.3)가 사용자에게 알린다
            runLogDao.insert(
                EngineRunLogEntity(at = clock.millis(), cause = cause.name, result = "NO_PERMISSION", registeredCount = 0, note = null),
            )
            reseedService.standDown(cause) // §6.4 권한 회수 → 고아 지오펜스 정리
            return Result.success()
        }

        // API 29+에서 addGeofences는 백그라운드 위치가 필요하다. 포그라운드-온리 사용자는
        // 재시도해도 절대 성공하지 않으므로 무한 루프 대신 스탠드다운한다 (§6.4).
        val bgGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        if (!bgGranted) {
            reseedService.standDown(cause) // 로그 포함
            return Result.success() // 재시도 무의미 — 대시보드(§4.3)가 안내
        }

        val here = locationProvider.currentOrLast()
        if (here == null) {
            runLogDao.insert(
                EngineRunLogEntity(at = clock.millis(), cause = cause.name, result = "NO_LOCATION", registeredCount = 0, note = null),
            )
            return Result.retry() // §6.4 위치 미취득 → 백오프 재시도
        }

        val result = reseedService.reseed(cause, here)
        if (result == ReseedResult.SKIPPED_DEBOUNCE && cause == ReseedCause.SENTINEL_EXIT) {
            // EXIT는 재신호가 없다 — 디바운스 창 이후로 스스로 재예약 (§6.2)
            runNow(applicationContext, ReseedCause.SENTINEL_EXIT, delayMs = EngineParams.RESEED_MIN_INTERVAL_MS)
        }
        return when (result) {
            ReseedResult.FAILED -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val UNIQUE_ONESHOT = "reseed_now" // 강한 원인: ITEM_CHANGE/BOOT/SENTINEL_EXIT/RETRY
        private const val UNIQUE_OPPORTUNISTIC = "reseed_opportunistic" // APP_OPEN 전용
        private const val UNIQUE_PERIODIC = "reseed_health_check"
        const val KEY_CAUSE = "cause"

        /** delayMs: ITEM_CHANGE 코얼레싱(§6.2)에 EngineParams.ITEM_CHANGE_COALESCE_MS 전달 */
        fun runNow(context: Context, cause: ReseedCause, delayMs: Long = 0L) {
            // APP_OPEN은 기회적 실행 — 대기 중인 강한 작업(코얼레싱 중인 ITEM_CHANGE, RETRY 체인 등)을
            // 절대 대체하지 않는다 (§6.2). 그 외 원인은 서로를 대체해도 안전(REPLACE)하다.
            val (name, policy) = when (cause) {
                ReseedCause.APP_OPEN -> UNIQUE_OPPORTUNISTIC to ExistingWorkPolicy.KEEP
                else -> UNIQUE_ONESHOT to ExistingWorkPolicy.REPLACE
            }
            WorkManager.getInstance(context).enqueueUniqueWork(
                name,
                policy,
                OneTimeWorkRequestBuilder<ReseedWorker>()
                    .setInputData(workDataOf(KEY_CAUSE to cause.name))
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    // §6.2 RETRY: 15분 시작 지수 백오프 (WorkManager 표준 — 15m/30m/1h/… 스펙 취지 충족)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build(),
            )
        }

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReseedWorker>(EngineParams.HEALTH_CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
                    .setInputData(workDataOf(KEY_CAUSE to ReseedCause.PERIODIC.name))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build(),
            )
        }
    }
}
