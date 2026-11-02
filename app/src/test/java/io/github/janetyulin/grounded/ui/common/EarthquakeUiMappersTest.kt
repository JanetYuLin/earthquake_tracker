package io.github.janetyulin.grounded.ui.common

import io.github.janetyulin.grounded.data.model.EarthquakeFeed
import io.github.janetyulin.grounded.data.model.EarthquakeFeedState
import io.github.janetyulin.grounded.domain.model.DataError
import io.github.janetyulin.grounded.domain.model.GeoPoint
import io.github.janetyulin.grounded.domain.model.Severity
import io.github.janetyulin.grounded.domain.model.TimePeriod
import io.github.janetyulin.grounded.testing.earthquake
import java.io.IOException
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EarthquakeUiMappersTest {

    private val now = Instant.parse("2026-10-31T12:00:00Z")
    private val pasadena = GeoPoint(34.15, -118.14)
    private val ridgecrest =
        earthquake("ci1", magnitude = 5.4, latitude = 35.77, longitude = -117.6, timeMillis = minutesAgo(30))
    private val feed = EarthquakeFeed(TimePeriod.DAY, listOf(ridgecrest), fetchedAtMillis = minutesAgo(12))

    private fun minutesAgo(minutes: Long) = now.minus(Duration.ofMinutes(minutes)).toEpochMilli()

    @Test
    fun `nothing cached and no error is Loading`() {
        assertEquals(FeedContent.Loading, EarthquakeFeedState().toFeedContent(now, pasadena))
    }

    @Test
    fun `nothing cached and a failed refresh is a full-screen Error`() {
        val state = EarthquakeFeedState(refreshError = DataError.Network(IOException()))

        assertEquals(FeedContent.Error(LoadErrorUiState.NETWORK), state.toFeedContent(now, pasadena))
    }

    @Test
    fun `a failed refresh with a cache keeps the data and says how old it is`() {
        val state = EarthquakeFeedState(feed, refreshError = DataError.Server(503))

        val content = state.toFeedContent(now, pasadena) as FeedContent.Loaded

        assertEquals(listOf("ci1"), content.earthquakes.map { it.id })
        assertEquals(12, content.updatedMinutesAgo)
        assertEquals(LoadErrorUiState.SERVER, content.refreshError)
    }

    @Test
    fun `maps each earthquake with severity, age and distance from the user`() {
        val quake = (EarthquakeFeedState(feed).toFeedContent(now, pasadena) as FeedContent.Loaded).earthquakes.single()

        assertEquals(Severity.STRONG, quake.severity)
        assertEquals(30, quake.minutesAgo)
        assertEquals(187, quake.distanceKm)
    }

    @Test
    fun `distance is null without a location`() {
        val quake = (
            EarthquakeFeedState(
                feed
            ).toFeedContent(now, here = null) as FeedContent.Loaded
            ).earthquakes.single()

        assertNull(quake.distanceKm)
    }
}
