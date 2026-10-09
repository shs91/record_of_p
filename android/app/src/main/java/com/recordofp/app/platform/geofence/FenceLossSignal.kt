package com.recordofp.app.platform.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import com.recordofp.app.data.engine.ReseedService
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.platform.work.ReseedWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * OS 펜스가 사라졌다는 신호(BOOT·FENCE_LOST)를 받았을 때: 펜스 소실 표시를 먼저 남기고 재배치를 예약한다.
 * reseed_now 큐는 REPLACE라서, 예약한 작업이 시작되기 전에 다른 원인(ITEM_CHANGE 등)으로 대체될 수 있다.
 * 표시를 먼저 남겨 두면 어떤 원인의 재배치가 먼저 돌든 전체 재등록한다 (검토 B1, 묶음 A 최종 리뷰).
 * markFencesLost는 서비스 mutex를 거치므로 실행 중인 재배치가 표시를 지우는 일과 겹치지 않는다.
 * 표시에 실패해도 예약은 한다 — 워커 첫 시도가 다시 표시한다.
 */
internal fun BroadcastReceiver.markFencesLostThenReseed(
    context: Context,
    reseedService: ReseedService,
    cause: ReseedCause,
) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            reseedService.markFencesLost()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.w("RecordOfP", "펜스 소실 표시 실패 — 워커 첫 시도가 다시 표시한다", e)
        } finally {
            ReseedWorker.runNow(context, cause)
            pending.finish()
        }
    }
}
