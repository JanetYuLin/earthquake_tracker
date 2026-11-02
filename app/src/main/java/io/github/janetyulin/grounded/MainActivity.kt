package io.github.janetyulin.grounded

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import io.github.janetyulin.grounded.core.analytics.AnalyticsManager
import io.github.janetyulin.grounded.ui.navigation.GroundedNavHost
import io.github.janetyulin.grounded.ui.theme.GroundedTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var analyticsManager: AnalyticsManager

    /** Earthquake to open from a tapped notification; cleared once navigation handles it. */
    private var pendingEarthquakeId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // On recreation the saved back stack already contains the destination.
        if (savedInstanceState == null) consumeDeepLink(intent)
        enableEdgeToEdge()
        setContent {
            GroundedTheme {
                GroundedNavHost(
                    onScreenViewed = { navKey -> analyticsManager.trackScreen(navKey.screenName) },
                    onExit = ::finish,
                    deepLinkEarthquakeId = pendingEarthquakeId,
                    onDeepLinkHandled = { pendingEarthquakeId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeDeepLink(intent)
    }

    private fun consumeDeepLink(intent: Intent) {
        intent.getStringExtra(EXTRA_EARTHQUAKE_ID)?.let { pendingEarthquakeId = it }
        intent.removeExtra(EXTRA_EARTHQUAKE_ID)
    }

    companion object {
        const val EXTRA_EARTHQUAKE_ID = "io.github.janetyulin.grounded.EARTHQUAKE_ID"
    }
}
