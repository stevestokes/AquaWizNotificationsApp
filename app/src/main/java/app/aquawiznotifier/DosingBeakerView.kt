package app.aquawiznotifier

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Null means the volume or container size is unknown; never assume a full container. */
internal fun dosingFraction(remainingMl: Double?, containerMl: Double?): Float? {
    if (remainingMl == null || !remainingMl.isFinite() || remainingMl < 0 ||
        containerMl == null || !containerMl.isFinite() || containerMl <= 0) return null
    return (remainingMl / containerMl).coerceIn(0.0, 1.0).toFloat()
}

class DosingBeakerView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vessel = Path()
    private val liquid = Path()
    private val outline = Path()
    private val typeface = resources.getFont(R.font.aw_extrabold)
    private var fraction: Float? = null
    private var displayedFill = 0f
    private var phase = 0f
    private var low = false
    private var device: String? = null
    private var active = false
    private var fillAnimator: ValueAnimator? = null
    private var waveAnimator: ValueAnimator? = null
    private val blue = 0xFF0089FF.toInt()
    private val orange = 0xFFE76C00.toInt()

    init {
        isClickable = true; isFocusable = true
        background = AwUi.surface(context, 0xFFF1F6FB.toInt(), 12, false)
    }

    fun setLevel(serial: String, remainingMl: Double?, containerMl: Double?, warningMl: Double?) {
        val next = dosingFraction(remainingMl, containerMl)
        val changedDevice = device != serial
        device = serial
        low = remainingMl != null && warningMl != null && remainingMl < warningMl
        contentDescription = "KH dosing: " + (remainingMl?.let { "%.0f mL remaining".format(it) } ?: "remaining volume unavailable") +
            (next?.let { ", ${(it * 100).roundToInt()} percent full" } ?: ", full container volume not set") +
            ". Tap to set full container volume."
        if (changedDevice) { fillAnimator?.cancel(); displayedFill = 0f }
        if (next != fraction || changedDevice) {
            fraction = next
            animateFill()
        }
        updateWave(); invalidate()
    }

    private fun animateFill() {
        fillAnimator?.cancel()
        val target = fraction ?: 0f
        if (!active || !ValueAnimator.areAnimatorsEnabled()) { displayedFill = target; return }
        fillAnimator = ValueAnimator.ofFloat(displayedFill, target).apply {
            duration = 700
            addUpdateListener { displayedFill = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun updateWave() {
        val shouldAnimate = active && ValueAnimator.areAnimatorsEnabled() && fraction?.let { it > 0 && it < 1 } == true
        if (!shouldAnimate) { waveAnimator?.cancel(); waveAnimator = null; phase = 0f }
        else if (waveAnimator == null) {
            waveAnimator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
                duration = 2800; repeatCount = ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        val becomingVisible = isVisible && !active
        active = isVisible
        if (!active) { fillAnimator?.cancel(); displayedFill = fraction ?: 0f }
        else if (becomingVisible) { displayedFill = 0f; animateFill() }
        updateWave()
    }

    override fun onDetachedFromWindow() {
        active = false; fillAnimator?.cancel(); waveAnimator?.cancel(); waveAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val box = RectF(width * .23f, height * .12f, width * .77f, height * .70f)
        vessel.reset()
        vessel.addRoundRect(box, 3 * d, 3 * d, Path.Direction.CW)
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE
        canvas.drawPath(vessel, paint)
        val saved = canvas.save(); canvas.clipPath(vessel)
        if (fraction != null && displayedFill > 0f) {
            val level = box.bottom - box.height() * displayedFill
            val amplitude = if (displayedFill > .02f && displayedFill < .98f) 1.2f * d else 0f
            liquid.reset(); liquid.moveTo(box.left, box.bottom)
            for (step in 0..24) {
                val x = box.left + box.width() * step / 24
                liquid.lineTo(x, level + amplitude * sin(step / 24f * 2 * PI + phase).toFloat())
            }
            liquid.lineTo(box.right, box.bottom); liquid.close()
            paint.color = if (low) orange else blue; paint.alpha = 185
            canvas.drawPath(liquid, paint); paint.alpha = 255
        }
        canvas.restoreToCount(saved)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.6f * d
        paint.color = if (low) orange else 0xFF697584.toInt()
        // Open top, pouring lip, and small graduation marks.
        outline.reset()
        outline.apply {
            moveTo(box.left - 3 * d, box.top); lineTo(box.left, box.top + 3 * d)
            lineTo(box.left, box.bottom - 3 * d)
            quadTo(box.left, box.bottom, box.left + 3 * d, box.bottom)
            lineTo(box.right - 3 * d, box.bottom)
            quadTo(box.right, box.bottom, box.right, box.bottom - 3 * d)
            lineTo(box.right, box.top); lineTo(box.right + 3 * d, box.top)
        }
        canvas.drawPath(outline, paint)
        for (tick in 1..3) {
            val y = box.bottom - box.height() * tick / 4
            canvas.drawLine(box.right - 6 * d, y, box.right - 2 * d, y, paint)
        }
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
        paint.textSize = 10 * resources.displayMetrics.scaledDensity
        paint.typeface = typeface
        paint.color = if (low) orange else 0xFF40536B.toInt()
        canvas.drawText(fraction?.let { "${(it * 100).roundToInt()}%" } ?: "Set size", width / 2f, height * .90f, paint)
    }
}
