package app.aquawiznotifier

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class IngestionCaptureStoreTest {
    @Test fun capturedDailySummariesAreIdentifiedButRealEightAmReadingsAreProtected() {
        val directory = Files.createTempDirectory("aw-daily-evidence").toFile()
        try {
            val fixture = JSONObject(javaClass.getResource("/daily-summary-capture.json")!!.readText()).getJSONArray("captures")
            val store = IngestionCaptureStore(directory)
            store.record(fixture.getJSONObject(0)); store.record(fixture.getJSONObject(1))
            val serial = "KH-A"
            val summaries = MeasurementJson.graphMeasurements(fixture.getJSONObject(1).getJSONObject("graph").toString(), serial)
            assertEquals(2, store.confirmedDailySummaryIdentities().size)
            // A genuine raw row at the very same 8 AM timestamp must survive, even with identical values.
            val raw = fixture.getJSONObject(0)
            raw.getJSONObject("graph").getJSONArray("results").put(
                fixture.getJSONObject(1).getJSONObject("graph").getJSONArray("results").getJSONArray(0))
            store.record(raw)
            val identities = store.confirmedDailySummaryIdentities()
            assertFalse(summaryIdentity(serial, summaries.first()) in identities)
            assertTrue(summaryIdentity(serial, summaries.last()) in identities)
        } finally { directory.deleteRecursively() }
    }
    private fun entry(id: Int, eight: Boolean = false, serial: String = "KH-A") = JSONObject()
        .put("serial", serial).put("id", id).put("timeZone", "America/New_York")
        .put("acceptedReadings", JSONArray().put(JSONObject().put("measuredAt",
            if (eight) "2026-10-08T12:00:00Z" else "2026-10-08T11:34:00Z")))

    @Test fun keepsEightAmEvidenceWhenRoutineCapturesRotateAndFiltersDevice() {
        val directory = Files.createTempDirectory("aw-captures").toFile()
        try {
            val store = IngestionCaptureStore(directory, recentLimit = 2, eightAmLimit = 2)
            store.record(entry(1, eight = true))
            repeat(5) { store.record(entry(it + 2)) }
            store.record(entry(100, serial = "KH-B"))
            val captures = JSONObject(store.export("kh-a", "test")).getJSONArray("captures")
            assertTrue((0 until captures.length()).any { captures.getJSONObject(it).getInt("id") == 1 })
            assertFalse((0 until captures.length()).any { captures.getJSONObject(it).getInt("id") == 100 })
            assertTrue(directory.listFiles()!!.size <= 4)
            assertEquals(captures.length(), JSONObject(IngestionCaptureStore(directory).export("KH-A", "test"))
                .getJSONArray("captures").length())
        } finally { directory.deleteRecursively() }
    }

    @Test fun retainsRawEightAmRowsEvenWhenParserRejectsThem() {
        val directory = Files.createTempDirectory("aw-rejected").toFile()
        try {
            val store = IngestionCaptureStore(directory, recentLimit = 1)
            store.record(JSONObject().put("serial", "KH-A").put("timeZone", "America/New_York")
                .put("acceptedReadings", JSONArray()).put("graph", JSONObject().put("results",
                    JSONArray().put(JSONArray().put(1791460800000L).put(JSONObject().put("field22", 8044))))))
            store.record(JSONObject().put("serial", "KH-A").put("timeZone", "America/New_York")
                .put("status", JSONObject().put("latest_time", 1791460800000L)))
            repeat(5) { store.record(entry(it)) }
            assertEquals(3, JSONObject(store.export("KH-A", "test")).getJSONArray("captures").length())
        } finally { directory.deleteRecursively() }
    }

    @Test fun storageBudgetIsEnforcedAndOversizedRecordDoesNotReplaceEvidence() {
        val directory = Files.createTempDirectory("aw-budget").toFile()
        try {
            val store = IngestionCaptureStore(directory, byteLimit = 4096)
            repeat(30) { store.record(entry(it).put("payload", "x".repeat(500))) }
            assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 4096)
            val before = JSONObject(store.export("KH-A", "test")).getJSONArray("captures").length()
            store.record(entry(100).put("payload", "x".repeat(5000)))
            assertEquals(before, JSONObject(store.export("KH-A", "test")).getJSONArray("captures").length())
        } finally { directory.deleteRecursively() }
    }
}
