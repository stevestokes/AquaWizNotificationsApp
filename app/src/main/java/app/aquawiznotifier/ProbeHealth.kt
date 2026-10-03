package app.aquawiznotifier

object ProbeHealth {
    fun percent(status: Double?): Double? = status?.takeIf { it.isFinite() && it >= 0 && it < 1000 }?.coerceAtMost(100.0)
    fun label(status: Double?): String = when {
        status == null || !status.isFinite() || status < 0 -> "Probe status unavailable"
        status >= 1000 -> "PH Probe: Fail"
        status >= 100 -> "PH Probe: Healthy · 100%"
        else -> "PH Probe: %.0f%%".format(status)
    }
}
