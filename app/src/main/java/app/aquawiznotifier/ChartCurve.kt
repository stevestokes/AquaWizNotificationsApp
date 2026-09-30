package app.aquawiznotifier

import kotlin.math.abs
import kotlin.math.sign

/** Smooth cubic segments whose control points stay inside each pair's value bounds. */
object ChartCurve {
    data class Point(val x: Double, val y: Double)
    data class Segment(val start: Point, val control1: Point, val control2: Point, val end: Point)

    fun segments(points: List<Point>): List<Segment> {
        if (points.size < 2) return emptyList()
        val slopes = points.zipWithNext { a, b -> (b.y - a.y) / (b.x - a.x) }
        val tangents = DoubleArray(points.size)
        tangents[0] = slopes.first()
        tangents[points.lastIndex] = slopes.last()
        for (i in 1 until points.lastIndex) {
            val before = slopes[i - 1]; val after = slopes[i]
            tangents[i] = if (before * after <= 0.0) 0.0 else sign(before) * minOf(abs(before), abs(after))
        }
        return points.zipWithNext().mapIndexed { i, (a, b) ->
            val third = (b.x - a.x) / 3.0
            Segment(a, Point(a.x + third, a.y + tangents[i] * third),
                Point(b.x - third, b.y - tangents[i + 1] * third), b)
        }
    }
}
