package io.github.janetyulin.grounded.ui.map

import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.data.model.UserLocation
import io.github.janetyulin.grounded.data.model.UserPreferences
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepositoryImpl
import io.github.janetyulin.grounded.domain.model.GeoPoint
import io.github.janetyulin.grounded.domain.model.TimePeriod
import io.github.janetyulin.grounded.testing.FakeEarthquakeRepository
import io.github.janetyulin.grounded.testing.FakeLocationRepository
import io.github.janetyulin.grounded.testing.InMemoryDataStore
import io.github.janetyulin.grounded.testing.MainDispatcherRule
import io.github.janetyulin.grounded.testing.earthquake
import io.github.janetyulin.grounded.testing.keepCollecting
import io.github.janetyulin.grounded.ui.common.FeedContent
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class MapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-10-31T12:00:00Z")
    private val pasadena = GeoPoint(34.15, -118.14)
    private val dataStore = InMemoryDataStore(UserPreferences())
    private val earthquakeRepository = FakeEarthquakeRepository()
    private val locationRepository = FakeLocationRepository(UserLocation.Available(pasadena, now))
    private val ridgecrest = earthquake("ci1", magnitude = 5.4, latitude = 35.77, longitude = -117.6)

    private fun TestScope.createViewModel() = MapViewModel(
        earthquakeRepository,
        UserPreferencesRepositoryImpl(dataStore, mockk<LoggingManager>(relaxUnitFun = true)),
        locationRepository,
        Clock.fixed(now, ZoneOffset.UTC)
    ).also { keepCollecting(it.uiState) }

    @Test
    fun `shows the cached feed with the user's location and distances`() = runTest {
        earthquakeRepository.setFeed(TimePeriod.DAY, listOf(ridgecrest))

        with(createViewModel().uiState.value) {
            assertEquals(pasadena, userLocation)
            assertEquals(187, (content as FeedContent.Loaded).earthquakes.single().distanceKm)
        }
    }

    @Test
    fun `without a location there is no user marker`() = runTest {
        locationRepository.set(UserLocation.Unavailable)
        earthquakeRepository.setFeed(TimePeriod.DAY, listOf(ridgecrest))

        assertNull(createViewModel().uiState.value.userLocation)
    }

    @Test
    fun `selecting an earthquake shows it until dismissed`() = runTest {
        earthquakeRepository.setFeed(TimePeriod.DAY, listOf(ridgecrest))
        val viewModel = createViewModel()

        viewModel.onEvent(MapUiEvent.SelectEarthquake("ci1"))
        assertEquals("ci1", viewModel.uiState.value.selected?.id)

        viewModel.onEvent(MapUiEvent.DismissSelection)
        assertNull(viewModel.uiState.value.selected)
    }

    @Test
    fun `a new period clears the selection and is saved for the List too`() = runTest {
        earthquakeRepository.setFeed(TimePeriod.DAY, listOf(ridgecrest))
        val viewModel = createViewModel()
        viewModel.onEvent(MapUiEvent.SelectEarthquake("ci1"))

        viewModel.onEvent(MapUiEvent.SelectPeriod(TimePeriod.WEEK))

        assertEquals(TimePeriod.WEEK, dataStore.value.selectedPeriod)
        assertNull(viewModel.uiState.value.selected)
        assertEquals(FeedContent.Loading, viewModel.uiState.value.content)
    }

    @Test
    fun `the camera position is kept for returning to the tab`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(MapUiEvent.CameraIdle(MapCameraUiState(1.0, 2.0, 3.0)))

        assertEquals(MapCameraUiState(1.0, 2.0, 3.0), viewModel.uiState.value.camera)
    }

    @Test
    fun `refresh downloads the selected period`() = runTest {
        dataStore.updateData { it.copy(selectedPeriod = TimePeriod.WEEK) }
        val viewModel = createViewModel()

        viewModel.onEvent(MapUiEvent.Refresh)

        assertEquals(listOf(TimePeriod.WEEK), earthquakeRepository.refreshCalls)
    }
}
