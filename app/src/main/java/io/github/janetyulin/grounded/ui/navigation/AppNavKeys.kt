package io.github.janetyulin.grounded.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import io.github.janetyulin.grounded.R
import kotlinx.serialization.Serializable

/**
 * Every destination in the app. Serializable so the back stack survives configuration changes
 * and process death.
 */
@Serializable
sealed interface AppNavKey : NavKey {
    /** Stable analytics name; not derived from the class name, which R8 obfuscates. */
    val screenName: String

    /** Shown in the global top app bar. */
    @get:StringRes
    val titleResId: Int
}

/**
 * Top level navigation keys, one per bottom-bar tab.
 */
@Serializable
sealed interface TopLevelNavKey : AppNavKey {
    @get:StringRes
    val labelResId: Int

    @get:DrawableRes
    val iconResId: Int

    override val titleResId: Int get() = labelResId
}

@Serializable
data object RouteMap : TopLevelNavKey {
    override val labelResId: Int get() = R.string.nav_map
    override val iconResId: Int get() = R.drawable.ic_map
    override val screenName: String get() = "map"
}

@Serializable
data object RouteList : TopLevelNavKey {
    override val labelResId: Int get() = R.string.nav_list
    override val iconResId: Int get() = R.drawable.ic_list
    override val screenName: String get() = "list"
}

@Serializable
data object RouteAlerts : TopLevelNavKey {
    override val labelResId: Int get() = R.string.nav_alerts
    override val iconResId: Int get() = R.drawable.ic_notifications
    override val screenName: String get() = "alerts"
}

/**
 * Details for one earthquake, pushed onto the current tab's stack. Carries only the id;
 * the destination loads the data.
 */
@Serializable
data class RouteEarthquakeDetail(val earthquakeId: String) : AppNavKey {
    override val screenName: String get() = "earthquake_detail"
    override val titleResId: Int get() = R.string.detail_title
}

val TOP_LEVEL_NAV_KEYS: List<TopLevelNavKey> = listOf(
    RouteMap,
    RouteList,
    RouteAlerts
)
