package app.aquawiznotifier

enum class ChartSeries(val label: String, val color: Int, val defaultVisible: Boolean) {
    KH("KH", 0xFF5B34FF.toInt(), true),
    PH("pH", 0xFF3BBF5B.toInt(), true),
    PH_OPEN_AIR("pH(O)", 0xFF287DF0.toInt(), true),
    DELTA("ΔpH", 0xFFF07818.toInt(), false),
    DOSE("Dose (mL)", 0xFFE5BD00.toInt(), false);

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
    fun secondary(items: List<Measurement>, visible: Set<ChartSeries>): Pair<Double, Double> {
        val enabled = visible.filter { it == ChartSeries.DELTA || it == ChartSeries.DOSE }
        // Keep the right scale available even before either secondary line is enabled.
        val series = enabled.ifEmpty { listOf(ChartSeries.DELTA, ChartSeries.DOSE) }
        val values = items.flatMap { m -> series.mapNotNull { it.value(m) } }.filter { it.isFinite() }.toMutableList()
        if (values.isEmpty()) return -0.2 to 0.2
        if (ChartSeries.DOSE in series) values += 0.0
        val low = values.minOrNull()!!; val high = values.maxOrNull()!!
        val padding = maxOf(0.02, (high - low) * 0.1)
        return (low - padding) to (high + padding)
    }

}
