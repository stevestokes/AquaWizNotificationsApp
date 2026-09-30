package app.aquawiznotifier

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AquaWizAuthContractTest {
    @Test fun officialSettingsDetermineKhBounds() {
        val summary = DeviceSummaryJson.parse("""{"field8":"8200","field15":"1500","latest_ph":"100","field14":"450","field16":"50"}""", "KH-A")!!
        assertEquals(8.2, summary.khTarget!!, 0.00001)
        assertEquals(6.7, summary.khLow!!, 0.00001)
        assertEquals(9.7, summary.khHigh!!, 0.00001)
        val bounds = ChartBounds.calculate(emptyList(), summary.khLow, summary.khHigh)
        assertEquals(6.5, bounds.first, 0.00001)
        assertEquals(9.9, bounds.second, 0.00001)
    }
    @Test fun zeroDeviationStillReceivesPointTwoPadding() {
        assertEquals(7.8 to 8.2, ChartBounds.calculate(emptyList(), 8.0, 8.0))
    }
    @Test fun invalidSettingsFallBackToObservedKh() {
        val readings = listOf(Measurement(9.8, Instant.EPOCH), Measurement(10.1, Instant.EPOCH.plusSeconds(3600)))
        val bounds = ChartBounds.calculate(readings, 12.0, 8.0)
        assertEquals(9.6, bounds.first, 0.00001)
        assertEquals(10.3, bounds.second, 0.00001)
    }
    @Test fun refusesAnotherControllerOrAmbiguousSettings() {
        assertNull(DeviceSummaryJson.parse("""{"serial":"KH-B","field8":8200}""", "KH-A"))
        assertNull(DeviceSummaryJson.parse("""[{"field8":8200},{"field8":9000}]""", "KH-A"))
        val summary = DeviceSummaryJson.parse("""{"devices":[{"serial":"KH-A","field8":8200,"field15":500},{"serial":"KH-B","field8":9000,"field15":1000}]}""", "KH-A")!!
        assertEquals(8.2, summary.khTarget!!, 0.00001)
    }
    @Test fun missingThresholdDoesNotInventLimits() {
        val summary = DeviceSummaryJson.parse("""{"field8":8200}""", "KH-A")!!
        assertNull(summary.khLow)
        assertNull(summary.khHigh)
    }
    @Test fun displayDatesUseRequestedPatternIncludingMidnight() {
        val instant = Instant.parse("2026-09-30T00:05:00Z")
        assertEquals("09/30/26 @ 12:05 AM", AppDates.formatter.withZone(ZoneId.of("UTC")).format(instant))
    }

    @Test fun outOfTargetDataExpandsOnlyTheNeededEdge() {
        val readings = listOf(Measurement(6.4, Instant.EPOCH), Measurement(8.1, Instant.EPOCH.plusSeconds(3600)))
        val bounds = ChartBounds.calculate(readings, 7.0, 8.5)
        assertEquals(6.2, bounds.first, 0.00001)
        assertEquals(8.7, bounds.second, 0.00001)
        val above = ChartBounds.calculate(listOf(Measurement(9.0, Instant.EPOCH)), 7.0, 8.5)
        assertEquals(6.8, above.first, 0.00001)
        assertEquals(9.2, above.second, 0.00001)
    }
    @Test fun enabledLinesAreIncludedAndHiddenLinesDoNotAddEmptySpace() {
        val readings = listOf(Measurement(8.0, Instant.EPOCH, ph = 6.0, doseMl = 15.0, deltaPh = -0.2))
        val khOnly = ChartBounds.calculate(readings, 7.5, 8.5, setOf(ChartSeries.KH))
        assertEquals(7.3, khOnly.first, 0.00001)
        assertEquals(8.7, khOnly.second, 0.00001)
        val enabled = ChartBounds.calculate(readings, 7.5, 8.5, ChartSeries.values().toSet())
        assertEquals(-0.4, enabled.first, 0.00001)
        assertEquals(15.2, enabled.second, 0.00001)
    }
    @Test fun ignoresNonFiniteDataWhenScaling() {
        val readings = listOf(Measurement(Double.NaN, Instant.EPOCH), Measurement(8.0, Instant.EPOCH, ph = Double.POSITIVE_INFINITY))
        assertEquals(7.8 to 8.2, ChartBounds.calculate(readings, null, null, setOf(ChartSeries.KH, ChartSeries.PH)))
    }
    @Test fun dateFormattingHandlesNoonAndAfternoon() {
        val fmt = AppDates.formatter.withZone(ZoneId.of("UTC"))
        assertEquals("09/30/26 @ 12:05 PM", fmt.format(Instant.parse("2026-09-30T12:05:00Z")))
        assertEquals("09/30/26 @ 03:27 PM", fmt.format(Instant.parse("2026-09-30T15:27:00Z")))
    }
    @Test fun savedTwentyFourHourLogDatesReceiveAmPm() {
        assertEquals("[09/30/26 @ 03:27 PM] Connected", AppDates.normalizeActivityLog("[09/30/26 @ 15:27] Connected"))
        assertEquals("[09/30/26 @ 03:27 PM] Connected", AppDates.normalizeActivityLog("[09/30/26 @ 03:27 PM] Connected"))
    }
}
