package io.github.janetyulin.grounded.data.remote.earthquake

import io.github.janetyulin.grounded.data.model.Earthquake
import io.github.janetyulin.grounded.domain.model.DataError
import io.github.janetyulin.grounded.domain.model.TimePeriod

/**
 * Magnitude filters supported by USGS GeoJSON feed.
 */
enum class MagnitudeFilter(val slug: String) {
    ALL("all"),
    M1_0("1.0"),
    M2_5("2.5"),
    M4_5("4.5"),
    SIGNIFICANT("significant")
}

/**
 * Abstraction layer for fetching earthquake data from the remote source.
 * All functions are main-safe and return failures as a [Result] holding a [DataError].
 */
interface EarthquakeApiService {
    /** Events from a real-time summary feed. */
    suspend fun getSummaryFeed(magnitude: MagnitudeFilter, period: TimePeriod): Result<List<Earthquake>>

    /** A single event by its USGS id. */
    suspend fun getEvent(id: String): Result<Earthquake>
}
