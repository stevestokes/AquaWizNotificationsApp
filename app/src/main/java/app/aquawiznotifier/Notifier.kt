package app.aquawiznotifier

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

object Notifier {
    private const val CHANNEL = "measurements"
    private const val ID_MEASUREMENT = 1001
    private const val ID_ERROR = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "AquaWiz measurements", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notification for each new AquaWiz KH measurement"
                }
            )
        }
    }

    fun measurement(context: Context, m: Measurement, previousKh: Double?) {
        if (!allowed(context)) return
        ensureChannel(context)
        val time = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(m.measuredAt)
        val change = previousKh?.let {
            val delta = m.kh - it
            val arrow = when { delta > 0.0001 -> "↑"; delta < -0.0001 -> "↓"; else -> "→" }
            "$arrow ${"%.2f".format(abs(delta))}"
        }
        val detail = buildString {
            append("Measured $time")
            if (change != null) append("  •  $change dKH")
            if (m.ph != null) append("  •  pH ${"%.2f".format(m.ph)}")
        }
        val pending = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aquawiz_notify)
            .setContentTitle("AquaWiz · ${"%.2f".format(m.kh)} dKH")
            .setContentText(detail)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(m.measuredAt.toEpochMilli())
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_MEASUREMENT, n)
    }

    fun signInRequired(context: Context) {
        if (!allowed(context)) return
        ensureChannel(context)
        val pending = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aquawiz_notify)
            .setContentTitle("AquaWiz sign-in required")
            .setContentText("Open AquaWiz Notifier to reconnect.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_ERROR, n)
    }

    fun test(context: Context) {
        measurement(context, Measurement(8.12, java.time.Instant.now(), rawId = "test"), 8.05)
    }

    private fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
