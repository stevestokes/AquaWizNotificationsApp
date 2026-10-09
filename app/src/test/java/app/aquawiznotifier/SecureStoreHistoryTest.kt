package app.aquawiznotifier

import android.app.Activity
import android.content.Context
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SecureStoreHistoryTest {
    @Test fun upgradeRemovesCapturedDailySummaryAndRepairsDosingTotalAndPollingAnchor() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val prefs = activity.getSharedPreferences("aquawiz_notifier", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        java.io.File(activity.filesDir, "measurement-support").deleteRecursively()
        val fixture = JSONObject(javaClass.getResource("/daily-summary-capture.json")!!.readText()).getJSONArray("captures")
        val store = SecureStore(activity)
        for (i in 0 until fixture.length()) store.captureIngestion(fixture.getJSONObject(i))
        val aggregateAt = 1791547200000L
        val rawRows = fixture.getJSONObject(0).getJSONObject("graph").getJSONArray("results")
        val realAt = rawRows.getJSONArray(rawRows.length() - 1).getLong(0)
        fun row(at: Long, kh: Double, ph: Double, open: Double, dose: Double) = JSONObject()
            .put("serial", "KH-A").put("rawId", "graph:$at").put("measuredAt", at)
            .put("kh", kh).put("ph", ph).put("phOpenAir", open).put("doseMl", dose)
        val aggregate = row(aggregateAt, 8.166, 8.327, 8.353, 60.0)
        val real = row(realAt, 8.262, 8.263, 8.329, 60.0)
        // Another historical 8 AM test is not covered by the aggregate evidence.
        val legitimate = row(1791374400000L, 8.9, 8.2, 8.3, 0.0)
        prefs.edit().putString("device", "KH-A").putString("measurement_history", JSONArray().put(real).put(aggregate).put(legitimate).toString())
            .putString("last_measurement_json", aggregate.toString()).putLong("last_measurement_ms", aggregateAt)
            .putLong("next_poll_ms", aggregateAt + 3600000).commit()
        val history = store.measurementHistory()
        assertEquals(2, history.size)
        assertFalse(history.any { it.second.measuredAt.toEpochMilli() == aggregateAt })
        assertTrue(history.any { it.second.measuredAt.toEpochMilli() == 1791374400000L })
        assertEquals(60.0, history.sumOf { it.second.doseMl ?: 0.0 }, 0.0)
        assertEquals(realAt, store.lastMeasurementEpochMs())
        assertNull(store.nextPollEpochMs())
        assertEquals(realAt, store.lastStoredMeasurement()!!.second.measuredAt.toEpochMilli())
        activity.finish()
    }
    @Test fun futureCacheCannotOverrideHomeOrLeavePollingAnchoredInFuture() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val prefs = activity.getSharedPreferences("aquawiz_notifier", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val now = Instant.now()
        val real = now.minusSeconds(300).toEpochMilli()
        val future = now.plusSeconds(8 * 3600).toEpochMilli()
        fun row(at: Long, kh: Double) = JSONObject().put("serial", "KH-A").put("kh", kh).put("measuredAt", at)
        prefs.edit().putString("device", "KH-A")
            .putString("measurement_history", JSONArray().put(row(future, 8.181)).put(row(real, 8.312)).toString())
            .putString("last_measurement_json", row(future, 8.181).toString())
            .putLong("last_measurement_ms", future).putLong("next_poll_ms", future + 3600000).commit()
        val store = SecureStore(activity)
        assertEquals(1, store.measurementHistory().size)
        assertEquals(8.312, store.lastStoredMeasurement()!!.second.kh, 0.00001)
        assertFalse(store.lastMeasurementEpochMs()?.let { it > now.toEpochMilli() } == true)
        assertEquals(real, store.lastMeasurementEpochMs())
        assertNull(store.nextPollEpochMs())
        store.saveMeasurement("KH-A", Measurement(8.181, Instant.ofEpochMilli(future)))
        assertEquals(1, store.measurementHistory().size)
        activity.finish()
    }
}
