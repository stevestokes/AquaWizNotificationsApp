package app.aquawiznotifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

class MeasurementJsonTest {
    @Test fun selectsConfiguredDeviceFromAccountLevelPayload() {
        val raw = """
            {
              "devices": [
                {"deviceSerial":"KH-A", "latest_kh":7.91, "latest_time":"2026-09-29T12:00:00Z"},
                {"deviceSerial":"KH-B", "latest_kh":8.17, "latest_time":"2026-09-29T12:05:00Z"}
              ]
            }
        """.trimIndent()

        val result = MeasurementJson.findLatest(raw, "KH-A", requirePreferredSerialWhenAmbiguous = true)
        assertNotNull(result)
        assertEquals(7.91, result!!.kh, 0.0001)
        assertEquals(Instant.parse("2026-09-29T12:00:00Z"), result.measuredAt)
    }

    @Test fun accountLevelPayloadFailsClosedWhenConfiguredSerialDoesNotMatchMultipleDevices() {
        val raw = """
            {
              "devices": [
                {"deviceSerial":"KH-A", "latest_kh":7.91, "latest_time":"2026-09-29T12:00:00Z"},
                {"deviceSerial":"KH-B", "latest_kh":8.17, "latest_time":"2026-09-29T12:05:00Z"}
              ]
            }
        """.trimIndent()

        assertNull(MeasurementJson.findLatest(raw, "KH-C", requirePreferredSerialWhenAmbiguous = true))
    }

    @Test fun graphFallbackCanUseSingleExplicitMeasurementWithoutSerial() {
        val raw = """{"points":[{"kh":"8.12", "timestamp":"2026-09-29T13:04:00Z"}]}"""
        val result = MeasurementJson.findLatest(raw, "KH-A", requirePreferredSerialWhenAmbiguous = false)
        assertNotNull(result)
        assertEquals(8.12, result!!.kh, 0.0001)
    }

    @Test fun rejectsImplausibleKh() {
        val raw = """{"latest_kh":88.1,"latest_time":"2026-09-29T13:04:00Z"}"""
        assertNull(MeasurementJson.findLatest(raw))
    }
    @Test fun accountLevelPayloadDoesNotGuessBetweenMultipleUnlabelledCandidates() {
        val raw = """
            {"values":[
              {"latest_kh":7.91,"latest_time":"2026-09-29T12:00:00Z"},
              {"latest_kh":8.17,"latest_time":"2026-09-29T12:05:00Z"}
            ]}
        """.trimIndent()
        assertNull(MeasurementJson.findLatest(raw, "KH-A", requirePreferredSerialWhenAmbiguous = true))
    }


    @Test fun parsesOfficialRawGraphRowsAndScalesField22() {
        val raw = """
            {
              "results": [
                ["2026-09-29T12:04:00Z", {"field22":"8123","field23":"821"}],
                ["2026-09-29T13:04:00Z", {"field22":8176,"field23":"824"}]
              ]
            }
        """.trimIndent()

        val result = MeasurementJson.findLatest(raw, "KH1-00-05117", requirePreferredSerialWhenAmbiguous = false)
        assertNotNull(result)
        assertEquals(8.176, result!!.kh, 0.0001)
        assertEquals(Instant.parse("2026-09-29T13:04:00Z"), result.measuredAt)
    }

    @Test fun parsesAlreadyTransformedGraphObjectWithField22() {
        val raw = """{"results":[{"date":"2026-09-29T13:04:00Z","field22":"8.176"}]}"""
        val result = MeasurementJson.findLatest(raw, "KH1-00-05117", requirePreferredSerialWhenAmbiguous = false)
        assertNotNull(result)
        assertEquals(8.176, result!!.kh, 0.0001)
    }

}
