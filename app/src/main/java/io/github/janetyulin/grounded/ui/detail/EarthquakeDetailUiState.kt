package io.github.janetyulin.grounded.ui.detail

import io.github.janetyulin.grounded.domain.model.AlertLevel
import io.github.janetyulin.grounded.domain.model.Severity
import io.github.janetyulin.grounded.ui.common.LoadErrorUiState

/**
 * Single UI State for the earthquake detail screen.
 */
sealed interface EarthquakeDetailUiState {
    data object Loading : EarthquakeDetailUiState
    data class Error(val error: LoadErrorUiState) : EarthquakeDetailUiState
    data class Loaded(val earthquake: EarthquakeDetailsUiState) : EarthquakeDetailUiState
}

/**
 * @param timeText Local date and time of the event, already formatted for the user's locale.
 * @param distanceKm From the device; `null` when the location isn't available.
 */
data class EarthquakeDetailsUiState(
    val title: String,
    val place: String,
    val magnitude: Double,
    val severity: Severity,
    val timeText: String,
    val distanceKm: Int?,
    val depthKm: Double,
    val latitude: Double,
    val longitude: Double,
    val reviewed: Boolean,
    val feltReports: Int?,
    val alertLevel: AlertLevel?,
    val tsunamiFlag: Boolean,
    val url: String
)

sealed interface EarthquakeDetailUiEvent {
    data object Retry : EarthquakeDetailUiEvent
}
