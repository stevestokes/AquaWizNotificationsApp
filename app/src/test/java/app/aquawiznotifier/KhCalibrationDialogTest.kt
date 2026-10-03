package app.aquawiznotifier

import android.app.Activity
import android.view.View
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class KhCalibrationDialogTest {
    @Test fun loadsThenAllowsChangedValuePreventsDuplicateWritesAndKeepsErrorsEditable() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        lateinit var loaded: (Result<Double>) -> Unit
        lateinit var saved: (Result<Unit>) -> Unit
        val writes = mutableListOf<BigDecimal>()
        val sheet = KhCalibrationDialog(activity, null, load = { loaded = it }, submit = { value, done -> writes += value; saved = done })
        sheet.show()
        val root = sheet.window!!.decorView
        val input = root.findViewWithTag<EditText>("calibration_input")
        val button = root.findViewWithTag<View>("calibration_save")
        assertFalse(button.isEnabled)
        loaded(Result.success(7.86))
        assertEquals("7.86", input.text.toString()); assertFalse(button.isEnabled)
        input.setText("8.2"); assertTrue(button.isEnabled)
        button.performClick(); button.performClick()
        assertEquals(listOf(BigDecimal("8.2")), writes)
        assertFalse(root.findViewWithTag<View>("calibration_cancel").isEnabled)
        saved(Result.failure(IllegalStateException("Server rejected this value")))
        assertTrue(sheet.isShowing); assertTrue(button.isEnabled)
        assertEquals("Server rejected this value", root.findViewWithTag<TextView>("calibration_status").text.toString())
        input.setText("0"); button.performClick()
        assertEquals(0, writes.last().signum())
        saved(Result.success(Unit)); assertFalse(sheet.isShowing)
        activity.finish()
    }
    @Test fun loadFailureAndCancelNeverSubmitCalibration() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var writes = 0
        val sheet = KhCalibrationDialog(activity, 8.0, load = { it(Result.failure(IllegalStateException("Offline"))) }, submit = { _, _ -> writes++ })
        sheet.show()
        val root = sheet.window!!.decorView
        root.findViewWithTag<EditText>("calibration_input").setText("7.5")
        assertFalse(root.findViewWithTag<View>("calibration_save").isEnabled)
        root.findViewWithTag<View>("calibration_cancel").performClick()
        assertEquals(0, writes); assertFalse(sheet.isShowing)
        activity.finish()
    }
}
