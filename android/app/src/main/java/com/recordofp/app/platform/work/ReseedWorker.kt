package com.recordofp.app.platform.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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

        val fineGranted = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            // 재시도 무의미 — 권한은 대시보드(§4.3)가 사용자에게 알린다
            runLogDao.insert(
                EngineRunLogEntity(at = clock.millis(), cause = cause.name, result = "NO_PERMISSION", registeredCount = 0, note = null),
            )
            return Result.success()
        }

        val here = locationProvider.currentOrLast()
        if (here == null) {
            runLogDao.insert(
                EngineRunLogEntity(at = clock.millis(), cause = cause.name, result = "NO_LOCATION", registeredCount = 0, note = null),
            )
            return Result.retry() // §6.4 위치 미취득 → 백오프 재시도
        }

        return when (reseedService.reseed(cause, here)) {
            ReseedResult.FAILED -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val UNIQUE_ONESHOT = "reseed_now"
        private const val UNIQUE_PERIODIC = "reseed_health_check"
        const val KEY_CAUSE = "cause"

        /** delayMs: ITEM_CHANGE 코얼레싱(§6.2)에 EngineParams.ITEM_CHANGE_COALESCE_MS 전달 */
        fun runNow(context: Context, cause: ReseedCause, delayMs: Long = 0L) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONESHOT,
                ExistingWorkPolicy.REPLACE,
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
