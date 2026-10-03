package app.aquawiznotifier

import java.time.temporal.ChronoUnit
import java.util.Locale

object HistoryMerge {
    fun readingTime(measurement: Measurement) = measurement.measuredAt.truncatedTo(ChronoUnit.MINUTES)
    fun sameReading(first: Measurement, second: Measurement) = readingTime(first) == readingTime(second)
    fun readingKey(serial: String, measurement: Measurement) =
        serial.trim().uppercase(Locale.ROOT) + "|" + readingTime(measurement).toEpochMilli()

    fun merge(items: List<Pair<String, Measurement>>): List<Pair<String, Measurement>> {
        val merged = linkedMapOf<String, Pair<String, Measurement>>()
        items.forEach { (serial, measurement) ->
            // all_field and graph report the same test with different seconds/milliseconds.
            // Controller + displayed minute is the shared identity across both sources.
            val normalizedSerial = serial.trim().uppercase(Locale.ROOT)
            val key = readingKey(normalizedSerial, measurement)
            val previous = merged[key]?.second
            merged[key] = normalizedSerial to if (previous == null) measurement else combine(previous, measurement)
        }
        return merged.values.sortedByDescending { it.second.measuredAt }.take(2000)
    }

    private fun combine(first: Measurement, second: Measurement): Measurement {
        fun score(m: Measurement) = (if (m.rawId?.startsWith("graph:") == true) 10 else 0) +
            listOf(m.ph, m.phOpenAir, m.deltaPh, m.doseMl).count { it?.isFinite() == true }
        val preferred = if (score(second) > score(first)) second else first
        val fallback = if (preferred === first) second else first
        fun value(a: Double?, b: Double?) = a?.takeIf { it.isFinite() } ?: b?.takeIf { it.isFinite() }
        val ph = value(preferred.ph, fallback.ph)
        val openAir = value(preferred.phOpenAir, fallback.phOpenAir)
        return preferred.copy(
            ph = ph, phOpenAir = openAir,
            deltaPh = value(preferred.deltaPh, fallback.deltaPh) ?: if (ph != null && openAir != null) ph - openAir else null,
            doseMl = value(preferred.doseMl, fallback.doseMl),
        )
    }
}
