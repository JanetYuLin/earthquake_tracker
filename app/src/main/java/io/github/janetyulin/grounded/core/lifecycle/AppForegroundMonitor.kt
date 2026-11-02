package io.github.janetyulin.grounded.core.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Whether any of the app's UI is visible. Process-wide, so configuration changes don't toggle it.
 */
interface AppForegroundMonitor {
    val isForeground: Flow<Boolean>
}

/**
 * [AppForegroundMonitor] backed by [ProcessLifecycleOwner]: STARTED or above means foreground.
 */
class ProcessLifecycleForegroundMonitor @Inject constructor() : AppForegroundMonitor {
    override val isForeground: Flow<Boolean> =
        ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) }
            .distinctUntilChanged()
}
