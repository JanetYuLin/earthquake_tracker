package io.github.janetyulin.grounded.data.repository

import androidx.datastore.core.DataStore
import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.data.model.AlertPrimingKey
import io.github.janetyulin.grounded.data.model.AlertState
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * Persisted bookkeeping for nearby alerts. Kept apart from user preferences so recording ids doesn't
 * re-emit every screen's state, and so a background check in a new process can read it.
 */
interface AlertStateRepository {

    suspend fun current(): AlertState

    /**
     * Saves that the ids were primed for [primedFor] and that [handled] were dealt with, keeping
     * only the most recent [MAX_NOTIFIED_IDS].
     */
    suspend fun record(primedFor: AlertPrimingKey, handled: Collection<String>)

    companion object {
        const val MAX_NOTIFIED_IDS = 200
    }
}

/**
 * [AlertStateRepository] persisted with a typed [DataStore].
 */
@Singleton
class AlertStateRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<AlertState>,
    private val logger: LoggingManager
) : AlertStateRepository {

    override suspend fun current(): AlertState = dataStore.data
        .catch { error ->
            if (error !is IOException) throw error
            logger.error(TAG, "Failed to read alert state", error)
            emit(AlertState())
        }
        .first()

    override suspend fun record(primedFor: AlertPrimingKey, handled: Collection<String>) {
        dataStore.updateData { state ->
            val merged = (state.notifiedEarthquakeIds - handled.toSet()) + handled
            state.copy(
                notifiedEarthquakeIds = merged.takeLast(AlertStateRepository.MAX_NOTIFIED_IDS),
                primedFor = primedFor
            )
        }
    }

    private companion object {
        const val TAG = "AlertStateRepository"
    }
}
