package app.aquawiznotifier

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

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
                val marker = if (includesEightAm(record)) "eight" else "recent"
                val name = "${System.currentTimeMillis()}-${UUID.randomUUID()}-$marker.json"
                val temporary = File(directory, "$name.tmp")
                temporary.writeText(text, Charsets.UTF_8)
                val target = File(directory, name)
                if (!temporary.renameTo(target)) { temporary.delete(); return@runCatching }
                listOf("recent" to recentLimit, "eight" to eightAmLimit).forEach { (kind, limit) ->
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
                if (serial != null && record.optString("serial").equals(serial, true)) records.put(record)
            }
        }
        JSONObject().put("appVersion", version).put("exportedAt", Instant.now().toString())
            .put("captures", records).toString(2)
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
