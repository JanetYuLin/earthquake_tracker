package io.github.janetyulin.grounded.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.janetyulin.grounded.R
import io.github.janetyulin.grounded.domain.model.AlertLevel
import io.github.janetyulin.grounded.domain.model.Severity
import io.github.janetyulin.grounded.ui.common.ExternalLinks
import io.github.janetyulin.grounded.ui.common.MagnitudeBadge
import io.github.janetyulin.grounded.ui.common.TsunamiChip
import io.github.janetyulin.grounded.ui.common.labelRes
import io.github.janetyulin.grounded.ui.common.messageRes
import io.github.janetyulin.grounded.ui.theme.GroundedTheme

/**
 * Stateless detail screen for one earthquake. The title and back arrow come from the global top bar.
 */
@Composable
fun EarthquakeDetailScreen(
    uiState: EarthquakeDetailUiState,
    onEvent: (EarthquakeDetailUiEvent) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (uiState) {
            is EarthquakeDetailUiState.Loading -> CircularProgressIndicator()

            is EarthquakeDetailUiState.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(uiState.error.messageRes()))
                Button(onClick = { onEvent(EarthquakeDetailUiEvent.Retry) }) {
                    Text(stringResource(R.string.action_retry))
                }
            }

            is EarthquakeDetailUiState.Loaded -> Details(uiState.earthquake, onOpenLink)
        }
    }
}

@Composable
private fun Details(earthquake: EarthquakeDetailsUiState, onOpenLink: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MagnitudeBadge(magnitude = earthquake.magnitude, severity = earthquake.severity)
            Text(
                text = earthquake.place,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
        }
        if (earthquake.tsunamiFlag) TsunamiChip(onClick = { onOpenLink(ExternalLinks.TSUNAMI_INFO) })
        HorizontalDivider()
        DetailRow(stringResource(R.string.detail_time), earthquake.timeText)
        earthquake.distanceKm?.let {
            DetailRow(stringResource(R.string.detail_distance), stringResource(R.string.detail_distance_value, it))
        }
        DetailRow(
            stringResource(R.string.detail_depth),
            stringResource(R.string.detail_depth_value, earthquake.depthKm)
        )
        DetailRow(
            stringResource(R.string.detail_coordinates),
            stringResource(R.string.detail_coordinates_value, earthquake.latitude, earthquake.longitude)
        )
        DetailRow(
            stringResource(R.string.detail_status),
            stringResource(
                if (earthquake.reviewed) R.string.detail_status_reviewed else R.string.detail_status_automatic
            )
        )
        earthquake.feltReports?.let { DetailRow(stringResource(R.string.detail_felt), it.toString()) }
        earthquake.alertLevel?.let { DetailRow(stringResource(R.string.detail_pager), stringResource(it.labelRes())) }
        Button(onClick = { onOpenLink(earthquake.url) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.detail_open_usgs))
        }
        SafetySection(tsunamiFlag = earthquake.tsunamiFlag, onOpenLink = onOpenLink)
        Text(
            text = stringResource(R.string.attribution_usgs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(0.6f))
    }
}

@Preview(showBackground = true)
@Composable
private fun EarthquakeDetailScreenPreview() {
    GroundedTheme {
        EarthquakeDetailScreen(
            uiState = EarthquakeDetailUiState.Loaded(
                EarthquakeDetailsUiState(
                    "M 4.6 - 5 km NW of Ridgecrest, CA", "5 km NW of Ridgecrest, CA", 4.6, Severity.MODERATE,
                    "Nov 2, 2026, 9:41:00 AM", 180, 8.2, 35.7, -117.6, true, 120, AlertLevel.GREEN, true,
                    "https://usgs.gov"
                )
            ),
            onEvent = {},
            onOpenLink = {}
        )
    }
}
