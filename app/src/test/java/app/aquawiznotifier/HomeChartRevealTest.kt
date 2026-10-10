package app.aquawiznotifier

import android.animation.ValueAnimator
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class HomeChartRevealTest {
    private fun field(name: String) = HomeChartView::class.java.getDeclaredField(name).apply { isAccessible = true }

    @Test fun repeatedRevealRequestsPreserveTheActiveSweep() {
        val chart = HomeChartView(RuntimeEnvironment.getApplication())
        val animator = ValueAnimator.ofFloat(0f, 1f).apply { duration = 650; start() }
        assertTrue(animator.isStarted)
        field("revealAnimator").set(chart, animator)
        field("reveal").setFloat(chart, .4f)
        chart.revealLines()
        chart.revealLines()
        assertSame(animator, field("revealAnimator").get(chart))
        assertTrue(animator.isStarted)
        assertEquals(.4f, field("reveal").getFloat(chart), 0f)
        animator.cancel()
    }

    @Test fun settingsOnlyUpdatesDoNotReplayMeasurementLines() {
        val chart = HomeChartView(RuntimeEnvironment.getApplication())
        val readings = listOf(Measurement(8.1, Instant.parse("2026-10-10T12:00:00Z")))
        chart.setMeasurements(readings, 7.8, 8.5, 8.2)
        field("reveal").setFloat(chart, .6f)
        chart.setMeasurements(readings, 7.9, 8.6, 8.3)
        assertEquals(.6f, field("reveal").getFloat(chart), 0f)
    }
}
