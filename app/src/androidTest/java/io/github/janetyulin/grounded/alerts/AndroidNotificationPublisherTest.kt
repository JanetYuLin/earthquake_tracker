package io.github.janetyulin.grounded.alerts

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Posts a real notification, so resource formatting and channel setup are exercised end to end. */
@RunWith(AndroidJUnit4::class)
class AndroidNotificationPublisherTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val publisher = AndroidNotificationPublisher(context)

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()
    }

    @Test
    fun showEarthquakeAlert_postsFormattedNotification() {
        publisher.createChannels()

        publisher.showEarthquakeAlert(EarthquakeAlert("ci1", 4.6, "5 km NW of Ridgecrest, CA", 180))

        val posted = awaitNotification("ci1".hashCode())
        assertEquals("M4.6 earthquake 180 km away", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("5 km NW of Ridgecrest, CA", posted.extras.getString(Notification.EXTRA_TEXT))
    }

    /** Posting is asynchronous; wait briefly for the system to report it. */
    private fun awaitNotification(id: Int, timeoutMs: Long = 5_000): Notification {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            notificationManager.activeNotifications.firstOrNull { it.id == id }?.let { return it.notification }
            SystemClock.sleep(100)
        }
        throw AssertionError("Notification $id was not posted within ${timeoutMs}ms")
    }
}
