package com.recordofp.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.recordofp.app.domain.engine.EngineParams
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 알림 정책 설정값 (스펙 §4.5) — §6.5 필터 체인 정책으로는 [com.recordofp.app.data.engine.StoreGatePolicyProvider]가 변환한다.
 * 기본값은 EngineParams에서만 가져온다 — 리터럴 중복 금지 (§10.2, 최종 리뷰 M1)
 */
data class NotificationPolicySettings(
    // 선택지 1/4/12/24 (§4.5)
    val cooldownHours: Int = Duration.ofMillis(EngineParams.COOLDOWN_PER_ITEM_MS).toHours().toInt(),
    // 선택지 5/10/20/0(0=무제한)
    val dailyCapTotal: Int = EngineParams.DAILY_CAP_TOTAL,
    val quietEnabled: Boolean = true,
    val quietStartMinute: Int = EngineParams.QUIET_START_MINUTE,
    val quietEndMinute: Int = EngineParams.QUIET_END_MINUTE,
)

interface SettingsStore {
    val onboardingDone: Flow<Boolean>
    suspend fun setOnboardingDone()

    val policy: Flow<NotificationPolicySettings>
    suspend fun updatePolicy(transform: (NotificationPolicySettings) -> NotificationPolicySettings)
}

@Singleton
class DataStoreSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsStore {
    private val onboardingKey = booleanPreferencesKey("onboarding_done")
    private val cooldownKey = intPreferencesKey("policy_cooldown_hours")
    private val capKey = intPreferencesKey("policy_daily_cap")
    private val quietEnabledKey = booleanPreferencesKey("policy_quiet_enabled")
    private val quietStartKey = intPreferencesKey("policy_quiet_start")
    private val quietEndKey = intPreferencesKey("policy_quiet_end")

    override val onboardingDone: Flow<Boolean> = dataStore.data.map { it[onboardingKey] ?: false }

    override suspend fun setOnboardingDone() {
        dataStore.edit { it[onboardingKey] = true }
    }

    private val defaults = NotificationPolicySettings()

    private fun read(p: Preferences) = NotificationPolicySettings(
        cooldownHours = p[cooldownKey] ?: defaults.cooldownHours,
        dailyCapTotal = p[capKey] ?: defaults.dailyCapTotal,
        quietEnabled = p[quietEnabledKey] ?: defaults.quietEnabled,
        quietStartMinute = p[quietStartKey] ?: defaults.quietStartMinute,
        quietEndMinute = p[quietEndKey] ?: defaults.quietEndMinute,
    )

    override val policy: Flow<NotificationPolicySettings> = dataStore.data.map { read(it) }

    override suspend fun updatePolicy(transform: (NotificationPolicySettings) -> NotificationPolicySettings) {
        dataStore.edit { p ->
            val next = transform(read(p))
            p[cooldownKey] = next.cooldownHours
            p[capKey] = next.dailyCapTotal
            p[quietEnabledKey] = next.quietEnabled
            p[quietStartKey] = next.quietStartMinute
            p[quietEndKey] = next.quietEndMinute
        }
    }
}
