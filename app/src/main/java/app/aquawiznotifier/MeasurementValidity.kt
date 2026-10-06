package app.aquawiznotifier

import java.time.Instant

object MeasurementValidity {
    // Small clock skew is tolerable; a measurement many hours in the future is not.
    fun isNotFuture(measurement: Measurement, now: Instant = Instant.now()): Boolean =
        !measurement.measuredAt.isAfter(now.plusSeconds(5 * 60))
}
