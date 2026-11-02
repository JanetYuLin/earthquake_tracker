package io.github.janetyulin.grounded.data.repository

import androidx.datastore.core.DataStore
import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.data.model.AlertPreferences
import io.github.janetyulin.grounded.data.model.UserPreferences
import io.github.janetyulin.grounded.domain.model.TimePeriod
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Single source of truth for the user's choices, stored on the device.
 */
interface UserPreferencesRepository {

    val preferences: Flow<UserPreferences>

    suspend fun setSelectedPeriod(period: TimePeriod)

    suspend fun updateAlertPreferences(transform: (AlertPreferences) -> AlertPreferences)
}

/**
 * [UserPreferencesRepository] persisted with a typed [DataStore].
 */
@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<UserPreferences>,
    private val logger: LoggingManager
) : UserPreferencesRepository {

    /** Falls back to defaults if the file can't be read, rather than crashing every observer. */
    override val preferences: Flow<UserPreferences> = dataStore.data.catch { error ->
        if (error !is IOException) throw error
        logger.error(TAG, "Failed to read preferences", error)
        emit(UserPreferences())
    }

    override suspend fun setSelectedPeriod(period: TimePeriod) {
        dataStore.updateData { it.copy(selectedPeriod = period) }
    }

    override suspend fun updateAlertPreferences(transform: (AlertPreferences) -> AlertPreferences) {
        dataStore.updateData { it.copy(alerts = transform(it.alerts)) }
    }

    private companion object {
        const val TAG = "UserPreferencesRepository"
    }
}
