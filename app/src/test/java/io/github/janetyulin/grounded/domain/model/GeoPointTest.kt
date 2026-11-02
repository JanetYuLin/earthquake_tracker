package io.github.janetyulin.grounded.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoPointTest {

    @Test
    fun `distance to itself is zero`() {
        val point = GeoPoint(34.05, -118.25)
        assertEquals(0.0, point.distanceKmTo(point), 1e-9)
    }

    @Test
    fun `distance between Los Angeles and San Francisco is about 559 km`() {
        val la = GeoPoint(34.0522, -118.2437)
        val sf = GeoPoint(37.7749, -122.4194)
        assertEquals(559.0, la.distanceKmTo(sf), 2.0)
    }

    @Test
    fun `distance across the antimeridian takes the short way`() {
        val west = GeoPoint(0.0, 179.5)
        val east = GeoPoint(0.0, -179.5)
        assertEquals(111.2, west.distanceKmTo(east), 0.5)
    }
}
