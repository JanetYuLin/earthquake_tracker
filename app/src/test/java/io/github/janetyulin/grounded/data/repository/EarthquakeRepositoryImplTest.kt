package io.github.janetyulin.grounded.data.repository

import app.cash.turbine.test
import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.data.model.CachedFeeds
import io.github.janetyulin.grounded.data.model.EarthquakeFeed
import io.github.janetyulin.grounded.data.model.EarthquakeFeedState
import io.github.janetyulin.grounded.data.remote.earthquake.EarthquakeApiService
import io.github.janetyulin.grounded.data.remote.earthquake.MagnitudeFilter
import io.github.janetyulin.grounded.domain.model.DataError
import io.github.janetyulin.grounded.domain.model.TimePeriod
import io.github.janetyulin.grounded.testing.InMemoryDataStore
import io.github.janetyulin.grounded.testing.earthquake
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarthquakeRepositoryImplTest {

    private val now = Instant.parse("2026-10-31T12:00:00Z")
    private val apiService = mockk<EarthquakeApiService>()
    private val dataStore = InMemoryDataStore(CachedFeeds())
    private val repository = EarthquakeRepositoryImpl(
        apiService,
        dataStore,
        Clock.fixed(now, ZoneOffset.UTC),
        mockk<LoggingManager>(relaxUnitFun = true)
    )

    private val cachedDay = EarthquakeFeed(TimePeriod.DAY, listOf(earthquake("old")), fetchedAtMillis = 1_000L)

    @Test
    fun `feed is empty before anything was downloaded`() = runTest {
        assertEquals(EarthquakeFeedState(), repository.feed(TimePeriod.DAY).first())
    }

    @Test
    fun `feed serves what's on disk without touching the network`() = runTest {
        dataStore.updateData { CachedFeeds(mapOf(TimePeriod.DAY to cachedDay)) }

        assertEquals(EarthquakeFeedState(cachedDay), repository.feed(TimePeriod.DAY).first())
        coVerify(exactly = 0) { apiService.getSummaryFeed(any(), any()) }
    }

    @Test
    fun `refresh downloads the M2_5 feed and replaces the cache with a timestamp`() = runTest {
        val fresh = listOf(earthquake("a"), earthquake("b"))
        coEvery { apiService.getSummaryFeed(MagnitudeFilter.M2_5, TimePeriod.WEEK) } returns Result.success(fresh)

        assertTrue(repository.refresh(TimePeriod.WEEK).isSuccess)

        assertEquals(
            EarthquakeFeed(TimePeriod.WEEK, fresh, now.toEpochMilli()),
            repository.feed(TimePeriod.WEEK).first().feed
        )
    }

    @Test
    fun `a failed refresh keeps the cached feed and reports why`() = runTest {
        dataStore.updateData { CachedFeeds(mapOf(TimePeriod.DAY to cachedDay)) }
        val error = DataError.Network(IOException("offline"))
        coEvery { apiService.getSummaryFeed(any(), any()) } returns Result.failure(error)

        repository.feed(TimePeriod.DAY).test {
            assertEquals(EarthquakeFeedState(cachedDay), awaitItem())

            assertEquals(error, repository.refresh(TimePeriod.DAY).exceptionOrNull())

            assertEquals(EarthquakeFeedState(cachedDay, refreshError = error), awaitItem())
        }
    }

    @Test
    fun `the next successful refresh clears the error`() = runTest {
        coEvery { apiService.getSummaryFeed(any(), any()) } returns Result.failure(DataError.Server(503))
        repository.refresh(TimePeriod.DAY)
        coEvery { apiService.getSummaryFeed(any(), any()) } returns Result.success(listOf(earthquake("a")))

        repository.refresh(TimePeriod.DAY)

        assertNull(repository.feed(TimePeriod.DAY).first().refreshError)
    }

    @Test
    fun `errors are tracked per period`() = runTest {
        coEvery { apiService.getSummaryFeed(any(), TimePeriod.WEEK) } returns Result.failure(DataError.Server(503))

        repository.refresh(TimePeriod.WEEK)

        assertNull(repository.feed(TimePeriod.DAY).first().refreshError)
    }

    @Test
    fun `earthquake comes from any cached feed, so details work offline`() = runTest {
        dataStore.updateData { CachedFeeds(mapOf(TimePeriod.DAY to cachedDay)) }

        assertEquals("old", repository.earthquake("old").getOrThrow().id)
        coVerify(exactly = 0) { apiService.getEvent(any()) }
    }

    @Test
    fun `earthquake falls back to the network when it isn't cached`() = runTest {
        coEvery { apiService.getEvent("x") } returns Result.success(earthquake("x"))

        assertEquals("x", repository.earthquake("x").getOrThrow().id)
    }
}
