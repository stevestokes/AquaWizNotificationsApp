package app.aquawiznotifier

import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class KhCalibrationEventTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val at = Instant.now().minusSeconds(600)
    @Before fun clear() {
        context.getSharedPreferences("aquawiz_notifier", Context.MODE_PRIVATE).edit().clear().commit()
        java.io.File(context.filesDir, "measurement-support").deleteRecursively()
    }
    @Test fun localEventsSurviveRestartRefreshAndDisconnectWithoutChangingMeasurements() {
        val store = SecureStore(context)
        store.setSelectedDevice("KH1-A")
        val original = Measurement(7.9, at.minusSeconds(60), doseMl = 2.16)
        store.saveMeasurement("KH1-A", original)
        val before = store.measurementHistory()
        val event = store.recordKhCalibration(" kh1-a ", 8.3, at)
        assertEquals(7.9, event.previousKh!!, 0.0)
        assertEquals(before, store.measurementHistory())
        store.saveMeasurement("KH1-A", original)
        assertEquals(listOf(event), SecureStore(context).calibrationEvents("kh1-a"))
        assertTrue(store.calibrationEvents("KH1-B").isEmpty())
        store.clearMonitoringState(); store.clearSession()
        assertEquals(listOf(event), SecureStore(context).calibrationEvents())
        assertTrue(store.activityLog().contains("8.30 dKH"))
        val export = JSONObject(store.supportData()).getJSONArray("calibrations")
        assertEquals(event.id, export.getJSONObject(0).getString("id"))
    }
    @Test fun historyShowsCalibrationSeparatelyAndKeepsMeasurementNotesEditable() {
        val store = SecureStore(context)
        val m = Measurement(7.9, at.minusSeconds(60))
        store.recordKhCalibration("KH1-A", 8.3, at)
        val adapter = HistoryAdapter(context, store)
        adapter.submit(listOf("KH1-A" to m))
        assertEquals(2, adapter.count)
        assertNull(adapter.measurementAt(0)); assertFalse(adapter.isEnabled(0))
        val eventRow = adapter.getView(0, null, null) as TextView
        assertTrue(eventRow.text.contains("8.30"))
        assertEquals(AwUi.CALIBRATION, eventRow.currentTextColor)
        assertEquals(m, adapter.measurementAt(1)!!.second); assertTrue(adapter.isEnabled(1))
        store.recordKhCalibration("KH1-A", 0.0, at.plusSeconds(1))
        adapter.submit(listOf("KH1-A" to m))
        assertEquals(3, adapter.count)
        assertTrue((adapter.getView(0, eventRow, null) as TextView).text.contains("disabled"))
    }
    @Test fun graphIncludesLaterAdjustmentAndCanInspectItWithKhHidden() {
        val chart = HomeChartView(context)
        val event = KhCalibrationEvent("event", "KH1-A", at, 9.3, 7.9)
        val m = Measurement(7.9, at.minusSeconds(3600))
        chart.setMeasurements(listOf(m))
        chart.setCalibrations(listOf(event, event.copy(id = "disabled", newKh = 0.0, recordedAt = at.plusSeconds(60))))
        val timeWindow = chart.javaClass.getDeclaredMethod("timeWindow").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val times = timeWindow.invoke(chart) as Pair<Long, Long>
        assertEquals(at.toEpochMilli(), times.second)
        val bounds = ChartBounds.calculate(listOf(m), null, null, emptySet(), null, listOf(event.newKh))
        assertTrue(bounds.first < 9.3 && bounds.second > 9.3)
        chart.setSeriesVisibility(emptySet(), emptyMap())
        chart.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY))
        chart.layout(0, 0, 600, 320)
        chart.draw(Canvas(Bitmap.createBitmap(600, 320, Bitmap.Config.ARGB_8888)))
        var picked: KhCalibrationEvent? = null
        chart.onCalibrationSelected = { picked = it }
        val right = chart.javaClass.getDeclaredMethod("plotRight").apply { isAccessible = true }.invoke(chart) as Float
        val touch = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, right, 100f, 0)
        assertTrue(chart.onTouchEvent(touch)); touch.recycle()
        assertEquals(event, picked)
        assertTrue(chart.contentDescription.toString().contains("calibration"))
        chart.setMeasurements(emptyList())
        chart.draw(Canvas(Bitmap.createBitmap(600, 320, Bitmap.Config.ARGB_8888)))
    }
}
