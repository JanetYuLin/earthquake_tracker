package io.github.janetyulin.grounded.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.janetyulin.grounded.ui.common.rememberLocationAccess

/**
 * Navigation entry point for the List tab: resolves the ViewModel, owns the location permission flow
 * and wires both to the stateless [ListScreen].
 */
@Composable
fun ListRoute(
    onEarthquakeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val locationAccess = rememberLocationAccess(onCheck = { viewModel.onEvent(ListUiEvent.CheckLocation) })
    ListScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onEarthquakeClick = onEarthquakeClick,
        locationPermissionPermanentlyDenied = locationAccess.permanentlyDenied,
        onLocationAction = { locationAccess.act(uiState.location) },
        onOpenLink = uriHandler::openUri,
        modifier = modifier
    )
}
