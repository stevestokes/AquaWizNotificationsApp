package app.aquawiznotifier

import java.util.Locale

object MeasurementNotification {
    fun tag(serial: String, measurement: Measurement): String =
        "measurement:" + serial.trim().uppercase(Locale.ROOT) + ":" +
            (if (measurement.rawId == "test") measurement.measuredAt.toEpochMilli() else HistoryMerge.readingTime(measurement).toEpochMilli())

    fun text(serial: String, m: Measurement, showPhOpenAir: Boolean, showDeltaPh: Boolean, showDoseMl: Boolean): String {
        fun value(number: Double) = String.format(Locale.US, "%.2f", number)
        val primary = "[$serial] New measurement result: " + value(m.kh) + " dKH" +
            (m.ph?.let { ", pH " + value(it) } ?: "") + "."
        val details = mutableListOf<String>()
        if (showPhOpenAir && m.phOpenAir != null) details += "pH(O) " + value(m.phOpenAir)
        if (showDeltaPh && m.deltaPh != null) details += "ΔpH " + String.format(Locale.US, "%+.2f", m.deltaPh)
        if (showDoseMl && m.doseMl != null) details += "Dose " + value(m.doseMl) + " mL"
        return (listOf(primary) + details).joinToString(" • ")
    }
}
