package app.aquawiznotifier

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AquaWizAuthContractTest {
    @Test
    fun officialLoginPayloadUsesUserAndEmptyToken() {
        val body = AquaWizApi.buildLoginBody("reef-user", "secret")
        val json = JSONObject(body)

        assertEquals("reef-user", json.getString("user"))
        assertEquals("secret", json.getString("password"))
        assertEquals("", json.getJSONObject("token").getString("access_token"))
        assertFalse(json.has("username"))
    }
}
