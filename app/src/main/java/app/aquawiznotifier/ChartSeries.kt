package app.aquawiznotifier

enum class ChartSeries(val label: String, val color: Int, val defaultVisible: Boolean) {
    KH("KH", 0xFF5B34FF.toInt(), true),
    PH("pH", 0xFF3BBF5B.toInt(), true),
    PH_OPEN_AIR("pH(O)", 0xFF1D9945.toInt(), true),
    DELTA("ΔpH", 0xFF16AE89.toInt(), false),
    DOSE("Dose (mL)", 0xFFE6B84C.toInt(), false);

    fun value(m: Measurement): Double? = when (this) {
        KH -> m.kh
        PH -> m.ph
        PH_OPEN_AIR -> m.phOpenAir
        DELTA -> m.deltaPh
        DOSE -> m.doseMl
    }
}

enum class ChartLineStyle { SOLID, DASHED, DOTTED }

object ChartBounds {
    fun calculate(items: List<Measurement>, low: Double?, high: Double?): Pair<Double, Double> {
        if (low != null && high != null && low.isFinite() && high.isFinite() && low <= high) return (low - 0.5) to (high + 0.5)
        val values = items.map { it.kh }.filter { it.isFinite() }
        return ((values.minOrNull() ?: 7.0) - 0.5) to ((values.maxOrNull() ?: 9.0) + 0.5)
    }
}
