package com.recordofp.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 알림 정책 설정값 (스펙 §4.5) — §6.5 필터 체인 정책으로는 [com.recordofp.app.data.engine.StoreGatePolicyProvider]가 변환한다. */
data class NotificationPolicySettings(
    val cooldownHours: Int = 4,          // 선택지 1/4/12/24 (§4.5)
    val dailyCapTotal: Int = 10,         // 선택지 5/10/20/0(0=무제한)
    val quietEnabled: Boolean = true,
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 8 * 60,
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

    override val policy: Flow<NotificationPolicySettings> = dataStore.data.map { p ->
        NotificationPolicySettings(
            cooldownHours = p[cooldownKey] ?: 4,
            dailyCapTotal = p[capKey] ?: 10,
            quietEnabled = p[quietEnabledKey] ?: true,
            quietStartMinute = p[quietStartKey] ?: 22 * 60,
            quietEndMinute = p[quietEndKey] ?: 8 * 60,
        )
    }

    override suspend fun updatePolicy(transform: (NotificationPolicySettings) -> NotificationPolicySettings) {
        dataStore.edit { p ->
            val next = transform(
                NotificationPolicySettings(
                    cooldownHours = p[cooldownKey] ?: 4,
                    dailyCapTotal = p[capKey] ?: 10,
                    quietEnabled = p[quietEnabledKey] ?: true,
                    quietStartMinute = p[quietStartKey] ?: 22 * 60,
                    quietEndMinute = p[quietEndKey] ?: 8 * 60,
                ),
            )
            p[cooldownKey] = next.cooldownHours
            p[capKey] = next.dailyCapTotal
            p[quietEnabledKey] = next.quietEnabled
            p[quietStartKey] = next.quietStartMinute
            p[quietEndKey] = next.quietEndMinute
        }
    }
}
