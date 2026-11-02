package io.github.janetyulin.grounded.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.janetyulin.grounded.core.coroutines.WhileUiSubscribed
import io.github.janetyulin.grounded.data.model.pointOrNull
import io.github.janetyulin.grounded.data.repository.EarthquakeRepository
import io.github.janetyulin.grounded.data.repository.LocationRepository
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepository
import io.github.janetyulin.grounded.ui.common.FeedContent
import io.github.janetyulin.grounded.ui.common.toFeedContent
import io.github.janetyulin.grounded.ui.common.toUiState
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Map screen: the selected period's worldwide M2.5+ earthquakes, the same data the
 * List shows, with distances from the device when its location is known.
 *
 * Fetching is owned by the foreground poller (which also reacts to a period change); this ViewModel
 * observes the cached feed and only refreshes on an explicit user request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val earthquakeRepository: EarthquakeRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val locationRepository: LocationRepository,
    private val clock: Clock
) : ViewModel() {

    private val selectedId = MutableStateFlow<String?>(null)
    private val camera = MutableStateFlow<MapCameraUiState?>(null)
    private val isRefreshing = MutableStateFlow(false)

    private val period = preferencesRepository.preferences.map { it.selectedPeriod }.distinctUntilChanged()
    private val feed = period.flatMapLatest { current -> earthquakeRepository.feed(current).map { current to it } }

    val uiState: StateFlow<MapUiState> = combine(
        feed,
        locationRepository.location,
        selectedId,
        camera,
        isRefreshing
    ) { (currentPeriod, feedState), location, selected, cameraState, refreshing ->
        val here = location.pointOrNull
        val content = feedState.toFeedContent(clock.instant(), here)
        MapUiState(
            period = currentPeriod,
            content = content,
            location = location.toUiState(),
            userLocation = here,
            selected = (content as? FeedContent.Loaded)?.earthquakes?.firstOrNull { it.id == selected },
            isRefreshing = refreshing,
            camera = cameraState
        )
    }.stateIn(
        scope = viewModelScope,
        started = WhileUiSubscribed,
        initialValue = MapUiState()
    )

    fun onEvent(event: MapUiEvent) {
        when (event) {
            is MapUiEvent.SelectPeriod -> {
                selectedId.value = null
                viewModelScope.launch { preferencesRepository.setSelectedPeriod(event.period) }
            }

            is MapUiEvent.SelectEarthquake -> selectedId.value = event.id

            is MapUiEvent.DismissSelection -> selectedId.value = null

            is MapUiEvent.CameraIdle -> camera.value = event.camera

            is MapUiEvent.Refresh -> refresh()

            is MapUiEvent.CheckLocation -> viewModelScope.launch { locationRepository.refresh() }
        }
    }

    private fun refresh() {
        // Claim the flag before suspending so a second tap can't start a parallel refresh.
        if (isRefreshing.value) return
        isRefreshing.value = true
        viewModelScope.launch {
            try {
                earthquakeRepository.refresh(period.first())
            } finally {
                isRefreshing.value = false
            }
        }
    }
}
