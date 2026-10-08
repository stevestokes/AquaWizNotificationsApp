package app.aquawiznotifier

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class AquaWizApi(
    private val baseUrl: String = GLOBAL_BASE,
    private val capture: ((JSONObject) -> Unit)? = null,
    private val source: String = "unspecified",
) {
    data class TargetReadback(val summary: DeviceSummary?, val matches: Boolean, val error: Throwable? = null)
    companion object {
        const val GLOBAL_BASE = "https://server.aquawiz.net"
        const val CHINA_BASE = "https://server.aquawiz.cn"

    }

    class ApiException(val status: Int, message: String) : Exception(message)

    /** Status is settings-only. Measurements come exclusively from official graph rows. */
    fun latestMeasurement(session: Session, serial: String, onSummary: ((DeviceSummary) -> Unit)? = null): Measurement {
        val normalizedSerial = serial.trim().uppercase()
        var statusRaw: String? = null
        if (onSummary != null) {
            try {
                statusRaw = rawAllFields(session, normalizedSerial)
                DeviceSummaryJson.parse(statusRaw, normalizedSerial)?.let(onSummary)
            } catch (e: ApiException) {
                if (e.status == 401 || e.status == 403) throw e
            } catch (_: Exception) {
                // Settings availability must not promote status values into History.
            }
        }
        val since = Instant.now().minusSeconds(8 * 60 * 60)
        return parseAndCapture(rawGraph(session, normalizedSerial, since), normalizedSerial, since, statusRaw).lastOrNull()
            ?: throw ApiException(200, "Connected, but no KH measurement could be identified in the AquaWiz graph response")
    }

    /**
     * Official app contract recovered from Hermes bytecode:
     * POST /api/v1/KH/{deviceSerial}/all_field
     * Authorization: Bearer <access_token>
     * body: {"user":"<username>","token":{"access_token":"<access_token>"}}
     */
    fun rawAllFields(session: Session, serial: String): String {
        val encodedSerial = URLEncoder.encode(serial.trim().uppercase(), StandardCharsets.UTF_8.toString()).replace("+", "%20")
        val body = JSONObject()
            .put("user", session.username)
            .put("token", JSONObject().put("access_token", session.accessToken))
            .toString()
        return request(
            "POST",
            "$baseUrl/api/v1/KH/$encodedSerial/all_field",
            token = session.accessToken,
            body = body,
        )
    }

    /** Official app graph route recovered from AquaWiz APK. */
    fun rawGraph(session: Session, serial: String): String =
        rawGraph(session, serial, Instant.now().minusSeconds(8 * 60 * 60))

    fun rawGraph(session: Session, serial: String, since: Instant): String {
        val encodedSerial = URLEncoder.encode(serial.trim().uppercase(), StandardCharsets.UTF_8.toString()).replace("+", "%20")
        val encodedDate = URLEncoder.encode(since.toString(), StandardCharsets.UTF_8.toString())
        return request("GET", "$baseUrl/api/v1/query/device/$encodedSerial/graph?date=$encodedDate", token = session.accessToken)
    }

    fun graphMeasurements(session: Session, serial: String, since: Instant): List<Measurement> {
        val normalizedSerial = serial.trim().uppercase()
        val raw = rawGraph(session, normalizedSerial, since)
        return parseAndCapture(raw, normalizedSerial, since)
    }

    private fun parseAndCapture(raw: String, serial: String, since: Instant, statusRaw: String? = null): List<Measurement> {
        val now = Instant.now()
        val rows = MeasurementJson.graphMeasurements(raw, serial, now)
        val sink = capture
        if (sink != null) runCatching {
            val accepted = JSONArray()
            rows.forEach { m -> accepted.put(JSONObject()
                .put("measuredAt", m.measuredAt.toString()).put("rawId", m.rawId)
                .put("kh", m.kh).put("ph", m.ph).put("phOpenAir", m.phOpenAir)
                .put("deltaPh", m.deltaPh).put("doseMl", m.doseMl)) }
            sink.invoke(JSONObject()
                .put("capturedAt", now.toString()).put("source", source).put("serial", serial)
                .put("server", baseUrl).put("graphSince", since.toString())
                .put("timeZone", ZoneId.systemDefault().id)
                .put("graph", redactExportCredentials(JSONTokener(raw).nextValue()))
                .put("status", statusRaw?.let { redactExportCredentials(JSONTokener(it).nextValue()) } ?: JSONObject.NULL)
                .put("acceptedReadings", accepted))
        }
        // Capture failures must never prevent monitoring or alter the parsed readings.
        return rows
    }

    private fun redactExportCredentials(value: Any?): Any? {
        when (value) {
            is JSONObject -> value.keys().asSequence().toList().forEach { key ->
                val name = key.lowercase(java.util.Locale.ROOT)
                if (listOf("token", "passcode", "password", "secret", "credential").any { name.contains(it) } || name.endsWith("_pw")) {
                    value.put(key, "[REDACTED]")
                } else value.put(key, redactExportCredentials(value.opt(key)))
            }
            is JSONArray -> for (i in 0 until value.length()) value.put(i, redactExportCredentials(value.opt(i)))
        }
        return value
    }

    fun setTrueTankKh(session: Session, serial: String, value: java.math.BigDecimal) {
        request("POST", baseUrl + KhCalibration.PATH, token = session.accessToken,
            body = KhCalibration.body(session, serial, value).toString())
    }

    fun setKhTarget(session: Session, serial: String, settings: KhTargetSettings) {
        request("POST", baseUrl + KhCalibration.PATH, token = session.accessToken,
            body = KhDeviceSettings.targetBody(session, serial, settings).toString())
    }

    /** A successful write acknowledges the request; it does not prove the controller applied it. */
    fun setKhTargetAndReadBack(session: Session, serial: String, settings: KhTargetSettings): TargetReadback {
        setKhTarget(session, serial, settings)
        return try {
            val summary = DeviceSummaryJson.parse(rawAllFields(session, serial), serial)
                ?: error("Unable to identify this controller's settings after saving")
            TargetReadback(summary, KhDeviceSettings.matchesTarget(summary, settings))
        } catch (error: Exception) {
            // Never retry a write that was already accepted, even if readback fails.
            TargetReadback(null, false, error)
        }
    }

    fun setKhDosing(session: Session, serial: String, settings: KhDosingSettings, current: DeviceSummary) {
        request("POST", baseUrl + KhCalibration.PATH, token = session.accessToken,
            body = KhDeviceSettings.dosingBody(session, serial, settings, current).toString())
    }

    private fun request(method: String, url: String, token: String? = null, body: String? = null): String {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) doOutput = true
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw ApiException(status, "AquaWiz HTTP $status${if (text.isNotBlank()) ": ${text.take(500)}" else ""}")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun firstString(root: JSONObject, keys: List<String>): String? {
        for (key in keys) root.optString(key).takeIf { it.isNotBlank() && it != "null" }?.let { return it }
        return null
    }

    private fun extractDevices(value: Any?): List<String> {
        val out = linkedSetOf<String>()
        fun walk(v: Any?) {
            when (v) {
                is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i))
                is JSONObject -> {
                    for (key in MeasurementJson.serialKeys) {
                        v.optString(key).takeIf { it.isNotBlank() && it != "null" }?.let(out::add)
                    }
                    val it = v.keys(); while (it.hasNext()) walk(v.opt(it.next()))
                }
                is String -> if (v.startsWith("KH", true) || v.startsWith("CA", true)) out.add(v)
            }
        }
        walk(value)
        return out.toList()
    }
}

object MeasurementJson {
    internal val serialKeys = listOf("deviceSerial", "device_serial", "serial", "serialNumber", "serial_number", "sn")
    private val khKeys = listOf("latest_kh", "latestKh", "tankKh", "tank_kh", "kh", "dkh", "dKH", "alk", "alkalinity")
    private val timeKeys = listOf("latest_time", "latestTime", "measurementTime", "measurement_time", "date", "timestamp", "time", "created_at", "createdAt")
    private val idKeys = listOf("id", "measurement_id", "measurementId", "uuid")
    // Official DevicePage uses latest_ph as probe status and latest_ph1 as the displayed pH value.
    private val phKeys = listOf("latest_ph1", "latestPh1", "phValue", "ph", "pH")

    private data class Candidate(val measurement: Measurement, val serial: String?)

    fun graphMeasurements(raw: String, preferredSerial: String? = null, now: Instant = Instant.now()): List<Measurement> {
        val root = runCatching { JSONTokener(raw).nextValue() }.getOrNull() ?: return emptyList()
        val found = mutableListOf<Candidate>()
        extractOfficialGraphRows(root, preferredSerial, found)
        return found
            .map { it.measurement }
            .filter { MeasurementValidity.isNotFuture(it, now) }
            .distinctBy { it.fingerprint }
            .sortedBy { it.measuredAt }
    }

    fun findLatest(
        raw: String,
        preferredSerial: String? = null,
        requirePreferredSerialWhenAmbiguous: Boolean = false,
        currentOnly: Boolean = false,
        now: Instant = Instant.now(),
    ): Measurement? {
        val root = runCatching { JSONTokener(raw).nextValue() }.getOrNull() ?: return null
        // Graph responses contain statistics too. Only results rows are measurements.
        if (!currentOnly && (root as? JSONObject)?.has("results") == true) {
            return graphMeasurements(raw, preferredSerial, now).lastOrNull()
        }
        val found = mutableListOf<Candidate>()
        walk(root, found, inheritedSerial = null, currentOnly = currentOnly)
        found.removeAll { !MeasurementValidity.isNotFuture(it.measurement, now) }
        if (found.isEmpty()) return null

        if (!preferredSerial.isNullOrBlank()) {
            val matching = found.filter { serialEquals(it.serial, preferredSerial) }
            if (matching.isNotEmpty()) return matching.maxByOrNull { it.measurement.measuredAt }?.measurement
        }

        if (requirePreferredSerialWhenAmbiguous && !preferredSerial.isNullOrBlank()) {
            // all_field is account-level. If the response did not give us a positive serial match,
            // only accept a single unlabelled measurement candidate. Multiple candidates or one
            // explicitly labelled as another device must fall back to the serial-specific graph.
            if (found.size != 1) return null
            val only = found.single()
            if (only.serial != null && !serialEquals(only.serial, preferredSerial)) return null
            return only.measurement
        }
        return found.maxByOrNull { it.measurement.measuredAt }?.measurement
    }

    /**
     * Official graph response contract recovered from the AquaWiz APK:
     * response.results.map(row => ({ date: row[0], ...transform(row[1]) }))
     *
     * For KH-series devices, field22 is the KH value and the official client transforms it by
     * dividing the raw value by 1000.
     */
    private fun extractOfficialGraphRows(root: Any?, preferredSerial: String?, out: MutableList<Candidate>) {
        val results = (root as? JSONObject)?.optJSONArray("results") ?: return
        for (i in 0 until results.length()) {
            val row = results.optJSONArray(i) ?: continue
            if (row.length() < 2) continue
            val measuredAt = parseInstant(row.opt(0)) ?: continue
            val fields = row.optJSONObject(1) ?: continue
            val kh = khFromField22(fields.opt("field22")) ?: continue
            if (kh !in 2.0..20.0) continue
            val ph = phFromField27(fields.opt("field27"))
            val phOpenAir = phFromField28(fields.opt("field28"))
            val deltaPh = if (ph != null && phOpenAir != null) ph - phOpenAir else null
            val doseMl = doseFromField26(fields.opt("field26"))
            out += Candidate(
                Measurement(
                    kh = kh,
                    measuredAt = measuredAt,
                    rawId = "graph:" + measuredAt.toEpochMilli(),
                    ph = ph,
                    phOpenAir = phOpenAir,
                    deltaPh = deltaPh,
                    doseMl = doseMl,
                ),
                preferredSerial,
            )
        }
    }

    private fun khFromField22(value: Any?): Double? {
        val raw = asDouble(value) ?: return null
        // Official transformKhRawValue(field22) => formatNumber(Number(value) / 1000, 3).
        // Accept already-scaled values defensively in case the server changes representation.
        return if (raw > 20.0) raw / 1000.0 else raw
    }

    private fun phFromField27(value: Any?): Double? {
        val raw = asDouble(value) ?: return null
        val scaled = if (raw > 12.0) raw / 1000.0 else raw
        return scaled.takeIf { it in 4.0..12.0 }
    }

    private fun phFromField28(value: Any?): Double? {
        val raw = asDouble(value) ?: return null
        val scaled = if (raw > 12.0) raw / 1000.0 else raw
        return scaled.takeIf { it in 4.0..12.0 }
    }

    private fun doseFromField26(value: Any?): Double? {
        val raw = asDouble(value) ?: return null
        // Captured KH1 graph field26=100 matches the official CSV dose of 50 mL.
        val scaled = raw / 2.0
        return scaled.takeIf { it >= 0.0 }
    }

    private fun walk(v: Any?, out: MutableList<Candidate>, inheritedSerial: String?, currentOnly: Boolean = false) {
        when (v) {
            is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i), out, inheritedSerial, currentOnly)
            is JSONObject -> {
                val localSerial = string(v, serialKeys) ?: inheritedSerial
                if (!currentOnly || v.has("latest_kh") || v.has("latestKh")) parseObject(v, currentOnly)?.let { out += Candidate(it, localSerial) }
                val it = v.keys()
                while (it.hasNext()) walk(v.opt(it.next()), out, localSerial, currentOnly)
            }
        }
    }

    private fun parseObject(o: JSONObject, currentOnly: Boolean = false): Measurement? {
        val kh = number(o, khKeys)?.let { if (it > 20.0) it / 1000.0 else it } ?: khFromField22(o.opt("field22")) ?: inferKhFromGraphObject(o) ?: return null
        if (kh !in 2.0..20.0) return null
        val whenAt = instant(o, if (currentOnly) listOf("latest_time", "latestTime") else timeKeys) ?: return null
        val id = string(o, idKeys)
        val ph = number(o, phKeys)?.let { if (it > 12.0) it / 1000.0 else it }?.takeIf { it in 4.0..12.0 } ?: phFromField27(o.opt("field27"))
        val phOpenAir = number(o, listOf("phOpenAir", "ph_open_air", "phO", "ph_o"))
            ?.takeIf { it in 4.0..12.0 } ?: phFromField28(o.opt("field28"))
        val deltaPh = number(o, listOf("deltaPh", "delta_ph", "delta"))
            ?: if (ph != null && phOpenAir != null) ph - phOpenAir else null
        val doseMl = number(o, listOf("doseMl", "dose_ml", "dailyDosingTotal", "dosingMl", "dosing_ml"))
            ?: doseFromField26(o.opt("field26"))
        return Measurement(
            kh = kh,
            measuredAt = whenAt,
            rawId = id,
            ph = ph,
            phOpenAir = phOpenAir,
            deltaPh = deltaPh,
            doseMl = doseMl,
        )
    }

    /**
     * Graph payloads may use opaque field names. Only accept a fallback value when exactly one
     * plausible KH candidate exists beside a valid timestamp; ambiguity fails closed.
     */
    private fun inferKhFromGraphObject(o: JSONObject): Double? {
        val candidates = mutableListOf<Double>()
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (!k.startsWith("field", true) && k !in listOf("value", "result")) continue
            if (k.equals("field22", ignoreCase = true)) continue
            val n = asDouble(o.opt(k)) ?: continue
            if (n in 2.0..20.0) candidates += n
        }
        return candidates.distinctBy { kotlin.math.round(it * 10000.0) }.singleOrNull()
    }

    private fun number(o: JSONObject, keys: List<String>): Double? {
        for (k in keys) asDouble(o.opt(k))?.let { return it }
        return null
    }

    private fun asDouble(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.trim().removeSuffix(" dKH").toDoubleOrNull()
        else -> null
    }

    private fun string(o: JSONObject, keys: List<String>): String? {
        for (k in keys) o.optString(k).takeIf { it.isNotBlank() && it != "null" }?.let { return it }
        return null
    }

    private fun instant(o: JSONObject, keys: List<String>): Instant? {
        for (k in keys) parseInstant(o.opt(k))?.let { return it }
        return null
    }

    private fun parseInstant(v: Any?): Instant? = when (v) {
        is Number -> {
            val n = v.toLong()
            runCatching { Instant.ofEpochMilli(if (n < 10_000_000_000L) n * 1000 else n) }.getOrNull()
        }
        is String -> {
            val x = v.trim()
            runCatching { Instant.parse(x) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(x).toInstant() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(x.replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
                ?: x.toLongOrNull()?.let { n -> runCatching { Instant.ofEpochMilli(if (n < 10_000_000_000L) n * 1000 else n) }.getOrNull() }
        }
        else -> null
    }

    private fun serialEquals(a: String?, b: String): Boolean =
        a?.trim()?.equals(b.trim(), ignoreCase = true) == true
}
