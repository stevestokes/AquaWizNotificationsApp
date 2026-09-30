package app.aquawiznotifier

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.max

class HomeChartView(context: Context) : View(context) {
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = sp(11f) }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE7E7E7.toInt(); strokeWidth = dp(1f) }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; strokeWidth = dp(1f) }
    private val bandPaint = Paint().apply { color = 0x0E5B34FF }
    private val limitPaint = Paint().apply { color = 0x775B34FF; strokeWidth = dp(1f); pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f) }
    private var items = emptyList<Measurement>()
    private var visible = ChartSeries.values().filter { it.defaultVisible }.toSet()
    private var styles = emptyMap<ChartSeries, ChartLineStyle>()
    private var limits: Pair<Double?, Double?> = null to null
    private var selected: Int? = null
    private var zoom = 1.0
    private var center = 0.5
    var onSelected: ((Measurement) -> Unit)? = null

    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val left = dp(44f)
            val fraction = ((detector.focusX - left) / (width - dp(8f) - left)).coerceIn(0f, 1f).toDouble()
            val oldZoom = zoom
            zoom = (zoom * detector.scaleFactor).coerceIn(1.0, 20.0)
            center += (fraction - 0.5) * (1.0 / oldZoom - 1.0 / zoom)
            center = center.coerceIn(0.5 / zoom, 1 - 0.5 / zoom)
            invalidate()
            return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onDoubleTap(e: MotionEvent): Boolean { resetZoom(); return true }
    })

    fun setSeriesVisibility(series: Set<ChartSeries>, lineStyles: Map<ChartSeries, ChartLineStyle>) {
        visible = series
        styles = lineStyles
        invalidate()
    }
    fun resetZoom() { zoom = 1.0; center = 0.5; invalidate() }
    fun setMeasurements(measurements: List<Measurement>, lowLimit: Double? = null, highLimit: Double? = null) {
        val sorted = measurements.sortedBy { it.measuredAt }
        if (items == sorted && limits == (lowLimit to highLimit)) return
        val selectedTime = selected?.let { items.getOrNull(it)?.measuredAt }
        items = sorted
        limits = lowLimit to highLimit
        selected = selectedTime?.let { time -> items.indexOfFirst { it.measuredAt == time }.takeIf { it >= 0 } }
        invalidate()
    }
    private fun timeWindow(): Pair<Long, Long> {
        val start = items.first().measuredAt.toEpochMilli()
        val end = items.last().measuredAt.toEpochMilli()
        val span = max(60_000L, end - start)
        return (start + span * (center - 0.5 / zoom)).toLong() to (start + span * (center + 0.5 / zoom)).toLong()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = dp(44f); val right = width - dp(8f)
        val top = dp(12f); val bottom = height - dp(42f)
        if (right <= left || bottom <= top) return
        val (yMin, yMax) = ChartBounds.calculate(items, limits.first, limits.second)
        fun y(value: Double) = bottom - (bottom - top) * ((value - yMin) / (yMax - yMin)).toFloat()
        limits.first?.let { low -> limits.second?.let { high ->
            canvas.drawRect(left, y(high), right, y(low), bandPaint)
            canvas.drawLine(left, y(low), right, y(low), limitPaint)
            canvas.drawLine(left, y(high), right, y(high), limitPaint)
        } }
        for (i in 0..5) {
            val value = yMin + (yMax - yMin) * i / 5
            val yy = y(value)
            canvas.drawLine(left, yy, right, yy, gridPaint)
            canvas.drawText("%.1f".format(value), dp(3f), yy + dp(4f), axisPaint)
        }
        if (items.isEmpty() || visible.isEmpty()) {
            val message = if (items.isEmpty()) "No measurements in this range" else "Select a line above to display it"
            canvas.drawText(message, left, (top + bottom) / 2, axisPaint)
            return
        }
        val (minTime, maxTime) = timeWindow()
        fun x(m: Measurement) = left + (right - left) * ((m.measuredAt.toEpochMilli() - minTime).toDouble() / max(1, maxTime - minTime)).toFloat()
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        visible.forEach { series ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = series.color; style = Paint.Style.STROKE; strokeWidth = dp(2.5f); strokeCap = Paint.Cap.ROUND
                pathEffect = when (styles[series]) {
                    ChartLineStyle.DASHED -> DashPathEffect(floatArrayOf(dp(8f), dp(5f)), 0f)
                    ChartLineStyle.DOTTED -> DashPathEffect(floatArrayOf(dp(1f), dp(5f)), 0f)
                    else -> null
                }
            }
            val path = Path()
            var continuing = false
            items.forEach { m ->
                val value = series.value(m)
                if (value == null || !value.isFinite()) continuing = false
                else {
                    if (continuing) path.lineTo(x(m), y(value)) else path.moveTo(x(m), y(value))
                    continuing = true
                    canvas.drawCircle(x(m), y(value), dp(1.5f), paint)
                }
            }
            canvas.drawPath(path, paint)
            selected?.let { index -> series.value(items[index])?.let { value -> canvas.drawCircle(x(items[index]), y(value), dp(4f), paint) } }
        }
        selected?.let { canvas.drawLine(x(items[it]), top, x(items[it]), bottom, markerPaint) }
        canvas.restore()
        // Full requested date/time, two edge labels so the mobile labels do not collide.
        val first = AppDates.format(java.time.Instant.ofEpochMilli(minTime))
        val last = AppDates.format(java.time.Instant.ofEpochMilli(maxTime))
        val fw = axisPaint.measureText(first); val lw = axisPaint.measureText(last)
        canvas.drawText(first, left, bottom + dp(24f), axisPaint)
        if (fw + lw + dp(10f) < right - left) canvas.drawText(last, right - lw, bottom + dp(24f), axisPaint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (items.isEmpty()) return false
        scale.onTouchEvent(event)
        gestures.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!scale.isInProgress && event.pointerCount == 1) {
                    val (start, end) = timeWindow()
                    val left = dp(44f); val right = width - dp(8f)
                    if (right <= left) return false
                    val target = start + ((event.x.coerceIn(left, right) - left) / (right - left) * (end - start)).toLong()
                    selected = items.indices.minByOrNull { abs(items[it].measuredAt.toEpochMilli() - target) }
                    selected?.let { onSelected?.invoke(items[it]); contentDescription = AppDates.format(items[it].measuredAt) + ", KH " + items[it].kh }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> { parent?.requestDisallowInterceptTouchEvent(false); performClick() }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun sp(value: Float) = value * resources.displayMetrics.scaledDensity
}
