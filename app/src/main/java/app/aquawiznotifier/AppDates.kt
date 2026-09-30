package app.aquawiznotifier

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object AppDates {
    val formatter: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("MM/dd/yy '@' HH:mm", Locale.US).withZone(ZoneId.systemDefault())
    fun format(instant: Instant): String = formatter.format(instant)
    fun normalizeActivityLog(log: String): String = log.lineSequence().joinToString("\n") { line ->
        val close = line.indexOf(']')
        if (!line.startsWith("[") || close < 0) line else {
            val old = line.substring(1, close)
            val converted = listOf("MMM d, h:mm:ss a", "MMM d, h:mm a").firstNotNullOfOrNull { pattern ->
                runCatching {
                    val zone = ZoneId.systemDefault()
                    val now = java.time.LocalDateTime.now(zone)
                    var parsed = java.time.LocalDateTime.parse(old + " " + now.year,
                        DateTimeFormatter.ofPattern(pattern + " uuuu", Locale.US))
                    if (parsed.isAfter(now.plusDays(1))) parsed = parsed.minusYears(1)
                    format(parsed.atZone(zone).toInstant())
                }.getOrNull()
            }
            if (converted == null) line else "[" + converted + line.substring(close)
        }
    }
}
