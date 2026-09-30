package app.aquawiznotifier

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.widget.LinearLayout

/** Draws one solid 1dp border at the actual card bounds, after clipped child content. */
class HeroCardLayout(context: Context, private val cornerRadius: Int = 18) : LinearLayout(context) {
    private val radius = AwUi.dp(context, cornerRadius).toFloat()
    private val borderWidth = resources.displayMetrics.density
    init {
        background = AwUi.surface(context, radius = cornerRadius, border = false)
        elevation = 0f
    }
    override fun dispatchDraw(canvas: Canvas) {
        val inner = RectF(borderWidth, borderWidth, width - borderWidth, height - borderWidth)
        val innerRadius = (radius - borderWidth).coerceAtLeast(0f)
        val clip = Path().apply { addRoundRect(inner, innerRadius, innerRadius, Path.Direction.CW) }
        val saved = canvas.save()
        canvas.clipPath(clip)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saved)
    }
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        AwUi.drawBorder(canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, borderWidth)
    }
}
