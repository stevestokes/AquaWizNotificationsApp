package app.aquawiznotifier

import android.content.Context
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.ceil

/** Keeps the unit beside the number on its baseline, even after text autosizing. */
class MeasurementValueView(context: Context, private val value: TextView, private val unit: TextView) : ViewGroup(context) {
    private val gap = AwUi.dp(context, 4)
    init { addView(value); addView(unit) }
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val width = MeasureSpec.getSize(widthSpec)
        val height = resolveSize(AwUi.dp(context, 58), heightSpec)
        unit.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST))
        val valueWidth = (width - unit.measuredWidth - gap).coerceAtLeast(0)
        value.measure(MeasureSpec.makeMeasureSpec(valueWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        setMeasuredDimension(resolveSize(width, widthSpec), height)
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        value.layout(0, 0, value.measuredWidth, measuredHeight)
        alignUnit()
    }
    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        alignUnit()
        super.dispatchDraw(canvas)
    }
    private fun alignUnit() {
        val unitLeft = ceil(value.paint.measureText(value.text.toString())).toInt().coerceAtMost(value.measuredWidth) + gap
        val unitTop = (value.baseline - unit.baseline).coerceIn(0, (measuredHeight - unit.measuredHeight).coerceAtLeast(0))
        unit.layout(unitLeft, unitTop, unitLeft + unit.measuredWidth, unitTop + unit.measuredHeight)
    }
}
