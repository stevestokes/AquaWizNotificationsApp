package app.aquawiznotifier

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.json.JSONObject

class KhCalibrationTest {
    @Test fun sendsVerifiedCalibrationRequestAndDoesNotRetryARejectedWrite() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requests = 0
        var method = ""
        var received: JSONObject? = null
        var response = 200
        server.createContext(KhCalibration.PATH) { exchange ->
            requests++
            method = exchange.requestMethod
            received = JSONObject(exchange.requestBody.bufferedReader().use { it.readText() })
            val bytes = "ok".toByteArray()
            exchange.sendResponseHeaders(response, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = AquaWizApi("http://127.0.0.1:${server.address.port}")
            val session = Session("test-user", "test-token", listOf("KH-A"))
            api.setTrueTankKh(session, "KH-A", BigDecimal("7.86"))
            assertEquals("POST", method)
            assertEquals("7860", received!!.getString("field10"))
            assertEquals("KH-A", received!!.getString("serial"))
            response = 403
            try {
                api.setTrueTankKh(session, "KH-A", BigDecimal.ZERO)
                fail("Rejected calibration must throw")
            } catch (error: AquaWizApi.ApiException) { assertEquals(403, error.status) }
            assertEquals(2, requests)
        } finally { server.stop(0) }
    }
    @Test fun matchesOfficialEndpointAndScaledFieldWithNoOtherSettingChanges() {
        val body = KhCalibration.body(Session("test-account", "test-token", listOf("KH-A")), " kh-a ", KhCalibration.parse("7.861")!!)
        assertEquals("/api/v1/KH/start-config", KhCalibration.PATH)
        assertEquals(setOf("user", "token", "serial", "field10"), body.keys().asSequence().toSet())
        assertEquals("KH-A", body.getString("serial"))
        assertEquals("7861", body.getString("field10"))
        assertEquals("test-account", body.getString("user"))
        assertEquals("test-token", body.getJSONObject("token").getString("access_token"))
        assertEquals("0", KhCalibration.body(Session("user", "token", emptyList()), "KH-A", BigDecimal.ZERO).getString("field10"))
        assertEquals("8200", KhCalibration.body(Session("user", "token", emptyList()), "KH-A", KhCalibration.parse("8,20")!!).getString("field10"))
    }
    @Test fun validatesDecimalInputAndReadsCurrentTrueTankKhWithoutInventingIt() {
        for (invalid in listOf("", " ", "-1", "NaN", "Infinity", "1e4", "7.5.2", "seven")) assertNull(KhCalibration.parse(invalid))
        assertEquals(BigDecimal("0.5"), KhCalibration.parse(".5"))
        assertEquals(7.86, DeviceSummaryJson.parse("""{"field10":"7860"}""", "KH-A")!!.trueTankKh!!, 0.00001)
        assertEquals(0.0, DeviceSummaryJson.parse("""{"field10":"0"}""", "KH-A")!!.trueTankKh!!, 0.00001)
        assertNull(DeviceSummaryJson.parse("""{"field8":"8200"}""", "KH-A")!!.trueTankKh)
        assertNull(DeviceSummaryJson.parse("""{"serial":"KH-B","field10":"7860"}""", "KH-A"))
    }
    @Test fun probeHealthIncludesPercentWithoutTreatingFailureCodesAsPercent() {
        assertEquals("PH Probe: Healthy · 100%", ProbeHealth.label(100.0))
        assertEquals("PH Probe: Healthy · 100%", ProbeHealth.label(999.0))
        assertEquals("PH Probe: 75%", ProbeHealth.label(75.0))
        assertEquals(100.0, ProbeHealth.percent(150.0)!!, 0.001)
        assertEquals(0.0, ProbeHealth.percent(0.0)!!, 0.001)
        assertEquals("PH Probe: Fail", ProbeHealth.label(1000.0))
        assertNull(ProbeHealth.percent(1000.0)); assertNull(ProbeHealth.percent(null))
    }
}
