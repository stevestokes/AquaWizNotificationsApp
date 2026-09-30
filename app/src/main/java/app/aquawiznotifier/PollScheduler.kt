package app.aquawiznotifier

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

object PollScheduler {
    private const val UNIQUE_WORK = "aquawiz_measurement_poll"

    fun schedule(context: Context, desired: Instant) {
        val delayMs = Duration.between(Instant.now(), desired).toMillis().coerceAtLeast(1_000L)
        SecureStore(context).setNextPollEpochMs(desired.toEpochMilli())
        val request = OneTimeWorkRequest.Builder(MeasurementWorker::class.java)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun start(context: Context, immediate: Boolean = false) {
        val store = SecureStore(context)
        val last = store.lastMeasurementEpochMs()?.let(Instant::ofEpochMilli)
        val desired = if (immediate) Instant.now().plusSeconds(1) else PollCadence.nextRun(Instant.now(), last, store.measurementIntervalMinutes())
        schedule(context, desired)
    }

    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
}
