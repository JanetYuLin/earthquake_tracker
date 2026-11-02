package io.github.janetyulin.grounded.ui.common

import io.github.janetyulin.grounded.data.model.Earthquake
import io.github.janetyulin.grounded.data.model.EarthquakeFeedState
import io.github.janetyulin.grounded.data.model.UserLocation
import io.github.janetyulin.grounded.domain.model.DataError
import io.github.janetyulin.grounded.domain.model.GeoPoint
import io.github.janetyulin.grounded.domain.model.Severity
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

// Data-to-UI mappers shared by the Map and List ViewModels. Only ViewModels call these, so this is
// the one file under ui/common that imports from data/.

internal fun Earthquake.toUiState(now: Instant, here: GeoPoint?) = EarthquakeUiState(
    id = id,
    place = place,
    magnitude = magnitude,
    severity = Severity.fromMagnitude(magnitude),
    latitude = latitude,
    longitude = longitude,
    depthKm = depthKm,
    minutesAgo = minutesBetween(Instant.ofEpochMilli(timeMillis), now),
    distanceKm = here?.distanceKmTo(location)?.roundToInt(),
    alertLevel = alertLevel,
    tsunamiFlag = tsunamiFlag
)

/**
 * @param arrange Ordering or filtering applied to the mapped earthquakes, e.g. the List's sort.
 */
internal fun EarthquakeFeedState.toFeedContent(
    now: Instant,
    here: GeoPoint?,
    arrange: (List<EarthquakeUiState>) -> List<EarthquakeUiState> = { it }
): FeedContent {
    val cached = feed
    return when {
        cached != null -> FeedContent.Loaded(
            earthquakes = arrange(cached.earthquakes.map { it.toUiState(now, here) }),
            updatedMinutesAgo = minutesBetween(Instant.ofEpochMilli(cached.fetchedAtMillis), now),
            refreshError = refreshError?.toUiState()
        )

        refreshError != null -> FeedContent.Error(refreshError.toUiState())

        else -> FeedContent.Loading
    }
}

internal fun DataError.toUiState(): LoadErrorUiState = when (this) {
    is DataError.Network -> LoadErrorUiState.NETWORK
    is DataError.Server -> LoadErrorUiState.SERVER
    is DataError.Unknown -> LoadErrorUiState.UNKNOWN
}

internal fun Throwable.toLoadErrorUiState(): LoadErrorUiState =
    (this as? DataError)?.toUiState() ?: LoadErrorUiState.UNKNOWN

internal fun UserLocation.toUiState(): LocationUiState = when (this) {
    UserLocation.Unknown -> LocationUiState.CHECKING
    is UserLocation.Available -> LocationUiState.AVAILABLE
    UserLocation.PermissionRequired -> LocationUiState.PERMISSION_REQUIRED
    UserLocation.Unavailable -> LocationUiState.UNAVAILABLE
}

private fun minutesBetween(from: Instant, to: Instant): Long = Duration.between(from, to).toMinutes().coerceAtLeast(0)
