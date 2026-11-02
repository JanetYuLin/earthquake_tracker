package io.github.janetyulin.grounded.data.remote.earthquake

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit definition of the USGS endpoints. Internal to the data layer; callers use [EarthquakeApiService].
 */
interface UsgsApi {

    /** Real-time summary feed, e.g. `2.5_week`. USGS's preferred source for displaying recent events. */
    @GET("earthquakes/feed/v1.0/summary/{feed}.geojson")
    suspend fun summaryFeed(@Path("feed") feed: String): USGSFeedDto

    /** A single event by id; the response is one GeoJSON Feature, not a collection. */
    @GET("fdsnws/event/1/query?format=geojson")
    suspend fun event(@Query("eventid") eventId: String): USGSFeatureDto
}
