package app.aquawiznotifier

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.TextView

object AwUi {
    val BORDER get() = if (AppAppearance.dark) 0xFF405069.toInt() else 0xFFCBD3DE.toInt()
    val INK get() = if (AppAppearance.dark) 0xFFE6EDF7.toInt() else 0xFF253348.toInt()
    val SURFACE get() = if (AppAppearance.dark) 0xFF1B2636.toInt() else Color.WHITE
    val BACKGROUND get() = if (AppAppearance.dark) 0xFF101824.toInt() else 0xFFF2F4F7.toInt()
    val HOME_BACKGROUND get() = if (AppAppearance.dark) BACKGROUND else 0xFFF2F2F2.toInt()
    val VALUE get() = if (AppAppearance.dark) INK else Color.BLACK
    val MUTED get() = if (AppAppearance.dark) 0xFFACBCD0.toInt() else 0xFF65758B.toInt()
    val SECONDARY get() = if (AppAppearance.dark) MUTED else Color.DKGRAY
    val INPUT get() = if (AppAppearance.dark) 0xFF253246.toInt() else 0xFFF5F7FA.toInt()
    val ICON_SURFACE get() = if (AppAppearance.dark) INPUT else 0xFFF1F1F1.toInt()
    val ALTERNATE_ROW get() = if (AppAppearance.dark) 0xFF243249.toInt() else 0xFFEEF4FB.toInt()
    val GRID get() = if (AppAppearance.dark) 0xFF38475C.toInt() else 0xFFE7E7E7.toInt()
    val SHIMMER get() = if (AppAppearance.dark) 0xFF28384E.toInt() else 0xFFEAF0F6.toInt()
    val WARNING get() = if (AppAppearance.dark) 0xFFFFAB59.toInt() else 0xFFE76C00.toInt()
    val DANGER get() = if (AppAppearance.dark) 0xFFFF9898.toInt() else 0xFFB64242.toInt()
    val SUCCESS get() = if (AppAppearance.dark) 0xFF7DE3A4.toInt() else 0xFF26985A.toInt()
    val BLUE = 0xFF287DF0.toInt()
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun surface(context: Context, color: Int = SURFACE, radius: Int = 20, border: Boolean = true): android.graphics.drawable.Drawable =
        RoundedSurface(color, dp(context, radius).toFloat(), if (border) dp(context, 2).toFloat() else 0f)
    fun drawBorder(canvas: android.graphics.Canvas, rect: android.graphics.RectF, radius: Float, width: Float) {
        // Fill the ring between parallel contours, with no shadow or stroke overlap at corners.
        val inner = android.graphics.RectF(rect).apply { inset(width, width) }
        val path = android.graphics.Path().apply {
            fillType = android.graphics.Path.FillType.EVEN_ODD
            addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW)
            addRoundRect(inner, (radius - width).coerceAtLeast(0f), (radius - width).coerceAtLeast(0f), android.graphics.Path.Direction.CW)
        }
        canvas.drawPath(path, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = BORDER })
    }
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
            surface(button.context, if (primary) BLUE else SURFACE, 12, !primary), null)
    }
}

/** Filled concentric contours keep the border equally thin along straight lines and corners. */
private class RoundedSurface(private val fill: Int, private val radius: Float, private val stroke: Float) : android.graphics.drawable.Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: android.graphics.Canvas) {
        val rect = android.graphics.RectF(bounds)
        paint.style = android.graphics.Paint.Style.FILL; paint.color = fill
        canvas.drawRoundRect(rect, radius, radius, paint)
        if (stroke > 0f) {
            AwUi.drawBorder(canvas, rect, radius, stroke)
        }
    }
    override fun getOutline(outline: android.graphics.Outline) { outline.setRoundRect(bounds, radius) }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
