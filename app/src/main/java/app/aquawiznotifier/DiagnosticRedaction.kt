package app.aquawiznotifier

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Fail closed for non-JSON pages; strip credentials recursively and exact active-token echoes. */
internal object DiagnosticRedaction {
    fun text(value: String, token: String?): String =
        (if (token.isNullOrBlank()) value else value.replace(token, "[REDACTED]"))
            .replace(Regex("""(?i)Bearer\s+[^\s"<>]+"""), "Bearer [REDACTED]")
            .replace(Regex("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"), "[REDACTED]")

    fun body(raw: String?, token: String?): Any {
        if (raw == null) return JSONObject.NULL
        val parsed = runCatching { JSONTokener(raw).nextValue() }.getOrNull()
        if (parsed !is JSONObject && parsed !is JSONArray) {
            return JSONObject().put("omitted", "Non-JSON response").put("characters", raw.length)
        }
        fun walk(value: Any?): Any? = when (value) {
            is JSONObject -> value.apply {
                keys().asSequence().toList().forEach { key ->
                    val name = key.lowercase(java.util.Locale.ROOT).replace("_", "").replace("-", "")
                    val sensitive = listOf("token", "password", "passcode", "secret", "credential", "authorization", "cookie").any { name.contains(it) } ||
                        name in listOf("user", "username", "email", "pw", "passwd") || name.endsWith("pw")
                    put(key, if (sensitive) "[REDACTED]" else walk(opt(key)))
                }
            }
            is JSONArray -> value.apply { for (i in 0 until length()) put(i, walk(opt(i))) }
            is String -> text(value, token)
            else -> value
        }
        return walk(parsed)!!
    }
}
