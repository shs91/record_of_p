package com.recordofp.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordofp.app.data.engine.ProtectionReseedTrigger
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.model.Reminder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val protectionTrigger: ProtectionReseedTrigger,
) : ViewModel() {

    val items: StateFlow<List<Reminder>> = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun complete(id: Long) = viewModelScope.launch { repository.complete(id) }

    /** 완료 스낵바의 [실행 취소] (최종 리뷰 I5) */
    fun reactivate(id: Long) = viewModelScope.launch { repository.reactivate(id) }

    /** 화면 재개 시 보호 상태 보고 — 미보호→보호 전이면 기회적 재배치 (F1) */
    fun reportProtection(fullyProtected: Boolean) = protectionTrigger.onSnapshot(fullyProtected)
}
