package io.github.janetyulin.grounded.ui.map

import io.github.janetyulin.grounded.domain.model.GeoPoint
import io.github.janetyulin.grounded.domain.model.TimePeriod
import io.github.janetyulin.grounded.ui.common.EarthquakeUiState
import io.github.janetyulin.grounded.ui.common.FeedContent
import io.github.janetyulin.grounded.ui.common.LocationUiState

/**
 * Single UI State for the Map screen.
 */
data class MapUiState(
    val period: TimePeriod = TimePeriod.DAY,
    val content: FeedContent = FeedContent.Loading,
    val location: LocationUiState = LocationUiState.CHECKING,
    /** Drawn as a "you are here" marker; `null` when the location isn't available. */
    val userLocation: GeoPoint? = null,
    val selected: EarthquakeUiState? = null,
    val isRefreshing: Boolean = false,
    /** Last camera position, restored when returning to the tab; `null` lets the map pick a start. */
    val camera: MapCameraUiState? = null
)

data class MapCameraUiState(val latitude: Double, val longitude: Double, val zoom: Double)

sealed interface MapUiEvent {
    data class SelectPeriod(val period: TimePeriod) : MapUiEvent
    data class SelectEarthquake(val id: String) : MapUiEvent
    data object DismissSelection : MapUiEvent
    data class CameraIdle(val camera: MapCameraUiState) : MapUiEvent
    data object Refresh : MapUiEvent

    /** The screen became visible or the permission dialog closed; re-read the location. */
    data object CheckLocation : MapUiEvent
}
