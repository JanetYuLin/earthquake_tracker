package io.github.janetyulin.grounded.data.repository

import androidx.datastore.core.DataStore
import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.data.model.CachedFeeds
import io.github.janetyulin.grounded.data.model.Earthquake
import io.github.janetyulin.grounded.data.model.EarthquakeFeed
import io.github.janetyulin.grounded.data.model.EarthquakeFeedState
import io.github.janetyulin.grounded.data.remote.earthquake.EarthquakeApiService
import io.github.janetyulin.grounded.data.remote.earthquake.MagnitudeFilter
import io.github.janetyulin.grounded.domain.model.DataError
import io.github.janetyulin.grounded.domain.model.TimePeriod
import java.io.IOException
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for earthquake data. The last successful download of each feed is kept on
 * disk, so screens show it immediately, offline, and after process death.
 */
interface EarthquakeRepository {

    /** Cached M2.5+ worldwide events for [period] and the outcome of the latest refresh. */
    fun feed(period: TimePeriod): Flow<EarthquakeFeedState>

    /**
     * Downloads [period]'s feed and replaces the cache. On failure the cache is left untouched, so
     * observers keep showing the previous data; the [DataError] is returned and reported through [feed].
     */
    suspend fun refresh(period: TimePeriod): Result<Unit>

    /** A single event: from any cached feed when present (works offline), otherwise from the network. */
    suspend fun earthquake(id: String): Result<Earthquake>

    companion object {
        /** How recent an event must be to trigger a nearby alert. */
        val RECENT_WINDOW: Duration = Duration.ofHours(5)
    }
}

/**
 * Default implementation of [EarthquakeRepository].
 */
@Singleton
class EarthquakeRepositoryImpl @Inject constructor(
    private val apiService: EarthquakeApiService,
    private val dataStore: DataStore<CachedFeeds>,
    private val clock: Clock,
    private val logger: LoggingManager
) : EarthquakeRepository {

    /** Falls back to an empty cache if the file can't be read, rather than failing every observer. */
    private val cached: Flow<CachedFeeds> = dataStore.data.catch { error ->
        if (error !is IOException) throw error
        logger.error(TAG, "Failed to read cached feeds", error)
        emit(CachedFeeds())
    }

    /** Failure of the latest refresh per period, kept in memory: it only describes this session. */
    private val refreshErrors = MutableStateFlow<Map<TimePeriod, DataError>>(emptyMap())

    override fun feed(period: TimePeriod): Flow<EarthquakeFeedState> =
        combine(cached.map { it.feeds[period] }, refreshErrors.map { it[period] }) { feed, error ->
            EarthquakeFeedState(feed, error)
        }.distinctUntilChanged()

    override suspend fun refresh(period: TimePeriod): Result<Unit> =
        apiService.getSummaryFeed(MagnitudeFilter.M2_5, period)
            .map { quakes ->
                val feed = EarthquakeFeed(period, quakes, clock.millis())
                dataStore.updateData { it.copy(feeds = it.feeds + (period to feed)) }
                refreshErrors.update { it - period }
                logger.info(TAG, "Loaded ${quakes.size} earthquakes (${period.name})")
            }
            .onFailure { error ->
                logger.error(TAG, "Failed to load earthquakes (${period.name})", error)
                val dataError = error as? DataError ?: DataError.Unknown(error)
                refreshErrors.update { it + (period to dataError) }
            }

    override suspend fun earthquake(id: String): Result<Earthquake> {
        val cachedQuake = cached.first().feeds.values.firstNotNullOfOrNull { feed ->
            feed.earthquakes.firstOrNull { it.id == id }
        }
        return cachedQuake?.let { Result.success(it) } ?: apiService.getEvent(id)
    }

    private companion object {
        const val TAG = "EarthquakeRepository"
    }
}
