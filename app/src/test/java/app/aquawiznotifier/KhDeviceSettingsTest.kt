package app.aquawiznotifier

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.math.BigDecimal
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class KhDeviceSettingsTest {
    private val session = Session("test-user", "test-token", listOf("KH1-A"))
    private fun n(value: String) = BigDecimal(value)
    private val target = KhTargetSettings(n("8.5"), n("0.5"), 1, 22, 7)
    private val dosing = KhDosingSettings(n("100"), n("10"), n("0"), n("100"))
    private val summary = DeviceSummary(khTarget = 8.5, khDeviation = 0.5, dosingRemainingMl = 100.0, dosingWarningMl = 100.0,
        dosingField5 = "010123", dosingField6 = "0054567", measurementSchedule = "12207")

    @Test fun targetMatchesScaledKhAndPackedScheduleWithoutOtherFields() {
        val body = KhDeviceSettings.targetBody(session, " kh1-a ", target)
        assertEquals(setOf("user", "token", "serial", "field8", "field13", "field15"), body.keys().asSequence().toSet())
        assertEquals("8500", body.getString("field8")); assertEquals("500", body.getString("field15"))
        assertEquals("12207", body.getString("field13")); assertEquals("KH1-A", body.getString("serial"))
        assertEquals(target, KhDeviceSettings.target(summary))
    }
    @Test fun dosingPreservesCalibrationDigitsOnOtherModelsAndUsesDirectValuesOnKh1() {
        val direct = KhDeviceSettings.dosingBody(session, "KH1-A", dosing, summary)
        assertEquals("10", direct.getString("field5")); assertEquals("0", direct.getString("field6"))
        assertEquals("100", direct.getString("field14")); assertEquals("100", direct.getString("field16"))
        assertEquals(setOf("user", "token", "serial", "field5", "field6", "field14", "field16"), direct.keys().asSequence().toSet())
        val packed = KhDeviceSettings.dosingBody(session, "KH2-A", dosing, summary)
        assertEquals("010123", packed.getString("field5")); assertEquals("0004567", packed.getString("field6"))
        val read = KhDeviceSettings.dosing(summary, "KH2-A")
        assertEquals(0, read.amountPerDkh.compareTo(n("10"))); assertEquals(0, read.maxPerHour.compareTo(n("5")))
        val kh1 = KhDeviceSettings.dosing(summary.copy(dosingField5 = "10", dosingField6 = "0"), "KH1-A")
        assertEquals(0, kh1.amountPerDkh.compareTo(n("10"))); assertEquals(0, kh1.maxPerHour.signum())
    }
    @Test fun missingCurrentSettingsAndInvalidHoursCannotBeWritten() {
        fun invalid(action: () -> Unit) { try { action(); fail("Expected validation failure") } catch (_: IllegalArgumentException) {} }
        invalid { KhDeviceSettings.validate(target.copy(interval = 7)) }
        invalid { KhDeviceSettings.validate(target.copy(sleepFrom = 24)) }
        invalid { KhDeviceSettings.validate(target.copy(target = BigDecimal.ZERO)) }
        invalid { KhDeviceSettings.validate(dosing.copy(maxPerHour = n("-1")), "KH1-A") }
        invalid { KhDeviceSettings.validate(dosing.copy(remaining = n("10.5")), "KH1-A") }
        invalid { KhDeviceSettings.validate(dosing.copy(amountPerDkh = n("1000")), "KH2-A") }
        assertTrue(runCatching { KhDeviceSettings.target(summary.copy(measurementSchedule = null)) }.isFailure)
        assertTrue(runCatching { KhDeviceSettings.dosingBody(session, "KH2-A", dosing, summary.copy(dosingField6 = null)) }.isFailure)
        assertNull(DeviceSummaryJson.parse("""{"serial":"KH-B","field8":"8500"}""", "KH-A"))
        val parsed = DeviceSummaryJson.parse("""{"field8":"8500","field15":"500","field13":"12207","field5":"010123","field6":"0054567","field14":"100","field16":"100"}""", "KH2-A")!!
        assertEquals(summary.dosingField5, parsed.dosingField5); assertEquals(target, KhDeviceSettings.target(parsed))
    }
    @Test fun postsBothSettingsContractsAndDoesNotRetryRejectedDosing() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val bodies = mutableListOf<JSONObject>()
        var response = 200
        server.createContext(KhCalibration.PATH) { exchange ->
            assertEquals("POST", exchange.requestMethod)
            assertEquals("Bearer test-token", exchange.requestHeaders.getFirst("Authorization"))
            bodies += JSONObject(exchange.requestBody.bufferedReader().use { it.readText() })
            val bytes = "ok".toByteArray(); exchange.sendResponseHeaders(response, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = AquaWizApi("http://127.0.0.1:${server.address.port}")
            api.setKhTarget(session, "KH1-A", target); api.setKhDosing(session, "KH1-A", dosing, summary)
            assertEquals("8500", bodies[0].getString("field8")); assertEquals("0", bodies[1].getString("field6"))
            response = 403
            try { api.setKhDosing(session, "KH1-A", dosing, summary); fail("Expected rejected write") }
            catch (error: AquaWizApi.ApiException) { assertEquals(403, error.status) }
            assertEquals(3, bodies.size)
        } finally { server.stop(0) }
    }
    @Test fun intervalAndSleepEncodingIsIndependentOfPhoneTimezone() {
        val original = java.util.TimeZone.getDefault()
        try {
            for (zone in listOf("America/Detroit", "UTC", "Asia/Shanghai")) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone))
                for (interval in 1..6) {
                    val requested = target.copy(interval = interval, sleepFrom = 0, sleepTo = 7)
                    val packed = KhDeviceSettings.targetBody(session, "KH1-A", requested).getString("field13")
                    assertEquals("${interval}0007", packed)
                    assertTrue(KhDeviceSettings.matchesTarget(summary.copy(measurementSchedule = packed), requested))
                }
            }
        } finally { java.util.TimeZone.setDefault(original) }
    }
    @Test fun readbackDistinguishesMatchingPendingAndUnavailableSettingsWithoutRepeatingWrites() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var writes = 0
        var readStatus = 200
        var schedule = "32207"
        server.createContext(KhCalibration.PATH) { exchange ->
            exchange.requestBody.use { it.readBytes() }; writes++
            val bytes = "ok".toByteArray(); exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/api/v1/KH/KH1-A/all_field") { exchange ->
            val bytes = """{"field8":"8500","field15":"500","field13":"$schedule"}""".toByteArray()
            exchange.sendResponseHeaders(readStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = AquaWizApi("http://127.0.0.1:${server.address.port}")
            val requested = target.copy(interval = 3)
            val matching = api.setKhTargetAndReadBack(session, "KH1-A", requested)
            assertTrue(matching.matches); assertEquals("32207", matching.summary!!.measurementSchedule)
            schedule = "12207"
            val pending = api.setKhTargetAndReadBack(session, "KH1-A", requested)
            assertFalse(pending.matches); assertEquals("12207", pending.summary!!.measurementSchedule)
            readStatus = 403
            val unavailable = api.setKhTargetAndReadBack(session, "KH1-A", requested)
            assertFalse(unavailable.matches); assertNull(unavailable.summary)
            assertEquals(403, (unavailable.error as AquaWizApi.ApiException).status)
            assertEquals(3, writes)
            assertFalse(KhDeviceSettings.matchesTarget(summary.copy(measurementSchedule = null), requested))
            assertFalse(KhDeviceSettings.matchesTarget(summary.copy(khTarget = 8.6, measurementSchedule = "32207"), requested))
        } finally { server.stop(0) }
    }
}
