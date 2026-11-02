package io.github.janetyulin.grounded.data.remote.earthquake

import io.github.janetyulin.grounded.domain.model.DataError
import io.github.janetyulin.grounded.domain.model.TimePeriod
import io.github.janetyulin.grounded.testing.readResource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class USGSEarthquakeApiServiceTest {

    private val server = MockWebServer()
    private lateinit var service: USGSEarthquakeApiService

    @Before
    fun setUp() {
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(UsgsApi::class.java)
        service = USGSEarthquakeApiService(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `summary feed requests the feed path and parses features`() = runTest {
        server.enqueue(MockResponse().setBody(readResource("usgs/feed.geojson")))

        val result = service.getSummaryFeed(MagnitudeFilter.M2_5, TimePeriod.WEEK)

        assertEquals("/earthquakes/feed/v1.0/summary/2.5_week.geojson", server.takeRequest().path)
        assertEquals(listOf("ci1", "us2"), result.getOrThrow().map { it.id })
    }

    @Test
    fun `a connection failure is a network error`() = runTest {
        server.shutdown()

        assertTrue(service.getSummaryFeed(MagnitudeFilter.M2_5, TimePeriod.DAY).exceptionOrNull() is DataError.Network)
    }

    @Test
    fun `an HTTP error status is a server error with its code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val error = service.getSummaryFeed(MagnitudeFilter.M2_5, TimePeriod.DAY).exceptionOrNull()

        assertEquals(503, (error as DataError.Server).code)
    }

    @Test
    fun `event lookup parses a single feature`() = runTest {
        server.enqueue(MockResponse().setBody(readResource("usgs/event.geojson")))

        val result = service.getEvent("ci1")

        assertEquals("ci1", server.takeRequest().requestUrl!!.queryParameter("eventid"))
        assertEquals("ci1", result.getOrThrow().id)
    }

    @Test
    fun `malformed json is an unknown error, not a crash`() = runTest {
        server.enqueue(MockResponse().setBody("{not json"))

        assertTrue(service.getSummaryFeed(MagnitudeFilter.M2_5, TimePeriod.DAY).exceptionOrNull() is DataError.Unknown)
    }
}
