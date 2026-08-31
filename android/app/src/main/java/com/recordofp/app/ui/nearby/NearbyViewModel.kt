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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NearbyGroup(val matchKey: String, val pois: List<PoiCandidate>)

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
                        if (pois.isEmpty()) null else NearbyGroup(req.matchKey, pois)
                    }
                    is PlaceRequest -> NearbyGroup(
                        req.matchKey,
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
