package io.github.janetyulin.grounded.data.local

import androidx.datastore.core.CorruptionException
import io.github.janetyulin.grounded.data.model.AlertPreferences
import io.github.janetyulin.grounded.data.model.CachedFeeds
import io.github.janetyulin.grounded.data.model.EarthquakeFeed
import io.github.janetyulin.grounded.data.model.UserPreferences
import io.github.janetyulin.grounded.domain.model.TimePeriod
import io.github.janetyulin.grounded.testing.earthquake
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val preferences = JsonSerializer(json, UserPreferences.serializer(), UserPreferences())
    private val feeds = JsonSerializer(json, CachedFeeds.serializer(), CachedFeeds())

    @Test
    fun `round-trips preferences`() = runTest {
        val value = UserPreferences(
            alerts = AlertPreferences(enabled = true, minMagnitude = 4.5, radiusKm = 300),
            selectedPeriod = TimePeriod.WEEK
        )

        assertEquals(value, preferences.roundTrip(value))
    }

    @Test
    fun `round-trips cached feeds keyed by period`() = runTest {
        val value = CachedFeeds(
            mapOf(TimePeriod.DAY to EarthquakeFeed(TimePeriod.DAY, listOf(earthquake("a"), earthquake("b")), 1_234L))
        )

        assertEquals(value, feeds.roundTrip(value))
    }

    @Test
    fun `missing fields take their defaults and unknown fields are ignored`() = runTest {
        // A file written by the earlier profile-based version still loads.
        val old = """{"name":"Alex","address":null,"alerts":{"enabled":true},"futureField":true}"""

        assertEquals(
            UserPreferences(alerts = AlertPreferences(enabled = true)),
            preferences.readFrom(old.byteInputStream())
        )
    }

    @Test(expected = CorruptionException::class)
    fun `malformed data is reported as corruption`() = runTest {
        feeds.readFrom("{not json".byteInputStream())
    }

    private suspend fun <T> JsonSerializer<T>.roundTrip(value: T): T {
        val output = ByteArrayOutputStream()
        writeTo(value, output)
        return readFrom(ByteArrayInputStream(output.toByteArray()))
    }
}
