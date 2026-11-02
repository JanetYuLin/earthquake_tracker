package io.github.janetyulin.grounded.alerts

import io.github.janetyulin.grounded.core.lifecycle.AppForegroundMonitor
import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.data.model.UserPreferences
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepositoryImpl
import io.github.janetyulin.grounded.domain.model.TimePeriod
import io.github.janetyulin.grounded.testing.FakeEarthquakeRepository
import io.github.janetyulin.grounded.testing.FakeLocationRepository
import io.github.janetyulin.grounded.testing.InMemoryDataStore
import io.mockk.mockk
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyEarthquakePollerTest {

    private val interval = Duration.ofMinutes(2)
    private val foreground = MutableStateFlow(true)
    private val dataStore = InMemoryDataStore(UserPreferences())
    private val preferencesRepository =
        UserPreferencesRepositoryImpl(dataStore, mockk<LoggingManager>(relaxUnitFun = true))
    private val earthquakeRepository = FakeEarthquakeRepository()
    private val locationRepository = FakeLocationRepository()
    private val foregroundMonitor = object : AppForegroundMonitor {
        override val isForeground = foreground
    }

    private fun TestScope.startPoller() {
        val poller = NearbyEarthquakePoller(
            foregroundMonitor,
            preferencesRepository,
            earthquakeRepository,
            locationRepository,
            backgroundScope,
            interval,
            mockk<LoggingManager>(relaxUnitFun = true)
        )
        backgroundScope.launch { poller.run() }
        runCurrent()
    }

    private fun TestScope.advanceBy(duration: Duration) {
        advanceTimeBy(duration.toMillis())
        runCurrent()
    }

    @Test
    fun `refreshes location and the selected feed immediately, then once per interval`() = runTest {
        startPoller()
        assertEquals(listOf(TimePeriod.DAY), earthquakeRepository.refreshCalls)
        assertEquals(1, locationRepository.refreshCount)

        advanceBy(interval)
        assertEquals(2, earthquakeRepository.refreshCalls.size)

        advanceBy(interval.multipliedBy(2))
        assertEquals(4, earthquakeRepository.refreshCalls.size)
        assertEquals(4, locationRepository.refreshCount)
    }

    @Test
    fun `stops polling in the background and resumes immediately in the foreground`() = runTest {
        startPoller()

        foreground.value = false
        runCurrent()
        advanceBy(Duration.ofHours(1))
        assertEquals(1, earthquakeRepository.refreshCalls.size)

        foreground.value = true
        runCurrent()
        assertEquals(2, earthquakeRepository.refreshCalls.size)
    }

    @Test
    fun `does nothing in the background from the start`() = runTest {
        foreground.value = false

        startPoller()
        advanceBy(Duration.ofHours(1))

        assertEquals(emptyList<TimePeriod>(), earthquakeRepository.refreshCalls)
        assertEquals(0, locationRepository.refreshCount)
    }

    @Test
    fun `a new period refreshes that feed right away`() = runTest {
        startPoller()

        preferencesRepository.setSelectedPeriod(TimePeriod.WEEK)
        runCurrent()

        assertEquals(listOf(TimePeriod.DAY, TimePeriod.WEEK), earthquakeRepository.refreshCalls)
    }

    @Test
    fun `unrelated preference changes do not trigger extra refreshes`() = runTest {
        startPoller()

        preferencesRepository.updateAlertPreferences { it.copy(enabled = true) }
        runCurrent()

        assertEquals(1, earthquakeRepository.refreshCalls.size)
    }
}
