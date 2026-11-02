package io.github.janetyulin.grounded.di

import android.content.Context
import androidx.datastore.core.DataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.janetyulin.grounded.data.local.AppDataStores
import io.github.janetyulin.grounded.data.location.AndroidLocationProvider
import io.github.janetyulin.grounded.data.location.LocationProvider
import io.github.janetyulin.grounded.data.model.AlertState
import io.github.janetyulin.grounded.data.model.CachedFeeds
import io.github.janetyulin.grounded.data.model.LastLocation
import io.github.janetyulin.grounded.data.model.UserPreferences
import io.github.janetyulin.grounded.data.repository.AlertStateRepository
import io.github.janetyulin.grounded.data.repository.AlertStateRepositoryImpl
import io.github.janetyulin.grounded.data.repository.EarthquakeRepository
import io.github.janetyulin.grounded.data.repository.EarthquakeRepositoryImpl
import io.github.janetyulin.grounded.data.repository.LocationRepository
import io.github.janetyulin.grounded.data.repository.LocationRepositoryImpl
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepository
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepositoryImpl
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindEarthquakeRepository(impl: EarthquakeRepositoryImpl): EarthquakeRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindAlertStateRepository(impl: AlertStateRepositoryImpl): AlertStateRepository

    @Binds
    abstract fun bindLocationProvider(impl: AndroidLocationProvider): LocationProvider

    companion object {

        @Provides
        @Singleton
        fun provideUserPreferencesDataStore(
            @ApplicationContext context: Context,
            json: Json,
            @IoDispatcher ioDispatcher: CoroutineDispatcher
        ): DataStore<UserPreferences> = AppDataStores.userPreferences(context, json, ioDispatcher)

        @Provides
        @Singleton
        fun provideCachedFeedsDataStore(
            @ApplicationContext context: Context,
            json: Json,
            @IoDispatcher ioDispatcher: CoroutineDispatcher
        ): DataStore<CachedFeeds> = AppDataStores.cachedFeeds(context, json, ioDispatcher)

        @Provides
        @Singleton
        fun provideAlertStateDataStore(
            @ApplicationContext context: Context,
            json: Json,
            @IoDispatcher ioDispatcher: CoroutineDispatcher
        ): DataStore<AlertState> = AppDataStores.alertState(context, json, ioDispatcher)

        @Provides
        @Singleton
        fun provideLastLocationDataStore(
            @ApplicationContext context: Context,
            json: Json,
            @IoDispatcher ioDispatcher: CoroutineDispatcher
        ): DataStore<LastLocation> = AppDataStores.lastLocation(context, json, ioDispatcher)
    }
}
