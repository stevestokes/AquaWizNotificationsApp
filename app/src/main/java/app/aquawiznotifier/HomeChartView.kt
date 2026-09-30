package app.aquawiznotifier

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class HomeChartView(context: Context) : View(context) {
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 120, 120)
        textSize = sp(11f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(224, 224, 224)
        strokeWidth = dp(1f)
    }
    private val khPaint = linePaint(Color.rgb(91, 52, 255))
    private val phPaint = linePaint(Color.rgb(59, 191, 91))
    private val phOpenAirPaint = linePaint(Color.rgb(29, 153, 69))
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(80, 80, 80)
        strokeWidth = dp(1f)
    }
    private val tooltipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 30, 30)
        textSize = sp(12f)
    }

    private var measurements: List<Measurement> = emptyList()
    private var selectedIndex: Int? = null
    private var khLowLimit: Double? = null
    private var khHighLimit: Double? = null

    private val timeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")
        .withZone(ZoneId.systemDefault())

    fun setMeasurements(
        items: List<Measurement>,
        lowLimit: Double? = null,
        highLimit: Double? = null,
    ) {
        measurements = items.sortedBy { it.measuredAt }
        khLowLimit = lowLimit
        khHighLimit = highLimit
        selectedIndex = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)

        val left = dp(48f)
        val right = width - dp(12f)
        val top = dp(18f)
        val bottom = height - dp(34f)
        if (right <= left || bottom <= top) return

        val khValues = measurements.map { it.kh }
        val baseLow = khLowLimit ?: khValues.minOrNull() ?: 7.0
        val baseHigh = khHighLimit ?: khValues.maxOrNull() ?: 9.0

        var yMin = baseLow - 0.5
        var yMax = baseHigh + 0.5
        if (yMax - yMin < 1.0) {
            val mid = (yMin + yMax) / 2.0
            yMin = mid - 0.5
            yMax = mid + 0.5
        }

        val tickCount = 5
        for (i in 0..tickCount) {
            val fraction = i.toFloat() / tickCount.toFloat()
            val y = bottom - (bottom - top) * fraction
            canvas.drawLine(left, y, right, y, gridPaint)
            val value = yMin + (yMax - yMin) * fraction
            canvas.drawText(formatAxis(value), dp(4f), y + dp(4f), axisPaint)
        }

        if (measurements.isEmpty()) {
            canvas.drawText("No measurements in this range yet", left + dp(12f), (top + bottom) / 2f, axisPaint)
            return
        }

        val minTime = measurements.first().measuredAt.toEpochMilli()
        val maxTime = measurements.last().measuredAt.toEpochMilli()
        val timeSpan = max(1L, maxTime - minTime)

        fun xFor(m: Measurement): Float {
            val f = (m.measuredAt.toEpochMilli() - minTime).toDouble() / timeSpan.toDouble()
            return left + (right - left) * f.toFloat()
        }

        fun yFor(value: Double): Float {
            val fraction = ((value - yMin) / (yMax - yMin)).toFloat()
            return bottom - (bottom - top) * fraction
        }

        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        drawSeries(canvas, measurements.map { it to it.kh }, khPaint, ::xFor, ::yFor)
        drawSeries(canvas, measurements.mapNotNull { m -> m.ph?.let { m to it } }, phPaint, ::xFor, ::yFor)
        drawSeries(canvas, measurements.mapNotNull { m -> m.phOpenAir?.let { m to it } }, phOpenAirPaint, ::xFor, ::yFor)

        selectedIndex?.takeIf { it in measurements.indices }?.let { index ->
            val selected = measurements[index]
            val x = xFor(selected)
            canvas.drawLine(x, top, x, bottom, markerPaint)
            canvas.drawCircle(x, yFor(selected.kh), dp(4f), khPaint)
        }
        canvas.restore()

        drawXAxisLabels(canvas, minTime, maxTime, left, right, bottom)

        selectedIndex?.takeIf { it in measurements.indices }?.let { index ->
            val selected = measurements[index]
            val details = buildString {
                append(timeFormatter.format(selected.measuredAt))
                append("  KH ")
                append("%.3f".format(selected.kh))
                selected.ph?.let { append("  pH " + "%.3f".format(it)) }
                selected.phOpenAir?.let { append("  pH(O) " + "%.3f".format(it)) }
            }
            canvas.drawText(details, left, dp(14f), tooltipPaint)
        }
    }

    private fun drawSeries(
        canvas: Canvas,
        points: List<Pair<Measurement, Double>>,
        paint: Paint,
        xFor: (Measurement) -> Float,
        yFor: (Double) -> Float,
    ) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            canvas.drawCircle(xFor(points[0].first), yFor(points[0].second), dp(3f), paint)
            return
        }

        val path = Path()
        points.forEachIndexed { index, pair ->
            val x = xFor(pair.first)
            val y = yFor(pair.second)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawXAxisLabels(
        canvas: Canvas,
        minTime: Long,
        maxTime: Long,
        left: Float,
        right: Float,
        bottom: Float,
    ) {
        val span = max(1L, maxTime - minTime)
        for (i in 0..3) {
            val fraction = i / 3.0
            val instant = Instant.ofEpochMilli(minTime + (span * fraction).toLong())
            val label = if (span <= 3L * 24 * 60 * 60 * 1000) {
                DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(instant)
            } else {
                DateTimeFormatter.ofPattern("M/d").withZone(ZoneId.systemDefault()).format(instant)
            }
            val x = left + (right - left) * fraction.toFloat()
            val textWidth = axisPaint.measureText(label)
            canvas.drawText(label, min(right - textWidth, max(left, x - textWidth / 2f)), bottom + dp(22f), axisPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (measurements.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val left = dp(48f)
                val right = width - dp(12f)
                if (right <= left) return false
                val minTime = measurements.first().measuredAt.toEpochMilli()
                val maxTime = measurements.last().measuredAt.toEpochMilli()
                val span = max(1L, maxTime - minTime)
                val clampedX = event.x.coerceIn(left, right)
                val target = minTime + (((clampedX - left) / (right - left)) * span).toLong()
                selectedIndex = measurements.indices.minByOrNull {
                    abs(measurements[it].measuredAt.toEpochMilli() - target)
                }
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun linePaint(colorValue: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorValue
        strokeWidth = dp(2.5f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun formatAxis(value: Double): String {
        val rounded = kotlin.math.round(value * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) "%.0f".format(rounded) else "%.1f".format(rounded)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
