package app.aquawiznotifier

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SwipeNavigationLayoutTest {
    private fun send(view: SwipeNavigationLayout, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0, 100, action, x, y, 0)
        view.onTouchEvent(event); event.recycle()
    }
    @Test fun leftAndRightSwipesNavigateInOppositeDirections() {
        val directions = mutableListOf<Boolean>()
        val view = SwipeNavigationLayout(RuntimeEnvironment.getApplication()) { directions.add(it) }
        send(view, MotionEvent.ACTION_DOWN, 1000f, 100f)
        send(view, MotionEvent.ACTION_MOVE, 200f, 105f)
        send(view, MotionEvent.ACTION_UP, 200f, 105f)
        send(view, MotionEvent.ACTION_DOWN, 200f, 100f)
        send(view, MotionEvent.ACTION_MOVE, 1000f, 100f)
        send(view, MotionEvent.ACTION_UP, 1000f, 100f)
        assertEquals(listOf(true, false), directions)
    }
    @Test fun verticalScrollTapCancelledAndMultiTouchGesturesDoNotNavigate() {
        val directions = mutableListOf<Boolean>()
        val view = SwipeNavigationLayout(RuntimeEnvironment.getApplication()) { directions.add(it) }
        send(view, MotionEvent.ACTION_DOWN, 1000f, 100f)
        send(view, MotionEvent.ACTION_MOVE, 990f, 900f)
        send(view, MotionEvent.ACTION_UP, 200f, 900f)
        send(view, MotionEvent.ACTION_DOWN, 1000f, 100f)
        send(view, MotionEvent.ACTION_UP, 999f, 100f)
        send(view, MotionEvent.ACTION_DOWN, 1000f, 100f)
        send(view, MotionEvent.ACTION_MOVE, 200f, 100f)
        send(view, MotionEvent.ACTION_CANCEL, 200f, 100f)
        send(view, MotionEvent.ACTION_UP, 200f, 100f)
        send(view, MotionEvent.ACTION_DOWN, 1000f, 100f)
        send(view, MotionEvent.ACTION_POINTER_DOWN, 1000f, 100f)
        send(view, MotionEvent.ACTION_MOVE, 200f, 100f)
        send(view, MotionEvent.ACTION_UP, 200f, 100f)
        assertTrue(directions.isEmpty())
    }
}
