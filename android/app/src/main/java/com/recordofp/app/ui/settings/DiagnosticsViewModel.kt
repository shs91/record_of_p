package com.recordofp.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val runLogDao: EngineRunLogDao,
    private val clock: Clock,
) : ViewModel() {

    // Eagerly: 구독자 없이 buildExport()만 호출돼도(공유 직후 등) 최신 값을 담고 있어야 한다.
    // WhileSubscribed는 최초 구독이 있어야 업스트림 수집을 시작하므로 그 요구를 만족하지 못한다.
    val entries: StateFlow<List<EngineRunLogEntity>> = runLogDao.observeRecent(200)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            runLogDao.pruneOlderThan(clock.millis() - RETENTION_MS)
        }
    }

    /** 진단 로그 수동 내보내기 (§4.4 — 기기 밖 자동 전송 없음, 사용자가 공유할 때만) */
    fun buildExport(): String {
        val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
        return entries.value.joinToString("\n") { e ->
            val t = fmt.format(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()))
            "[$t] ${e.cause} -> ${e.result} (fences=${e.registeredCount})${e.note?.let { " $it" } ?: ""}"
        }
    }

    companion object {
        private const val RETENTION_MS = 14L * 24 * 3600 * 1000
    }
}
