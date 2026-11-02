package io.github.janetyulin.grounded.testing

import io.github.janetyulin.grounded.data.model.Earthquake
import io.github.janetyulin.grounded.data.model.EarthquakeFeed
import io.github.janetyulin.grounded.data.model.EarthquakeFeedState
import io.github.janetyulin.grounded.data.model.UserLocation
import io.github.janetyulin.grounded.data.repository.EarthquakeRepository
import io.github.janetyulin.grounded.data.repository.LocationRepository
import io.github.janetyulin.grounded.domain.model.DataError
import io.github.janetyulin.grounded.domain.model.TimePeriod
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * [EarthquakeRepository] fake backed by a [MutableStateFlow]. Tests set [feeds] directly to simulate
 * cached data, and [nextRefresh] to choose what the next [refresh] does.
 */
class FakeEarthquakeRepository : EarthquakeRepository {
    val feeds = MutableStateFlow<Map<TimePeriod, EarthquakeFeedState>>(emptyMap())
    val refreshCalls = mutableListOf<TimePeriod>()

    /** Earthquakes the next refresh "downloads", or `null` to fail with a network error. */
    var nextRefresh: List<Earthquake>? = emptyList()
    var nextFetchedAtMillis = 0L

    override fun feed(period: TimePeriod): Flow<EarthquakeFeedState> = feeds.map { it[period] ?: EarthquakeFeedState() }

    override suspend fun refresh(period: TimePeriod): Result<Unit> {
        refreshCalls += period
        val quakes = nextRefresh
        val previous = feeds.value[period] ?: EarthquakeFeedState()
        return if (quakes != null) {
            setFeed(period, quakes, nextFetchedAtMillis)
            Result.success(Unit)
        } else {
            val error = DataError.Network(IOException("offline"))
            feeds.value += period to previous.copy(refreshError = error)
            Result.failure(error)
        }
    }

    override suspend fun earthquake(id: String): Result<Earthquake> =
        feeds.value.values.firstNotNullOfOrNull { state -> state.feed?.earthquakes?.firstOrNull { it.id == id } }
            ?.let { Result.success(it) }
            ?: Result.failure(DataError.Network(IOException("offline")))

    fun setFeed(period: TimePeriod, quakes: List<Earthquake>, fetchedAtMillis: Long = 0L) {
        feeds.value += period to EarthquakeFeedState(EarthquakeFeed(period, quakes, fetchedAtMillis))
    }
}

/** [LocationRepository] fake; [nextFix] is what the next [refresh] reports, [saved] what [lastSaved] returns. */
class FakeLocationRepository(initial: UserLocation = UserLocation.Unknown) : LocationRepository {
    private val _location = MutableStateFlow(initial)
    override val location = _location.asStateFlow()
    var nextFix: UserLocation = initial
    var refreshCount = 0
    var saved: UserLocation.Available? = null

    override suspend fun lastSaved(): UserLocation.Available? = saved

    override suspend fun refresh() {
        refreshCount++
        _location.value = nextFix
    }

    fun set(value: UserLocation) {
        _location.value = value
        nextFix = value
    }
}

/** Keeps a `stateIn(WhileSubscribed)` flow active for the test, so `.value` reflects upstream changes. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun <T> kotlinx.coroutines.test.TestScope.keepCollecting(flow: Flow<T>) {
    backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}
