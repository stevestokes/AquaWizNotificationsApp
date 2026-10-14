package app.aquawiznotifier

/** field26 is dosing demand in thousandths of a dKH, not an unconditional mL value. */
object DosingCalculation {
    fun amountPerDkh(serial: String, field5: String?): Double? {
        val raw = field5?.toBigIntegerOrNull()?.takeIf { it.signum() >= 0 } ?: return null
        val amount = if (serial.trim().startsWith("KH1", true)) raw else raw.divide(java.math.BigInteger.valueOf(1000))
        return amount.toDouble().takeIf { it.isFinite() && it >= 0 }
    }
    fun millilitres(raw: Double?, amount: Double?): Double? {
        if (raw == null || !raw.isFinite() || raw < 0) return null
        if (raw == 0.0) return 0.0
        if (amount == null || !amount.isFinite() || amount < 0) return null
        return (raw * amount / 1000.0).takeIf { it.isFinite() }
    }
}
