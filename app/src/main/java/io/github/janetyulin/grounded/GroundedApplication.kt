package io.github.janetyulin.grounded

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.janetyulin.grounded.alerts.BackgroundAlertScheduler
import io.github.janetyulin.grounded.alerts.ForegroundAlertTrigger
import io.github.janetyulin.grounded.alerts.NearbyEarthquakePoller
import javax.inject.Inject
import org.maplibre.android.MapLibre
import timber.log.Timber

/**
 * Hilt Application class. Also configures WorkManager with Hilt's factory, so workers get injected
 * dependencies (the default initializer is removed in the manifest).
 */
@HiltAndroidApp
class GroundedApplication :
    Application(),
    Configuration.Provider {

    @Inject
    lateinit var nearbyEarthquakePoller: NearbyEarthquakePoller

    @Inject
    lateinit var foregroundAlertTrigger: ForegroundAlertTrigger

    @Inject
    lateinit var backgroundAlertScheduler: BackgroundAlertScheduler

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Local-only logging for this sample; a release build would plant a crash-reporting Tree instead.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Must run before any MapView is created.
        MapLibre.getInstance(this)
        // The poller and notifier only work while the app is in the foreground; the scheduler keeps a
        // periodic background check registered while alerts are on.
        foregroundAlertTrigger.start()
        nearbyEarthquakePoller.start()
        backgroundAlertScheduler.start()
    }
}
