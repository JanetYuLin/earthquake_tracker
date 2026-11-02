package io.github.janetyulin.grounded.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.janetyulin.grounded.R
import io.github.janetyulin.grounded.domain.model.AlertLevel
import io.github.janetyulin.grounded.domain.model.Severity
import io.github.janetyulin.grounded.ui.common.EarthquakeUiState
import io.github.janetyulin.grounded.ui.common.ExternalLinks
import io.github.janetyulin.grounded.ui.common.FeedContent
import io.github.janetyulin.grounded.ui.common.FeedError
import io.github.janetyulin.grounded.ui.common.FeedStatus
import io.github.janetyulin.grounded.ui.common.LoadErrorUiState
import io.github.janetyulin.grounded.ui.common.LocationBanner
import io.github.janetyulin.grounded.ui.common.LocationUiState
import io.github.janetyulin.grounded.ui.common.MagnitudeBadge
import io.github.janetyulin.grounded.ui.common.PeriodChips
import io.github.janetyulin.grounded.ui.common.TsunamiChip
import io.github.janetyulin.grounded.ui.common.labelRes
import io.github.janetyulin.grounded.ui.common.timeAndDistanceText
import io.github.janetyulin.grounded.ui.theme.GroundedTheme

/**
 * Stateless List screen: the same earthquakes as the Map, as a sortable list. Also the accessible
 * alternative to the native map.
 *
 * @param locationPermissionPermanentlyDenied Supplied by the route; changes the banner's action.
 */
@Composable
fun ListScreen(
    uiState: ListUiState,
    onEvent: (ListUiEvent) -> Unit,
    onEarthquakeClick: (String) -> Unit,
    locationPermissionPermanentlyDenied: Boolean,
    onLocationAction: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { onEvent(ListUiEvent.Refresh) },
        modifier = modifier.fillMaxSize()
    ) {
        when (val content = uiState.content) {
            is FeedContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is FeedContent.Error -> FeedError(content.error, onRetry = { onEvent(ListUiEvent.Refresh) })

            is FeedContent.Loaded -> LoadedContent(
                uiState = uiState,
                content = content,
                onEvent = onEvent,
                onEarthquakeClick = onEarthquakeClick,
                locationPermissionPermanentlyDenied = locationPermissionPermanentlyDenied,
                onLocationAction = onLocationAction,
                onTsunamiClick = { onOpenLink(ExternalLinks.TSUNAMI_INFO) }
            )
        }
    }
}

@Composable
private fun LoadedContent(
    uiState: ListUiState,
    content: FeedContent.Loaded,
    onEvent: (ListUiEvent) -> Unit,
    onEarthquakeClick: (String) -> Unit,
    locationPermissionPermanentlyDenied: Boolean,
    onLocationAction: () -> Unit,
    onTsunamiClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PeriodChips(selected = uiState.period, onSelect = { onEvent(ListUiEvent.SelectPeriod(it)) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.sortOptions.forEach { sort ->
                        FilterChip(
                            selected = sort == uiState.sort,
                            onClick = { onEvent(ListUiEvent.SelectSort(sort)) },
                            label = { Text(stringResource(sort.labelRes())) }
                        )
                    }
                }
                FeedStatus(content, onRetry = { onEvent(ListUiEvent.Refresh) })
                LocationBanner(uiState.location, locationPermissionPermanentlyDenied, onLocationAction)
            }
        }
        if (content.earthquakes.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }
        items(items = content.earthquakes, key = { it.id }) { earthquake ->
            EarthquakeItem(
                earthquake = earthquake,
                onClick = { onEarthquakeClick(earthquake.id) },
                onTsunamiClick = onTsunamiClick
            )
        }
    }
}

@Composable
fun EarthquakeItem(
    earthquake: EarthquakeUiState,
    onClick: () -> Unit,
    onTsunamiClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MagnitudeBadge(magnitude = earthquake.magnitude, severity = earthquake.severity)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = earthquake.place, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = timeAndDistanceText(earthquake.minutesAgo, earthquake.distanceKm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (earthquake.alertLevel != null || earthquake.tsunamiFlag) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        earthquake.alertLevel?.let { level ->
                            SuggestionChip(onClick = onClick, label = { Text(stringResource(level.labelRes())) })
                        }
                        if (earthquake.tsunamiFlag) TsunamiChip(onClick = onTsunamiClick)
                    }
                }
            }
        }
    }
}

private fun ListSortUiState.labelRes(): Int = when (this) {
    ListSortUiState.RECENT -> R.string.sort_recent
    ListSortUiState.NEAREST -> R.string.sort_nearest
    ListSortUiState.STRONGEST -> R.string.sort_strongest
}

private val previewQuakes = listOf(
    EarthquakeUiState(
        "1", "5 km NW of Ridgecrest, CA", 4.6, Severity.MODERATE, 35.7, -117.6, 8.2, 23, 180, AlertLevel.GREEN, false
    ),
    EarthquakeUiState("2", "12 km S of Malibu, CA", 2.8, Severity.MINOR, 34.0, -118.7, 4.1, 95, 40, null, true)
)

@Preview(showBackground = true)
@Composable
private fun ListScreenLoadedPreview() {
    GroundedTheme {
        ListScreen(
            uiState = ListUiState(
                content = FeedContent.Loaded(previewQuakes, updatedMinutesAgo = 3, refreshError = null),
                location = LocationUiState.AVAILABLE,
                sortOptions = ListSortUiState.entries
            ),
            onEvent = {},
            onEarthquakeClick = {},
            locationPermissionPermanentlyDenied = false,
            onLocationAction = {},
            onOpenLink = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ListScreenStaleNoLocationPreview() {
    GroundedTheme {
        ListScreen(
            uiState = ListUiState(
                content = FeedContent.Loaded(
                    previewQuakes.map { it.copy(distanceKm = null) },
                    updatedMinutesAgo = 180,
                    refreshError = LoadErrorUiState.NETWORK
                ),
                location = LocationUiState.PERMISSION_REQUIRED
            ),
            onEvent = {},
            onEarthquakeClick = {},
            locationPermissionPermanentlyDenied = false,
            onLocationAction = {},
            onOpenLink = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ListScreenErrorPreview() {
    GroundedTheme {
        ListScreen(ListUiState(content = FeedContent.Error(LoadErrorUiState.NETWORK)), {}, {}, false, {}, {})
    }
}
