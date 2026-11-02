package io.github.janetyulin.grounded.data.repository

import io.github.janetyulin.grounded.data.location.LocationProvider
import io.github.janetyulin.grounded.data.model.LastLocation
import io.github.janetyulin.grounded.data.model.UserLocation
import io.github.janetyulin.grounded.domain.model.GeoPoint
import io.github.janetyulin.grounded.testing.InMemoryDataStore
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationRepositoryImplTest {

    private var granted = true

    /** The fix the provider returns; a test can leave it pending to simulate a slow fix. */
    private var nextFix = CompletableDeferred<UserLocation>(UserLocation.Unavailable)
    private val lastLocationStore = InMemoryDataStore(LastLocation())
    private val repository = LocationRepositoryImpl(
        object : LocationProvider {
            override fun hasPermission() = granted
            override suspend fun currentLocation() = if (granted) nextFix.await() else UserLocation.PermissionRequired
        },
        lastLocationStore
    )
    private val fix = UserLocation.Available(GeoPoint(34.15, -118.14), Instant.EPOCH)

    private fun respondWith(location: UserLocation) {
        nextFix = CompletableDeferred(location)
    }

    @Test
    fun `starts unknown until the first check`() {
        assertEquals(UserLocation.Unknown, repository.location.value)
    }

    @Test
    fun `publishes each fix`() = runTest {
        respondWith(fix)

        repository.refresh()

        assertEquals(fix, repository.location.value)
    }

    @Test
    fun `without permission reports it without waiting for a fix`() = runTest {
        granted = false

        repository.refresh()

        assertEquals(UserLocation.PermissionRequired, repository.location.value)
    }

    @Test
    fun `a fresh grant stops asking for permission before the fix arrives`() = runTest {
        granted = false
        repository.refresh()

        granted = true
        nextFix = CompletableDeferred()
        backgroundScope.launch { repository.refresh() }
        runCurrent()
        // Regression: the banner kept asking for the permission until the (slow) fix arrived.
        assertEquals(UserLocation.Unknown, repository.location.value)

        nextFix.complete(fix)
        runCurrent()
        assertEquals(fix, repository.location.value)
    }

    @Test
    fun `keeps the last fix when a later attempt can't get one`() = runTest {
        respondWith(fix)
        repository.refresh()

        respondWith(UserLocation.Unavailable)
        repository.refresh()

        assertEquals(fix, repository.location.value)
    }

    @Test
    fun `a revoked permission replaces the last fix`() = runTest {
        respondWith(fix)
        repository.refresh()

        granted = false
        repository.refresh()

        assertEquals(UserLocation.PermissionRequired, repository.location.value)
    }

    @Test
    fun `each fix is saved for background checks`() = runTest {
        respondWith(fix)

        repository.refresh()

        assertEquals(fix, repository.lastSaved())
    }

    @Test
    fun `nothing is saved before the first fix`() = runTest {
        assertNull(repository.lastSaved())
    }

    @Test
    fun `losing the permission forgets the saved location`() = runTest {
        respondWith(fix)
        repository.refresh()

        granted = false
        repository.refresh()

        assertNull(repository.lastSaved())
    }
}
