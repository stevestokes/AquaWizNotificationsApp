package app.aquawiznotifier

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** Horizontal screen navigation; children can reserve gestures (chart drag and zoom). */
class SwipeNavigationLayout(context: Context, private val navigate: (Boolean) -> Unit) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var horizontal = false
    private var blocked = false

    private fun begin(event: MotionEvent) {
        startX = event.x; startY = event.y; horizontal = false; blocked = false
    }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(event)
            MotionEvent.ACTION_POINTER_DOWN -> blocked = true
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount != 1) blocked = true
                val dx = abs(event.x - startX); val dy = abs(event.y - startY)
                if (!horizontal && dy > slop && dy >= dx) blocked = true
                if (!blocked && dx > slop * 2 && dx > dy * 1.5f) horizontal = true
            }
            MotionEvent.ACTION_CANCEL -> { horizontal = false; blocked = true }
        }
        return horizontal && !blocked
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(event)
            MotionEvent.ACTION_POINTER_DOWN -> blocked = true
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - startX); val dy = abs(event.y - startY)
                if (event.pointerCount != 1 || (!horizontal && dy > slop && dy >= dx)) blocked = true
                if (!blocked && dx > slop * 2 && dx > dy * 1.5f) horizontal = true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - startX
                if (horizontal && !blocked && abs(dx) >= maxOf(48f * resources.displayMetrics.density, width * .15f)) navigate(dx < 0)
                else if (!horizontal && !blocked) performClick()
                horizontal = false
            }
            MotionEvent.ACTION_CANCEL -> { horizontal = false; blocked = true }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
