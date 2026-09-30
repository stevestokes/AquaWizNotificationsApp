package app.aquawiznotifier

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PollCadenceTest {
    @Test fun hourlyOffsetsEndTwoMinutesAfterExpectedTest() {
        assertEquals(listOf(17L, 32L, 47L, 62L), PollCadence.probeOffsetsMinutes(60))
    }

    @Test fun reanchorsToMeasurementTimestamp() {
        val t = Instant.parse("2026-09-29T12:00:00Z")
        assertEquals(t.plusSeconds(17 * 60), PollCadence.nextRun(t.plusSeconds(60), t, 60))
        assertEquals(t.plusSeconds(62 * 60), PollCadence.nextRun(t.plusSeconds(48 * 60), t, 60))
    }

    @Test fun afterFourthProbeKeepsFifteenMinuteSpacingFromPlus62() {
        val t = Instant.parse("2026-09-29T12:00:00Z")
        assertEquals(t.plusSeconds(77 * 60), PollCadence.nextRun(t.plusSeconds(63 * 60), t, 60))
        assertEquals(t.plusSeconds(92 * 60), PollCadence.nextRun(t.plusSeconds(80 * 60), t, 60))
    }
}
