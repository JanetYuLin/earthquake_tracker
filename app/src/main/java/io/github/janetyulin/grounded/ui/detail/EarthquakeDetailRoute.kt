package io.github.janetyulin.grounded.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Navigation entry point for one earthquake's details.
 */
@Composable
fun EarthquakeDetailRoute(
    earthquakeId: String,
    modifier: Modifier = Modifier,
    viewModel: EarthquakeDetailViewModel =
        hiltViewModel<EarthquakeDetailViewModel, EarthquakeDetailViewModel.Factory> { it.create(earthquakeId) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    EarthquakeDetailScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onOpenLink = uriHandler::openUri,
        modifier = modifier
    )
}
