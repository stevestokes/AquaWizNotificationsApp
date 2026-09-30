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


    @Test fun officialGraphRowMapsKhPhOpenAirDeltaAndDose() {
        val raw = """
            {
              "results": [
                ["2026-09-29T13:04:00Z", {
                  "field22":8420,
                  "field26":6000,
                  "field27":8270,
                  "field28":8350
                }]
              ]
            }
        """.trimIndent()

        val result = MeasurementJson.findLatest(raw, "KH1-00-05117", requirePreferredSerialWhenAmbiguous = false)
        assertNotNull(result)
        assertEquals(8.42, result!!.kh, 0.0001)
        assertEquals(8.27, result.ph!!, 0.0001)
        assertEquals(8.35, result.phOpenAir!!, 0.0001)
        assertEquals(-0.08, result.deltaPh!!, 0.0001)
        assertEquals(1.20, result.doseMl!!, 0.0001)
    }

    @Test fun allFieldUsesLatestPh1NotLatestPhStatus() {
        val raw = """
            {
              "deviceSerial":"KH1-00-05117",
              "latest_kh":8.42,
              "latest_time":"2026-09-29T13:04:00Z",
              "latest_ph":1,
              "latest_ph1":8.27
            }
        """.trimIndent()

        val result = MeasurementJson.findLatest(raw, "KH1-00-05117", requirePreferredSerialWhenAmbiguous = true)
        assertNotNull(result)
        assertEquals(8.27, result!!.ph!!, 0.0001)
    }


    @Test fun graphMeasurementsReturnsFullSortedSeries() {
        val raw = """
            {
              "results": [
                ["2026-09-29T15:00:00Z", {"field22":9990,"field27":7790,"field28":7900,"field26":0}],
                ["2026-09-29T11:00:00Z", {"field22":10050,"field27":7730,"field28":7840,"field26":5000}]
              ]
            }
        """.trimIndent()

        val result = MeasurementJson.graphMeasurements(raw, "KH1-00-05117")
        assertEquals(2, result.size)
        assertEquals(Instant.parse("2026-09-29T11:00:00Z"), result[0].measuredAt)
        assertEquals(10.05, result[0].kh, 0.0001)
        assertEquals(7.73, result[0].ph!!, 0.0001)
        assertEquals(1.0, result[0].doseMl!!, 0.0001)
        assertEquals(Instant.parse("2026-09-29T15:00:00Z"), result[1].measuredAt)
    }

    @Test fun scalesRawCurrentValuesAndSmallGraphDoses() {
        val current = MeasurementJson.findLatest("""{"latest_kh":8420,"latest_ph1":8270,"latest_time":"2026-09-29T13:04:00Z"}""")!!
        assertEquals(8.42, current.kh, 0.00001)
        assertEquals(8.27, current.ph!!, 0.00001)
        val rows = MeasurementJson.graphMeasurements("""{"results":[["2026-09-29T13:04:00Z",{"field22":8420,"field26":50}]]}""")
        assertEquals(0.01, rows.single().doseMl!!, 0.00001)
    }
    @Test fun currentAndGraphReadingsMergeWithoutLosingOptionalFields() {
        val at = Instant.parse("2026-09-29T13:04:00Z")
        val current = Measurement(8.42, at, ph = 8.27)
        val graph = Measurement(8.42, at, rawId = "graph:1", ph = 8.27, phOpenAir = 8.35, doseMl = 1.2)
        val result = HistoryMerge.merge(listOf("KH-A" to current, "kh-a" to graph, "KH-B" to graph))
        assertEquals(2, result.size)
        assertEquals(8.35, result.first().second.phOpenAir!!, 0.0001)
        assertEquals(1.2, result.first().second.doseMl!!, 0.0001)
    }

}
