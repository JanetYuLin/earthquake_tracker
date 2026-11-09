package io.github.janetyulin.grounded.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import io.github.janetyulin.grounded.alerts.AndroidNotificationPublisher
import io.github.janetyulin.grounded.alerts.NearbyAlertChecker
import io.github.janetyulin.grounded.alerts.NotificationPublisher
import io.github.janetyulin.grounded.core.work.BackgroundTask
import java.time.Duration

@Module
@InstallIn(SingletonComponent::class)
abstract class AlertsModule {

    @Binds
    abstract fun bindNotificationPublisher(impl: AndroidNotificationPublisher): NotificationPublisher

    companion object {
        /** USGS data updates about once a minute, so polling faster gains nothing. */
        @Provides
        @PollInterval
        fun providePollInterval(): Duration = Duration.ofMinutes(2)

        @Provides
        @IntoMap
        @StringKey(NearbyAlertChecker.BACKGROUND_TASK)
        fun provideBackgroundAlertTask(checker: NearbyAlertChecker): BackgroundTask =
            BackgroundTask { checker.checkInBackground() }
    }
}
