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
            val fraction = ((detector.focusX - left) / (plotRight() - left).coerceAtLeast(1f)).coerceIn(0f, 1f).toDouble()
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
        val left = dp(44f); val right = plotRight()
        val top = dp(24f); val axisBottom = height - dp(42f)
        val showDelta = ChartSeries.DELTA in visible
        val showDose = ChartSeries.DOSE in visible
        val bottom = axisBottom - if (showDose) dp(76f) else 0f
        val doseTop = bottom + dp(28f)
        if (right <= left || bottom <= top) return
        val window = if (items.isEmpty()) null else timeWindow()
        val inView = if (window == null) items else items.filterIndexed { index, m ->
            val time = m.measuredAt.toEpochMilli()
            // Include adjoining endpoints so segments crossing a zoom edge remain in bounds.
            time in window.first..window.second ||
                (index < items.lastIndex && time < window.first && items[index + 1].measuredAt.toEpochMilli() >= window.first) ||
                (index > 0 && time > window.second && items[index - 1].measuredAt.toEpochMilli() <= window.second)
        }
        val primary = ChartBounds.calculate(inView, limits.first, limits.second, visible)
        val delta = ChartBounds.delta(inView)
        val dose = ChartBounds.dose(inView)
        fun mapY(value: Double, bounds: Pair<Double, Double>, from: Float, to: Float) =
            to - (to - from) * ((value - bounds.first) / (bounds.second - bounds.first)).toFloat()
        fun y(value: Double) = mapY(value, primary, top, bottom)
        fun seriesY(series: ChartSeries, value: Double) = when (series) {
            ChartSeries.DELTA -> mapY(value, delta, top, bottom)
            ChartSeries.DOSE -> mapY(value, dose, doseTop, axisBottom)
            else -> y(value)
        }
        limits.first?.takeIf { it.isFinite() }?.let { low -> limits.second?.takeIf { it.isFinite() && it >= low }?.let { high ->
            canvas.drawRect(left, y(high), right, y(low), bandPaint)
            canvas.drawLine(left, y(low), right, y(low), limitPaint)
            canvas.drawLine(left, y(high), right, y(high), limitPaint)
        } }
        axisPaint.color = Color.GRAY
        canvas.drawText("dKH / pH", left, top - dp(10f), axisPaint)
        for (i in 0..5) {
            val value = primary.first + (primary.second - primary.first) * i / 5
            val yy = y(value)
            canvas.drawLine(left, yy, right, yy, gridPaint)
            canvas.drawText("%.1f".format(value), dp(3f), yy + dp(4f), axisPaint)
        }
        if (showDelta) {
            axisPaint.color = ChartSeries.DELTA.color
            canvas.drawText("ΔpH", right + dp(6f), top - dp(10f), axisPaint)
            val decimals = if (delta.second - delta.first < 0.1) 3 else 2
            for (i in 0..5) {
                val value = delta.first + (delta.second - delta.first) * i / 5
                val yy = mapY(value, delta, top, bottom)
                canvas.drawLine(right, yy, right + dp(3f), yy, markerPaint)
                canvas.drawText("%.$decimals".plus("f").format(value), right + dp(6f), yy + dp(4f), axisPaint)
            }
        }
        if (showDose) {
            axisPaint.color = ChartSeries.DOSE.color
            canvas.drawText("Dose · mL", left, doseTop - dp(9f), axisPaint)
            axisPaint.color = Color.GRAY
            for (value in listOf(0.0, dose.second)) {
                val yy = seriesY(ChartSeries.DOSE, value)
                canvas.drawLine(left, yy, right, yy, gridPaint)
                canvas.drawText("%.1f".format(value), dp(3f), yy + dp(4f), axisPaint)
            }
        }
        axisPaint.color = Color.GRAY
        if (items.isEmpty() || visible.isEmpty()) {
            val message = if (items.isEmpty()) "No measurements in this range" else "Select a line above to display it"
            canvas.drawText(message, left, (top + bottom) / 2, axisPaint)
            return
        }
        val (minTime, maxTime) = timeWindow()
        fun x(m: Measurement) = left + (right - left) * ((m.measuredAt.toEpochMilli() - minTime).toDouble() / max(1, maxTime - minTime)).toFloat()
        visible.forEach { series ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = series.color; style = Paint.Style.STROKE; strokeWidth = dp(2.5f); strokeCap = Paint.Cap.ROUND
                pathEffect = when (styles[series]) {
                    ChartLineStyle.DASHED -> DashPathEffect(floatArrayOf(dp(8f), dp(5f)), 0f)
                    ChartLineStyle.DOTTED -> DashPathEffect(floatArrayOf(dp(1f), dp(5f)), 0f)
                    else -> null
                }
            }
            canvas.save()
            canvas.clipRect(left - dp(4f), if (series == ChartSeries.DOSE) doseTop - dp(4f) else top - dp(4f),
                right + dp(4f), if (series == ChartSeries.DOSE) axisBottom + dp(4f) else bottom + dp(4f))
            val path = Path()
            var continuing = false
            items.forEach { m ->
                val value = series.value(m)
                if (value == null || !value.isFinite()) continuing = false
                else {
                    if (continuing) path.lineTo(x(m), seriesY(series, value)) else path.moveTo(x(m), seriesY(series, value))
                    continuing = true
                    canvas.drawCircle(x(m), seriesY(series, value), dp(1.5f), paint)
                }
            }
            canvas.drawPath(path, paint)
            selected?.let { index -> series.value(items[index])?.takeIf { it.isFinite() }?.let { value ->
                canvas.drawCircle(x(items[index]), seriesY(series, value), dp(4f), paint)
            } }
            canvas.restore()
        }
        selected?.let {
            canvas.save(); canvas.clipRect(left, top, right, axisBottom)
            canvas.drawLine(x(items[it]), top, x(items[it]), bottom, markerPaint)
            if (showDose) canvas.drawLine(x(items[it]), doseTop, x(items[it]), axisBottom, markerPaint)
            canvas.restore()
        }
        val first = AppDates.format(java.time.Instant.ofEpochMilli(minTime))
        val last = AppDates.format(java.time.Instant.ofEpochMilli(maxTime))
        val fw = axisPaint.measureText(first); val lw = axisPaint.measureText(last)
        canvas.drawText(first, left, axisBottom + dp(24f), axisPaint)
        if (fw + lw + dp(10f) < right - left) canvas.drawText(last, right - lw, axisBottom + dp(24f), axisPaint)
    }
    private fun plotRight() = width - dp(if (ChartSeries.DELTA in visible) 56f else 8f)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (items.isEmpty()) return false
        scale.onTouchEvent(event)
        gestures.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!scale.isInProgress && event.pointerCount == 1) {
                    val (start, end) = timeWindow()
                    val left = dp(44f); val right = plotRight()
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
