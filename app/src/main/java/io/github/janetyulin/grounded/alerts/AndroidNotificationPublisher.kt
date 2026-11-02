package io.github.janetyulin.grounded.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.janetyulin.grounded.MainActivity
import io.github.janetyulin.grounded.R
import javax.inject.Inject

/**
 * [NotificationPublisher] backed by [NotificationManagerCompat]. Tapping an alert opens its detail screen.
 */
class AndroidNotificationPublisher @Inject constructor(@param:ApplicationContext private val context: Context) :
    NotificationPublisher {

    private val notificationManager = NotificationManagerCompat.from(context)

    override fun createChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NEARBY,
                context.getString(R.string.notification_channel_nearby),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.notification_channel_nearby_description) }
        )
    }

    override fun showEarthquakeAlert(alert: EarthquakeAlert) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_EARTHQUAKE_ID, alert.earthquakeId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.earthquakeId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_NEARBY)
            .setSmallIcon(R.drawable.ic_safety)
            .setContentTitle(context.getString(R.string.notification_title, alert.magnitude, alert.distanceKm))
            .setContentText(alert.place)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(alert.earthquakeId.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_NEARBY = "nearby_earthquakes"
    }
}
