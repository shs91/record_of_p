package com.recordofp.app.platform.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.engine.AlertGroup
import com.recordofp.app.data.engine.GeofenceEventHandler
import com.recordofp.app.data.engine.ReseedService
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.platform.logReceiverError
import com.recordofp.app.platform.notify.NearbyNotifier
import com.recordofp.app.platform.work.ReseedWorker
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 지오펜스 전이 이벤트 진입점 (스펙 §6.5). 처리 순서·예외 규칙은 runFenceEvent (최종 리뷰 I1) */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var handler: GeofenceEventHandler
    @Inject lateinit var notifier: NearbyNotifier
    @Inject lateinit var reseedService: ReseedService
    @Inject lateinit var runLogDao: EngineRunLogDao
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            // 위치가 꺼지면 OS가 이 앱의 펜스를 전부 지우고 이 오류를 보낸다. 전체 재등록을 예약한다 —
            // 펜스 소실 표시를 먼저 남긴다. 위치가 다시 켜질 때까지 워커는 NO_LOCATION/FAILED로 백오프 재시도한다 (검토 B1)
            if (event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
                markFencesLostThenReseed(context, reseedService, ReseedCause.FENCE_LOST)
            }
            return
        }
        val ids = event.triggeringGeofences?.map { it.requestId } ?: return
        val triggeringPoint = event.triggeringLocation?.let { GeoPoint(it.latitude, it.longitude) }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 처리 순서·예외 규칙은 runFenceEvent(JVM 테스트) — 여기는 Android 접착만 (최종 리뷰 I1)
                runFenceEvent(
                    ids,
                    object : FenceEventSteps {
                        override suspend fun evaluate(ids: List<String>) = handler.onFenceEvent(ids, triggeringPoint)
                        override fun scheduleSentinelReseed() = ReseedWorker.runNow(context, ReseedCause.SENTINEL_EXIT)
                        override fun show(group: AlertGroup) = notifier.show(group)
                        override suspend fun recordShown(group: AlertGroup) = handler.recordShown(group)
                        override suspend fun recordNotShown(group: AlertGroup) = handler.recordNotShown(group)
                        override suspend fun logError(error: Exception) =
                            runLogDao.logReceiverError(clock, GeofenceEventHandler.LOG_CAUSE, error)
                    },
                )
            } finally {
                pending.finish()
            }
        }
    }
}
