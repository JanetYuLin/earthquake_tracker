package io.github.janetyulin.grounded.core.logging

import javax.inject.Inject
import timber.log.Timber

/**
 * [LoggingManager] backed by Timber. Output depends on which Trees are planted
 * (see [io.github.janetyulin.grounded.GroundedApplication]).
 */
class TimberLoggingManager @Inject constructor() : LoggingManager {

    override fun info(tag: String, message: String) {
        Timber.tag(tag).i(message)
    }

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        Timber.tag(tag).w(throwable, message)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        Timber.tag(tag).e(throwable, message)
    }
}
