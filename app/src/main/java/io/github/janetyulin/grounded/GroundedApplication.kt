package io.github.janetyulin.grounded

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.janetyulin.grounded.alerts.NearbyAlertChecker
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
    lateinit var nearbyAlertChecker: NearbyAlertChecker

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
        // The poller refreshes data only while the app is in the foreground. The checker alerts on every
        // new feed or fix, and keeps a periodic background check scheduled while alerts are on.
        nearbyAlertChecker.start()
        nearbyEarthquakePoller.start()
    }
}
