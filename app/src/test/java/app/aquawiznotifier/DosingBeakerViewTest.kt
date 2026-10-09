package app.aquawiznotifier

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DosingBeakerViewTest {
    @Test fun fillUsesConfiguredContainerVolumeAndHandlesEmptyAndOverfilledContainers() {
        assertEquals(.255f, dosingFraction(255.0, 1000.0)!!, .00001f)
        assertEquals(0f, dosingFraction(0.0, 1000.0)!!, 0f)
        assertEquals(1f, dosingFraction(1500.0, 1000.0)!!, 0f)
        assertNull(dosingFraction(255.0, null))
        assertNull(dosingFraction(null, 1000.0))
        assertNull(dosingFraction(-1.0, 1000.0))
        assertNull(dosingFraction(255.0, 0.0))
        assertNull(dosingFraction(Double.NaN, 1000.0))
        assertNull(dosingFraction(255.0, Double.POSITIVE_INFINITY))
    }

    @Test fun capacitySurvivesReloadAndIsSeparateForEachController() {
        val context = RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("aquawiz_notifier", Context.MODE_PRIVATE).edit().clear().commit()
        SecureStore(context).setDosingContainerMl("KH-A", 1000.0)
        val restored = SecureStore(context)
        assertEquals(1000.0, restored.dosingContainerMl("kh-a")!!, 0.0)
        assertNull(restored.dosingContainerMl("KH-B"))
        restored.setDosingContainerMl("KH-B", 500.0)
        assertEquals(1000.0, restored.dosingContainerMl("KH-A")!!, 0.0)
        assertEquals(500.0, restored.dosingContainerMl("KH-B")!!, 0.0)
    }

    @Test fun accessibilityReportsRemainingMlAndPercentageWithoutInventingUnknownValues() {
        val view = DosingBeakerView(RuntimeEnvironment.getApplication())
        view.setLevel("KH-A", 255.0, 1000.0, 300.0)
        assertTrue(view.contentDescription.toString().contains("255 mL remaining"))
        assertTrue(view.contentDescription.toString().contains("26 percent full"))
        view.setLevel("KH-B", null, null, null)
        assertTrue(view.contentDescription.toString().contains("remaining volume unavailable"))
        assertFalse(view.contentDescription.toString().contains("percent full"))
    }
}
