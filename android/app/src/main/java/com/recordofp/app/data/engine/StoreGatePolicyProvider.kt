package com.recordofp.app.data.engine

import com.recordofp.app.data.repo.SettingsStore
import com.recordofp.app.domain.engine.NotificationGate
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** 설정 저장소의 값을 필터 체인 정책으로 변환 (스펙 §4.5 → §6.5) */
@Singleton
class StoreGatePolicyProvider @Inject constructor(
    private val settings: SettingsStore,
) : GatePolicyProvider {
    override suspend fun policy(): NotificationGate.Policy {
        val s = settings.policy.first()
        return NotificationGate.Policy(
            cooldownPerItem = Duration.ofHours(s.cooldownHours.toLong()),
            dailyCapTotal = if (s.dailyCapTotal <= 0) Int.MAX_VALUE else s.dailyCapTotal,
            quietStartMinute = if (s.quietEnabled) s.quietStartMinute else 0,
            quietEndMinute = if (s.quietEnabled) s.quietEndMinute else 0,
        )
    }
}
