package app.aquawiznotifier

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class DeviceSummary(
    val khTarget: Double? = null,
    val khDeviation: Double? = null,
    val phProbeStatus: Double? = null,
    val dosingRemainingMl: Double? = null,
    val dosingWarningMl: Double? = null,
    val trueTankKh: Double? = null,
    val dosingField5: String? = null,
    val dosingField6: String? = null,
    val measurementSchedule: String? = null,
) {
    val khLow: Double? get() = if (khTarget != null && khDeviation != null) khTarget - khDeviation else null
    val khHigh: Double? get() = if (khTarget != null && khDeviation != null) khTarget + khDeviation else null
}

object DeviceSummaryJson {
    // Verified from official DevicePage + TargetKhSettingsModal Hermes bytecode v96.
    fun parse(raw: String, serial: String): DeviceSummary? {
        val root = runCatching { JSONTokener(raw).nextValue() }.getOrNull() ?: return null
        val candidates = mutableListOf<Pair<String?, JSONObject>>()
        fun walk(value: Any?, inheritedSerial: String?) {
            when (value) {
                is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i), inheritedSerial)
                is JSONObject -> {
                    val ownSerial = MeasurementJson.serialKeys.firstNotNullOfOrNull { key ->
                        value.optString(key).takeIf { it.isNotBlank() && it != "null" }
                    } ?: inheritedSerial
                    if (listOf("field8", "field10", "field15", "latest_ph", "field14").any { value.has(it) }) candidates += ownSerial to value
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        walk(value.opt(key), if (key.equals(serial, true)) serial else ownSerial)
                    }
                }
            }
        }
        walk(root, null)
        val matched = candidates.filter { it.first?.equals(serial, true) == true }
        val objectValue = if (matched.size == 1) matched.single().second
            else if (matched.isEmpty() && candidates.size == 1 && candidates.single().first == null) candidates.single().second
            else return null
        fun number(key: String) = objectValue.opt(key)?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() }
        return DeviceSummary(
            khTarget = number("field8")?.div(1000.0)?.takeIf { it > 0 },
            khDeviation = number("field15")?.div(1000.0)?.takeIf { it >= 0 },
            phProbeStatus = number("latest_ph")?.takeIf { it >= 0 },
            dosingRemainingMl = number("field14")?.takeIf { it >= 0 },
            dosingWarningMl = number("field16")?.takeIf { it >= 0 },
            trueTankKh = number("field10")?.div(1000.0)?.takeIf { it >= 0 },
            dosingField5 = objectValue.opt("field5")?.toString()?.takeIf { it.matches(Regex("[0-9]+")) },
            dosingField6 = objectValue.opt("field6")?.toString()?.takeIf { it.matches(Regex("[0-9]+")) },
            measurementSchedule = objectValue.opt("field13")?.toString()?.takeIf { it.matches(Regex("[0-9]{5}")) },
        )
    }
}
