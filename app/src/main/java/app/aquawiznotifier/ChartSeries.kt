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
    fun calculate(
        items: List<Measurement>, low: Double?, high: Double?,
        visible: Set<ChartSeries> = setOf(ChartSeries.KH),
    ): Pair<Double, Double> {
        val values = items.flatMap { m -> visible.filter { it != ChartSeries.DELTA && it != ChartSeries.DOSE }.mapNotNull { it.value(m) } }
            .filter { it.isFinite() }.toMutableList()
        if (low != null && high != null && low.isFinite() && high.isFinite() && low <= high) {
            values += low
            values += high
        }
        // Tight, linear bounds retain the target band and every enabled KH/pH line.
        // Each edge receives only 0.2 padding, rather than fixed empty bands.
        return ((values.minOrNull() ?: 7.0) - 0.2) to ((values.maxOrNull() ?: 9.0) + 0.2)
    }
    fun delta(items: List<Measurement>): Pair<Double, Double> {
        val values = items.mapNotNull { it.deltaPh }.filter { it.isFinite() }
        if (values.isEmpty()) return -0.2 to 0.2
        val low = values.minOrNull()!!; val high = values.maxOrNull()!!
        val padding = maxOf(0.02, (high - low) * 0.1)
        return (low - padding) to (high + padding)
    }
    fun dose(items: List<Measurement>): Pair<Double, Double> {
        val values = items.mapNotNull { it.doseMl }.filter { it.isFinite() }
        val low = minOf(0.0, values.minOrNull() ?: 0.0)
        val high = maxOf(0.1, values.maxOrNull() ?: 0.1)
        val padding = (high - low) * 0.1
        return (low - padding) to (high + padding)
    }

}
