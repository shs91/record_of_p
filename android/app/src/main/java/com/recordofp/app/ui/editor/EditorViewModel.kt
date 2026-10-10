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
    /** 입력 중인(아직 칩으로 확정하지 않은) 브랜드 — 저장 시 함께 확정된다. 회전에도 남도록 상태에 둔다 (최종 리뷰 I6) */
    val brandInput: String = "",
    val place: PickedPlace? = null,
    val placeQuery: String = "",
    val placeResults: List<PoiCandidate> = emptyList(),
    val placeSearchError: PlaceSearchError? = null,
    /** 저장·삭제 진행 중 — 연타로 두 번 처리되지 않게 한다 (최종 리뷰 I6) */
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
    /** null이면 새 기록, 값이 있으면 편집 중인 기존 기록의 id (§3.1 CRUD 갭) */
    val editingId: Long? = null,
) {
    val canSave: Boolean
        get() = !saving && title.isNotBlank() &&
            (selectedCategoryIds.isNotEmpty() || brandKeywords.isNotEmpty() || brandInput.isNotBlank() || place != null)

    /** 입력 중인 브랜드를 칩으로 확정한다 (앞뒤 공백 제거, 중복 무시) */
    fun withBrandInputCommitted(): EditorUiState {
        val k = brandInput.trim()
        if (k.isEmpty()) return copy(brandInput = "")
        return copy(brandKeywords = if (k in brandKeywords) brandKeywords else brandKeywords + k, brandInput = "")
    }
}

/** 에디터 화면이 부르는 동작 — 뷰모델이 구현하고, 미리보기는 빈 구현을 넘긴다 (디자인 시스템 §5) */
interface EditorActions {
    fun onTitleChange(v: String)
    fun onMemoChange(v: String)
    fun toggleCategory(id: String)
    fun onBrandInputChange(v: String)
    fun commitBrandInput()
    fun removeBrand(keyword: String)
    fun onPlaceQueryChange(v: String)
    fun searchPlace()
    fun pickPlace(place: PickedPlace)
    fun clearPlace()
    fun save()
    fun delete()
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val poiRepository: PoiRepository,
    private val locationProvider: LocationProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), EditorActions {

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

    override fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    override fun onMemoChange(v: String) = _state.update { it.copy(memo = v) }
    override fun toggleCategory(id: String) = _state.update {
        val s = it.selectedCategoryIds
        it.copy(selectedCategoryIds = if (id in s) s - id else s + id)
    }
    override fun onBrandInputChange(v: String) = _state.update { it.copy(brandInput = v) }
    override fun commitBrandInput() = _state.update { it.withBrandInputCommitted() }
    override fun removeBrand(keyword: String) = _state.update { it.copy(brandKeywords = it.brandKeywords - keyword) }
    override fun onPlaceQueryChange(v: String) =
        _state.update { it.copy(placeQuery = v, placeSearchError = null) }
    override fun pickPlace(place: PickedPlace) = _state.update { it.copy(place = place, placeResults = emptyList(), placeQuery = "") }
    override fun clearPlace() = _state.update { it.copy(place = null) }

    override fun searchPlace() {
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

    override fun save() {
        // 입력만 하고 확정하지 않은 브랜드도 저장한다 — 조용히 버리지 않는다 (최종 리뷰 I6)
        val s = _state.value.withBrandInputCommitted()
        if (!s.canSave) return // 저장·삭제 중이면 canSave가 false — 연타 무시
        _state.value = s.copy(saving = true, saveFailed = false)
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
            try {
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
                // saving은 그대로 둔다 — 화면이 닫히기 전까지 다시 눌리지 않게
                _state.update { it.copy(saved = true) }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.w(TAG, "기록 저장 실패", e)
                // 앱을 죽이지 않고 알린다. 입력은 그대로라 다시 저장할 수 있다 (최종 리뷰 I6)
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    /** 편집 모드에서 기록 삭제. 저장과 동일하게 saved 플래그를 재사용해 화면을 닫는다 (§3.1 CRUD 갭) */
    override fun delete() {
        val s = _state.value
        val id = s.editingId ?: return
        if (s.saving) return // 연타 무시
        _state.value = s.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                repository.delete(id)
                _state.update { it.copy(saved = true) }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.w(TAG, "기록 삭제 실패", e)
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    companion object {
        const val ARG_REMINDER_ID = "reminderId"
        private const val TAG = "RecordOfP"
    }
}
