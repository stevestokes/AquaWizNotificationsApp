package app.aquawiznotifier

import java.time.Instant
import org.json.JSONObject

/** A locally recorded, server-accepted calibration request, not a controller measurement. */
data class KhCalibrationEvent(
    val id: String,
    val serial: String,
    val recordedAt: Instant,
    val newKh: Double,
    val previousKh: Double? = null,
) {
    val enabled: Boolean get() = newKh > 0.0
    fun description(): String = if (!enabled) "dKH calibration disabled" else
        "dKH calibration: " + (previousKh?.let { "%.2f → ".format(it) } ?: "") + "%.2f dKH".format(newKh)
    fun toJson(): JSONObject = JSONObject().put("id", id).put("serial", serial)
        .put("recordedAt", recordedAt.toString()).put("newKh", newKh).put("previousKh", previousKh)
    companion object {
        fun parse(j: JSONObject): KhCalibrationEvent? = runCatching {
            val value = j.getDouble("newKh")
            val serial = j.getString("serial")
            require(serial.isNotBlank() && value.isFinite() && value >= 0)
            KhCalibrationEvent(j.getString("id"), serial, Instant.parse(j.getString("recordedAt")), value,
                if (j.isNull("previousKh")) null else j.optDouble("previousKh").takeIf { it.isFinite() && it > 0 })
        }.getOrNull()
    }
}
