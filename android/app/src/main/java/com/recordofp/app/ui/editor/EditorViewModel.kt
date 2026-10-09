package com.recordofp.app.ui.editor

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordofp.app.data.location.LocationProvider
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.EngineParams
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.PoiResolution
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PickedPlace(val name: String, val kakaoId: String, val point: GeoPoint)

/** 장소 검색 실패 원인 (F4) — UI가 원인별 안내 문구를 고른다 */
enum class PlaceSearchError { NO_LOCATION, NETWORK, SERVICE }

data class EditorUiState(
    val title: String = "",
    val memo: String = "",
    val selectedCategoryIds: Set<String> = emptySet(),
    val brandKeywords: List<String> = emptyList(),
    val place: PickedPlace? = null,
    val placeQuery: String = "",
    val placeResults: List<PoiCandidate> = emptyList(),
    val placeSearchError: PlaceSearchError? = null,
    val saved: Boolean = false,
    /** null이면 새 기록, 값이 있으면 편집 중인 기존 기록의 id (§3.1 CRUD 갭) */
    val editingId: Long? = null,
) {
    val canSave: Boolean
        get() = title.isNotBlank() &&
            (selectedCategoryIds.isNotEmpty() || brandKeywords.isNotEmpty() || place != null)
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val poiRepository: PoiRepository,
    private val locationProvider: LocationProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state

    /** 편집 대상의 원래 생성 시각 — save()에서 upsert()에 그대로 넘겨줘야 새 것으로 취급되지 않는다 */
    private var createdAt: Long = 0L
    private var searchJob: Job? = null

    init {
        val reminderId: Long = savedStateHandle.get<Long>(ARG_REMINDER_ID) ?: -1L
        if (reminderId >= 0) {
            viewModelScope.launch {
                repository.byId(reminderId)?.let(::applyLoaded)
            }
        }
    }

    private fun applyLoaded(reminder: Reminder) {
        createdAt = reminder.createdAt
        _state.update {
            it.copy(
                editingId = reminder.id,
                title = reminder.title,
                memo = reminder.memo ?: "",
                selectedCategoryIds = reminder.triggers
                    .filter { t -> t.type == TriggerType.CATEGORY }
                    .mapNotNull { t -> t.categoryId }
                    .toSet(),
                brandKeywords = reminder.triggers
                    .filter { t -> t.type == TriggerType.BRAND }
                    .mapNotNull { t -> t.brandKeyword },
                place = reminder.triggers.firstOrNull { t -> t.type == TriggerType.PLACE }?.let { t ->
                    PickedPlace(
                        name = t.placeName ?: "",
                        kakaoId = t.placeKakaoId ?: "",
                        point = t.placePoint ?: GeoPoint(0.0, 0.0),
                    )
                },
            )
        }
    }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    fun onMemoChange(v: String) = _state.update { it.copy(memo = v) }
    fun toggleCategory(id: String) = _state.update {
        val s = it.selectedCategoryIds
        it.copy(selectedCategoryIds = if (id in s) s - id else s + id)
    }
    fun addBrand(keyword: String) {
        val k = keyword.trim()
        if (k.isEmpty()) return
        _state.update { if (k in it.brandKeywords) it else it.copy(brandKeywords = it.brandKeywords + k) }
    }
    fun removeBrand(keyword: String) = _state.update { it.copy(brandKeywords = it.brandKeywords - keyword) }
    fun onPlaceQueryChange(v: String) =
        _state.update { it.copy(placeQuery = v, placeSearchError = null) }
    fun pickPlace(place: PickedPlace) = _state.update { it.copy(place = place, placeResults = emptyList(), placeQuery = "") }
    fun clearPlace() = _state.update { it.copy(place = null) }

    fun searchPlace() {
        val query = _state.value.placeQuery.trim()
        if (query.isEmpty()) return
        // T12(NearbyViewModel.load)와 동일한 취소-재시작 가드 — 연타 시 먼저 보낸 검색이 늦게 도착해
        // 최신 결과를 덮어쓰는 경쟁을 막는다 (M8).
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val here = locationProvider.currentOrLast()
            if (here == null) {
                // F4: 원인 로깅 — F0(401을 네트워크로 오인) 같은 진단 지연 재발 방지
                Log.w(TAG, "장소 검색 실패: 위치 미취득")
                _state.update { it.copy(placeSearchError = PlaceSearchError.NO_LOCATION) }
                return@launch
            }
            // runCatching은 Throwable을 전부 잡아 CancellationException까지 삼키므로 쓰지 않는다
            // (구조적 동시성 위반 — 컨트롤러 ruling). 취소는 재던지고 나머지만 실패로 처리한다.
            try {
                val results = poiRepository.search(
                    resolution = PoiResolution.KEYWORD,
                    query = query,
                    center = here,
                    radiusM = EngineParams.PLACE_SEARCH_RADIUS_M,
                    maxResults = EngineParams.PLACE_SEARCH_MAX_RESULTS,
                )
                _state.update { it.copy(placeResults = results, placeSearchError = null) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Exception) {
                // HTTP 오류 응답(401 쿼터/키 문제 등)은 서비스 문제 — 네트워크 안내로 오인시키지 않는다 (F4)
                val error = when (t) {
                    is retrofit2.HttpException -> PlaceSearchError.SERVICE
                    is java.io.IOException -> PlaceSearchError.NETWORK
                    else -> PlaceSearchError.SERVICE
                }
                Log.w(TAG, "장소 검색 실패: ${error.name}", t)
                _state.update { it.copy(placeSearchError = error) }
            }
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        val triggers = buildList {
            s.selectedCategoryIds.forEach { add(TriggerSpec(type = TriggerType.CATEGORY, categoryId = it)) }
            s.brandKeywords.forEach { add(TriggerSpec(type = TriggerType.BRAND, brandKeyword = it)) }
            s.place?.let {
                add(
                    TriggerSpec(
                        type = TriggerType.PLACE, placeName = it.name,
                        placeKakaoId = it.kakaoId, placePoint = it.point,
                    ),
                )
            }
        }
        viewModelScope.launch {
            repository.upsert(
                Reminder(
                    id = s.editingId ?: 0L,
                    title = s.title.trim(),
                    memo = s.memo.trim().ifEmpty { null },
                    createdAt = createdAt,
                    updatedAt = 0,
                    triggers = triggers,
                ),
            )
            _state.update { it.copy(saved = true) }
        }
    }

    /** 편집 모드에서 기록 삭제. 저장과 동일하게 saved 플래그를 재사용해 화면을 닫는다 (§3.1 CRUD 갭) */
    fun delete() {
        val id = _state.value.editingId ?: return
        viewModelScope.launch {
            repository.delete(id)
            _state.update { it.copy(saved = true) }
        }
    }

    companion object {
        const val ARG_REMINDER_ID = "reminderId"
        private const val TAG = "RecordOfP"
    }
}
