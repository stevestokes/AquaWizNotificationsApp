package app.aquawiznotifier

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class HistoryMergeTest {
    private val at = Instant.parse("2026-09-30T21:34:03Z")
    private val current = Measurement(7.41, at.plusSeconds(20).plusMillis(125), ph = 7.62)
    private val graph = Measurement(7.414, at, rawId = "graph:" + at.toEpochMilli(),
        ph = 7.625, phOpenAir = 7.61, deltaPh = 0.015, doseMl = 0.0)

    @Test fun currentAndServerRowsWithDifferentSecondsBecomeOneCompleteReading() {
        val rows = HistoryMerge.merge(listOf(" KH-A " to current, "kh-a" to graph))
        assertEquals(1, rows.size)
        assertEquals("KH-A", rows.single().first)
        assertEquals(graph, rows.single().second)
    }
    @Test fun serverValuesWinRegardlessOfArrivalOrderAndMergingIsIdempotent() {
        val expected = HistoryMerge.merge(listOf("KH-A" to graph, "KH-A" to current))
        assertEquals(expected, HistoryMerge.merge(listOf("KH-A" to current, "KH-A" to graph)))
        assertEquals(expected, HistoryMerge.merge(expected + expected))
    }
    @Test fun otherMinutesAndControllersKeepSeparateRows() {
        val rows = HistoryMerge.merge(listOf("KH-A" to current, "KH-A" to graph,
            "KH-A" to graph.copy(measuredAt = at.plusSeconds(60)), "KH-B" to graph))
        assertEquals(3, rows.size)
        assertEquals(at.plusSeconds(60), rows.first().second.measuredAt)
    }
    @Test fun complementaryValuesFillMissingFieldsAndCanDeriveDelta() {
        val rows = HistoryMerge.merge(listOf("KH-A" to current,
            "KH-A" to Measurement(7.41, at, phOpenAir = 7.61, doseMl = 0.0)))
        val m = rows.single().second
        assertEquals(7.62, m.ph!!, 0.00001)
        assertEquals(7.61, m.phOpenAir!!, 0.00001)
        assertEquals(0.01, m.deltaPh!!, 0.00001)
        assertEquals(0.0, m.doseMl!!, 0.00001)
    }
    @Test fun invalidOptionalValuesCannotDisplaceFiniteServerData() {
        val bad = current.copy(ph = Double.NaN, doseMl = Double.POSITIVE_INFINITY)
        assertEquals(graph, HistoryMerge.merge(listOf("KH-A" to bad, "KH-A" to graph)).single().second)
    }
}
