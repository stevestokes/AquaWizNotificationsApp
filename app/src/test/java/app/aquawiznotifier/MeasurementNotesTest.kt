package app.aquawiznotifier

import android.app.Activity
import android.content.Context
import android.database.DataSetObserver
import android.view.View
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MeasurementNotesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val measurement = Measurement(7.6, Instant.parse("2026-10-03T15:00:01Z"), rawId = "notification")
    @Before fun clearData() { context.getSharedPreferences("aquawiz_notifier", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun notesSurviveNewStoreAndServerMergeAndAreIsolatedByDeviceAndMinute() {
        val store = SecureStore(context)
        store.saveMeasurement("controller-a", measurement)
        store.saveMeasurementNote(" controller-a ", measurement, " Added bicarbonate\nRetest tonight ")
        val server = measurement.copy(measuredAt = measurement.measuredAt.plusSeconds(29), rawId = "graph:server", ph = 8.1)
        store.saveMeasurements("CONTROLLER-A", listOf(server))
        val restarted = SecureStore(context)
        val (serial, merged) = restarted.measurementHistory().single()
        assertEquals(8.1, merged.ph!!, 0.001)
        assertEquals("Added bicarbonate\nRetest tonight", restarted.measurementNote(serial, merged))
        assertEquals("", restarted.measurementNote("controller-b", merged))
        assertEquals("", restarted.measurementNote(serial, merged.copy(measuredAt = merged.measuredAt.plusSeconds(60))))
        restarted.saveMeasurementNote(serial, merged, " \n ")
        assertEquals("", SecureStore(context).measurementNote(serial, merged))
    }

    @Test fun noteEditsRefreshUnchangedReadingsAndRecycledRowsDoNotLeakNotes() {
        val store = SecureStore(context)
        val adapter = HistoryAdapter(context, store)
        val items = listOf("controller-a" to measurement, "controller-a" to measurement.copy(measuredAt = measurement.measuredAt.minusSeconds(3600)))
        adapter.submit(items)
        var notifications = 0
        adapter.registerDataSetObserver(object : DataSetObserver() { override fun onChanged() { notifications++ } })
        store.saveMeasurementNote("controller-a", measurement, "Test note")
        adapter.submit(items)
        assertEquals(1, notifications)
        val row = adapter.getView(0, null, null)
        assertEquals("Test note", row.findViewWithTag<TextView>("measurement_note").text.toString())
        assertTrue(adapter.isEnabled(0))
        val recycled = adapter.getView(1, row, null)
        assertEquals(View.GONE, recycled.findViewWithTag<View>("measurement_note").visibility)
        assertFalse(recycled.contentDescription.toString().contains("Test note"))
    }

    @Test fun bottomEditorSavesEditsCancelsAndAllowsRemovingNote() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val store = SecureStore(activity)
        var saves = 0
        fun editor() = MeasurementNoteDialog(activity, "controller-a", measurement, store) { saves++ }.apply { show() }
        val first = editor()
        first.window!!.decorView.findViewWithTag<EditText>("note_input").setText("Added 100 mL")
        first.window!!.decorView.findViewWithTag<View>("note_save").performClick()
        assertEquals("Added 100 mL", store.measurementNote("controller-a", measurement))
        assertFalse(first.isShowing)
        val canceled = editor()
        val input = canceled.window!!.decorView.findViewWithTag<EditText>("note_input")
        assertEquals("Added 100 mL", input.text.toString())
        input.setText("Discard this")
        canceled.window!!.decorView.findViewWithTag<View>("note_cancel").performClick()
        assertEquals("Added 100 mL", store.measurementNote("controller-a", measurement))
        val cleared = editor()
        cleared.window!!.decorView.findViewWithTag<EditText>("note_input").setText("")
        cleared.window!!.decorView.findViewWithTag<View>("note_save").performClick()
        assertEquals("", store.measurementNote("controller-a", measurement))
        assertEquals(2, saves)
        activity.finish()
    }
}
