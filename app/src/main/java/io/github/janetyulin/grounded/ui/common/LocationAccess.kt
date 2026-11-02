package io.github.janetyulin.grounded.ui.common

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Route-level handling of the location permission, shared by the Map and List routes so their
 * screens stay free of platform APIs.
 */
@Stable
class LocationAccess internal constructor(
    /** True once the system will no longer show the permission dialog. */
    val permanentlyDenied: Boolean,
    private val onAction: (LocationUiState) -> Unit
) {
    /** Handles the banner's action for [location]: asks for the permission or opens settings. */
    fun act(location: LocationUiState) = onAction(location)
}

/**
 * @param onCheck Called on every resume and after the permission dialog closes, so the ViewModel can
 * re-read the location (the user may have changed the permission or location setting meanwhile).
 */
@Composable
fun rememberLocationAccess(onCheck: () -> Unit): LocationAccess {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnCheck by rememberUpdatedState(onCheck)
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        currentOnCheck()
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // After a denial, no rationale means "don't ask again": only Settings can grant it now.
        permanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
        currentOnCheck()
    }

    return LocationAccess(permanentlyDenied) { location ->
        when {
            location == LocationUiState.UNAVAILABLE ->
                context.startSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

            permanentlyDenied -> context.startSettings(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            )

            else -> launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}

/** Some devices (kiosk builds, some emulators) ship without these settings screens. */
private fun Context.startSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Nothing more we can do; the banner still explains why distances are hidden.
    }
}
