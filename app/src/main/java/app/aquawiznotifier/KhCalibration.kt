package app.aquawiznotifier

import org.json.JSONObject
import java.math.BigDecimal
import java.util.Locale

/** Contract recovered from CalibrateKhModal + changeConfig in the official Hermes APK. */
object KhCalibration {
    const val PATH = "/api/v1/KH/start-config"
    fun parse(text: String): BigDecimal? {
        val normalized = text.trim().replace(',', '.')
        if (!normalized.matches(Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))) return null
        return normalized.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 && it.toDouble().isFinite() && it.toDouble() * 1000 < Double.POSITIVE_INFINITY }
    }
    fun body(session: Session, serial: String, value: BigDecimal): JSONObject {
        require(serial.isNotBlank() && value.signum() >= 0 && value.toDouble().isFinite() && (value.toDouble() * 1000).isFinite())
        return JSONObject().put("user", session.username)
            .put("token", JSONObject().put("access_token", session.accessToken))
            .put("serial", serial.trim().uppercase(Locale.ROOT))
            .put("field10", value.multiply(BigDecimal(1000)).stripTrailingZeros().toPlainString())
    }
}
