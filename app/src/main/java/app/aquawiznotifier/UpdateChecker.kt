package app.aquawiznotifier

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val htmlUrl: String,
)

object UpdateChecker {
    private const val OWNER = "stevestokes"
    private const val REPO = "AquaWizNotificationsApp"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest"
    private const val UNIQUE_PERIODIC_WORK = "aquawiz_release_update_periodic"
    private const val UNIQUE_MANUAL_WORK = "aquawiz_release_update_now"
    private const val STARTUP_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    internal const val MANUAL_CHECK = "manual_update_check"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context)
        val periodic = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(networkConstraints)
            .build()
        manager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic,
        )

        val lastCheck = SecureStore(context).lastUpdateCheckEpochMs() ?: 0L
        if (System.currentTimeMillis() - lastCheck >= STARTUP_CHECK_INTERVAL_MS) {
            enqueueNow(context, replace = false)
        }
    }

    fun checkNow(context: Context) = enqueueNow(context, replace = true)

    private fun enqueueNow(context: Context, replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setConstraints(networkConstraints)
            .setInputData(Data.Builder().putBoolean(MANUAL_CHECK, replace).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_MANUAL_WORK,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    internal fun isNewer(latestTag: String, installedVersion: String): Boolean {
        val latest = parseVersion(latestTag) ?: return false
        val installed = parseVersion(installedVersion) ?: return false
        val width = maxOf(latest.size, installed.size)
        for (i in 0 until width) {
            val left = latest.getOrElse(i) { 0 }
            val right = installed.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    internal fun notifyIfNewer(context: Context, release: ReleaseInfo, manual: Boolean) {
        if (!isNewer(release.tagName, BuildConfig.VERSION_NAME)) return
        val store = SecureStore(context)
        if (manual || store.lastUpdateNotifiedVersion() != release.versionName) {
            Notifier.updateAvailable(context, release)
            store.appendActivity("Update available: " + release.versionName)
            store.setLastUpdateNotifiedVersion(release.versionName)
        }
    }

    private fun parseVersion(value: String): List<Int>? {
        val normalized = value.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        if (normalized.isBlank()) return null
        val parts = normalized.split(".")
        if (parts.isEmpty()) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        return numbers
    }

    internal fun fetchLatestRelease(): ReleaseInfo {
        val conn = (URI(LATEST_RELEASE_API).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "AquaWizNotifier/" + BuildConfig.VERSION_NAME)
        }
        try {
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException(
                    if (status == 404) {
                        "No public GitHub release found. The repository must be public and have at least one published release."
                    } else {
                        "GitHub Releases HTTP " + status + if (text.isNotBlank()) ": " + text.take(300) else ""
                    }
                )
            }
            val json = JSONObject(text)
            val tag = json.getString("tag_name")
            val url = json.getString("html_url")
            return ReleaseInfo(tag, tag.removePrefix("v").removePrefix("V"), url)
        } finally {
            conn.disconnect()
        }
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val store = SecureStore(applicationContext)
        store.setLastUpdateCheckEpochMs(System.currentTimeMillis())
        store.appendActivity("Checking GitHub Releases for an app update")
        return try {
            val release = UpdateChecker.fetchLatestRelease()
            store.setLatestReleaseVersion(release.versionName)
            store.setLatestReleaseUrl(release.htmlUrl)
            store.setLastUpdateError(null)
            store.appendActivity("Latest GitHub release: " + release.versionName)

            UpdateChecker.notifyIfNewer(applicationContext, release, inputData.getBoolean(UpdateChecker.MANUAL_CHECK, false))
            Result.success()
        } catch (e: Exception) {
            store.setLastUpdateError(e.message ?: e.javaClass.simpleName)
            store.appendActivity("Update check failed: " + (e.message ?: e.javaClass.simpleName))
            Result.success()
        }
    }
}
