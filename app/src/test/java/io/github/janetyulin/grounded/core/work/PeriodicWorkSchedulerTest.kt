package io.github.janetyulin.grounded.core.work

import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodicWorkSchedulerTest {

    private val work = PeriodicWork("job", Duration.ofMinutes(15))
    private val calls = mutableListOf<String>()
    private val scheduler = object : PeriodicWorkScheduler {
        override fun schedule(work: PeriodicWork) {
            calls += "schedule ${work.name}"
        }
        override fun cancel(name: String) {
            calls += "cancel $name"
        }
    }

    @Test
    fun `keepScheduledWhile follows the flag and ignores repeats`() = runTest {
        val enabled = MutableStateFlow(false)
        backgroundScope.launch { scheduler.keepScheduledWhile(enabled, work) }
        runCurrent()

        enabled.value = true
        runCurrent()
        enabled.value = false
        runCurrent()

        assertEquals(listOf("cancel job", "schedule job", "cancel job"), calls)
    }
}
