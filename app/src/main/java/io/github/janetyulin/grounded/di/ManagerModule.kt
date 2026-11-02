package io.github.janetyulin.grounded.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.janetyulin.grounded.core.analytics.AnalyticsManager
import io.github.janetyulin.grounded.core.analytics.SampleAnalyticsManager
import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.core.logging.TimberLoggingManager
import javax.inject.Singleton

/**
 * Logging and analytics. Swap a vendor implementation in here; nothing else depends on it.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ManagerModule {

    @Binds
    @Singleton
    abstract fun bindLoggingManager(impl: TimberLoggingManager): LoggingManager

    @Binds
    @Singleton
    abstract fun bindAnalyticsManager(impl: SampleAnalyticsManager): AnalyticsManager
}
