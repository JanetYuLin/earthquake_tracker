package io.github.janetyulin.grounded.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.janetyulin.grounded.R
import io.github.janetyulin.grounded.domain.model.AlertRules
import io.github.janetyulin.grounded.ui.common.LocationBanner
import io.github.janetyulin.grounded.ui.common.LocationUiState
import io.github.janetyulin.grounded.ui.theme.GroundedTheme
import kotlin.math.roundToInt

/**
 * Stateless Alerts screen: notification settings plus data attribution.
 *
 * @param onAlertsToggled Handled by the route, which may need to request the notification permission first.
 */
@Composable
fun AlertsScreen(
    uiState: AlertsUiState,
    onEvent: (AlertsUiEvent) -> Unit,
    onAlertsToggled: (Boolean) -> Unit,
    locationPermissionPermanentlyDenied: Boolean,
    onLocationAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard(title = stringResource(R.string.alerts_title)) {
            AlertSettings(uiState, onEvent, onAlertsToggled, locationPermissionPermanentlyDenied, onLocationAction)
        }
        SectionCard(title = stringResource(R.string.about_title)) {
            Text(stringResource(R.string.attribution_usgs), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.attribution_map), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ColumnScope.AlertSettings(
    uiState: AlertsUiState,
    onEvent: (AlertsUiEvent) -> Unit,
    onAlertsToggled: (Boolean) -> Unit,
    locationPermissionPermanentlyDenied: Boolean,
    onLocationAction: () -> Unit
) {
    // One toggleable row so TalkBack announces the label together with the switch state.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = uiState.enabled, role = Role.Switch, onValueChange = onAlertsToggled)
    ) {
        Text(
            text = stringResource(R.string.alerts_toggle),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = uiState.enabled, onCheckedChange = null)
    }
    if (uiState.notificationPermissionDenied) {
        Text(
            text = stringResource(R.string.alerts_permission_denied),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
    }
    if (uiState.enabled) {
        LocationBanner(uiState.location, locationPermissionPermanentlyDenied, onLocationAction)
    }

    Text(stringResource(R.string.alerts_min_magnitude), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AlertRules.MAGNITUDE_OPTIONS.forEach { magnitude ->
            FilterChip(
                selected = uiState.minMagnitude == magnitude,
                onClick = { onEvent(AlertsUiEvent.SetMinMagnitude(magnitude)) },
                label = { Text(stringResource(R.string.alerts_magnitude_option, magnitude)) }
            )
        }
    }

    var radiusDraft by rememberSaveable(uiState.radiusKm) { mutableFloatStateOf(uiState.radiusKm.toFloat()) }
    val radiusLabel = stringResource(R.string.alerts_radius, radiusDraft.roundToInt())
    Text(radiusLabel, style = MaterialTheme.typography.labelLarge)
    val radiusRange = AlertRules.RADIUS_KM_RANGE
    val steps = (radiusRange.last - radiusRange.first) / AlertRules.RADIUS_STEP_KM - 1
    Slider(
        value = radiusDraft,
        onValueChange = { radiusDraft = it },
        onValueChangeFinished = { onEvent(AlertsUiEvent.SetRadius(radiusDraft.roundToInt())) },
        valueRange = radiusRange.first.toFloat()..radiusRange.last.toFloat(),
        steps = steps,
        modifier = Modifier.semantics { stateDescription = radiusLabel }
    )
    Text(
        text = stringResource(R.string.alerts_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AlertsScreenPreview() {
    GroundedTheme {
        AlertsScreen(
            uiState = AlertsUiState(isLoading = false, enabled = true, location = LocationUiState.PERMISSION_REQUIRED),
            onEvent = {},
            onAlertsToggled = {},
            locationPermissionPermanentlyDenied = false,
            onLocationAction = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AlertsScreenBlockedPreview() {
    GroundedTheme {
        AlertsScreen(
            uiState = AlertsUiState(
                isLoading = false,
                enabled = true,
                notificationPermissionDenied = true,
                location = LocationUiState.AVAILABLE
            ),
            onEvent = {},
            onAlertsToggled = {},
            locationPermissionPermanentlyDenied = false,
            onLocationAction = {}
        )
    }
}
