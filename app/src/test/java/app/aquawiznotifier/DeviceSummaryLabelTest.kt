package app.aquawiznotifier

import org.junit.Assert.*
import org.junit.Test

class DeviceSummaryLabelTest {
    @Test fun deviceScopedResponseWithTankNameLoadsActualSettings() {
        val raw = """{"serial":"Lagoon Reef","field5":"16","field6":"3","field8":"8000","field13":"10000","field14":"201","field15":"300","field16":"250","latest_ph":"399"}"""
        val summary = DeviceSummaryJson.parse(raw, "KH1-00-TEST")!!
        assertEquals(8.0, summary.khTarget!!, 0.0)
        assertEquals(201.0, summary.dosingRemainingMl!!, 0.0)
        assertEquals(250.0, summary.dosingWarningMl!!, 0.0)
        assertEquals(399.0, summary.phProbeStatus!!, 0.0)
        assertEquals("10000", summary.measurementSchedule)
        assertEquals("16", summary.dosingField5)
        assertEquals("3", summary.dosingField6)
    }
    @Test fun explicitWrongControllerStillRejectedEvenWithTankLabel() {
        assertNull(DeviceSummaryJson.parse("""{"serial":"Lagoon Reef","deviceSerial":"KH-B","field8":8000}""", "KH-A"))
        assertNull(DeviceSummaryJson.parse("""{"serial":"KH-B","field8":8000}""", "KH-A"))
    }
    @Test fun multipleNamedControllerSettingsRemainAmbiguous() {
        assertNull(DeviceSummaryJson.parse("""[{"serial":"Lagoon Reef","field8":8000},{"serial":"Other Reef","field8":9000}]""", "KH-A"))
        assertNull(DeviceSummaryJson.parse("""{"serial":"Lagoon Reef","field8":8000,"child":{"field8":9000}}""", "KH-A"))
    }
}
