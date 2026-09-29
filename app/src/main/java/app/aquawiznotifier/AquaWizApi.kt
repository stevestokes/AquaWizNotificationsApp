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

class AquaWizApi(private val baseUrl: String = GLOBAL_BASE) {
    companion object {
        const val GLOBAL_BASE = "https://server.aquawiz.net"
        const val CHINA_BASE = "https://server.aquawiz.cn"

        internal fun buildLoginBody(username: String, password: String): String =
            JSONObject()
                .put("user", username)
                .put("password", password)
                .put("token", JSONObject().put("access_token", ""))
                .toString()
    }

    class ApiException(val status: Int, message: String) : Exception(message)

    fun login(username: String, password: String): Session {
        // Exact auth payload used by the official AquaWiz Android app (recovered from Hermes bytecode).
        // AquaWiz expects the account identifier under "user", not "username", and includes an
        // initially-empty token object even for the credential exchange.
        val body = buildLoginBody(username, password)
        val response = request("POST", "$baseUrl/api/v1/KH/auth", body = body)
        val root = JSONObject(response)
        val token = firstString(root, listOf("access_token", "accessToken"))
            ?: root.optJSONObject("token")?.let { firstString(it, listOf("access_token", "accessToken")) }
            ?: (root.opt("token") as? String)?.takeIf { it.isNotBlank() }
            ?: throw ApiException(200, "AquaWiz login response did not contain an access token")
        val user = root.optJSONObject("user") ?: root
        val devices = extractDevices(user.opt("devices")).ifEmpty { extractDevices(root.opt("devices")) }
        return Session(username.trim(), password, token, devices)
    }

    /**
     * Reads the current AquaWiz value using the two cloud calls recovered from the official APK.
     *
     * The account-level all_field call is preferred because the official app names its current
     * values latest_kh/latest_time. The device graph call is retained as a fallback in case the
     * undocumented all_field schema changes.
     */
    fun latestMeasurement(session: Session, serial: String): Measurement {
        val normalizedSerial = serial.trim().uppercase()
        val directFailure: Exception? = try {
            val raw = rawAllFields(session, normalizedSerial)
            MeasurementJson.findLatest(raw, normalizedSerial, requirePreferredSerialWhenAmbiguous = true)?.let { return it }
            ApiException(200, "AquaWiz all_field response did not contain a current KH value for device $normalizedSerial")
        } catch (e: ApiException) {
            if (e.status == 401 || e.status == 403) throw e
            e
        } catch (e: Exception) {
            e
        }

        try {
            val raw = rawGraph(session, normalizedSerial)
            return MeasurementJson.findLatest(raw, normalizedSerial, requirePreferredSerialWhenAmbiguous = false)
                ?: throw ApiException(200, "Connected, but no KH measurement could be identified in the AquaWiz graph response")
        } catch (e: ApiException) {
            if (e.status == 401 || e.status == 403) throw e
            val detail = directFailure?.message?.let { "all_field: $it; graph: ${e.message}" } ?: e.message.orEmpty()
            throw ApiException(e.status, detail)
        }
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
    fun rawGraph(session: Session, serial: String): String {
        val since = Instant.now().minusSeconds(8 * 60 * 60).toString()
        val encodedSerial = URLEncoder.encode(serial.trim().uppercase(), StandardCharsets.UTF_8.toString()).replace("+", "%20")
        val encodedDate = URLEncoder.encode(since, StandardCharsets.UTF_8.toString())
        return request("GET", "$baseUrl/api/v1/query/device/$encodedSerial/graph?date=$encodedDate", token = session.accessToken)
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
    private val phKeys = listOf("latest_ph", "latest_ph1", "latestPh", "phValue", "ph", "pH")

    private data class Candidate(val measurement: Measurement, val serial: String?)

    fun findLatest(
        raw: String,
        preferredSerial: String? = null,
        requirePreferredSerialWhenAmbiguous: Boolean = false,
    ): Measurement? {
        val root = runCatching { JSONTokener(raw).nextValue() }.getOrNull() ?: return null
        val found = mutableListOf<Candidate>()
        extractOfficialGraphRows(root, preferredSerial, found)
        walk(root, found, inheritedSerial = null)
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
            out += Candidate(
                Measurement(
                    kh = kh,
                    measuredAt = measuredAt,
                    rawId = "graph:" + measuredAt.toEpochMilli(),
                    ph = null,
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

    private fun walk(v: Any?, out: MutableList<Candidate>, inheritedSerial: String?) {
        when (v) {
            is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i), out, inheritedSerial)
            is JSONObject -> {
                val localSerial = string(v, serialKeys) ?: inheritedSerial
                parseObject(v)?.let { out += Candidate(it, localSerial) }
                val it = v.keys()
                while (it.hasNext()) walk(v.opt(it.next()), out, localSerial)
            }
        }
    }

    private fun parseObject(o: JSONObject): Measurement? {
        val kh = number(o, khKeys) ?: khFromField22(o.opt("field22")) ?: inferKhFromGraphObject(o) ?: return null
        if (kh !in 2.0..20.0) return null
        val whenAt = instant(o, timeKeys) ?: return null
        val id = string(o, idKeys)
        val ph = number(o, phKeys)?.takeIf { it in 4.0..12.0 }
        return Measurement(kh = kh, measuredAt = whenAt, rawId = id, ph = ph)
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
