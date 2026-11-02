package io.github.janetyulin.grounded.core.coroutines

import kotlinx.coroutines.flow.SharingStarted

/**
 * Sharing policy for ViewModel `stateIn`: keeps upstream flows alive for 5 s after the last
 * collector stops, so a configuration change doesn't restart them.
 */
val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(5_000)
