package app.aquawiznotifier

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.widget.LinearLayout

/** Loading overlay owned by each panel; animation stops when the panel is hidden. */
open class ShimmerLayout(context: Context, private val shimmerRadius: Int = 0) : LinearLayout(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var animator: ValueAnimator? = null
    private var aggregatedVisible = false
    var isLoading = false
        set(value) { field = value; updateAnimation(); invalidate() }

    private fun updateAnimation() {
        if (!isLoading || !aggregatedVisible || !isShown || !isAttachedToWindow || !ValueAnimator.areAnimatorsEnabled()) {
            animator?.cancel(); animator = null; return
        }
        if (animator != null) return
        animator = ValueAnimator.ofFloat(-1f, 2f).apply {
            duration = 1100; repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }
    override fun onVisibilityAggregated(visible: Boolean) { super.onVisibilityAggregated(visible); aggregatedVisible = visible; updateAnimation() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; aggregatedVisible = false; super.onDetachedFromWindow() }
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!isLoading) return
        val radius = shimmerRadius * resources.displayMetrics.density
        paint.shader = null; paint.color = 0xFFEAF0F6.toInt()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
        if (animator != null && width > 0) {
            val x = phase * width
            paint.shader = LinearGradient(x - width * .4f, 0f, x + width * .4f, height.toFloat(),
                intArrayOf(0x00FFFFFF, 0xBFFFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
            paint.shader = null
        }
    }
}
