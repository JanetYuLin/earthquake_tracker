package io.github.janetyulin.grounded.data.remote.earthquake

import io.github.janetyulin.grounded.core.coroutines.resultOf
import io.github.janetyulin.grounded.data.model.Earthquake
import io.github.janetyulin.grounded.domain.model.DataError
import io.github.janetyulin.grounded.domain.model.TimePeriod
import java.io.IOException
import javax.inject.Inject
import retrofit2.HttpException

/**
 * [EarthquakeApiService] backed by the USGS real-time feeds and FDSN event service via Retrofit.
 * Retrofit suspend calls run off the main thread, so no dispatcher switch is needed.
 */
class USGSEarthquakeApiService @Inject constructor(private val api: UsgsApi) : EarthquakeApiService {

    override suspend fun getSummaryFeed(magnitude: MagnitudeFilter, period: TimePeriod): Result<List<Earthquake>> =
        usgsCall {
            api.summaryFeed("${magnitude.slug}_${period.slug}").features.map { it.toDomain() }
        }

    override suspend fun getEvent(id: String): Result<Earthquake> = usgsCall {
        api.event(id).toDomain()
    }
}

/** Runs [block], turning any failure into a [DataError]. */
private inline fun <T> usgsCall(block: () -> T): Result<T> = resultOf(block).recoverCatching { throw it.toDataError() }

private fun Throwable.toDataError(): DataError = when (this) {
    is DataError -> this
    is IOException -> DataError.Network(this)
    is HttpException -> DataError.Server(code())
    else -> DataError.Unknown(this)
}

/** Path segment of the USGS summary feed for this period, e.g. `week` in `2.5_week`. */
private val TimePeriod.slug: String
    get() = when (this) {
        TimePeriod.HOUR -> "hour"
        TimePeriod.DAY -> "day"
        TimePeriod.WEEK -> "week"
        TimePeriod.MONTH -> "month"
    }
