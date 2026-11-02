package io.github.janetyulin.grounded.ui.alerts

import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.data.model.AlertPreferences
import io.github.janetyulin.grounded.data.model.UserLocation
import io.github.janetyulin.grounded.data.model.UserPreferences
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepositoryImpl
import io.github.janetyulin.grounded.domain.model.AlertRules
import io.github.janetyulin.grounded.testing.FakeLocationRepository
import io.github.janetyulin.grounded.testing.InMemoryDataStore
import io.github.janetyulin.grounded.testing.MainDispatcherRule
import io.github.janetyulin.grounded.testing.keepCollecting
import io.github.janetyulin.grounded.ui.common.LocationUiState
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlertsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dataStore = InMemoryDataStore(UserPreferences())
    private val preferencesRepository =
        UserPreferencesRepositoryImpl(dataStore, mockk<LoggingManager>(relaxUnitFun = true))
    private val locationRepository = FakeLocationRepository(UserLocation.PermissionRequired)

    private fun TestScope.createViewModel(repository: UserPreferencesRepositoryImpl = preferencesRepository) =
        AlertsViewModel(repository, locationRepository).also { keepCollecting(it.uiState) }

    @Test
    fun `shows the saved preferences and the location state`() = runTest {
        dataStore.updateData { it.copy(alerts = AlertPreferences(enabled = true, minMagnitude = 4.5, radiusKm = 300)) }

        with(createViewModel().uiState.value) {
            assertFalse(isLoading)
            assertTrue(enabled)
            assertEquals(4.5, minMagnitude, 0.0)
            assertEquals(300, radiusKm)
            assertEquals(LocationUiState.PERMISSION_REQUIRED, location)
        }
    }

    @Test
    fun `blocked notifications are reported even when checked before preferences load`() = runTest {
        // Regression: the check used to read uiState.value, which was still the initial state.
        val delayed = MutableStateFlow<UserPreferences?>(null)
        val slowStore = object : androidx.datastore.core.DataStore<UserPreferences> {
            override val data = delayed.filterNotNull()
            override suspend fun updateData(transform: suspend (t: UserPreferences) -> UserPreferences) =
                transform(delayed.value ?: UserPreferences()).also { delayed.value = it }
        }
        val viewModel =
            createViewModel(UserPreferencesRepositoryImpl(slowStore, mockk<LoggingManager>(relaxUnitFun = true)))

        viewModel.onEvent(AlertsUiEvent.NotificationPermissionChecked(granted = false))
        delayed.value = UserPreferences(alerts = AlertPreferences(enabled = true))

        assertTrue(viewModel.uiState.value.notificationPermissionDenied)
    }

    @Test
    fun `blocked notifications only matter while alerts are on`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(AlertsUiEvent.NotificationPermissionChecked(granted = false))
        assertFalse(viewModel.uiState.value.notificationPermissionDenied)

        viewModel.onEvent(AlertsUiEvent.SetAlertsEnabled(true))
        assertTrue(viewModel.uiState.value.notificationPermissionDenied)

        viewModel.onEvent(AlertsUiEvent.NotificationPermissionChecked(granted = true))
        assertFalse(viewModel.uiState.value.notificationPermissionDenied)
    }

    @Test
    fun `alert preference events update the repository, clamping the radius`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(AlertsUiEvent.SetMinMagnitude(5.5))
        viewModel.onEvent(AlertsUiEvent.SetRadius(10_000))

        assertEquals(5.5, dataStore.value.alerts.minMagnitude, 0.0)
        assertEquals(AlertRules.RADIUS_KM_RANGE.last, dataStore.value.alerts.radiusKm)
    }

    @Test
    fun `checking location asks the repository for a fresh fix`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(AlertsUiEvent.CheckLocation)

        assertEquals(1, locationRepository.refreshCount)
    }
}
