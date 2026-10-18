package app.aquawiznotifier

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppAppearanceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @After fun reset() {
        AppAppearance.setMode(context, AppearanceMode.LIGHT)
        AppAppearance.apply(context)
    }
    @Test fun fixedModesOverrideAndroidAndSystemModeTracksOnlyNightFlag() {
        for (flag in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_UNDEFINED)) {
            assertFalse(AppAppearance.usesDark(AppearanceMode.LIGHT, flag))
            assertTrue(AppAppearance.usesDark(AppearanceMode.DARK, flag))
        }
        assertTrue(AppAppearance.usesDark(AppearanceMode.SYSTEM, Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_CAR))
        assertFalse(AppAppearance.usesDark(AppearanceMode.SYSTEM, Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_DESK))
        assertFalse(AppAppearance.usesDark(AppearanceMode.SYSTEM, Configuration.UI_MODE_NIGHT_UNDEFINED))
    }
    @Test fun choicePersistsAndAppliedThemeUsesCurrentAndroidConfiguration() {
        AppAppearance.setMode(context, AppearanceMode.SYSTEM)
        assertEquals(AppearanceMode.SYSTEM, AppAppearance.mode(context))
        AppAppearance.apply(context, Configuration.UI_MODE_NIGHT_YES)
        assertTrue(AppAppearance.dark)
        AppAppearance.apply(context, Configuration.UI_MODE_NIGHT_NO)
        assertFalse(AppAppearance.dark)
        assertEquals(AppearanceMode.SYSTEM, AppAppearance.mode(context))
        assertEquals(AppearanceMode.LIGHT, AppearanceMode.from("invalid"))
    }
    @Test fun darkPanelsLabelsChartAndSystemBarRemainReadable() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        try {
            AppAppearance.setMode(activity, AppearanceMode.DARK)
            AppAppearance.apply(activity)
            assertEquals(AwUi.INK, AwUi.label(activity, "Reading", 16f).currentTextColor)
            assertNotEquals(Color.WHITE, AwUi.SURFACE)
            assertNotEquals(Color.BLACK, AwUi.VALUE)
            fun luminance(color: Int): Double {
                fun channel(n: Int): Double { val v = n / 255.0; return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
                return .2126 * channel(Color.red(color)) + .7152 * channel(Color.green(color)) + .0722 * channel(Color.blue(color))
            }
            fun contrast(a: Int, b: Int): Double {
                val x = luminance(a); val y = luminance(b)
                return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
            }
            assertTrue(contrast(AwUi.INK, AwUi.SURFACE) >= 4.5)
            assertTrue(contrast(AwUi.MUTED, AwUi.ALTERNATE_ROW) >= 4.5)
            ChartSeries.values().forEach { assertTrue(it.name, contrast(it.color, AwUi.SURFACE) >= 3.0) }
            val sheet = BottomEditorDialog(activity)
            sheet.show()
            assertNotNull(sheet.window)
            SystemNavigation.configure(activity.window)
            @Suppress("DEPRECATION")
            assertEquals(0, activity.window.decorView.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
            sheet.dismiss()
        } finally { controller.pause().stop().destroy() }
    }
}
