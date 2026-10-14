package app.aquawiznotifier

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DosingCalculationTest {
    @Test fun capturedKh1DosesMatchServerLatestDosAcrossDifferentDemands() {
        val amount = DosingCalculation.amountPerDkh("KH1-A", "16")!!
        listOf(12.0 to 0.192, 125.0 to 2.0, 178.0 to 2.848, 126.0 to 2.016,
            18.0 to 0.288, 54.0 to 0.864, 52.0 to 0.832, 135.0 to 2.16, 187.0 to 2.992).forEach { (raw, actual) ->
            assertEquals(actual, DosingCalculation.millilitres(raw, amount)!!, 0.000001)
        }
        assertEquals(4.08, listOf(68.0, 187.0).sumOf { DosingCalculation.millilitres(it, amount)!! }, 0.000001)
        assertEquals(50.0, DosingCalculation.millilitres(100.0, 500.0)!!, 0.0)
    }
    @Test fun packedKh2SettingUsesAmountPrefixNotCalibrationSuffix() {
        assertEquals(16.0, DosingCalculation.amountPerDkh("KH2-A", "016123")!!, 0.0)
        assertEquals(2.992, DosingCalculation.millilitres(187.0, DosingCalculation.amountPerDkh("KH2-A", "016123"))!!, 0.000001)
    }
    @Test fun unknownInvalidSettingsNeverAssumeDoseAndZeroDemandRemainsZero() {
        assertNull(DosingCalculation.millilitres(187.0, null))
        assertNull(DosingCalculation.millilitres(Double.NaN, 16.0))
        assertNull(DosingCalculation.millilitres(-1.0, 16.0))
        assertNull(DosingCalculation.amountPerDkh("KH1-A", "invalid"))
        assertEquals(0.0, DosingCalculation.millilitres(0.0, null)!!, 0.0)
        assertEquals(0.0, DosingCalculation.millilitres(187.0, 0.0)!!, 0.0)
    }
    @Test fun graphRetainsRawDemandAndUsesExplicitControllerConversion() {
        val raw = """{"results":[["2026-10-12T21:44:02Z",{"field22":7771,"field26":187}]]}"""
        val now = Instant.parse("2026-10-13T00:00:00Z")
        val missing = MeasurementJson.graphMeasurements(raw, "KH1-A", now).single()
        assertNull(missing.doseMl); assertEquals(187.0, missing.doseRawField26!!, 0.0)
        val known = MeasurementJson.graphMeasurements(raw, "KH1-A", now, 16.0).single()
        assertEquals(2.992, known.doseMl!!, 0.000001)
        assertEquals(16.0, known.doseAmountPerDkh!!, 0.0)
        assertEquals(known.measuredAt, missing.measuredAt)
    }
}
