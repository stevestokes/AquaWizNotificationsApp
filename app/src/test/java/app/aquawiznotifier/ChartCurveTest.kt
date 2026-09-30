package app.aquawiznotifier

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class ChartCurveTest {
    @Test fun smoothCurvePreservesReadingsAndDoesNotInventPeaksBetweenThem() {
        val points = listOf(ChartCurve.Point(0.0, 7.2), ChartCurve.Point(1.0, 8.0),
            ChartCurve.Point(25.0, 7.3), ChartCurve.Point(26.0, 7.3), ChartCurve.Point(100.0, 9.0))
        val segments = ChartCurve.segments(points)
        assertEquals(points.size - 1, segments.size)
        segments.forEachIndexed { i, s ->
            assertEquals(points[i], s.start); assertEquals(points[i + 1], s.end)
            for (step in 0..100) {
                val t = step / 100.0
                val y = (1 - t).pow(3) * s.start.y + 3 * (1 - t).pow(2) * t * s.control1.y +
                    3 * (1 - t) * t.pow(2) * s.control2.y + t.pow(3) * s.end.y
                assertTrue(y >= minOf(s.start.y, s.end.y) - 1e-9)
                assertTrue(y <= maxOf(s.start.y, s.end.y) + 1e-9)
            }
        }
    }
    @Test fun adjacentSegmentsHaveMatchingSlopesAtTheJoin() {
        val segments = ChartCurve.segments(listOf(ChartCurve.Point(0.0, 7.0),
            ChartCurve.Point(2.0, 8.0), ChartCurve.Point(10.0, 9.0)))
        val a = segments[0]; val b = segments[1]
        val incoming = (a.end.y - a.control2.y) / (a.end.x - a.control2.x)
        val outgoing = (b.control1.y - b.start.y) / (b.control1.x - b.start.x)
        assertEquals(incoming, outgoing, 1e-9)
    }
    @Test fun zeroDoseRemainsAFlatLine() {
        val segments = ChartCurve.segments(listOf(ChartCurve.Point(0.0, 0.0),
            ChartCurve.Point(1.0, 0.0), ChartCurve.Point(20.0, 0.0)))
        segments.forEach { assertEquals(0.0, it.control1.y, 0.0); assertEquals(0.0, it.control2.y, 0.0) }
        assertTrue(ChartCurve.segments(emptyList()).isEmpty())
        assertTrue(ChartCurve.segments(listOf(ChartCurve.Point(0.0, 7.0))).isEmpty())
    }
}
