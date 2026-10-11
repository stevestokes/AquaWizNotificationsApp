package app.aquawiznotifier

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DiagnosticRequestTest {
    private class Connection(val code: Int, val response: String) : HttpURLConnection(URL("https://example.invalid")) {
        val sent = ByteArrayOutputStream()
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun connect() {}
        override fun getResponseCode() = code
        override fun getOutputStream() = sent
        override fun getInputStream() = ByteArrayInputStream(response.toByteArray())
        override fun getErrorStream() = ByteArrayInputStream(response.toByteArray())
    }
    @Test fun capturesSettingsRequestAndParserOutcomeWithoutCredentials() {
        val records = mutableListOf<JSONObject>()
        val conn = Connection(200, """{"serial":"Lagoon Reef","field8":8000,"token":"other-secret","password":"pw","note":"active-secret"}""")
        val api = AquaWizApi("https://example.invalid", { records.add(it) }, "settings", true, { conn })
        api.rawAllFields(Session("private-user", "active-secret", listOf("KH-A")), "KH-A")
        assertEquals(listOf("http-start", "http-response", "settings-parse"), records.map { it.getString("type") })
        assertTrue(records.last().getBoolean("identified"))
        assertEquals(records[0].getString("requestId"), records[1].getString("requestId"))
        val exported = records.joinToString()
        listOf("active-secret", "other-secret", "private-user", "\"pw\"").forEach { assertFalse(exported.contains(it)) }
        assertTrue(conn.sent.toString().contains("active-secret")) // Transport still receives real credentials.
    }
    @Test fun authRejectionRetainsStatusAndSanitizedBodyThenThrows() {
        val records = mutableListOf<JSONObject>()
        val api = AquaWizApi("https://example.invalid", { records.add(it) }, "poll", true,
            { Connection(401, """{"error":"session expired","access_token":"secret"}""") })
        try { api.rawGraph(Session("user", "secret", emptyList()), "KH-A"); fail("Expected rejection") }
        catch (e: AquaWizApi.ApiException) { assertEquals(401, e.status) }
        assertEquals(listOf("http-start", "http-response", "http-failure"), records.map { it.getString("type") })
        assertEquals("session expired", records[1].getJSONObject("responseBody").getString("error"))
        assertFalse(records.joinToString().contains("secret"))
    }
    @Test fun networkFailureAndDisabledLogging() {
        val records = mutableListOf<JSONObject>()
        fun api(enabled: Boolean) = AquaWizApi("https://example.invalid", { records.add(it) }, "settings", enabled,
            { throw java.io.IOException("token-secret") })
        runCatching { api(true).rawGraph(Session("u", "token-secret", emptyList()), "KH-A") }
        assertEquals("IOException", records.last().getString("errorType"))
        assertFalse(records.joinToString().contains("token-secret"))
        records.clear()
        runCatching { api(false).rawGraph(Session("u", "token-secret", emptyList()), "KH-A") }
        assertTrue(records.isEmpty())
    }
    @Test fun nestedCredentialsAndNonJsonAreOmitted() {
        val safe = DiagnosticRedaction.body("""{"nested":[{"refreshToken":"secret","cookie":"secret","user":"private"}],"field14":201}""", null).toString()
        assertFalse(safe.contains("secret")); assertFalse(safe.contains("private")); assertTrue(safe.contains("201"))
        assertFalse(DiagnosticRedaction.body("<html>password=secret</html>", null).toString().contains("secret"))
    }
}
