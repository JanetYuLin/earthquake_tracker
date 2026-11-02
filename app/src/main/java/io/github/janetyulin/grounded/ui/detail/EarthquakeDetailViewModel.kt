package io.github.janetyulin.grounded.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.janetyulin.grounded.core.coroutines.WhileUiSubscribed
import io.github.janetyulin.grounded.data.model.Earthquake
import io.github.janetyulin.grounded.data.model.pointOrNull
import io.github.janetyulin.grounded.data.repository.EarthquakeRepository
import io.github.janetyulin.grounded.data.repository.LocationRepository
import io.github.janetyulin.grounded.domain.model.GeoPoint
import io.github.janetyulin.grounded.domain.model.Severity
import io.github.janetyulin.grounded.ui.common.toLoadErrorUiState
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for one earthquake's details. The id comes from the navigation key via assisted injection.
 * Cached feeds serve the event offline; the distance updates if the device location changes.
 */
@HiltViewModel(assistedFactory = EarthquakeDetailViewModel.Factory::class)
class EarthquakeDetailViewModel @AssistedInject constructor(
    @Assisted private val earthquakeId: String,
    private val repository: EarthquakeRepository,
    locationRepository: LocationRepository,
    private val clock: Clock
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(earthquakeId: String): EarthquakeDetailViewModel
    }

    /** `null` while loading. */
    private val result = MutableStateFlow<Result<Earthquake>?>(null)

    val uiState: StateFlow<EarthquakeDetailUiState> = combine(result, locationRepository.location) { loaded, location ->
        when {
            loaded == null -> EarthquakeDetailUiState.Loading

            else -> loaded.fold(
                onSuccess = { EarthquakeDetailUiState.Loaded(it.toDetailsUiState(location.pointOrNull)) },
                onFailure = { EarthquakeDetailUiState.Error(it.toLoadErrorUiState()) }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = WhileUiSubscribed,
        initialValue = EarthquakeDetailUiState.Loading
    )

    init {
        load()
    }

    fun onEvent(event: EarthquakeDetailUiEvent) {
        when (event) {
            is EarthquakeDetailUiEvent.Retry -> load()
        }
    }

    private fun load() {
        result.value = null
        viewModelScope.launch {
            result.value = repository.earthquake(earthquakeId)
        }
    }

    private fun Earthquake.toDetailsUiState(here: GeoPoint?) = EarthquakeDetailsUiState(
        title = title,
        place = place,
        magnitude = magnitude,
        severity = Severity.fromMagnitude(magnitude),
        timeText = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .withZone(clock.zone)
            .format(Instant.ofEpochMilli(timeMillis)),
        distanceKm = here?.distanceKmTo(location)?.roundToInt(),
        depthKm = depthKm,
        latitude = latitude,
        longitude = longitude,
        reviewed = reviewed,
        feltReports = feltReports,
        alertLevel = alertLevel,
        tsunamiFlag = tsunamiFlag,
        url = url
    )
}
