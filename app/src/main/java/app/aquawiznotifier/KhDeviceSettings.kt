package app.aquawiznotifier

import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale

data class KhTargetSettings(val target: BigDecimal, val deviation: BigDecimal, val interval: Int, val sleepFrom: Int, val sleepTo: Int)
data class KhDosingSettings(val remaining: BigDecimal, val amountPerDkh: BigDecimal, val maxPerHour: BigDecimal, val emailThreshold: BigDecimal)

/** TargetKhSettingsModal #30790/#30793 and KhDosingSettingsModal #29929/#29932. */
object KhDeviceSettings {
    fun target(summary: DeviceSummary): KhTargetSettings {
        val schedule = summary.measurementSchedule ?: error("Controller did not return its measurement schedule")
        val settings = KhTargetSettings(BigDecimal.valueOf(summary.khTarget ?: error("Missing KH target")),
            BigDecimal.valueOf(summary.khDeviation ?: error("Missing KH email threshold")),
            schedule.take(1).toInt(), schedule.substring(1, 3).toInt(), schedule.substring(3, 5).toInt())
        validate(settings)
        return settings
    }
    fun dosing(summary: DeviceSummary, serial: String): KhDosingSettings {
        val raw5 = BigInteger(summary.dosingField5 ?: error("Missing dosing amount setting"))
        val raw6 = BigInteger(summary.dosingField6 ?: error("Missing maximum dosing setting"))
        val kh1 = serial.trim().startsWith("KH1", true)
        return KhDosingSettings(BigDecimal.valueOf(summary.dosingRemainingMl ?: error("Missing dosing solution remaining")),
            BigDecimal(if (kh1) raw5 else raw5.divide(BigInteger.valueOf(1000))),
            BigDecimal(if (kh1) raw6 else raw6.divide(BigInteger.valueOf(10000))),
            BigDecimal.valueOf(summary.dosingWarningMl ?: error("Missing dosing email threshold")))
    }
    fun validate(value: KhTargetSettings) {
        require(value.target.signum() > 0 && value.target.toDouble().isFinite()) { "Enter a positive KH target" }
        require(value.deviation.signum() >= 0 && value.deviation.toDouble().isFinite()) { "Enter a non-negative KH threshold" }
        require(value.interval in 1..6 && value.sleepFrom in 0..23 && value.sleepTo in 0..23) { "Choose a valid interval and sleep hours" }
    }
    fun validate(value: KhDosingSettings, serial: String) {
        listOf(value.remaining, value.amountPerDkh, value.maxPerHour, value.emailThreshold).forEach {
            require(it.signum() >= 0 && it.stripTrailingZeros().scale() <= 0 && it.toDouble().isFinite()) { "Enter whole, non-negative mL values" }
        }
        if (!serial.trim().startsWith("KH1", true)) {
            require(value.amountPerDkh <= BigDecimal(999) && value.maxPerHour <= BigDecimal(999)) { "This controller supports up to 999 mL for these dosing settings" }
        }
    }
    private fun base(session: Session, serial: String) = JSONObject().put("user", session.username)
        .put("token", JSONObject().put("access_token", session.accessToken)).put("serial", serial.trim().uppercase(Locale.ROOT))
    private fun plain(value: BigDecimal) = value.stripTrailingZeros().toPlainString()
    fun targetBody(session: Session, serial: String, value: KhTargetSettings): JSONObject {
        validate(value)
        return base(session, serial).put("field8", plain(value.target.multiply(BigDecimal(1000))))
            .put("field15", plain(value.deviation.multiply(BigDecimal(1000))))
            .put("field13", "${value.interval}${value.sleepFrom.toString().padStart(2, '0')}${value.sleepTo.toString().padStart(2, '0')}")
    }
    fun matchesTarget(summary: DeviceSummary, requested: KhTargetSettings): Boolean {
        val actual = runCatching { target(summary) }.getOrNull() ?: return false
        return actual.target.compareTo(requested.target) == 0 && actual.deviation.compareTo(requested.deviation) == 0 &&
            actual.interval == requested.interval && actual.sleepFrom == requested.sleepFrom && actual.sleepTo == requested.sleepTo
    }
    fun dosingBody(session: Session, serial: String, value: KhDosingSettings, current: DeviceSummary): JSONObject {
        validate(value, serial)
        fun encoded(amount: BigDecimal, raw: String?, divisor: Long, width: Int): String {
            if (serial.trim().startsWith("KH1", true)) return plain(amount)
            val remainder = BigInteger(raw ?: error("Missing dosing calibration setting")).mod(BigInteger.valueOf(divisor))
            return plain(amount).padStart(3, '0') + remainder.toString().padStart(width, '0')
        }
        return base(session, serial).put("field5", encoded(value.amountPerDkh, current.dosingField5, 1000, 3))
            .put("field6", encoded(value.maxPerHour, current.dosingField6, 10000, 4))
            .put("field14", plain(value.remaining)).put("field16", plain(value.emailThreshold))
    }
}
