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
        assertEquals(6.2, bounds.first, 0.00001)
        assertEquals(10.2, bounds.second, 0.00001)
    }
    @Test fun zeroDeviationStillReceivesHalfDkhPadding() {
        assertEquals(7.5 to 8.5, ChartBounds.calculate(emptyList(), 8.0, 8.0))
    }
    @Test fun invalidSettingsFallBackToObservedKh() {
        val readings = listOf(Measurement(9.8, Instant.EPOCH), Measurement(10.1, Instant.EPOCH.plusSeconds(3600)))
        assertEquals(9.3 to 10.6, ChartBounds.calculate(readings, 12.0, 8.0))
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
        assertEquals("09/30/26 @ 00:05", AppDates.formatter.withZone(ZoneId.of("UTC")).format(instant))
    }
}
