package app.aquawiznotifier

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Instant

class MeasurementWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val store = SecureStore(applicationContext)
        store.setLastPollEpochMs(System.currentTimeMillis())
        store.appendActivity("Checking AquaWiz for a new measurement")
        if (store.authPaused()) {
            store.appendActivity("AquaWiz polling skipped: monitoring is paused after an authentication conflict")
            return Result.success()
        }
        val session = store.session() ?: return Result.success()
        val serial = store.selectedDevice() ?: session.devices.firstOrNull() ?: return Result.success()
        val api = AquaWizApi(store.baseUrl())

        try {
            val measurement = api.latestMeasurement(session, serial) { store.saveDeviceSummary(serial, it) }

            store.setLastError(null)
            val previousFingerprint = store.lastFingerprint()
            val previousKh = store.lastKh()
            val isFirstBaseline = previousFingerprint == null
            val isNew = previousFingerprint != measurement.fingerprint

            if (isNew) {
                store.saveMeasurement(serial, measurement)
                if (!isFirstBaseline) {
                    Notifier.measurement(applicationContext, serial, measurement, previousKh)
                    store.appendActivity("New measurement: [" + serial + "] " + "%.2f".format(measurement.kh) + " dKH" + (measurement.ph?.let { ", " + "%.2f".format(it) + " pH" } ?: ""))
                } else {
                    store.appendActivity("Baseline established for " + serial + " at " + "%.2f".format(measurement.kh) + " dKH")
                }
                store.setLastFingerprint(measurement.fingerprint)
                store.setLastMeasurementEpochMs(measurement.measuredAt.toEpochMilli())
                store.setLastKh(measurement.kh)
            }

            val anchor = store.lastMeasurementEpochMs()?.let(Instant::ofEpochMilli) ?: measurement.measuredAt
            val next = PollCadence.nextRun(Instant.now(), anchor, store.measurementIntervalMinutes())
            PollScheduler.schedule(applicationContext, next)
            return Result.success()
        } catch (e: AquaWizApi.ApiException) {
            store.setLastError(e.message ?: "AquaWiz API error")
            store.appendActivity("AquaWiz API error: " + (e.message ?: "unknown error"))
            if (e.status == 401 || e.status == 403) {
                store.setAuthPaused(true)
                store.clearNextPollEpochMs()
                store.appendActivity("Monitoring paused after AquaWiz rejected the notifier session. Automatic re-login is disabled to avoid logging the official AquaWiz app out.")
                Notifier.signInRequired(applicationContext)
                return Result.success()
            }
            PollScheduler.schedule(applicationContext, Instant.now().plusSeconds(15 * 60))
            return Result.success()
        } catch (e: Exception) {
            store.setLastError(e.message ?: e.javaClass.simpleName)
            store.appendActivity("Background check error: " + (e.message ?: e.javaClass.simpleName))
            PollScheduler.schedule(applicationContext, Instant.now().plusSeconds(15 * 60))
            return Result.success()
        }
    }
}
