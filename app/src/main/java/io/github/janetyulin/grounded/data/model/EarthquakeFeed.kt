package io.github.janetyulin.grounded.data.model

import io.github.janetyulin.grounded.domain.model.DataError
import io.github.janetyulin.grounded.domain.model.TimePeriod
import kotlinx.serialization.Serializable

/**
 * The last successful download of one USGS summary feed.
 *
 * @param fetchedAtMillis When it was downloaded, so screens can say how old cached data is.
 */
@Serializable
data class EarthquakeFeed(val period: TimePeriod, val earthquakes: List<Earthquake>, val fetchedAtMillis: Long)

/**
 * Every cached feed, persisted as one JSON document.
 */
@Serializable
data class CachedFeeds(val feeds: Map<TimePeriod, EarthquakeFeed> = emptyMap())

/**
 * What screens observe for one period: the cached feed, plus why the latest refresh failed, if it did.
 *
 * @param feed `null` until the first successful refresh.
 * @param refreshError Cleared by the next successful refresh. Not persisted.
 */
data class EarthquakeFeedState(val feed: EarthquakeFeed? = null, val refreshError: DataError? = null)
