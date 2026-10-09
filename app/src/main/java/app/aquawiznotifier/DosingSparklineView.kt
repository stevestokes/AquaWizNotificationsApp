package app.aquawiznotifier

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Per-measurement dose, using the same yellow series as the main chart. */
class DosingSparklineView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ChartSeries.DOSE.color; style = Paint.Style.STROKE
        strokeWidth = 2 * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val line = Path()
    private var readings = emptyList<Measurement>()

    fun setMeasurements(measurements: List<Measurement>) {
        readings = measurements.filter { it.doseMl?.let { n -> n.isFinite() && n >= 0 } == true }.sortedBy { it.measuredAt }
        contentDescription = if (readings.isEmpty()) "Today's dosing chart: no dosing data available"
            else "Today's dosing chart: ${readings.size} measurements, %.2f mL total".format(readings.sumOf { it.doseMl!! })
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (readings.isEmpty()) return
        val padding = 4 * resources.displayMetrics.density
        val w = (width - padding * 2).coerceAtLeast(0f)
        val h = (height - padding * 2).coerceAtLeast(0f)
        val first = readings.first().measuredAt.toEpochMilli()
        val span = (readings.last().measuredAt.toEpochMilli() - first).coerceAtLeast(1)
        val maximum = readings.maxOf { it.doseMl!! }.takeIf { it > 0 } ?: 1.0
        line.reset()
        readings.forEachIndexed { index, m ->
            val x = if (readings.size == 1) width / 2f else padding + w * (m.measuredAt.toEpochMilli() - first).toFloat() / span
            val y = height - padding - h * (m.doseMl!! / maximum).toFloat()
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
            if (readings.size == 1) canvas.drawPoint(x, y, paint)
        }
        canvas.drawPath(line, paint)
    }
}
