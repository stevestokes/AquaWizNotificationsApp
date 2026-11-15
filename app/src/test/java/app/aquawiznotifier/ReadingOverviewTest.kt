package app.aquawiznotifier

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReadingOverviewTest {
    private val now = Instant.parse("2026-11-15T15:00:00Z")
    private val zone = ZoneId.of("America/New_York")
    private val target = DeviceSummary(khTarget = 8.5, khDeviation = .5)
    private fun reading(kh: Double, at: Instant = now, dose: Double? = 1.0) = Measurement(kh, at, doseMl = dose)

    @Test fun rollingWindowIncludesBoundsButExcludesOldFutureAndInvalidKh() {
        val result = ReadingOverview.calculate(listOf(
            reading(8.0, now.minusSeconds(86400)), reading(9.0), reading(9.2),
            reading(12.0, now.minusSeconds(86401)), reading(13.0, now.plusSeconds(1)),
            reading(Double.NaN), reading(0.0)
        ), target, now, zone)
        assertEquals(8.0, result.minimumKh!!, 0.0)
        assertEquals(9.2, result.maximumKh!!, 0.0)
        assertEquals(3, result.readingCount)
        assertEquals(2, result.readingsInTarget)
        assertEquals(67, result.inTargetPercent)
    }
    @Test fun dosingUsesLocalMidnightAndTruncatesOnlyAfterAdding() {
        val result = ReadingOverview.calculate(listOf(
            reading(8.4, Instant.parse("2026-11-15T04:59:59Z"), 20.0),
            reading(8.4, Instant.parse("2026-11-15T05:00:00Z"), 30.75),
            reading(8.7, now, 30.4)
        ), target, now, zone)
        assertEquals(61.15, result.todayDoseMl!!, .000001)
        assertEquals("61", result.doseText)
        assertEquals("Last 24h: 8.40–8.70 dKH · In target 100%", result.text)
    }
    @Test fun missingDosesNeverShowPartialTotalsAndMissingTargetIsUnknown() {
        for (missing in listOf(null, Double.NaN, -1.0)) {
            val result = ReadingOverview.calculate(listOf(reading(8.4), reading(8.6, dose = missing)), null, now, zone)
            assertNull(result.todayDoseMl)
            assertNull(result.inTargetPercent)
            assertEquals("—", result.targetText)
        }
        val empty = ReadingOverview.calculate(emptyList(), target, now, zone)
        assertNull(empty.minimumKh); assertNull(empty.inTargetPercent); assertNull(empty.todayDoseMl)
        val zero = ReadingOverview.calculate(listOf(reading(8.5, dose = 0.0)), target, now, zone)
        assertEquals("0", zero.doseText)
    }
    @Test fun percentageCountsReadingsNotTimeAndUsesCurrentTarget() {
        val readings = listOf(reading(8.1, now.minusSeconds(86000)), reading(8.4, now.minusSeconds(60)), reading(8.8))
        assertEquals(100, ReadingOverview.calculate(readings, target, now, zone).inTargetPercent)
        assertEquals(33, ReadingOverview.calculate(readings, DeviceSummary(khTarget = 8.5, khDeviation = .2), now, zone).inTargetPercent)
    }
}
