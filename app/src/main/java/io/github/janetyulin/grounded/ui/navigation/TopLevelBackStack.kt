package io.github.janetyulin.grounded.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One back stack per top-level tab, flattened into [backStack] for `NavDisplay` (current tab last),
 * following the Navigation 3 multiple-back-stacks recipe.
 *
 * Back follows Material navigation guidance: it pops within the current tab; from another tab's
 * root it returns to the start tab; from the start tab's root it exits. Every tab keeps its history.
 */
class TopLevelBackStack private constructor(
    stacks: List<List<AppNavKey>>,
    topLevelKey: TopLevelNavKey,
    private val startKey: TopLevelNavKey
) {
    constructor(startKey: TopLevelNavKey) : this(listOf(listOf(startKey)), startKey, startKey)

    private val topLevelStacks: LinkedHashMap<TopLevelNavKey, SnapshotStateList<AppNavKey>> =
        LinkedHashMap<TopLevelNavKey, SnapshotStateList<AppNavKey>>().apply {
            stacks.forEach { stack -> put(stack.first() as TopLevelNavKey, mutableStateListOf(*stack.toTypedArray())) }
        }

    var topLevelKey by mutableStateOf(topLevelKey)
        private set

    val backStack = mutableStateListOf<AppNavKey>()

    init {
        updateBackStack()
    }

    private fun updateBackStack() = backStack.apply {
        clear()
        addAll(topLevelStacks.flatMap { it.value })
    }

    /** Switches to [key]'s tab, creating its stack on first use and keeping its history otherwise. */
    fun addTopLevel(key: TopLevelNavKey) {
        val existing = topLevelStacks.remove(key)
        topLevelStacks[key] = existing ?: mutableStateListOf(key)
        topLevelKey = key
        updateBackStack()
    }

    /** Pushes [key] onto the current tab's stack. */
    fun add(key: AppNavKey) {
        topLevelStacks[topLevelKey]?.add(key)
        updateBackStack()
    }

    /** True when the current tab has screens above its root, so an Up/back arrow applies. */
    val canGoBackWithinTab: Boolean
        get() = (topLevelStacks[topLevelKey]?.size ?: 0) > 1

    /**
     * Pops the top screen of the current tab, never its root. Safe to call repeatedly (e.g. a
     * double-tapped back arrow) because extra calls are no-ops.
     */
    fun popWithinTab() {
        val stack = topLevelStacks[topLevelKey] ?: return
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            updateBackStack()
        }
    }

    /**
     * System back. Never pops a tab's root, so repeated calls can't empty the stack.
     *
     * @return `false` when back should leave the app (the start tab's root is showing).
     */
    fun removeLast(): Boolean {
        when {
            canGoBackWithinTab -> popWithinTab()
            topLevelKey != startKey -> addTopLevel(startKey)
            else -> return false
        }
        return true
    }

    @Serializable
    private data class Snapshot(
        val stacks: List<List<AppNavKey>>,
        val topLevelKey: TopLevelNavKey,
        val startKey: TopLevelNavKey
    )

    companion object {
        private val json = Json

        /** Persists all tab stacks as JSON so they survive configuration changes and process death. */
        val Saver: Saver<TopLevelBackStack, String> = Saver(
            save = { stack ->
                json.encodeToString(
                    Snapshot(stack.topLevelStacks.values.map { it.toList() }, stack.topLevelKey, stack.startKey)
                )
            },
            restore = { saved ->
                val snapshot = json.decodeFromString<Snapshot>(saved)
                TopLevelBackStack(snapshot.stacks, snapshot.topLevelKey, snapshot.startKey)
            }
        )
    }
}
