package io.github.janetyulin.grounded.ui.navigation

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelBackStackTest {

    @Test
    fun `starts with only the start tab`() {
        val stack = TopLevelBackStack(RouteMap)

        assertEquals(listOf(RouteMap), stack.backStack.toList())
        assertEquals(RouteMap, stack.topLevelKey)
    }

    @Test
    fun `switching tabs keeps each tab's history`() {
        val stack = TopLevelBackStack(RouteMap)
        stack.add(RouteEarthquakeDetail("a"))
        stack.addTopLevel(RouteList)
        stack.addTopLevel(RouteMap)

        assertEquals(listOf(RouteList, RouteMap, RouteEarthquakeDetail("a")), stack.backStack.toList())
    }

    @Test
    fun `back pops within a tab, then returns to the start tab, then exits`() {
        val stack = TopLevelBackStack(RouteMap)
        stack.addTopLevel(RouteList)
        stack.add(RouteEarthquakeDetail("a"))

        assertTrue(stack.removeLast())
        assertEquals(RouteList, stack.backStack.last())

        assertTrue(stack.removeLast())
        assertEquals(RouteMap, stack.topLevelKey)
        assertEquals(RouteMap, stack.backStack.last())

        assertFalse(stack.removeLast())
    }

    @Test
    fun `back from the start tab exits even after visiting other tabs, keeping their history`() {
        val stack = TopLevelBackStack(RouteMap)
        stack.addTopLevel(RouteList)
        stack.add(RouteEarthquakeDetail("a"))
        stack.addTopLevel(RouteMap)

        assertFalse(stack.removeLast())

        stack.addTopLevel(RouteList)
        assertEquals(RouteEarthquakeDetail("a"), stack.backStack.last())
    }

    @Test
    fun `back arrow applies only above a tab's root`() {
        val stack = TopLevelBackStack(RouteMap)
        assertFalse(stack.canGoBackWithinTab)

        stack.add(RouteEarthquakeDetail("a"))
        assertTrue(stack.canGoBackWithinTab)

        stack.addTopLevel(RouteList)
        assertFalse(stack.canGoBackWithinTab)
    }

    @Test
    fun `popWithinTab never pops a tab's root, even when called twice`() {
        val stack = TopLevelBackStack(RouteMap)
        stack.add(RouteEarthquakeDetail("a"))

        stack.popWithinTab()
        stack.popWithinTab()

        assertEquals(listOf(RouteMap), stack.backStack.toList())
        assertFalse(stack.canGoBackWithinTab)
    }

    @Test
    fun `system back never empties the stack, even when called twice quickly`() {
        val stack = TopLevelBackStack(RouteMap)
        stack.add(RouteEarthquakeDetail("a"))

        assertTrue(stack.removeLast())
        assertFalse(stack.removeLast())
        assertFalse(stack.removeLast())

        assertEquals(listOf(RouteMap), stack.backStack.toList())
    }

    @Test
    fun `saver round-trips every tab stack and the selected tab`() {
        val stack = TopLevelBackStack(RouteMap)
        stack.add(RouteEarthquakeDetail("a"))
        stack.addTopLevel(RouteAlerts)

        val saved = with(TopLevelBackStack.Saver) { SaverScope { true }.save(stack) }!!
        val restored = TopLevelBackStack.Saver.restore(saved)!!

        assertEquals(stack.backStack.toList(), restored.backStack.toList())
        assertEquals(RouteAlerts, restored.topLevelKey)
    }
}
