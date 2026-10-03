package app.aquawiznotifier

import android.app.Activity
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PullRefreshViewTest {
    @Test fun downwardPullOnlyInterceptsWhenContentIsAtTop() {
        var scrolled = true
        val context = RuntimeEnvironment.getApplication()
        val refresh = PullRefreshView(context, { scrolled }) {}
        refresh.addView(View(context))
        refresh.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
        refresh.layout(0, 0, 360, 640)
        fun touch(action: Int, y: Float): Boolean {
            val event = MotionEvent.obtain(0, 10, action, 100f, y, 0)
            return try { refresh.onInterceptTouchEvent(event) } finally { event.recycle() }
        }
        touch(MotionEvent.ACTION_DOWN, 20f)
        assertFalse(touch(MotionEvent.ACTION_MOVE, 240f))
        scrolled = false
        touch(MotionEvent.ACTION_DOWN, 20f)
        assertTrue(touch(MotionEvent.ACTION_MOVE, 240f))
    }

    @Test fun accessibleRefreshCoalescesUntilSpinnerIsFinishedAndSystemButtonsUseWhiteContrast() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var calls = 0
        val refresh = PullRefreshView(activity, { false }) { calls++ }
        refresh.addView(View(activity))
        assertTrue(refresh.performAccessibilityAction(R.id.action_refresh_measurements, null))
        refresh.performAccessibilityAction(R.id.action_refresh_measurements, null)
        assertEquals(1, calls)
        refresh.isRefreshing = false
        refresh.performAccessibilityAction(R.id.action_refresh_measurements, null)
        assertEquals(2, calls)
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        SystemNavigation.configure(activity.window)
        @Suppress("DEPRECATION")
        assertEquals(0, activity.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        @Suppress("DEPRECATION")
        assertEquals(Color.BLACK, activity.window.navigationBarColor)
        activity.finish()
    }
}
