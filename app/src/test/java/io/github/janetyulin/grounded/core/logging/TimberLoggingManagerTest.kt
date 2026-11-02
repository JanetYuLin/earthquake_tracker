package io.github.janetyulin.grounded.core.logging

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class TimberLoggingManagerTest {

    private data class LogEntry(val priority: Int, val tag: String?, val message: String, val throwable: Throwable?)

    private val entries = mutableListOf<LogEntry>()
    private val recordingTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            entries += LogEntry(priority, tag, message, t)
        }
    }
    private val loggingManager = TimberLoggingManager()

    @Before
    fun setUp() {
        Timber.plant(recordingTree)
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun `info logs at INFO priority with the given tag`() {
        loggingManager.info("Tag", "hello")

        assertEquals(LogEntry(Log.INFO, "Tag", "hello", null), entries.single())
    }

    @Test
    fun `warn logs at WARN priority with the throwable`() {
        val cause = IllegalStateException("boom")

        loggingManager.warn("Tag", "careful", cause)

        val entry = entries.single()
        assertEquals(Log.WARN, entry.priority)
        assertEquals("Tag", entry.tag)
        assertSame(cause, entry.throwable)
    }

    @Test
    fun `error logs at ERROR priority with the throwable`() {
        val cause = IllegalStateException("boom")

        loggingManager.error("Tag", "failed", cause)

        val entry = entries.single()
        assertEquals(Log.ERROR, entry.priority)
        assertEquals("Tag", entry.tag)
        assertSame(cause, entry.throwable)
    }
}
