package app.aquawiznotifier

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class MeasurementNotificationTest {
    private val reading = Measurement(7.94, Instant.parse("2026-09-30T15:35:00Z"), rawId = "reading-1",
        ph = 7.51, phOpenAir = 7.63, deltaPh = -0.12, doseMl = 0.02)
    @Test fun matchesAlkatronicInOneTextBlockWithoutADate() {
        val text = MeasurementNotification.text("KH1-00-05117", reading, false, false, false)
        assertEquals("[KH1-00-05117] New measurement result: 7.94 dKH, pH 7.51.", text)
        assertFalse(text.contains('\n'))
        assertFalse(text.contains("09/30"))
        assertFalse(text.contains("Measured"))
    }
    @Test fun optionalFieldsStayInlineAndRespectToggles() {
        val text = MeasurementNotification.text("KH-A", reading, true, false, true)
        assertTrue(text.endsWith(" • pH(O) 7.63 • Dose 0.02 mL"))
        assertFalse(text.contains("ΔpH"))
        assertFalse(text.contains('\n'))
        assertTrue(MeasurementNotification.text("KH-A", reading, false, true, false).endsWith(" • ΔpH -0.12"))
    }
    @Test fun unavailableValuesAreOmitted() {
        val m = Measurement(8.0, Instant.EPOCH)
        assertEquals("[KH-A] New measurement result: 8.00 dKH.", MeasurementNotification.text("KH-A", m, true, true, true))
    }
    @Test fun separateReadingsAndControllersHaveSeparateNotificationTags() {
        val first = MeasurementNotification.tag("KH-A", reading)
        assertNotEquals(first, MeasurementNotification.tag("KH-A", reading.copy(measuredAt = reading.measuredAt.plusSeconds(3600))))
        assertNotEquals(first, MeasurementNotification.tag("KH-B", reading))
        assertEquals(first, MeasurementNotification.tag(" kh-a ", reading.copy(rawId = null, phOpenAir = null)))
    }
    @Test fun repeatedTestNotificationsDoNotReplaceEachOther() {
        val first = reading.copy(rawId = "test")
        assertNotEquals(MeasurementNotification.tag("KH-A", first),
            MeasurementNotification.tag("KH-A", first.copy(measuredAt = first.measuredAt.plusMillis(1))))
    }
}
