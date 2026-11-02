package io.github.janetyulin.grounded.di

import javax.inject.Qualifier

/**
 * Qualifies the base URL of the USGS earthquake feed.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UsgsBaseUrl

/**
 * Qualifies the [kotlinx.coroutines.CoroutineDispatcher] used for blocking I/O.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Qualifies the [kotlinx.coroutines.CoroutineDispatcher] used for CPU-bound work.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * Qualifies the app-lifetime [kotlinx.coroutines.CoroutineScope] for work that outlives any screen.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Qualifies the [java.time.Duration] between foreground polls for nearby earthquakes.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PollInterval
