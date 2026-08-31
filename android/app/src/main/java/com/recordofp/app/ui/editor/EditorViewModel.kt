package com.recordofp.app.ui.editor

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PickedPlace(val name: String, val kakaoId: String, val point: GeoPoint)

data class EditorUiState(
    val title: String = "",
    val memo: String = "",
    val selectedCategoryIds: Set<String> = emptySet(),
    val brandKeywords: List<String> = emptyList(),
    val place: PickedPlace? = null,
    val placeQuery: String = "",
    val placeResults: List<PoiCandidate> = emptyList(),
    val placeSearchFailed: Boolean = false,
    val saved: Boolean = false,
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
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state

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
    fun onPlaceQueryChange(v: String) = _state.update { it.copy(placeQuery = v, placeSearchFailed = false) }
    fun pickPlace(place: PickedPlace) = _state.update { it.copy(place = place, placeResults = emptyList(), placeQuery = "") }
    fun clearPlace() = _state.update { it.copy(place = null) }

    fun searchPlace() {
        val query = _state.value.placeQuery.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            val here = locationProvider.currentOrLast()
            if (here == null) {
                _state.update { it.copy(placeSearchFailed = true) }
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
                _state.update { it.copy(placeResults = results, placeSearchFailed = false) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Exception) {
                _state.update { it.copy(placeSearchFailed = true) }
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
                Reminder(title = s.title.trim(), memo = s.memo.trim().ifEmpty { null }, createdAt = 0, updatedAt = 0, triggers = triggers),
            )
            _state.update { it.copy(saved = true) }
        }
    }
}
