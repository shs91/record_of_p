package com.recordofp.app.platform.work

import android.content.Context
import com.recordofp.app.data.repo.ReseedRequester
import com.recordofp.app.domain.engine.EngineParams
import com.recordofp.app.domain.engine.ReseedCause
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 30초 지연으로 연속 편집을 코얼레싱 — 유니크 큐(REPLACE)가 마지막 요청만 남긴다 (§6.2) */
@Singleton
class WorkManagerReseedRequester @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReseedRequester {
    override fun requestItemChange() =
        ReseedWorker.runNow(context, ReseedCause.ITEM_CHANGE, delayMs = EngineParams.ITEM_CHANGE_COALESCE_MS)
}
