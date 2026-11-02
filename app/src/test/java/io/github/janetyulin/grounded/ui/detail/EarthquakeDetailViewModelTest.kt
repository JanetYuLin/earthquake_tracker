package io.github.janetyulin.grounded.ui.detail

import io.github.janetyulin.grounded.data.model.UserLocation
import io.github.janetyulin.grounded.data.repository.EarthquakeRepository
import io.github.janetyulin.grounded.domain.model.AlertLevel
import io.github.janetyulin.grounded.domain.model.DataError
import io.github.janetyulin.grounded.domain.model.GeoPoint
import io.github.janetyulin.grounded.domain.model.Severity
import io.github.janetyulin.grounded.testing.FakeLocationRepository
import io.github.janetyulin.grounded.testing.MainDispatcherRule
import io.github.janetyulin.grounded.testing.earthquake
import io.github.janetyulin.grounded.testing.keepCollecting
import io.github.janetyulin.grounded.ui.common.LoadErrorUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class EarthquakeDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-10-31T12:00:00Z")
    private val repository = mockk<EarthquakeRepository>()
    private val locationRepository = FakeLocationRepository(UserLocation.Available(GeoPoint(34.15, -118.14), now))
    private val quake = earthquake(
        "ci1",
        magnitude = 5.2,
        latitude = 35.77,
        longitude = -117.6,
        timeMillis = Instant.parse("2026-10-31T09:41:00Z").toEpochMilli(),
        alertLevel = AlertLevel.ORANGE,
        tsunamiFlag = true
    )

    private fun TestScope.createViewModel() =
        EarthquakeDetailViewModel("ci1", repository, locationRepository, Clock.fixed(now, ZoneOffset.UTC))
            .also { keepCollecting(it.uiState) }

    @Test
    fun `loads the earthquake for its id with distance and local time`() = runTest {
        Locale.setDefault(Locale.US)
        coEvery { repository.earthquake("ci1") } returns Result.success(quake)

        val state = createViewModel().uiState.value as EarthquakeDetailUiState.Loaded

        with(state.earthquake) {
            assertEquals(Severity.STRONG, severity)
            assertEquals(AlertLevel.ORANGE, alertLevel)
            assertEquals(true, tsunamiFlag)
            assertEquals(187, distanceKm)
            assertEquals(true, timeText.startsWith("Oct 31, 2026, 9:41"))
        }
    }

    @Test
    fun `distance is hidden without a location`() = runTest {
        locationRepository.set(UserLocation.PermissionRequired)
        coEvery { repository.earthquake("ci1") } returns Result.success(quake)

        val state = createViewModel().uiState.value as EarthquakeDetailUiState.Loaded

        assertNull(state.earthquake.distanceKm)
    }

    @Test
    fun `failure shows a typed Error and Retry loads again`() = runTest {
        coEvery { repository.earthquake("ci1") } returns Result.failure(DataError.Network(IOException()))
        val viewModel = createViewModel()
        assertEquals(EarthquakeDetailUiState.Error(LoadErrorUiState.NETWORK), viewModel.uiState.value)

        coEvery { repository.earthquake("ci1") } returns Result.success(quake)
        viewModel.onEvent(EarthquakeDetailUiEvent.Retry)

        assertEquals(EarthquakeDetailUiState.Loaded::class, viewModel.uiState.value::class)
        coVerify(exactly = 2) { repository.earthquake("ci1") }
    }
}
