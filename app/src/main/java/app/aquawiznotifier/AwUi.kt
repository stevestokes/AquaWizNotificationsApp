package app.aquawiznotifier

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.TextView

object AwUi {
    val BORDER = 0xFFCBD3DE.toInt()
    val INK = 0xFF253348.toInt()
    val BLUE = 0xFF287DF0.toInt()
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun surface(context: Context, color: Int = Color.WHITE, radius: Int = 20, border: Boolean = true) =
        RoundedSurface(color, dp(context, radius).toFloat(), if (border) dp(context, 2).toFloat() else 0f)
    fun label(context: Context, text: String, size: Float, bold: Boolean = false) = TextView(context).apply {
        this.text = text; textSize = size; setTextColor(INK); includeFontPadding = false
        typeface = context.resources.getFont(if (bold) R.font.aw_extrabold else R.font.aw_regular)
    }
    fun button(context: Context, text: String, primary: Boolean = false) = Button(context).apply {
        this.text = text; isAllCaps = false; textSize = 14f; includeFontPadding = false
        typeface = context.resources.getFont(R.font.aw_extrabold)
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        gravity = Gravity.CENTER; stateListAnimator = null
        setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
        styleButton(this, primary)
    }
    fun styleButton(button: Button, primary: Boolean) {
        button.setTextColor(if (primary) Color.WHITE else INK)
        button.background = RippleDrawable(ColorStateList.valueOf(0x22287DF0),
            surface(button.context, if (primary) BLUE else Color.WHITE, 12, !primary), null)
    }
}

/** Stroke center and corner radius share the same inset, keeping corners exactly parallel. */
private class RoundedSurface(private val fill: Int, private val radius: Float, private val stroke: Float) : android.graphics.drawable.Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: android.graphics.Canvas) {
        val rect = android.graphics.RectF(bounds)
        paint.style = android.graphics.Paint.Style.FILL; paint.color = fill
        canvas.drawRoundRect(rect, radius, radius, paint)
        if (stroke > 0f) {
            val inset = stroke / 2f
            rect.inset(inset, inset)
            paint.style = android.graphics.Paint.Style.STROKE; paint.color = AwUi.BORDER; paint.strokeWidth = stroke
            canvas.drawRoundRect(rect, (radius - inset).coerceAtLeast(0f), (radius - inset).coerceAtLeast(0f), paint)
        }
    }
    override fun getOutline(outline: android.graphics.Outline) { outline.setRoundRect(bounds, radius) }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
