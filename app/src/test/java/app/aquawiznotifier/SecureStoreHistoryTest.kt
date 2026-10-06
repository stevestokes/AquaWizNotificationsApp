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
