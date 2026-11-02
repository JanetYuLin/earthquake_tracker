package io.github.janetyulin.grounded.ui.map

import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import org.junit.Assert.assertEquals
import org.junit.Test

class LifecycleStepperTest {

    private val calls = mutableListOf<String>()
    private val stepper = LifecycleStepper({ calls += "start" }, { calls += "resume" }, { calls += "pause" }, {
        calls +=
            "stop"
    })

    @Test
    fun `a shown map follows the host up to resumed`() {
        stepper.update(RESUMED, shown = true)

        assertEquals(listOf("start", "resume"), calls)
        assertEquals(RESUMED, stepper.current)
    }

    @Test
    fun `hiding the map stops it but never destroys it, and showing resumes it`() {
        stepper.update(RESUMED, shown = true)
        calls.clear()

        stepper.update(RESUMED, shown = false)
        assertEquals(listOf("pause", "stop"), calls)
        assertEquals(CREATED, stepper.current)

        calls.clear()
        stepper.update(RESUMED, shown = true)
        assertEquals(listOf("start", "resume"), calls)
    }

    @Test
    fun `a hidden map stays stopped while the host comes and goes`() {
        stepper.update(RESUMED, shown = false)
        stepper.update(CREATED, shown = false)
        stepper.update(RESUMED, shown = false)

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `going to the background pauses and stops a shown map, in order`() {
        stepper.update(RESUMED, shown = true)
        calls.clear()

        stepper.update(STARTED, shown = true)
        stepper.update(CREATED, shown = true)

        assertEquals(listOf("pause", "stop"), calls)
    }

    @Test
    fun `before the host starts, or after it's destroyed, nothing runs`() {
        stepper.update(INITIALIZED, shown = true)
        assertEquals(emptyList<String>(), calls)

        stepper.update(RESUMED, shown = true)
        calls.clear()
        stepper.update(DESTROYED, shown = true)
        assertEquals(listOf("pause", "stop"), calls)
    }

    @Test
    fun `repeating the same update is a no-op`() {
        stepper.update(RESUMED, shown = true)
        calls.clear()

        stepper.update(RESUMED, shown = true)

        assertEquals(emptyList<String>(), calls)
    }
}
