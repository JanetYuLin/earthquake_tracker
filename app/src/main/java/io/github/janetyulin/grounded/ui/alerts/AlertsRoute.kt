package io.github.janetyulin.grounded.ui.alerts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.janetyulin.grounded.ui.common.rememberLocationAccess

/**
 * Navigation entry point for the Alerts tab. Owns the notification and location permission flows so
 * [AlertsScreen] stays free of platform APIs.
 */
@Composable
fun AlertsRoute(modifier: Modifier = Modifier, viewModel: AlertsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun notificationsGranted() = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(AlertsUiEvent.NotificationPermissionChecked(notificationsGranted()))
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onEvent(AlertsUiEvent.NotificationPermissionChecked(granted))
        // Keep the user's intent either way; the screen explains when the system blocks notifications.
        viewModel.onEvent(AlertsUiEvent.SetAlertsEnabled(true))
    }
    val locationAccess = rememberLocationAccess(onCheck = { viewModel.onEvent(AlertsUiEvent.CheckLocation) })

    AlertsScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onAlertsToggled = { enabled ->
            when {
                !enabled || notificationsGranted() -> viewModel.onEvent(AlertsUiEvent.SetAlertsEnabled(enabled))
                else -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        locationPermissionPermanentlyDenied = locationAccess.permanentlyDenied,
        onLocationAction = { locationAccess.act(uiState.location) },
        modifier = modifier
    )
}
