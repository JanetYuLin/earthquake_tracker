package io.github.janetyulin.grounded.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.janetyulin.grounded.R
import io.github.janetyulin.grounded.domain.model.TimePeriod

/** Feed periods Map and List offer. Both read the same selection, so they always match. */
val PERIOD_OPTIONS: List<TimePeriod> = listOf(TimePeriod.DAY, TimePeriod.WEEK)

@Composable
fun PeriodChips(selected: TimePeriod, onSelect: (TimePeriod) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PERIOD_OPTIONS.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(stringResource(period.labelRes())) }
            )
        }
    }
}

fun TimePeriod.labelRes(): Int = when (this) {
    TimePeriod.HOUR -> R.string.period_hour
    TimePeriod.DAY -> R.string.period_day
    TimePeriod.WEEK -> R.string.period_week
    TimePeriod.MONTH -> R.string.period_month
}

/**
 * "Updated 5 min ago", or, when the latest refresh failed, a warning that the data shown is older
 * than it should be, with a retry.
 */
@Composable
fun FeedStatus(content: FeedContent.Loaded, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val updated = stringResource(R.string.feed_updated, relativeTimeText(content.updatedMinutesAgo))
    val error = content.refreshError
    if (error == null) {
        Text(
            text = updated,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.feed_stale, stringResource(error.messageRes()), updated),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

/**
 * Explains why distances are hidden and offers the fix. Shows nothing when the location is known
 * or still being checked.
 *
 * @param permissionPermanentlyDenied The system won't show the permission dialog again, so the
 * action opens app settings instead.
 * @param onAction Requests the permission, or opens the relevant settings; the route decides.
 */
@Composable
fun LocationBanner(
    location: LocationUiState,
    permissionPermanentlyDenied: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (message, action) = when (location) {
        LocationUiState.PERMISSION_REQUIRED ->
            R.string.location_permission_message to
                if (permissionPermanentlyDenied) R.string.action_open_settings else R.string.action_allow

        LocationUiState.UNAVAILABLE -> R.string.location_unavailable_message to R.string.action_open_settings

        LocationUiState.CHECKING, LocationUiState.AVAILABLE -> return
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(stringResource(action)) }
        }
    }
}

/** Full-screen state used only when there is nothing cached to show. Scrollable so pull-to-refresh works. */
@Composable
fun FeedError(error: LoadErrorUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = stringResource(R.string.feed_error_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

fun LoadErrorUiState.messageRes(): Int = when (this) {
    LoadErrorUiState.NETWORK -> R.string.error_network
    LoadErrorUiState.SERVER -> R.string.error_server
    LoadErrorUiState.UNKNOWN -> R.string.error_unknown
}
