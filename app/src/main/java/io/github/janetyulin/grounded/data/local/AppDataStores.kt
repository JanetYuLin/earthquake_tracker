package io.github.janetyulin.grounded.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import io.github.janetyulin.grounded.data.model.AlertState
import io.github.janetyulin.grounded.data.model.CachedFeeds
import io.github.janetyulin.grounded.data.model.LastLocation
import io.github.janetyulin.grounded.data.model.UserPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Creates the app's on-disk stores, one JSON file each. DataStore allows only one instance per file,
 * so callers must keep each store as a singleton (see `DataModule`).
 */
object AppDataStores {

    fun userPreferences(context: Context, json: Json, ioDispatcher: CoroutineDispatcher): DataStore<UserPreferences> =
        create(context, json, ioDispatcher, Files.PREFERENCES, UserPreferences.serializer(), UserPreferences())

    fun cachedFeeds(context: Context, json: Json, ioDispatcher: CoroutineDispatcher): DataStore<CachedFeeds> =
        create(context, json, ioDispatcher, Files.FEEDS, CachedFeeds.serializer(), CachedFeeds())

    fun alertState(context: Context, json: Json, ioDispatcher: CoroutineDispatcher): DataStore<AlertState> =
        create(context, json, ioDispatcher, Files.ALERT_STATE, AlertState.serializer(), AlertState())

    fun lastLocation(context: Context, json: Json, ioDispatcher: CoroutineDispatcher): DataStore<LastLocation> =
        create(context, json, ioDispatcher, Files.LAST_LOCATION, LastLocation.serializer(), LastLocation())

    private fun <T> create(
        context: Context,
        json: Json,
        ioDispatcher: CoroutineDispatcher,
        fileName: String,
        serializer: KSerializer<T>,
        defaultValue: T
    ): DataStore<T> = DataStoreFactory.create(
        serializer = JsonSerializer(json, serializer, defaultValue),
        // An unreadable file (corruption, incompatible schema) resets to defaults instead of
        // failing every read forever.
        corruptionHandler = ReplaceFileCorruptionHandler { defaultValue },
        scope = CoroutineScope(ioDispatcher + SupervisorJob()),
        produceFile = { context.dataStoreFile(fileName) }
    )

    /** All but [PREFERENCES] are excluded from backup in `res/xml`; keep the names in sync. */
    private object Files {
        const val PREFERENCES = "user_preferences.json"
        const val FEEDS = "earthquake_feeds.json"
        const val ALERT_STATE = "alert_state.json"
        const val LAST_LOCATION = "last_location.json"
    }
}
