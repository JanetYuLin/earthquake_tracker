package io.github.janetyulin.grounded.ui.list

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
import io.github.janetyulin.grounded.ui.common.LoadErrorUiState
import io.github.janetyulin.grounded.ui.common.LocationUiState
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class ListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-10-31T12:00:00Z")
    private val pasadena = GeoPoint(34.15, -118.14)
    private val dataStore = InMemoryDataStore(UserPreferences())
    private val earthquakeRepository = FakeEarthquakeRepository()
    private val locationRepository = FakeLocationRepository(UserLocation.Available(pasadena, now))

    // Newest is mid-size and far; nearest is small; strongest is oldest.
    private val newestFar =
        earthquake("far", magnitude = 4.0, latitude = 35.68, longitude = 139.69, timeMillis = minutesAgo(5))
    private val nearSmall =
        earthquake("near", magnitude = 2.6, latitude = 34.1, longitude = -118.1, timeMillis = minutesAgo(60))
    private val oldStrong =
        earthquake("strong", magnitude = 6.1, latitude = 38.3, longitude = 142.4, timeMillis = minutesAgo(600))

    private fun minutesAgo(minutes: Long) = now.minus(Duration.ofMinutes(minutes)).toEpochMilli()

    private fun TestScope.createViewModel() = ListViewModel(
        earthquakeRepository,
        UserPreferencesRepositoryImpl(dataStore, mockk<LoggingManager>(relaxUnitFun = true)),
        locationRepository,
        Clock.fixed(now, ZoneOffset.UTC)
    ).also { keepCollecting(it.uiState) }

    private fun ListUiState.ids() = (content as FeedContent.Loaded).earthquakes.map { it.id }

    @Test
    fun `shows the selected period's cached feed, newest first`() = runTest {
        earthquakeRepository.setFeed(TimePeriod.DAY, listOf(oldStrong, nearSmall, newestFar))

        val state = createViewModel().uiState.value

        assertEquals(listOf("far", "near", "strong"), state.ids())
        assertEquals(LocationUiState.AVAILABLE, state.location)
    }

    @Test
    fun `sorts by distance or magnitude on request`() = runTest {
        earthquakeRepository.setFeed(TimePeriod.DAY, listOf(oldStrong, nearSmall, newestFar))
        val viewModel = createViewModel()

        viewModel.onEvent(ListUiEvent.SelectSort(ListSortUiState.NEAREST))
        assertEquals("near", viewModel.uiState.value.ids().first())

        viewModel.onEvent(ListUiEvent.SelectSort(ListSortUiState.STRONGEST))
        assertEquals(listOf("strong", "far", "near"), viewModel.uiState.value.ids())
    }

    @Test
    fun `without location, distances are hidden and Nearest falls back to Recent`() = runTest {
        locationRepository.set(UserLocation.PermissionRequired)
        earthquakeRepository.setFeed(TimePeriod.DAY, listOf(nearSmall, newestFar))
        val viewModel = createViewModel()

        viewModel.onEvent(ListUiEvent.SelectSort(ListSortUiState.NEAREST))

        with(viewModel.uiState.value) {
            assertEquals(LocationUiState.PERMISSION_REQUIRED, location)
            assertFalse(ListSortUiState.NEAREST in sortOptions)
            assertEquals(ListSortUiState.RECENT, sort)
            assertEquals(listOf(null, null), (content as FeedContent.Loaded).earthquakes.map { it.distanceKm })
        }
    }

    @Test
    fun `a failed refresh keeps the cached list and flags it as stale`() = runTest {
        earthquakeRepository.setFeed(TimePeriod.DAY, listOf(newestFar), fetchedAtMillis = minutesAgo(30))
        earthquakeRepository.nextRefresh = null
        val viewModel = createViewModel()

        viewModel.onEvent(ListUiEvent.Refresh)

        with(viewModel.uiState.value.content as FeedContent.Loaded) {
            assertEquals(listOf("far"), earthquakes.map { it.id })
            assertEquals(30, updatedMinutesAgo)
            assertEquals(LoadErrorUiState.NETWORK, refreshError)
        }
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `selecting a period saves it, so the Map shows the same one`() = runTest {
        earthquakeRepository.setFeed(TimePeriod.WEEK, listOf(oldStrong))
        val viewModel = createViewModel()

        viewModel.onEvent(ListUiEvent.SelectPeriod(TimePeriod.WEEK))

        assertEquals(TimePeriod.WEEK, dataStore.value.selectedPeriod)
        assertEquals(TimePeriod.WEEK, viewModel.uiState.value.period)
        assertEquals(listOf("strong"), viewModel.uiState.value.ids())
    }

    @Test
    fun `refresh downloads the selected period`() = runTest {
        dataStore.updateData { it.copy(selectedPeriod = TimePeriod.WEEK) }
        val viewModel = createViewModel()

        viewModel.onEvent(ListUiEvent.Refresh)

        assertEquals(listOf(TimePeriod.WEEK), earthquakeRepository.refreshCalls)
    }

    @Test
    fun `checking location asks the repository for a fresh fix`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(ListUiEvent.CheckLocation)

        assertEquals(1, locationRepository.refreshCount)
    }
}
