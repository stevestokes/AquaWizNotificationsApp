package app.aquawiznotifier

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal fun summaryIdentity(serial: String, m: Measurement): String =
    listOf(HistoryMerge.readingKey(serial, m), m.kh, m.ph, m.phOpenAir, m.doseMl).joinToString("|")

/** Private, bounded evidence from actual ingestion; no extra server requests. */
class IngestionCaptureStore(
    private val directory: File,
    private val recentLimit: Int = 64,
    private val eightAmLimit: Int = 8,
    private val byteLimit: Long = 8L * 1024 * 1024,
) {
    companion object { private val lock = Any() }

    fun record(record: JSONObject) {
        synchronized(lock) {
            runCatching {
                directory.mkdirs()
                val text = record.toString()
                // Avoid an unbounded diagnostic write from an unexpectedly large response.
                if (text.toByteArray(Charsets.UTF_8).size > byteLimit / 2) return@runCatching
                val marker = if (record.optString("type") in listOf("http-failure", "session-event", "auth-endpoint-comparison") || record.optInt("status") in listOf(401, 403)) "failure" else if (includesEightAm(record)) "eight" else "recent"
                val name = "${System.currentTimeMillis()}-${UUID.randomUUID()}-$marker.json"
                val temporary = File(directory, "$name.tmp")
                temporary.writeText(text, Charsets.UTF_8)
                val target = File(directory, name)
                if (!temporary.renameTo(target)) { temporary.delete(); return@runCatching }
                listOf("recent" to recentLimit, "eight" to eightAmLimit, "failure" to 32).forEach { (kind, limit) ->
                    files().filter { it.name.endsWith("-$kind.json") }.dropLast(limit).forEach { it.delete() }
                }
                var all = files()
                while (all.sumOf { it.length() } > byteLimit && all.isNotEmpty()) {
                    // Preserve the latest 8 AM evidence in preference to routine polling captures.
                    if (!(all.firstOrNull { it.name.endsWith("-recent.json") } ?: all.first()).delete()) break
                    all = files()
                }
            }
        }
    }

    fun export(serial: String?, version: String): String = synchronized(lock) {
        val records = JSONArray()
        files().forEach { file ->
            runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()?.let { record ->
                if (serial == null || record.optString("serial").isBlank() || record.optString("serial") == "null" || record.optString("serial").equals(serial, true)) records.put(DiagnosticRedaction.body(record.toString(), null))
            }
        }
        JSONObject().put("appVersion", version).put("exportedAt", Instant.now().toString())
            .put("captures", records).toString(2)
    }

    /** Only remove an exact captured aggregate, and protect any timestamp seen in raw captures. */
    fun confirmedDailySummaryIdentities(): Set<String> = synchronized(lock) {
        val summaries = mutableSetOf<String>()
        val rawTimes = mutableSetOf<String>()
        val summaryRows = mutableListOf<Pair<String, Measurement>>()
        files().forEach { file ->
            runCatching {
                val record = JSONObject(file.readText(Charsets.UTF_8))
                val graph = record.optJSONObject("graph") ?: return@runCatching
                val serial = record.getString("serial")
                val since = Instant.parse(record.getString("graphSince"))
                val at = Instant.parse(record.getString("capturedAt"))
                // Identity must use the old /2 representation to remove legacy daily summaries.
                val rows = MeasurementJson.graphMeasurements(graph.toString(), serial, at, 500.0)
                val daily = since.isBefore(at.minusSeconds(31L * 86400)) && rows.size >= 2 &&
                    rows.all { it.measuredAt.atZone(java.time.ZoneOffset.UTC).toLocalTime() == java.time.LocalTime.NOON } &&
                    rows.map { it.measuredAt.atZone(java.time.ZoneOffset.UTC).toLocalDate() }.distinct().size == rows.size
                if (daily) summaryRows.addAll(rows.map { serial to it })
                else if (!since.isBefore(at.minusSeconds(31L * 86400))) {
                    rows.forEach { rawTimes.add(HistoryMerge.readingKey(serial, it)) }
                }
            }
        }
        summaryRows.filter { (serial, m) -> HistoryMerge.readingKey(serial, m) !in rawTimes }
            .forEach { (serial, m) -> summaries.add(summaryIdentity(serial, m)) }
        summaries
    }

    /** Earliest retained raw capture supplies its contemporaneous controller dosing setting. */
    fun dosingEvidence(): Map<String, Measurement> = synchronized(lock) {
        val records = files().mapNotNull { runCatching { JSONObject(it.readText(Charsets.UTF_8)) }.getOrNull() }
        data class Setting(val serial: String, val at: Instant, val amount: Double)
        val settings = records.mapNotNull { record ->
            val serial = record.optString("serial").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val body = record.optJSONObject("status") ?: if (record.optString("url").endsWith("/all_field") && record.optInt("status") == 200)
                record.optJSONObject("responseBody") else null
            val summary = body?.let { DeviceSummaryJson.parse(it.toString(), serial) } ?: return@mapNotNull null
            val amount = DosingCalculation.amountPerDkh(serial, summary.dosingField5) ?: return@mapNotNull null
            val at = runCatching { Instant.parse(record.getString("capturedAt")) }.getOrNull() ?: return@mapNotNull null
            Setting(serial, at, amount)
        }.sortedBy { it.at }
        val evidence = linkedMapOf<String, Measurement>()
        records.sortedBy { it.optString("capturedAt") }.forEach { record ->
            val serial = record.optString("serial")
            val at = runCatching { Instant.parse(record.getString("capturedAt")) }.getOrNull() ?: return@forEach
            val since = runCatching { Instant.parse(record.getString("graphSince")) }.getOrNull()
            // Long-window daily aggregates must not be used to repair individual readings.
            if (since != null && since.isBefore(at.minusSeconds(86460))) return@forEach
            val graph = record.optJSONObject("graph") ?: if (record.optString("url").contains("/graph?") && record.optInt("status") == 200)
                record.optJSONObject("responseBody") else null
            graph ?: return@forEach
            val amount = settings.filter { it.serial.equals(serial, true) && !it.at.isAfter(at) && !it.at.isBefore(at.minusSeconds(86400)) }
                .lastOrNull()?.amount ?: settings.firstOrNull { it.serial.equals(serial, true) && it.at.isAfter(at) && it.at.isBefore(at.plusSeconds(120)) }?.amount
            MeasurementJson.graphMeasurements(graph.toString(), serial, at, amount).forEach { m ->
                val key = HistoryMerge.readingKey(serial, m)
                val old = evidence[key]
                if (old == null || old.doseAmountPerDkh == null && m.doseAmountPerDkh != null) evidence[key] = m
            }
        }
        evidence
    }

    private fun files(): List<File> = directory.listFiles()?.filter { it.name.endsWith(".json") }
        ?.sortedBy { it.name }.orEmpty()

    private fun includesEightAm(record: JSONObject): Boolean {
        val zone = runCatching { ZoneId.of(record.optString("timeZone")) }.getOrDefault(ZoneId.systemDefault())
        fun atEight(value: Any?): Boolean {
            value ?: return false
            val time = runCatching {
                if (value is Number) Instant.ofEpochMilli(value.toLong()) else Instant.parse(value.toString())
            }.getOrNull()?.atZone(zone) ?: return false
            return time.hour == 8 && time.minute == 0
        }
        listOf("acceptedReadings", "incomingReadings").forEach { key ->
            val rows = record.optJSONArray(key) ?: return@forEach
            for (i in 0 until rows.length()) if (atEight(rows.optJSONObject(i)?.opt("measuredAt"))) return true
        }
        if (atEight(record.optJSONObject("status")?.opt("latest_time"))) return true
        // Keep evidence even if a future 8 AM row was rejected by the parser.
        val rawRows = record.optJSONObject("graph")?.optJSONArray("results")
        if (rawRows != null) for (i in 0 until rawRows.length()) {
            if (atEight(rawRows.optJSONArray(i)?.opt(0))) return true
        }
        return false
    }
}
