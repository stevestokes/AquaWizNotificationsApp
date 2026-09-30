package app.aquawiznotifier

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan

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

    fun measurement(context: Context, serial: String, m: Measurement) {
        if (!allowed(context)) return
        ensureChannel(context)
        val store = SecureStore(context)
        val message = MeasurementNotification.text(serial, m, store.showPhOpenAir(), store.showDeltaPh(), store.showDoseMl())
        val text = SpannableString(message).apply { setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        val pending = PendingIntent.getActivity(
            context,
            1,
            officialAquaWizLaunchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aquawiz_notify)
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .build()
        context.getSystemService(NotificationManager::class.java).notify(MeasurementNotification.tag(serial, m), ID_MEASUREMENT, n)
    }

    fun signInRequired(context: Context) {
        if (!allowed(context)) return
        ensureChannel(context)
        val pending = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aquawiz_notify)
            .setContentTitle("AquaWiz monitoring paused")
            .setContentText("Session rejected. Auto-login was stopped to avoid logging the official AquaWiz app out.")
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
        val store = SecureStore(context)
        val stored = store.lastStoredMeasurement()
        if (stored != null) {
            val (serial, m) = stored
            measurement(context, serial, m.copy(measuredAt = java.time.Instant.now(), rawId = "test"))
            return
        }

        val samples = listOf(
            Measurement(8.12, java.time.Instant.now(), rawId = "test", ph = 8.21, phOpenAir = 8.31, deltaPh = -0.10, doseMl = 1.20),
            Measurement(8.24, java.time.Instant.now(), rawId = "test", ph = 8.17, phOpenAir = 8.28, deltaPh = -0.11, doseMl = 1.35),
            Measurement(8.05, java.time.Instant.now(), rawId = "test", ph = 8.26, phOpenAir = 8.34, deltaPh = -0.08, doseMl = 1.10),
        )
        measurement(context, "KH1-00-00000", samples.random())
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
