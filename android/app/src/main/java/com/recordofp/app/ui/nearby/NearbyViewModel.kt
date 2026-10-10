package com.recordofp.app.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordofp.app.data.location.LocationProvider
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.PlaceRequest
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.engine.QueryRequest
import com.recordofp.app.domain.engine.TriggerResolver
import com.recordofp.app.domain.model.distanceMeters
import com.recordofp.app.ui.common.TriggerVisual
import com.recordofp.app.ui.common.categoryVisual
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 주변 보기 그룹 — 트리거 하나(matchKey)와 그 근처 지점들. visual은 그룹 머리에 그릴 타일·이름 (개편안 2 §2) */
data class NearbyGroup(val matchKey: String, val visual: TriggerVisual, val pois: List<PoiCandidate>)

data class NearbyUiState(
    val loading: Boolean = true,
    val locationUnavailable: Boolean = false,
    val groups: List<NearbyGroup> = emptyList(),
)

@HiltViewModel
class NearbyViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val poiRepository: PoiRepository,
    private val resolver: TriggerResolver,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(NearbyUiState())
    val state: StateFlow<NearbyUiState> = _state

    private var loadJob: Job? = null

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, locationUnavailable = false) }
            val here = locationProvider.currentOrLast()
            if (here == null) {
                _state.update { it.copy(loading = false, locationUnavailable = true) }
                return@launch
            }
            val groups = resolver.resolve(repository.activeTriggers()).mapNotNull { req ->
                when (req) {
                    is QueryRequest -> {
                        val pois = try {
                            poiRepository.search(req.resolution, req.query, here, maxResults = 5)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                        if (pois.isEmpty()) null else NearbyGroup(req.matchKey, req.visual(), pois)
                    }
                    is PlaceRequest -> NearbyGroup(
                        req.matchKey,
                        TriggerVisual.Place(req.name.orEmpty()),
                        listOf(
                            PoiCandidate(
                                id = req.matchKey, name = req.name ?: "", point = req.point,
                                distanceM = distanceMeters(here, req.point),
                            ),
                        ),
                    )
                }
            }
            _state.update { it.copy(loading = false, groups = groups) }
        }
    }
}

/** 그룹 머리에 그릴 트리거 — 브랜드는 matchKey(소문자)가 아니라 입력한 검색어 그대로 보인다 */
private fun QueryRequest.visual(): TriggerVisual =
    if (matchKey.startsWith("cat:")) categoryVisual(matchKey.removePrefix("cat:")) else TriggerVisual.BrandKeyword(query)
