package io.github.janetyulin.grounded.data.remote.earthquake

import io.github.janetyulin.grounded.data.model.Earthquake
import io.github.janetyulin.grounded.domain.model.AlertLevel
import io.github.janetyulin.grounded.testing.readResource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class UsgsMapperTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val features = json.decodeFromString<USGSFeedDto>(readResource("usgs/feed.geojson")).features

    @Test
    fun `maps a complete feature to the domain model`() {
        assertEquals(
            Earthquake(
                id = "ci1",
                title = "M 4.5 - 10 km N of Ridgecrest, CA",
                magnitude = 4.5,
                place = "10 km N of Ridgecrest, CA",
                timeMillis = 1_700_000_000_000L,
                updatedMillis = 1_700_000_100_000L,
                longitude = -117.6,
                latitude = 35.7,
                depthKm = 8.2,
                url = "https://earthquake.usgs.gov/earthquakes/eventpage/ci1",
                alertLevel = AlertLevel.YELLOW,
                tsunamiFlag = true,
                feltReports = 120,
                reviewed = true
            ),
            features[0].toDomain()
        )
    }

    @Test
    fun `maps nullable fields to safe defaults`() {
        val earthquake = features[1].toDomain()

        assertEquals("Tokyo, Japan", earthquake.title)
        assertEquals(earthquake.timeMillis, earthquake.updatedMillis)
        assertNull(earthquake.alertLevel)
        assertNull(earthquake.feltReports)
        assertFalse(earthquake.tsunamiFlag)
        assertFalse(earthquake.reviewed)
    }

    @Test
    fun `falls back to defaults for missing optional fields`() {
        val feature = USGSFeatureDto(
            id = "id",
            properties = USGSPropertiesDto(time = 1L, url = "u", alert = "unknown-level"),
            geometry = USGSGeometryDto(type = "Point", coordinates = emptyList())
        )

        val earthquake = feature.toDomain()

        assertEquals("Earthquake", earthquake.title)
        assertEquals("Unknown location", earthquake.place)
        assertEquals(0.0, earthquake.magnitude, 0.0)
        assertEquals(0.0, earthquake.depthKm, 0.0)
        assertNull(earthquake.alertLevel)
    }
}
