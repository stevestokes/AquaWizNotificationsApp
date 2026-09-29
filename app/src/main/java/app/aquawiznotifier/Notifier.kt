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
    private const val ID_UPDATE = 1003

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

    fun measurement(context: Context, serial: String, m: Measurement, previousKh: Double?) {
        if (!allowed(context)) return
        ensureChannel(context)
        val time = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(m.measuredAt)
        val change = previousKh?.let {
            val delta = m.kh - it
            val arrow = when { delta > 0.0001 -> "↑"; delta < -0.0001 -> "↓"; else -> "→" }
            "$arrow ${"%.2f".format(abs(delta))}"
        }
        val store = SecureStore(context)
        val title = buildString {
            append("[")
            append(serial)
            append("] ")
            append("%.2f".format(m.kh))
            append(" dKH")
            if (m.ph != null) {
                append(", ")
                append("%.2f".format(m.ph))
                append(" pH")
            }
        }
        val optional = mutableListOf<String>()
        if (store.showPhOpenAir() && m.phOpenAir != null) optional += "pH(O) " + "%.2f".format(m.phOpenAir)
        if (store.showDeltaPh() && m.deltaPh != null) optional += "ΔpH " + "%+.2f".format(m.deltaPh)
        if (store.showDoseMl() && m.doseMl != null) optional += "Dose " + "%.2f".format(m.doseMl) + " mL"
        val detail = if (optional.isEmpty()) "Measured $time" else optional.joinToString(" • ")
        val subText = buildString {
            append("Measured $time")
            if (change != null) append(" • $change dKH")
        }
        val pending = PendingIntent.getActivity(
            context,
            1,
            officialAquaWizLaunchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aquawiz_notify)
            .setContentTitle(title)
            .setContentText(detail)
            .setSubText(subText)
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

    fun updateAvailable(context: Context, release: ReleaseInfo) {
        if (!allowed(context)) return
        ensureChannel(context)
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(release.htmlUrl))
        val pending = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aquawiz_notify)
            .setContentTitle("AquaWiz Notifier update available")
            .setContentText("Version ${release.versionName} is available. Tap to update.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_UPDATE, n)
    }

    fun test(context: Context) {
        measurement(context, "KH1-00-05117", Measurement(8.42, java.time.Instant.now(), rawId = "test", ph = 8.27, phOpenAir = 8.35, deltaPh = -0.08, doseMl = 1.20), 8.35)
    }


    private fun officialAquaWizLaunchIntent(context: Context): Intent {
        val packageManager = context.packageManager
        val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val official = packageManager.queryIntentActivities(launcherQuery, PackageManager.MATCH_ALL)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map { it to it.loadLabel(packageManager).toString().trim() }
            .sortedByDescending { (_, label) -> label.equals("AquaWiz", ignoreCase = true) }
            .firstOrNull { (_, label) ->
                label.equals("AquaWiz", ignoreCase = true) ||
                    label.contains("AquaWiz", ignoreCase = true)
            }
            ?.first
            ?.activityInfo

        return if (official != null) {
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(official.packageName, official.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        } else {
            Intent(context, MainActivity::class.java)
        }
    }

    private fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
