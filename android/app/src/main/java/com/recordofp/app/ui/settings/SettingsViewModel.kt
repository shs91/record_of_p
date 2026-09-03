package com.recordofp.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordofp.app.data.engine.ProtectionReseedTrigger
import com.recordofp.app.data.repo.NotificationPolicySettings
import com.recordofp.app.data.repo.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val protectionTrigger: ProtectionReseedTrigger,
) : ViewModel() {

    /** 시스템 설정에서 돌아온 재개 시점 보고 — 미보호→보호 전이면 기회적 재배치 (F1) */
    fun reportProtection(fullyProtected: Boolean) = protectionTrigger.onSnapshot(fullyProtected)

    val policy: StateFlow<NotificationPolicySettings> = store.policy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationPolicySettings())

    fun setCooldownHours(h: Int) = update { it.copy(cooldownHours = h) }
    fun setDailyCap(n: Int) = update { it.copy(dailyCapTotal = n) }
    fun setQuietEnabled(enabled: Boolean) = update { it.copy(quietEnabled = enabled) }
    fun setQuietRange(startMinute: Int, endMinute: Int) =
        update { it.copy(quietStartMinute = startMinute, quietEndMinute = endMinute) }

    private fun update(transform: (NotificationPolicySettings) -> NotificationPolicySettings) =
        viewModelScope.launch { store.updatePolicy(transform) }
}
