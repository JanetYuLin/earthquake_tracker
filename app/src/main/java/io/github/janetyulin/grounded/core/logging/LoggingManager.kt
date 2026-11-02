package io.github.janetyulin.grounded.core.logging

/**
 * App-wide logging abstraction. Depend on this instead of a concrete logger so callers stay testable
 * and the backing implementation can be swapped (e.g. remote crash reporting) without touching them.
 *
 * Callers pass an explicit [tag]: when logging goes through a wrapper, stack-derived tags would
 * point at the wrapper instead of the call site.
 */
interface LoggingManager {
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)
}
