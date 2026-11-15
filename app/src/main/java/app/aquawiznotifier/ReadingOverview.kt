package app.aquawiznotifier

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Local raw-reading statistics: rolling 24-hour KH, calendar-day dosing. */
internal data class ReadingOverview(
    val minimumKh: Double?,
    val maximumKh: Double?,
    val readingsInTarget: Int?,
    val readingCount: Int,
    val todayDoseMl: Double?,
) {
    val inTargetPercent: Int? get() = readingsInTarget?.let { (100.0 * it / readingCount).roundToInt() }
    val doseText: String get() = todayDoseMl?.toLong()?.toString() ?: "—"
    val rangeText: String get() = minimumKh?.let { "%.2f–%.2f dKH".format(it, maximumKh) } ?: "—"
    val targetText: String get() = inTargetPercent?.let { "$it%" } ?: "—"
    val text: String get() = "Last 24h: $rangeText · In target $targetText"
    val description: String get() = "Last 24 hours: $rangeText from $readingCount saved readings. " +
        (readingsInTarget?.let { "$it of $readingCount readings within the current KH target bounds ($targetText). " }
            ?: "Target percentage unavailable. ")

    companion object {
        fun calculate(readings: List<Measurement>, summary: DeviceSummary?, now: Instant, zone: ZoneId): ReadingOverview {
            val past = readings.filter { !it.measuredAt.isAfter(now) }
            val since = now.minusSeconds(86400)
            val kh = past.filter { !it.measuredAt.isBefore(since) }.map { it.kh }.filter { it.isFinite() && it > 0 }
            val low = summary?.khLow
            val high = summary?.khHigh
            val targetKnown = low != null && high != null && low.isFinite() && high.isFinite() && low <= high
            val inTarget = if (kh.isNotEmpty() && targetKnown) kh.count { it >= low!! - 1e-9 && it <= high!! + 1e-9 } else null
            val today = now.atZone(zone).toLocalDate()
            val todayReadings = past.filter { it.measuredAt.atZone(zone).toLocalDate() == today }
            val doses = todayReadings.mapNotNull { it.doseMl?.takeIf { dose -> dose.isFinite() && dose >= 0 } }
            val total = if (doses.isNotEmpty() && doses.size == todayReadings.size) doses.sum().takeIf { it.isFinite() } else null
            return ReadingOverview(kh.minOrNull(), kh.maxOrNull(), inTarget, kh.size, total)
        }
    }
}
