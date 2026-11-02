package io.github.janetyulin.grounded.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.janetyulin.grounded.core.coroutines.WhileUiSubscribed
import io.github.janetyulin.grounded.data.repository.LocationRepository
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepository
import io.github.janetyulin.grounded.domain.model.AlertRules
import io.github.janetyulin.grounded.ui.common.toUiState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Alerts screen: nearby-earthquake notification settings.
 */
@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    /** Raw permission state; whether it's a *problem* depends on the saved preference, derived below. */
    private val notificationsGranted = MutableStateFlow(true)

    val uiState: StateFlow<AlertsUiState> = combine(
        preferencesRepository.preferences,
        notificationsGranted,
        locationRepository.location
    ) { preferences, granted, location ->
        val alerts = preferences.alerts
        AlertsUiState(
            isLoading = false,
            enabled = alerts.enabled,
            minMagnitude = alerts.minMagnitude,
            radiusKm = alerts.radiusKm,
            notificationPermissionDenied = alerts.enabled && !granted,
            location = location.toUiState()
        )
    }.stateIn(
        scope = viewModelScope,
        started = WhileUiSubscribed,
        initialValue = AlertsUiState()
    )

    fun onEvent(event: AlertsUiEvent) {
        when (event) {
            is AlertsUiEvent.SetAlertsEnabled -> viewModelScope.launch {
                preferencesRepository.updateAlertPreferences { it.copy(enabled = event.enabled) }
            }

            is AlertsUiEvent.NotificationPermissionChecked -> notificationsGranted.value = event.granted

            is AlertsUiEvent.SetMinMagnitude -> viewModelScope.launch {
                preferencesRepository.updateAlertPreferences { it.copy(minMagnitude = event.magnitude) }
            }

            is AlertsUiEvent.SetRadius -> viewModelScope.launch {
                val radius = event.radiusKm.coerceIn(AlertRules.RADIUS_KM_RANGE)
                preferencesRepository.updateAlertPreferences { it.copy(radiusKm = radius) }
            }

            is AlertsUiEvent.CheckLocation -> viewModelScope.launch { locationRepository.refresh() }
        }
    }
}
