package app.aquawiznotifier

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class KhSettingsDialogTest {
    private val summary = DeviceSummary(khTarget = 8.5, khDeviation = 0.5, dosingRemainingMl = 100.0, dosingWarningMl = 100.0,
        dosingField5 = "10", dosingField6 = "0", measurementSchedule = "12207")
    @Test fun targetLoadsAllValuesAndOnlySubmitsChangesKeepingFailuresEditable() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        lateinit var loaded: (Result<DeviceSummary>) -> Unit
        lateinit var completed: (Result<Unit>) -> Unit
        val writes = mutableListOf<KhTargetSettings>()
        val sheet = KhSettingsDialog(activity, KhSettingsSection.TARGET, "KH1-A", load = { loaded = it },
            submit = { target, dosing, done -> assertNull(dosing); writes += target!!; completed = done })
        sheet.show(); shadowOf(Looper.getMainLooper()).idle()
        val root = sheet.window!!.decorView
        val apply = root.findViewWithTag<View>("settings_apply")
        val input = root.findViewWithTag<EditText>("settings_target")
        assertFalse(apply.isEnabled); assertFalse(input.isEnabled)
        loaded(Result.success(summary))
        assertEquals("8.5", input.text.toString()); assertFalse(apply.isEnabled)
        assertEquals("22:00", root.findViewWithTag<TextView>("settings_from").text.toString())
        assertEquals("07:00", root.findViewWithTag<TextView>("settings_to").text.toString())
        input.setText("8.6"); assertTrue(apply.isEnabled)
        apply.performClick(); apply.performClick()
        assertEquals(1, writes.size); assertEquals(BigDecimal("8.6"), writes.single().target)
        assertFalse(root.findViewWithTag<View>("settings_cancel").isEnabled)
        completed(Result.failure(IllegalStateException("Rejected settings")))
        assertTrue(sheet.isShowing); assertTrue(apply.isEnabled)
        assertEquals("Rejected settings", root.findViewWithTag<TextView>("settings_status").text.toString())
        apply.performClick(); completed(Result.success(Unit)); assertFalse(sheet.isShowing)
        activity.finish()
    }
    @Test fun dosingZeroMaximumIsPreservedAndCacheRoundTripsSettings() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val store = SecureStore(activity)
        store.saveDeviceSummary("KH1-A", summary)
        assertEquals(summary, store.deviceSummary("KH1-A"))
        var received: KhDosingSettings? = null
        val sheet = KhSettingsDialog(activity, KhSettingsSection.DOSING, "KH1-A", load = { it(Result.success(summary)) },
            submit = { target, dosing, done -> assertNull(target); received = dosing; done(Result.success(Unit)) })
        sheet.show(); shadowOf(Looper.getMainLooper()).idle()
        val root = sheet.window!!.decorView
        assertEquals("0", root.findViewWithTag<EditText>("settings_maximum").text.toString())
        root.findViewWithTag<EditText>("settings_remaining").setText("250")
        root.findViewWithTag<View>("settings_apply").performClick()
        assertEquals(0, received!!.maxPerHour.signum()); assertEquals(BigDecimal("250"), received!!.remaining)
        assertFalse(sheet.isShowing); activity.finish()
    }
    @Test fun unavailableSettingsAndCancelNeverWrite() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var writes = 0
        val sheet = KhSettingsDialog(activity, KhSettingsSection.TARGET, "KH1-A", load = { it(Result.success(DeviceSummary(khTarget = 8.5))) },
            submit = { _, _, _ -> writes++ })
        sheet.show(); shadowOf(Looper.getMainLooper()).idle()
        val root = sheet.window!!.decorView
        assertFalse(root.findViewWithTag<View>("settings_apply").isEnabled)
        root.findViewWithTag<View>("settings_cancel").performClick()
        assertEquals(0, writes); assertFalse(sheet.isShowing); activity.finish()
    }
}
