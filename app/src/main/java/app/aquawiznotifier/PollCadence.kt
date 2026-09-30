package app.aquawiznotifier

import java.time.Duration
import java.time.Instant

/**
 * Anchors checks to the controller's actual measurement timestamp instead of the app's last wakeup.
 * For the default 60-minute measurement cycle and 2-minute post-target, probes are T+17,+32,+47,+62.
 */
object PollCadence {
    const val PROBE_SPACING_MINUTES = 15L
    const val DEFAULT_MEASUREMENT_INTERVAL_MINUTES = 60L
    const val POST_EXPECTED_MINUTES = 2L

    fun probeOffsetsMinutes(
        measurementIntervalMinutes: Long = DEFAULT_MEASUREMENT_INTERVAL_MINUTES,
        postExpectedMinutes: Long = POST_EXPECTED_MINUTES,
    ): List<Long> {
        val end = measurementIntervalMinutes + postExpectedMinutes
        val offsets = mutableListOf<Long>()
        var value = end
        while (value > 0 && offsets.size < 4) {
            offsets += value
            value -= PROBE_SPACING_MINUTES
        }
        return offsets.sorted()
    }

    fun nextRun(
        now: Instant,
        lastMeasurement: Instant?,
        measurementIntervalMinutes: Long = DEFAULT_MEASUREMENT_INTERVAL_MINUTES,
    ): Instant {
        if (lastMeasurement == null) return now.plus(Duration.ofMinutes(PROBE_SPACING_MINUTES))
        val offsets = probeOffsetsMinutes(measurementIntervalMinutes)
        for (offset in offsets) {
            val candidate = lastMeasurement.plus(Duration.ofMinutes(offset))
            if (candidate.isAfter(now.plusSeconds(5))) return candidate
        }
        // We are past the expected window. Continue every 15 minutes from the final planned
        // probe (for hourly testing: T+62, T+77, T+92, ...). This preserves the user's
        // requested cadence instead of snapping back to T-aligned quarter hours.
        val lastPlannedOffset = offsets.last()
        val lastPlanned = lastMeasurement.plus(Duration.ofMinutes(lastPlannedOffset))
        val overdueMinutes = Duration.between(lastPlanned, now).toMinutes().coerceAtLeast(0)
        val steps = (overdueMinutes / PROBE_SPACING_MINUTES) + 1
        return lastPlanned.plus(Duration.ofMinutes(steps * PROBE_SPACING_MINUTES))
    }
}
