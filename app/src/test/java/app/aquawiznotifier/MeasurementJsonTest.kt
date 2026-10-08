package app.aquawiznotifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

class MeasurementJsonTest {
    private val reproductionNow = Instant.parse("2026-10-06T04:39:00Z")
    @Test fun capturedGraphRowMatchesOfficialCsvUnitsAndTimestamp() {
        val raw = """{"sample_size":1,"device":"KH-A","results":[[1791467192000,
            {"field22":8390,"field23":0,"field24":12389,"field25":7278,
             "field26":100,"field27":8348,"field28":8339}]]}"""
        val row = MeasurementJson.graphMeasurements(raw, "KH-A", Instant.parse("2026-10-08T16:00:00Z")).single()
        assertEquals(Instant.parse("2026-10-08T13:46:32Z"), row.measuredAt)
        assertEquals(8.39, row.kh, 0.00001)
        assertEquals(8.348, row.ph!!, 0.00001)
        assertEquals(8.339, row.phOpenAir!!, 0.00001)
        assertEquals(0.009, row.deltaPh!!, 0.00001)
        assertEquals(50.0, row.doseMl!!, 0.00001)
    }
    @Test fun graphRejectsSummaryObjectsEvenWhenDatedInThePast() {
        val raw = """{"results":[["2026-10-08T11:34:00Z",{"field22":8102}],
            {"date":"2026-10-08T12:00:00Z","field22":8044,"field27":8329.5,"field28":8306.333}],
            "summary":{"date":"2026-10-08T12:00:00Z","field22":8044}}"""
        val rows = MeasurementJson.graphMeasurements(raw, "KH-A", Instant.parse("2026-10-08T13:40:00Z"))
        assertEquals(1, rows.size)
        assertEquals(8.102, rows.single().kh, 0.00001)
    }
    @Test fun statusLatestAverageCannotBecomeMeasurementEvenAfterEightAm() {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        fun respond(exchange: com.sun.net.httpserver.HttpExchange, raw: String) {
            val bytes = raw.toByteArray(); exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/api/v1/KH/KH-A/all_field") { e ->
            respond(e, """{"field8":8000,"latest_kh":8044,"latest_time":"2026-10-08T12:00:00Z","field27":8329.5}""")
        }
        server.createContext("/api/v1/query/device/KH-A/graph") { e ->
            respond(e, """{"results":[["2026-10-08T11:34:00Z",{"field22":8102,"field27":8270,"field28":8333}]]}""")
        }
        server.start()
        try {
            var summary: DeviceSummary? = null
            val api = AquaWizApi("http://127.0.0.1:${server.address.port}")
            val result = api.latestMeasurement(Session("test", "test-token", listOf("KH-A")), "KH-A") { summary = it }
            assertEquals(8.102, result.kh, 0.00001)
            assertEquals(Instant.parse("2026-10-08T11:34:00Z"), result.measuredAt)
            assertEquals(8.0, summary!!.khTarget!!, 0.00001)
        } finally { server.stop(0) }
    }
    @Test fun graphSummaryCannotBecomeEightAmMeasurement() {
        val raw = """{"results":[["2026-10-06T04:34:00Z",{"field22":8312,"field27":8400,"field28":8340,"field26":0}]],
            "summary":{"date":"2026-10-06T12:00:00Z","field22":8181,"field27":8463.5,"field28":8350.5,"field26":0}}"""
        val result = MeasurementJson.findLatest(raw, "KH-A", now = reproductionNow)!!
        assertEquals(8.312, result.kh, 0.00001)
        assertEquals(Instant.parse("2026-10-06T04:34:00Z"), result.measuredAt)
        // Even after 8 AM, summary/metadata objects must never be promoted to readings.
        assertEquals(result, MeasurementJson.findLatest(raw, "KH-A", now = reproductionNow.plusSeconds(12 * 3600)))
    }
    @Test fun futureResultsRowIsRejectedWithoutChangingTimestampOrValues() {
        val raw = """{"results":[["2026-10-06T04:34:00Z",{"field22":8312}],
            ["2026-10-06T12:00:00Z",{"field22":8181,"field27":8463.5,"field28":8350.5}]]}"""
        val result = MeasurementJson.graphMeasurements(raw, "KH-A", reproductionNow)
        assertEquals(1, result.size); assertEquals(8.312, result.single().kh, 0.00001)
    }
    @Test fun currentFieldsRequireTheirOwnMeasurementTimestampAndRejectFuture() {
        val invalid = """{"latest_kh":8181,"date":"2026-10-06T12:00:00Z"}"""
        assertNull(MeasurementJson.findLatest(invalid, currentOnly = true, now = reproductionNow))
        val future = """{"latest_kh":8181,"latest_time":"2026-10-06T12:00:00Z"}"""
        assertNull(MeasurementJson.findLatest(future, currentOnly = true, now = reproductionNow))
        val current = """{"latest_kh":8312,"latest_time":"2026-10-06T04:34:00Z"}"""
        assertEquals(8.312, MeasurementJson.findLatest(current, currentOnly = true, now = reproductionNow)!!.kh, 0.00001)
    }
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

    @Test fun rejectsAlreadyTransformedGraphObjects() {
        val raw = """{"results":[{"date":"2026-09-29T13:04:00Z","field22":"8.176"}]}"""
        val result = MeasurementJson.findLatest(raw, "KH1-00-05117", requirePreferredSerialWhenAmbiguous = false)
        assertNull(result)
    }


    @Test fun officialGraphRowMapsKhPhOpenAirDeltaAndDose() {
        val raw = """
            {
              "results": [
                ["2026-09-29T13:04:00Z", {
                  "field22":8420,
                  "field26":2.4,
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
                ["2026-09-29T11:00:00Z", {"field22":10050,"field27":7730,"field28":7840,"field26":2}]
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
        assertEquals(25.0, rows.single().doseMl!!, 0.00001)
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
