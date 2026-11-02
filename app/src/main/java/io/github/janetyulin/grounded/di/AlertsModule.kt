package io.github.janetyulin.grounded.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.janetyulin.grounded.alerts.AlertWorkScheduler
import io.github.janetyulin.grounded.alerts.AndroidNotificationPublisher
import io.github.janetyulin.grounded.alerts.NotificationPublisher
import io.github.janetyulin.grounded.alerts.WorkManagerAlertScheduler
import io.github.janetyulin.grounded.core.lifecycle.AppForegroundMonitor
import io.github.janetyulin.grounded.core.lifecycle.ProcessLifecycleForegroundMonitor
import java.time.Duration

@Module
@InstallIn(SingletonComponent::class)
abstract class AlertsModule {

    @Binds
    abstract fun bindAppForegroundMonitor(impl: ProcessLifecycleForegroundMonitor): AppForegroundMonitor

    @Binds
    abstract fun bindNotificationPublisher(impl: AndroidNotificationPublisher): NotificationPublisher

    @Binds
    abstract fun bindAlertWorkScheduler(impl: WorkManagerAlertScheduler): AlertWorkScheduler

    companion object {
        /** USGS data updates about once a minute, so polling faster gains nothing. */
        @Provides
        @PollInterval
        fun providePollInterval(): Duration = Duration.ofMinutes(2)
    }
}
