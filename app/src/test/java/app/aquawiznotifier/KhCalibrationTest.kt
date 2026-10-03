package app.aquawiznotifier

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class KhCalibrationTest {
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
