package io.github.janetyulin.grounded.ui.common

import io.github.janetyulin.grounded.domain.model.AlertLevel
import io.github.janetyulin.grounded.domain.model.Severity

/**
 * One earthquake as Map and List show it, so both views present the same information.
 *
 * @param distanceKm From the device; `null` when the location isn't available.
 */
data class EarthquakeUiState(
    val id: String,
    val place: String,
    val magnitude: Double,
    val severity: Severity,
    val latitude: Double,
    val longitude: Double,
    val depthKm: Double,
    val minutesAgo: Long,
    val distanceKm: Int?,
    val alertLevel: AlertLevel?,
    val tsunamiFlag: Boolean
)

/**
 * The earthquake area of Map and List.
 */
sealed interface FeedContent {
    /** Nothing cached yet and the first download hasn't finished. */
    data object Loading : FeedContent

    /** Nothing cached and the download failed; the only state without data to show. */
    data class Error(val error: LoadErrorUiState) : FeedContent

    /**
     * Cached or fresh data. [refreshError] is set when the latest refresh failed, so the screen
     * keeps the data and says how old it is.
     */
    data class Loaded(
        val earthquakes: List<EarthquakeUiState>,
        val updatedMinutesAgo: Long,
        val refreshError: LoadErrorUiState?
    ) : FeedContent
}

/** Why loading failed, as far as the user needs to know. */
enum class LoadErrorUiState { NETWORK, SERVER, UNKNOWN }

/** Whether distances can be shown, and if not, what the user can do about it. */
enum class LocationUiState { CHECKING, AVAILABLE, PERMISSION_REQUIRED, UNAVAILABLE }
