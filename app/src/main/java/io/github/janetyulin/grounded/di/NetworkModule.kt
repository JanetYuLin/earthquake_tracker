package io.github.janetyulin.grounded.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.janetyulin.grounded.data.remote.earthquake.EarthquakeApiService
import io.github.janetyulin.grounded.data.remote.earthquake.USGSEarthquakeApiService
import io.github.janetyulin.grounded.data.remote.earthquake.UsgsApi
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * HTTP client, Retrofit and the USGS remote data source.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindEarthquakeApiService(impl: USGSEarthquakeApiService): EarthquakeApiService

    companion object {

        @Provides
        @UsgsBaseUrl
        fun provideUSGSBaseUrl(): String = "https://earthquake.usgs.gov/"

        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        @Provides
        @Singleton
        fun provideUSGSApi(@UsgsBaseUrl baseUrl: String, client: OkHttpClient, json: Json): UsgsApi = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(UsgsApi::class.java)
    }
}
