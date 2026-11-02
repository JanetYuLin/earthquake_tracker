package io.github.janetyulin.grounded.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.janetyulin.grounded.R
import io.github.janetyulin.grounded.domain.model.GeoPoint
import io.github.janetyulin.grounded.domain.model.Severity
import io.github.janetyulin.grounded.ui.common.EarthquakeUiState
import io.github.janetyulin.grounded.ui.common.FeedContent
import io.github.janetyulin.grounded.ui.common.FeedStatus
import io.github.janetyulin.grounded.ui.common.LoadErrorUiState
import io.github.janetyulin.grounded.ui.common.LocationBanner
import io.github.janetyulin.grounded.ui.common.LocationUiState
import io.github.janetyulin.grounded.ui.common.MagnitudeBadge
import io.github.janetyulin.grounded.ui.common.PeriodChips
import io.github.janetyulin.grounded.ui.common.labelRes
import io.github.janetyulin.grounded.ui.common.messageRes
import io.github.janetyulin.grounded.ui.common.timeAndDistanceText
import io.github.janetyulin.grounded.ui.theme.GroundedTheme
import io.github.janetyulin.grounded.ui.theme.colors

/**
 * Stateless Map screen. The List tab shows the same earthquakes and is the accessible alternative
 * to the native map.
 *
 * @param map Slot that renders the map itself; production passes [QuakeMap], tests pass a fake.
 * @param locationPermissionPermanentlyDenied Supplied by the route; changes the banner's action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    uiState: MapUiState,
    onEvent: (MapUiEvent) -> Unit,
    onEarthquakeDetailsClick: (String) -> Unit,
    locationPermissionPermanentlyDenied: Boolean,
    onLocationAction: () -> Unit,
    map: @Composable (List<EarthquakeUiState>, GeoPoint?, MapCameraUiState?, Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    val content = uiState.content
    val earthquakes = (content as? FeedContent.Loaded)?.earthquakes.orEmpty()
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PeriodChips(
                    selected = uiState.period,
                    onSelect = { onEvent(MapUiEvent.SelectPeriod(it)) },
                    modifier = Modifier.weight(1f)
                )
                if (uiState.isRefreshing || content is FeedContent.Loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp))
                } else {
                    IconButton(onClick = { onEvent(MapUiEvent.Refresh) }) {
                        Icon(
                            painterResource(R.drawable.ic_refresh),
                            contentDescription = stringResource(R.string.action_refresh)
                        )
                    }
                }
            }
            when (content) {
                is FeedContent.Loading -> Unit

                is FeedContent.Error -> ErrorRow(content.error, onRetry = { onEvent(MapUiEvent.Refresh) })

                is FeedContent.Loaded -> {
                    Text(
                        text = pluralStringResource(
                            R.plurals.map_count,
                            content.earthquakes.size,
                            content.earthquakes.size
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                    FeedStatus(content, onRetry = { onEvent(MapUiEvent.Refresh) })
                }
            }
            LocationBanner(uiState.location, locationPermissionPermanentlyDenied, onLocationAction)
        }

        Box(modifier = Modifier.weight(1f)) {
            // Markers on a native map aren't reachable with TalkBack, so point to the List tab instead.
            val mapDescription = pluralStringResource(R.plurals.map_description, earthquakes.size, earthquakes.size)
            map(
                earthquakes,
                uiState.userLocation,
                uiState.camera,
                Modifier.fillMaxSize().semantics { contentDescription = mapDescription }
            )
            // Bottom-end keeps the map's attribution (bottom-start) visible, as the tile license requires.
            Legend(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
        }
    }

    uiState.selected?.let { selected ->
        ModalBottomSheet(onDismissRequest = { onEvent(MapUiEvent.DismissSelection) }) {
            SelectedEarthquake(
                earthquake = selected,
                onDetailsClick = {
                    onEvent(MapUiEvent.DismissSelection)
                    onEarthquakeDetailsClick(selected.id)
                }
            )
        }
    }
}

@Composable
private fun ErrorRow(error: LoadErrorUiState, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.map_legend_title), style = MaterialTheme.typography.labelMedium)
            Severity.entries.reversed().forEach { severity ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(severity.colors().container, CircleShape)
                    )
                    Text(
                        text = stringResource(
                            R.string.map_legend_item,
                            stringResource(severity.rangeRes()),
                            stringResource(severity.labelRes())
                        ),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private fun Severity.rangeRes(): Int = when (this) {
    Severity.MINOR -> R.string.map_range_minor
    Severity.LIGHT -> R.string.map_range_light
    Severity.MODERATE -> R.string.map_range_moderate
    Severity.STRONG -> R.string.map_range_strong
    Severity.MAJOR -> R.string.map_range_major
}

@Composable
private fun SelectedEarthquake(earthquake: EarthquakeUiState, onDetailsClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MagnitudeBadge(magnitude = earthquake.magnitude, severity = earthquake.severity)
            Column(modifier = Modifier.weight(1f)) {
                Text(earthquake.place, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = timeAndDistanceText(earthquake.minutesAgo, earthquake.distanceKm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.earthquake_depth, earthquake.depthKm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Button(onClick = onDetailsClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.map_details))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MapScreenStalePreview() {
    GroundedTheme {
        MapScreen(
            uiState = MapUiState(
                content = FeedContent.Loaded(
                    earthquakes = emptyList(),
                    updatedMinutesAgo = 42,
                    refreshError = LoadErrorUiState.NETWORK
                ),
                location = LocationUiState.PERMISSION_REQUIRED
            ),
            onEvent = {},
            onEarthquakeDetailsClick = {},
            locationPermissionPermanentlyDenied = false,
            onLocationAction = {},
            map = { _, _, _, mapModifier -> Box(mapModifier.background(MaterialTheme.colorScheme.surfaceContainer)) }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MapScreenErrorPreview() {
    GroundedTheme {
        MapScreen(
            uiState = MapUiState(
                content = FeedContent.Error(LoadErrorUiState.SERVER),
                location = LocationUiState.AVAILABLE
            ),
            onEvent = {},
            onEarthquakeDetailsClick = {},
            locationPermissionPermanentlyDenied = false,
            onLocationAction = {},
            map = { _, _, _, mapModifier -> Box(mapModifier.background(MaterialTheme.colorScheme.surfaceContainer)) }
        )
    }
}
