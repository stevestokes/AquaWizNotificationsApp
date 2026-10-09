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
    private var reveal = 1f
    private var revealAnimator: android.animation.ValueAnimator? = null
    fun revealLines() {
        revealAnimator?.cancel()
        if (!isShown || !android.animation.ValueAnimator.areAnimatorsEnabled()) { reveal = 1f; invalidate(); return }
        reveal = 0f
        revealAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { reveal = it.animatedValue as Float; invalidate() }; start()
        }
    }
    override fun onVisibilityAggregated(visible: Boolean) {
        super.onVisibilityAggregated(visible)
        if (visible) revealLines() else { revealAnimator?.cancel(); reveal = 1f }
    }
    override fun onDetachedFromWindow() { revealAnimator?.cancel(); super.onDetachedFromWindow() }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = sp(11f) }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE7E7E7.toInt(); strokeWidth = dp(1f) }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; strokeWidth = dp(1f) }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; strokeWidth = dp(1f) }
    private val bandPaint = Paint().apply { color = 0x0E5B34FF }
    private val limitPaint = Paint().apply { color = 0x775B34FF; strokeWidth = dp(1f); pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f) }
    private var items = emptyList<Measurement>()
    private var visible = ChartSeries.values().filter { it.defaultVisible }.toSet()
    private var styles = emptyMap<ChartSeries, ChartLineStyle>()
    private var limits: Pair<Double?, Double?> = null to null
    private var targetKh: Double? = null
    private var selected: Int? = null
    private var zoom = 1.0
    private var center = 0.5
    private var overview = false
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
    fun setOverview(enabled: Boolean) { if (overview != enabled) { overview = enabled; invalidate() } }
    fun setMeasurements(measurements: List<Measurement>, lowLimit: Double? = null, highLimit: Double? = null, target: Double? = null) {
        val sorted = HistoryMerge.merge(measurements.map { "chart" to it }).map { it.second }.sortedBy { it.measuredAt }
        if (items == sorted && limits == (lowLimit to highLimit) && targetKh == target) return
        val selectedTime = selected?.let { items.getOrNull(it)?.measuredAt }
        items = sorted
        limits = lowLimit to highLimit
        targetKh = target
        selected = selectedTime?.let { time -> items.indexOfFirst { it.measuredAt == time }.takeIf { it >= 0 } }
        revealLines()
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
        val top = dp(38f); val bottom = height - dp(32f)
        if (right <= left || bottom <= top) return
        val window = if (items.isEmpty()) null else timeWindow()
        val inView = if (window == null) items else items.filterIndexed { index, m ->
            val time = m.measuredAt.toEpochMilli()
            // Include adjoining endpoints so segments crossing a zoom edge remain in bounds.
            time in window.first..window.second ||
                (index < items.lastIndex && time < window.first && items[index + 1].measuredAt.toEpochMilli() >= window.first) ||
                (index > 0 && time > window.second && items[index - 1].measuredAt.toEpochMilli() <= window.second)
        }
        val primary = ChartBounds.calculate(inView, limits.first, limits.second, visible, targetKh)
        val secondary = ChartBounds.secondary(inView, visible)
        fun mapY(value: Double, bounds: Pair<Double, Double>, from: Float, to: Float) =
            to - (to - from) * ((value - bounds.first) / (bounds.second - bounds.first)).toFloat()
        fun y(value: Double) = mapY(value, primary, top, bottom)
        fun seriesY(series: ChartSeries, value: Double) = when (series) {
            ChartSeries.DELTA, ChartSeries.DOSE -> mapY(value, secondary, top, bottom)
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
        targetKh?.takeIf { it.isFinite() }?.let { target ->
            canvas.drawLine(left, y(target), right, y(target), targetPaint)
        }
        axisPaint.color = AwUi.INK
        canvas.drawText("ΔpH", right + dp(6f), dp(13f), axisPaint)
        canvas.drawText("Dose · mL", right + dp(6f), dp(27f), axisPaint)
        canvas.drawLine(right, top, right, bottom, markerPaint)
        val decimals = if (secondary.second - secondary.first < 0.1) 3 else 2
        for (i in 0..5) {
            val value = secondary.first + (secondary.second - secondary.first) * i / 5
            val yy = mapY(value, secondary, top, bottom)
            canvas.drawLine(right, yy, right + dp(3f), yy, markerPaint)
            canvas.drawText(("%." + decimals + "f").format(java.util.Locale.US, value), right + dp(6f), yy + dp(4f), axisPaint)
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
                color = series.color; style = Paint.Style.STROKE; strokeWidth = dp(2.5f)
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                pathEffect = when (styles[series]) {
                    ChartLineStyle.DASHED -> DashPathEffect(floatArrayOf(dp(8f), dp(5f)), 0f)
                    ChartLineStyle.DOTTED -> DashPathEffect(floatArrayOf(dp(1f), dp(5f)), 0f)
                    else -> null
                }
            }
            canvas.save()
            canvas.clipRect(left - dp(4f), top - dp(4f), left + (right - left) * reveal + dp(4f), bottom + dp(4f))
            val path = Path()
            val group = mutableListOf<ChartCurve.Point>()
            fun finishGroup() {
                if (group.isEmpty()) return
                path.moveTo(group.first().x.toFloat(), group.first().y.toFloat())
                if (overview) {
                    ChartCurve.segments(group).forEach { segment ->
                        path.cubicTo(segment.control1.x.toFloat(), segment.control1.y.toFloat(),
                            segment.control2.x.toFloat(), segment.control2.y.toFloat(), segment.end.x.toFloat(), segment.end.y.toFloat())
                    }
                } else group.drop(1).forEach { path.lineTo(it.x.toFloat(), it.y.toFloat()) }
                group.clear()
            }
            items.forEach { m ->
                val value = series.value(m)
                if (value == null || !value.isFinite()) finishGroup()
                else {
                    group += ChartCurve.Point(x(m).toDouble(), seriesY(series, value).toDouble())
                    if (!overview) canvas.drawCircle(x(m), seriesY(series, value), dp(1.5f), paint)
                }
            }
            finishGroup()
            canvas.drawPath(path, paint)
            if (!overview) selected?.let { index -> series.value(items[index])?.takeIf { it.isFinite() }?.let { value ->
                canvas.drawCircle(x(items[index]), seriesY(series, value), dp(4f), paint)
            } }
            canvas.restore()
        }
        selected?.let {
            canvas.save(); canvas.clipRect(left, top, right, bottom)
            canvas.drawLine(x(items[it]), top, x(items[it]), bottom, markerPaint)
            canvas.restore()
        }
        val first = AppDates.format(java.time.Instant.ofEpochMilli(minTime))
        val last = AppDates.format(java.time.Instant.ofEpochMilli(maxTime))
        val fw = axisPaint.measureText(first); val lw = axisPaint.measureText(last)
        canvas.drawText(first, left, bottom + dp(24f), axisPaint)
        if (fw + lw + dp(10f) < right - left) canvas.drawText(last, right - lw, bottom + dp(24f), axisPaint)
    }
    private fun plotRight(): Float {
        val bounds = ChartBounds.secondary(items, visible)
        val labelWidth = listOf("Dose · mL", "%.3f".format(java.util.Locale.US, bounds.first),
            "%.3f".format(java.util.Locale.US, bounds.second)).maxOf { axisPaint.measureText(it) }
        return width - maxOf(dp(64f), labelWidth + dp(12f))
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
