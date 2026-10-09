package com.recordofp.app.platform

import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import java.time.Clock

/**
 * 리시버 코루틴에서 잡은 예외를 진단 로그로 남긴다 (§4.4, 최종 리뷰 I1).
 * 로그 쓰기마저 실패해도 던지지 않는다 — goAsync 코루틴 밖으로 나간 예외는 프로세스를 죽인다.
 */
internal suspend fun EngineRunLogDao.logReceiverError(clock: Clock, cause: String, error: Exception) {
    try {
        insert(
            EngineRunLogEntity(
                at = clock.millis(), cause = cause, result = "ERROR", registeredCount = 0,
                note = "${error.javaClass.simpleName}: ${error.message}",
            ),
        )
    } catch (_: Exception) {
    }
}
