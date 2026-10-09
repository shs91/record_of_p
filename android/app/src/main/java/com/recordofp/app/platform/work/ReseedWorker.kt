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
import com.recordofp.app.data.engine.ReseedService
import com.recordofp.app.domain.engine.EngineParams
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.domain.model.GeoPoint
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

        // 분기 판단은 runReseedWork(JVM 테스트) — 여기는 Android 접착만
        val steps = object : ReseedWorkSteps {
            override suspend fun markFencesLost() = reseedService.markFencesLost()
            override suspend fun pruneLogs() =
                runLogDao.pruneOlderThan(clock.millis() - EngineParams.RUN_LOG_RETENTION_MS)
            override fun missingPermissions() = missingLocationPermissions()
            override suspend fun standDown(cause: ReseedCause, missing: List<String>) {
                reseedService.standDown(cause, note = missing.joinToString { it.substringAfterLast('.') })
            }
            override suspend fun currentLocation() = locationProvider.currentOrLast()
            override suspend fun logNoLocation(cause: ReseedCause) = runLogDao.insert(
                EngineRunLogEntity(at = clock.millis(), cause = cause.name, result = "NO_LOCATION", registeredCount = 0, note = null),
            )
            override suspend fun reseed(cause: ReseedCause, here: GeoPoint) = reseedService.reseed(cause, here)
            override fun scheduleSentinelRetry(delayMs: Long) =
                runNow(applicationContext, ReseedCause.SENTINEL_EXIT, delayMs = delayMs)
        }
        return when (runReseedWork(cause, runAttemptCount, steps)) {
            ReseedWorkResult.SUCCESS -> Result.success()
            ReseedWorkResult.RETRY -> Result.retry()
        }
    }

    /** 지오펜싱에 필요한 위치 권한 중 없는 것. API 29+는 백그라운드 위치가 필수다 (검토 B3) */
    private fun missingLocationPermissions(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }.filter {
        ContextCompat.checkSelfPermission(applicationContext, it) != PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val UNIQUE_ONESHOT = "reseed_now" // 강한 원인: ITEM_CHANGE/BOOT/FENCE_LOST/SENTINEL_EXIT/RETRY
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
                // UPDATE: 주기를 튜닝하면(EngineParams) 앱 업데이트 뒤 다음 실행부터 반영된다. KEEP은 옛 주기를 영원히 유지한다 (최종 리뷰 M2)
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ReseedWorker>(EngineParams.HEALTH_CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
                    .setInputData(workDataOf(KEY_CAUSE to ReseedCause.PERIODIC.name))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build(),
            )
        }
    }
}
