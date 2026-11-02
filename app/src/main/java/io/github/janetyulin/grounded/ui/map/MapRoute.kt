package io.github.janetyulin.grounded.ui.map

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.janetyulin.grounded.ui.common.rememberLocationAccess

/**
 * Navigation entry point for the Map tab. Owns the location permission flow so [MapScreen] stays
 * free of platform APIs.
 *
 * @param mapHolder The retained native map, owned by the nav host so it survives leaving this tab.
 */
@Composable
fun MapRoute(
    mapHolder: QuakeMapHolder,
    onEarthquakeDetailsClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val darkTheme = isSystemInDarkTheme()
    val locationAccess = rememberLocationAccess(onCheck = { viewModel.onEvent(MapUiEvent.CheckLocation) })
    MapScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onEarthquakeDetailsClick = onEarthquakeDetailsClick,
        locationPermissionPermanentlyDenied = locationAccess.permanentlyDenied,
        onLocationAction = { locationAccess.act(uiState.location) },
        modifier = modifier,
        map = { earthquakes, userLocation, camera, mapModifier ->
            QuakeMap(
                holder = mapHolder,
                earthquakes = earthquakes,
                userLocation = userLocation,
                camera = camera,
                darkTheme = darkTheme,
                onEarthquakeClick = { viewModel.onEvent(MapUiEvent.SelectEarthquake(it)) },
                onCameraIdle = { viewModel.onEvent(MapUiEvent.CameraIdle(it)) },
                modifier = mapModifier
            )
        }
    )
}
