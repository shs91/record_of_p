package com.recordofp.app.platform.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.platform.geofence.GeofenceController
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 재배치 실행자 (설계 §6.2~6.4).
 * 호출 경로: 센티널 EXIT / 항목 변경 / 부팅·업데이트 / 앱 진입 / 6시간 헬스체크 / 실패 백오프.
 */
@HiltWorker
class ReseedWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val reminderRepository: ReminderRepository,
    private val poiRepository: PoiRepository,
    private val geofenceController: GeofenceController,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // TODO(v1): §6.3~6.4 —
        //  1. 디바운스 확인 (RESEED_MIN_INTERVAL_MS)
        //  2. 위치 취득: getCurrentLocation(BALANCED) → 실패 시 lastLocation → 없으면 RETRY
        //  3. activeTriggers()를 TriggerCandidates로 해석 (PoiRepository 조회, 실패 시 기존 등록 유지 + Result.retry())
        //  4. ReseedPlanner.plan() → GeofenceController.applyPlan()
        //  5. EngineRunLog 기록 (cause = inputData의 KEY_CAUSE)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_ONESHOT = "reseed_now"
        private const val UNIQUE_PERIODIC = "reseed_health_check"
        const val KEY_CAUSE = "cause"

        fun runNow(context: Context, cause: String) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONESHOT,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReseedWorker>()
                    .setInputData(workDataOf(KEY_CAUSE to cause))
                    .build(),
            )
        }

        /** 최후 방어선: 6시간 주기 헬스체크 (설계 §6.2 PERIODIC) */
        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReseedWorker>(6, TimeUnit.HOURS)
                    .setInputData(workDataOf(KEY_CAUSE to "PERIODIC"))
                    .build(),
            )
        }
    }
}
